package com.anchor.watch.utils

object FallDetectionConstants {
    // Magnitude (g) above which a sample is treated as fall-impact candidate.
    // 2.5g approximates a hard impact (or large free-fall + impact transient).
    // Starting value per PRD 2.5.1; tune via on-watch trials before launch.
    const val FALL_THRESHOLD_G: Float = 2.5f

    // Magnitude (g) below which the user is considered lying still.
    // Body at rest reads ~0 g of net motion (gravity already factored out).
    const val STILLNESS_THRESHOLD_G: Float = 0.3f

    // Time after the impact spike within which stillness must arrive
    // for the sample sequence to count as a fall.
    const val IMPACT_WINDOW_MS: Long = 2_000L

    // How long the user has to press "אני בסדר" before family is alerted.
    const val GRACE_PERIOD_MS: Long = 30_000L
}
