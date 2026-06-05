package com.anchor.watch.services

import com.anchor.watch.data.MedicationApi
import com.anchor.watch.data.MedicationRepository
import com.anchor.watch.data.local.MedicationEntity
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.data.local.MedicationStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationSchedulerTest {

    private class FakeStore : MedicationStore {
        val items = mutableMapOf<String, MedicationEntity>()
        override suspend fun upsert(entity: MedicationEntity) { items[entity.id] = entity }
        override suspend fun upsertAll(entities: List<MedicationEntity>) {
            entities.forEach { items[it.id] = it }
        }
        override suspend fun byId(id: String): MedicationEntity? = items[id]
        override suspend fun all(): List<MedicationEntity> = items.values.toList()
        override suspend fun unsynced(): List<MedicationEntity> =
            items.values.filterNot { it.isSynced }
        override suspend fun markSynced(id: String) {
            items[id] = items[id]!!.copy(isSynced = true)
        }
    }

    private class FakeApi(private val remote: List<MedicationEntity>?) : MedicationApi {
        var todayCalls = 0
        override suspend fun today(userId: String): List<MedicationEntity>? {
            todayCalls++
            return remote
        }
        override suspend fun confirm(medicationId: String, timestamp: Long): Boolean = true
        override suspend fun miss(medicationId: String, timestamp: Long): Boolean = true
    }

    private fun millis(dt: LocalDateTime): Long =
        dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun run_pullsRemote_cachesLocally_andSchedulesEachReminder() = runTest {
        val store = FakeStore()
        val remote = listOf(
            MedicationEntity("a", "אקמול", "09:00", MedicationStatus.PENDING, "self"),
            MedicationEntity("b", "ויטמין D", "21:00", MedicationStatus.PENDING, "self"),
        )
        val api = FakeApi(remote)
        val repo = MedicationRepository(store = store, api = api, onQueueForRetry = {})
        val scheduled = mutableMapOf<String, Long>()
        val now = LocalDateTime.of(2026, 5, 16, 7, 0)

        MedicationScheduler(
            repository = repo,
            scheduleAlarm = { id, trigger -> scheduled[id] = trigger },
            now = { now },
        ).run()

        assertEquals(1, api.todayCalls)
        // Remote list is cached into the local store via today().
        assertEquals(remote.toSet(), store.all().toSet())
        // Both 09:00 and 21:00 are still in the future on 2026-05-16 → scheduled today.
        assertEquals(millis(LocalDateTime.of(2026, 5, 16, 9, 0)), scheduled["a"])
        assertEquals(millis(LocalDateTime.of(2026, 5, 16, 21, 0)), scheduled["b"])
    }

    @Test
    fun run_timeAlreadyPassedToday_schedulesTomorrow() = runTest {
        val store = FakeStore()
        val remote = listOf(
            MedicationEntity("a", "אקמול", "08:00", MedicationStatus.PENDING, "self"),
        )
        val repo = MedicationRepository(store = store, api = FakeApi(remote), onQueueForRetry = {})
        val scheduled = mutableMapOf<String, Long>()
        val now = LocalDateTime.of(2026, 5, 16, 9, 0)

        MedicationScheduler(
            repository = repo,
            scheduleAlarm = { id, trigger -> scheduled[id] = trigger },
            now = { now },
        ).run()

        assertEquals(millis(LocalDateTime.of(2026, 5, 17, 8, 0)), scheduled["a"])
    }

    @Test
    fun run_honorsDaysOfWeek_skipsToNextAllowedDay() = runTest {
        val store = FakeStore()
        // 2026-05-16 is Saturday (code 6); only Sunday (0) allowed → next day.
        val remote = listOf(
            MedicationEntity(
                id = "a",
                name = "אקמול",
                scheduledTime = "08:00",
                status = MedicationStatus.PENDING,
                userId = "self",
                daysOfWeek = listOf(0),
            ),
        )
        val repo = MedicationRepository(store = store, api = FakeApi(remote), onQueueForRetry = {})
        val scheduled = mutableMapOf<String, Long>()
        val now = LocalDateTime.of(2026, 5, 16, 7, 0)

        MedicationScheduler(
            repository = repo,
            scheduleAlarm = { id, trigger -> scheduled[id] = trigger },
            now = { now },
        ).run()

        assertEquals(millis(LocalDateTime.of(2026, 5, 17, 8, 0)), scheduled["a"])
    }

    @Test
    fun run_skipsNonPendingAndUnparseableTimes() = runTest {
        val store = FakeStore()
        val remote = listOf(
            MedicationEntity("taken", "א", "09:00", MedicationStatus.TAKEN, "self"),
            MedicationEntity("bad", "ב", "not-a-time", MedicationStatus.PENDING, "self"),
            MedicationEntity("ok", "ג", "10:00", MedicationStatus.PENDING, "self"),
        )
        val repo = MedicationRepository(store = store, api = FakeApi(remote), onQueueForRetry = {})
        val scheduled = mutableMapOf<String, Long>()
        val now = LocalDateTime.of(2026, 5, 16, 7, 0)

        MedicationScheduler(
            repository = repo,
            scheduleAlarm = { id, trigger -> scheduled[id] = trigger },
            now = { now },
        ).run()

        assertNull(scheduled["taken"])
        assertNull(scheduled["bad"])
        assertEquals(millis(LocalDateTime.of(2026, 5, 16, 10, 0)), scheduled["ok"])
    }

    @Test
    fun run_remoteUnavailable_schedulesFromLocalCache() = runTest {
        val store = FakeStore()
        store.items["cached"] = MedicationEntity(
            id = "cached",
            name = "אקמול",
            scheduledTime = "08:00",
            status = MedicationStatus.PENDING,
            userId = "self",
        )
        // Remote returns null → today() falls back to the local store.
        val repo = MedicationRepository(store = store, api = FakeApi(null), onQueueForRetry = {})
        val scheduled = mutableMapOf<String, Long>()
        val now = LocalDateTime.of(2026, 5, 16, 7, 0)

        MedicationScheduler(
            repository = repo,
            scheduleAlarm = { id, trigger -> scheduled[id] = trigger },
            now = { now },
        ).run()

        assertTrue(scheduled.containsKey("cached"))
        assertEquals(millis(LocalDateTime.of(2026, 5, 16, 8, 0)), scheduled["cached"])
    }
}
