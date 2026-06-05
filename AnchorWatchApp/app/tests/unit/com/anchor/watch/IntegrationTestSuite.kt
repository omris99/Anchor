package com.anchor.watch

import com.anchor.watch.data.CheckInApi
import com.anchor.watch.data.CheckInRepository
import com.anchor.watch.data.CheckInStatus
import com.anchor.watch.data.MedicationApi
import com.anchor.watch.data.MedicationRepository
import com.anchor.watch.data.local.CheckInEntity
import com.anchor.watch.data.local.CheckInStore
import com.anchor.watch.data.local.EmergencyEventEntity
import com.anchor.watch.data.local.EmergencyStore
import com.anchor.watch.data.local.MedicationEntity
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.data.local.MedicationStore
import com.anchor.watch.services.EmergencyApi
import com.anchor.watch.services.EmergencyOrchestrator
import com.anchor.watch.services.EmergencyState
import com.anchor.watch.services.EmergencySyncWorker
import com.anchor.watch.services.MedicationOrchestrator
import com.anchor.watch.utils.FallAlertController
import com.anchor.watch.utils.FallDetector
import com.anchor.watch.utils.TimeoutManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * IntegrationTestSuite — composed-component flows mapped to the Phase 6 spec.
 *
 * Cases that require Activity launch, AlarmManager firing, or real sensor delivery
 * are @Ignore'd here with a pointer to instrumented (androidTest) coverage —
 * pure-JVM tests cannot exercise those code paths reliably.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntegrationTestSuite {

    // ---- Test doubles --------------------------------------------------------

    private class FakeEmergencyStore : EmergencyStore {
        val saved = mutableListOf<EmergencyEventEntity>()
        val synced = mutableSetOf<String>()
        override suspend fun save(event: EmergencyEventEntity) { saved += event }
        override suspend fun unsynced(): List<EmergencyEventEntity> =
            saved.filterNot { it.id in synced }
        override suspend fun markSynced(id: String) { synced += id }
    }

    private class FakeEmergencyApi(var online: Boolean) : EmergencyApi {
        var calls = 0
        override suspend fun submit(event: EmergencyEventEntity): Boolean {
            calls++
            return online
        }
    }

    private class FakeMedStore : MedicationStore {
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

    private class FakeMedApi(val online: Boolean) : MedicationApi {
        var confirms = 0
        var misses = 0
        override suspend fun today(userId: String): List<MedicationEntity>? = null
        override suspend fun confirm(medicationId: String, timestamp: Long): Boolean {
            confirms++; return online
        }
        override suspend fun miss(medicationId: String, timestamp: Long): Boolean {
            misses++; return online
        }
    }

    private class FakeCheckInStore : CheckInStore {
        val saved = mutableListOf<CheckInEntity>()
        override suspend fun save(entity: CheckInEntity) { saved += entity }
        override suspend fun unsynced(): List<CheckInEntity> = saved.filterNot { it.isSynced }
        override suspend fun markSynced(id: String) {
            saved.replaceAll { if (it.id == id) it.copy(isSynced = true) else it }
        }
    }

    private class FakeCheckInApi(val online: Boolean) : CheckInApi {
        var lastCall: CheckInEntity? = null
        override suspend fun submit(entity: CheckInEntity): Boolean {
            lastCall = entity
            return online
        }
    }

    // ---- SUITE A — Core Navigation ------------------------------------------

    @Test
    @Ignore("UI nav: belongs in androidTest with createAndroidComposeRule on MainActivity")
    fun suite_A_1_launch_app_main_watch_screen_visible() = Unit

    @Test
    @Ignore("UI nav: belongs in androidTest — verifies MainActivity routing state Main → Sos")
    fun suite_A_2_main_tap_sos_button_shows_sos_screen() = Unit

    @Test
    @Ignore("UI nav: belongs in androidTest — verifies SosScreen cancel returns to Main")
    fun suite_A_3_sos_cancel_returns_to_main() = Unit

    // ---- SUITE B — SOS Emergency -------------------------------------------

    @Test
    fun suite_B_4_countdownComplete_dispatchesEmergency() = runTest {
        val store = FakeEmergencyStore()
        val api = FakeEmergencyApi(online = true)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = EmergencyOrchestrator(
            store = store, api = api, onQueueForRetry = {},
        )
        orch.start(graceSeconds = 3, scope = scope)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(EmergencyState.Sent(online = true), orch.state.value)
    }

    @Test
    fun suite_B_5_online_dispatch_marksSynced() = runTest {
        val store = FakeEmergencyStore()
        val api = FakeEmergencyApi(online = true)
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = EmergencyOrchestrator(
            store = store, api = api, onQueueForRetry = {},
            idGenerator = { "evt-online" },
        )
        orch.start(graceSeconds = 1, scope = scope)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, api.calls)
        assertTrue(store.synced.contains("evt-online"))
        assertTrue(store.unsynced().isEmpty())
    }

    @Test
    fun suite_B_6_offline_dispatch_queuesUnsynced() = runTest {
        val store = FakeEmergencyStore()
        val api = FakeEmergencyApi(online = false)
        val retries = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = EmergencyOrchestrator(
            store = store, api = api,
            onQueueForRetry = { retries.incrementAndGet() },
            idGenerator = { "evt-offline" },
        )
        orch.start(graceSeconds = 1, scope = scope)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, store.saved.size)
        assertFalse(store.saved[0].isSynced)
        assertEquals(1, retries.get())
        assertEquals(EmergencyState.Sent(online = false), orch.state.value)
    }

    @Test
    fun suite_B_7_networkRestored_drainQueuedEvents() = runTest {
        val store = FakeEmergencyStore()
        // Seed an unsynced event (as if a prior offline run queued it)
        store.save(
            EmergencyEventEntity(
                id = "queued-1",
                timestamp = 1L,
                userId = "self",
                type = "SOS",
                isSynced = false,
            ),
        )
        val api = FakeEmergencyApi(online = true)
        val drained = EmergencySyncWorker.drain(store, api)
        assertTrue(drained)
        assertTrue(store.unsynced().isEmpty())
    }

    // ---- SUITE C — Medication ----------------------------------------------

    @Test
    @Ignore("AlarmManager fire + Activity launch: androidTest with shadow alarm or device test")
    fun suite_C_8_alarm_fires_at_scheduled_time_shows_medicationScreen() = Unit

    @Test
    fun suite_C_9_tapTaken_callsConfirmApi_marksTaken() = runTest {
        val store = FakeMedStore().apply {
            items["med-1"] = MedicationEntity(
                id = "med-1", name = "א", scheduledTime = "08:00",
                status = MedicationStatus.PENDING, userId = "self",
            )
        }
        val api = FakeMedApi(online = true)
        val repo = MedicationRepository(store = store, api = api, onQueueForRetry = {})
        val cancelled = mutableListOf<String>()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = {},
            onCancelRepeat = { cancelled += it },
            timeoutManager = TimeoutManager(
                firstDelayMs = 3 * 60_000L,
                secondDelayMs = 4 * 60_000L,
            ),
        )
        orch.start(scope, "med-1")
        orch.confirm("med-1")
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, api.confirms)
        assertEquals(MedicationStatus.TAKEN, store.items["med-1"]!!.status)
        assertEquals(listOf("med-1"), cancelled)
    }

    @Test
    fun suite_C_10_firstTimeout_firesAlert_withoutMiss() = runTest {
        val store = FakeMedStore().apply {
            items["med-1"] = MedicationEntity(
                id = "med-1", name = "א", scheduledTime = "08:00",
                status = MedicationStatus.PENDING, userId = "self",
            )
        }
        val repo = MedicationRepository(store = store, api = FakeMedApi(true), onQueueForRetry = {})
        val alerts = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = {},
            onCancelRepeat = {},
            onAlert = { alerts.incrementAndGet() },
            timeoutManager = TimeoutManager(
                firstDelayMs = 3 * 60_000L,
                secondDelayMs = 4 * 60_000L,
            ),
        )
        orch.start(scope, "med-1")
        scope.testScheduler.advanceTimeBy(3 * 60_000L + 100)
        assertEquals(1, alerts.get())
        assertEquals(MedicationStatus.PENDING, store.items["med-1"]!!.status)
    }

    @Test
    fun suite_C_11_finalTimeout_marksMissed_andSchedulesRepeat() = runTest {
        val store = FakeMedStore().apply {
            items["med-1"] = MedicationEntity(
                id = "med-1", name = "א", scheduledTime = "08:00",
                status = MedicationStatus.PENDING, userId = "self",
            )
        }
        val api = FakeMedApi(online = true)
        val repo = MedicationRepository(store = store, api = api, onQueueForRetry = {})
        val scheduled = mutableListOf<String>()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = { scheduled += it },
            onCancelRepeat = {},
            timeoutManager = TimeoutManager(
                firstDelayMs = 3 * 60_000L,
                secondDelayMs = 4 * 60_000L,
            ),
        )
        orch.start(scope, "med-1")
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, api.misses)
        assertEquals(MedicationStatus.MISSED, store.items["med-1"]!!.status)
        assertEquals(listOf("med-1"), scheduled)
    }

    @Test
    fun suite_C_12_missConfirmed_repeatCallbackInvokedOnce() = runTest {
        // The orchestrator schedules exactly one repeat per miss cycle.
        val store = FakeMedStore().apply {
            items["med-1"] = MedicationEntity(
                id = "med-1", name = "א", scheduledTime = "08:00",
                status = MedicationStatus.PENDING, userId = "self",
            )
        }
        val repo = MedicationRepository(store = store, api = FakeMedApi(true), onQueueForRetry = {})
        val scheduled = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val orch = MedicationOrchestrator(
            repository = repo,
            onScheduleRepeat = { scheduled.incrementAndGet() },
            onCancelRepeat = {},
            timeoutManager = TimeoutManager(
                firstDelayMs = 3 * 60_000L,
                secondDelayMs = 4 * 60_000L,
            ),
        )
        orch.start(scope, "med-1")
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, scheduled.get())
    }

    // ---- SUITE D — Daily Check-in ------------------------------------------

    @Test
    @Ignore("AlarmManager + CheckInActivity launch: androidTest")
    fun suite_D_13_scheduled_time_shows_daily_checkin_screen() = Unit

    @Test
    fun suite_D_14_emojiTap_callsCheckinApi_withCorrectStatusString() = runTest {
        val store = FakeCheckInStore()
        val api = FakeCheckInApi(online = true)
        val repo = CheckInRepository(
            store = store, api = api,
            onQueueForRetry = {},
            idGenerator = { "ci-1" },
            clock = { 1000L },
        )
        repo.submit(CheckInStatus.Happy)
        assertEquals("happy", api.lastCall?.status)
    }

    @Test
    fun suite_D_15_threeMinuteTimeout_firesFirstCallback() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val first = AtomicInteger()
        val final = AtomicInteger()
        val tm = TimeoutManager(firstDelayMs = 3 * 60_000L, secondDelayMs = 3 * 60_000L)
        tm.start(
            scope,
            onFirstTimeout = { first.incrementAndGet() },
            onFinalTimeout = { final.incrementAndGet() },
        )
        scope.testScheduler.advanceTimeBy(3 * 60_000L + 100)
        assertEquals(1, first.get())
        assertEquals(0, final.get())
    }

    @Test
    fun suite_D_16_sixMinuteTimeout_recordsNoResponse() = runTest {
        val store = FakeCheckInStore()
        val api = FakeCheckInApi(online = true)
        val repo = CheckInRepository(
            store = store, api = api,
            onQueueForRetry = {},
            idGenerator = { "ci-final" },
        )
        // Wired in DailyCheckInScreen: onFinalTimeout → repo.submit(NoResponse).
        // Tests the repository side of that wire-up.
        repo.submit(CheckInStatus.NoResponse)
        assertEquals("no_response", store.saved.firstOrNull()?.status)
    }

    // ---- SUITE E — Fall Detection -------------------------------------------

    @Test
    fun suite_E_17_thresholdSpike_thenStillness_yieldsDetection() {
        val clock = object { var t = 0L }
        val detector = FallDetector(
            fallThresholdG = 2.5f,
            stillnessThresholdG = 0.3f,
            impactWindowMs = 2000L,
            clock = { clock.t },
        )
        clock.t = 1000L; detector.onSample(3.0f)
        clock.t = 2500L
        assertTrue(detector.onSample(0.2f))
    }

    @Test
    fun suite_E_18_cancelWithinGrace_doesNotInvokeTrigger() = runTest {
        val triggered = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val ctrl = FallAlertController(
            graceMs = 30_000L,
            tickIntervalMs = 1000L,
            onTrigger = { triggered.incrementAndGet() },
        )
        ctrl.start(scope)
        scope.testScheduler.advanceTimeBy(5_000L)
        ctrl.cancel()
        scope.testScheduler.advanceUntilIdle()
        assertEquals(0, triggered.get())
    }

    @Test
    fun suite_E_19_graceEnds_invokesTrigger_whichWouldStartEmergencyService() = runTest {
        val triggered = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val ctrl = FallAlertController(
            graceMs = 30_000L,
            tickIntervalMs = 1000L,
            onTrigger = { triggered.incrementAndGet() },
        )
        ctrl.start(scope)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, triggered.get())
        // FallAlertActivity wires onTrigger -> EmergencyService.start(ctx, 1)
    }
}
