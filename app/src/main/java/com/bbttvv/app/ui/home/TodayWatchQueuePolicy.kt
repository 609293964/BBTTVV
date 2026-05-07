package com.bbttvv.app.ui.home

internal data class TodayWatchQueueConsumeUpdate(
    val updatedPlan: TodayWatchPlan,
    val consumedApplied: Boolean,
    val shouldRefill: Boolean
)

internal fun consumeVideoFromTodayWatchPlan(
    plan: TodayWatchPlan,
    consumedBvid: String,
    queuePreviewLimit: Int
): TodayWatchQueueConsumeUpdate {
    if (consumedBvid.isBlank()) {
        return TodayWatchQueueConsumeUpdate(
            updatedPlan = plan,
            consumedApplied = false,
            shouldRefill = false
        )
    }
    if (plan.videoQueue.none { it.bvid == consumedBvid }) {
        return TodayWatchQueueConsumeUpdate(
            updatedPlan = plan,
            consumedApplied = false,
            shouldRefill = false
        )
    }

    val updatedQueue = plan.videoQueue.filterNot { it.bvid == consumedBvid }
    val updatedPlan = plan.copy(
        videoQueue = updatedQueue,
        explanationByBvid = plan.explanationByBvid - consumedBvid
    )
    val previewLimit = queuePreviewLimit.coerceAtLeast(1)
    return TodayWatchQueueConsumeUpdate(
        updatedPlan = updatedPlan,
        consumedApplied = true,
        shouldRefill = updatedQueue.size < previewLimit
    )
}
