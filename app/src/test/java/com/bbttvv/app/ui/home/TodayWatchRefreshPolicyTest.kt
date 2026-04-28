package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWatchRefreshPolicyTest {

    @Test
    fun `manual refresh consumes current preview queue to rotate recommendations`() {
        val plan = TodayWatchPlan(
            videoQueue = listOf(
                VideoItem(bvid = "a", title = "A"),
                VideoItem(bvid = "b", title = "B"),
                VideoItem(bvid = "c", title = "C")
            )
        )

        val consumed = collectTodayWatchConsumedForManualRefresh(
            plan = plan,
            previewLimit = 2
        )

        assertEquals(setOf("a", "b"), consumed)
    }

    @Test
    fun `manual refresh consumption ignores blank bvids and empty plan`() {
        val plan = TodayWatchPlan(
            videoQueue = listOf(
                VideoItem(bvid = "", title = "NoId"),
                VideoItem(bvid = "b", title = "B")
            )
        )

        val consumed = collectTodayWatchConsumedForManualRefresh(
            plan = plan,
            previewLimit = 5
        )
        val emptyConsumed = collectTodayWatchConsumedForManualRefresh(
            plan = null,
            previewLimit = 3
        )

        assertEquals(setOf("b"), consumed)
        assertTrue(emptyConsumed.isEmpty())
    }
}
