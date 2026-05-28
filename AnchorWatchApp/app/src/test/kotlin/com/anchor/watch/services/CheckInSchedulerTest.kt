package com.anchor.watch.services

import com.anchor.watch.data.CheckInApi
import com.anchor.watch.data.CheckInRepository
import com.anchor.watch.data.CheckInResult
import com.anchor.watch.data.CheckInStatus
import com.anchor.watch.data.local.CheckInEntity
import com.anchor.watch.data.local.CheckInStore
import com.anchor.watch.utils.TimeoutManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CheckInSchedulerTest {

    private class FakeStore : CheckInStore {
        val saved = mutableListOf<CheckInEntity>()
        val synced = mutableSetOf<String>()
        override suspend fun save(entity: CheckInEntity) { saved += entity }
        override suspend fun unsynced(): List<CheckInEntity> =
            saved.filterNot { it.id in synced }
        override suspend fun markSynced(id: String) { synced += id }
    }

    private class FakeApi(private val online: Boolean) : CheckInApi {
        var calls = 0
        override suspend fun submit(entity: CheckInEntity): Boolean {
            calls++
            return online
        }
    }

    @Test
    fun nextTrigger_whenTimeIsLaterToday_returnsToday() {
        val now = LocalDateTime.of(2026, 5, 15, 8, 0)
        val target = LocalTime.of(20, 30)
        val result = CheckInSchedulerService.nextTrigger(now, target)
        assertEquals(LocalDateTime.of(2026, 5, 15, 20, 30), result)
    }

    @Test
    fun nextTrigger_whenTimeAlreadyPassedToday_returnsTomorrow() {
        val now = LocalDateTime.of(2026, 5, 15, 20, 31)
        val target = LocalTime.of(20, 30)
        val result = CheckInSchedulerService.nextTrigger(now, target)
        assertEquals(LocalDateTime.of(2026, 5, 16, 20, 30), result)
    }

    @Test
    fun nextTrigger_whenTimeIsExactlyNow_returnsTomorrow() {
        val now = LocalDateTime.of(2026, 5, 15, 20, 30, 0)
        val target = LocalTime.of(20, 30)
        val result = CheckInSchedulerService.nextTrigger(now, target)
        assertEquals(LocalDateTime.of(2026, 5, 16, 20, 30), result)
    }

    @Test
    fun timeoutManager_firstTimeoutFiresAfter3Min() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val firstFires = AtomicInteger()
        val finalFires = AtomicInteger()
        val tm = TimeoutManager(
            firstDelayMs = 3 * 60 * 1000L,
            secondDelayMs = 3 * 60 * 1000L,
            tickIntervalMs = 1000L,
        )

        tm.start(
            scope = scope,
            onFirstTimeout = { firstFires.incrementAndGet() },
            onFinalTimeout = { finalFires.incrementAndGet() },
        )
        scope.testScheduler.advanceTimeBy(3 * 60 * 1000L + 100)

        assertEquals(1, firstFires.get())
        assertEquals(0, finalFires.get())
    }

    @Test
    fun timeoutManager_finalTimeoutFiresAfter6Min() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val firstFires = AtomicInteger()
        val finalFires = AtomicInteger()
        val tm = TimeoutManager(
            firstDelayMs = 3 * 60 * 1000L,
            secondDelayMs = 3 * 60 * 1000L,
            tickIntervalMs = 1000L,
        )

        tm.start(
            scope = scope,
            onFirstTimeout = { firstFires.incrementAndGet() },
            onFinalTimeout = { finalFires.incrementAndGet() },
        )
        scope.testScheduler.advanceUntilIdle()

        assertEquals(1, firstFires.get())
        assertEquals(1, finalFires.get())
        assertEquals(TimeoutManager.Phase.Expired, tm.phase.value)
    }

    @Test
    fun timeoutManager_stopPreventsAllCallbacks() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val firstFires = AtomicInteger()
        val finalFires = AtomicInteger()
        val tm = TimeoutManager(
            firstDelayMs = 1000L,
            secondDelayMs = 1000L,
            tickIntervalMs = 100L,
        )

        tm.start(
            scope = scope,
            onFirstTimeout = { firstFires.incrementAndGet() },
            onFinalTimeout = { finalFires.incrementAndGet() },
        )
        scope.testScheduler.advanceTimeBy(500)
        tm.stop()
        scope.testScheduler.advanceUntilIdle()

        assertEquals(0, firstFires.get())
        assertEquals(0, finalFires.get())
        assertEquals(TimeoutManager.Phase.Idle, tm.phase.value)
    }

    @Test
    fun repository_marksNoResponseWhenFinalTimeoutFires() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = true)
        val repo = CheckInRepository(
            store = store,
            api = api,
            onQueueForRetry = {},
            clock = { 999L },
            idGenerator = { "id-no-resp" },
        )

        val result = repo.submit(CheckInStatus.NoResponse)

        assertEquals(CheckInResult.Sent, result)
        assertEquals(1, store.saved.size)
        assertEquals("no_response", store.saved[0].status)
        assertEquals("id-no-resp", store.saved[0].id)
        assertEquals(999L, store.saved[0].timestamp)
    }

    @Test
    fun statusEnum_mapsCorrectStrings() {
        assertEquals("sad", CheckInStatus.Sad.value)
        assertEquals("neutral", CheckInStatus.Neutral.value)
        assertEquals("happy", CheckInStatus.Happy.value)
        assertEquals("no_response", CheckInStatus.NoResponse.value)
        assertNotNull(CheckInStatus.fromValue("happy"))
        assertEquals(CheckInStatus.Happy, CheckInStatus.fromValue("happy"))
        assertNull(CheckInStatus.fromValue("bogus"))
    }

    @Test
    fun repository_offlineSubmit_queuesForRetry() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = false)
        val retries = AtomicInteger()
        val repo = CheckInRepository(
            store = store,
            api = api,
            onQueueForRetry = { retries.incrementAndGet() },
            idGenerator = { "id-offline" },
        )

        val result = repo.submit(CheckInStatus.Happy)

        assertEquals(CheckInResult.Queued, result)
        assertEquals(1, retries.get())
        assertEquals(1, store.unsynced().size)
        assertFalse(store.saved[0].isSynced)
        assertTrue(store.synced.isEmpty())
    }
}
