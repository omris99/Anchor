package com.anchor.watch.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anchor.watch.data.MedicationApi
import com.anchor.watch.data.WaterApi
import com.anchor.watch.data.local.MedicationLocalStore
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.data.local.MedicationStore
import com.anchor.watch.data.local.WaterLocalStore
import com.anchor.watch.data.local.WaterStatus
import com.anchor.watch.data.local.WaterStore
import com.anchor.watch.network.PartnerApi
import java.util.concurrent.TimeUnit

/**
 * Handles both medications and water reminders in a single periodic tick — they're the
 * same pull-drain-reschedule shape, so one WorkManager job covers both instead of running
 * two identical periodic jobs against the battery.
 */
class MedicationSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val medOk = drain(
            store = MedicationLocalStore(applicationContext),
            api = PartnerApi.medication(applicationContext),
        )
        val waterOk = drainWater(
            store = WaterLocalStore(applicationContext),
            api = PartnerApi.water(applicationContext),
        )
        // Pull newly created/edited reminders down and (re)arm their alarms so dashboard
        // changes propagate without waiting for a reboot or app open. Best-effort: a
        // failure here must not flip a successful status drain into a retry storm.
        runCatching { MedicationScheduler.syncAndReschedule(applicationContext) }
        runCatching { WaterScheduler.syncAndReschedule(applicationContext) }
        return if (medOk && waterOk) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "anchor_medication_sync"
        const val PERIODIC_WORK_NAME = "anchor_medication_sync_periodic"
        private const val PERIODIC_INTERVAL_MINUTES = 15L

        suspend fun drain(store: MedicationStore, api: MedicationApi): Boolean {
            val pending = store.unsynced()
            if (pending.isEmpty()) return true
            var allOk = true
            for (med in pending) {
                val ts = med.statusTimestamp ?: System.currentTimeMillis()
                val ok = runCatching {
                    when (med.status) {
                        MedicationStatus.TAKEN -> api.confirm(med.id, ts)
                        MedicationStatus.MISSED -> api.miss(med.id, ts)
                        else -> true
                    }
                }.getOrDefault(false)
                if (ok) store.markSynced(med.id) else allOk = false
            }
            return allOk
        }

        suspend fun drainWater(store: WaterStore, api: WaterApi): Boolean {
            val pending = store.unsynced()
            if (pending.isEmpty()) return true
            var allOk = true
            for (water in pending) {
                val ts = water.statusTimestamp ?: System.currentTimeMillis()
                val ok = runCatching {
                    when (water.status) {
                        WaterStatus.TAKEN -> api.confirm(water.id, ts)
                        WaterStatus.MISSED -> api.miss(water.id, ts)
                        else -> true
                    }
                }.getOrDefault(false)
                if (ok) store.markSynced(water.id) else allOk = false
            }
            return allOk
        }

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<MedicationSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /**
         * Periodic pull+reschedule so dashboard reminder edits reach the watch within
         * ~15 min. KEEP existing work so repeated calls (app launch, boot) don't reset
         * the interval. 15 min is WorkManager's minimum periodic interval.
         */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<MedicationSyncWorker>(
                PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
