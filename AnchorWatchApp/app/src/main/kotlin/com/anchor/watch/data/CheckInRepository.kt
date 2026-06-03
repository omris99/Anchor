package com.anchor.watch.data

import com.anchor.watch.data.local.CheckInEntity
import com.anchor.watch.data.local.CheckInStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.UUID

enum class CheckInStatus(val value: String) {
    Sad("sad"),
    Neutral("neutral"),
    Happy("happy"),
    NoResponse("no_response");

    companion object {
        fun fromValue(value: String): CheckInStatus? = entries.firstOrNull { it.value == value }
    }
}

sealed class CheckInResult {
    data object Sent : CheckInResult()
    data object Queued : CheckInResult()
}

/** Contextual watch data attached to every check-in (grows over time). */
data class CheckInContext(
    val lat: Double? = null,
    val lng: Double? = null,
    val batteryPercent: Int? = null,
)

interface CheckInApi {
    suspend fun submit(entity: CheckInEntity, context: CheckInContext): Boolean
}

object UnreachableCheckInApi : CheckInApi {
    override suspend fun submit(entity: CheckInEntity, context: CheckInContext): Boolean = false
}

class CheckInRepository(
    private val store: CheckInStore,
    private val api: CheckInApi,
    private val onQueueForRetry: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val userIdProvider: () -> String = { "self" },
) {
    suspend fun submit(status: CheckInStatus, context: CheckInContext = CheckInContext()): CheckInResult {
        val entity = CheckInEntity(
            id = idGenerator(),
            timestamp = clock(),
            userId = userIdProvider(),
            status = status.value,
            isSynced = false,
        )
        withContext(NonCancellable) {
            store.save(entity)
        }
        val ok = runCatching { api.submit(entity, context) }.getOrDefault(false)
        return if (ok) {
            withContext(NonCancellable) { store.markSynced(entity.id) }
            CheckInResult.Sent
        } else {
            onQueueForRetry()
            CheckInResult.Queued
        }
    }
}
