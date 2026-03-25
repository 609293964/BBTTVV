package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryItem

internal enum class HistoryNavigationKind {
    VIDEO,
    PGC,
    LIVE,
    ARTICLE
}

internal fun resolveHistoryNavigationKind(
    historyItem: HistoryItem?
): HistoryNavigationKind {
    return when (historyItem?.business) {
        HistoryBusiness.PGC -> HistoryNavigationKind.PGC
        HistoryBusiness.LIVE -> HistoryNavigationKind.LIVE
        HistoryBusiness.ARTICLE -> HistoryNavigationKind.ARTICLE
        else -> HistoryNavigationKind.VIDEO
    }
}
