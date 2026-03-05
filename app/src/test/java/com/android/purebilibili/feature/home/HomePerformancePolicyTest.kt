package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomePerformancePolicyTest {

    @Test
    fun keepsHomeVisualSettingsWhenDataSaverOff() {
        val config = resolveHomePerformanceConfig(
            headerBlurEnabled = true,
            bottomBarBlurEnabled = false,
            liquidGlassEnabled = true,
            cardAnimationEnabled = false,
            cardTransitionEnabled = true,
            isDataSaverActive = false,
            smartVisualGuardEnabled = false,
            normalPreloadAheadCount = 5
        )

        assertTrue(config.headerBlurEnabled)
        assertFalse(config.bottomBarBlurEnabled)
        assertTrue(config.liquidGlassEnabled)
        assertFalse(config.cardAnimationEnabled)
        assertTrue(config.cardTransitionEnabled)
        assertFalse(config.isDataSaverActive)
        assertTrue(config.preloadAheadCount == 5)
    }

    @Test
    fun dataSaverDisablesPreloadAhead() {
        val config = resolveHomePerformanceConfig(
            headerBlurEnabled = true,
            bottomBarBlurEnabled = true,
            liquidGlassEnabled = true,
            cardAnimationEnabled = true,
            cardTransitionEnabled = true,
            isDataSaverActive = true,
            smartVisualGuardEnabled = false,
            normalPreloadAheadCount = 5
        )

        assertTrue(config.isDataSaverActive)
        assertTrue(config.preloadAheadCount == 0)
    }

    @Test
    fun smartGuardFlag_noLongerAffectsHomePerformanceConfig() {
        val config = resolveHomePerformanceConfig(
            headerBlurEnabled = true,
            bottomBarBlurEnabled = true,
            liquidGlassEnabled = true,
            cardAnimationEnabled = true,
            cardTransitionEnabled = true,
            isDataSaverActive = false,
            smartVisualGuardEnabled = true,
            normalPreloadAheadCount = 5
        )

        assertFalse(config.isDataSaverActive)
        assertTrue(config.liquidGlassEnabled)
        assertTrue(config.preloadAheadCount == 5)
    }
}
