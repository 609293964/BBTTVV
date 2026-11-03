package com.bbttvv.app.ui.home
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.HomeFeedRepository
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
class HomeFeedConcurrencyTest {
    @Test fun refreshWaitsForFilterAndKeepsNewData() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var block = false
        var page = listOf(VideoItem(bvid="old", title="old", pic=""))
        val controller = HomeFeedController(RecommendDismissStore(), object : HomeFeedRepository {
            override suspend fun getHomeVideos(idx: Int) = Result.success(page)
        }, filterOverride = { videos ->
            if (block) { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
            videos
        })
        controller.loadMore()
        block = true
        val filter = async { controller.reapplyPluginFilters() }
        withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
        page = listOf(VideoItem(bvid="new", title="new", pic=""))
        val refresh = async { controller.refresh() }
        yield()
        assertFalse(refresh.isCompleted)
        block = false
        release.countDown()
        filter.await(); refresh.await()
        assertEquals(listOf("new"), controller.visibleSnapshot().map { it.bvid })
    }
    @Test fun failedAppendDoesNotPoisonDedupeIndex() = runBlocking {
        var fail = true
        val controller = HomeFeedController(RecommendDismissStore(), object : HomeFeedRepository {
            override suspend fun getHomeVideos(idx: Int): Result<List<VideoItem>> =
                if (fail) Result.failure(IllegalStateException("offline"))
                else Result.success(listOf(VideoItem(bvid="same",title="same",pic="")))
        }, filterOverride = { it })
        assertTrue(controller.loadMore() is HomeFeedLoadResult.Failure)
        fail = false
        controller.loadMore(); controller.loadMore()
        assertEquals(listOf("same"), controller.visibleSnapshot().map { it.bvid })
    }
}
