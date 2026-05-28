package com.anchor.watch.services

import com.anchor.watch.data.MedicationApi
import com.anchor.watch.data.MedicationRepository
import com.anchor.watch.data.local.MedicationEntity
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.data.local.MedicationStore
import com.anchor.watch.utils.TimeoutManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationAlarmServiceTest {

    private class FakeStore : MedicationStore {
        val items = mutableMapOf<String, MedicationEntity>()
        override suspend fun upsert(entity: MedicationEntity) { items[entity.id] = entity }
        override suspend fun upsertAll(entities: List<MedicationEntity>) {
            entities.forEach { items[it.id] = it }
        }
        override suspend fun byId(id: String): MedicationEntity? = items[id]
        override suspend fun all(): List<MedicationEntity> = items.values.toList()
        override suspend fun unsynced(): List<MedicationEntity> = items.values.filterNot { it.isSynced }
        override suspend fun markSynced(id: String) {
            items[id] = items[id]!!.copy(isSynced = true)
        }
    }

    private class FakeApi(private val online: Boolean) : MedicationApi {
        var confirmCalls = 0
        var missCalls = 0
        override suspend fun today(userId: String): List<MedicationEntity>? = null
        override suspend fun confirm(medicationId: String, timestamp: Long): Boolean {
            confirmCalls++
            return online
        }
        override suspend fun miss(medicationId: String, timestamp: Long): Boolean {
            missCalls++
            return online
        }
    }

    private fun seededRepo(
        api: MedicationApi = FakeApi(online = true),
        store: FakeStore = FakeStore(),
        onQueueForRetry: () -> Unit = {},
        timestamp: Long = 1_000L,
    ): Pair<MedicationRepository, FakeStore> {
        store.items["med-1"] = MedicationEntity(
            id = "med-1",
            name = "אספירין",
            scheduledTime = "08:00",
            status = MedicationStatus.PENDING,
            userId = "self",
        )
        val repo = MedicationRepository(
            store = store,
            api = api,
            onQueueForRetry = onQueueForRetry,
            clock = { timestamp },
            userIdProvider = { "self" },
        )
        return repo to store
    }

    @Test
    fun nextTrigger_whenScheduledLaterToday_returnsToday() {
        val now = LocalDateTime.of(2026, 5, 16, 7, 0)
        val time = LocalTime.of(8, 0)
        assertEquals(
            LocalDateTime.of(2026, 5, 16, 8, 0),
            MedicationAlarmService.nextTriggerForToday(now, time),
        )
    }

    @Test
    fun nextTrigger_whenScheduledAlreadyPassed_returnsTomorrow() {
        val now = LocalDateTime.of(2026, 5, 16, 8, 1)
        val time = LocalTime.of(8, 0)
        assertEquals(
            LocalDateTime.of(2026, 5, 17, 8, 0),
            MedicationAlarmService.nextTriggerForToday(now, time),
        )
    }

    // 2026-05-16 is a Saturday (backend day code 6); 2026-05-17 is a Sunday (code 0).

    @Test
    fun nextTrigger_dayAware_todayAllowedAndFuture_returnsToday() {
        val now = LocalDateTime.of(2026, 5, 16, 7, 0)
        val time = LocalTime.of(8, 0)
        assertEquals(
            LocalDateTime.of(2026, 5, 16, 8, 0),
            MedicationAlarmService.nextTriggerForToday(now, time, allowedDays = setOf(6)),
        )
    }

    @Test
    fun nextTrigger_dayAware_todayNotAllowed_skipsToNextAllowedDay() {
        val now = LocalDateTime.of(2026, 5, 16, 7, 0)
        val time = LocalTime.of(8, 0)
        // Only Sunday (0) allowed → skip Saturday to the next day.
        assertEquals(
            LocalDateTime.of(2026, 5, 17, 8, 0),
            MedicationAlarmService.nextTriggerForToday(now, time, allowedDays = setOf(0)),
        )
    }

    @Test
    fun nextTrigger_dayAware_todayAllowedButPassed_skipsToNextWeek() {
        val now = LocalDateTime.of(2026, 5, 16, 9, 0)
        val time = LocalTime.of(8, 0)
        // Saturday allowed but 08:00 already passed → next Saturday.
        assertEquals(
            LocalDateTime.of(2026, 5, 23, 8, 0),
            MedicationAlarmService.nextTriggerForToday(now, time, allowedDays = setOf(6)),
        )
    }

    @Test
    fun nextTrigger_dayAware_emptyAllowedDays_behavesLikeEveryDay() {
        val now = LocalDateTime.of(2026, 5, 16, 9, 0)
        val time = LocalTime.of(8, 0)
        assertEquals(
            MedicationAlarmService.nextTriggerForToday(now, time),
            MedicationAlarmService.nextTriggerForToday(now, time, allowedDays = emptySet()),
        )
    }

    @Test
    fun confirm_withinGrace_marksTakenAndCancelsRepeat_andNoMiss() = runTest {
        val (repo, store) = seededRepo()
        val scheduledIds = mutableListOf<String>()
        val cancelledIds = mutableListOf<String>()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = { scheduledIds += it },
            onCancelRepeat = { cancelledIds += it },
            timeoutManager = TimeoutManager(
                firstDelayMs = 3 * 60 * 1000L,
                secondDelayMs = 4 * 60 * 1000L,
                tickIntervalMs = 1000L,
            ),
        )

        orch.start(scope, "med-1")
        scope.testScheduler.advanceTimeBy(2 * 60 * 1000L)
        orch.confirm("med-1")
        scope.testScheduler.advanceUntilIdle()

        val saved = store.items["med-1"]!!
        assertEquals(MedicationStatus.TAKEN, saved.status)
        assertEquals(listOf("med-1"), cancelledIds)
        assertTrue(scheduledIds.isEmpty())
    }

    @Test
    fun noConfirm_throughFinalTimeout_recordsMissAndSchedulesRepeat() = runTest {
        val (repo, store) = seededRepo()
        val scheduledIds = mutableListOf<String>()
        val cancelledIds = mutableListOf<String>()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = { scheduledIds += it },
            onCancelRepeat = { cancelledIds += it },
            timeoutManager = TimeoutManager(
                firstDelayMs = 3 * 60 * 1000L,
                secondDelayMs = 4 * 60 * 1000L,
                tickIntervalMs = 1000L,
            ),
        )

        orch.start(scope, "med-1")
        scope.testScheduler.advanceUntilIdle()

        val saved = store.items["med-1"]!!
        assertEquals(MedicationStatus.MISSED, saved.status)
        assertEquals(listOf("med-1"), scheduledIds)
        assertTrue(cancelledIds.isEmpty())
    }

    @Test
    fun confirmBeforeFirstTimeout_noAlertNoMissAndCancelsRepeat() = runTest {
        val (repo, store) = seededRepo()
        val alerts = AtomicInteger()
        val scheduledIds = mutableListOf<String>()
        val cancelledIds = mutableListOf<String>()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = { scheduledIds += it },
            onCancelRepeat = { cancelledIds += it },
            onAlert = { alerts.incrementAndGet() },
            timeoutManager = TimeoutManager(
                firstDelayMs = 3 * 60 * 1000L,
                secondDelayMs = 4 * 60 * 1000L,
                tickIntervalMs = 1000L,
            ),
        )

        orch.start(scope, "med-1")
        scope.testScheduler.advanceTimeBy(60 * 1000L)
        orch.confirm("med-1")
        scope.testScheduler.advanceUntilIdle()

        // Gentle re-prompt never fired, nothing was missed, snooze loop was stopped.
        assertEquals(0, alerts.get())
        assertEquals(MedicationStatus.TAKEN, store.items["med-1"]!!.status)
        assertTrue(scheduledIds.isEmpty())
        assertEquals(listOf("med-1"), cancelledIds)
    }

    @Test
    fun gentleVibration_isDistinctlySofterThanSosAlarm() {
        val sosTotal = EmergencyService.SOS_VIBRATION_PATTERN.sum()
        val gentleTotal = MedicationAlarmService.GENTLE_VIBRATION_PATTERN.sum()
        assertTrue(
            "Medication haptic ($gentleTotal ms) must be shorter than SOS ($sosTotal ms)",
            gentleTotal < sosTotal,
        )
    }

    @Test
    fun firstTimeout_firesAlertButDoesNotMarkMiss() = runTest {
        val (repo, store) = seededRepo()
        val alerts = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = { },
            onCancelRepeat = { },
            onAlert = { alerts.incrementAndGet() },
            timeoutManager = TimeoutManager(
                firstDelayMs = 3 * 60 * 1000L,
                secondDelayMs = 4 * 60 * 1000L,
                tickIntervalMs = 1000L,
            ),
        )

        orch.start(scope, "med-1")
        scope.testScheduler.advanceTimeBy(3 * 60 * 1000L + 100)

        assertEquals(1, alerts.get())
        val saved = store.items["med-1"]!!
        assertEquals(MedicationStatus.PENDING, saved.status)
    }

    @Test
    fun repository_offlineConfirm_queuesForRetry() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = false)
        val retries = AtomicInteger()
        val (repo, _) = seededRepo(api = api, store = store, onQueueForRetry = { retries.incrementAndGet() })

        repo.confirm("med-1")

        assertEquals(1, retries.get())
        val saved = store.items["med-1"]!!
        assertFalse(saved.isSynced)
        assertEquals(MedicationStatus.TAKEN, saved.status)
        assertEquals(1, store.unsynced().size)
    }

    @Test
    fun repository_today_cachesRemoteListLocally() = runTest {
        val store = FakeStore()
        val remote = listOf(
            MedicationEntity(id = "a", name = "אקמול", scheduledTime = "09:00", status = MedicationStatus.PENDING, userId = "self"),
            MedicationEntity(id = "b", name = "ויטמין D", scheduledTime = "12:00", status = MedicationStatus.PENDING, userId = "self"),
        )
        val api = object : MedicationApi {
            override suspend fun today(userId: String): List<MedicationEntity> = remote
            override suspend fun confirm(medicationId: String, timestamp: Long): Boolean = true
            override suspend fun miss(medicationId: String, timestamp: Long): Boolean = true
        }
        val repo = MedicationRepository(
            store = store,
            api = api,
            onQueueForRetry = {},
            userIdProvider = { "self" },
        )

        val result = repo.today()

        assertEquals(2, result.size)
        assertEquals(remote.toSet(), store.all().toSet())
    }

    @Test
    fun repository_today_fallsBackToCacheWhenRemoteFails() = runTest {
        val store = FakeStore()
        store.items["cached"] = MedicationEntity(
            id = "cached",
            name = "אקמול",
            scheduledTime = "09:00",
            status = MedicationStatus.PENDING,
            userId = "self",
        )
        val api = object : MedicationApi {
            override suspend fun today(userId: String): List<MedicationEntity>? = null
            override suspend fun confirm(medicationId: String, timestamp: Long): Boolean = false
            override suspend fun miss(medicationId: String, timestamp: Long): Boolean = false
        }
        val repo = MedicationRepository(store = store, api = api, onQueueForRetry = {})

        val result = repo.today()

        assertEquals(1, result.size)
        assertEquals("cached", result[0].id)
    }

    @Test
    fun multipleMedications_eachConfirmsIndependently() = runTest {
        val store = FakeStore()
        store.items["a"] = MedicationEntity(id = "a", name = "א", scheduledTime = "08:00", status = MedicationStatus.PENDING, userId = "self")
        store.items["b"] = MedicationEntity(id = "b", name = "ב", scheduledTime = "20:00", status = MedicationStatus.PENDING, userId = "self")
        val api = FakeApi(online = true)
        val repo = MedicationRepository(store = store, api = api, onQueueForRetry = {})

        val cancelled = mutableListOf<String>()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = {},
            onCancelRepeat = { cancelled += it },
            timeoutManager = TimeoutManager(
                firstDelayMs = 10_000L,
                secondDelayMs = 10_000L,
                tickIntervalMs = 1000L,
            ),
        )

        orch.start(scope, "a")
        orch.confirm("a")
        scope.testScheduler.advanceUntilIdle()

        orch.start(scope, "b")
        orch.confirm("b")
        scope.testScheduler.advanceUntilIdle()

        assertEquals(MedicationStatus.TAKEN, store.items["a"]!!.status)
        assertEquals(MedicationStatus.TAKEN, store.items["b"]!!.status)
        assertEquals(listOf("a", "b"), cancelled)
        assertEquals(2, api.confirmCalls)
    }
}
