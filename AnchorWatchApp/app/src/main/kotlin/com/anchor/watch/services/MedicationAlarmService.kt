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
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val store = MedicationLocalStore(applicationContext)
        repository = MedicationRepository(
            store = store,
            // PartnerApiAdapter: was UnreachableMedicationApi (SOURCE default stub).
            api = PartnerApi.medication(applicationContext),
            onQueueForRetry = { MedicationSyncWorker.enqueue(applicationContext) },
        )
        orchestrator = MedicationOrchestrator(
            repository = repository,
            onScheduleRepeat = { id -> scheduleRepeat(applicationContext, id) },
            onCancelRepeat = { id -> cancel(applicationContext, id) },
            onAlert = {
                vibrate()
                playAlertSound()
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
                    stopSelfSafe()
                    return START_NOT_STICKY
                }
                liveMedicationId.value = id
                startForegroundCompat()
                vibrate()
                launchActivity(id)
                orchestrator.start(scope, id)
            }
            ACTION_CONFIRM -> {
                val id = intent.getStringExtra(EXTRA_MED_ID)
                if (id == null) {
                    stopSelfSafe()
                    return START_NOT_STICKY
                }
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

    private fun launchActivity(medicationId: String) {
        val launch = Intent(this, MedicationActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
            putExtra(EXTRA_MED_ID, medicationId)
        }
        startActivity(launch)
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.medication_notification_title))
            .setContentText(getString(R.string.medication_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
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
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.medication_notification_title),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.medication_notification_text)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 250, 150, 250)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as Vibrator)
                .vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun playAlertSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        val ringtone = RingtoneManager.getRingtone(applicationContext, uri) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ringtone.play()
    }

    private fun stopSelfSafe() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_FIRE = "com.anchor.watch.action.MED_FIRE"
        const val ACTION_CONFIRM = "com.anchor.watch.action.MED_CONFIRM"
        const val EXTRA_MED_ID = "medication_id"
        private const val CHANNEL_ID = "anchor_medication"
        private const val NOTIFICATION_ID = 912
        private const val REPEAT_AFTER_MISS_MS = 15 * 60 * 1000L
        private const val FIRST_TIMEOUT_MS = 3 * 60 * 1000L
        private const val SECOND_TIMEOUT_MS = 4 * 60 * 1000L

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
