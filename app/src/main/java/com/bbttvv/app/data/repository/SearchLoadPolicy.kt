package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.SearchType

internal fun shouldFallbackGuestVideoSearch(
    isLoggedIn: Boolean,
    page: Int,
    primaryResultCount: Int
): Boolean {
    return !isLoggedIn && page == 1 && primaryResultCount == 0
}

internal fun resolveSearchLoadedPage(
    requestedPage: Int,
    responsePage: Int
): Int {
    return maxOf(requestedPage, responsePage.coerceAtLeast(1))
}

internal fun shouldApplySearchResult(
    requestSessionId: Long,
    activeSessionId: Long,
    requestQuery: String,
    activeQuery: String,
    requestType: SearchType,
    activeType: SearchType
): Boolean {
    return requestSessionId == activeSessionId &&
        requestQuery == activeQuery &&
        requestType == activeType
}

internal fun <T, K> mergeSearchPageResults(
    existing: List<T>,
    incoming: List<T>,
    keySelector: (T) -> K
): List<T> {
    val seen = LinkedHashSet<K>()
    val merged = ArrayList<T>(existing.size + incoming.size)
    for (item in existing) {
        if (seen.add(keySelector(item))) {
            merged += item
        }
    }
    for (item in incoming) {
        if (seen.add(keySelector(item))) {
            merged += item
        }
    }
    return merged
}

