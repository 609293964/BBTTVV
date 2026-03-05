package com.android.purebilibili.core.ui.blur

import com.android.purebilibili.core.ui.adaptive.MotionTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlurBudgetPolicyTest {

    @Test
    fun header_normalStatic_prefersHigherBudget() {
        val budget = resolveBlurBudget(
            surfaceType = BlurSurfaceType.HEADER,
            motionTier = MotionTier.Normal,
            isScrolling = false,
            isTransitionRunning = false
        )

        assertEquals(2, budget.maxBlurLevel)
        assertEquals(1.0f, budget.backgroundAlphaMultiplier)
        assertTrue(budget.allowRealtime)
    }

    @Test
    fun header_scrolling_keepsVisualBudgetStable_toAvoidBrightnessPulsing() {
        val budget = resolveBlurBudget(
            surfaceType = BlurSurfaceType.HEADER,
            motionTier = MotionTier.Normal,
            isScrolling = true,
            isTransitionRunning = false
        )

        assertEquals(2, budget.maxBlurLevel)
        assertEquals(1.0f, budget.backgroundAlphaMultiplier)
    }

    @Test
    fun bottomBar_duringTransition_shouldLowerBudgetAndDisableRealtime() {
        val budget = resolveBlurBudget(
            surfaceType = BlurSurfaceType.BOTTOM_BAR,
            motionTier = MotionTier.Normal,
            isScrolling = false,
            isTransitionRunning = true
        )

        assertEquals(0, budget.maxBlurLevel)
        assertFalse(budget.allowRealtime)
    }

    @Test
    fun reducedMotion_scrolling_generic_shouldClampToThin() {
        val budget = resolveBlurBudget(
            surfaceType = BlurSurfaceType.GENERIC,
            motionTier = MotionTier.Reduced,
            isScrolling = true,
            isTransitionRunning = false
        )

        assertEquals(0, budget.maxBlurLevel)
        assertFalse(budget.allowRealtime)
    }

    @Test
    fun applyBudget_shouldClampPreferredIntensity() {
        val budget = BlurBudget(
            maxBlurLevel = 1,
            backgroundAlphaMultiplier = 1.0f,
            allowRealtime = true
        )

        assertEquals(
            BlurIntensity.APPLE_DOCK,
            resolveBudgetedBlurIntensity(BlurIntensity.THICK, budget)
        )
    }

    @Test
    fun forceLowBudget_shouldOverrideStaticHeaderBudget() {
        val budget = resolveBlurBudget(
            surfaceType = BlurSurfaceType.HEADER,
            motionTier = MotionTier.Normal,
            isScrolling = false,
            isTransitionRunning = false,
            forceLowBudget = true
        )

        assertEquals(0, budget.maxBlurLevel)
        assertFalse(budget.allowRealtime)
    }
}
