package com.bbttvv.app.core.util

import com.bbttvv.app.data.model.VideoQuality

internal fun resolvePlaybackDefaultQualityId(
    storedQuality: Int,
    autoHighestEnabled: Boolean,
    isLoggedIn: Boolean,
    isVip: Boolean
): Int {
    if (autoHighestEnabled) {
        return VideoQuality.SUPER_8K.code
    }

    return resolvePlayableDefaultQualityId(
        storedQuality = storedQuality,
        isLoggedIn = isLoggedIn,
        isVip = isVip
    )
}

internal fun resolveStoredQualityForContent(
    normalStoredQuality: Int,
    pgcStoredQuality: Int?,
    isPgc: Boolean
): Int {
    return if (isPgc) {
        pgcStoredQuality?.takeIf { it > 0 } ?: normalStoredQuality
    } else {
        normalStoredQuality
    }
}

internal fun shouldRefreshVipStatusBeforeResolvingDefaultQuality(
    storedQuality: Int,
    autoHighestEnabled: Boolean,
    isLoggedIn: Boolean,
    cachedIsVip: Boolean
): Boolean {
    if (!isLoggedIn || cachedIsVip) return false
    return autoHighestEnabled || storedQuality > 80
}

internal fun resolvePlayableDefaultQualityId(
    storedQuality: Int,
    isLoggedIn: Boolean,
    isVip: Boolean
): Int {
    return when {
        isVip -> storedQuality
        !isLoggedIn -> minOf(storedQuality, VideoQuality.HIGH_720.code)
        storedQuality > VideoQuality.HIGH_1080.code -> VideoQuality.HIGH_1080.code
        else -> storedQuality
    }
}
