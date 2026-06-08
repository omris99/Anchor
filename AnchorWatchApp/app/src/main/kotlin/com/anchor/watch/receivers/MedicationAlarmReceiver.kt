package com.anchor.watch.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.anchor.watch.services.MedicationAlarmService

class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MedicationAlarmService.ACTION_FIRE) return
        val medicationId = intent.getStringExtra(MedicationAlarmService.EXTRA_MED_ID) ?: return

        // Turn the screen on here, before the service starts. MedicationAlarmService
        // is a cold-start service — its own WakeLock in launchActivity() fires only
        // after onCreate() + onStartCommand(), which is too late. Acquiring
        // ACQUIRE_CAUSES_WAKEUP in the Receiver guarantees the screen lights up the
        // moment the alarm fires. Auto-releases after 10 s; the service's WakeLock
        // takes over within that window.
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "anchor:med_receiver_wakeup",
            )
            .acquire(10_000L)

        val serviceIntent = Intent(context, MedicationAlarmService::class.java).apply {
            action = MedicationAlarmService.ACTION_FIRE
            putExtra(MedicationAlarmService.EXTRA_MED_ID, medicationId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
