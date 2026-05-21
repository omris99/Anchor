package com.anchor.watch.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.anchor.watch.data.CheckInApi
import com.anchor.watch.data.local.CheckInLocalStore
import com.anchor.watch.data.local.CheckInStore
import com.anchor.watch.network.PartnerApi

class CheckInSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ok = drain(
            store = CheckInLocalStore(applicationContext),
            // PartnerApiAdapter: was UnreachableCheckInApi (SOURCE default stub).
            api = PartnerApi.checkIn(applicationContext),
        )
        return if (ok) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "anchor_checkin_sync"

        suspend fun drain(store: CheckInStore, api: CheckInApi): Boolean {
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
            val request = OneTimeWorkRequestBuilder<CheckInSyncWorker>()
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
