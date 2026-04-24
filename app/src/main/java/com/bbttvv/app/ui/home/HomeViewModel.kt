package com.bbttvv.app.ui.home

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.paging.PagedFeedGridState
import com.bbttvv.app.core.paging.appliedOrNull
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.json.JsonPluginManager
import com.bbttvv.app.core.store.CreatorSignalSnapshot
import com.bbttvv.app.core.store.TodayWatchFeedbackSnapshot
import com.bbttvv.app.core.store.TodayWatchFeedbackStore
import com.bbttvv.app.core.store.TodayWatchProfileStore
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.ActionRepository
import com.bbttvv.app.data.repository.FeedRepository
import com.bbttvv.app.data.repository.HistoryRepository
import com.bbttvv.app.data.repository.VideoDetailRepository
import com.bbttvv.app.feature.plugin.TodayWatchPlugin
import com.bbttvv.app.feature.plugin.TodayWatchPluginConfig
import com.bbttvv.app.ui.components.AppTopLevelTab
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

import androidx.lifecycle.ViewModelStore

private const val HomeFocusSummaryPrefetchDelayMs = 240L
private const val TodayWatchHistoryPageSize = 40
private const val TodayWatchHistoryPageLimit = 2
private const val TodayWatchHistoryFailureCooldownMs = 60_000L
private val TodayWatchTitleKeywordPattern = Regex("""[\p{L}\p{N}\u4E00-\u9FFF]{2,12}""")

data class HomeUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val currentIdx: Long = 0L,
    val errorMsg: String? = null,
    val todayWatchEnabled: Boolean = false,
    val todayWatchConfig: TodayWatchPluginConfig = TodayWatchPluginConfig(),
    val todayWatchMode: TodayWatchMode = TodayWatchMode.RELAX,
    val todayWatchPlan: TodayWatchPlan = TodayWatchPlan(),
    val todayWatchLoading: Boolean = false,
    val todayWatchErrorMsg: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val tabViewModelStores = mutableMapOf<com.bbttvv.app.ui.components.AppTopLevelTab, ViewModelStore>()

    private val appContext = application.applicationContext
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var detailPrefetchJob: Job? = null
    private var pendingPrefetchBvid: String? = null
    private var lastPrefetchedBvid: String? = null
    private var todayWatchPluginConfigJob: Job? = null
    private var todayWatchRebuildJob: Job? = null

    private val recommendFeed = PagedFeedGridState<Int, VideoItem, VideoItem>(initialKey = 0)
    private val sessionConsumedTodayWatchBvids = linkedSetOf<String>()
    private val dismissedRecommendLock = Any()
    private val dismissedRecommendBvids = linkedSetOf<String>()
    private val dismissedRecommendAids = linkedSetOf<Long>()

    private var todayWatchPlugin: TodayWatchPlugin? = null
    private var cachedHistorySample: List<VideoItem> = emptyList()
    private var selectedHomeTab: AppTopLevelTab = AppTopLevelTab.RECOMMEND
    private var pendingTodayWatchRebuildForceReload = false
    private var lastTodayWatchHistoryLoadFailureMs = 0L
    private var feedbackSnapshot: TodayWatchFeedbackSnapshot =
        TodayWatchFeedbackStore.getSnapshot(appContext)
    private var latestTodayWatchRefreshToken: Long = 0L

    init {
        observeFeedPluginUpdates()
        observeTodayWatchPlugin()
        loadMore()
    }

    override fun onCleared() {
        super.onCleared()
        tabViewModelStores.values.forEach { it.clear() }
        tabViewModelStores.clear()
    }

    fun clearUnselectedTabStores(selectedTab: com.bbttvv.app.ui.components.AppTopLevelTab) {
        val iter = tabViewModelStores.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key != selectedTab) {
                entry.value.clear()
                iter.remove()
            }
        }
    }

    fun onHomeTabVisible(tab: AppTopLevelTab) {
        selectedHomeTab = tab
        if (tab != AppTopLevelTab.TODAY_WATCH) return

        val state = _uiState.value
        val needsInitialBuild = state.todayWatchEnabled &&
            !state.todayWatchLoading &&
            state.todayWatchPlan.videoQueue.isEmpty() &&
            recommendFeed.visibleSnapshot().isNotEmpty()
        if (pendingTodayWatchRebuildForceReload || needsInitialBuild) {
            val forceReload = pendingTodayWatchRebuildForceReload
            pendingTodayWatchRebuildForceReload = false
            requestTodayWatchRebuild(forceReloadHistory = forceReload)
        }
    }

    fun loadMore() {
        val snapshot = recommendFeed.snapshot()
        if (snapshot.isLoading || snapshot.endReached) return

        _uiState.update {
            it.copy(
                isLoading = true,
                isError = false,
                errorMsg = null
            )
        }

        viewModelScope.launch {
            val startGeneration = recommendFeed.snapshot().generation
            try {
                val loadResult = recommendFeed.loadNextPage(
                    isRefresh = false,
                    fetch = { pageKey ->
                        val incomingVideos = FeedRepository.getHomeVideos(idx = pageKey).getOrElse { error -> throw error }
                        val filteredIncoming = applyIncrementalFeedFiltersOffMain(
                            videos = incomingVideos,
                            recordStats = true
                        )
                        PagedFeedGridState.Page(
                            sourceItems = incomingVideos,
                            visibleItems = filteredIncoming,
                            nextKey = if (incomingVideos.isNotEmpty()) pageKey + 1 else pageKey,
                            endReached = incomingVideos.isEmpty()
                        )
                    },
                    reduce = { _, page -> page }
                )

                loadResult.appliedOrNull() ?: return@launch
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        videos = recommendFeed.visibleSnapshot(),
                        currentIdx = recommendFeed.snapshot().nextKey.toLong(),
                        isError = false,
                        errorMsg = null
                    )
                }
                requestTodayWatchRebuild(forceReloadHistory = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (recommendFeed.snapshot().generation != startGeneration) return@launch
                Logger.e("HomeViewModel", "Failed to load videos", error)
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        isError = true,
                        errorMsg = error.message ?: "未知错误"
                    )
                }
            }
        }
    }
    fun refresh() {
        recommendFeed.reset()
        _uiState.update {
            it.copy(
                isLoading = true,
                isError = false,
                errorMsg = null
            )
        }
        viewModelScope.launch {
            val startGeneration = recommendFeed.snapshot().generation
            try {
                val loadResult = recommendFeed.loadNextPage(
                    isRefresh = true,
                    fetch = { pageKey ->
                        val incomingVideos = FeedRepository.getHomeVideos(idx = pageKey).getOrElse { error -> throw error }
                        val filteredVideos = applyFeedFiltersOffMain(
                            videos = incomingVideos,
                            recordStats = true
                        )
                        PagedFeedGridState.Page(
                            sourceItems = incomingVideos,
                            visibleItems = filteredVideos,
                            nextKey = if (incomingVideos.isNotEmpty()) pageKey + 1 else pageKey,
                            endReached = incomingVideos.isEmpty()
                        )
                    },
                    reduce = { _, page -> page }
                )

                loadResult.appliedOrNull() ?: return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isError = false,
                        errorMsg = null,
                        videos = recommendFeed.visibleSnapshot(),
                        currentIdx = recommendFeed.snapshot().nextKey.toLong()
                    )
                }
                requestTodayWatchRebuild(forceReloadHistory = false)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (recommendFeed.snapshot().generation != startGeneration) return@launch
                Logger.e("HomeViewModel", "Failed to refresh videos", error)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isError = true,
                        errorMsg = error.message ?: "未知错误"
                    )
                }
            }
        }
    }
    fun refreshTodayWatchOnly() {
        if (!_uiState.value.todayWatchEnabled) return
        val consumed = collectTodayWatchConsumedForManualRefresh(
            plan = _uiState.value.todayWatchPlan,
            previewLimit = _uiState.value.todayWatchConfig.queuePreviewLimit
        )
        if (consumed.isNotEmpty()) {
            sessionConsumedTodayWatchBvids.addAll(consumed)
        }
        requestTodayWatchRebuild(forceReloadHistory = false)
    }

    fun setTodayWatchMode(mode: TodayWatchMode) {
        if (_uiState.value.todayWatchMode == mode) return
        _uiState.update {
            it.copy(
                todayWatchMode = mode,
                todayWatchPlan = it.todayWatchPlan.copy(mode = mode)
            )
        }
        todayWatchPlugin?.setCurrentMode(mode)
        requestTodayWatchRebuild(forceReloadHistory = false)
    }

    fun markTodayWatchVideoOpened(video: VideoItem) {
        val consumedBvid = video.bvid.takeIf { it.isNotBlank() } ?: return
        sessionConsumedTodayWatchBvids.add(consumedBvid)

        val update = consumeVideoFromTodayWatchPlan(
            plan = _uiState.value.todayWatchPlan,
            consumedBvid = consumedBvid,
            queuePreviewLimit = _uiState.value.todayWatchConfig.queuePreviewLimit
        )
        if (update.consumedApplied) {
            _uiState.update { it.copy(todayWatchPlan = update.updatedPlan) }
        }
        if (update.shouldRefill) {
            requestTodayWatchRebuild(forceReloadHistory = false)
        }
    }

    fun markTodayWatchNotInterested(video: VideoItem) {
        val dislikedBvids = buildSet {
            addAll(feedbackSnapshot.dislikedBvids)
            video.bvid.takeIf { it.isNotBlank() }?.let(::add)
        }
        val dislikedCreatorMids = buildSet {
            addAll(feedbackSnapshot.dislikedCreatorMids)
            video.owner.mid.takeIf { it > 0L }?.let(::add)
        }
        val nextSnapshot = feedbackSnapshot.copy(
            dislikedBvids = dislikedBvids,
            dislikedCreatorMids = dislikedCreatorMids,
            dislikedKeywords = feedbackSnapshot.dislikedKeywords + extractTodayWatchFeedbackKeywords(video.title)
        )
        persistTodayWatchFeedback(nextSnapshot)

        val consumedBvid = video.bvid.takeIf { it.isNotBlank() }.orEmpty()
        if (consumedBvid.isNotBlank()) {
            sessionConsumedTodayWatchBvids.add(consumedBvid)
        }
        val update = consumeVideoFromTodayWatchPlan(
            plan = _uiState.value.todayWatchPlan,
            consumedBvid = consumedBvid,
            queuePreviewLimit = _uiState.value.todayWatchConfig.queuePreviewLimit
        )
        if (update.consumedApplied) {
            _uiState.update { it.copy(todayWatchPlan = update.updatedPlan) }
        }
        requestTodayWatchRebuild(forceReloadHistory = false)
    }

    fun markWatchLater(video: VideoItem, onResult: (Result<Boolean>) -> Unit = {}) {
        viewModelScope.launch {
            val result = if (video.aid > 0L) {
                ActionRepository.toggleWatchLater(aid = video.aid, add = true)
            } else {
                Result.failure(Exception("閺冪姵纭跺ǎ璇插閻╁瓨鎸遍幋鏍ㄦ￥閺佸牐顫嬫０鎴濆煂缁嬪秴鎮楅崘宥囨箙"))
            }
            onResult(result)
        }
    }

    fun markRecommendNotInterested(video: VideoItem) {
        val bvid = video.bvid.trim()
        val changed = synchronized(dismissedRecommendLock) {
            var didChange = false
            if (bvid.isNotEmpty()) {
                didChange = dismissedRecommendBvids.add(bvid) || didChange
            }
            if (video.aid > 0L) {
                didChange = dismissedRecommendAids.add(video.aid) || didChange
            }
            didChange
        }
        if (!changed) return

        val nextVisibleVideos = recommendFeed.removeVisibleIf(::isDismissedRecommendVideo)
        _uiState.update { currentState ->
            currentState.copy(videos = nextVisibleVideos)
        }
        requestTodayWatchRebuild(forceReloadHistory = false)
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
            delay(HomeFocusSummaryPrefetchDelayMs)
            VideoDetailRepository.prefetchDetailSummary(video)
            lastPrefetchedBvid = video.bvid
            if (pendingPrefetchBvid == video.bvid) {
                pendingPrefetchBvid = null
            }
        }
    }

    private fun observeFeedPluginUpdates() {
        viewModelScope.launch {
            PluginManager.feedPluginUpdateToken.collectLatest { token ->
                if (token <= 0L || recommendFeed.sourceSnapshot().isEmpty()) return@collectLatest
                val filteredVideos = applyFeedFiltersOffMain(recommendFeed.sourceSnapshot(), recordStats = false)
                val nextVisibleVideos = recommendFeed.replaceVisible(filteredVideos)
                _uiState.update { currentState ->
                    currentState.copy(videos = nextVisibleVideos)
                }
                requestTodayWatchRebuild(forceReloadHistory = false)
            }
        }
    }
    private fun observeTodayWatchPlugin() {
        viewModelScope.launch {
            PluginManager.pluginsFlow.collectLatest { plugins ->
                val pluginInfo = plugins.firstOrNull { it.plugin.id == TodayWatchPlugin.PLUGIN_ID }
                val plugin = pluginInfo?.plugin as? TodayWatchPlugin
                val enabled = pluginInfo?.enabled == true
                val config = plugin?.configState?.value ?: TodayWatchPluginConfig()
                val previousConfig = _uiState.value.todayWatchConfig

                todayWatchPlugin = plugin
                applyTodayWatchConfig(
                    enabled = enabled,
                    config = config
                )

                todayWatchPluginConfigJob?.cancel()
                if (plugin != null) {
                    todayWatchPluginConfigJob = viewModelScope.launch {
                        plugin.configState.collectLatest { latestConfig ->
                            val oldConfig = _uiState.value.todayWatchConfig
                            applyTodayWatchConfig(
                                enabled = PluginManager.plugins.any {
                                    it.plugin.id == TodayWatchPlugin.PLUGIN_ID && it.enabled
                                },
                                config = latestConfig
                            )
                            val forceReloadHistory =
                                latestConfig.historySampleLimit != oldConfig.historySampleLimit ||
                                    latestConfig.refreshTriggerToken != oldConfig.refreshTriggerToken
                            if (_uiState.value.todayWatchEnabled) {
                                requestTodayWatchRebuild(forceReloadHistory = forceReloadHistory)
                            }
                        }
                    }
                }

                if (enabled) {
                    val forceReloadHistory =
                        previousConfig.historySampleLimit != config.historySampleLimit ||
                            previousConfig.refreshTriggerToken != config.refreshTriggerToken ||
                            cachedHistorySample.isEmpty()
                    requestTodayWatchRebuild(forceReloadHistory = forceReloadHistory)
                }
            }
        }
    }

    private fun applyTodayWatchConfig(
        enabled: Boolean,
        config: TodayWatchPluginConfig
    ) {
        if (config.refreshTriggerToken != latestTodayWatchRefreshToken) {
            latestTodayWatchRefreshToken = config.refreshTriggerToken
            feedbackSnapshot = TodayWatchFeedbackStore.getSnapshot(appContext)
            sessionConsumedTodayWatchBvids.clear()
        }
        _uiState.update { current ->
            current.copy(
                todayWatchEnabled = enabled,
                todayWatchConfig = config,
                todayWatchMode = config.currentMode,
                todayWatchPlan = if (enabled) {
                    current.todayWatchPlan.copy(mode = config.currentMode)
                } else {
                    TodayWatchPlan(mode = config.currentMode)
                },
                todayWatchLoading = if (enabled) current.todayWatchLoading else false,
                todayWatchErrorMsg = if (enabled) current.todayWatchErrorMsg else null
            )
        }
    }

    private fun requestTodayWatchRebuild(forceReloadHistory: Boolean) {
        if (!_uiState.value.todayWatchEnabled) return
        if (selectedHomeTab != AppTopLevelTab.TODAY_WATCH) {
            pendingTodayWatchRebuildForceReload =
                pendingTodayWatchRebuildForceReload || forceReloadHistory
            return
        }
        pendingTodayWatchRebuildForceReload = false
        todayWatchRebuildJob?.cancel()
        todayWatchRebuildJob = viewModelScope.launch {
            rebuildTodayWatchPlan(forceReloadHistory = forceReloadHistory)
        }
    }

    private suspend fun rebuildTodayWatchPlan(forceReloadHistory: Boolean) {
        val state = _uiState.value
        if (!state.todayWatchEnabled) return

        val candidates = recommendFeed.visibleSnapshot()
        if (candidates.isEmpty()) {
            _uiState.update {
                it.copy(
                    todayWatchLoading = false,
                    todayWatchPlan = TodayWatchPlan(mode = it.todayWatchMode),
                    todayWatchErrorMsg = if (it.isLoading) "正在加载推荐内容..." else "暂无可用于今日观看的推荐内容"
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                todayWatchLoading = true,
                todayWatchErrorMsg = null
            )
        }

        val config = _uiState.value.todayWatchConfig
        val mode = _uiState.value.todayWatchMode
        val historySample = resolveTodayWatchHistorySample(
            forceReload = forceReloadHistory,
            limit = config.historySampleLimit
        )
        val creatorSignals = TodayWatchProfileStore.getCreatorSignals(
            context = appContext,
            limit = config.upRankLimit
        ).map(::toTodayWatchCreatorSignal)
        val penaltySignals = TodayWatchPenaltySignals(
            consumedBvids = sessionConsumedTodayWatchBvids.toSet(),
            dislikedBvids = feedbackSnapshot.dislikedBvids,
            dislikedCreatorMids = feedbackSnapshot.dislikedCreatorMids,
            dislikedKeywords = feedbackSnapshot.dislikedKeywords
        )

        val plan = withContext(Dispatchers.Default) {
            buildTodayWatchPlan(
                historyVideos = historySample,
                candidateVideos = candidates,
                mode = mode,
                eyeCareNightActive = false,
                upRankLimit = config.upRankLimit,
                queueLimit = config.queueBuildLimit,
                creatorSignals = creatorSignals,
                penaltySignals = penaltySignals
            )
        }

        _uiState.update {
            it.copy(
                todayWatchLoading = false,
                todayWatchPlan = plan,
                todayWatchErrorMsg = if (plan.videoQueue.isEmpty()) {
                    "今日观看暂未生成可播放队列，请刷新推荐内容后重试"
                } else {
                    null
                }
            )
        }
    }

    private suspend fun resolveTodayWatchHistorySample(
        forceReload: Boolean,
        limit: Int
    ): List<VideoItem> {
        if (!forceReload && cachedHistorySample.isNotEmpty()) {
            return cachedHistorySample.take(limit)
        }
        if (!forceReload &&
            cachedHistorySample.isEmpty() &&
            lastTodayWatchHistoryLoadFailureMs > 0L &&
            SystemClock.elapsedRealtime() - lastTodayWatchHistoryLoadFailureMs < TodayWatchHistoryFailureCooldownMs
        ) {
            return emptyList()
        }

        val loaded = mutableListOf<VideoItem>()
        var cursorMax = 0L
        var cursorViewAt = 0L
        var cursorBusiness: String? = null

        for (pageIndex in 0 until TodayWatchHistoryPageLimit) {
            val result = HistoryRepository.getHistoryList(
                ps = TodayWatchHistoryPageSize,
                max = cursorMax,
                viewAt = cursorViewAt,
                business = cursorBusiness
            )
            val page = result.getOrNull() ?: run {
                val error = result.exceptionOrNull()
                Logger.w("HomeViewModel", "Failed to load history sample for today watch", error)
                lastTodayWatchHistoryLoadFailureMs = SystemClock.elapsedRealtime()
                break
            }
            loaded += page.list
                .map { it.toVideoItem() }
                .filter { it.bvid.isNotBlank() && it.owner.mid > 0L }
            if (loaded.size >= limit) {
                break
            }
            val cursor = page.cursor ?: break
            val nextBusiness = cursor.business.takeIf { it.isNotBlank() }
            val hasNextCursor = cursor.max > 0L || cursor.view_at > 0L || nextBusiness != null
            if (!hasNextCursor) {
                break
            }
            cursorMax = cursor.max
            cursorViewAt = cursor.view_at
            cursorBusiness = nextBusiness
        }

        val normalized = loaded
            .distinctBy { it.bvid }
            .sortedByDescending { it.view_at }
            .take(limit)
        if (normalized.isNotEmpty()) {
            cachedHistorySample = normalized
            lastTodayWatchHistoryLoadFailureMs = 0L
        }
        return (if (normalized.isNotEmpty()) normalized else cachedHistorySample).take(limit)
    }

    private fun persistTodayWatchFeedback(snapshot: TodayWatchFeedbackSnapshot) {
        feedbackSnapshot = snapshot
        viewModelScope.launch(Dispatchers.IO) {
            TodayWatchFeedbackStore.saveSnapshot(appContext, snapshot)
        }
    }

    private fun extractTodayWatchFeedbackKeywords(title: String): Set<String> {
        if (title.isBlank()) return emptySet()
        return TodayWatchTitleKeywordPattern.findAll(title.lowercase())
            .map { it.value.trim() }
            .filter { keyword -> keyword.length >= 2 }
            .take(6)
            .toSet()
    }

    private fun toTodayWatchCreatorSignal(snapshot: CreatorSignalSnapshot): TodayWatchCreatorSignal {
        return TodayWatchCreatorSignal(
            mid = snapshot.mid,
            name = snapshot.name,
            score = snapshot.score,
            watchCount = snapshot.watchCount
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
        return PluginManager.filterFeedItems(jsonFiltered)
            .filterNot(::isDismissedRecommendVideo)
    }

    private suspend fun applyIncrementalFeedFiltersOffMain(
        videos: List<VideoItem>,
        recordStats: Boolean
    ): List<VideoItem> {
        if (videos.isEmpty()) return emptyList()
        return applyFeedFiltersOffMain(videos, recordStats = recordStats)
    }

    private fun isDismissedRecommendVideo(video: VideoItem): Boolean {
        val bvid = video.bvid.trim()
        return synchronized(dismissedRecommendLock) {
            (bvid.isNotEmpty() && bvid in dismissedRecommendBvids) ||
                (video.aid > 0L && video.aid in dismissedRecommendAids)
        }
    }
}
