package com.bbttvv.app.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAppLifecycleTest {
    @Test
    fun `foreground preserves requested playback state`() {
        assertTrue(resolvePlayWhenReadyForAppVisibility(true, isPlaybackSuppressed = false))
        assertFalse(resolvePlayWhenReadyForAppVisibility(false, isPlaybackSuppressed = false))
    }

    @Test
    fun `background or return pause latch rejects every auto play request`() {
        assertFalse(resolvePlayWhenReadyForAppVisibility(true, isPlaybackSuppressed = true))
        assertFalse(resolvePlayWhenReadyForAppVisibility(false, isPlaybackSuppressed = true))
    }
}
