package com.bbttvv.app.feature.live

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackPolicyTest {
    @Test
    fun `toggle pauses when playback is requested even while buffering`() {
        assertEquals(
            LivePlaybackToggleAction.Pause,
            resolveLivePlaybackToggleAction(playWhenReady = true),
        )
        assertEquals(
            LivePlaybackToggleAction.Play,
            resolveLivePlaybackToggleAction(playWhenReady = false),
        )
    }

    @Test
    fun `playing and buffering sessions request foreground resume`() {
        assertTrue(
            shouldRequestLivePlaybackResumeAfterBackground(
                isPausedByUser = false,
                playWhenReady = true,
                isLoading = false,
                playerState = Player.STATE_READY,
                hasError = false,
            ),
        )
        assertTrue(
            shouldRequestLivePlaybackResumeAfterBackground(
                isPausedByUser = false,
                playWhenReady = true,
                isLoading = false,
                playerState = Player.STATE_BUFFERING,
                hasError = false,
            ),
        )
    }

    @Test
    fun `initial loading session resumes after returning to foreground`() {
        val resumeRequested = shouldRequestLivePlaybackResumeAfterBackground(
            isPausedByUser = false,
            playWhenReady = false,
            isLoading = true,
            playerState = Player.STATE_IDLE,
            hasError = false,
        )

        assertTrue(resumeRequested)
        assertTrue(
            shouldResumeLivePlaybackOnForeground(
                resumeRequested = resumeRequested,
                isPausedByUser = false,
                isLoading = true,
                hasPlaybackSource = false,
                playerState = Player.STATE_IDLE,
                hasError = false,
            ),
        )
    }

    @Test
    fun `user pause error and ended state do not resume`() {
        assertFalse(
            shouldRequestLivePlaybackResumeAfterBackground(
                isPausedByUser = true,
                playWhenReady = false,
                isLoading = false,
                playerState = Player.STATE_READY,
                hasError = false,
            ),
        )
        assertFalse(
            shouldResumeLivePlaybackOnForeground(
                resumeRequested = true,
                isPausedByUser = false,
                isLoading = false,
                hasPlaybackSource = true,
                playerState = Player.STATE_IDLE,
                hasError = true,
            ),
        )
        assertFalse(
            shouldResumeLivePlaybackOnForeground(
                resumeRequested = true,
                isPausedByUser = false,
                isLoading = false,
                hasPlaybackSource = true,
                playerState = Player.STATE_ENDED,
                hasError = false,
            ),
        )
    }

    @Test
    fun `stale resume request without loading or source is ignored`() {
        assertFalse(
            shouldResumeLivePlaybackOnForeground(
                resumeRequested = true,
                isPausedByUser = false,
                isLoading = false,
                hasPlaybackSource = false,
                playerState = Player.STATE_IDLE,
                hasError = false,
            ),
        )
    }
}
