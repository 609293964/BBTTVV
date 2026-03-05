package com.android.purebilibili.feature.home.components.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCardScrollLiteVisualPolicyTest {

    @Test
    fun `normal mode keeps full elegant card visuals`() {
        val policy = resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = false,
            compactStatsOnCover = true
        )

        assertEquals(1f, policy.coverShadowElevationDp, 0.0001f)
        assertTrue(policy.showCoverGradientMask)
        assertTrue(policy.showHistoryProgressBar)
        assertTrue(policy.showCompactStatsOnCover)
        assertFalse(policy.showSecondaryStatsRow)
    }

    @Test
    fun `scroll lite mode keeps stats and a perceivable cover shadow`() {
        val policy = resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = true,
            compactStatsOnCover = true
        )

        assertEquals(1f, policy.coverShadowElevationDp, 0.0001f)
        assertFalse(policy.showCoverGradientMask)
        assertFalse(policy.showHistoryProgressBar)
        assertTrue(policy.showCompactStatsOnCover)
        assertFalse(policy.showSecondaryStatsRow)
    }

    @Test
    fun `scroll lite mode keeps secondary row when cover stats are disabled`() {
        val policy = resolveVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = true,
            compactStatsOnCover = false
        )

        assertFalse(policy.showCompactStatsOnCover)
        assertTrue(policy.showSecondaryStatsRow)
    }

    @Test
    fun `normal mode keeps story card secondary stats row`() {
        val policy = resolveStoryVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = false
        )

        assertEquals(6f, policy.coverShadowElevationDp, 0.0001f)
        assertTrue(policy.showSecondaryStatsRow)
    }

    @Test
    fun `scroll lite mode simplifies story card shadows but keeps stats`() {
        val policy = resolveStoryVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = true
        )

        assertEquals(3f, policy.coverShadowElevationDp, 0.0001f)
        assertTrue(policy.showSecondaryStatsRow)
    }
}
