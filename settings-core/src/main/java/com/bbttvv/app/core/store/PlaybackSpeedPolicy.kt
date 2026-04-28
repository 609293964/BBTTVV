package com.bbttvv.app.core.store

import kotlin.math.abs

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

