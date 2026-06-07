package com.anchor.watch.network

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anchor.watch.data.CheckInApi
import com.anchor.watch.data.CheckInContext
import com.anchor.watch.data.MedicationApi
import com.anchor.watch.data.local.CheckInEntity
import com.anchor.watch.data.local.EmergencyEventEntity
import com.anchor.watch.data.local.MedicationEntity
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.services.EmergencyApi
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * PartnerApiAdapter — the single adaptation layer between the SOURCE watch app
 * (Claude prompt watchapp; lives at `com.anchor.watch.*`) and the TARGET partner
 * AWS Lambda + API Gateway backend.
 *
 * SOURCE interfaces stay untouched:
 *   - [EmergencyApi]   (com.anchor.watch.services)
 *   - [MedicationApi]  (com.anchor.watch.data)
 *   - [CheckInApi]     (com.anchor.watch.data)
 *
 * Each [PartnerEmergencyApi], [PartnerMedicationApi], [PartnerCheckInApi] implements
 * the SOURCE contract by translating to/from the partner's REST shape:
 *
 *   - Auth header:        X-Watch-Key  (DataStore-backed [WatchKeyStore])
 *   - Base URL:           https://u7cxnohim6.execute-api.us-east-1.amazonaws.com
 *                         (from anchor-backend/api-config.json per CLAUDE.md)
 *   - Error envelope:     partner returns `{"error":"…"}` on non-2xx; we map any
 *                         non-2xx response to `false`/`null` to fit the SOURCE
 *                         offline-first runCatching{}.getOrDefault(false) pattern.
 *
 * Pairing flow (DECISIONS.md §2): the watch initially has no X-Watch-Key. After
 * the dashboard scans the QR and calls /users/{id}/watch/pair, the backend
 * returns the watch's permanent API key + user_id. The watch persists both via
 * [WatchKeyStore.savePairingResult]. Until that happens, all API calls fail with
 * 401/403; SOURCE's offline queue + WorkManager handle the retry.
 */
object PartnerApi {
    private const val BASE_URL = "https://u7cxnohim6.execute-api.us-east-1.amazonaws.com/"

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val retrofitRef = AtomicReference<Retrofit?>()

    private fun retrofit(context: Context): Retrofit {
        retrofitRef.get()?.let { return it }
        val store = WatchKeyStore.get(context)
        val client = OkHttpClient.Builder()
            .addInterceptor(WatchKeyAuthInterceptor(store))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
        val built = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofitRef.set(built)
        return built
    }

    /** Adapter implementing SOURCE's [EmergencyApi]. */
    fun emergency(context: Context): EmergencyApi =
        PartnerEmergencyApi(retrofit(context).create(PartnerEmergencyService::class.java))

    /** Adapter implementing SOURCE's [MedicationApi]. */
    fun medication(context: Context): MedicationApi =
        PartnerMedicationApi(retrofit(context).create(PartnerMedicationService::class.java))

    /** Adapter implementing SOURCE's [CheckInApi]. */
    fun checkIn(context: Context): CheckInApi =
        PartnerCheckInApi(retrofit(context).create(PartnerCheckInService::class.java))

    /** Pairing helper (no SOURCE interface — invoked by a future PairingScreen). */
    fun pairing(context: Context): PartnerPairingApi =
        PartnerPairingApi(retrofit(context).create(PartnerPairingService::class.java))

    /** Registers the FCM token with the backend so it can push medication sync messages. */
    suspend fun registerFcmToken(context: Context, token: String): Boolean {
        return withContext(Dispatchers.IO) {
            runCatching {
                retrofit(context)
                    .create(PartnerFcmService::class.java)
                    .registerFcmToken(FcmTokenDto(token))
                    .isSuccessful
            }.getOrDefault(false)
        }
    }
}

// ───────────────────────── Retrofit service interfaces ──────────────────────────

internal interface PartnerEmergencyService {
    @POST("emergency")
    suspend fun trigger(@Body body: EmergencyRequestDto): retrofit2.Response<EmergencyResponseDto>

    @POST("emergency/{id}/acknowledge")
    suspend fun acknowledge(@Path("id") alertId: String): retrofit2.Response<Unit>
}

internal interface PartnerMedicationService {
    @GET("medication-reminders/{userId}")
    suspend fun today(@Path("userId") userId: String): retrofit2.Response<MedicationListDto>

    @POST("medication-reminders/{id}/confirm")
    suspend fun confirm(
        @Path("id") medicationId: String,
        @Body body: MedicationStatusDto,
    ): retrofit2.Response<Unit>

    @POST("medication-reminders/{id}/missed")
    suspend fun missed(
        @Path("id") medicationId: String,
        @Body body: MedicationStatusDto,
    ): retrofit2.Response<Unit>
}

internal interface PartnerCheckInService {
    @POST("checkins")
    suspend fun submit(@Body body: CheckInRequestDto): retrofit2.Response<CheckInResponseDto>
}

internal interface PartnerFcmService {
    @POST("watch/fcm-token")
    suspend fun registerFcmToken(@Body body: FcmTokenDto): retrofit2.Response<Unit>
}

internal interface PartnerPairingService {
    @POST("watch/init-pairing")
    suspend fun initPairing(@Body body: InitPairingRequestDto): retrofit2.Response<InitPairingResponseDto>

    // Polling endpoint: returns watch_api_key once dashboard completes pairing.
    @GET("watch/credentials")
    suspend fun fetchCredentials(
        @Query("watch_id") watchId: String,
    ): retrofit2.Response<PairingResultDto>
}

// ─────────────────────────────── DTOs (Moshi) ───────────────────────────────────

@JsonClass(generateAdapter = true)
internal data class EmergencyRequestDto(
    val event_id: String,
    val user_id: String,
    val type: String,
    val timestamp: Long,
    val is_emergency: Boolean = true,
    val lat: Double? = null,
    val lng: Double? = null,
)

@JsonClass(generateAdapter = true)
internal data class EmergencyResponseDto(
    val alert_id: String?,
    val status: String?,
)

@JsonClass(generateAdapter = true)
internal data class CheckInRequestDto(
    val event_id: String,
    val user_id: String,
    val status: String,
    val timestamp: Long,
    val lat: Double?,
    val lng: Double?,
    val battery_percent: Int?,
)

@JsonClass(generateAdapter = true)
internal data class CheckInResponseDto(
    val id: String?,
    val status: String?,
    val timestamp: Long?,
)

@JsonClass(generateAdapter = true)
internal data class MedicationListDto(
    val medications: List<MedicationDto>,
)

@JsonClass(generateAdapter = true)
internal data class MedicationDto(
    val id: String,
    val medication_name: String,
    val scheduled_time: String,
    val days_of_week: List<Int>? = null,
    val status: String? = null,
    val user_id: String? = null,
    val status_timestamp: Long? = null,
)

@JsonClass(generateAdapter = true)
internal data class MedicationStatusDto(
    val timestamp: Long,
)

@JsonClass(generateAdapter = true)
internal data class FcmTokenDto(
    val fcm_token: String,
)

// Sent in the body of POST /watch/init-pairing so the backend can store the
// watch's friendly name alongside the pairing record and later stamp it on the user row.
@JsonClass(generateAdapter = true)
internal data class InitPairingRequestDto(
    val device_name: String,
)

@JsonClass(generateAdapter = true)
internal data class InitPairingResponseDto(
    val pairing_token: String,
    val watch_id: String,       // used for polling GET /watch/credentials
    val expires_at: Long,
)

@JsonClass(generateAdapter = true)
internal data class PairingResultDto(
    val watch_api_key: String,
    val user_id: String,
)

// ──────────────────────────── API implementations ──────────────────────────────

internal class PartnerEmergencyApi(
    private val service: PartnerEmergencyService,
) : EmergencyApi {
    override suspend fun submit(event: EmergencyEventEntity, lat: Double?, lng: Double?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                service.trigger(
                    EmergencyRequestDto(
                        event_id = event.id,
                        user_id = event.userId,
                        type = event.type,
                        timestamp = event.timestamp,
                        lat = lat,
                        lng = lng,
                    )
                ).isSuccessful
            }.getOrDefault(false)
        }
}

internal class PartnerMedicationApi(
    private val service: PartnerMedicationService,
) : MedicationApi {

    override suspend fun today(userId: String): List<MedicationEntity>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = service.today(userId.ifBlank { "me" })
                if (!response.isSuccessful) return@runCatching null
                response.body()?.medications?.map { it.toEntity() }
            }.getOrNull()
        }

    override suspend fun confirm(medicationId: String, timestamp: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                service.confirm(medicationId, MedicationStatusDto(timestamp)).isSuccessful
            }.getOrDefault(false)
        }

    override suspend fun miss(medicationId: String, timestamp: Long): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                service.missed(medicationId, MedicationStatusDto(timestamp)).isSuccessful
            }.getOrDefault(false)
        }

    private fun MedicationDto.toEntity(): MedicationEntity = MedicationEntity(
        id = id,
        name = medication_name,
        scheduledTime = scheduled_time,
        status = status ?: MedicationStatus.PENDING,
        userId = user_id ?: "me",
        isSynced = true,
        statusTimestamp = status_timestamp,
        daysOfWeek = days_of_week ?: emptyList(),
    )
}

internal class PartnerCheckInApi(
    private val service: PartnerCheckInService,
) : CheckInApi {
    override suspend fun submit(entity: CheckInEntity, context: CheckInContext): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val dto = CheckInRequestDto(
                    event_id = entity.id,
                    user_id = entity.userId,
                    status = entity.status,
                    timestamp = entity.timestamp,
                    lat = context.lat,
                    lng = context.lng,
                    battery_percent = context.batteryPercent,
                )
                val response = service.submit(dto)
                response.isSuccessful
            }.onFailure {
                android.util.Log.e("PartnerCheckInApi", "submit failed", it)
            }.getOrDefault(false)
        }
}

/**
 * Pairing helper. Not a SOURCE interface — wired in by future PairingScreen.
 * Returns the temporary pairing token the watch displays as a QR for the elder
 * to scan in the dashboard, completing pairing via /users/{id}/watch/pair.
 */
class PartnerPairingApi internal constructor(
    private val service: PartnerPairingService,
) {
    /**
     * Fetches a 5-minute pairing token (DECISIONS.md §2). Returns null on failure.
     * [deviceName] is the watch's friendly name (e.g. "Galaxy Watch 6"); stored by the backend
     * so the dashboard can display it instead of a raw watch_id.
     */
    internal suspend fun initPairing(deviceName: String): InitPairingResponseDto? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = service.initPairing(InitPairingRequestDto(device_name = deviceName))
                if (response.isSuccessful) response.body() else null
            }.getOrNull()
        }

    /**
     * Polls the backend for the permanent watch_api_key by watch_id.
     * Returns null when not paired yet (404) or on network error.
     * Returns [PairingResultDto] once the dashboard has completed pairing.
     */
    internal suspend fun fetchCredentials(watchId: String): PairingResultDto? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = service.fetchCredentials(watchId)
                if (response.isSuccessful) response.body() else null
            }.getOrNull()
        }
}

// ─────────────────────────── X-Watch-Key persistence ───────────────────────────

private val Context.partnerWatchDataStore by preferencesDataStore(name = "partner_watch")

/** DataStore-backed persistent watch credentials. Survives reboot per DECISIONS.md §3. */
class WatchKeyStore private constructor(private val context: Context) {

    suspend fun savePairingResult(apiKey: String, userId: String) {
        context.partnerWatchDataStore.edit { prefs ->
            prefs[KEY_API_KEY] = apiKey
            prefs[KEY_USER_ID] = userId
        }
    }

    suspend fun apiKey(): String? =
        context.partnerWatchDataStore.data.first()[KEY_API_KEY]

    suspend fun userId(): String? =
        context.partnerWatchDataStore.data.first()[KEY_USER_ID]

    /** Synchronous read for use inside OkHttp Interceptor.chain (no suspend allowed). */
    internal fun apiKeyBlocking(): String? =
        runCatching { runBlocking { apiKey() } }.getOrNull()

    suspend fun clear() {
        context.partnerWatchDataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_API_KEY = stringPreferencesKey("watch_api_key")
        private val KEY_USER_ID = stringPreferencesKey("user_id")

        @Volatile private var instance: WatchKeyStore? = null

        fun get(context: Context): WatchKeyStore =
            instance ?: synchronized(this) {
                instance ?: WatchKeyStore(context.applicationContext).also { instance = it }
            }
    }
}

/** OkHttp interceptor that injects `X-Watch-Key` from [WatchKeyStore]. */
internal class WatchKeyAuthInterceptor(
    private val store: WatchKeyStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val key = store.apiKeyBlocking()
        val request = if (key.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("X-Watch-Key", key)
                .build()
        }
        return chain.proceed(request)
    }
}
