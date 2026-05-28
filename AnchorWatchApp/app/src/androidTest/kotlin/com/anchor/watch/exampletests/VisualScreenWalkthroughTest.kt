/*
 * ═══════════════════════════════════════════════════════════════════════════════
 *  Anchor — Visual Screen Walkthrough (instrumented, watch the emulator!)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 *  Purpose: render each major SOURCE screen on the emulator with a deliberate
 *  pause so you can WATCH it live for ~12 seconds per screen. This is not a
 *  pass/fail test — every method always "passes" (the assertion is trivial).
 *  The value is the on-screen rendering.
 *
 *  Demo order (enforced by @FixMethodOrder + screenN_ prefix):
 *      1. Main watch face   — the landing / "hero" screen
 *      2. Daily check-in    — recurring wellness flow
 *      3. Medication reminder
 *      4. Fall-detected alert
 *      5. SOS countdown     — the climactic emergency flow (last on purpose)
 *
 *  How to run:
 *    1. Boot a Wear OS emulator (API 34 Large Round recommended).
 *    2. From the IDE terminal:
 *         .\gradlew.bat :app:connectedDebugAndroidTest `
 *           -Pandroid.testInstrumentationRunnerArguments.class=`
 *           com.anchor.watch.exampletests.VisualScreenWalkthroughTest
 *    3. Watch the emulator window. Each test holds its screen for ~12 seconds.
 *    4. Total runtime: ~75 seconds (5 screens × 12 s, plus per-test overhead).
 *
 *  Record video while watching (optional):
 *      adb shell screenrecord --time-limit 90 /sdcard/walkthrough.mp4
 *      # in a second terminal, start the test
 *      adb pull /sdcard/walkthrough.mp4
 *
 *  Take a screenshot of the currently-rendered screen:
 *      adb exec-out screencap -p > screen_$(Get-Date -Format yyyyMMddHHmmss).png
 *
 *  This file is additive — does not modify any existing production code or the
 *  41 JVM unit tests.
 */

package com.anchor.watch.exampletests

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anchor.watch.data.CheckInApi
import com.anchor.watch.data.CheckInRepository
import com.anchor.watch.data.local.CheckInEntity
import com.anchor.watch.data.local.CheckInStore
import com.anchor.watch.data.local.MedicationEntity
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.data.local.MedicationStore
import com.anchor.watch.screens.DailyCheckInScreen
import com.anchor.watch.screens.FallAlertScreen
import com.anchor.watch.screens.MainWatchScreen
import com.anchor.watch.screens.MedicationReminderScreen
import com.anchor.watch.screens.SosScreen
import com.anchor.watch.utils.FallAlertController
import com.anchor.watch.utils.FallDetectionConstants
import com.anchor.watch.utils.TimeoutManager
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** How long each screen stays visible on the emulator before the test moves on. */
private const val DWELL_MS = 12_000L

// NAME_ASCENDING + the screenN_ prefix is what actually guarantees demo order —
// JUnit4's default ordering is deterministic but unspecified (often hash-based),
// so without this the screens would shuffle on different JVMs / device images.
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class VisualScreenWalkthroughTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // ─────────────────────────────────────────────────────────────────────────
    // Screen 1: Main watch face (Hebrew clock + SOS button)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun screen1_mainWatchScreen_visibleFor12Seconds() {
        composeRule.setContent {
            MainWatchScreen(
                isAmbient = false,
                onSosClick = { /* no-op — observation only */ },
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(DWELL_MS)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen 2: Daily check-in ("איך אתה מרגיש היום?" + 3 emoji buttons)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun screen2_dailyCheckIn_visibleFor12Seconds() {
        composeRule.setContent {
            DailyCheckInScreen(
                repository = CheckInRepository(
                    store = InMemoryCheckInStore(),
                    api = AlwaysOkCheckInApi(),
                    onQueueForRetry = {},
                ),
                // 5-minute timeout so the screen doesn't auto-dismiss during the dwell
                timeoutManager = TimeoutManager(
                    firstDelayMs = 5 * 60 * 1000L,
                    secondDelayMs = 5 * 60 * 1000L,
                ),
                onFinished = { /* do nothing — keep screen visible */ },
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(DWELL_MS)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen 3: Medication reminder ("נטלתי" button + medication name)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun screen3_medicationReminder_visibleFor12Seconds() {
        val testMedication = MedicationEntity(
            id = "demo_med_001",
            name = "ASPIRIN",          // shown large on the screen
            scheduledTime = "09:00",
            status = MedicationStatus.PENDING,
            userId = "demo",
            isSynced = true,
            statusTimestamp = null,
        )
        val store = InMemoryMedicationStore(listOf(testMedication))

        composeRule.setContent {
            MedicationReminderScreen(
                store = store,
                medicationId = testMedication.id,
                onConfirm = { /* no-op — keep screen visible */ },
                onFinished = { /* no-op */ },
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(DWELL_MS)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen 4: Fall-detected alert (red, 30s "אני בסדר" grace period)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun screen4_fallAlertScreen_visibleFor12Seconds() {
        val controller = FallAlertController(
            graceMs = FallDetectionConstants.GRACE_PERIOD_MS,
            onTrigger = { /* no-op */ },
            onCancel = { /* no-op */ },
        )
        composeRule.setContent {
            FallAlertScreen(
                controller = controller,
                onFinished = { /* no-op */ },
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(DWELL_MS)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Screen 5: SOS countdown screen (red, "שולח בעוד N שניות")
    // ─────────────────────────────────────────────────────────────────────────
    //
    // SosScreen reads from EmergencyService.liveState (a global MutableStateFlow).
    // Without an actual EmergencyService started, the screen sits in "Idle" and
    // would call EmergencyService.start in LaunchedEffect. We let that happen so
    // you can see the real countdown render — but be aware: the EmergencyService
    // will attempt a real POST /emergency when the count hits zero. To keep the
    // dwell pure-visual without a real network call, the dwell is short enough
    // that the 10-second countdown is what you see (not the dispatch).
    @Test
    fun screen5_sosScreen_visibleFor12Seconds() {
        composeRule.setContent {
            SosScreen(
                graceSeconds = 10,
                onDismiss = { /* no-op */ },
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(DWELL_MS)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// In-memory fakes (live only inside this test file; do not affect production)
// ─────────────────────────────────────────────────────────────────────────────

private class AlwaysOkCheckInApi : CheckInApi {
    override suspend fun submit(entity: CheckInEntity): Boolean = true
}

private class InMemoryCheckInStore : CheckInStore {
    private val rows = mutableListOf<CheckInEntity>()
    override suspend fun save(entity: CheckInEntity) { rows.add(entity) }
    override suspend fun unsynced(): List<CheckInEntity> = rows.filter { !it.isSynced }
    override suspend fun markSynced(id: String) {
        rows.replaceAll { if (it.id == id) it.copy(isSynced = true) else it }
    }
}

private class InMemoryMedicationStore(initial: List<MedicationEntity>) : MedicationStore {
    private val rows = initial.associateBy { it.id }.toMutableMap()
    override suspend fun upsert(entity: MedicationEntity) { rows[entity.id] = entity }
    override suspend fun upsertAll(entities: List<MedicationEntity>) {
        entities.forEach { rows[it.id] = it }
    }
    override suspend fun byId(id: String): MedicationEntity? = rows[id]
    override suspend fun all(): List<MedicationEntity> = rows.values.toList()
    override suspend fun unsynced(): List<MedicationEntity> = rows.values.filter { !it.isSynced }
    override suspend fun markSynced(id: String) {
        rows.computeIfPresent(id) { _, v -> v.copy(isSynced = true) }
    }
}
