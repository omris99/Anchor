package com.anchor.watch.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anchor.watch.data.local.MedicationLocalStore
import com.anchor.watch.services.CheckInSchedulerService
import com.anchor.watch.services.CheckInSyncWorker
import com.anchor.watch.services.EmergencySyncWorker
import com.anchor.watch.services.FallDetectionService
import com.anchor.watch.services.MedicationAlarmService
import com.anchor.watch.services.MedicationSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

class SosReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        EmergencySyncWorker.enqueue(context)
        CheckInSyncWorker.enqueue(context)
        MedicationSyncWorker.enqueue(context)
        CheckInSchedulerService(context).rescheduleIfConfigured()
        FallDetectionService.start(context)
        rearmMedications(context)
    }

    private fun rearmMedications(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = MedicationLocalStore(context)
                val now = LocalDateTime.now()
                for (med in store.all()) {
                    val time = parseTime(med.scheduledTime) ?: continue
                    val triggerMillis = MedicationAlarmService.nextTriggerMillis(now, time)
                    MedicationAlarmService.schedule(context, med.id, triggerMillis)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun parseTime(value: String): LocalTime? = runCatching {
        LocalTime.parse(value)
    }.getOrNull()
}
