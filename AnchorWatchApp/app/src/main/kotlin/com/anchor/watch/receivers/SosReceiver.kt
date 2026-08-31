package com.anchor.watch.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anchor.watch.services.CheckInSchedulerService
import com.anchor.watch.services.CheckInSyncWorker
import com.anchor.watch.services.EmergencySyncWorker
import com.anchor.watch.services.FallDetectionService
import com.anchor.watch.services.MedicationScheduler
import com.anchor.watch.services.MedicationSyncWorker
import com.anchor.watch.services.WaterScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SosReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        EmergencySyncWorker.enqueue(context)
        CheckInSyncWorker.enqueue(context)
        MedicationSyncWorker.enqueue(context)
        MedicationSyncWorker.enqueuePeriodic(context)
        CheckInSchedulerService(context).rescheduleIfConfigured()
        FallDetectionService.start(context)
        rearmMedications(context)
        rearmWater(context)
    }

    // Pull reminders from the backend before scheduling so a dashboard-created reminder
    // that was never cached locally still gets an alarm after a reboot.
    private fun rearmMedications(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MedicationScheduler.syncAndReschedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun rearmWater(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WaterScheduler.syncAndReschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
