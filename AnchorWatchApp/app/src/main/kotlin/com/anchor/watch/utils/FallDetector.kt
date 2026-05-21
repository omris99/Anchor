package com.anchor.watch.utils

class FallDetector(
    val fallThresholdG: Float = FallDetectionConstants.FALL_THRESHOLD_G,
    val stillnessThresholdG: Float = FallDetectionConstants.STILLNESS_THRESHOLD_G,
    val impactWindowMs: Long = FallDetectionConstants.IMPACT_WINDOW_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var inFallWindow: Boolean = false
    private var fallWindowStartMs: Long = 0L

    val isWindowOpen: Boolean get() = inFallWindow

    fun onSample(magnitudeG: Float): Boolean {
        val now = clock()

        if (inFallWindow) {
            val elapsed = now - fallWindowStartMs
            when {
                magnitudeG < stillnessThresholdG && elapsed in 0..impactWindowMs -> {
                    inFallWindow = false
                    return true
                }
                elapsed > impactWindowMs -> {
                    inFallWindow = false
                }
                else -> return false
            }
        }

        if (!inFallWindow && magnitudeG > fallThresholdG) {
            inFallWindow = true
            fallWindowStartMs = now
        }
        return false
    }

    fun reset() {
        inFallWindow = false
        fallWindowStartMs = 0L
    }
}
