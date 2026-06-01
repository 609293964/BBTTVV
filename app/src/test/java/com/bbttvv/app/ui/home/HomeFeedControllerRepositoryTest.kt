package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.HomeFeedRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedControllerRepositoryTest {

    @Test
    fun `load more reads from injected repository and appends deduped pages`() = runBlocking {
        val repository = FakeHomeFeedRepository(
            pages = mapOf(
                0 to Result.success(listOf(video("BV1"), video("BV2"))),
                1 to Result.success(listOf(video("BV2"), video("BV3")))
            )
        )
        val controller = HomeFeedController(
            dismissStore = RecommendDismissStore(),
            feedRepository = repository
        )

        val first = controller.loadMore() as HomeFeedLoadResult.Success
        val second = controller.loadMore() as HomeFeedLoadResult.Success

        assertEquals(listOf(0, 1), repository.requestedPages)
        assertEquals(listOf("BV1", "BV2"), first.videos.map { it.bvid })
        assertEquals(listOf("BV1", "BV2", "BV3"), second.videos.map { it.bvid })
    }

    @Test
    fun `empty injected page marks feed as ended`() = runBlocking {
        val controller = HomeFeedController(
            dismissStore = RecommendDismissStore(),
            feedRepository = FakeHomeFeedRepository(
                pages = mapOf(0 to Result.success(emptyList()))
            )
        )

        val result = controller.loadMore() as HomeFeedLoadResult.Success

        assertTrue(result.videos.isEmpty())
        assertEquals(false, result.hasMore)
    }

    @Test
    fun `manual refresh requests the current recommend cursor`() = runBlocking {
        val repository = FakeHomeFeedRepository(
            pages = mapOf(
                0 to Result.success(listOf(video("BV1"))),
                1 to Result.success(listOf(video("BV2"))),
                2 to Result.success(listOf(video("BV3")))
            )
        )
        val controller = HomeFeedController(
            dismissStore = RecommendDismissStore(),
            feedRepository = repository
        )

        controller.loadMore()
        val refreshed = controller.refresh() as HomeFeedLoadResult.Success
        val nextPage = controller.loadMore() as HomeFeedLoadResult.Success

        assertEquals(listOf(0, 1, 2), repository.requestedPages)
        assertEquals(listOf("BV2"), refreshed.videos.map { it.bvid })
        assertEquals(listOf("BV2", "BV3"), nextPage.videos.map { it.bvid })
    }

    @Test
    fun `manual refresh retries the same cursor after empty response`() = runBlocking {
        val repository = FakeHomeFeedRepository(
            pages = mapOf(
                0 to Result.success(listOf(video("BV1"))),
                1 to Result.success(emptyList())
            )
        )
        val controller = HomeFeedController(
            dismissStore = RecommendDismissStore(),
            feedRepository = repository
        )

        controller.loadMore()
        val firstRefresh = controller.refresh() as HomeFeedLoadResult.Success
        val secondRefresh = controller.refresh() as HomeFeedLoadResult.Success

        assertEquals(listOf(0, 1, 1), repository.requestedPages)
        assertTrue(firstRefresh.videos.isEmpty())
        assertTrue(secondRefresh.videos.isEmpty())
        assertEquals(false, secondRefresh.hasMore)
    }

    @Test
    fun `repository failure surfaces as load failure`() = runBlocking {
        val failure = IllegalStateException("offline")
        val controller = HomeFeedController(
            dismissStore = RecommendDismissStore(),
            feedRepository = FakeHomeFeedRepository(
                pages = mapOf(0 to Result.failure(failure))
            )
        )

        val result = controller.loadMore()

        assertTrue(result is HomeFeedLoadResult.Failure)
        assertEquals(failure, (result as HomeFeedLoadResult.Failure).error)
    }

    private class FakeHomeFeedRepository(
        private val pages: Map<Int, Result<List<VideoItem>>>
    ) : HomeFeedRepository {
        val requestedPages = mutableListOf<Int>()

        override suspend fun getHomeVideos(idx: Int): Result<List<VideoItem>> {
            requestedPages += idx
            return pages[idx] ?: Result.success(emptyList())
        }
    }

    private fun video(bvid: String): VideoItem {
        return VideoItem(
            id = bvid.removePrefix("BV").toLongOrNull() ?: 0L,
            bvid = bvid,
            title = "Video $bvid",
            pic = "https://example.com/$bvid.jpg"
        )
    }
}
