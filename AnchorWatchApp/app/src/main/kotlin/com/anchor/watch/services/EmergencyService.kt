package com.anchor.watch.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anchor.watch.R
import com.anchor.watch.data.local.EmergencyEventEntity
import com.anchor.watch.data.local.EmergencyLocalStore
import com.anchor.watch.data.local.EmergencyStore
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.utils.requestBestLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class EmergencyState {
    data object Idle : EmergencyState()
    data class CountingDown(val secondsLeft: Int, val total: Int) : EmergencyState()
    data object Dispatching : EmergencyState()
    data class Sent(val online: Boolean) : EmergencyState()
}

interface EmergencyApi {
    suspend fun submit(
        event: EmergencyEventEntity,
        lat: Double? = null,
        lng: Double? = null,
    ): Boolean
}

object UnreachableEmergencyApi : EmergencyApi {
    override suspend fun submit(event: EmergencyEventEntity, lat: Double?, lng: Double?): Boolean = false
}

class EmergencyOrchestrator(
    private val store: EmergencyStore,
    private val api: EmergencyApi,
    private val onQueueForRetry: () -> Unit,
    private val locationProvider: (suspend () -> Pair<Double?, Double?>)? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    private val _state = MutableStateFlow<EmergencyState>(EmergencyState.Idle)
    val state: StateFlow<EmergencyState> = _state.asStateFlow()

    private var job: Job? = null
    private var pendingType: String = "SOS"

    fun start(
        graceSeconds: Int,
        scope: CoroutineScope,
        type: String = "SOS",
        onCountdownComplete: (() -> Unit)? = null,
        onDispatched: ((online: Boolean) -> Unit)? = null,
    ) {
        pendingType = type
        Log.i(OTAG, "start: grace=${graceSeconds}s")
        cancel()
        job = scope.launch {
            for (s in graceSeconds downTo 1) {
                Log.d(OTAG, "countdown: ${s}s remaining")
                _state.value = EmergencyState.CountingDown(s, graceSeconds)
                delay(1000L)
            }
            Log.i(OTAG, "countdown elapsed — invoking onCountdownComplete")
            runCatching { onCountdownComplete?.invoke() }.onFailure { e ->
                Log.e(OTAG, "onCountdownComplete threw", e)
            }
            dispatch()
            val current = _state.value
            Log.d(OTAG, "post-dispatch state: $current")
            if (current is EmergencyState.Sent) {
                runCatching { onDispatched?.invoke(current.online) }.onFailure { e ->
                    Log.e(OTAG, "onDispatched threw", e)
                }
            }
        }
    }

    fun cancel() {
        Log.i(OTAG, "cancel")
        job?.cancel()
        job = null
        _state.value = EmergencyState.Idle
    }

    suspend fun dispatch() {
        Log.i(OTAG, "dispatch: fetching location")
        _state.value = EmergencyState.Dispatching
        val (lat, lng) = runCatching { locationProvider?.invoke() ?: (null to null) }
            .onFailure { e -> Log.e(OTAG, "locationProvider threw", e) }
            .getOrDefault(null to null)
        Log.d(OTAG, "dispatch: location=($lat, $lng)")
        val event = EmergencyEventEntity(
            id = idGenerator(),
            timestamp = clock(),
            userId = "self",
            type = pendingType,
            isSynced = false,
        )
        Log.d(OTAG, "dispatch: saving event id=${event.id}")
        runCatching { store.save(event) }.onFailure { e -> Log.e(OTAG, "store.save threw", e) }
        Log.i(OTAG, "dispatch: submitting to API")
        val ok = runCatching { api.submit(event, lat, lng) }
            .onFailure { e -> Log.e(OTAG, "api.submit threw", e) }
            .getOrDefault(false)
        if (ok) {
            Log.i(OTAG, "dispatch: API success — event synced")
            runCatching { store.markSynced(event.id) }.onFailure { e -> Log.e(OTAG, "store.markSynced threw", e) }
            _state.value = EmergencyState.Sent(online = true)
        } else {
            Log.w(OTAG, "dispatch: API failed — queuing for retry")
            runCatching { onQueueForRetry() }.onFailure { e -> Log.e(OTAG, "onQueueForRetry threw", e) }
            _state.value = EmergencyState.Sent(online = false)
        }
    }

    companion object {
        private const val OTAG = "EmergencyOrchestrator"
    }
}

class EmergencyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var orchestrator: EmergencyOrchestrator
    // Accessed only from mainHandler — never from background threads.
    private var activeAlarmRingtone: android.media.Ringtone? = null

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()
        createChannel()
        orchestrator = EmergencyOrchestrator(
            store = EmergencyLocalStore(applicationContext),
            // PartnerApiAdapter: was UnreachableEmergencyApi (SOURCE default stub).
            api = PartnerApi.emergency(applicationContext),
            onQueueForRetry = { EmergencySyncWorker.enqueue(applicationContext) },
            locationProvider = {
                val location = requestBestLocation(applicationContext)
                location?.latitude to location?.longitude
            },
        )
        scope.launch {
            orchestrator.state.collect { liveState.value = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val grace = intent.getIntExtra(EXTRA_GRACE_SECONDS, DEFAULT_GRACE_SECONDS)
                    .coerceAtLeast(1)
                val type = intent.getStringExtra(EXTRA_TYPE) ?: "SOS"
                Log.i(TAG, "ACTION_START: grace=${grace}s type=$type")
                startForegroundCompat()
                runCatching { vibrate() }.onFailure { e -> Log.e(TAG, "vibrate threw", e) }
                // Alarm plays throughout the countdown, stops when countdown ends.
                runCatching { playLocalAlarm() }.onFailure { e -> Log.e(TAG, "playLocalAlarm threw", e) }
                orchestrator.start(
                    graceSeconds = grace,
                    scope = scope,
                    type = type,
                    onCountdownComplete = {
                        Log.i(TAG, "countdown complete — stopping alarm before dispatch")
                        // Ringtone.stop() must run on the main thread (OEM native constraint).
                        mainHandler.post {
                            runCatching { stopAlarmRingtone() }.onFailure { e -> Log.e(TAG, "stopAlarmRingtone threw", e) }
                        }
                    },
                    onDispatched = { online ->
                        Log.i(TAG, "onDispatched: online=$online — will stop service in ${SENT_DISPLAY_MS}ms")
                        scope.launch {
                            delay(SENT_DISPLAY_MS)
                            Log.d(TAG, "stopping service after sent display delay")
                            // Post to main thread: stopAlarmRingtone (Ringtone.stop) and
                            // stopSelf must run on main thread to avoid threading crashes.
                            mainHandler.post { stopSelfSafe() }
                        }
                    },
                )
            }
            ACTION_CANCEL -> {
                Log.i(TAG, "ACTION_CANCEL: state=${orchestrator.state.value}")
                if (orchestrator.state.value is EmergencyState.CountingDown) {
                    orchestrator.cancel()
                }
                stopSelfSafe()
            }
            else -> {
                Log.w(TAG, "unknown action '${intent?.action}' — stopping service")
                stopSelfSafe()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.sos_notification_title))
            .setContentText(getString(R.string.sos_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sos_notification_title),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.sos_notification_text)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    private fun vibrate() {
        val pattern = SOS_VIBRATION_PATTERN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as Vibrator)
                .vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun playLocalAlarm() {
        Log.d(TAG, "playLocalAlarm")
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (uri == null) {
            Log.w(TAG, "playLocalAlarm: no default alarm URI")
            return
        }
        val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
        if (ringtone == null) {
            Log.w(TAG, "playLocalAlarm: getRingtone returned null")
            return
        }
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        activeAlarmRingtone = ringtone
        runCatching { ringtone.play() }.onFailure { e -> Log.e(TAG, "ringtone.play threw", e) }
        Log.d(TAG, "playLocalAlarm: playing")
    }

    private fun stopAlarmRingtone() {
        Log.d(TAG, "stopAlarmRingtone: active=${activeAlarmRingtone != null}")
        runCatching { activeAlarmRingtone?.stop() }.onFailure { e -> Log.e(TAG, "ringtone.stop threw", e) }
        activeAlarmRingtone = null
    }

    private fun stopSelfSafe() {
        Log.d(TAG, "stopSelfSafe: current liveState=${liveState.value}")
        stopAlarmRingtone()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        // Self-heal: reset the process-static state so a subsequent SOS press starts
        // a fresh countdown instead of observing a stale terminal state.
        if (liveState.value !is EmergencyState.CountingDown) {
            liveState.value = EmergencyState.Idle
        }
        Log.d(TAG, "stopSelfSafe: calling stopSelf()")
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopAlarmRingtone()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "EmergencyService"
        const val DEFAULT_GRACE_SECONDS = 10
        const val ACTION_START = "com.anchor.watch.action.SOS_START"
        const val ACTION_CANCEL = "com.anchor.watch.action.SOS_CANCEL"
        const val EXTRA_GRACE_SECONDS = "grace_seconds"
        const val EXTRA_TYPE = "emergency_type"
        private const val CHANNEL_ID = "anchor_emergency"
        private const val NOTIFICATION_ID = 911
        private const val SENT_DISPLAY_MS = 3000L

        // Loud, insistent SOS haptic: long buzzes. Medication uses a much softer/shorter
        // pattern (see MedicationAlarmService.GENTLE_VIBRATION_PATTERN) — kept here so the
        // two profiles can be compared in tests and stay deliberately distinct.
        val SOS_VIBRATION_PATTERN = longArrayOf(0, 300, 150, 300, 150, 600)

        val liveState: MutableStateFlow<EmergencyState> = MutableStateFlow(EmergencyState.Idle)

        fun start(context: Context, graceSeconds: Int = DEFAULT_GRACE_SECONDS, type: String = "SOS") {
            val intent = Intent(context, EmergencyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GRACE_SECONDS, graceSeconds)
                putExtra(EXTRA_TYPE, type)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, EmergencyService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
