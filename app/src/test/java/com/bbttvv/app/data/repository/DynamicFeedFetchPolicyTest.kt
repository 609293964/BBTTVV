package com.bbttvv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicFeedFetchPolicyTest {
    @Test
    fun `incremental fetch stops when reported update count is satisfied`() {
        assertFalse(
            shouldContinueDynamicIncrementalFetch(
                accumulatedItemCount = 6,
                updateNum = 6,
                hasMore = true,
                previousOffset = "",
                nextOffset = "next",
                pagesFetched = 1,
            )
        )
    }

    @Test
    fun `incremental fetch counts raw items even when none are renderable`() {
        assertTrue(
            shouldContinueDynamicIncrementalFetch(
                accumulatedItemCount = 3,
                updateNum = 6,
                hasMore = true,
                previousOffset = "",
                nextOffset = "next",
                pagesFetched = 2,
            )
        )
    }

    @Test
    fun `incremental fetch stops at terminal or stalled cursor`() {
        assertFalse(
            shouldContinueDynamicIncrementalFetch(
                accumulatedItemCount = 1,
                updateNum = 3,
                hasMore = false,
                previousOffset = "old",
                nextOffset = "next",
                pagesFetched = 1,
            )
        )
        assertFalse(
            shouldContinueDynamicIncrementalFetch(
                accumulatedItemCount = 1,
                updateNum = 3,
                hasMore = true,
                previousOffset = "same",
                nextOffset = "same",
                pagesFetched = 1,
            )
        )
        assertFalse(
            shouldContinueDynamicIncrementalFetch(
                accumulatedItemCount = 1,
                updateNum = 3,
                hasMore = true,
                previousOffset = "old",
                nextOffset = " ",
                pagesFetched = 1,
            )
        )
    }

    @Test
    fun `incremental fetch respects absolute page limit`() {
        assertFalse(
            shouldContinueDynamicIncrementalFetch(
                accumulatedItemCount = 9,
                updateNum = 20,
                hasMore = true,
                previousOffset = "old",
                nextOffset = "next",
                pagesFetched = DYNAMIC_ABSOLUTE_PAGE_FETCH_LIMIT,
            )
        )
    }

    @Test
    fun `only first page can advance update baseline`() {
        assertEquals(
            "fresh",
            resolveDynamicFeedUpdateBaseline(
                currentBaseline = "old",
                responseBaseline = "fresh",
                pagesFetched = 0
            )
        )
        assertEquals(
            "old",
            resolveDynamicFeedUpdateBaseline(
                currentBaseline = "old",
                responseBaseline = "",
                pagesFetched = 0
            )
        )
        assertEquals(
            "fresh",
            resolveDynamicFeedUpdateBaseline(
                currentBaseline = "fresh",
                responseBaseline = "later",
                pagesFetched = 1
            )
        )
    }
}
