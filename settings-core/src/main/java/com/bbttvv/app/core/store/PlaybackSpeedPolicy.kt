package com.bbttvv.app.core.store

import kotlin.math.abs
import kotlin.math.roundToInt

fun normalizePlaybackSpeed(speed: Float): Float {
    return speed.coerceIn(0.1f, 8.0f)
}

const val DEFAULT_LONG_PRESS_SPEED = 2.0f
val LONG_PRESS_SPEED_OPTIONS = listOf(1.5f, 2.0f, 2.5f, 3.0f)

fun normalizeLongPressSpeed(speed: Float): Float {
    return LONG_PRESS_SPEED_OPTIONS.minByOrNull { option -> abs(option - speed) }
        ?: DEFAULT_LONG_PRESS_SPEED
}

fun resolvePreferredPlaybackSpeed(
    defaultSpeed: Float,
    rememberLastSpeed: Boolean,
    lastSpeed: Float
): Float {
    val normalizedDefault = normalizePlaybackSpeed(defaultSpeed)
    if (!rememberLastSpeed) return normalizedDefault
    return normalizePlaybackSpeed(lastSpeed)
}

const val DEFAULT_PLAYER_VOLUME_CALIBRATION_SCALE = 1.0f
val PLAYER_VOLUME_CALIBRATION_SCALES = listOf(1.0f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f)

fun normalizePlayerVolumeCalibrationScale(scale: Float): Float {
    if (!scale.isFinite()) return DEFAULT_PLAYER_VOLUME_CALIBRATION_SCALE
    if (scale < PLAYER_VOLUME_CALIBRATION_SCALES.last() || scale > DEFAULT_PLAYER_VOLUME_CALIBRATION_SCALE) {
        return DEFAULT_PLAYER_VOLUME_CALIBRATION_SCALE
    }
    return PLAYER_VOLUME_CALIBRATION_SCALES.minByOrNull { option -> abs(option - scale) }
        ?: DEFAULT_PLAYER_VOLUME_CALIBRATION_SCALE
}

fun nextPlayerVolumeCalibrationScale(scale: Float): Float {
    val normalized = normalizePlayerVolumeCalibrationScale(scale)
    val index = PLAYER_VOLUME_CALIBRATION_SCALES.indexOf(normalized)
    return if (index == -1 || index == PLAYER_VOLUME_CALIBRATION_SCALES.lastIndex) {
        PLAYER_VOLUME_CALIBRATION_SCALES.first()
    } else {
        PLAYER_VOLUME_CALIBRATION_SCALES[index + 1]
    }
}

fun formatPlayerVolumeCalibrationLabel(scale: Float): String {
    val normalized = normalizePlayerVolumeCalibrationScale(scale)
    return if (abs(normalized - DEFAULT_PLAYER_VOLUME_CALIBRATION_SCALE) < 0.001f) {
        "关闭"
    } else {
        "${(normalized * 100).roundToInt()}%"
    }
}

