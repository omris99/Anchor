package com.anchor.watch.utils

import android.util.Log

class FallDetector(
    val fallThresholdG: Float = FallDetectionConstants.FALL_THRESHOLD_G,
    val stillnessThresholdG: Float = FallDetectionConstants.STILLNESS_THRESHOLD_G,
    val impactWindowMs: Long = FallDetectionConstants.IMPACT_WINDOW_MS,
    val stillnessDurationMs: Long = FallDetectionConstants.STILLNESS_DURATION_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var inFallWindow: Boolean = false
    private var fallWindowStartMs: Long = 0L
    private var lastSampleMs: Long = 0L
    private var cumulativeStillMs: Long = 0L
    private var inStillPhase: Boolean = false

    val isWindowOpen: Boolean get() = inFallWindow

    fun onSample(magnitudeG: Float): Boolean {
        val now = clock()

        if (inFallWindow) {
            val elapsed = now - fallWindowStartMs
            if (elapsed > impactWindowMs) {
                Log.d(TAG, "fall window expired after ${elapsed}ms (cumulative still: ${cumulativeStillMs}ms) — resetting")
                reset()
                return false
            }

            // Accumulate still time rather than requiring consecutive stillness.
            // Soft surfaces (bed, sofa) cause brief vibration spikes that would
            // endlessly reset a consecutive timer, even though the person is lying still.
            val dt = now - lastSampleMs
            if (magnitudeG < stillnessThresholdG) {
                cumulativeStillMs += dt
                if (!inStillPhase) {
                    inStillPhase = true
                    Log.d(TAG, "stillness phase: elapsed=${elapsed}ms, cumulative=${cumulativeStillMs}ms, magnitude=${magnitudeG}g")
                }
                if (cumulativeStillMs >= stillnessDurationMs) {
                    Log.i(TAG, "FALL CONFIRMED: ${cumulativeStillMs}ms cumulative stillness over ${elapsed}ms (last magnitude=${magnitudeG}g)")
                    reset()
                    return true
                }
            } else {
                if (inStillPhase) {
                    inStillPhase = false
                    Log.d(TAG, "motion spike: elapsed=${elapsed}ms, cumulative=${cumulativeStillMs}ms, magnitude=${magnitudeG}g")
                }
            }
            lastSampleMs = now
            return false
        }

        if (magnitudeG > fallThresholdG) {
            Log.i(TAG, "IMPACT detected: ${magnitudeG}g > threshold ${fallThresholdG}g — opening fall window")
            inFallWindow = true
            fallWindowStartMs = now
            lastSampleMs = now
            cumulativeStillMs = 0L
            inStillPhase = false
        }
        return false
    }

    fun reset() {
        inFallWindow = false
        fallWindowStartMs = 0L
        lastSampleMs = 0L
        cumulativeStillMs = 0L
        inStillPhase = false
    }

    companion object {
        private const val TAG = "FallDetector"
    }
}
