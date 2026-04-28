package com.bbttvv.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.paging.PagedGridStateMachine
import com.bbttvv.app.core.paging.appliedOrNull
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.LiveRoom
import com.bbttvv.app.data.repository.LiveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveCategory(
    val id: String,
    val label: String,
    val parentAreaId: Int = 0,
    val areaId: Int = 0
)

val fallbackLiveCategories = listOf(
    LiveCategory(id = "recommend", label = "推荐直播"),
    LiveCategory(id = "parent:2", label = "网游", parentAreaId = 2),
    LiveCategory(id = "parent:3", label = "手游", parentAreaId = 3),
    LiveCategory(id = "parent:6", label = "单机", parentAreaId = 6),
    LiveCategory(id = "parent:9", label = "虚拟主播", parentAreaId = 9),
    LiveCategory(id = "parent:1", label = "娱乐", parentAreaId = 1),
    LiveCategory(id = "parent:5", label = "电台", parentAreaId = 5),
    LiveCategory(id = "parent:13", label = "赛事", parentAreaId = 13),
    LiveCategory(id = "parent:14", label = "聊天室", parentAreaId = 14),
    LiveCategory(id = "parent:10", label = "生活", parentAreaId = 10),
    LiveCategory(id = "parent:11", label = "知识", parentAreaId = 11)
)

data class LiveUiState(
    val categories: List<LiveCategory> = fallbackLiveCategories,
    val selectedCategoryIndex: Int = 0,
    val liveRooms: List<LiveRoom> = emptyList(),
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val errorMsg: String? = null,
    val hasMore: Boolean = true
)

class LiveViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(LiveUiState())
    val uiState: StateFlow<LiveUiState> = _uiState.asStateFlow()

    private val categoryRooms = mutableMapOf<String, List<LiveRoom>>()
    private val categoryHasMore = mutableMapOf<String, Boolean>()
    private val categoryPaging = mutableMapOf<String, PagedGridStateMachine<Int>>()
    private var initialLoadStarted = false

    fun onEnter() {
        if (!initialLoadStarted) {
            loadInitial()
            return
        }

        viewModelScope.launch {
            loadRoomsForCategory(index = _uiState.value.selectedCategoryIndex, refresh = true)
        }
    }

    private fun loadInitial() {
        if (initialLoadStarted) return
        initialLoadStarted = true

        viewModelScope.launch {
            loadCategories()
            loadRoomsForCategory(index = 0, refresh = true)
        }
    }

    fun selectCategory(index: Int) {
        val categories = _uiState.value.categories
        val safeIndex = index.coerceIn(0, categories.lastIndex)
        val selectedCategory = categories.getOrNull(safeIndex) ?: return

        if (safeIndex == _uiState.value.selectedCategoryIndex) {
            viewModelScope.launch { loadRoomsForCategory(index = safeIndex, refresh = true) }
            return
        }

        val cachedRooms = categoryRooms[selectedCategory.id].orEmpty()
        _uiState.update {
            it.copy(
                selectedCategoryIndex = safeIndex,
                liveRooms = cachedRooms,
                isError = false,
                errorMsg = null,
                hasMore = categoryHasMore[selectedCategory.id]
                    ?: !pagingForCategory(selectedCategory.id).snapshot().endReached
            )
        }

        if (cachedRooms.isEmpty()) {
            viewModelScope.launch { loadRoomsForCategory(index = safeIndex, refresh = true) }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore) return
        viewModelScope.launch {
            loadRoomsForCategory(index = state.selectedCategoryIndex, refresh = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadRoomsForCategory(index = _uiState.value.selectedCategoryIndex, refresh = true)
        }
    }

    private suspend fun loadCategories() {
        val result = LiveRepository.getLiveAreas()
        val apiCategories = result.getOrDefault(emptyList())
            .map { parent ->
                LiveCategory(
                    id = "parent:${parent.id}",
                    label = parent.name,
                    parentAreaId = parent.id
                )
            }
            .filter { it.parentAreaId > 0 && it.label.isNotBlank() }

        val categories = if (apiCategories.isNotEmpty()) {
            listOf(fallbackLiveCategories.first()) + apiCategories
        } else {
            fallbackLiveCategories
        }

        _uiState.update { state ->
            state.copy(
                categories = categories,
                selectedCategoryIndex = state.selectedCategoryIndex.coerceIn(0, categories.lastIndex)
            )
        }
    }

    private suspend fun loadRoomsForCategory(index: Int, refresh: Boolean) {
        val category = _uiState.value.categories.getOrNull(index) ?: return
        val categoryKey = category.id
        val paging = pagingForCategory(categoryKey)
        if (refresh) {
            paging.reset()
        } else {
            val snapshot = paging.snapshot()
            if (snapshot.isLoading || snapshot.endReached) return
        }

        val startGeneration = paging.snapshot().generation
        val page = paging.snapshot().nextKey
        _uiState.update {
            val selectedKey = it.categories.getOrNull(it.selectedCategoryIndex)?.id
            if (selectedKey == categoryKey) {
                it.copy(
                    isLoading = true,
                    isError = false,
                    errorMsg = null
                )
            } else {
                it
            }
        }

        try {
            Logger.d("LiveViewModel", "Loading live category=${category.label}, parent=${category.parentAreaId}, page=$page")
            val loadResult = paging.loadNextPage(
                isRefresh = refresh,
                fetch = { pageKey ->
                    LiveRepository.getLiveRooms(
                        page = pageKey,
                        parentAreaId = category.parentAreaId
                    ).getOrElse { error -> throw error }
                },
                reduce = { pageKey, rooms ->
                    PagedGridStateMachine.Update(
                        items = rooms,
                        nextKey = if (rooms.isNotEmpty()) pageKey + 1 else pageKey,
                        endReached = rooms.isEmpty()
                    )
                }
            )

            val applied = loadResult.appliedOrNull() ?: return
            val mergedRooms = if (refresh) {
                applied.items
            } else {
                categoryRooms[categoryKey].orEmpty() + applied.items
            }
            categoryRooms[categoryKey] = mergedRooms
            categoryHasMore[categoryKey] = !paging.snapshot().endReached

            _uiState.update { state ->
                val selectedKey = state.categories.getOrNull(state.selectedCategoryIndex)?.id
                if (selectedKey == categoryKey) {
                    state.copy(
                        liveRooms = mergedRooms,
                        isLoading = false,
                        isError = false,
                        errorMsg = null,
                        hasMore = categoryHasMore[categoryKey] ?: true
                    )
                } else {
                    state
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (paging.snapshot().generation != startGeneration) return
            Logger.e("LiveViewModel", "Failed to load live category=${category.label}", error)
            _uiState.update { state ->
                val selectedKey = state.categories.getOrNull(state.selectedCategoryIndex)?.id
                if (selectedKey == categoryKey) {
                    state.copy(
                        isLoading = false,
                        isError = true,
                        errorMsg = error.message ?: "Failed to load live rooms"
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun pagingForCategory(categoryKey: String): PagedGridStateMachine<Int> {
        return categoryPaging.getOrPut(categoryKey) { PagedGridStateMachine(initialKey = 1) }
    }
}
