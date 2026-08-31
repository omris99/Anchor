package com.anchor.watch.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.anchor.watch.services.WaterAlarmService

class WaterAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WaterAlarmService.ACTION_FIRE) return
        val waterReminderId = intent.getStringExtra(WaterAlarmService.EXTRA_WATER_ID) ?: return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        Log.d(TAG, "onReceive: waterReminderId=$waterReminderId  screenOn=${pm.isInteractive}")

        // See MedicationAlarmReceiver: WaterAlarmService is a cold-start service — its own
        // WakeLock in launchActivity() fires only after onCreate() + onStartCommand(), which
        // is too late. ACQUIRE_CAUSES_WAKEUP here guarantees the screen lights up immediately.
        @Suppress("DEPRECATION")
        pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "anchor:water_receiver_wakeup",
        ).acquire(10_000L)

        Log.d(TAG, "WakeLock acquired  screenOn=${pm.isInteractive}")

        val serviceIntent = Intent(context, WaterAlarmService::class.java).apply {
            action = WaterAlarmService.ACTION_FIRE
            putExtra(WaterAlarmService.EXTRA_WATER_ID, waterReminderId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        Log.d(TAG, "startForegroundService called")
    }

    companion object {
        private const val TAG = "AnchorWaterDebug"
    }
}
