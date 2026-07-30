package com.bbttvv.app.core.store

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedSupportTest {
    @Test
    fun playbackSpeedPresetsStayTvFriendlyAndCycle() {
        assertEquals(
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f),
            PLAYER_PLAYBACK_SPEED_PRESETS
        )
        assertEquals(0.75f, nextPlayerPlaybackSpeedPreset(0.5f))
        assertEquals(1.75f, nextPlayerPlaybackSpeedPreset(1.5f))
        assertEquals(0.5f, nextPlayerPlaybackSpeedPreset(2.75f))
    }

    @Test
    fun unsupportedCurrentSpeedRestartsAtFirstPreset() {
        assertEquals(0.5f, nextPlayerPlaybackSpeedPreset(1.1f))
    }
}
