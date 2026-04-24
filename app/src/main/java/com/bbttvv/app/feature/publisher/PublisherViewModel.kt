package com.bbttvv.app.feature.publisher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.paging.PagedGridStateMachine
import com.bbttvv.app.core.paging.appliedOrNull
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.PublisherRepository
import com.bbttvv.app.data.repository.VideoDetailRepository
import com.bbttvv.app.data.repository.normalizePublisherVideoErrorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PublisherUiState(
    val mid: Long = 0L,
    val header: PublisherRepository.PublisherHeader? = null,
    val items: List<VideoItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val selectedSort: PublisherRepository.PublisherSortOrder = PublisherRepository.PublisherSortOrder.TIME,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val headerError: String? = null,
    val videoError: String? = null
)

class PublisherViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PublisherUiState())
    val uiState: StateFlow<PublisherUiState> = _uiState.asStateFlow()

    private var currentMid: Long = 0L
    private val videoPaging = PagedGridStateMachine(initialKey = 1)

    fun primeVideoDetail(video: VideoItem) {
        VideoDetailRepository.prefetchDetailLanding(video, scope = viewModelScope)
    }

    fun load(
        mid: Long,
        initialHeader: PublisherRepository.PublisherHeader? = null
    ) {
        if (mid <= 0L) {
            videoPaging.reset()
            _uiState.update {
                PublisherUiState(
                    mid = mid,
                    headerError = "无效的发布者 ID",
                    videoError = "无效的发布者 ID"
                )
            }
            currentMid = mid
            return
        }
        if (currentMid == mid && (_uiState.value.isLoading || _uiState.value.page > 0 || _uiState.value.header != null)) {
            return
        }

        currentMid = mid
        videoPaging.reset()
        _uiState.update {
            PublisherUiState(
                mid = mid,
                header = initialHeader,
                isLoading = true
            )
        }

        viewModelScope.launch {
            val headerResult = PublisherRepository.getPublisherHeader(mid)
            if (currentMid != mid) return@launch
            headerResult
                .onSuccess { header ->
                    _uiState.update {
                        it.copy(
                            header = header,
                            headerError = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        if (it.header != null) {
                            it.copy(headerError = null)
                        } else {
                            it.copy(headerError = error.message ?: "发布者信息加载失败")
                        }
                    }
                }

            loadPage(mid = mid, reset = true)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.mid <= 0L) {
            return
        }
        viewModelScope.launch {
            loadPage(mid = state.mid, reset = false)
        }
    }

    fun changeSort(sortOrder: PublisherRepository.PublisherSortOrder) {
        val state = _uiState.value
        if (state.mid <= 0L || state.selectedSort == sortOrder || state.isLoading) {
            return
        }
        _uiState.update { it.copy(selectedSort = sortOrder) }
        videoPaging.reset()
        viewModelScope.launch {
            loadPage(mid = state.mid, reset = true)
        }
    }

    private suspend fun loadPage(
        mid: Long,
        reset: Boolean
    ) {
        if (reset) {
            videoPaging.reset()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    videoError = null,
                    items = emptyList(),
                    page = 0,
                    hasMore = false
                )
            }
        } else {
            val snapshot = videoPaging.snapshot()
            if (snapshot.isLoading || snapshot.endReached) return
            _uiState.update {
                it.copy(
                    isLoadingMore = true,
                    videoError = null
                )
            }
        }

        val startGeneration = videoPaging.snapshot().generation
        val ownerName = _uiState.value.header?.name
        val sortOrder = _uiState.value.selectedSort
        try {
            var fetchedPage: PublisherRepository.PublisherVideosPage? = null
            val loadResult = videoPaging.loadNextPage(
                isRefresh = reset,
                fetch = { pageKey ->
                    PublisherRepository.getPublisherVideos(
                        mid = mid,
                        page = pageKey,
                        ownerName = ownerName,
                        sortOrder = sortOrder
                    ).getOrElse { error -> throw error }
                },
                reduce = { _, pageData ->
                    fetchedPage = pageData
                    PagedGridStateMachine.Update(
                        items = pageData.items,
                        nextKey = if (pageData.hasMore) pageData.page + 1 else pageData.page,
                        endReached = !pageData.hasMore
                    )
                }
            )

            val applied = loadResult.appliedOrNull() ?: return
            val pageData = fetchedPage ?: return
            if (currentMid != mid || _uiState.value.selectedSort != sortOrder) return

            _uiState.update { state ->
                val mergedItems = if (reset) {
                    applied.items
                } else {
                    appendDistinctVideos(state.items, applied.items)
                }
                val normalizedItems = state.header?.name?.takeIf { it.isNotBlank() }?.let { headerName ->
                    mergedItems.map { item ->
                        item.copy(
                            owner = item.owner.copy(
                                mid = mid,
                                name = headerName,
                                face = state.header.face
                            )
                        )
                    }
                } ?: mergedItems

                state.copy(
                    items = normalizedItems,
                    page = pageData.page,
                    hasMore = !videoPaging.snapshot().endReached,
                    isLoading = false,
                    isLoadingMore = false,
                    videoError = null
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (videoPaging.snapshot().generation != startGeneration ||
                currentMid != mid ||
                _uiState.value.selectedSort != sortOrder
            ) {
                return
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    videoError = normalizePublisherVideoErrorMessage(error.message)
                )
            }
        }
    }

    private fun appendDistinctVideos(
        existing: List<VideoItem>,
        incoming: List<VideoItem>
    ): List<VideoItem> {
        if (incoming.isEmpty()) return existing
        val seenKeys = existing.map(::resolveVideoKey).toMutableSet()
        val appended = incoming.filter { item -> seenKeys.add(resolveVideoKey(item)) }
        return if (appended.isEmpty()) existing else existing + appended
    }

    private fun resolveVideoKey(item: VideoItem): String {
        return item.bvid.ifBlank { "aid:${item.aid}:${item.cid}:${item.title}" }
    }
}
