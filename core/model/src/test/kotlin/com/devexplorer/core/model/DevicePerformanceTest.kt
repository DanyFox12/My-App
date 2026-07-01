package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePerformanceTest {

    @Test
    fun low_tier_disables_motion_and_snaps() {
        val budget = DevicePerformanceTier.Low.budget
        assertFalse(budget.richMotionEnabled)
        assertFalse(budget.blurEffectsEnabled)
        assertEquals(0, budget.crossfadeMillis)
    }

    @Test
    fun high_tier_enables_rich_effects() {
        val budget = DevicePerformanceTier.High.budget
        assertTrue(budget.richMotionEnabled)
        assertTrue(budget.blurEffectsEnabled)
    }

    @Test
    fun cache_and_prefetch_grow_with_tier() {
        assertTrue(
            DevicePerformanceTier.Low.budget.maxAnalysisCacheEntries <
                DevicePerformanceTier.Medium.budget.maxAnalysisCacheEntries,
        )
        assertTrue(
            DevicePerformanceTier.Medium.budget.listPrefetchDistance <
                DevicePerformanceTier.High.budget.listPrefetchDistance,
        )
    }
}
