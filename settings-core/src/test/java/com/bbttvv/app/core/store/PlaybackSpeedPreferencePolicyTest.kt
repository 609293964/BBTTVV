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

    @Test
    fun `player volume calibration defaults to normal`() {
        assertEquals(1.0f, DEFAULT_PLAYER_VOLUME_CALIBRATION_SCALE)
        assertEquals("正常", formatPlayerVolumeCalibrationLabel(DEFAULT_PLAYER_VOLUME_CALIBRATION_SCALE))
    }

    @Test
    fun `player volume calibration rejects unsupported values`() {
        assertEquals(1.0f, normalizePlayerVolumeCalibrationScale(Float.NaN))
        assertEquals(1.0f, normalizePlayerVolumeCalibrationScale(2.5f))
        assertEquals(1.0f, normalizePlayerVolumeCalibrationScale(-0.1f))
    }

    @Test
    fun `player volume calibration accepts values within 0 to 200 percent`() {
        assertEquals(0.0f, normalizePlayerVolumeCalibrationScale(0.0f))
        assertEquals(1.5f, normalizePlayerVolumeCalibrationScale(1.5f))
        assertEquals(2.0f, normalizePlayerVolumeCalibrationScale(2.0f))
    }

    @Test
    fun `player volume calibration snaps to supported steps`() {
        assertEquals(0.9f, normalizePlayerVolumeCalibrationScale(0.92f))
        assertEquals(0.7f, normalizePlayerVolumeCalibrationScale(0.68f))
        assertEquals(1.3f, normalizePlayerVolumeCalibrationScale(1.32f))
        assertEquals(1.7f, normalizePlayerVolumeCalibrationScale(1.68f))
        assertEquals("70%", formatPlayerVolumeCalibrationLabel(0.7f))
        assertEquals("130%", formatPlayerVolumeCalibrationLabel(1.3f))
        assertEquals("200%", formatPlayerVolumeCalibrationLabel(2.0f))
        assertEquals("0%", formatPlayerVolumeCalibrationLabel(0.0f))
    }

    @Test
    fun `player volume calibration cycles through supported steps`() {
        assertEquals(1.9f, nextPlayerVolumeCalibrationScale(2.0f))
        assertEquals(1.0f, nextPlayerVolumeCalibrationScale(1.1f))
        assertEquals(0.9f, nextPlayerVolumeCalibrationScale(1.0f))
        assertEquals(0.1f, nextPlayerVolumeCalibrationScale(0.2f))
        assertEquals(0.0f, nextPlayerVolumeCalibrationScale(0.1f))
        assertEquals(2.0f, nextPlayerVolumeCalibrationScale(0.0f))
    }

    @Test
    fun `playback speed preferences expose resolved speed`() {
        assertEquals(
            2.0f,
            PlaybackSpeedPreferences(
                defaultSpeed = 1.25f,
                rememberLastSpeed = true,
                lastSpeed = 2.0f
            ).preferredSpeed
        )
    }

    @Test
    fun `danmaku display area is clamped`() {
        assertEquals(0.1f, normalizeDanmakuDisplayArea(0.0f))
        assertEquals(0.55f, normalizeDanmakuDisplayArea(0.55f))
        assertEquals(1.0f, normalizeDanmakuDisplayArea(1.4f))
    }
}

