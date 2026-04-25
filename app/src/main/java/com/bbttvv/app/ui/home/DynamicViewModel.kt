package com.bbttvv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.paging.PagedFeedGridState
import com.bbttvv.app.core.paging.appliedOrNull
import com.bbttvv.app.data.model.response.DynamicItem
import com.bbttvv.app.data.model.response.FollowedLiveRoom
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.DynamicRepository
import com.bbttvv.app.data.repository.VideoDetailRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DynamicFocusSummaryPrefetchDelayMs = 300L

data class DynamicUiState(
    val liveUsers: List<FollowedLiveRoom> = emptyList(),
    val dynamicVideos: List<DynamicItem> = emptyList(),
    val isLoadingLive: Boolean = false,
    val isLoadingVideos: Boolean = false,
    val videoErrorMsg: String? = null,
    val liveErrorMsg: String? = null
)

class DynamicViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DynamicUiState())
    val uiState: StateFlow<DynamicUiState> = _uiState.asStateFlow()

    private var hasMoreVideos = true
    private val videoFeed = PagedFeedGridState<Int, DynamicItem, DynamicItem>(initialKey = 1)
    private var initialLoadStarted = false
    private var loadMoreJob: Job? = null
    private var detailPrefetchJob: Job? = null
    private var pendingPrefetchBvid: String? = null
    private var lastPrefetchedBvid: String? = null
    private var liveUsersRequestGeneration: Long = 0L

    fun onEnter() {
        if (!initialLoadStarted) {
            initialLoadStarted = true
            loadInitial()
            return
        }
        refreshLiveUsers(showLoading = false)
        if (_uiState.value.dynamicVideos.isEmpty()) {
            refreshDynamicVideos(showLoading = false)
        }
    }

    private fun loadInitial() {
        loadMoreJob?.cancel()
        loadMoreJob = null
        refreshLiveUsers(showLoading = true)
        refreshDynamicVideos(showLoading = true)
    }

    private fun refreshDynamicVideos(showLoading: Boolean = false) {
        loadMoreJob?.cancel()
        loadMoreJob = null
        loadDynamicVideos(refresh = true, showLoading = showLoading)
    }

    fun refresh() {
        refreshLiveUsers(showLoading = true)
        refreshDynamicVideos(showLoading = true)
    }

    fun refreshLiveUsers(showLoading: Boolean = false) {
        val generation = ++liveUsersRequestGeneration
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update {
                    it.copy(
                        isLoadingLive = true,
                        liveErrorMsg = null
                    )
                }
            }

            val result = DynamicRepository.getFollowedLiveUsers(page = 1, pageSize = 30)
            if (generation != liveUsersRequestGeneration) return@launch
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        liveUsers = result.getOrDefault(emptyList()),
                        isLoadingLive = false,
                        liveErrorMsg = null
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        isLoadingLive = false,
                        liveErrorMsg = result.exceptionOrNull()?.message ?: state.liveErrorMsg
                    )
                }
            }
        }
    }

    fun loadMoreVideos() {
        if (!hasMoreVideos || _uiState.value.isLoadingVideos || loadMoreJob?.isActive == true) return
        loadDynamicVideos(refresh = false, showLoading = true)
    }

    private fun loadDynamicVideos(refresh: Boolean, showLoading: Boolean) {
        if (refresh) {
            hasMoreVideos = true
            videoFeed.resetPageCursor()
        } else {
            val snapshot = videoFeed.snapshot()
            if (snapshot.isLoading || snapshot.endReached) return
        }

        val startGeneration = videoFeed.snapshot().generation
        val job = viewModelScope.launch {
            if (showLoading) {
                _uiState.update {
                    it.copy(
                        isLoadingVideos = true,
                        videoErrorMsg = null
                    )
                }
            }
            try {
                val result = videoFeed.loadNextPage(
                    isRefresh = refresh,
                    fetch = { pageKey ->
                        val newVideos = DynamicRepository.getDynamicFeed(refresh = refresh)
                            .getOrElse { error -> throw error }
                        PagedFeedGridState.Page(
                            sourceItems = newVideos,
                            visibleItems = newVideos,
                            nextKey = pageKey + 1,
                            endReached = newVideos.isEmpty()
                        )
                    },
                    reduce = { _, page -> page }
                )

                if (result.appliedOrNull() == null) {
                    if (videoFeed.snapshot().generation == startGeneration) {
                        hasMoreVideos = !videoFeed.snapshot().endReached
                        _uiState.update { it.copy(isLoadingVideos = false) }
                    }
                    return@launch
                }

                hasMoreVideos = !videoFeed.snapshot().endReached
                _uiState.update {
                    it.copy(
                        dynamicVideos = videoFeed.visibleSnapshot(),
                        isLoadingVideos = false,
                        videoErrorMsg = null
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (videoFeed.snapshot().generation != startGeneration) return@launch
                hasMoreVideos = !videoFeed.snapshot().endReached
                _uiState.update {
                    it.copy(
                        isLoadingVideos = false,
                        videoErrorMsg = error.message ?: if (refresh) "Failed to load dynamics" else "Failed to load more"
                    )
                }
            } finally {
                if (videoFeed.snapshot().generation == startGeneration) {
                    loadMoreJob = null
                }
            }
        }
        loadMoreJob = job
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
            delay(DynamicFocusSummaryPrefetchDelayMs)
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
}
