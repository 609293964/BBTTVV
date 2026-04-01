package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoProgressBarPolicyTest {

    @Test
    fun seekableDuration_usesFallbackWhenPlaybackDurationIsUnset() {
        assertEquals(
            120_000L,
            resolveSeekableDurationMs(
                playbackDurationMs = 0L,
                fallbackDurationMs = 120_000L
            )
        )
    }

    @Test
    fun seekableDuration_prefersPlaybackDurationWhenItIsValid() {
        assertEquals(
            95_000L,
            resolveSeekableDurationMs(
                playbackDurationMs = 95_000L,
                fallbackDurationMs = 120_000L
            )
        )
    }

    @Test
    fun displayedProgress_prefersPlaybackTransitionTarget_untilPlayerCatchesUp() {
        assertEquals(
            PlayerProgress(current = 25_000L, duration = 120_000L, buffered = 30_000L),
            resolveDisplayedPlayerProgress(
                progress = PlayerProgress(current = 1_200L, duration = 120_000L, buffered = 30_000L),
                previewPositionMs = null,
                previewActive = false,
                playbackTransitionPositionMs = 25_000L
            )
        )
    }

    @Test
    fun dragging_progress_uses_live_drag_value() {
        assertEquals(
            0.68f,
            resolveVideoProgressBarDisplayProgress(
                progress = 0.12f,
                dragProgress = 0.68f,
                isDragging = true,
                pendingSettledProgress = null
            )
        )
    }

    @Test
    fun settled_progress_holds_after_release_until_external_progress_catches_up() {
        assertEquals(
            0.68f,
            resolveVideoProgressBarDisplayProgress(
                progress = 0.12f,
                dragProgress = 0.68f,
                isDragging = false,
                pendingSettledProgress = 0.68f
            )
        )
    }

    @Test
    fun settled_progress_clears_when_external_progress_matches_target() {
        assertFalse(
            shouldHoldVideoProgressBarSettledProgress(
                progress = 0.679f,
                pendingSettledProgress = 0.68f
            )
        )
    }

    @Test
    fun settled_progress_stays_when_external_progress_is_stale() {
        assertTrue(
            shouldHoldVideoProgressBarSettledProgress(
                progress = 0.12f,
                pendingSettledProgress = 0.68f
            )
        )
    }
}
