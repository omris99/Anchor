package com.anchor.watch.data

import com.anchor.watch.data.local.WaterEntity
import com.anchor.watch.data.local.WaterStatus
import com.anchor.watch.data.local.WaterStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

interface WaterApi {
    suspend fun today(userId: String): List<WaterEntity>?
    suspend fun confirm(waterReminderId: String, timestamp: Long): Boolean
    suspend fun miss(waterReminderId: String, timestamp: Long): Boolean
    suspend fun scheduleAck(waterReminderId: String): Boolean
}

object UnreachableWaterApi : WaterApi {
    override suspend fun today(userId: String): List<WaterEntity>? = null
    override suspend fun confirm(waterReminderId: String, timestamp: Long): Boolean = false
    override suspend fun miss(waterReminderId: String, timestamp: Long): Boolean = false
    override suspend fun scheduleAck(waterReminderId: String): Boolean = false
}

class WaterRepository(
    private val store: WaterStore,
    private val api: WaterApi,
    private val onQueueForRetry: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val userIdProvider: () -> String = { "self" },
) {
    suspend fun get(waterReminderId: String): WaterEntity? = store.byId(waterReminderId)

    suspend fun localIds(): Set<String> = store.all().map { it.id }.toSet()

    suspend fun today(): List<WaterEntity> {
        val remote = runCatching { api.today(userIdProvider()) }.getOrNull()
        if (remote != null) {
            withContext(NonCancellable) { store.upsertAll(remote) }
            return remote
        }
        return store.all()
    }

    suspend fun confirm(waterReminderId: String) {
        markStatus(waterReminderId, WaterStatus.TAKEN) { ts ->
            api.confirm(waterReminderId, ts)
        }
    }

    suspend fun miss(waterReminderId: String) {
        markStatus(waterReminderId, WaterStatus.MISSED) { ts ->
            api.miss(waterReminderId, ts)
        }
    }

    suspend fun scheduleAck(waterReminderId: String) {
        runCatching { api.scheduleAck(waterReminderId) }
        // Fire-and-forget: if this fails, the periodic 15-min sync will re-ack on next schedule.
    }

    private suspend fun markStatus(
        waterReminderId: String,
        status: String,
        remote: suspend (Long) -> Boolean,
    ) {
        val existing = store.byId(waterReminderId) ?: return
        val ts = clock()
        val updated = existing.copy(
            status = status,
            statusTimestamp = ts,
            isSynced = false,
        )
        withContext(NonCancellable) { store.upsert(updated) }
        val ok = runCatching { remote(ts) }.getOrDefault(false)
        if (ok) {
            withContext(NonCancellable) { store.markSynced(waterReminderId) }
        } else {
            onQueueForRetry()
        }
    }
}
