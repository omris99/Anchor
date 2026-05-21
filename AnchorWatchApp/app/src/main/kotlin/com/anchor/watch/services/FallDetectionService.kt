package com.anchor.watch.services

import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anchor.watch.FallAlertActivity
import com.anchor.watch.R
import com.anchor.watch.utils.FallDetector
import kotlin.math.sqrt

class FallDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private val detector = FallDetector()
    private var alertInFlight: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (intent?.action == ACTION_ACKNOWLEDGE) {
            acknowledgeAlertHandled()
            return START_STICKY
        }
        val sensor = accelerometer
        if (sensor == null) {
            stopSelfSafe()
            return START_NOT_STICKY
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (alertInFlight) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitudeG = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH

        if (detector.onSample(magnitudeG)) {
            alertInFlight = true
            launchFallAlert()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun launchFallAlert() {
        val intent = Intent(this, FallAlertActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
        }
        startActivity(intent)
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

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.fall_notification_title),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.fall_notification_text)
            setSound(null, null)
        }
        nm.createNotificationChannel(ch)
    }

    fun acknowledgeAlertHandled() {
        alertInFlight = false
        detector.reset()
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
        runCatching { sensorManager.unregisterListener(this) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "anchor_fall_detection"
        private const val NOTIFICATION_ID = 913
        const val ACTION_ACKNOWLEDGE = "com.anchor.watch.action.FALL_ACK"

        fun start(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun acknowledge(context: Context) {
            val intent = Intent(context, FallDetectionService::class.java).apply {
                action = ACTION_ACKNOWLEDGE
            }
            context.startService(intent)
        }
    }
}
