package com.android.purebilibili.feature.home.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomBarIndicatorPolicyTest {

    @Test
    fun `five or more items stays close to top floating indicator family`() {
        val policy = resolveBottomBarIndicatorPolicy(itemCount = 5)
        val topTuning = resolveTopTabVisualTuning()

        assertEquals(topTuning.floatingIndicatorWidthMultiplier + 0.02f, policy.widthMultiplier)
        assertEquals(topTuning.floatingIndicatorMinWidthDp + 2f, policy.minWidthDp)
        assertEquals(topTuning.floatingIndicatorMaxWidthDp + 2f, policy.maxWidthDp)
        assertEquals(true, policy.clampToBounds)
        assertEquals(topTuning.floatingIndicatorMaxWidthToItemRatio + 0.02f, policy.maxWidthToItemRatio)
    }

    @Test
    fun `four items is only slightly wider than five item geometry`() {
        val policy = resolveBottomBarIndicatorPolicy(itemCount = 4)
        val topTuning = resolveTopTabVisualTuning()

        assertEquals(topTuning.floatingIndicatorWidthMultiplier + 0.04f, policy.widthMultiplier)
        assertEquals(topTuning.floatingIndicatorMinWidthDp + 4f, policy.minWidthDp)
        assertEquals(topTuning.floatingIndicatorMaxWidthDp + 4f, policy.maxWidthDp)
        assertEquals(topTuning.floatingIndicatorMaxWidthToItemRatio + 0.04f, policy.maxWidthToItemRatio)
        assertEquals(true, policy.clampToBounds)
    }

    @Test
    fun `icon and text mode with five items uses flatter indicator height on phone`() {
        assertEquals(
            50f,
            resolveBottomIndicatorHeightDp(
                labelMode = 0,
                isTablet = false,
                itemCount = 5
            )
        )
    }
}
