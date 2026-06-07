package com.anchor.watch.services

import com.anchor.watch.utils.FallAlertController
import com.anchor.watch.utils.FallDetector
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
class FallDetectionServiceTest {

    private class FakeClock {
        var t: Long = 0L
        val getter: () -> Long = { t }
    }

    private fun newDetector(clock: FakeClock) = FallDetector(
        fallThresholdG = 2.5f,
        stillnessThresholdG = 0.3f,
        impactWindowMs = 2000L,
        clock = clock.getter,
    )

    @Test
    fun highGSpikeAlone_doesNotDetect_andWindowExpires() {
        val c = FakeClock()
        val d = newDetector(c)
        c.t = 1000L
        assertFalse(d.onSample(3.0f))
        assertTrue(d.isWindowOpen)

        c.t = 1500L
        assertFalse(d.onSample(2.7f))
        c.t = 3100L
        assertFalse(d.onSample(0.2f))
        assertFalse(d.isWindowOpen)
    }

    @Test
    fun highGSpike_thenStillnessWithinWindow_detectsFall() {
        val c = FakeClock()
        val d = newDetector(c)
        c.t = 1000L
        d.onSample(3.0f)
        c.t = 2500L
        assertTrue(d.onSample(0.2f))
        assertFalse(d.isWindowOpen)
    }

    @Test
    fun highGSpike_thenStillnessOutsideWindow_doesNotDetect() {
        val c = FakeClock()
        val d = newDetector(c)
        c.t = 1000L
        d.onSample(3.0f)
        c.t = 3500L
        assertFalse(d.onSample(0.2f))
        assertFalse(d.isWindowOpen)
    }

    @Test
    fun stillnessWithoutPriorSpike_doesNotDetect() {
        val d = newDetector(FakeClock())
        assertFalse(d.onSample(0.1f))
        assertFalse(d.onSample(0.05f))
        assertFalse(d.onSample(0.2f))
    }

    @Test
    fun secondSpikeDuringOpenWindow_doesNotResetStartTime() {
        val c = FakeClock()
        val d = newDetector(c)
        c.t = 1000L
        d.onSample(3.0f)
        c.t = 1800L
        d.onSample(3.5f)
        c.t = 2900L
        assertTrue(d.onSample(0.2f))
    }

    @Test
    fun reset_clearsState() {
        val c = FakeClock()
        val d = newDetector(c)
        c.t = 1000L
        d.onSample(3.0f)
        assertTrue(d.isWindowOpen)
        d.reset()
        assertFalse(d.isWindowOpen)
        c.t = 1100L
        assertFalse(d.onSample(0.2f))
    }

    @Test
    fun fallAlertController_cancelWithinGrace_doesNotTrigger() = runTest {
        val triggered = AtomicInteger()
        val cancelled = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val ctrl = FallAlertController(
            graceMs = 30_000L,
            tickIntervalMs = 1000L,
            onTrigger = { triggered.incrementAndGet() },
            onCancel = { cancelled.incrementAndGet() },
        )
        ctrl.start(scope)
        scope.testScheduler.advanceTimeBy(5_000L)
        ctrl.cancel()
        scope.testScheduler.advanceUntilIdle()

        assertEquals(0, triggered.get())
        assertEquals(1, cancelled.get())
        assertEquals(FallAlertController.State.Cancelled, ctrl.state.value)
    }

    @Test
    fun fallAlertController_noCancel_triggersAfterGrace() = runTest {
        val triggered = AtomicInteger()
        val cancelled = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val ctrl = FallAlertController(
            graceMs = 30_000L,
            tickIntervalMs = 1000L,
            onTrigger = { triggered.incrementAndGet() },
            onCancel = { cancelled.incrementAndGet() },
        )
        ctrl.start(scope)
        scope.testScheduler.advanceUntilIdle()

        assertEquals(1, triggered.get())
        assertEquals(0, cancelled.get())
        assertEquals(FallAlertController.State.Triggered, ctrl.state.value)
    }

    @Test
    fun fallAlertController_cancelAfterTrigger_isNoOp() = runTest {
        val triggered = AtomicInteger()
        val cancelled = AtomicInteger()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val ctrl = FallAlertController(
            graceMs = 1_000L,
            tickIntervalMs = 100L,
            onTrigger = { triggered.incrementAndGet() },
            onCancel = { cancelled.incrementAndGet() },
        )
        ctrl.start(scope)
        scope.testScheduler.advanceUntilIdle()
        ctrl.cancel()

        assertEquals(1, triggered.get())
        assertEquals(0, cancelled.get())
        assertEquals(FallAlertController.State.Triggered, ctrl.state.value)
    }
}
