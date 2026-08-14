package com.bbttvv.app.data.repository

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
