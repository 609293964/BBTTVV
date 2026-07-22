package com.bbttvv.app.feature.live

import kotlin.math.max

internal fun shouldAcceptLiveDanmaku(
    isEnabled: Boolean,
    isPlaying: Boolean,
    isAppInBackground: Boolean,
    isPlaybackSuppressed: Boolean,
): Boolean {
    return isEnabled && isPlaying && !isAppInBackground && !isPlaybackSuppressed
}

internal fun resolveLiveDanmakuShowAtMs(
    currentPositionMs: Long,
    lastShowAtMs: Long,
    showLeadMs: Long,
    minGapMs: Long,
): Long {
    return max(
        currentPositionMs.coerceAtLeast(0L) + showLeadMs.coerceAtLeast(0L),
        lastShowAtMs.coerceAtLeast(0L) + minGapMs.coerceAtLeast(0L),
    )
}
