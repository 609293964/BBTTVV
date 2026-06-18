package com.bbttvv.app.core.store

import kotlin.math.abs

val PLAYER_PLAYBACK_SPEED_PRESETS: List<Float> =
    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

fun normalizePlaybackSpeed(speed: Float): Float {
    return speed.coerceIn(0.5f, 3.0f)
}

fun nextPlayerPlaybackSpeedPreset(speed: Float): Float {
    val currentIndex = PLAYER_PLAYBACK_SPEED_PRESETS.indexOfFirst { option ->
        abs(option - speed) < 0.001f
    }
    return if (currentIndex == -1 || currentIndex == PLAYER_PLAYBACK_SPEED_PRESETS.lastIndex) {
        PLAYER_PLAYBACK_SPEED_PRESETS.first()
    } else {
        PLAYER_PLAYBACK_SPEED_PRESETS[currentIndex + 1]
    }
}

fun resolvePreferredPlaybackSpeed(
    defaultSpeed: Float,
    rememberLastSpeed: Boolean,
    lastSpeed: Float
): Float {
    return if (rememberLastSpeed) {
        normalizePlaybackSpeed(lastSpeed)
    } else {
        normalizePlaybackSpeed(defaultSpeed)
    }
}
