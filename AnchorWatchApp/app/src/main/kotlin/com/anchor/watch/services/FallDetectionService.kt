package com.anchor.watch.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anchor.watch.FallAlertActivity
import com.anchor.watch.R
import com.anchor.watch.utils.FallAlertController
import com.anchor.watch.utils.FallDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var notificationManager: NotificationManager
    private var accelerometer: Sensor? = null
    private val detector = FallDetector()
    private var alertInFlight: Boolean = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val sensor = accelerometer
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            Log.i(TAG, "accelerometer registered: ${sensor.name}")
        } else {
            Log.e(TAG, "TYPE_LINEAR_ACCELERATION sensor not available on this device — fall detection disabled")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, alertInFlight=$alertInFlight")
        startForegroundCompat()
        if (accelerometer == null) {
            Log.e(TAG, "no accelerometer — stopping service")
            stopSelfSafe()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_ACKNOWLEDGE) {
            acknowledgeAlertHandled()
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        if (alertInFlight) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitudeG = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        if (detector.onSample(magnitudeG)) {
            Log.i(TAG, "fall confirmed by detector — launching FallAlertActivity")
            alertInFlight = true
            launchFallAlert()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun launchFallAlert() {
        Log.d(TAG, "launchFallAlert: starting countdown in service + best-effort activity launch")

        // Vibrate immediately from the service — the activity might never open, so the
        // user must get tactile feedback regardless.
        vibrateFallAlert()

        // Wake the screen before attempting any activity launch. Without this, Android
        // won't display an activity over the lock/ambient screen even with setTurnScreenOn.
        val wl = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            @Suppress("DEPRECATION")
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "anchor:fall_wakeup",
        )
        wl.acquire(3000L)

        val activityIntent = Intent(this, FallAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_FALL_ALERT,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ackIntent = Intent(this, FallDetectionService::class.java).apply {
            action = ACTION_ACKNOWLEDGE
        }
        val ackPendingIntent = PendingIntent.getService(
            this,
            REQUEST_ACK,
            ackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Countdown lives in the service — guaranteed to fire EmergencyService even if
        // the Activity never opens (OS throttled the launch, screen locked, etc.).
        val controller = FallAlertController(
            onTrigger = {
                Log.i(TAG, "countdown elapsed in service — firing EmergencyService")
                EmergencyService.start(applicationContext, 1)
                acknowledgeAlertHandled()
            },
            onCancel = {
                Log.i(TAG, "fall alert cancelled — resetting service state")
                acknowledgeAlertHandled()
            },
        )
        activeController = controller
        controller.start(serviceScope)

        // Attempt 1: direct startActivity() — now that the WakeLock lit the screen,
        // the foreground-service exception often makes this succeed.
        runCatching { startActivity(activityIntent) }
            .onSuccess { Log.d(TAG, "direct startActivity() succeeded") }
            .onFailure { Log.w(TAG, "direct startActivity() blocked — relying on AlarmManager/notification", it) }

        // Attempt 2: AlarmManager.setAlarmClock() — not rate-limited, fires even when
        // direct startActivity() is blocked by background launch restrictions.
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis(), activityPendingIntent),
            activityPendingIntent,
        )

        // Notification: visual + fullScreenIntent as third layer. setDeleteIntent sends
        // ACTION_ACKNOWLEDGE if the notification is somehow dismissed while ongoing.
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.fall_detected_title))
            .setContentIntent(activityPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(activityPendingIntent, true)
            .setDeleteIntent(ackPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        notificationManager.notify(FALL_ALERT_NOTIFICATION_ID, notification)
    }

    private fun vibrateFallAlert() {
        val pattern = longArrayOf(0, 500, 150, 500, 150, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as Vibrator)
                .vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    private fun startForegroundCompat() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.fall_notification_title))
            .setContentText(getString(R.string.fall_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
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

    private fun createChannels() {
        NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fall_notification_title),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.fall_notification_text)
            setSound(null, null)
        }.also { notificationManager.createNotificationChannel(it) }

        NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.fall_detected_title),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }.also { notificationManager.createNotificationChannel(it) }
    }

    fun acknowledgeAlertHandled() {
        Log.i(TAG, "acknowledgeAlertHandled: cancelling countdown and resetting state")
        // Clear activeController before cancel() to prevent recursive loops:
        // cancel() → onCancel() → acknowledgeAlertHandled() → cancel() → ...
        // After null-ing activeController, the recursive call finds ctrl=null and stops.
        val ctrl = activeController
        activeController = null
        ctrl?.cancel()
        alertInFlight = false
        detector.reset()
        notificationManager.cancel(FALL_ALERT_NOTIFICATION_ID)
    }

    private fun stopSelfSafe() {
        Log.d(TAG, "stopSelfSafe")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        runCatching { sensorManager.unregisterListener(this) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FallDetectionService"
        private const val CHANNEL_ID = "anchor_fall_detection"
        private const val ALERT_CHANNEL_ID = "anchor_fall_alert"
        private const val NOTIFICATION_ID = 913
        private const val FALL_ALERT_NOTIFICATION_ID = 914
        private const val REQUEST_FALL_ALERT = 914
        private const val REQUEST_ACK = 915

        @Volatile var activeController: FallAlertController? = null

        const val ACTION_ACKNOWLEDGE = "com.anchor.watch.action.FALL_ACK"

        fun start(context: Context) {
            Log.d(TAG, "start() called")
            val intent = Intent(context, FallDetectionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun acknowledge(context: Context) {
            Log.d(TAG, "acknowledge() called")
            val intent = Intent(context, FallDetectionService::class.java).apply {
                action = ACTION_ACKNOWLEDGE
            }
            context.startService(intent)
        }
    }
}
