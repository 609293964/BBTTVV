package com.android.purebilibili.feature.video.playback.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackTransitionPolicyTest {

    @Test
    fun holdPlaybackPosition_staysActive_whilePlayerStillReportsOldPosition() {
        assertTrue(
            shouldHoldPlaybackTransitionPosition(
                playerPositionMs = 1_200L,
                transitionPositionMs = 25_000L
            )
        )
    }

    @Test
    fun holdPlaybackPosition_releases_whenPlayerCatchesUp() {
        assertFalse(
            shouldHoldPlaybackTransitionPosition(
                playerPositionMs = 24_700L,
                transitionPositionMs = 25_000L
            )
        )
    }

    @Test
    fun displayedPlaybackPosition_prefersTransitionTarget_untilCatchUp() {
        assertEquals(
            25_000L,
            resolveDisplayedPlaybackTransitionPosition(
                playerPositionMs = 1_200L,
                transitionPositionMs = 25_000L
            )
        )
    }

    @Test
    fun displayedPlaybackPosition_returnsPlayerPosition_afterCatchUp() {
        assertEquals(
            24_700L,
            resolveDisplayedPlaybackTransitionPosition(
                playerPositionMs = 24_700L,
                transitionPositionMs = 25_000L
            )
        )
    }

    @Test
    fun displayedQuality_prefersRequestedQuality_whileSwitching() {
        assertEquals(
            80,
            resolveDisplayedQualityId(
                currentQuality = 64,
                requestedQuality = 80,
                isQualitySwitching = true
            )
        )
    }

    @Test
    fun displayedQuality_usesCurrentQuality_whenSwitchIsIdle() {
        assertEquals(
            64,
            resolveDisplayedQualityId(
                currentQuality = 64,
                requestedQuality = 80,
                isQualitySwitching = false
            )
        )
    }
}
