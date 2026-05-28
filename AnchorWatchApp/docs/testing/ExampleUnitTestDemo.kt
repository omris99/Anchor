/*
 * ═══════════════════════════════════════════════════════════════════════════════
 *  Anchor watch app — Testing Primer (REFERENCE EXAMPLE, not part of the build)
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * THIS FILE IS DOCUMENTATION, NOT PRODUCTION CODE.
 *
 * It lives under docs/testing/ which is OUTSIDE every Gradle source set
 * (src/main/, src/test/, src/androidTest/). The Kotlin compiler never sees it.
 * You can read it, copy snippets out of it, or delete it without affecting the
 * build in any way.
 *
 * Purpose: walk through the three test patterns the real Anchor test suite uses,
 * with minimal self-contained examples — so a newcomer can map "this is how the
 * SOS tests work" onto "this is how I'd write a test for my new feature".
 *
 * To actually run anything like this in earnest, you would create a NEW file
 * under app/src/test/kotlin/com/anchor/watch/<your-package>/ with a similar
 * shape — but DO NOT modify any of the existing 41 tests; this file is for
 * teaching only.
 *
 * Patterns demonstrated (each mirrors a real Anchor test):
 *   §1 — A pure-JVM unit test with a FakeClock (mirrors FallDetectionServiceTest)
 *   §2 — A coroutine state-machine test with StandardTestDispatcher virtual time
 *        (mirrors SosServiceTest + the *Orchestrator countdown tests)
 *   §3 — A repository test using a Fake* in-memory store
 *        (mirrors CheckInRepository's offline-first contract test in IntegrationTestSuite)
 *   §4 — A Robolectric Compose test sketch
 *        (mirrors MainWatchScreenTest's Hebrew-locale UI assertions)
 */

@file:Suppress("unused", "UNUSED_PARAMETER")

package docs.testing  // <-- intentionally a non-existent package; signals "not real code"

// ─────────────────────────────────────────────────────────────────────────────
// §1. Pure-JVM unit test with an injected clock (mirrors FallDetector pattern)
// ─────────────────────────────────────────────────────────────────────────────
//
// Pattern: the production class accepts `clock: () -> Long` as a constructor
// parameter, defaulting to System::currentTimeMillis. In tests, we substitute
// a FakeClock so we can advance time deterministically.

class StalenessGate(
    private val maxAgeMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastFreshAt: Long = 0L

    fun markFresh() { lastFreshAt = clock() }

    fun isStale(): Boolean = (clock() - lastFreshAt) > maxAgeMs
}

class FakeClock(var nowMs: Long = 0L) {
    fun read(): Long = nowMs
    fun advanceBy(ms: Long) { nowMs += ms }
}

/*
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse

class StalenessGateTest {

    @Test
    fun freshlyMarked_isNotStale() {
        val clock = FakeClock(nowMs = 1_000L)
        val gate = StalenessGate(maxAgeMs = 5_000L, clock = clock::read)
        gate.markFresh()
        clock.advanceBy(4_000L)             // still inside the 5s window
        assertFalse(gate.isStale())
    }

    @Test
    fun pastMaxAge_isStale() {
        val clock = FakeClock(nowMs = 1_000L)
        val gate = StalenessGate(maxAgeMs = 5_000L, clock = clock::read)
        gate.markFresh()
        clock.advanceBy(6_000L)             // crossed the 5s window by 1s
        assertTrue(gate.isStale())
    }
}
*/

// Compare with the real Anchor test:
//   app/src/test/kotlin/com/anchor/watch/services/FallDetectionServiceTest.kt
// — it uses the SAME pattern: FakeClock injected via the `clock = clock::read`
// constructor argument to FallDetector, then `clock.advanceBy(...)` to step
// through the spike/stillness window without any real Thread.sleep.

// ─────────────────────────────────────────────────────────────────────────────
// §2. Coroutine state machine with virtual time (mirrors SosServiceTest)
// ─────────────────────────────────────────────────────────────────────────────
//
// Pattern: when the production class uses `delay(...)` inside a launched
// coroutine, we run the test on a StandardTestDispatcher and call
// `testScheduler.advanceTimeBy(...)` to advance the *virtual* clock — no
// real-time sleeps, no flakiness.
//
// Required dependency (already declared in libs.versions.toml as
// `kotlinx-coroutines-test`):
//   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

/*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals

class TickingCountdown {
    sealed class State {
        data object Idle : State()
        data class Counting(val secondsLeft: Int) : State()
        data object Done : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(seconds: Int, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            for (s in seconds downTo 1) {
                _state.value = State.Counting(s)
                delay(1_000L)
            }
            _state.value = State.Done
        }
    }
}

class TickingCountdownTest {

    @Test
    fun emitsEverySecond_thenDone() = runTest {
        val countdown = TickingCountdown()
        countdown.start(seconds = 3, scope = this)

        // Tick 1
        assertEquals(TickingCountdown.State.Counting(3), countdown.state.value)
        advanceTimeBy(1_000L); runCurrent()
        assertEquals(TickingCountdown.State.Counting(2), countdown.state.value)
        advanceTimeBy(1_000L); runCurrent()
        assertEquals(TickingCountdown.State.Counting(1), countdown.state.value)
        advanceTimeBy(1_000L); runCurrent()
        assertEquals(TickingCountdown.State.Done, countdown.state.value)
    }
}
*/

// Compare with the real Anchor test:
//   app/src/test/kotlin/com/anchor/watch/services/SosServiceTest.kt
//   — `countdown_emitsEverySecondDownToOne` uses exactly this pattern on the
//   EmergencyOrchestrator (which lives in services/EmergencyService.kt:49).

// ─────────────────────────────────────────────────────────────────────────────
// §3. Repository test with a Fake* in-memory store (mirrors CheckInRepository)
// ─────────────────────────────────────────────────────────────────────────────
//
// Pattern: the repository accepts a `Store` interface (not the concrete Room
// implementation). Tests pass a Fake that's just a MutableList behind the
// interface — no Robolectric, no real database, blazing fast.

interface NoteStore {
    suspend fun save(note: String)
    suspend fun all(): List<String>
}

class FakeNoteStore : NoteStore {
    val saved = mutableListOf<String>()
    override suspend fun save(note: String) { saved.add(note) }
    override suspend fun all(): List<String> = saved.toList()
}

class NoteRepository(private val store: NoteStore) {
    suspend fun add(note: String) {
        require(note.isNotBlank()) { "note must not be blank" }
        store.save(note.trim())
    }
    suspend fun list(): List<String> = store.all()
}

/*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals

class NoteRepositoryTest {

    @Test
    fun add_trimsAndPersists() = runTest {
        val store = FakeNoteStore()
        val repo = NoteRepository(store)

        repo.add("  hello  ")

        assertEquals(listOf("hello"), store.saved)
    }
}
*/

// Compare with the real Anchor test:
//   app/src/test/kotlin/com/anchor/watch/IntegrationTestSuite.kt
//   — the FakeEmergencyApi / FakeMedApi / FakeCheckInApi classes inside the
//   suite (lines 57, 79, 100) are exactly this pattern.

// ─────────────────────────────────────────────────────────────────────────────
// §4. Robolectric Compose UI test sketch (mirrors MainWatchScreenTest)
// ─────────────────────────────────────────────────────────────────────────────
//
// Pattern: annotate the test class with @RunWith(RobolectricTestRunner::class)
// and @Config(sdk=[33], qualifiers="he") to simulate a Hebrew-locale Wear OS
// device on the JVM. Use createComposeRule() (or createAndroidComposeRule for
// tests that need a real Activity), then call rule.setContent { ... } and
// rule.onNodeWithText / onNodeWithContentDescription to assert.
//
// Required dependencies (already declared):
//   testImplementation("org.robolectric:robolectric:4.11.1")
//   testImplementation("androidx.compose.ui:ui-test-junit4:...")

/*
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text  // M3 for the demo; the real screens use Wear Compose M2
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "he")    // Hebrew locale, Wear OS 13 (matches MainWatchScreenTest)
class HebrewGreetingScreenTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun greeting_isDisplayed_inHebrew() {
        rule.setContent {
            Box(modifier = androidx.compose.ui.Modifier.semantics {
                contentDescription = "ברכת בוקר"
            }) {
                Text("בוקר טוב")
            }
        }
        rule.onNodeWithContentDescription("ברכת בוקר").assertIsDisplayed()
    }
}
*/

// Compare with the real Anchor test:
//   app/src/test/kotlin/com/anchor/watch/screens/MainWatchScreenTest.kt
//   — same @Config(sdk=33, qualifiers="he") shape, but the real tests also
//   verify the typography constants (≥18sp), touch target sizes (≥48dp), and
//   the layoutDirection is RTL — see MainWatchSizing in
//   app/src/main/kotlin/com/anchor/watch/screens/MainWatchScreen.kt:49.

// ─────────────────────────────────────────────────────────────────────────────
// Cheat-sheet — when to use which pattern
// ─────────────────────────────────────────────────────────────────────────────
//
// | Production code shape              | Test pattern                                 |
// |------------------------------------|----------------------------------------------|
// | Pure logic + a clock               | §1 — FakeClock                               |
// | Coroutine state machine + delay()  | §2 — runTest + advanceTimeBy                 |
// | Repository over a DAO interface    | §3 — Fake*Store                              |
// | @Composable function               | §4 — Robolectric + createComposeRule         |
// | DAO with real SQL queries          | androidTest/ + Room.inMemoryDatabaseBuilder  |
// | Activity lifecycle / AlarmManager  | androidTest/ on a real device or emulator    |
//
// Anchor's 5 @Ignore'd IntegrationTestSuite cases are the last two rows —
// they're acknowledged-but-deferred, see VERIFICATION_REPORT §9.8 #5.
//
// END OF REFERENCE EXAMPLE.
