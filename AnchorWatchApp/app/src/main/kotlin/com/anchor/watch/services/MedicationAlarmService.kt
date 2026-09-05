package com.anchor.watch.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.anchor.watch.MedicationActivity
import com.anchor.watch.R
import com.anchor.watch.data.MedicationRepository
import com.anchor.watch.data.local.MedicationLocalStore
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.receivers.MedicationAlarmReceiver
import com.anchor.watch.utils.TimeoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class MedicationOrchestrator(
    private val repository: MedicationRepository,
    private val onScheduleRepeat: (medicationId: String) -> Unit,
    private val onCancelRepeat: (medicationId: String) -> Unit,
    private val onAlert: () -> Unit = {},
    private val timeoutManager: TimeoutManager = TimeoutManager(),
) {
    val phase = timeoutManager.phase

    fun start(scope: CoroutineScope, medicationId: String) {
        timeoutManager.start(
            scope = scope,
            onFirstTimeout = onAlert,
            onFinalTimeout = {
                scope.launch {
                    repository.miss(medicationId)
                    onScheduleRepeat(medicationId)
                }
            },
        )
    }

    suspend fun confirm(medicationId: String) {
        timeoutManager.stop()
        repository.confirm(medicationId)
        onCancelRepeat(medicationId)
    }
}

class MedicationAlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var orchestrator: MedicationOrchestrator
    private lateinit var repository: MedicationRepository
    private lateinit var notificationManager: NotificationManager
    private var activeRingtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        val store = MedicationLocalStore(applicationContext)
        repository = MedicationRepository(
            store = store,
            api = PartnerApi.medication(applicationContext),
            onQueueForRetry = { MedicationSyncWorker.enqueue(applicationContext) },
        )
        orchestrator = MedicationOrchestrator(
            repository = repository,
            onScheduleRepeat = { id -> scheduleRepeat(applicationContext, id) },
            onCancelRepeat = { id -> cancel(applicationContext, id) },
            // Medication reminders are strictly visual: re-launch the activity so the
            // screen wakes (showOnLockScreen + turnScreenOn) without any audio or haptics.
            // Gentle re-prompt at the first timeout: soft chime + short buzz, then wake
            // the screen again. Deliberately softer than SOS (audio is otherwise SOS-only).
            onAlert = {
                gentleAlert()
                liveMedicationId.value?.let { launchActivity(it) }
            },
            timeoutManager = TimeoutManager(
                firstDelayMs = FIRST_TIMEOUT_MS,
                secondDelayMs = SECOND_TIMEOUT_MS,
            ),
        )
        scope.launch {
            orchestrator.phase.collect { livePhase.value = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FIRE -> {
                val id = intent.getStringExtra(EXTRA_MED_ID)
                if (id == null) {
                    Log.e(TAG, "ACTION_FIRE received but EXTRA_MED_ID is null")
                    stopSelfSafe()
                    return START_NOT_STICKY
                }
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                Log.d(TAG, "ACTION_FIRE: id=$id  screenOn=${pm.isInteractive}")
                liveMedicationId.value = id
                startForegroundCompat()
                Log.d(TAG, "startForeground done")
                gentleAlert()
                launchActivity(id)
                orchestrator.start(scope, id)
            }
            ACTION_CONFIRM -> {
                val id = intent.getStringExtra(EXTRA_MED_ID)
                if (id == null) {
                    stopSelfSafe()
                    return START_NOT_STICKY
                }
                stopRingtone()
                scope.launch {
                    orchestrator.confirm(id)
                    liveMedicationId.value = null
                    stopSelfSafe()
                }
            }
            else -> stopSelfSafe()
        }
        return START_NOT_STICKY
    }

    /**
     * Gentle medication alert — intentionally distinct from and softer than the SOS
     * alarm: a short double-tap buzz plus a one-shot NOTIFICATION chime (not the looping
     * TYPE_ALARM ringtone SOS uses). Repetition comes from the existing 15-min snooze
     * loop re-firing this service, not from a continuous loud alarm.
     */
    private fun gentleAlert() {
        gentleVibrate()
        playGentleChime()
    }

    private fun gentleVibrate() {
        val effect = VibrationEffect.createWaveform(GENTLE_VIBRATION_PATTERN, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(effect)
        }
    }

    private fun playGentleChime() {
        stopRingtone()
        // TYPE_NOTIFICATION gives a short, gentle chime instead of the looping alarm.
        // USAGE_ALARM on AudioAttributes is still required so the sound bypasses
        // Theater Mode / DND on Wear OS (the URI alone does not determine this).
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        val ringtone = RingtoneManager.getRingtone(applicationContext, uri) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
        activeRingtone = ringtone
    }

    private fun stopRingtone() {
        activeRingtone?.stop()
        activeRingtone = null
    }

    private fun launchActivity(medicationId: String) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        Log.d(TAG, "launchActivity start  screenOn=${pm.isInteractive}")

        // Layer 1: ACQUIRE_CAUSES_WAKEUP forces the screen on. Without this, layers 2
        // and 3 fail silently when the screen is off.
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "anchor:med_wakeup",
        )
        wl.acquire(3_000L)
        Log.d(TAG, "WakeLock acquired  screenOn=${pm.isInteractive}")

        val launch = Intent(this, MedicationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_MED_ID, medicationId)
        }
        val activityPi = PendingIntent.getActivity(
            this,
            REQUEST_MED_ALERT,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Layer 2: direct startActivity() — succeeds when the foreground-service
        // exception allows background launches.
        runCatching { startActivity(launch) }
            .onSuccess { Log.d(TAG, "startActivity: success") }
            .onFailure { Log.e(TAG, "startActivity: FAILED — ${it::class.simpleName}: ${it.message}") }

        // Layer 3a: AlarmManager.setAlarmClock() — not rate-limited by the OS,
        // fires even when direct startActivity() is blocked by background restrictions.
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis(), activityPi),
            activityPi,
        )
        Log.d(TAG, "setAlarmClock (immediate) fired")

        // Layer 3b: IMPORTANCE_HIGH notification with fullScreenIntent on a dedicated
        // channel (no setSilent — that silently disables fullScreenIntent on the same
        // notification). The foreground service notification uses a separate LOW channel
        // so it does not interfere.
        val canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager.canUseFullScreenIntent().also {
                Log.d(TAG, "canUseFullScreenIntent=$it")
            }
        } else {
            Log.d(TAG, "canUseFullScreenIntent=true (pre-API-34)")
            true
        }
        val alertNotif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.medication_notification_title))
            .setContentIntent(activityPi)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(activityPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotif)
        Log.d(TAG, "fullScreenIntent notification posted (canUseFullScreenIntent=$canUseFullScreenIntent)")
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.medication_notification_title))
            .setContentText(getString(R.string.medication_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        // Foreground service channel: LOW so it stays silent and does not interfere
        // with fullScreenIntent (setSilent on IMPORTANCE_HIGH silently disables it).
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.medication_notification_title),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.medication_notification_text)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            },
        )
        // Alert channel: HIGH so fullScreenIntent can fire above the lock/watch face.
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.medication_notification_title),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
        )
    }

    private fun stopSelfSafe() {
        notificationManager.cancel(ALERT_NOTIFICATION_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopRingtone()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AnchorMedDebug"
        const val ACTION_FIRE = "com.anchor.watch.action.MED_FIRE"
        const val ACTION_CONFIRM = "com.anchor.watch.action.MED_CONFIRM"
        const val EXTRA_MED_ID = "medication_id"
        private const val CHANNEL_ID = "anchor_medication_v2"
        private const val ALERT_CHANNEL_ID = "anchor_medication_alert_v1"
        private const val NOTIFICATION_ID = 912
        private const val ALERT_NOTIFICATION_ID = 916
        private const val REQUEST_MED_ALERT = 917
        private const val REPEAT_AFTER_MISS_MS = 15 * 60 * 1000L
        private const val FIRST_TIMEOUT_MS = 3 * 60 * 1000L
        private const val SECOND_TIMEOUT_MS = 4 * 60 * 1000L

        // Short, soft double-tap. Total duration is far shorter than
        // EmergencyService.SOS_VIBRATION_PATTERN so the medication haptic stays gentle.
        val GENTLE_VIBRATION_PATTERN = longArrayOf(0, 120, 80, 120)

        val livePhase: MutableStateFlow<TimeoutManager.Phase> =
            MutableStateFlow(TimeoutManager.Phase.Idle)
        val liveMedicationId: MutableStateFlow<String?> = MutableStateFlow(null)

        fun schedule(context: Context, medicationId: String, triggerAtMillis: Long) {
            val pi = pendingIntent(context, medicationId)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, pi), pi)
        }

        fun scheduleRepeat(context: Context, medicationId: String) {
            schedule(context, medicationId, System.currentTimeMillis() + REPEAT_AFTER_MISS_MS)
        }

        fun cancel(context: Context, medicationId: String) {
            val pi = pendingIntent(context, medicationId)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
        }

        fun nextTriggerForToday(now: LocalDateTime, scheduledTime: LocalTime): LocalDateTime {
            val today = now.toLocalDate().atTime(scheduledTime)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }

        fun nextTriggerMillis(now: LocalDateTime, scheduledTime: LocalTime): Long =
            nextTriggerForToday(now, scheduledTime)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        /**
         * Next trigger honoring backend day codes (0=Sun..6=Sat). An empty [allowedDays]
         * means "every day" and behaves like the day-agnostic overload. Scans up to 7
         * days ahead for the first allowed day whose scheduled time is still in the future.
         */
        fun nextTriggerForToday(
            now: LocalDateTime,
            scheduledTime: LocalTime,
            allowedDays: Set<Int>,
        ): LocalDateTime {
            if (allowedDays.isEmpty()) return nextTriggerForToday(now, scheduledTime)
            for (offset in 0..7) {
                val candidate = now.toLocalDate().plusDays(offset.toLong()).atTime(scheduledTime)
                // java.time: MON=1..SUN=7 → backend code 0=Sun..6=Sat via value % 7.
                val code = candidate.dayOfWeek.value % 7
                if (code in allowedDays && candidate.isAfter(now)) return candidate
            }
            // Unreachable for any non-empty allowedDays, but stay total.
            return nextTriggerForToday(now, scheduledTime)
        }

        fun nextTriggerMillis(
            now: LocalDateTime,
            scheduledTime: LocalTime,
            allowedDays: Set<Int>,
        ): Long =
            nextTriggerForToday(now, scheduledTime, allowedDays)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        fun sendConfirm(context: Context, medicationId: String) {
            val intent = Intent(context, MedicationAlarmService::class.java).apply {
                action = ACTION_CONFIRM
                putExtra(EXTRA_MED_ID, medicationId)
            }
            context.startService(intent)
        }

        private fun pendingIntent(context: Context, medicationId: String): PendingIntent {
            val intent = Intent(context, MedicationAlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_MED_ID, medicationId)
            }
            return PendingIntent.getBroadcast(
                context,
                medicationId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
