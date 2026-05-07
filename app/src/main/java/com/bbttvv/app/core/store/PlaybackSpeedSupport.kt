package com.bbttvv.app.core.store

fun normalizePlaybackSpeed(speed: Float): Float {
    return speed.coerceIn(0.5f, 3.0f)
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
