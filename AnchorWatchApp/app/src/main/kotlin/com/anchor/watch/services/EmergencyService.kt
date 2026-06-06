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
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    fun start(
        graceSeconds: Int,
        scope: CoroutineScope,
        onCountdownComplete: (() -> Unit)? = null,
        onDispatched: ((online: Boolean) -> Unit)? = null,
    ) {
        cancel()
        job = scope.launch {
            for (s in graceSeconds downTo 1) {
                _state.value = EmergencyState.CountingDown(s, graceSeconds)
                delay(1000L)
            }
            onCountdownComplete?.invoke()
            dispatch()
            val current = _state.value
            if (current is EmergencyState.Sent) onDispatched?.invoke(current.online)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = EmergencyState.Idle
    }

    suspend fun dispatch() {
        _state.value = EmergencyState.Dispatching
        val (lat, lng) = locationProvider?.invoke() ?: (null to null)
        val event = EmergencyEventEntity(
            id = idGenerator(),
            timestamp = clock(),
            userId = "self",
            type = "SOS",
            isSynced = false,
        )
        store.save(event)
        val ok = runCatching { api.submit(event, lat, lng) }.getOrDefault(false)
        if (ok) {
            store.markSynced(event.id)
            _state.value = EmergencyState.Sent(online = true)
        } else {
            onQueueForRetry()
            _state.value = EmergencyState.Sent(online = false)
        }
    }
}

class EmergencyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var orchestrator: EmergencyOrchestrator
    private var activeAlarmRingtone: android.media.Ringtone? = null

    override fun onCreate() {
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
        when (intent?.action) {
            ACTION_START -> {
                val grace = intent.getIntExtra(EXTRA_GRACE_SECONDS, DEFAULT_GRACE_SECONDS)
                    .coerceAtLeast(1)
                startForegroundCompat()
                vibrate()
                orchestrator.start(
                    graceSeconds = grace,
                    scope = scope,
                    onCountdownComplete = { playLocalAlarm() },
                    onDispatched = {
                        scope.launch {
                            delay(SENT_DISPLAY_MS)
                            stopSelfSafe()
                        }
                    },
                )
            }
            ACTION_CANCEL -> {
                if (orchestrator.state.value is EmergencyState.CountingDown) {
                    orchestrator.cancel()
                }
                stopSelfSafe()
            }
            else -> stopSelfSafe()
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
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        val ringtone = RingtoneManager.getRingtone(applicationContext, uri) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        activeAlarmRingtone = ringtone
        ringtone.play()
    }

    private fun stopAlarmRingtone() {
        activeAlarmRingtone?.stop()
        activeAlarmRingtone = null
    }

    private fun stopSelfSafe() {
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
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarmRingtone()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val DEFAULT_GRACE_SECONDS = 10
        const val ACTION_START = "com.anchor.watch.action.SOS_START"
        const val ACTION_CANCEL = "com.anchor.watch.action.SOS_CANCEL"
        const val EXTRA_GRACE_SECONDS = "grace_seconds"
        private const val CHANNEL_ID = "anchor_emergency"
        private const val NOTIFICATION_ID = 911
        private const val SENT_DISPLAY_MS = 3000L

        // Loud, insistent SOS haptic: long buzzes. Medication uses a much softer/shorter
        // pattern (see MedicationAlarmService.GENTLE_VIBRATION_PATTERN) — kept here so the
        // two profiles can be compared in tests and stay deliberately distinct.
        val SOS_VIBRATION_PATTERN = longArrayOf(0, 300, 150, 300, 150, 600)

        val liveState: MutableStateFlow<EmergencyState> = MutableStateFlow(EmergencyState.Idle)

        fun start(context: Context, graceSeconds: Int = DEFAULT_GRACE_SECONDS) {
            val intent = Intent(context, EmergencyService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GRACE_SECONDS, graceSeconds)
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
