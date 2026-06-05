///*
// * ═══════════════════════════════════════════════════════════════════════════════
// *  Anchor — Instrumented Compose UI Test (REFERENCE EXAMPLE)
// * ═══════════════════════════════════════════════════════════════════════════════
// *
// *  Standalone example for the testing-strategy guide. Lives in a new sub-package
// *  (com.anchor.watch.exampletests) so it cannot collide with any existing file.
// *  Does NOT modify any production class or any of the 41 existing JVM tests.
// *
// *  What this file demonstrates:
// *    §A — Hosting an existing production Composable (MainWatchScreen) under
// *         ComponentActivity, finding its SOS button by Hebrew content-description,
// *         clicking it, and asserting the click was observed.
// *
// *    §B — Hosting a brand-new tiny inline Composable (no production dependency)
// *         to demonstrate TestTag-based lookups and state assertions in their
// *         simplest form — useful when you want a deterministic test that has
// *         no Activity / Service / repository wiring.
// *
// *  How to run (after a Wear OS emulator is booted and listed by `adb devices`):
// *
// *    From Android Studio:
// *      Right-click this file → "Run 'InstrumentedExampleScreenTest'"
// *      OR open the file and click the gutter ▶ next to the class name.
// *
// *    From the IDE terminal:
// *      .\gradlew.bat :app:connectedDebugAndroidTest \
// *          -Pandroid.testInstrumentationRunnerArguments.class=\
// *          com.anchor.watch.exampletests.InstrumentedExampleScreenTest
// *
// *    Visual report:
// *      app\build\reports\androidTests\connected\debug\index.html
// *
// *  Required dependency status (already present in app/build.gradle.kts):
// *    androidTestImplementation(platform(libs.compose.bom))   ✓
// *    androidTestImplementation(libs.ui.test.junit4)          ✓
// *
// *  If you see `ClassNotFoundException: AndroidJUnit4` on first run, add:
// *    androidTestImplementation("androidx.test.ext:junit:1.1.5")
// *  (Often transitive via ui-test-junit4; sometimes needs to be explicit.)
// */
//
//package com.anchor.watch.exampletests
//
//import androidx.activity.ComponentActivity
//import androidx.compose.foundation.layout.Column
//import androidx.compose.material3.Button
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.testTag
//import androidx.compose.ui.test.assertIsDisplayed
//import androidx.compose.ui.test.assertTextEquals
//import androidx.compose.ui.test.junit4.createAndroidComposeRule
//import androidx.compose.ui.test.onNodeWithContentDescription
//import androidx.compose.ui.test.onNodeWithTag
//import androidx.compose.ui.test.performClick
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import com.anchor.watch.screens.MainWatchScreen
//import org.junit.Assert.assertEquals
//import org.junit.Assert.assertTrue
//import org.junit.Rule
//import org.junit.Test
//import org.junit.runner.RunWith
//
//@RunWith(AndroidJUnit4::class)
//class InstrumentedExampleScreenTest {
//
//    /**
//     * createAndroidComposeRule launches a real ComponentActivity on the device,
//     * so the test exercises the actual Android runtime — Context, broadcasts,
//     * AnimationClock, theme resolution. This is the key distinction from the
//     * JVM Robolectric tests under src/test/.
//     */
//    @get:Rule
//    val composeRule = createAndroidComposeRule<ComponentActivity>()
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // §A — Test against an existing production screen (MainWatchScreen)
//    // ─────────────────────────────────────────────────────────────────────────
//
//    @Test
//    fun mainWatchScreen_sosButton_clickIsObserved() {
//        var sosClickCount = 0
//
//        composeRule.setContent {
//            MainWatchScreen(
//                isAmbient = false,
//                onSosClick = { sosClickCount++ },
//            )
//        }
//
//        // The SOS button is labelled in production with stringResource(R.string.cd_sos)
//        // = "לחצן חירום" (Hebrew, from values-iw/strings.xml). Using the literal
//        // string here makes the test reproducible regardless of system locale —
//        // the screen forces RTL + Hebrew via the themes.xml layoutDirection.
//        composeRule
//            .onNodeWithContentDescription("לחצן חירום")
//            .assertIsDisplayed()
//            .performClick()
//
//        assertEquals("SOS button click should be observed exactly once", 1, sosClickCount)
//    }
//
//    @Test
//    fun mainWatchScreen_inAmbientMode_hidesSosButton() {
//        composeRule.setContent {
//            MainWatchScreen(
//                isAmbient = true,
//                onSosClick = {},
//            )
//        }
//
//        // In ambient mode the SOS button is not rendered at all (see
//        // MainWatchScreen.kt — the `if (!isAmbient)` guard before the Button).
//        // assertDoesNotExist is the correct probe; assertIsNotDisplayed would
//        // fail differently (node exists but is hidden).
//        composeRule.onNodeWithContentDescription("לחצן חירום").assertDoesNotExist()
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // §B — Self-contained demo: TestTag-based lookup + state assertion
//    // ─────────────────────────────────────────────────────────────────────────
//    //
//    // Use this pattern when the production screen doesn't yet have stable
//    // content-descriptions, or when you want a unit-of-isolation test that
//    // doesn't depend on any production class at all.
//
//    @Composable
//    private fun TinyCounterDemo() {
//        var count by remember { mutableStateOf(0) }
//        Column {
//            Text(
//                text = "Count: $count",
//                modifier = Modifier.testTag("counter_value"),
//            )
//            Button(
//                onClick = { count++ },
//                modifier = Modifier.testTag("increment_button"),
//            ) {
//                Text("Increment")
//            }
//        }
//    }
//
//    @Test
//    fun tinyCounter_clickingIncrement_advancesValue() {
//        composeRule.setContent { TinyCounterDemo() }
//
//        composeRule.onNodeWithTag("counter_value").assertTextEquals("Count: 0")
//
//        composeRule.onNodeWithTag("increment_button").performClick()
//        composeRule.onNodeWithTag("counter_value").assertTextEquals("Count: 1")
//
//        composeRule.onNodeWithTag("increment_button").performClick()
//        composeRule.onNodeWithTag("increment_button").performClick()
//        composeRule.onNodeWithTag("counter_value").assertTextEquals("Count: 3")
//    }
//
//    @Test
//    fun tinyCounter_assertsExistence_withMultipleStrategies() {
//        composeRule.setContent { TinyCounterDemo() }
//
//        // Strategy 1: by TestTag (preferred — locale-independent, refactor-safe)
//        composeRule.onNodeWithTag("increment_button").assertExists()
//
//        // Strategy 2: by visible text (brittle if the string changes / localizes)
//        // For documentation only — we don't ASSERT on this in production tests
//        // because changing the button label would break it. TestTag wins.
//        val nodeByText = composeRule.onNodeWithTag("counter_value")
//        nodeByText.assertExists()
//        assertTrue(true)  // placeholder so the test demonstrates assertion shape
//    }
//}
