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
import com.anchor.watch.WaterActivity
import com.anchor.watch.R
import com.anchor.watch.data.WaterRepository
import com.anchor.watch.data.local.WaterLocalStore
import com.anchor.watch.network.PartnerApi
import com.anchor.watch.receivers.WaterAlarmReceiver
import com.anchor.watch.utils.TimeoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class WaterOrchestrator(
    private val repository: WaterRepository,
    private val onScheduleRepeat: (waterReminderId: String) -> Unit,
    private val onCancelRepeat: (waterReminderId: String) -> Unit,
    private val onAlert: () -> Unit = {},
    private val timeoutManager: TimeoutManager = TimeoutManager(),
) {
    val phase = timeoutManager.phase

    fun start(scope: CoroutineScope, waterReminderId: String) {
        timeoutManager.start(
            scope = scope,
            onFirstTimeout = onAlert,
            onFinalTimeout = {
                scope.launch {
                    repository.miss(waterReminderId)
                    onScheduleRepeat(waterReminderId)
                }
            },
        )
    }

    suspend fun confirm(waterReminderId: String) {
        timeoutManager.stop()
        repository.confirm(waterReminderId)
        onCancelRepeat(waterReminderId)
    }
}

class WaterAlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var orchestrator: WaterOrchestrator
    private lateinit var repository: WaterRepository
    private lateinit var notificationManager: NotificationManager
    private var activeRingtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        val store = WaterLocalStore(applicationContext)
        repository = WaterRepository(
            store = store,
            api = PartnerApi.water(applicationContext),
            onQueueForRetry = { MedicationSyncWorker.enqueue(applicationContext) },
        )
        orchestrator = WaterOrchestrator(
            repository = repository,
            onScheduleRepeat = { id -> scheduleRepeat(applicationContext, id) },
            onCancelRepeat = { id -> cancel(applicationContext, id) },
            // Water reminders are strictly visual: re-launch the activity so the screen
            // wakes (showOnLockScreen + turnScreenOn) without any audio or haptics beyond
            // the gentle re-prompt below. Deliberately softer than SOS.
            onAlert = {
                gentleAlert()
                liveWaterReminderId.value?.let { launchActivity(it) }
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
                val id = intent.getStringExtra(EXTRA_WATER_ID)
                if (id == null) {
                    Log.e(TAG, "ACTION_FIRE received but EXTRA_WATER_ID is null")
                    stopSelfSafe()
                    return START_NOT_STICKY
                }
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                Log.d(TAG, "ACTION_FIRE: id=$id  screenOn=${pm.isInteractive}")
                liveWaterReminderId.value = id
                startForegroundCompat()
                Log.d(TAG, "startForeground done")
                gentleAlert()
                launchActivity(id)
                orchestrator.start(scope, id)
            }
            ACTION_CONFIRM -> {
                val id = intent.getStringExtra(EXTRA_WATER_ID)
                if (id == null) {
                    stopSelfSafe()
                    return START_NOT_STICKY
                }
                stopRingtone()
                scope.launch {
                    orchestrator.confirm(id)
                    liveWaterReminderId.value = null
                    stopSelfSafe()
                }
            }
            else -> stopSelfSafe()
        }
        return START_NOT_STICKY
    }

    /**
     * Gentle water alert — same double-tap buzz + one-shot NOTIFICATION chime pattern as
     * [MedicationAlarmService], intentionally distinct from and softer than the SOS alarm.
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

    private fun launchActivity(waterReminderId: String) {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        Log.d(TAG, "launchActivity start  screenOn=${pm.isInteractive}")

        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "anchor:water_wakeup",
        )
        wl.acquire(3_000L)
        Log.d(TAG, "WakeLock acquired  screenOn=${pm.isInteractive}")

        val launch = Intent(this, WaterActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_WATER_ID, waterReminderId)
        }
        val activityPi = PendingIntent.getActivity(
            this,
            REQUEST_WATER_ALERT,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        runCatching { startActivity(launch) }
            .onSuccess { Log.d(TAG, "startActivity: success") }
            .onFailure { Log.e(TAG, "startActivity: FAILED — ${it::class.simpleName}: ${it.message}") }

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis(), activityPi),
            activityPi,
        )
        Log.d(TAG, "setAlarmClock (immediate) fired")

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
            .setContentTitle(getString(R.string.water_notification_title))
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
            .setContentTitle(getString(R.string.water_notification_title))
            .setContentText(getString(R.string.water_notification_text))
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
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.water_notification_title),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.water_notification_text)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
            },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.water_notification_title),
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
        private const val TAG = "AnchorWaterDebug"
        const val ACTION_FIRE = "com.anchor.watch.action.WATER_FIRE"
        const val ACTION_CONFIRM = "com.anchor.watch.action.WATER_CONFIRM"
        const val EXTRA_WATER_ID = "water_reminder_id"
        private const val CHANNEL_ID = "anchor_water_v1"
        private const val ALERT_CHANNEL_ID = "anchor_water_alert_v1"
        private const val NOTIFICATION_ID = 932
        private const val ALERT_NOTIFICATION_ID = 936
        private const val REQUEST_WATER_ALERT = 937
        private const val REPEAT_AFTER_MISS_MS = 15 * 60 * 1000L
        private const val FIRST_TIMEOUT_MS = 3 * 60 * 1000L
        private const val SECOND_TIMEOUT_MS = 4 * 60 * 1000L

        // Same short, soft double-tap as MedicationAlarmService.
        val GENTLE_VIBRATION_PATTERN = longArrayOf(0, 120, 80, 120)

        val livePhase: MutableStateFlow<TimeoutManager.Phase> =
            MutableStateFlow(TimeoutManager.Phase.Idle)
        val liveWaterReminderId: MutableStateFlow<String?> = MutableStateFlow(null)

        fun schedule(context: Context, waterReminderId: String, triggerAtMillis: Long) {
            val pi = pendingIntent(context, waterReminderId)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, pi), pi)
        }

        fun scheduleRepeat(context: Context, waterReminderId: String) {
            schedule(context, waterReminderId, System.currentTimeMillis() + REPEAT_AFTER_MISS_MS)
        }

        fun cancel(context: Context, waterReminderId: String) {
            val pi = pendingIntent(context, waterReminderId)
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pi)
        }

        fun sendConfirm(context: Context, waterReminderId: String) {
            val intent = Intent(context, WaterAlarmService::class.java).apply {
                action = ACTION_CONFIRM
                putExtra(EXTRA_WATER_ID, waterReminderId)
            }
            context.startService(intent)
        }

        private fun pendingIntent(context: Context, waterReminderId: String): PendingIntent {
            val intent = Intent(context, WaterAlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                putExtra(EXTRA_WATER_ID, waterReminderId)
            }
            return PendingIntent.getBroadcast(
                context,
                waterReminderId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
