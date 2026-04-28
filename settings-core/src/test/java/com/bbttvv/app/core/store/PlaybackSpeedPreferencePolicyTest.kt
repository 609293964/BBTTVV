package com.bbttvv.app.core.store

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSpeedPreferencePolicyTest {

    @Test
    fun `preferred speed should use default when remember-last disabled`() {
        assertEquals(
            1.3f,
            resolvePreferredPlaybackSpeed(
                defaultSpeed = 1.3f,
                rememberLastSpeed = false,
                lastSpeed = 1.8f
            )
        )
    }

    @Test
    fun `preferred speed should use last when remember-last enabled`() {
        assertEquals(
            1.8f,
            resolvePreferredPlaybackSpeed(
                defaultSpeed = 1.3f,
                rememberLastSpeed = true,
                lastSpeed = 1.8f
            )
        )
    }

    @Test
    fun `playback speed should be clamped into supported range`() {
        assertEquals(0.1f, normalizePlaybackSpeed(0.0f))
        assertEquals(8.0f, normalizePlaybackSpeed(9.5f))
    }

    @Test
    fun `long press speed should snap to supported options`() {
        assertEquals(1.5f, normalizeLongPressSpeed(1.25f))
        assertEquals(2.0f, normalizeLongPressSpeed(2.1f))
        assertEquals(3.0f, normalizeLongPressSpeed(3.4f))
    }
}

