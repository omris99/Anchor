package com.anchor.watch.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.anchor.watch.services.MedicationAlarmService

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MedicationAlarmService.ACTION_FIRE) return
        val medicationId = intent.getStringExtra(MedicationAlarmService.EXTRA_MED_ID) ?: return

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        Log.d(TAG, "onReceive: medicationId=$medicationId  screenOn=${pm.isInteractive}")

        // Turn the screen on here, before the service starts. MedicationAlarmService
        // is a cold-start service — its own WakeLock in launchActivity() fires only
        // after onCreate() + onStartCommand(), which is too late. Acquiring
        // ACQUIRE_CAUSES_WAKEUP in the Receiver guarantees the screen lights up the
        // moment the alarm fires. Auto-releases after 10 s; the service's WakeLock
        // takes over within that window.
        @Suppress("DEPRECATION")
        pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "anchor:med_receiver_wakeup",
        ).acquire(10_000L)

        Log.d(TAG, "WakeLock acquired  screenOn=${pm.isInteractive}")

        val serviceIntent = Intent(context, MedicationAlarmService::class.java).apply {
            action = MedicationAlarmService.ACTION_FIRE
            putExtra(MedicationAlarmService.EXTRA_MED_ID, medicationId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
        Log.d(TAG, "startForegroundService called")
    }

    companion object {
        private const val TAG = "AnchorMedDebug"
    }
}
