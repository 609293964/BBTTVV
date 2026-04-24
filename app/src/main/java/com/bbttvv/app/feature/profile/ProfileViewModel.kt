package com.bbttvv.app.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.history.PlaybackHistorySyncBus
import com.bbttvv.app.core.history.PlaybackHistorySyncEvent
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.paging.PagedGridStateMachine
import com.bbttvv.app.core.paging.appliedOrNull
import com.bbttvv.app.core.store.AccountSessionStore
import com.bbttvv.app.core.store.StoredAccountSession
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.FavFolder
import com.bbttvv.app.data.model.response.HistoryBusiness
import com.bbttvv.app.data.model.response.HistoryData
import com.bbttvv.app.data.model.response.NavData
import com.bbttvv.app.data.model.response.NavStatData
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.data.repository.ActionRepository
import com.bbttvv.app.data.repository.CreatorCardStats
import com.bbttvv.app.data.repository.FavoriteRepository
import com.bbttvv.app.data.repository.HistoryRepository
import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import com.bbttvv.app.data.repository.VideoDetailRepository
import com.bbttvv.app.data.repository.VideoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HISTORY_PAGE_SIZE = 30
private const val HISTORY_INITIAL_BOOTSTRAP_DAY_BUCKETS = 2
private const val HISTORY_INITIAL_BOOTSTRAP_MAX_ITEMS = 60
private const val HISTORY_INITIAL_BOOTSTRAP_MAX_PAGES = 3
private const val HISTORY_DETAIL_ENRICH_CONCURRENCY = 4
private const val FAVORITE_PAGE_SIZE = 30
private const val COLLECTED_FAVORITE_FOLDER_PAGE_SIZE = 40
private const val COLLECTED_FAVORITE_FOLDER_MAX_PAGES = 6

data class ProfileUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val navData: NavData? = null,
    val navStatData: NavStatData? = null,
    val creatorStats: CreatorCardStats? = null,
    val storedAccounts: List<StoredAccountSession> = emptyList(),
    val storedAccountCount: Int = 0,
    val activeAccountMid: Long? = null,
    val historyItems: List<HistoryData> = emptyList(),
    val historyHasMore: Boolean = false,
    val isHistoryLoading: Boolean = false,
    val isHistoryLoadingMore: Boolean = false,
    val historyErrorMessage: String? = null,
    val favoriteFolders: List<FavFolder> = emptyList(),
    val selectedFavoriteFolderKey: String? = null,
    val favoriteItems: List<VideoItem> = emptyList(),
    val favoriteHasMore: Boolean = false,
    val isFavoriteLoading: Boolean = false,
    val isFavoriteLoadingMore: Boolean = false,
    val favoriteErrorMessage: String? = null,
    val watchLaterItems: List<VideoItem> = emptyList(),
    val watchLaterTotalCount: Int = 0,
    val isWatchLaterLoading: Boolean = false,
    val watchLaterErrorMessage: String? = null
)

class ProfileViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var historyCursorMax = 0L
    private var historyCursorViewAt = 0L
    private var historyCursorBusiness: String? = null
    private var historyLoadedForMid: Long? = null
    private var pendingHistorySyncEvent: PlaybackHistorySyncEvent? = null
    private var historyEnrichmentJob: Job? = null
    private var favoriteLoadedForMid: Long? = null
    private var watchLaterLoadedForMid: Long? = null
    private val favoritePaging = PagedGridStateMachine(initialKey = 1)
    private var favoriteSelectedFolder: FavFolder? = null

    fun primeVideoDetail(video: VideoItem) {
        VideoDetailRepository.prefetchDetailLanding(video, scope = viewModelScope)
    }

    init {
        observePlaybackHistorySync()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val accounts = AccountSessionStore.getAccounts(context)
            val activeMid = AccountSessionStore.getActiveAccountMid(context)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    storedAccounts = accounts,
                    storedAccountCount = accounts.size,
                    activeAccountMid = activeMid
                )
            }

            val navData = SubtitleAndAuxRepository.getNavInfo().getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "加载我的页面失败",
                        navData = null,
                        navStatData = null,
                        creatorStats = null,
                        historyItems = emptyList(),
                        historyHasMore = false,
                        isHistoryLoading = false,
                        isHistoryLoadingMore = false,
                        historyErrorMessage = null,
                        favoriteFolders = emptyList(),
                        selectedFavoriteFolderKey = null,
                        favoriteItems = emptyList(),
                        favoriteHasMore = false,
                        isFavoriteLoading = false,
                        isFavoriteLoadingMore = false,
                        favoriteErrorMessage = null,
                        watchLaterItems = emptyList(),
                        watchLaterTotalCount = 0,
                        isWatchLaterLoading = false,
                        watchLaterErrorMessage = null
                    )
                }
                return@launch
            }

            if (!navData.isLogin) {
                historyLoadedForMid = null
                favoriteLoadedForMid = null
                watchLaterLoadedForMid = null
                resetHistoryState()
                resetFavoriteState()
                resetWatchLaterState()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        navData = navData,
                        navStatData = null,
                        creatorStats = null,
                        storedAccounts = accounts,
                        storedAccountCount = accounts.size,
                        activeAccountMid = activeMid
                    )
                }
                return@launch
            }

            val navStatData = runCatching {
                NetworkModule.api.getNavStat()
            }.getOrNull()?.takeIf { it.code == 0 }?.data

            val creatorStats = SubtitleAndAuxRepository.getCreatorCardStats(navData.mid).getOrNull()

            AccountSessionStore.upsertCurrentAccount(context, navData)
            val updatedAccounts = AccountSessionStore.getAccounts(context)
            val updatedActiveMid = AccountSessionStore.getActiveAccountMid(context) ?: navData.mid

            val shouldReloadHistory = historyLoadedForMid != navData.mid
            val shouldReloadFavorite = favoriteLoadedForMid != navData.mid
            val shouldReloadWatchLater = watchLaterLoadedForMid != navData.mid
            if (shouldReloadHistory) {
                historyLoadedForMid = null
                resetHistoryState()
            }
            if (shouldReloadFavorite) {
                favoriteLoadedForMid = null
                resetFavoriteState()
            }
            if (shouldReloadWatchLater) {
                watchLaterLoadedForMid = null
                resetWatchLaterState()
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    navData = navData,
                    navStatData = navStatData,
                    creatorStats = creatorStats,
                    storedAccounts = updatedAccounts,
                    storedAccountCount = updatedAccounts.size,
                    activeAccountMid = updatedActiveMid
                )
            }

            ensureHistoryLoaded(force = shouldReloadHistory)
        }
    }

    fun ensureHistoryLoaded(force: Boolean = false) {
        val navData = _uiState.value.navData ?: return
        if (!navData.isLogin) return

        if (_uiState.value.isHistoryLoading || _uiState.value.isHistoryLoadingMore) return
        val hasPendingSync = pendingHistorySyncEvent?.mid == navData.mid
        if (!force && !hasPendingSync && historyLoadedForMid == navData.mid) {
            return
        }

        viewModelScope.launch {
            loadHistory(reset = true, expectedMid = navData.mid)
        }
    }

    fun ensureFavoriteLoaded(force: Boolean = false) {
        val navData = _uiState.value.navData ?: return
        if (!navData.isLogin) return

        if (!force) {
            if (_uiState.value.isFavoriteLoading || _uiState.value.isFavoriteLoadingMore) return
            if (favoriteLoadedForMid == navData.mid) return
        }

        viewModelScope.launch {
            loadFavoriteFolders(reset = true, expectedMid = navData.mid)
        }
    }

    fun ensureWatchLaterLoaded(force: Boolean = false) {
        val navData = _uiState.value.navData ?: return
        if (!navData.isLogin) return

        if (_uiState.value.isWatchLaterLoading) return
        if (!force && watchLaterLoadedForMid == navData.mid) return

        viewModelScope.launch {
            loadWatchLater(expectedMid = navData.mid)
        }
    }

    fun loadMoreHistory() {
        val state = _uiState.value
        val navData = state.navData ?: return
        if (!navData.isLogin) return
        if (!state.historyHasMore || state.isHistoryLoading || state.isHistoryLoadingMore) return

        viewModelScope.launch {
            loadHistory(reset = false, expectedMid = navData.mid)
        }
    }

    fun loadMoreFavorites() {
        val state = _uiState.value
        val navData = state.navData ?: return
        val folder = favoriteSelectedFolder ?: return
        if (!navData.isLogin) return
        if (!state.favoriteHasMore || state.isFavoriteLoading || state.isFavoriteLoadingMore) return

        viewModelScope.launch {
            loadFavoriteItems(reset = false, expectedMid = navData.mid, folder = folder)
        }
    }

    fun selectFavoriteFolder(folderKey: String) {
        val navData = _uiState.value.navData ?: return
        if (!navData.isLogin) return
        if (_uiState.value.isFavoriteLoading || _uiState.value.isFavoriteLoadingMore) return

        val folder = _uiState.value.favoriteFolders.firstOrNull { candidate ->
            resolveProfileFavoriteFolderKey(candidate) == folderKey
        } ?: return

        if (_uiState.value.selectedFavoriteFolderKey == folderKey &&
            _uiState.value.favoriteItems.isNotEmpty()
        ) {
            return
        }

        viewModelScope.launch {
            loadFavoriteItems(reset = true, expectedMid = navData.mid, folder = folder)
        }
    }

    fun switchAccount(mid: Long) {
        if (mid <= 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val activated = AccountSessionStore.activateAccount(context, mid)
            if (!activated) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "切换账号失败"
                    )
                }
                return@launch
            }

            VideoRepository.invalidateAccountScopedCaches()
            historyLoadedForMid = null
            favoriteLoadedForMid = null
            watchLaterLoadedForMid = null
            resetHistoryState()
            resetFavoriteState()
            resetWatchLaterState()
            refresh()
        }
    }

    fun removeStoredAccount(mid: Long) {
        if (mid <= 0L) return
        viewModelScope.launch {
            AccountSessionStore.removeAccount(context, mid)
            val accounts = AccountSessionStore.getAccounts(context)
            _uiState.update {
                it.copy(
                    storedAccounts = accounts,
                    storedAccountCount = accounts.size,
                    activeAccountMid = AccountSessionStore.getActiveAccountMid(context)
                )
            }
        }
    }

    fun logout(removeStoredAccount: Boolean) {
        viewModelScope.launch {
            val currentMid = _uiState.value.navData?.mid ?: AccountSessionStore.getActiveAccountMid(context)
            if (removeStoredAccount && currentMid != null) {
                AccountSessionStore.removeAccount(context, currentMid)
            } else {
                AccountSessionStore.clearActiveAccount(context)
            }

            TokenManager.clear(context)
            NetworkModule.clearRuntimeCookies()
            VideoRepository.invalidateAccountScopedCaches()
            historyLoadedForMid = null
            favoriteLoadedForMid = null
            watchLaterLoadedForMid = null
            resetHistoryState()
            resetFavoriteState()
            resetWatchLaterState()

            val accounts = AccountSessionStore.getAccounts(context)
            _uiState.update {
                ProfileUiState(
                    isLoading = false,
                    navData = NavData(isLogin = false),
                    storedAccounts = accounts,
                    storedAccountCount = accounts.size,
                    activeAccountMid = AccountSessionStore.getActiveAccountMid(context)
                )
            }
        }
    }

    fun removeWatchLater(video: VideoItem) {
        val aid = video.aid.takeIf { it > 0L } ?: video.id.takeIf { it > 0L } ?: 0L
        if (aid <= 0L) {
            _uiState.update { it.copy(watchLaterErrorMessage = "无法移除：缺少视频 AID") }
            return
        }

        viewModelScope.launch {
            val previousItems = _uiState.value.watchLaterItems
            val previousCount = _uiState.value.watchLaterTotalCount
            val bvid = video.bvid.trim()
            _uiState.update { state ->
                val nextItems = state.watchLaterItems.filterNot { item ->
                    item.aid == aid ||
                        item.id == aid ||
                        (bvid.isNotEmpty() && item.bvid == bvid)
                }
                state.copy(
                    watchLaterItems = nextItems,
                    watchLaterTotalCount = if (previousCount > 0) {
                        (previousCount - (state.watchLaterItems.size - nextItems.size)).coerceAtLeast(0)
                    } else {
                        nextItems.size
                    },
                    watchLaterErrorMessage = null
                )
            }

            val result = ActionRepository.toggleWatchLater(aid = aid, add = false)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        watchLaterItems = previousItems,
                        watchLaterTotalCount = previousCount,
                        watchLaterErrorMessage = result.exceptionOrNull()?.message ?: "移除稍后再看失败"
                    )
                }
            }
        }
    }

    private suspend fun loadHistory(
        reset: Boolean,
        expectedMid: Long
    ) {
        if (reset) {
            historyEnrichmentJob?.cancel()
        }
        if (reset) {
            resetHistoryCursor()
            _uiState.update {
                it.copy(
                    isHistoryLoading = true,
                    isHistoryLoadingMore = false,
                    historyErrorMessage = null,
                    historyItems = emptyList(),
                    historyHasMore = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isHistoryLoadingMore = true,
                    historyErrorMessage = null
                )
            }
        }

        var nextMax = if (reset) 0L else historyCursorMax
        var nextViewAt = if (reset) 0L else historyCursorViewAt
        var nextBusiness = if (reset) null else historyCursorBusiness
        var mergedItems = if (reset) emptyList() else _uiState.value.historyItems
        var hasMore = false
        var fetchedPageCount = 0

        do {
            val result = HistoryRepository.getHistoryList(
                ps = HISTORY_PAGE_SIZE,
                max = nextMax,
                viewAt = nextViewAt,
                business = nextBusiness
            )

            val historyResult = result.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isHistoryLoading = false,
                        isHistoryLoadingMore = false,
                        historyErrorMessage = error.message ?: "加载历史记录失败"
                    )
                }
                return
            }

            if (_uiState.value.navData?.mid != expectedMid) return

            historyResult.cursor?.let { cursor ->
                nextMax = cursor.max
                nextViewAt = cursor.view_at
                nextBusiness = cursor.business.ifBlank { null }
            } ?: run {
                nextMax = 0L
                nextViewAt = 0L
                nextBusiness = null
            }

            mergedItems = if (reset && fetchedPageCount == 0) {
                historyResult.list
            } else {
                appendUniqueHistoryItems(
                    existing = mergedItems,
                    incoming = historyResult.list
                )
            }
            hasMore = historyResult.cursor?.max?.let { max -> max > 0L } == true &&
                historyResult.list.isNotEmpty()
            fetchedPageCount += 1
        } while (
            reset &&
            hasMore &&
            fetchedPageCount < HISTORY_INITIAL_BOOTSTRAP_MAX_PAGES &&
            mergedItems.size < HISTORY_INITIAL_BOOTSTRAP_MAX_ITEMS &&
            resolveHistoryDayBucketCount(mergedItems) < HISTORY_INITIAL_BOOTSTRAP_DAY_BUCKETS
        )

        mergedItems = applyCachedHistoryViewInfo(mergedItems)
        historyCursorMax = nextMax
        historyCursorViewAt = nextViewAt
        historyCursorBusiness = nextBusiness
        historyLoadedForMid = expectedMid
        if (pendingHistorySyncEvent?.mid == expectedMid) {
            pendingHistorySyncEvent = null
        }
        _uiState.update {
            it.copy(
                historyItems = mergedItems,
                historyHasMore = hasMore,
                isHistoryLoading = false,
                isHistoryLoadingMore = false,
                historyErrorMessage = null
            )
        }
        scheduleHistoryDetailEnrichment(expectedMid = expectedMid, items = mergedItems)
    }

    private suspend fun loadFavoriteFolders(
        reset: Boolean,
        expectedMid: Long
    ) {
        if (reset) {
            favoritePaging.reset()
            favoriteSelectedFolder = null
            _uiState.update {
                it.copy(
                    favoriteFolders = emptyList(),
                    selectedFavoriteFolderKey = null,
                    favoriteItems = emptyList(),
                    favoriteHasMore = false,
                    isFavoriteLoading = true,
                    isFavoriteLoadingMore = false,
                    favoriteErrorMessage = null
                )
            }
        }

        val folders = runCatching {
            fetchFavoriteFolders(expectedMid)
        }.getOrElse { error ->
            _uiState.update {
                it.copy(
                    isFavoriteLoading = false,
                    isFavoriteLoadingMore = false,
                    favoriteErrorMessage = error.message ?: "加载收藏夹失败"
                )
            }
            return
        }

        if (_uiState.value.navData?.mid != expectedMid) return

        if (folders.isEmpty()) {
            favoriteLoadedForMid = expectedMid
            _uiState.update {
                it.copy(
                    favoriteFolders = emptyList(),
                    selectedFavoriteFolderKey = null,
                    favoriteItems = emptyList(),
                    favoriteHasMore = false,
                    isFavoriteLoading = false,
                    isFavoriteLoadingMore = false,
                    favoriteErrorMessage = null
                )
            }
            return
        }

        val selectedFolder = favoriteSelectedFolder
            ?.let { current ->
                folders.firstOrNull {
                    resolveProfileFavoriteFolderKey(it) == resolveProfileFavoriteFolderKey(current)
                }
            }
            ?: folders.first()

        _uiState.update {
            it.copy(
                favoriteFolders = folders,
                selectedFavoriteFolderKey = resolveProfileFavoriteFolderKey(selectedFolder),
                favoriteItems = emptyList(),
                favoriteHasMore = false,
                isFavoriteLoading = true,
                isFavoriteLoadingMore = false,
                favoriteErrorMessage = null
            )
        }

        loadFavoriteItems(reset = true, expectedMid = expectedMid, folder = selectedFolder)
    }

    private suspend fun loadFavoriteItems(
        reset: Boolean,
        expectedMid: Long,
        folder: FavFolder
    ) {
        if (reset) {
            favoritePaging.reset()
            favoriteSelectedFolder = folder
            _uiState.update {
                it.copy(
                    selectedFavoriteFolderKey = resolveProfileFavoriteFolderKey(folder),
                    favoriteItems = emptyList(),
                    favoriteHasMore = false,
                    isFavoriteLoading = true,
                    isFavoriteLoadingMore = false,
                    favoriteErrorMessage = null
                )
            }
        } else {
            val snapshot = favoritePaging.snapshot()
            if (snapshot.isLoading || snapshot.endReached) return
            _uiState.update {
                it.copy(
                    isFavoriteLoadingMore = true,
                    favoriteErrorMessage = null
                )
            }
        }

        val startGeneration = favoritePaging.snapshot().generation
        val folderKey = resolveProfileFavoriteFolderKey(folder)
        val mediaId = resolveProfileFavoriteFolderMediaId(folder)
        try {
            val loadResult = favoritePaging.loadNextPage(
                isRefresh = reset,
                fetch = { pageKey ->
                    FavoriteRepository.getFavoriteList(
                        mediaId = mediaId,
                        pn = pageKey,
                        ps = FAVORITE_PAGE_SIZE
                    ).getOrElse { error -> throw error }
                },
                reduce = { pageKey, favoriteData ->
                    val incomingItems = favoriteData.medias.orEmpty().map { item -> item.toVideoItem() }
                    val hasMore = favoriteData.has_more ||
                        (favoriteData.medias?.size ?: 0) >= FAVORITE_PAGE_SIZE
                    PagedGridStateMachine.Update(
                        items = incomingItems,
                        nextKey = if (hasMore) pageKey + 1 else pageKey,
                        endReached = !hasMore
                    )
                }
            )

            val applied = loadResult.appliedOrNull() ?: return
            if (_uiState.value.navData?.mid != expectedMid ||
                resolveProfileFavoriteFolderKey(favoriteSelectedFolder ?: folder) != folderKey
            ) {
                return
            }

            val mergedItems = if (reset) {
                applied.items
            } else {
                appendUniqueVideoItems(
                    existing = _uiState.value.favoriteItems,
                    incoming = applied.items
                )
            }

            favoriteSelectedFolder = folder
            favoriteLoadedForMid = expectedMid
            _uiState.update {
                it.copy(
                    selectedFavoriteFolderKey = folderKey,
                    favoriteItems = mergedItems,
                    favoriteHasMore = !favoritePaging.snapshot().endReached,
                    isFavoriteLoading = false,
                    isFavoriteLoadingMore = false,
                    favoriteErrorMessage = null
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (favoritePaging.snapshot().generation != startGeneration ||
                _uiState.value.navData?.mid != expectedMid
            ) {
                return
            }
            _uiState.update {
                it.copy(
                    isFavoriteLoading = false,
                    isFavoriteLoadingMore = false,
                    favoriteErrorMessage = error.message ?: "加载收藏内容失败"
                )
            }
        }
    }

    private suspend fun loadWatchLater(expectedMid: Long) {
        _uiState.update {
            it.copy(
                isWatchLaterLoading = true,
                watchLaterErrorMessage = null
            )
        }

        val watchLaterData = FavoriteRepository.getWatchLaterList().getOrElse { error ->
            _uiState.update {
                it.copy(
                    isWatchLaterLoading = false,
                    watchLaterErrorMessage = error.message ?: "加载稍后再看失败"
                )
            }
            return
        }

        if (_uiState.value.navData?.mid != expectedMid) return

        val items = appendUniqueVideoItems(
            existing = emptyList(),
            incoming = watchLaterData.list.orEmpty().map { item -> item.toVideoItem() }
        )
        watchLaterLoadedForMid = expectedMid
        _uiState.update {
            it.copy(
                watchLaterItems = items,
                watchLaterTotalCount = watchLaterData.count.takeIf { count -> count > 0 } ?: items.size,
                isWatchLaterLoading = false,
                watchLaterErrorMessage = null
            )
        }
    }

    private suspend fun fetchFavoriteFolders(mid: Long): List<FavFolder> = coroutineScope {
        val ownedFoldersDeferred = async {
            FavoriteRepository.getFavFolders(mid).getOrElse { error ->
                throw error
            }
        }
        val collectedFoldersDeferred = async { fetchCollectedFavoriteFolders(mid) }
        mergeProfileFavoriteFoldersForDisplay(
            ownedFolders = ownedFoldersDeferred.await(),
            subscribedFolders = collectedFoldersDeferred.await()
        )
    }

    private suspend fun fetchCollectedFavoriteFolders(mid: Long): List<FavFolder> {
        val folders = mutableListOf<FavFolder>()
        val seenKeys = HashSet<String>()
        var page = 1
        var totalCount = Int.MAX_VALUE

        while (page <= COLLECTED_FAVORITE_FOLDER_MAX_PAGES && folders.size < totalCount) {
            val collectedPage = FavoriteRepository.getCollectedFavFolders(
                mid = mid,
                pn = page,
                ps = COLLECTED_FAVORITE_FOLDER_PAGE_SIZE,
                platform = "web"
            ).getOrElse { error ->
                throw error
            }

            val uniqueFolders = collectedPage.folders.filter { folder ->
                seenKeys.add(resolveProfileFavoriteFolderKey(folder))
            }
            folders += uniqueFolders
            totalCount = collectedPage.totalCount.takeIf { it > 0 } ?: totalCount

            if (collectedPage.folders.isEmpty() ||
                collectedPage.folders.size < COLLECTED_FAVORITE_FOLDER_PAGE_SIZE
            ) {
                break
            }
            page += 1
        }

        return folders
    }

    private fun appendUniqueHistoryItems(
        existing: List<HistoryData>,
        incoming: List<HistoryData>
    ): List<HistoryData> {
        if (incoming.isEmpty()) return existing
        val seenKeys = existing.map(::resolveHistoryListKey).toMutableSet()
        val appended = incoming.filter { item ->
            seenKeys.add(resolveHistoryListKey(item))
        }
        return if (appended.isEmpty()) existing else existing + appended
    }

    private fun appendUniqueVideoItems(
        existing: List<VideoItem>,
        incoming: List<VideoItem>
    ): List<VideoItem> {
        if (incoming.isEmpty()) return existing
        val seenKeys = existing.map(::resolveVideoListKey).toMutableSet()
        val appended = incoming.filter { item ->
            seenKeys.add(resolveVideoListKey(item))
        }
        return if (appended.isEmpty()) existing else existing + appended
    }

    private fun resolveHistoryListKey(item: HistoryData): String {
        val history = item.history
        val identity = history?.bvid?.takeIf { it.isNotBlank() }
            ?: history?.oid?.takeIf { it > 0L }?.toString()
            ?: item.title.trim()
        return buildString {
            append(history?.business.orEmpty().ifBlank { "archive" })
            append('_')
            append(identity)
            append('_')
            append(history?.cid ?: 0L)
            append('_')
            append(history?.page ?: 1)
            append('_')
            append(item.view_at)
        }
    }

    private fun resolveVideoListKey(item: VideoItem): String {
        return item.bvid.takeIf { it.isNotBlank() }
            ?: item.collectionId.takeIf { it > 0L }?.let { "collection:$it" }
            ?: buildString {
                append(item.aid)
                append('_')
                append(item.cid)
                append('_')
                append(item.title.trim())
            }
    }

    private fun resolveHistoryDayBucketCount(items: List<HistoryData>): Int {
        val dayKeys = HashSet<String>()
        items.forEach { item ->
            if (item.view_at <= 0L) return@forEach
            dayKeys += HISTORY_DAY_BUCKET_FORMAT.format(Date(item.view_at * 1000L))
            if (dayKeys.size >= HISTORY_INITIAL_BOOTSTRAP_DAY_BUCKETS) {
                return dayKeys.size
            }
        }
        return dayKeys.size
    }

    private fun applyCachedHistoryViewInfo(items: List<HistoryData>): List<HistoryData> {
        if (items.isEmpty()) return items
        return items.map { item ->
            val cachedInfo = item.history?.bvid
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(VideoDetailRepository::getCachedDetailViewInfo)
                ?: return@map item
            mergeHistoryItemWithViewInfo(item, cachedInfo)
        }
    }

    private fun scheduleHistoryDetailEnrichment(
        expectedMid: Long,
        items: List<HistoryData>
    ) {
        historyEnrichmentJob?.cancel()
        if (items.isEmpty()) return
        historyEnrichmentJob = viewModelScope.launch {
            val enrichedItems = enrichHistoryItemsWithViewInfo(items)
            if (!isActive || _uiState.value.navData?.mid != expectedMid) return@launch
            _uiState.update { state ->
                state.copy(historyItems = enrichedItems)
            }
        }
    }

    private fun observePlaybackHistorySync() {
        viewModelScope.launch {
            PlaybackHistorySyncBus.events.collect { event ->
                pendingHistorySyncEvent = event
                val navData = _uiState.value.navData
                if (navData?.isLogin != true || navData.mid != event.mid) {
                    return@collect
                }
                if (_uiState.value.isHistoryLoading || _uiState.value.isHistoryLoadingMore) {
                    return@collect
                }
                Logger.d(
                    "ProfileVM",
                    "playback history sync event mid=${event.mid} bvid=${event.bvid} cid=${event.cid}"
                )
                loadHistory(reset = true, expectedMid = navData.mid)
            }
        }
    }

    private suspend fun enrichHistoryItemsWithViewInfo(items: List<HistoryData>): List<HistoryData> = coroutineScope {
        val limiter = Semaphore(HISTORY_DETAIL_ENRICH_CONCURRENCY)
        items.map { item ->
            async {
                if (!shouldEnrichHistoryViewInfo(item)) {
                    return@async item
                }
                val bvid = item.history?.bvid?.trim().orEmpty()
                val cachedInfo = VideoDetailRepository.getCachedDetailViewInfo(bvid)
                if (cachedInfo != null) {
                    return@async mergeHistoryItemWithViewInfo(item, cachedInfo)
                }
                limiter.withPermit {
                    val response = runCatching { VideoDetailRepository.getVideoInfo(bvid) }.getOrNull()
                    val viewInfo = response?.data ?: return@withPermit item
                    mergeHistoryItemWithViewInfo(item, viewInfo)
                }
            }
        }.awaitAll()
    }

    private fun shouldEnrichHistoryViewInfo(item: HistoryData): Boolean {
        val history = item.history ?: return false
        if (HistoryBusiness.fromValue(history.business) != HistoryBusiness.ARCHIVE) return false
        if (history.bvid.isBlank()) return false
        val stat = item.stat
        return stat == null ||
            stat.view <= 0 ||
            stat.danmaku <= 0 ||
            item.author_face.isBlank() ||
            item.author_name.isBlank() ||
            (item.cover.isBlank() && item.pic.isBlank())
    }

    private fun mergeHistoryItemWithViewInfo(
        item: HistoryData,
        viewInfo: ViewInfo
    ): HistoryData {
        val currentStat = item.stat
        val mergedStat = when {
            currentStat == null -> viewInfo.stat
            currentStat.view <= 0 && currentStat.danmaku <= 0 -> viewInfo.stat
            else -> currentStat
        }
        return item.copy(
            cover = item.cover.ifBlank { viewInfo.pic },
            pic = item.pic.ifBlank { viewInfo.pic },
            author_name = item.author_name.ifBlank { viewInfo.owner.name },
            author_face = item.author_face.ifBlank { viewInfo.owner.face },
            author_mid = item.author_mid.takeIf { it > 0L } ?: viewInfo.owner.mid,
            duration = item.duration.takeIf { it > 0 } ?: resolveHistoryDurationFromViewInfo(item, viewInfo),
            stat = mergedStat
        )
    }

    private fun resolveHistoryDurationFromViewInfo(
        item: HistoryData,
        viewInfo: ViewInfo
    ): Int {
        val targetCid = item.history?.cid ?: 0L
        return viewInfo.pages.firstOrNull { page -> page.cid == targetCid }
            ?.duration
            ?.toInt()
            ?: item.duration
    }

    private fun resetHistoryState() {
        historyEnrichmentJob?.cancel()
        historyEnrichmentJob = null
        resetHistoryCursor()
        _uiState.update {
            it.copy(
                historyItems = emptyList(),
                historyHasMore = false,
                isHistoryLoading = false,
                isHistoryLoadingMore = false,
                historyErrorMessage = null
            )
        }
    }

    private fun resetFavoriteState() {
        favoritePaging.reset()
        favoriteSelectedFolder = null
        _uiState.update {
            it.copy(
                favoriteFolders = emptyList(),
                selectedFavoriteFolderKey = null,
                favoriteItems = emptyList(),
                favoriteHasMore = false,
                isFavoriteLoading = false,
                isFavoriteLoadingMore = false,
                favoriteErrorMessage = null
            )
        }
    }

    private fun resetWatchLaterState() {
        _uiState.update {
            it.copy(
                watchLaterItems = emptyList(),
                watchLaterTotalCount = 0,
                isWatchLaterLoading = false,
                watchLaterErrorMessage = null
            )
        }
    }

    private fun resetHistoryCursor() {
        historyCursorMax = 0L
        historyCursorViewAt = 0L
        historyCursorBusiness = null
    }

    private companion object {
        val HISTORY_DAY_BUCKET_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
