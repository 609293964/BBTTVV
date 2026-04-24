package com.bbttvv.app.core.paging

/**
 * Thin in-memory state holder for paged TV grid feeds.
 *
 * Keeps pagination transitions and source/visible lists together, while callers still own
 * networking, filtering, UI state, and any feature-specific side effects.
 */
class PagedFeedGridState<K, SourceItem, VisibleItem>(
    initialKey: K,
) {
    private val paging = PagedGridStateMachine(initialKey = initialKey)
    private val sourceItems = mutableListOf<SourceItem>()
    private val visibleItems = mutableListOf<VisibleItem>()

    data class Page<K, SourceItem, VisibleItem>(
        val sourceItems: List<SourceItem>,
        val visibleItems: List<VisibleItem>,
        val nextKey: K,
        val endReached: Boolean,
    )

    fun snapshot(): PagedGridStateMachine.State<K> = paging.snapshot()

    fun resetPaging() {
        paging.reset()
    }

    fun clearAll() {
        paging.reset()
        sourceItems.clear()
        visibleItems.clear()
    }

    fun sourceSnapshot(): List<SourceItem> = sourceItems.toList()

    fun visibleSnapshot(): List<VisibleItem> = visibleItems.toList()

    fun replaceVisible(items: List<VisibleItem>): List<VisibleItem> {
        visibleItems.clear()
        visibleItems.addAll(items)
        return visibleSnapshot()
    }

    fun removeVisibleIf(predicate: (VisibleItem) -> Boolean): List<VisibleItem> {
        visibleItems.removeAll(predicate)
        return visibleSnapshot()
    }

    suspend fun <Fetched> loadNextPage(
        isRefresh: Boolean,
        fetch: suspend (key: K) -> Fetched?,
        reduce: (key: K, fetched: Fetched) -> Page<K, SourceItem, VisibleItem>,
    ): PagedGridStateMachine.LoadResult<VisibleItem> {
        var pageUpdate: Page<K, SourceItem, VisibleItem>? = null
        val result = paging.loadNextPage(
            isRefresh = isRefresh,
            fetch = fetch,
            reduce = { key, fetched ->
                val page = reduce(key, fetched)
                pageUpdate = page
                PagedGridStateMachine.Update(
                    items = page.visibleItems,
                    nextKey = page.nextKey,
                    endReached = page.endReached,
                )
            },
        )

        if (result is PagedGridStateMachine.LoadResult.Applied) {
            val page = pageUpdate ?: return result
            if (isRefresh) {
                sourceItems.clear()
                visibleItems.clear()
            }
            sourceItems.addAll(page.sourceItems)
            visibleItems.addAll(result.items)
        }
        return result
    }
}
