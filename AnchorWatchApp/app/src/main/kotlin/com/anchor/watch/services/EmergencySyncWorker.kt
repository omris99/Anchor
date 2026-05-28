package com.anchor.watch.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anchor.watch.data.local.EmergencyLocalStore
import com.anchor.watch.data.local.EmergencyStore
import com.anchor.watch.network.PartnerApi

class EmergencySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ok = drain(
            store = EmergencyLocalStore(applicationContext),
            // PartnerApiAdapter: was UnreachableEmergencyApi (SOURCE default stub).
            api = PartnerApi.emergency(applicationContext),
        )
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "anchor_emergency_sync"

        suspend fun drain(store: EmergencyStore, api: EmergencyApi): Boolean {
            val pending = store.unsynced()
            if (pending.isEmpty()) return true
            var allOk = true
            for (event in pending) {
                val ok = runCatching { api.submit(event) }.getOrDefault(false)
                if (ok) store.markSynced(event.id) else allOk = false
            }
            return allOk
        }

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<EmergencySyncWorker>()
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
