package com.bbttvv.app.core.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWatchFeedbackStoreTest {
    @Test
    fun `disliked video feedback stores recent sample and normalized signals`() {
        val snapshot = TodayWatchFeedbackSnapshot().withDislikedVideoFeedback(
            video = TodayWatchDislikedVideoSnapshot(
                bvid = " BV1xx411c7mD ",
                title = "  吵闹整活合集  ",
                creatorName = "  UP-X  ",
                creatorMid = 42L,
                dislikedAtMillis = 100L
            ),
            keywords = setOf(" 吵闹 ", "整活")
        )

        assertTrue("BV1xx411c7mD" in snapshot.dislikedBvids)
        assertTrue(42L in snapshot.dislikedCreatorMids)
        assertTrue("吵闹" in snapshot.dislikedKeywords)
        assertEquals("BV1xx411c7mD", snapshot.recentDislikedVideos.single().bvid)
        assertEquals("吵闹整活合集", snapshot.recentDislikedVideos.single().title)
        assertEquals("UP-X", snapshot.recentDislikedVideos.single().creatorName)
    }

    @Test
    fun `repeated disliked video replaces older recent sample`() {
        val first = TodayWatchFeedbackSnapshot().withDislikedVideoFeedback(
            video = TodayWatchDislikedVideoSnapshot(
                bvid = "same",
                title = "旧标题",
                creatorName = "UP-A",
                creatorMid = 1L,
                dislikedAtMillis = 100L
            ),
            keywords = emptySet()
        )

        val second = first.withDislikedVideoFeedback(
            video = TodayWatchDislikedVideoSnapshot(
                bvid = "same",
                title = "新标题",
                creatorName = "UP-A",
                creatorMid = 1L,
                dislikedAtMillis = 200L
            ),
            keywords = emptySet()
        )

        assertEquals(1, second.recentDislikedVideos.size)
        assertEquals("新标题", second.recentDislikedVideos.single().title)
    }
}
