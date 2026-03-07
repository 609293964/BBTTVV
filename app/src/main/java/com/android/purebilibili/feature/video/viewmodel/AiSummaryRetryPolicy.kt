package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.repository.AiSummaryFetchStatus

private val AI_SUMMARY_AUTO_RETRY_DELAYS_MS = listOf(
    2_500L,
    5_000L,
    10_000L,
    20_000L
)

internal fun resolveAiSummaryRetryDelayMs(queuedRetryCount: Int): Long {
    return AI_SUMMARY_AUTO_RETRY_DELAYS_MS.getOrElse(queuedRetryCount) {
        AI_SUMMARY_AUTO_RETRY_DELAYS_MS.last()
    }
}

internal fun shouldContinueAiSummaryAutoRetry(
    status: AiSummaryFetchStatus,
    queuedRetryCount: Int
): Boolean {
    return status == AiSummaryFetchStatus.QUEUED &&
        queuedRetryCount < AI_SUMMARY_AUTO_RETRY_DELAYS_MS.size
}
