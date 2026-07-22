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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PopularCategory(
    val label: String,
    val rid: Int? = null
)

val defaultPopularCategories = listOf(
    PopularCategory(label = "热门"),
    PopularCategory(label = "动画", rid = 1005),
    PopularCategory(label = "音乐", rid = 1003),
    PopularCategory(label = "舞蹈", rid = 1004),
    PopularCategory(label = "游戏", rid = 1008),
    PopularCategory(label = "知识", rid = 1010),
    PopularCategory(label = "科技", rid = 1012),
    PopularCategory(label = "运动", rid = 1018),
    PopularCategory(label = "汽车", rid = 1013),
    PopularCategory(label = "娱乐", rid = 1002),
    PopularCategory(label = "美食", rid = 1020),
    PopularCategory(label = "动物圈", rid = 1024),
    PopularCategory(label = "影视", rid = 1001)
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
    private val detailPrefetcher = HomeDetailPrefetcher(viewModelScope)
    private var initialLoadStarted = false

    init {
        observeFeedPluginUpdates()
    }

    fun onEnter() {
        if (initialLoadStarted) return
        initialLoadStarted = true
        loadCategory(index = _uiState.value.selectedCategoryIndex, refresh = true)
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
        detailPrefetcher.prime(video)
    }

    fun prefetchVideoDetail(video: VideoItem) {
        detailPrefetcher.prefetch(video)
    }

    fun prefetchVideoDetail(video: VideoItem, videos: List<VideoItem>, index: Int) {
        prefetchVideoDetail(video)
    }

    override fun onCleared() {
        detailPrefetcher.clear()
        super.onCleared()
    }

    private fun loadCategory(index: Int, refresh: Boolean) {
        val category = defaultPopularCategories.getOrNull(index) ?: return
        val feed = feedForCategory(index)
        val snapshot = feed.snapshot()
        if (!refresh) {
            if (snapshot.isLoading || snapshot.endReached) return
        }

        val expectedGeneration = if (refresh) snapshot.generation + 1 else snapshot.generation
        val page = if (refresh) 1 else snapshot.nextKey
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
                Logger.d("PopularViewModel", "Loading category=${category.label}, rid=${category.rid}, page=$page")
                val fetchPage: suspend (Int) -> PagedFeedGridState.Page<Int, VideoItem, VideoItem> = { pageKey ->
                        val incomingVideos = if (category.rid == null) {
                            FeedRepository.getPopularVideos(page = pageKey)
                        } else {
                            FeedRepository.getRegionVideos(tid = category.rid, page = pageKey)
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
                    }
                val loadResult = if (refresh) {
                    feed.refresh(
                        fetch = fetchPage,
                        reduce = { _, page -> page }
                    )
                } else {
                    feed.loadNextPage(
                        isRefresh = false,
                        fetch = fetchPage,
                        reduce = { _, page -> page }
                    )
                }

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
                if (feed.snapshot().generation != expectedGeneration) return@launch
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
