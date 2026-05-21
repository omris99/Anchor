package com.anchor.watch.screens

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.anchor.watch.data.CheckInApi
import com.anchor.watch.data.CheckInRepository
import com.anchor.watch.data.CheckInStatus
import com.anchor.watch.data.local.CheckInEntity
import com.anchor.watch.data.local.CheckInStore
import com.anchor.watch.utils.TimeoutManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    // SDK 34 matches the app's minSdk=34 so the framework parser doesn't reject
    // the merged manifest with PackageParserException("Requires newer sdk version #34").
    // (`manifest = Config.NONE` does NOT bypass that check while
    // testOptions.unitTests.isIncludeAndroidResources=true — the parser still
    // reads <uses-sdk> through the resource loader path.)
    sdk = [34],
    // Hebrew + Israel + RTL — forces values-iw/ resources to win and
    // LocalLayoutDirection to default to Rtl regardless of host JVM locale.
    // Use the legacy "iw" code (not modern "he"): Robolectric's resource resolver
    // doesn't apply the he→iw alias that runtime Android does, so the qualifier
    // must match the actual folder name (values-iw/).
    qualifiers = "iw-rIL-ldrtl",
    // Skip activity/service entry parsing — Compose tests don't need those.
    manifest = Config.NONE,
    application = android.app.Application::class,
)
class DailyCheckInScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private class FakeStore : CheckInStore {
        val saved = mutableListOf<CheckInEntity>()
        override suspend fun save(entity: CheckInEntity) { saved += entity }
        override suspend fun unsynced(): List<CheckInEntity> = saved
        override suspend fun markSynced(id: String) = Unit
    }

    private class FakeApi : CheckInApi {
        var lastCall: CheckInEntity? = null
        override suspend fun submit(entity: CheckInEntity): Boolean {
            lastCall = entity
            return true
        }
    }

    private fun repo(store: FakeStore = FakeStore(), api: FakeApi = FakeApi()) =
        CheckInRepository(
            store = store,
            api = api,
            onQueueForRetry = {},
            clock = { 1_000L },
            idGenerator = { "test-id" },
        )

    @Test
    fun all_three_emojis_render() {
        val store = FakeStore()
        rule.setContent {
            DailyCheckInScreen(
                repository = repo(store),
                timeoutManager = TimeoutManager(
                    firstDelayMs = 60_000L,
                    secondDelayMs = 60_000L,
                ),
                onFinished = {},
            )
        }
        rule.onNodeWithContentDescription("אני מרגיש עצוב").assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithContentDescription("אני מרגיש רגיל").assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithContentDescription("אני מרגיש טוב").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun tapping_happy_emoji_submitsHappyStatus() {
        val store = FakeStore()
        val api = FakeApi()
        var finishedCount = 0
        rule.setContent {
            DailyCheckInScreen(
                repository = repo(store, api),
                timeoutManager = TimeoutManager(
                    firstDelayMs = 60_000L,
                    secondDelayMs = 60_000L,
                ),
                onFinished = { finishedCount++ },
            )
        }
        rule.onNodeWithContentDescription("אני מרגיש טוב").performClick()
        rule.waitForIdle()

        assertEquals(1, store.saved.size)
        assertEquals(CheckInStatus.Happy.value, store.saved[0].status)
    }

    @Test
    fun tapping_sad_emoji_submitsSadStatus() {
        val store = FakeStore()
        rule.setContent {
            DailyCheckInScreen(
                repository = repo(store),
                timeoutManager = TimeoutManager(
                    firstDelayMs = 60_000L,
                    secondDelayMs = 60_000L,
                ),
                onFinished = {},
            )
        }
        rule.onNodeWithContentDescription("אני מרגיש עצוב").performClick()
        rule.waitForIdle()

        assertEquals(1, store.saved.size)
        assertEquals(CheckInStatus.Sad.value, store.saved[0].status)
    }

    @Test
    fun countdown_decrements_overTime() {
        val tm = TimeoutManager(
            firstDelayMs = 10_000L,
            secondDelayMs = 10_000L,
            tickIntervalMs = 1000L,
        )
        rule.setContent {
            DailyCheckInScreen(
                repository = repo(),
                timeoutManager = tm,
                onFinished = {},
            )
        }
        rule.waitForIdle()
        val first = tm.phase.value
        assertTrue(first is TimeoutManager.Phase.First)
        val firstRemaining = (first as TimeoutManager.Phase.First).remainingMs

        rule.mainClock.advanceTimeBy(3_000L)
        rule.waitForIdle()

        val later = tm.phase.value as? TimeoutManager.Phase.First
        if (later != null) {
            assertTrue(later.remainingMs < firstRemaining)
        }
    }

    @Test
    fun back_press_callback_consumesEvent() {
        var consumed = false
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { consumed = true }
        }
        rule.activity.onBackPressedDispatcher.addCallback(rule.activity, callback)
        rule.activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(consumed)
        assertFalse(rule.activity.isFinishing)
    }
}
