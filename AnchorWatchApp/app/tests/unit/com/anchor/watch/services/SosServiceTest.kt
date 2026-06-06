package com.anchor.watch.services

import com.anchor.watch.data.local.EmergencyEventEntity
import com.anchor.watch.data.local.EmergencyStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SosServiceTest {

    private class FakeStore : EmergencyStore {
        val saved = mutableListOf<EmergencyEventEntity>()
        val synced = mutableSetOf<String>()
        override suspend fun save(event: EmergencyEventEntity) { saved += event }
        override suspend fun unsynced(): List<EmergencyEventEntity> =
            saved.filterNot { it.id in synced }
        override suspend fun markSynced(id: String) { synced += id }
    }

    private class FakeApi(private val online: Boolean) : EmergencyApi {
        var calls = 0
        override suspend fun submit(event: EmergencyEventEntity, lat: Double?, lng: Double?): Boolean {
            calls++
            return online
        }
    }

    @Test
    fun countdown_emitsEverySecondDownToOne() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = true)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = EmergencyOrchestrator(
            store = store,
            api = api,
            onQueueForRetry = {},
            clock = { 0L },
            idGenerator = { "id" },
        )

        orch.start(graceSeconds = 3, scope = scope)
        scope.testScheduler.advanceTimeBy(1)
        assertEquals(EmergencyState.CountingDown(3, 3), orch.state.value)

        scope.testScheduler.advanceTimeBy(1000)
        assertEquals(EmergencyState.CountingDown(2, 3), orch.state.value)

        scope.testScheduler.advanceTimeBy(1000)
        assertEquals(EmergencyState.CountingDown(1, 3), orch.state.value)
    }

    @Test
    fun cancelDuringCountdown_preventsDispatch() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = true)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = EmergencyOrchestrator(
            store = store,
            api = api,
            onQueueForRetry = {},
        )

        orch.start(graceSeconds = 5, scope = scope)
        scope.testScheduler.advanceTimeBy(2_500)
        orch.cancel()
        scope.testScheduler.advanceUntilIdle()

        assertEquals(EmergencyState.Idle, orch.state.value)
        assertEquals(0, api.calls)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun noCancel_dispatchesAfterGracePeriod() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = true)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = EmergencyOrchestrator(
            store = store,
            api = api,
            onQueueForRetry = {},
            clock = { 100L },
            idGenerator = { "evt-1" },
        )

        orch.start(graceSeconds = 2, scope = scope)
        scope.testScheduler.advanceUntilIdle()

        assertEquals(1, api.calls)
        assertEquals(1, store.saved.size)
        assertEquals("evt-1", store.saved[0].id)
        assertEquals(100L, store.saved[0].timestamp)
        assertEquals("SOS", store.saved[0].type)
        assertEquals(EmergencyState.Sent(online = true), orch.state.value)
    }

    @Test
    fun restartAfterSent_runsSecondCountdownAndDispatchesAgain() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = true)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val ids = listOf("evt-1", "evt-2").iterator()
        val orch = EmergencyOrchestrator(
            store = store,
            api = api,
            onQueueForRetry = {},
            clock = { 0L },
            idGenerator = { ids.next() },
        )

        // First SOS: run a full cycle through to Sent.
        orch.start(graceSeconds = 1, scope = scope)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(EmergencyState.Sent(online = true), orch.state.value)
        assertEquals(1, api.calls)

        // Second SOS without any manual reset: start() must re-arm the countdown
        // and dispatch again (regression guard for the single-use SOS bug).
        orch.start(graceSeconds = 2, scope = scope)
        scope.testScheduler.advanceTimeBy(1)
        assertEquals(EmergencyState.CountingDown(2, 2), orch.state.value)
        scope.testScheduler.advanceUntilIdle()

        assertEquals(EmergencyState.Sent(online = true), orch.state.value)
        assertEquals(2, api.calls)
        assertEquals(2, store.saved.size)
        assertEquals("evt-2", store.saved[1].id)
    }

    @Test
    fun offlineDispatch_persistsAsUnsyncedAndSchedulesRetry() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = false)
        val retries = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = EmergencyOrchestrator(
            store = store,
            api = api,
            onQueueForRetry = { retries.incrementAndGet() },
            idGenerator = { "evt-offline" },
        )

        orch.start(graceSeconds = 1, scope = scope)
        scope.testScheduler.advanceUntilIdle()

        assertEquals(1, store.saved.size)
        assertFalse(store.saved[0].isSynced)
        assertEquals(1, retries.get())
        assertEquals(EmergencyState.Sent(online = false), orch.state.value)
        assertEquals(listOf(store.saved[0]), store.unsynced())
    }

    @Test
    fun syncDrain_marksAllSyncedWhenApiOnline() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = true)
        store.save(
            EmergencyEventEntity(
                id = "queued-1",
                timestamp = 1L,
                userId = "self",
                type = "SOS",
                isSynced = false,
            ),
        )
        store.save(
            EmergencyEventEntity(
                id = "queued-2",
                timestamp = 2L,
                userId = "self",
                type = "SOS",
                isSynced = false,
            ),
        )
        assertEquals(2, store.unsynced().size)

        val ok = EmergencySyncWorker.drain(store, api)

        assertTrue(ok)
        assertEquals(2, api.calls)
        assertTrue(store.unsynced().isEmpty())
    }

    @Test
    fun syncDrain_returnsRetryWhenApiStillOffline() = runTest {
        val store = FakeStore()
        val api = FakeApi(online = false)
        store.save(
            EmergencyEventEntity(
                id = "queued-1",
                timestamp = 1L,
                userId = "self",
                type = "SOS",
                isSynced = false,
            ),
        )

        val ok = EmergencySyncWorker.drain(store, api)

        assertFalse(ok)
        assertEquals(1, store.unsynced().size)
    }
}
