package com.bbttvv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.paging.PagedFeedGridState
import com.bbttvv.app.core.paging.appliedOrNull
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.json.JsonPluginManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.FeedRepository
import com.bbttvv.app.data.repository.VideoDetailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PopularFocusSummaryPrefetchDelayMs = 240L

data class PopularCategory(
    val label: String,
    val tid: Int? = null
)

val defaultPopularCategories = listOf(
    PopularCategory(label = "热门"),
    PopularCategory(label = "动画", tid = 1),
    PopularCategory(label = "音乐", tid = 3),
    PopularCategory(label = "舞蹈", tid = 129),
    PopularCategory(label = "游戏", tid = 4),
    PopularCategory(label = "知识", tid = 36),
    PopularCategory(label = "科技", tid = 188),
    PopularCategory(label = "运动", tid = 234),
    PopularCategory(label = "汽车", tid = 223),
    PopularCategory(label = "生活", tid = 160),
    PopularCategory(label = "美食", tid = 211),
    PopularCategory(label = "动物圈", tid = 217),
    PopularCategory(label = "影视", tid = 181)
)

data class PopularUiState(
    val categories: List<PopularCategory> = defaultPopularCategories,
    val selectedCategoryIndex: Int = 0,
    val videos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMsg: String? = null,
    val hasMore: Boolean = true
)

class PopularViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PopularUiState())
    val uiState: StateFlow<PopularUiState> = _uiState.asStateFlow()

    private val categoryFeeds = mutableMapOf<Int, PagedFeedGridState<Int, VideoItem, VideoItem>>()
    private var detailPrefetchJob: Job? = null
    private var pendingPrefetchBvid: String? = null
    private var lastPrefetchedBvid: String? = null

    init {
        observeFeedPluginUpdates()
        loadCategory(index = 0, refresh = true)
    }

    fun selectCategory(index: Int) {
        val safeIndex = index.coerceIn(0, defaultPopularCategories.lastIndex)
        if (safeIndex == _uiState.value.selectedCategoryIndex) {
            loadCategory(index = safeIndex, refresh = true)
            return
        }

        val cachedVideos = feedForCategory(safeIndex).visibleSnapshot()
        _uiState.update {
            it.copy(
                selectedCategoryIndex = safeIndex,
                videos = cachedVideos.toList(),
                isError = false,
                errorMsg = null,
                hasMore = !feedForCategory(safeIndex).snapshot().endReached
            )
        }
        if (cachedVideos.isEmpty()) {
            loadCategory(index = safeIndex, refresh = true)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        loadCategory(index = state.selectedCategoryIndex, refresh = false)
    }

    fun refresh() {
        loadCategory(index = _uiState.value.selectedCategoryIndex, refresh = true)
    }

    fun primeVideoDetail(video: VideoItem) {
        if (video.bvid == pendingPrefetchBvid) {
            detailPrefetchJob?.cancel()
            pendingPrefetchBvid = null
        }
        lastPrefetchedBvid = video.bvid.takeIf { it.isNotBlank() } ?: lastPrefetchedBvid
        VideoDetailRepository.prefetchDetailLanding(video)
    }

    fun prefetchVideoDetail(video: VideoItem) {
        if (video.bvid.isBlank()) return
        if (video.bvid == pendingPrefetchBvid || video.bvid == lastPrefetchedBvid) return
        detailPrefetchJob?.cancel()
        pendingPrefetchBvid = video.bvid
        detailPrefetchJob = viewModelScope.launch {
            delay(PopularFocusSummaryPrefetchDelayMs)
            VideoDetailRepository.prefetchDetailSummary(video)
            lastPrefetchedBvid = video.bvid
            if (pendingPrefetchBvid == video.bvid) {
                pendingPrefetchBvid = null
            }
        }
    }

    fun prefetchVideoDetail(video: VideoItem, videos: List<VideoItem>, index: Int) {
        prefetchVideoDetail(video)
    }

    private fun loadCategory(index: Int, refresh: Boolean) {
        val category = defaultPopularCategories.getOrNull(index) ?: return
        val feed = feedForCategory(index)
        if (refresh) {
            feed.reset()
        } else {
            val snapshot = feed.snapshot()
            if (snapshot.isLoading || snapshot.endReached) return
        }

        val startGeneration = feed.snapshot().generation
        val page = feed.snapshot().nextKey
        _uiState.update {
            if (it.selectedCategoryIndex == index) {
                it.copy(
                    isLoading = true,
                    isError = false,
                    errorMsg = null
                )
            } else {
                it
            }
        }

        viewModelScope.launch {
            try {
                Logger.d("PopularViewModel", "Loading category=${category.label}, tid=${category.tid}, page=$page")
                val loadResult = feed.loadNextPage(
                    isRefresh = refresh,
                    fetch = { pageKey ->
                        val incomingVideos = if (category.tid == null) {
                            FeedRepository.getPopularVideos(page = pageKey)
                        } else {
                            FeedRepository.getRegionVideos(tid = category.tid, page = pageKey)
                        }.getOrElse { error -> throw error }

                        PagedFeedGridState.Page(
                            sourceItems = incomingVideos,
                            visibleItems = applyFeedFiltersOffMain(
                                videos = incomingVideos,
                                recordStats = true
                            ),
                            nextKey = if (incomingVideos.isNotEmpty()) pageKey + 1 else pageKey,
                            endReached = incomingVideos.isEmpty()
                        )
                    },
                    reduce = { _, page -> page }
                )

                loadResult.appliedOrNull() ?: return@launch
                val visibleVideos = feed.visibleSnapshot()

                _uiState.update { state ->
                    if (state.selectedCategoryIndex == index) {
                        state.copy(
                            videos = visibleVideos,
                            isLoading = false,
                            isError = false,
                            errorMsg = null,
                            hasMore = !feed.snapshot().endReached
                        )
                    } else {
                        state
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (feed.snapshot().generation != startGeneration) return@launch
                Logger.e("PopularViewModel", "Failed to load category=${category.label}", error)
                _uiState.update { state ->
                    if (state.selectedCategoryIndex == index) {
                        state.copy(
                            isLoading = false,
                            isError = true,
                            errorMsg = error.message ?: "未知错误"
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun feedForCategory(index: Int): PagedFeedGridState<Int, VideoItem, VideoItem> {
        return categoryFeeds.getOrPut(index) { PagedFeedGridState(initialKey = 1) }
    }

    private fun observeFeedPluginUpdates() {
        viewModelScope.launch {
            PluginManager.feedPluginUpdateToken.collectLatest { token ->
                if (token <= 0L) return@collectLatest
                val selectedIndex = _uiState.value.selectedCategoryIndex
                if (categoryFeeds.isEmpty()) return@collectLatest
                categoryFeeds.forEach { (_, feed) ->
                    val filteredVideos = applyFeedFiltersOffMain(feed.sourceSnapshot(), recordStats = false)
                    feed.replaceVisible(filteredVideos)
                }
                val filteredSelectedVideos = feedForCategory(selectedIndex).visibleSnapshot()
                _uiState.update { state ->
                    state.copy(videos = filteredSelectedVideos)
                }
            }
        }
    }

    private suspend fun applyFeedFiltersOffMain(
        videos: List<VideoItem>,
        recordStats: Boolean
    ): List<VideoItem> = withContext(Dispatchers.Default) {
        applyFeedFilters(videos, recordStats = recordStats)
    }

    private fun applyFeedFilters(videos: List<VideoItem>, recordStats: Boolean): List<VideoItem> {
        val jsonFiltered = JsonPluginManager.filterVideos(videos, recordStats = recordStats)
        return PluginManager.filterFeedItems(jsonFiltered)
    }
}
