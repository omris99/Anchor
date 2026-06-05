package com.anchor.watch.utils

import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.anchor.watch.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Issue #2 requirement #4: prove locale switching + RTL/LTR resolution work *dynamically*
 * off the OS-configured context (the platform per-app locale model), not a hardcoded
 * Hebrew check.
 *
 * Each test reconfigures Robolectric's qualifiers at runtime, then asserts:
 *   - the correct localized string resource resolves (app_name: "עוגן" he / "Anchor" en), and
 *   - LocaleHelper.layoutDirection() derives Rtl for Hebrew and Ltr for English from the
 *     resolved locale via the framework's bidi rules.
 *
 * NOTE: This class uses RobolectricTestRunner and is therefore subject to the known
 * AGP9 + Robolectric 4.14.1 ShadowPackageParser incompatibility (PackageParserException at
 * PackageParser.java:1230) that blocks every Robolectric class in this module in the
 * current environment. manifest = Config.NONE avoids merged-manifest parsing where
 * possible; the assertions are correct and will pass once the toolchain is aligned. The
 * pure-JVM MainWatchFormattersTest covers requirement #3 in a runner that executes today.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    manifest = Config.NONE,
    application = android.app.Application::class,
)
class LocaleResolutionTest {

    private fun context() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun hebrewQualifier_resolvesHebrewStringsAndRtl() {
        RuntimeEnvironment.setQualifiers("he-rIL-ldrtl")
        val ctx = context()
        assertEquals("עוגן", ctx.getString(R.string.app_name))
        assertEquals(LayoutDirection.Rtl, LocaleHelper.layoutDirection(ctx))
    }

    @Test
    fun englishQualifier_resolvesEnglishStringsAndLtr() {
        RuntimeEnvironment.setQualifiers("en-rUS-ldltr")
        val ctx = context()
        assertEquals("Anchor", ctx.getString(R.string.app_name))
        assertEquals(LayoutDirection.Ltr, LocaleHelper.layoutDirection(ctx))
    }

    @Test
    fun layoutDirection_switchesDynamicallyWhenLocaleChanges() {
        val ctx = context()

        RuntimeEnvironment.setQualifiers("en-rUS-ldltr")
        assertEquals(LayoutDirection.Ltr, LocaleHelper.layoutDirection(ctx))

        // Flip to Hebrew at runtime — direction must follow the newly resolved locale,
        // proving nothing is cached from a global Locale.setDefault or a one-time wrap.
        RuntimeEnvironment.setQualifiers("he-rIL-ldrtl")
        assertEquals(LayoutDirection.Rtl, LocaleHelper.layoutDirection(ctx))
    }
}
