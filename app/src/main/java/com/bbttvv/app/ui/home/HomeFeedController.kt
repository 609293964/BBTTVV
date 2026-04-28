package com.bbttvv.app.ui.home

import com.bbttvv.app.core.paging.PagedFeedGridState
import com.bbttvv.app.core.paging.appliedOrNull
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.json.JsonPluginManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.FeedRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed class HomeFeedLoadResult {
    data class Success(
        val videos: List<VideoItem>,
        val hasMore: Boolean
    ) : HomeFeedLoadResult()

    data class Failure(
        val error: Throwable
    ) : HomeFeedLoadResult()

    data object Ignored : HomeFeedLoadResult()
}

/** Handles recommendation paging and plugin filtering outside HomeViewModel. */
internal class HomeFeedController(
    private val dismissStore: RecommendDismissStore,
    private val feedRepository: FeedRepository = FeedRepository
) {
    private val feed = PagedFeedGridState<Int, VideoItem, VideoItem>(initialKey = 0)

    fun visibleSnapshot(): List<VideoItem> = feed.visibleSnapshot()

    fun hasVisibleItems(): Boolean = feed.visibleSnapshot().isNotEmpty()

    fun hasSourceItems(): Boolean = feed.sourceSnapshot().isNotEmpty()

    fun isLoadingOrEndReached(): Boolean {
        val snapshot = feed.snapshot()
        return snapshot.isLoading || snapshot.endReached
    }

    fun resetPageCursor() {
        feed.resetPageCursor()
    }

    suspend fun loadMore(): HomeFeedLoadResult {
        val startGeneration = feed.snapshot().generation
        return try {
            val loadResult = feed.loadNextPage(
                isRefresh = false,
                fetch = { pageKey -> loadPage(pageKey, incremental = true) },
                reduce = { _, page -> page }
            )
            loadResult.appliedOrNull() ?: return HomeFeedLoadResult.Ignored
            HomeFeedLoadResult.Success(
                videos = feed.visibleSnapshot(),
                hasMore = !feed.snapshot().endReached
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (feed.snapshot().generation != startGeneration) return HomeFeedLoadResult.Ignored
            Logger.e("HomeFeedController", "Failed to load videos", error)
            HomeFeedLoadResult.Failure(error)
        }
    }

    suspend fun refresh(): HomeFeedLoadResult {
        val startGeneration = feed.snapshot().generation
        return try {
            val loadResult = feed.loadNextPage(
                isRefresh = true,
                fetch = { pageKey -> loadPage(pageKey, incremental = false) },
                reduce = { _, page -> page }
            )
            loadResult.appliedOrNull() ?: return HomeFeedLoadResult.Ignored
            HomeFeedLoadResult.Success(
                videos = feed.visibleSnapshot(),
                hasMore = !feed.snapshot().endReached
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (feed.snapshot().generation != startGeneration) return HomeFeedLoadResult.Ignored
            Logger.e("HomeFeedController", "Failed to refresh videos", error)
            HomeFeedLoadResult.Failure(error)
        }
    }

    suspend fun reapplyPluginFilters(): List<VideoItem> {
        val filteredVideos = applyFeedFiltersOffMain(feed.sourceSnapshot(), recordStats = false)
        return feed.replaceVisible(filteredVideos)
    }

    fun dismiss(video: VideoItem): List<VideoItem>? {
        if (!dismissStore.markDismissed(video)) return null
        return feed.removeVisibleIf(dismissStore::isDismissed)
    }

    private suspend fun loadPage(
        pageKey: Int,
        incremental: Boolean
    ): PagedFeedGridState.Page<Int, VideoItem, VideoItem> {
        val incomingVideos = feedRepository.getHomeVideos(idx = pageKey).getOrElse { error -> throw error }
        val dedupedIncomingVideos = withContext(Dispatchers.Default) {
            HomeFeedDeduper.dedupeNewVideos(
                existingVideos = if (incremental) feed.sourceSnapshot() else emptyList(),
                incomingVideos = incomingVideos,
            )
        }
        val filteredVideos = if (incremental) {
            applyIncrementalFeedFiltersOffMain(dedupedIncomingVideos, recordStats = true)
        } else {
            applyFeedFiltersOffMain(dedupedIncomingVideos, recordStats = true)
        }
        return PagedFeedGridState.Page(
            sourceItems = dedupedIncomingVideos,
            visibleItems = filteredVideos,
            nextKey = if (incomingVideos.isNotEmpty()) pageKey + 1 else pageKey,
            endReached = incomingVideos.isEmpty()
        )
    }

    private suspend fun applyFeedFiltersOffMain(
        videos: List<VideoItem>,
        recordStats: Boolean
    ): List<VideoItem> = withContext(Dispatchers.Default) {
        applyFeedFilters(videos, recordStats = recordStats)
    }

    private fun applyFeedFilters(videos: List<VideoItem>, recordStats: Boolean): List<VideoItem> {
        val jsonFiltered = JsonPluginManager.filterVideos(videos, recordStats = recordStats)
        val pluginFiltered = PluginManager.filterFeedItems(jsonFiltered)
        return if (dismissStore.hasDismissed()) {
            pluginFiltered.filterNot(dismissStore::isDismissed)
        } else {
            pluginFiltered
        }
    }

    private suspend fun applyIncrementalFeedFiltersOffMain(
        videos: List<VideoItem>,
        recordStats: Boolean
    ): List<VideoItem> {
        if (videos.isEmpty()) return emptyList()
        return applyFeedFiltersOffMain(videos, recordStats = recordStats)
    }
}

internal object HomeFeedDeduper {
    fun dedupeNewVideos(
        existingVideos: List<VideoItem>,
        incomingVideos: List<VideoItem>,
    ): List<VideoItem> {
        if (incomingVideos.isEmpty()) return emptyList()
        val seenKeys = LinkedHashSet<String>(existingVideos.size + incomingVideos.size)
        existingVideos.mapNotNullTo(seenKeys) { it.feedDedupeKey() }
        return incomingVideos.filter { video ->
            val key = video.feedDedupeKey()
            key == null || seenKeys.add(key)
        }
    }

    private fun VideoItem.feedDedupeKey(): String? {
        dynamicId.trim().takeIf { it.isNotEmpty() }?.let { return "dynamic:$it" }
        bvid.trim().takeIf { it.isNotEmpty() }?.let { return "bvid:$it" }
        if (id > 0L) return "id:$id"
        if (collectionId > 0L) return "collection:$collectionId"
        if (aid > 0L || cid > 0L) return "aid:$aid:cid:$cid"

        val contentKey = buildString {
            title.trim().takeIf { it.isNotEmpty() }?.let { append(it) }
            pic.trim().takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append(':')
                append(it)
            }
        }
        return contentKey.takeIf { it.isNotEmpty() }?.let { "content:$it" }
    }
}
