package com.bbttvv.app.core.paging

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedFeedGridStateTest {
    @Test
    fun refreshReplacesSourceAndVisibleItems() = runBlocking {
        val feed = PagedFeedGridState<Int, String, String>(initialKey = 1)

        val first = feed.loadNextPage(
            isRefresh = false,
            fetch = { listOf("old") },
            reduce = { key, fetched ->
                PagedFeedGridState.Page(
                    sourceItems = fetched.map { "source:$it" },
                    visibleItems = fetched.map { "visible:$it" },
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )
        assertTrue(first is PagedGridStateMachine.LoadResult.Applied)
        assertEquals(listOf("source:old"), feed.sourceSnapshot())
        assertEquals(listOf("visible:old"), feed.visibleSnapshot())

        val refreshed = feed.refresh(
            fetch = { listOf("new") },
            reduce = { key, fetched ->
                PagedFeedGridState.Page(
                    sourceItems = fetched.map { "source:$it" },
                    visibleItems = fetched.map { "visible:$it" },
                    nextKey = key + 1,
                    endReached = false,
                )
            },
        )

        assertTrue(refreshed is PagedGridStateMachine.LoadResult.Applied)
        assertEquals(listOf("source:new"), feed.sourceSnapshot())
        assertEquals(listOf("visible:new"), feed.visibleSnapshot())
        assertEquals(2, feed.snapshot().nextKey)
    }
}
