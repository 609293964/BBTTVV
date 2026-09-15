package com.bbttvv.app.core.player

import com.bbttvv.app.data.model.response.SponsorCategory

private const val SPONSOR_SKIP_END_GUARD_MS = 1_000L

/**
 * Keeps ordinary SponsorBlock skips inside the playable timeline. Only an outro is allowed to
 * reach the exact media end, where Media3 may naturally transition to STATE_ENDED.
 */
internal fun resolveSponsorBlockSkipTargetPositionMs(
    requestedPositionMs: Long,
    durationMs: Long,
    category: String,
): Long {
    val safeRequestedPositionMs = requestedPositionMs.coerceAtLeast(0L)
    if (durationMs <= 0L) return safeRequestedPositionMs
    if (category == SponsorCategory.OUTRO) {
        return safeRequestedPositionMs.coerceAtMost(durationMs)
    }
    return safeRequestedPositionMs.coerceAtMost(
        (durationMs - SPONSOR_SKIP_END_GUARD_MS).coerceAtLeast(0L)
    )
}
