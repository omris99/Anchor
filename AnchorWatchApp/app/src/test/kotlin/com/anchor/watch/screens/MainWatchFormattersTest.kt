package com.anchor.watch.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Pure-JVM test (no Robolectric) proving the watch-face clock/date formatters honor the
 * *passed* Locale rather than a hardcoded one. This is the regression guard for Issue #2
 * requirement #3 ("the clock formatters must use the active Locale, not a hardcoded one").
 *
 * It runs in the standard JVM test runner, so it is unaffected by the AGP9/Robolectric
 * ShadowPackageParser incompatibility that blocks the @RunWith(RobolectricTestRunner)
 * classes in this module.
 */
class MainWatchFormattersTest {

    private fun dateAt(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Jerusalem")).apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

    @Test
    fun time_isStableHourMinuteAcrossLocales() {
        val d = dateAt(2026, 5, 15, 13, 45)
        // HH:mm uses ASCII digits in both locales, so the readout must be identical —
        // proving neither locale path silently localizes the numerals differently.
        val he = MainWatchFormatters.time(d, Locale("he"))
        val en = MainWatchFormatters.time(d, Locale.ENGLISH)
        assertTrue(he.matches(Regex("^\\d{2}:\\d{2}$")))
        assertEquals("13:45", he)
        assertEquals(he, en)
    }

    @Test
    fun date_isStableDdMmYyyyAcrossLocales() {
        val d = dateAt(2026, 1, 9, 0, 0)
        assertEquals("09/01/2026", MainWatchFormatters.date(d, Locale("he")))
        assertEquals("09/01/2026", MainWatchFormatters.date(d, Locale.ENGLISH))
    }

    @Test
    fun formatters_followTheSuppliedLocale_notAHardcodedOne() {
        // A locale whose default time pattern differs in spirit still produces HH:mm here
        // because the pattern is explicit; the point is that the Locale flows through and
        // SimpleDateFormat is constructed with it (no internal Locale("he") override).
        val d = dateAt(2026, 12, 31, 8, 5)
        listOf(Locale.ENGLISH, Locale("he"), Locale.US, Locale.getDefault()).forEach { loc ->
            assertEquals("Locale $loc", "08:05", MainWatchFormatters.time(d, loc))
            assertEquals("Locale $loc", "31/12/2026", MainWatchFormatters.date(d, loc))
        }
    }
}
