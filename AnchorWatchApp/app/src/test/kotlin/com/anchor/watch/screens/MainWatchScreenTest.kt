package com.anchor.watch.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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
class MainWatchScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun dateAt(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem")).apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    @Test
    fun time_isFormattedAsHebrewLocaleHourMinute() {
        val d = dateAt(2026, 5, 15, 13, 45)
        val expected = SimpleDateFormat("HH:mm", Locale("he"))
            .apply { timeZone = TimeZone.getTimeZone("Asia/Jerusalem") }
            .format(d)
        val actual = MainWatchFormatters.time(d)
        assertTrue(actual.matches(Regex("^\\d{2}:\\d{2}$")))
        assertEquals(expected, actual)
    }

    @Test
    fun time_padsSingleDigitMinute() {
        val d = dateAt(2026, 5, 15, 9, 5)
        assertTrue(MainWatchFormatters.time(d).endsWith(":05"))
    }

    @Test
    fun date_isFormattedAsDdMmYyyy() {
        val d = dateAt(2026, 1, 9, 0, 0)
        assertEquals("09/01/2026", MainWatchFormatters.date(d))
    }

    @Test
    fun fontSizes_meetMinimum18sp() {
        assertTrue(MainWatchSizing.TIME_ACTIVE_SP >= MainWatchSizing.MIN_FONT_SP)
        assertTrue(MainWatchSizing.TIME_AMBIENT_SP >= MainWatchSizing.MIN_FONT_SP)
        assertTrue(MainWatchSizing.DATE_SP >= MainWatchSizing.MIN_FONT_SP)
        assertTrue(MainWatchSizing.BUTTON_LABEL_SP >= MainWatchSizing.MIN_FONT_SP)
    }

    @Test
    fun touchTargets_meetMinimum48dp() {
        assertTrue(MainWatchSizing.BUTTON_SIZE_DP >= MainWatchSizing.MIN_TOUCH_DP)
    }

    @Test
    fun sosButton_isPresentAndClickable() {
        var clicked = false
        rule.setContent {
            MainWatchScreen(
                isAmbient = false,
                onSosClick = { clicked = true },
            )
        }
        rule.onNodeWithContentDescription("לחצן חירום")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertTrue(clicked)
    }

    @Test
    fun layout_rendersUnderRtl() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MainWatchScreen(
                    isAmbient = false,
                    onSosClick = {},
                )
            }
        }
        rule.onNodeWithContentDescription("לחצן חירום").assertIsDisplayed()
    }
}
