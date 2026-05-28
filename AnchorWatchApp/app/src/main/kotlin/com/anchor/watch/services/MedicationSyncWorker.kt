package com.anchor.watch.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anchor.watch.data.MedicationApi
import com.anchor.watch.data.local.MedicationLocalStore
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.data.local.MedicationStore
import com.anchor.watch.network.PartnerApi

class MedicationSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ok = drain(
            store = MedicationLocalStore(applicationContext),
            // PartnerApiAdapter: was UnreachableMedicationApi (SOURCE default stub).
            api = PartnerApi.medication(applicationContext),
        )
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "anchor_medication_sync"

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
    }
}
