package com.android.purebilibili.feature.home.components.cards

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeVideoGlassBadgeStylePolicyTest {

    @Test
    fun `cover badges stay visible but become plain when glass style disabled`() {
        val policy = resolveHomeVideoGlassBadgeStylePolicy(
            showCoverGlassBadges = false,
            showInfoGlassBadges = true
        )

        assertEquals(HomeVideoBadgeStyle.PLAIN, policy.coverStyle)
        assertEquals(HomeVideoBadgeStyle.GLASS, policy.infoStyle)
    }

    @Test
    fun `info badges stay visible but become plain when glass style disabled`() {
        val policy = resolveHomeVideoGlassBadgeStylePolicy(
            showCoverGlassBadges = true,
            showInfoGlassBadges = false
        )

        assertEquals(HomeVideoBadgeStyle.GLASS, policy.coverStyle)
        assertEquals(HomeVideoBadgeStyle.PLAIN, policy.infoStyle)
    }
}
