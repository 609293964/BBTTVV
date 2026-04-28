package com.bbttvv.app.feature.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.database.AppDatabase
import com.bbttvv.app.core.database.entity.SearchHistory
import com.bbttvv.app.core.paging.PagedGridStateMachine
import com.bbttvv.app.core.paging.appliedOrNull
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.response.BangumiSearchItem
import com.bbttvv.app.data.model.response.HotItem
import com.bbttvv.app.data.model.response.LiveRoomSearchItem
import com.bbttvv.app.data.model.response.SearchArticleItem
import com.bbttvv.app.data.model.response.SearchType
import com.bbttvv.app.data.model.response.SearchUpItem
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.BlockedUpRepository
import com.bbttvv.app.data.repository.SearchRepository
import com.bbttvv.app.data.repository.VideoDetailRepository
import com.bbttvv.app.data.repository.mergeSearchPageResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val hasSubmittedQuery: Boolean = false,
    val searchType: SearchType = SearchType.VIDEO,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val defaultSearchHint: String = "Search video or creator",
    val defaultSearchHintSearchable: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val historyList: List<SearchHistory> = emptyList(),
    val hotList: List<HotItem> = emptyList(),
    val discoverTitle: String = "Discover",
    val discoverList: List<String> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val hasMoreResults: Boolean = false,
    val isLoadingMore: Boolean = false,
    val videoResults: List<VideoItem> = emptyList(),
    val upResults: List<SearchUpItem> = emptyList(),
    val bangumiResults: List<BangumiSearchItem> = emptyList(),
    val liveResults: List<LiveRoomSearchItem> = emptyList(),
    val articleResults: List<SearchArticleItem> = emptyList()
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val searchHistoryDao = AppDatabase.getDatabase(application).searchHistoryDao()
    private val blockedUpRepository = BlockedUpRepository(application)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState = _uiState.asStateFlow()

    private data class SearchPageKey(
        val keyword: String = "",
        val searchType: SearchType = SearchType.VIDEO,
        val page: Int = 1
    )

    private sealed interface SearchPagePayload {
        val pageInfo: SearchRepository.SearchPageInfo

        data class Videos(
            val items: List<VideoItem>,
            override val pageInfo: SearchRepository.SearchPageInfo
        ) : SearchPagePayload

        data class Ups(
            val items: List<SearchUpItem>,
            override val pageInfo: SearchRepository.SearchPageInfo
        ) : SearchPagePayload

        data class BangumiItems(
            val items: List<BangumiSearchItem>,
            override val pageInfo: SearchRepository.SearchPageInfo
        ) : SearchPagePayload

        data class LiveRooms(
            val items: List<LiveRoomSearchItem>,
            override val pageInfo: SearchRepository.SearchPageInfo
        ) : SearchPagePayload

        data class Articles(
            val items: List<SearchArticleItem>,
            override val pageInfo: SearchRepository.SearchPageInfo
        ) : SearchPagePayload
    }

    private val searchPaging = PagedGridStateMachine(initialKey = SearchPageKey())
    private var suggestJob: Job? = null
    private var loadMoreJob: Job? = null
    private var blockedMids: Set<Long> = emptySet()

    init {
        observeHistory()
        observeBlockedUps()
        loadDefaultSearchHint()
        loadHotSearch()
    }

    fun primeVideoDetail(video: VideoItem) {
        VideoDetailRepository.prefetchDetailLanding(video, scope = viewModelScope)
    }

    fun onQueryChange(query: String) {
        searchPaging.resetTo(SearchPageKey())
        loadMoreJob?.cancel()
        loadMoreJob = null
        _uiState.update {
            it.copy(
                query = query,
                suggestions = if (query.isBlank()) emptyList() else it.suggestions,
                isSearching = false,
                isLoadingMore = false,
                hasMoreResults = false,
                errorMessage = null
            )
        }
        if (query.isBlank()) {
            suggestJob?.cancel()
            _uiState.update { it.copy(suggestions = emptyList()) }
        } else {
            loadSuggestions(query)
        }
    }

    fun setSearchType(type: SearchType) {
        _uiState.update { it.copy(searchType = type) }
        if (_uiState.value.hasSubmittedQuery && _uiState.value.query.isNotBlank()) {
            search()
        }
    }

    fun applyKeyword(keyword: String) {
        _uiState.update { it.copy(query = keyword) }
        search()
    }

    fun search() {
        val state = _uiState.value
        val typedKeyword = state.query.trim()
        val keyword = if (typedKeyword.isNotBlank()) {
            typedKeyword
        } else if (state.defaultSearchHintSearchable) {
            state.defaultSearchHint.trim()
        } else {
            ""
        }
        if (keyword.isBlank()) {
            return
        }

        val requestType = state.searchType
        loadMoreJob?.cancel()
        loadMoreJob = null
        searchPaging.resetTo(
            SearchPageKey(
                keyword = keyword,
                searchType = requestType,
                page = 1
            )
        )

        _uiState.update {
            it.copy(
                query = keyword,
                hasSubmittedQuery = true,
                isSearching = true,
                errorMessage = null,
                suggestions = emptyList(),
                currentPage = 1,
                totalPages = 1,
                hasMoreResults = false,
                isLoadingMore = false,
                videoResults = emptyList(),
                upResults = emptyList(),
                bangumiResults = emptyList(),
                liveResults = emptyList(),
                articleResults = emptyList()
            )
        }
        saveHistory(keyword)

        viewModelScope.launch {
            runSearch(isRefresh = true)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMoreResults || state.isLoadingMore || state.isSearching || state.query.isBlank()) {
            return
        }

        val pagingState = searchPaging.snapshot()
        val nextKey = pagingState.nextKey
        if (pagingState.isLoading ||
            pagingState.endReached ||
            nextKey.keyword != state.query ||
            nextKey.searchType != state.searchType
        ) {
            return
        }

        _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            runSearch(isRefresh = false)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearAll()
        }
    }

    fun deleteHistory(item: SearchHistory) {
        viewModelScope.launch {
            searchHistoryDao.delete(item)
        }
    }

    private suspend fun runSearch(isRefresh: Boolean) {
        val initialSnapshot = searchPaging.snapshot()
        val requestKey = initialSnapshot.nextKey
        val startGeneration = initialSnapshot.generation
        if (requestKey.keyword.isBlank()) return

        try {
            val loadResult = searchPaging.loadNextPage(
                isRefresh = isRefresh,
                fetch = { key -> fetchSearchPage(key) },
                reduce = { key, payload ->
                    PagedGridStateMachine.Update(
                        items = listOf(payload),
                        nextKey = key.copy(page = payload.pageInfo.currentPage + 1),
                        endReached = !payload.pageInfo.hasMore
                    )
                }
            )

            val payload = loadResult.appliedOrNull()?.items?.firstOrNull() ?: return
            if (!shouldApplySearchPage(requestKey, startGeneration)) return
            applySearchPage(payload = payload, merge = !isRefresh)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!shouldApplySearchPage(requestKey, startGeneration)) return
            _uiState.update {
                it.copy(
                    isSearching = false,
                    isLoadingMore = false,
                    errorMessage = error.message ?: "Search failed."
                )
            }
        } finally {
            if (searchPaging.snapshot().generation == startGeneration) {
                loadMoreJob = null
            }
        }
    }

    private suspend fun fetchSearchPage(key: SearchPageKey): SearchPagePayload {
        return when (key.searchType) {
            SearchType.VIDEO -> {
                val (videos, pageInfo) = SearchRepository.search(
                    keyword = key.keyword,
                    page = key.page
                ).getOrElse { error -> throw error }
                SearchPagePayload.Videos(
                    items = videos.filter { it.owner.mid !in blockedMids },
                    pageInfo = pageInfo
                )
            }

            SearchType.UP -> {
                val (ups, pageInfo) = SearchRepository.searchUp(
                    keyword = key.keyword,
                    page = key.page
                ).getOrElse { error -> throw error }
                SearchPagePayload.Ups(
                    items = ups.filter { it.mid !in blockedMids },
                    pageInfo = pageInfo
                )
            }

            SearchType.BANGUMI, SearchType.MEDIA_FT -> {
                val result = if (key.searchType == SearchType.BANGUMI) {
                    SearchRepository.searchBangumi(keyword = key.keyword, page = key.page)
                } else {
                    SearchRepository.searchMediaFt(keyword = key.keyword, page = key.page)
                }
                val (items, pageInfo) = result.getOrElse { error -> throw error }
                SearchPagePayload.BangumiItems(items = items, pageInfo = pageInfo)
            }

            SearchType.LIVE -> {
                val (liveRooms, pageInfo) = SearchRepository.searchLive(
                    keyword = key.keyword,
                    page = key.page
                ).getOrElse { error -> throw error }
                SearchPagePayload.LiveRooms(
                    items = liveRooms.filter { it.uid !in blockedMids },
                    pageInfo = pageInfo
                )
            }

            SearchType.ARTICLE -> {
                val (articles, pageInfo) = SearchRepository.searchArticle(
                    keyword = key.keyword,
                    page = key.page
                ).getOrElse { error -> throw error }
                SearchPagePayload.Articles(items = articles, pageInfo = pageInfo)
            }
        }
    }

    private fun applySearchPage(payload: SearchPagePayload, merge: Boolean) {
        val pageInfo = payload.pageInfo
        _uiState.update { state ->
            val base = state.copy(
                isSearching = false,
                isLoadingMore = false,
                errorMessage = null,
                currentPage = pageInfo.currentPage,
                totalPages = pageInfo.totalPages,
                hasMoreResults = pageInfo.hasMore
            )
            when (payload) {
                is SearchPagePayload.Videos -> base.copy(
                    videoResults = if (merge) {
                        mergeSearchPageResults(state.videoResults, payload.items) { it.bvid }
                    } else {
                        payload.items
                    }
                )

                is SearchPagePayload.Ups -> base.copy(
                    upResults = if (merge) {
                        mergeSearchPageResults(state.upResults, payload.items) { it.mid }
                    } else {
                        payload.items
                    }
                )

                is SearchPagePayload.BangumiItems -> base.copy(
                    bangumiResults = if (merge) {
                        mergeSearchPageResults(state.bangumiResults, payload.items) { it.seasonId }
                    } else {
                        payload.items
                    }
                )

                is SearchPagePayload.LiveRooms -> base.copy(
                    liveResults = if (merge) {
                        mergeSearchPageResults(state.liveResults, payload.items) { it.roomid }
                    } else {
                        payload.items
                    }
                )

                is SearchPagePayload.Articles -> base.copy(
                    articleResults = if (merge) {
                        mergeSearchPageResults(state.articleResults, payload.items) { it.id }
                    } else {
                        payload.items
                    }
                )
            }
        }
    }

    private fun shouldApplySearchPage(
        key: SearchPageKey,
        generation: Long
    ): Boolean {
        val state = _uiState.value
        return searchPaging.snapshot().generation == generation &&
            state.query == key.keyword &&
            state.searchType == key.searchType
    }

    private fun observeHistory() {
        viewModelScope.launch {
            searchHistoryDao.getAll().collect { historyList ->
                _uiState.update { it.copy(historyList = historyList) }
                updateDiscover(historyList)
            }
        }
    }

    private fun observeBlockedUps() {
        viewModelScope.launch {
            blockedUpRepository.getAllBlockedUps().collect { blockedUps ->
                blockedMids = blockedUps.map { it.mid }.toSet()
            }
        }
    }

    private fun loadDefaultSearchHint() {
        viewModelScope.launch {
            SearchRepository.getDefaultSearchHint().onSuccess { hint ->
                if (hint.isNotBlank()) {
                    _uiState.update {
                        it.copy(
                            defaultSearchHint = hint,
                            defaultSearchHintSearchable = true
                        )
                    }
                }
            }
        }
    }

    private fun loadHotSearch() {
        viewModelScope.launch {
            SearchRepository.getHotSearch().onSuccess { items ->
                _uiState.update { it.copy(hotList = items) }
            }
        }
    }

    private fun loadSuggestions(keyword: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(300L)
            SearchRepository.getSuggest(keyword).onSuccess { suggestions ->
                _uiState.update {
                    if (it.query == keyword) {
                        it.copy(suggestions = suggestions.take(8))
                    } else {
                        it
                    }
                }
            }
        }
    }

    private fun updateDiscover(history: List<SearchHistory>) {
        viewModelScope.launch {
            val historyKeywords = history.map { it.keyword }
            SearchRepository.getSearchDiscover(historyKeywords).onSuccess { (title, list) ->
                _uiState.update {
                    it.copy(
                        discoverTitle = title,
                        discoverList = list
                    )
                }
            }
        }
    }

    private fun saveHistory(keyword: String) {
        viewModelScope.launch {
            if (SettingsManager.isPrivacyModeEnabledSync(getApplication())) {
                return@launch
            }
            searchHistoryDao.insert(
                SearchHistory(
                    keyword = keyword,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }
}
