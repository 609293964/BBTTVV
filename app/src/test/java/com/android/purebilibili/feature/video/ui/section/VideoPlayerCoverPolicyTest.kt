package com.android.purebilibili.feature.video.ui.section

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlayerCoverPolicyTest {

    @Test
    fun `without explicit force cover should not be forced`() {
        assertFalse(
            shouldForceCoverDuringReturnAnimation(
                forceCoverOnly = false
            )
        )
    }

    @Test
    fun `explicit force cover should win even when not returning`() {
        assertTrue(
            shouldForceCoverDuringReturnAnimation(
                forceCoverOnly = true
            )
        )
    }

    @Test
    fun `normal playback with first frame rendered should hide cover`() {
        assertFalse(
            shouldShowCoverImage(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false
            )
        )
    }

    @Test
    fun `forced return cover should stay visible even after first frame`() {
        assertTrue(
            shouldShowCoverImage(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = true
            )
        )
    }

    @Test
    fun `forced return cover should disable fade animation`() {
        assertTrue(shouldDisableCoverFadeAnimation(forceCoverDuringReturnAnimation = true))
        assertFalse(shouldDisableCoverFadeAnimation(forceCoverDuringReturnAnimation = false))
    }

    @Test
    fun `forced return cover should hide live player surface`() {
        assertTrue(shouldHidePlayerSurfaceDuringForcedReturn(forceCoverDuringReturnAnimation = true))
        assertFalse(shouldHidePlayerSurfaceDuringForcedReturn(forceCoverDuringReturnAnimation = false))
    }

    @Test
    fun `forced return cover should disable image crossfade`() {
        assertFalse(shouldEnableCoverImageCrossfade(forceCoverDuringReturnAnimation = true))
        assertTrue(shouldEnableCoverImageCrossfade(forceCoverDuringReturnAnimation = false))
    }

    @Test
    fun `forced return cover shared bounds requires transition and scopes`() {
        assertTrue(
            shouldEnableForcedReturnCoverSharedBounds(
                forceCoverDuringReturnAnimation = true,
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldEnableForcedReturnCoverSharedBounds(
                forceCoverDuringReturnAnimation = true,
                transitionEnabled = false,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun `playback fallback promotes first frame when ready and progressing`() {
        assertTrue(
            shouldPromoteFirstFrameByPlaybackFallback(
                isFirstFrameRendered = false,
                forceCoverDuringReturnAnimation = false,
                playbackState = androidx.media3.common.Player.STATE_READY,
                playWhenReady = true,
                currentPositionMs = 1500L,
                videoWidth = 1920,
                videoHeight = 1080
            )
        )
    }

    @Test
    fun `playback fallback does not override forced return cover`() {
        assertFalse(
            shouldPromoteFirstFrameByPlaybackFallback(
                isFirstFrameRendered = false,
                forceCoverDuringReturnAnimation = true,
                playbackState = androidx.media3.common.Player.STATE_READY,
                playWhenReady = true,
                currentPositionMs = 1500L,
                videoWidth = 1920,
                videoHeight = 1080
            )
        )
    }
}
