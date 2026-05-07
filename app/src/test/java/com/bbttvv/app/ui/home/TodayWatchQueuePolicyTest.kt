package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWatchQueuePolicyTest {

    @Test
    fun `consume removes item and asks refill when queue under preview limit`() {
        val plan = TodayWatchPlan(
            videoQueue = listOf(
                VideoItem(bvid = "a", title = "A"),
                VideoItem(bvid = "b", title = "B")
            ),
            explanationByBvid = mapOf("a" to "exp-a", "b" to "exp-b")
        )

        val update = consumeVideoFromTodayWatchPlan(
            plan = plan,
            consumedBvid = "a",
            queuePreviewLimit = 2
        )

        assertTrue(update.consumedApplied)
        assertTrue(update.shouldRefill)
        assertEquals(listOf("b"), update.updatedPlan.videoQueue.map { it.bvid })
        assertFalse(update.updatedPlan.explanationByBvid.containsKey("a"))
    }

    @Test
    fun `consume keeps plan unchanged when item not found`() {
        val plan = TodayWatchPlan(
            videoQueue = listOf(VideoItem(bvid = "x", title = "X")),
            explanationByBvid = mapOf("x" to "exp-x")
        )

        val update = consumeVideoFromTodayWatchPlan(
            plan = plan,
            consumedBvid = "unknown",
            queuePreviewLimit = 1
        )

        assertFalse(update.consumedApplied)
        assertFalse(update.shouldRefill)
        assertEquals(listOf("x"), update.updatedPlan.videoQueue.map { it.bvid })
        assertTrue(update.updatedPlan.explanationByBvid.containsKey("x"))
    }
}
