package com.bbttvv.app.data.repository

import com.bbttvv.app.data.model.response.*
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class HomeFeedMergePolicyTest {
    @Test fun `merge keeps stable display identity while enriching duplicate feedback`() {
        val feedback = RecommendationFeedbackMetadata("av", "123", listOf(RecommendationFeedbackReason(1, "不感兴趣")), true, 123)
        val app = VideoItem(bvid = "BV-duplicate", title = "app")
        val web = app.copy(title = "web", recommendationFeedback = feedback)
        val merged = HomeFeedMergePolicy.mergeFeeds(listOf(web, VideoItem(bvid = "BV-web")), listOf(app))
        assertEquals(listOf("BV-duplicate", "BV-web"), merged.map { it.bvid })
        assertEquals("app", merged.first().title)
        assertEquals(feedback, merged.first().recommendationFeedback)
    }
    @Test fun `one source can fail while the other remains usable`() {
        val videos = listOf(VideoItem(bvid = "BV-1"))
        assertEquals(videos, mergeRecommendationResults(Result.failure(Exception("fixture")), Result.success(videos)).getOrThrow())
        assertTrue(mergeRecommendationResults(Result.failure(Exception("fixture")), Result.failure(Exception("fixture"))).isFailure)
        assertTrue(HomeFeedMergePolicy.mergeFeeds(videos + videos, emptyList()).size == 1)
    }
    @Test(expected = CancellationException::class)
    fun `cancellation cannot become a successful partial feed`() {
        mergeRecommendationResults(Result.failure(CancellationException()), Result.success(listOf(VideoItem(bvid = "BV-1"))))
    }
    @Test fun `generic dismissal never chooses report or creator reasons`() {
        val feedback = RecommendationFeedbackMetadata(reasons = listOf(
            RecommendationFeedbackReason(2, "举报", "feedback"),
            RecommendationFeedbackReason(3, "不看此UP主"),
            RecommendationFeedbackReason(4, "不感兴趣"),
        ))
        assertEquals(4L, feedback.defaultDislikeReason()?.id)
        assertNull(feedback.copy(reasons = feedback.reasons.take(2)).defaultDislikeReason())
    }
}
