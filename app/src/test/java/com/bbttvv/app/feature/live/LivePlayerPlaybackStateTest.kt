package com.bbttvv.app.feature.live

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlayerPlaybackStateTest {
    @Test
    fun `playback remains active while a requested live stream is buffering`() {
        val state = LivePlayerPlaybackState(
            isPlaying = false,
            playWhenReady = true,
            isBuffering = true,
            playerState = Player.STATE_BUFFERING,
        )

        assertTrue(state.isPlaybackActive)
    }

    @Test
    fun `paused live stream does not keep playback active`() {
        val state = LivePlayerPlaybackState(
            isPlaying = false,
            playWhenReady = false,
            playerState = Player.STATE_READY,
        )

        assertFalse(state.isPlaybackActive)
    }

    @Test
    fun `idle and ended live streams do not keep playback active`() {
        assertFalse(
            LivePlayerPlaybackState(
                playWhenReady = true,
                playerState = Player.STATE_IDLE,
            ).isPlaybackActive,
        )
        assertFalse(
            LivePlayerPlaybackState(
                playWhenReady = true,
                playerState = Player.STATE_ENDED,
            ).isPlaybackActive,
        )
    }
}
