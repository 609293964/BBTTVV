package com.bbttvv.app.core.store

val PLAYER_PLAYBACK_SPEED_PRESETS: List<Float> =
    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f, 2.75f)

fun nextPlayerPlaybackSpeedPreset(speed: Float): Float {
    val currentIndex = PLAYER_PLAYBACK_SPEED_PRESETS.indexOfFirst { option ->
        kotlin.math.abs(option - speed) < 0.001f
    }
    return if (currentIndex == -1 || currentIndex == PLAYER_PLAYBACK_SPEED_PRESETS.lastIndex) {
        PLAYER_PLAYBACK_SPEED_PRESETS.first()
    } else {
        PLAYER_PLAYBACK_SPEED_PRESETS[currentIndex + 1]
    }
}
