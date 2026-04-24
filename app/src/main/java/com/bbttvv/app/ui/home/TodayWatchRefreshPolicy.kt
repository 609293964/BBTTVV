package com.bbttvv.app.ui.home

internal fun collectTodayWatchConsumedForManualRefresh(
    plan: TodayWatchPlan?,
    previewLimit: Int
): Set<String> {
    val safePlan = plan ?: return emptySet()
    val safeLimit = previewLimit.coerceAtLeast(1)
    return safePlan.videoQueue
        .take(safeLimit)
        .mapNotNull { it.bvid.takeIf { bvid -> bvid.isNotBlank() } }
        .toSet()
}
