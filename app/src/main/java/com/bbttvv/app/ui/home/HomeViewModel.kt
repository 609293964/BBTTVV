package com.bbttvv.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.di.AppContainer
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.ActionRepository
import com.bbttvv.app.feature.plugin.TodayWatchPlugin
import com.bbttvv.app.feature.plugin.TodayWatchPluginConfig
import com.bbttvv.app.ui.components.AppTopLevelTab
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class RecommendLoadState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val hasMore: Boolean = true,
    val errorMsg: String? = null,
)

data class TodayWatchUiState(
    val enabled: Boolean = false,
    val config: TodayWatchPluginConfig = TodayWatchPluginConfig(),
    val mode: TodayWatchMode = TodayWatchMode.RELAX,
    val plan: TodayWatchPlan = TodayWatchPlan(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

private fun resolveTabStorePolicy(): TabStorePolicy = TabStorePolicy.KeepAll

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val tabStoreOwner = HomeTabStoreOwner(resolveTabStorePolicy())
    private val _recommendVideoItems = MutableStateFlow<List<HomeRecommendVideoCardItem>>(emptyList())
    val recommendVideoItems: StateFlow<List<HomeRecommendVideoCardItem>> = _recommendVideoItems.asStateFlow()
    private val _recommendLoadState = MutableStateFlow(RecommendLoadState())
    val recommendLoadState: StateFlow<RecommendLoadState> = _recommendLoadState.asStateFlow()
    private val _refreshErrorMessage = MutableStateFlow<String?>(null)
    val refreshErrorMessage: StateFlow<String?> = _refreshErrorMessage.asStateFlow()
    private val _todayWatchState = MutableStateFlow(TodayWatchUiState())
    val todayWatchState: StateFlow<TodayWatchUiState> = _todayWatchState.asStateFlow()

    private val detailPrefetcher = HomeDetailPrefetcher(viewModelScope)
    private val recommendDismissStore = RecommendDismissStore()
    private val feedController = HomeFeedController(
        dismissStore = recommendDismissStore,
        feedRepository = AppContainer.feedRepository
    )
    private val todayWatchCoordinator = TodayWatchCoordinator(appContext, viewModelScope)
    private var todayWatchPluginConfigJob: Job? = null
    private var todayWatchRebuildJob: Job? = null

    private var todayWatchPlugin: TodayWatchPlugin? = null
    private var selectedHomeTab: AppTopLevelTab = AppTopLevelTab.RECOMMEND
    private var pendingTodayWatchRebuildForceReload = false
    private var tabStoreIdleTrimJob: Job? = null

    init {
        observeFeedPluginUpdates()
        observeTodayWatchPlugin()
        loadMore()
    }

    override fun onCleared() {
        super.onCleared()
        detailPrefetcher.clear()
        tabStoreIdleTrimJob?.cancel()
        tabStoreOwner.clearAll()
    }

    fun tabViewModelStore(tab: AppTopLevelTab) = tabStoreOwner.getOrCreate(tab)

    private fun trimTabViewModelStoresForVisibleTab(selectedTab: AppTopLevelTab) {
        scheduleIdleTabStoreTrim(tabStoreOwner.trimForSelected(selectedTab))
    }

    fun onHomeTabVisible(tab: AppTopLevelTab) {
        selectedHomeTab = tab
        trimTabViewModelStoresForVisibleTab(tab)
        if (tab != AppTopLevelTab.TODAY_WATCH) return

        val state = _todayWatchState.value
        val needsInitialBuild = state.enabled &&
            !state.isLoading &&
            state.plan.videoQueue.isEmpty() &&
            feedController.hasVisibleItems()
        if (pendingTodayWatchRebuildForceReload || needsInitialBuild) {
            val forceReload = pendingTodayWatchRebuildForceReload
            pendingTodayWatchRebuildForceReload = false
            requestTodayWatchRebuild(forceReloadHistory = forceReload)
        }
    }

    private fun scheduleIdleTabStoreTrim(delayMs: Long?) {
        tabStoreIdleTrimJob?.cancel()
        if (delayMs == null) {
            tabStoreIdleTrimJob = null
            return
        }
        tabStoreIdleTrimJob = viewModelScope.launch {
            var nextDelayMs: Long? = delayMs
            while (nextDelayMs != null) {
                delay(nextDelayMs.coerceAtLeast(1L))
                nextDelayMs = tabStoreOwner.trimForSelected(selectedHomeTab)
            }
        }
    }

    fun loadMore() {
        if (feedController.isLoadingOrEndReached()) return

        _recommendLoadState.update {
            it.copy(
                isLoading = true,
                isError = false,
                errorMsg = null,
            )
        }
        _refreshErrorMessage.value = null

        viewModelScope.launch {
            when (val result = feedController.loadMore()) {
                is HomeFeedLoadResult.Success -> {
                    applyRecommendLoadSuccess(result)
                    requestTodayWatchRebuild(forceReloadHistory = false)
                }
                is HomeFeedLoadResult.Failure -> {
                    _recommendLoadState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            isError = true,
                            errorMsg = result.error.message ?: "未知错误"
                        )
                    }
                }
                HomeFeedLoadResult.Ignored -> {
                    _recommendLoadState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun refresh() {
        _recommendLoadState.update {
            it.copy(
                isLoading = true,
                isError = false,
                hasMore = true,
                errorMsg = null,
            )
        }
        _refreshErrorMessage.value = null
        viewModelScope.launch {
            when (val result = feedController.refresh()) {
                is HomeFeedLoadResult.Success -> {
                    applyRecommendLoadSuccess(result)
                    requestTodayWatchRebuild(forceReloadHistory = false)
                }
                is HomeFeedLoadResult.Failure -> {
                    val errorMessage = result.error.message ?: "未知错误"
                    val refreshErrorMessage = if (_recommendVideoItems.value.isNotEmpty()) {
                        result.error.message ?: "刷新失败"
                    } else {
                        null
                    }
                    _recommendLoadState.update {
                        it.copy(
                            isLoading = false,
                            isError = true,
                            errorMsg = errorMessage,
                        )
                    }
                    _refreshErrorMessage.value = refreshErrorMessage
                }
                HomeFeedLoadResult.Ignored -> {
                    _recommendLoadState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun clearRefreshErrorMessage() {
        _refreshErrorMessage.value = null
    }

    fun canLoadMoreRecommend(): Boolean {
        val state = _recommendLoadState.value
        return state.hasMore && !state.isLoading
    }

    fun refreshTodayWatchOnly() {
        val state = _todayWatchState.value
        if (!state.enabled) return
        todayWatchCoordinator.collectManualRefreshConsumed(
            plan = state.plan,
            previewLimit = state.config.queuePreviewLimit
        )
        requestTodayWatchRebuild(forceReloadHistory = false)
    }

    fun setTodayWatchMode(mode: TodayWatchMode) {
        if (_todayWatchState.value.mode == mode) return
        _todayWatchState.update {
            it.copy(
                mode = mode,
                plan = it.plan.copy(mode = mode)
            )
        }
        todayWatchPlugin?.setCurrentMode(mode)
        requestTodayWatchRebuild(forceReloadHistory = false)
    }

    fun markTodayWatchVideoOpened(video: VideoItem) {
        val consumedBvid = video.bvid.takeIf { it.isNotBlank() } ?: return
        todayWatchCoordinator.markVideoOpened(video)
        val state = _todayWatchState.value

        val update = todayWatchCoordinator.consumeFromPlan(
            plan = state.plan,
            consumedBvid = consumedBvid,
            queuePreviewLimit = state.config.queuePreviewLimit
        )
        if (update.consumedApplied) {
            _todayWatchState.update { it.copy(plan = update.updatedPlan) }
        }
        if (update.shouldRefill) {
            requestTodayWatchRebuild(forceReloadHistory = false)
        }
    }

    fun markTodayWatchNotInterested(video: VideoItem) {
        todayWatchCoordinator.markNotInterested(video)
        val state = _todayWatchState.value

        val update = todayWatchCoordinator.consumeFromPlan(
            plan = state.plan,
            consumedBvid = video.bvid.takeIf { it.isNotBlank() }.orEmpty(),
            queuePreviewLimit = state.config.queuePreviewLimit
        )
        if (update.consumedApplied) {
            _todayWatchState.update { it.copy(plan = update.updatedPlan) }
        }
        requestTodayWatchRebuild(forceReloadHistory = false)
    }

    fun markWatchLater(video: VideoItem, onResult: (Result<Boolean>) -> Unit = {}) {
        viewModelScope.launch {
            val result = if (video.aid > 0L) {
                ActionRepository.toggleWatchLater(aid = video.aid, add = true)
            } else {
                Result.failure(Exception("无法添加直播或无效视频到稍后再看"))
            }
            onResult(result)
        }
    }

    fun markRecommendNotInterested(video: VideoItem) {
        val nextSnapshot = feedController.dismiss(video) ?: return
        _recommendVideoItems.value = nextSnapshot.recommendVideoItems
        requestTodayWatchRebuild(forceReloadHistory = false)
    }

    fun primeVideoDetail(video: VideoItem) {
        detailPrefetcher.prime(video)
    }

    fun prefetchVideoDetail(video: VideoItem) {
        detailPrefetcher.prefetch(video)
    }

    private fun observeFeedPluginUpdates() {
        viewModelScope.launch {
            PluginManager.feedPluginUpdateToken.collectLatest { token ->
                if (token <= 0L || !feedController.hasSourceItems()) return@collectLatest
                val nextSnapshot = feedController.reapplyPluginFilters()
                _recommendVideoItems.value = nextSnapshot.recommendVideoItems
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
                val previousConfig = _todayWatchState.value.config

                todayWatchPlugin = plugin
                applyTodayWatchConfig(
                    enabled = enabled,
                    config = config
                )

                todayWatchPluginConfigJob?.cancel()
                if (plugin != null) {
                    todayWatchPluginConfigJob = viewModelScope.launch {
                        plugin.configState.collectLatest { latestConfig ->
                            val oldConfig = _todayWatchState.value.config
                            applyTodayWatchConfig(
                                enabled = PluginManager.plugins.any {
                                    it.plugin.id == TodayWatchPlugin.PLUGIN_ID && it.enabled
                                },
                                config = latestConfig
                            )
                            val forceReloadHistory =
                                latestConfig.historySampleLimit != oldConfig.historySampleLimit ||
                                    latestConfig.refreshTriggerToken != oldConfig.refreshTriggerToken
                            if (_todayWatchState.value.enabled) {
                                requestTodayWatchRebuild(forceReloadHistory = forceReloadHistory)
                            }
                        }
                    }
                }

                if (enabled) {
                    val forceReloadHistory =
                        previousConfig.historySampleLimit != config.historySampleLimit ||
                            previousConfig.refreshTriggerToken != config.refreshTriggerToken ||
                            !todayWatchCoordinator.hasCachedHistorySample()
                    requestTodayWatchRebuild(forceReloadHistory = forceReloadHistory)
                }
            }
        }
    }

    private fun applyTodayWatchConfig(
        enabled: Boolean,
        config: TodayWatchPluginConfig
    ) {
        todayWatchCoordinator.applyRefreshToken(config)
        _todayWatchState.update { current ->
            current.copy(
                enabled = enabled,
                config = config,
                mode = config.currentMode,
                plan = if (enabled) {
                    current.plan.copy(mode = config.currentMode)
                } else {
                    TodayWatchPlan(mode = config.currentMode)
                },
                isLoading = if (enabled) current.isLoading else false,
                errorMessage = if (enabled) current.errorMessage else null
            )
        }
    }

    private fun requestTodayWatchRebuild(forceReloadHistory: Boolean) {
        if (!_todayWatchState.value.enabled) return
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
        val state = _todayWatchState.value
        if (!state.enabled) return

        val candidates = feedController.visibleSnapshot()
        if (candidates.isNotEmpty()) {
            _todayWatchState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null
                )
            }
        }

        val result = todayWatchCoordinator.buildPlan(
            candidates = candidates,
            config = state.config,
            mode = state.mode,
            forceReloadHistory = forceReloadHistory,
            isFeedLoading = _recommendLoadState.value.isLoading
        )

        _todayWatchState.update {
            it.copy(
                isLoading = false,
                plan = result.plan,
                errorMessage = result.errorMessage
            )
        }
    }

    private fun applyRecommendLoadSuccess(result: HomeFeedLoadResult.Success) {
        _recommendVideoItems.value = result.recommendVideoItems
        _recommendLoadState.update {
            it.copy(
                isLoading = false,
                isError = false,
                hasMore = result.hasMore,
                errorMsg = null,
            )
        }
        _refreshErrorMessage.value = null
    }
}
