package com.anchor.watch.data

import com.anchor.watch.data.local.MedicationEntity
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.data.local.MedicationStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface MedicationApi {
    suspend fun today(userId: String): List<MedicationEntity>?
    suspend fun confirm(medicationId: String, timestamp: Long): Boolean
    suspend fun miss(medicationId: String, timestamp: Long): Boolean
    suspend fun scheduleAck(medicationId: String): Boolean
}

object UnreachableMedicationApi : MedicationApi {
    override suspend fun today(userId: String): List<MedicationEntity>? = null
    override suspend fun confirm(medicationId: String, timestamp: Long): Boolean = false
    override suspend fun miss(medicationId: String, timestamp: Long): Boolean = false
    override suspend fun scheduleAck(medicationId: String): Boolean = false
}

class MedicationRepository(
    private val store: MedicationStore,
    private val api: MedicationApi,
    private val onQueueForRetry: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val userIdProvider: () -> String = { "self" },
) {
    suspend fun get(medicationId: String): MedicationEntity? = store.byId(medicationId)

    suspend fun localIds(): Set<String> = store.all().map { it.id }.toSet()

    suspend fun today(): List<MedicationEntity> {
        val remote = runCatching { api.today(userIdProvider()) }.getOrNull()
        if (remote != null) {
            withContext(NonCancellable) { store.upsertAll(remote) }
            return remote
        }
        return store.all()
    }

    suspend fun confirm(medicationId: String) {
        markStatus(medicationId, MedicationStatus.TAKEN) { ts ->
            api.confirm(medicationId, ts)
        }
    }

    suspend fun miss(medicationId: String) {
        markStatus(medicationId, MedicationStatus.MISSED) { ts ->
            api.miss(medicationId, ts)
        }
    }

    suspend fun scheduleAck(medicationId: String) {
        runCatching { api.scheduleAck(medicationId) }
        // Fire-and-forget: if this fails, the periodic 15-min sync will re-ack on next schedule.
    }

    private suspend fun markStatus(
        medicationId: String,
        status: String,
        remote: suspend (Long) -> Boolean,
    ) {
        val existing = store.byId(medicationId) ?: return
        val ts = clock()
        val updated = existing.copy(
            status = status,
            statusTimestamp = ts,
            isSynced = false,
        )
        withContext(NonCancellable) { store.upsert(updated) }
        val ok = runCatching { remote(ts) }.getOrDefault(false)
        if (ok) {
            withContext(NonCancellable) { store.markSynced(medicationId) }
        } else {
            onQueueForRetry()
        }
    }
}
