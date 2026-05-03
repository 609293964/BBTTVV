package com.bbttvv.app.core.store

const val DEFAULT_APP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

fun normalizeDanmakuDisplayArea(ratio: Float): Float {
    return ratio.coerceIn(0.1f, 1.0f)
}

data class PlaybackSpeedPreferences(
    val defaultSpeed: Float = 1.0f,
    val rememberLastSpeed: Boolean = false,
    val lastSpeed: Float = 1.0f
) {
    val preferredSpeed: Float
        get() = resolvePreferredPlaybackSpeed(
            defaultSpeed = defaultSpeed,
            rememberLastSpeed = rememberLastSpeed,
            lastSpeed = lastSpeed
        )
}

interface PlaybackSpeedSettingsStore {
    suspend fun readPlaybackSpeedPreferences(): PlaybackSpeedPreferences
    suspend fun saveDefaultPlaybackSpeed(speed: Float)
    suspend fun saveRememberLastPlaybackSpeed(enabled: Boolean)
    suspend fun saveLastPlaybackSpeed(speed: Float)
}
