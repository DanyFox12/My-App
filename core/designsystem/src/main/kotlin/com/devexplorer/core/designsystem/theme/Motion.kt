package com.devexplorer.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Centralized motion tokens so every animation in the app shares one rhythm.
 * Durations are *suggestions* — they are scaled (or zeroed) by the device's
 * [com.devexplorer.core.model.PerformanceBudget] so low-end phones stay smooth.
 *
 * The easing curves mirror Material 3's emphasized/standard sets.
 */
object Motion {
    // Durations (ms) for a Medium-tier device; the budget scales these.
    const val DURATION_SHORT = 150
    const val DURATION_MEDIUM = 250
    const val DURATION_LONG = 400

    val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}
