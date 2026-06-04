package com.bbttvv.app.core.player

internal object DanmakuInitialLoadRetryPolicy {
    private val INITIAL_RETRY_DELAYS_MS = longArrayOf(600L, 1_500L)

    val retryCount: Int
        get() = INITIAL_RETRY_DELAYS_MS.size

    fun nextDelayMs(
        failedAttemptIndex: Int,
        hasParsedPayload: Boolean,
        hasRawData: Boolean,
        isCurrentCid: Boolean,
        isDanmakuEnabled: Boolean,
        hasPublishedPayload: Boolean,
    ): Long? {
        if (hasParsedPayload || hasRawData) return null
        if (!isCurrentCid || !isDanmakuEnabled || hasPublishedPayload) return null
        return INITIAL_RETRY_DELAYS_MS.getOrNull(failedAttemptIndex)
    }
}
