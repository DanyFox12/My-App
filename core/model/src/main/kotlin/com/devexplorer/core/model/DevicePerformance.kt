package com.devexplorer.core.model

/**
 * A coarse classification of the device's capability, used to scale visual
 * richness and work intensity so the app feels smooth on a budget phone and
 * lush on a flagship — without ever crashing on the weak one.
 *
 * The actual measurement (RAM class, `ActivityManager.isLowRamDevice`, API
 * level, core count) happens in the Android layer; this enum is the
 * framework-free result the UI and use-cases reason about.
 *
 * See [PerformanceBudget] for the concrete knobs each tier turns.
 */
enum class DevicePerformanceTier {
    /** Low-RAM / older devices: minimize animation, avoid blur, smaller caches. */
    Low,

    /** The mainstream majority: full standard experience. */
    Medium,

    /** High-RAM / recent devices: richer motion and larger caches are safe. */
    High;

    val budget: PerformanceBudget
        get() = when (this) {
            Low -> PerformanceBudget(
                richMotionEnabled = false,
                blurEffectsEnabled = false,
                crossfadeMillis = 0,
                maxAnalysisCacheEntries = 32,
                listPrefetchDistance = 1,
            )
            Medium -> PerformanceBudget(
                richMotionEnabled = true,
                blurEffectsEnabled = false,
                crossfadeMillis = 180,
                maxAnalysisCacheEntries = 128,
                listPrefetchDistance = 4,
            )
            High -> PerformanceBudget(
                richMotionEnabled = true,
                blurEffectsEnabled = true,
                crossfadeMillis = 250,
                maxAnalysisCacheEntries = 512,
                listPrefetchDistance = 8,
            )
        }
}

/**
 * Concrete, tier-derived settings the UI and data layers read instead of
 * branching on the tier directly. Keeping the knobs in one place makes the
 * performance policy auditable and testable.
 */
data class PerformanceBudget(
    /** Whether non-essential, expensive transitions/animations should run. */
    val richMotionEnabled: Boolean,
    /** Whether RenderEffect blur (API 31+, GPU-heavy) may be used. */
    val blurEffectsEnabled: Boolean,
    /** Crossfade duration for content swaps; 0 means snap (no animation). */
    val crossfadeMillis: Int,
    /** Upper bound on in-memory analysis cache entries. */
    val maxAnalysisCacheEntries: Int,
    /** How many items a LazyColumn should prefetch ahead of the viewport. */
    val listPrefetchDistance: Int,
)
