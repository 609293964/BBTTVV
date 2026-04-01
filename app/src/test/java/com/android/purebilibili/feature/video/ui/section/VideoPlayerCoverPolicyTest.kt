package com.android.purebilibili.feature.video.ui.section

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlayerCoverPolicyTest {

    @Test
    fun `entry cover should win when both entry and detail covers exist`() {
        assertEquals(
            "https://img.test/entry.jpg",
            resolvePreferredVideoCoverUrl(
                entryCoverUrl = "https://img.test/entry.jpg",
                detailCoverUrl = "https://img.test/detail.jpg"
            )
        )
    }

    @Test
    fun `detail cover should be used when entry cover is blank`() {
        assertEquals(
            "https://img.test/detail.jpg",
            resolvePreferredVideoCoverUrl(
                entryCoverUrl = "   ",
                detailCoverUrl = "https://img.test/detail.jpg"
            )
        )
    }

    @Test
    fun `preferred cover should stay blank when both sources are blank`() {
        assertEquals(
            "",
            resolvePreferredVideoCoverUrl(
                entryCoverUrl = "",
                detailCoverUrl = "   "
            )
        )
    }

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
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = true
            )
        )
    }

    @Test
    fun `forced return cover should stay visible even after first frame`() {
        assertTrue(
            shouldShowCoverImage(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = true,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = true
            )
        )
    }

    @Test
    fun `manual start entry should keep cover after first frame while paused at start`() {
        assertTrue(
            shouldShowCoverImage(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = true,
                hasStartedSmoothReveal = true
            )
        )
    }

    @Test
    fun `first frame should keep cover visible until smooth reveal starts`() {
        assertTrue(
            shouldShowCoverImage(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = false
            )
        )
    }

    @Test
    fun `manual start pending should show dedicated play button`() {
        assertTrue(shouldShowManualStartPlayButton(shouldKeepCoverForManualStart = true))
        assertFalse(shouldShowManualStartPlayButton(shouldKeepCoverForManualStart = false))
    }

    @Test
    fun `manual start overlay should be clickable only for manual start state`() {
        assertTrue(shouldEnableManualStartCoverOverlay(shouldKeepCoverForManualStart = true))
        assertFalse(shouldEnableManualStartCoverOverlay(shouldKeepCoverForManualStart = false))
    }

    @Test
    fun `manual start cover should fill player viewport instead of shared 16 by 9 card`() {
        assertTrue(
            shouldFillPlayerViewportForManualStartCover(
                shouldKeepCoverForManualStart = true,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertFalse(
            shouldFillPlayerViewportForManualStartCover(
                shouldKeepCoverForManualStart = false,
                forceCoverDuringReturnAnimation = false
            )
        )
        assertFalse(
            shouldFillPlayerViewportForManualStartCover(
                shouldKeepCoverForManualStart = true,
                forceCoverDuringReturnAnimation = true
            )
        )
    }

    @Test
    fun `manual start play button should use pili plus style layout`() {
        val spec = resolveManualStartPlayButtonLayoutSpec()

        assertEquals(ManualStartPlayButtonAnchor.BottomEnd, spec.anchor)
        assertEquals(24, spec.endPaddingDp)
        assertEquals(72, spec.iconWidthDp)
        assertEquals(60, spec.iconHeightDp)
        assertFalse(spec.showCoverScrim)
        assertFalse(spec.showTopDecorations)
    }

    @Test
    fun `paused at start should count as manual start cover hold only before playback intent`() {
        assertTrue(
            shouldKeepCoverForManualStart(
                playWhenReady = false,
                currentPositionMs = 0L
            )
        )
        assertFalse(
            shouldKeepCoverForManualStart(
                playWhenReady = true,
                currentPositionMs = 0L
            )
        )
        assertFalse(
            shouldKeepCoverForManualStart(
                playWhenReady = false,
                currentPositionMs = 1_000L
            )
        )
    }

    @Test
    fun `forced return cover should disable fade animation`() {
        assertTrue(shouldDisableCoverFadeAnimation(forceCoverDuringReturnAnimation = true))
        assertFalse(shouldDisableCoverFadeAnimation(forceCoverDuringReturnAnimation = false))
    }

    @Test
    fun `cover motion spec disables fade when forced return is active`() {
        val spec = resolveVideoPlayerCoverMotionSpec(forceCoverDuringReturnAnimation = true)

        assertFalse(spec.shouldAnimateFade)
        assertEquals(200, spec.enterFadeDurationMillis)
        assertEquals(300, spec.exitFadeDurationMillis)
    }

    @Test
    fun `cover motion spec keeps fade animation during normal playback`() {
        val spec = resolveVideoPlayerCoverMotionSpec(forceCoverDuringReturnAnimation = false)

        assertTrue(spec.shouldAnimateFade)
        assertEquals(200, spec.enterFadeDurationMillis)
        assertEquals(300, spec.exitFadeDurationMillis)
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
                hasAnimatedVisibilityScope = true,
                sourceRoute = "home"
            )
        )
        assertFalse(
            shouldEnableForcedReturnCoverSharedBounds(
                forceCoverDuringReturnAnimation = true,
                transitionEnabled = false,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true,
                sourceRoute = "home"
            )
        )
    }

    @Test
    fun `forced return cover shared bounds disabled for non-card sources`() {
        assertFalse(
            shouldEnableForcedReturnCoverSharedBounds(
                forceCoverDuringReturnAnimation = true,
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true,
                sourceRoute = "settings"
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

    @Test
    fun `smooth cover reveal starts only after first frame in normal playback`() {
        assertTrue(
            shouldStartSmoothCoverReveal(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false
            )
        )
        assertFalse(
            shouldStartSmoothCoverReveal(
                isFirstFrameRendered = false,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false
            )
        )
        assertFalse(
            shouldStartSmoothCoverReveal(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = true,
                shouldKeepCoverForManualStart = false
            )
        )
        assertFalse(
            shouldStartSmoothCoverReveal(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = true
            )
        )
    }

    @Test
    fun `smooth cover reveal hold spec should use short settle delay`() {
        val spec = resolveVideoPlayerRevealMotionSpec()

        assertEquals(96, spec.coverRevealHoldDelayMillis)
        assertEquals(220, spec.surfaceRevealDurationMillis)
    }
}
