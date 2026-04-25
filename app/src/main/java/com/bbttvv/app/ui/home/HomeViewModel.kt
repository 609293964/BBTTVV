package com.bbttvv.app.ui.home

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.ActionRepository
import com.bbttvv.app.feature.plugin.TodayWatchPlugin
import com.bbttvv.app.feature.plugin.TodayWatchPluginConfig
import com.bbttvv.app.ui.components.AppTopLevelTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val hasMore: Boolean = true,
    val errorMsg: String? = null,
    val refreshErrorMessage: String? = null,
    val todayWatchEnabled: Boolean = false,
    val todayWatchConfig: TodayWatchPluginConfig = TodayWatchPluginConfig(),
    val todayWatchMode: TodayWatchMode = TodayWatchMode.RELAX,
    val todayWatchPlan: TodayWatchPlan = TodayWatchPlan(),
    val todayWatchLoading: Boolean = false,
    val todayWatchErrorMsg: String? = null
)

private fun resolveTabStorePolicy(context: Context): TabStorePolicy {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    return if (activityManager?.isLowRamDevice == true) {
        TabStorePolicy.KeepSelectedOnly
    } else {
        TabStorePolicy.KeepRecentTwo
    }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val tabStoreOwner = HomeTabStoreOwner(resolveTabStorePolicy(appContext))
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val detailPrefetcher = HomeDetailPrefetcher(viewModelScope)
    private val recommendDismissStore = RecommendDismissStore()
    private val feedController = HomeFeedController(recommendDismissStore)
    private val todayWatchCoordinator = TodayWatchCoordinator(appContext, viewModelScope)
    private var todayWatchPluginConfigJob: Job? = null
    private var todayWatchRebuildJob: Job? = null

    private var todayWatchPlugin: TodayWatchPlugin? = null
    private var selectedHomeTab: AppTopLevelTab = AppTopLevelTab.RECOMMEND
    private var pendingTodayWatchRebuildForceReload = false

    init {
        observeFeedPluginUpdates()
        observeTodayWatchPlugin()
        loadMore()
    }

    override fun onCleared() {
        super.onCleared()
        detailPrefetcher.clear()
        tabStoreOwner.clearAll()
    }

    fun tabViewModelStore(tab: AppTopLevelTab) = tabStoreOwner.getOrCreate(tab)

    fun trimTabViewModelStores(selectedTab: AppTopLevelTab) {
        tabStoreOwner.trimForSelected(selectedTab)
    }

    fun onHomeTabVisible(tab: AppTopLevelTab) {
        selectedHomeTab = tab
        if (tab != AppTopLevelTab.TODAY_WATCH) return

        val state = _uiState.value
        val needsInitialBuild = state.todayWatchEnabled &&
            !state.todayWatchLoading &&
            state.todayWatchPlan.videoQueue.isEmpty() &&
            feedController.hasVisibleItems()
        if (pendingTodayWatchRebuildForceReload || needsInitialBuild) {
            val forceReload = pendingTodayWatchRebuildForceReload
            pendingTodayWatchRebuildForceReload = false
            requestTodayWatchRebuild(forceReloadHistory = forceReload)
        }
    }

    fun loadMore() {
        if (feedController.isLoadingOrEndReached()) return

        _uiState.update {
            it.copy(
                isLoading = true,
                isError = false,
                errorMsg = null,
                refreshErrorMessage = null
            )
        }

        viewModelScope.launch {
            when (val result = feedController.loadMore()) {
                is HomeFeedLoadResult.Success -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            videos = result.videos,
                            hasMore = result.hasMore,
                            isError = false,
                            errorMsg = null
                        )
                    }
                    requestTodayWatchRebuild(forceReloadHistory = false)
                }
                is HomeFeedLoadResult.Failure -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            isError = true,
                            errorMsg = result.error.message ?: "未知错误"
                        )
                    }
                }
                HomeFeedLoadResult.Ignored -> Unit
            }
        }
    }

    fun refresh() {
        feedController.resetPageCursor()
        _uiState.update {
            it.copy(
                isLoading = true,
                isError = false,
                hasMore = true,
                errorMsg = null,
                refreshErrorMessage = null
            )
        }
        viewModelScope.launch {
            when (val result = feedController.refresh()) {
                is HomeFeedLoadResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isError = false,
                            errorMsg = null,
                            refreshErrorMessage = null,
                            videos = result.videos,
                            hasMore = result.hasMore
                        )
                    }
                    requestTodayWatchRebuild(forceReloadHistory = false)
                }
                is HomeFeedLoadResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isError = true,
                            errorMsg = result.error.message ?: "未知错误",
                            refreshErrorMessage = if (it.videos.isNotEmpty()) {
                                result.error.message ?: "刷新失败"
                            } else {
                                null
                            }
                        )
                    }
                }
                HomeFeedLoadResult.Ignored -> Unit
            }
        }
    }

    fun clearRefreshErrorMessage() {
        _uiState.update { state ->
            if (state.refreshErrorMessage == null) {
                state
            } else {
                state.copy(refreshErrorMessage = null)
            }
        }
    }

    fun refreshTodayWatchOnly() {
        if (!_uiState.value.todayWatchEnabled) return
        todayWatchCoordinator.collectManualRefreshConsumed(
            plan = _uiState.value.todayWatchPlan,
            previewLimit = _uiState.value.todayWatchConfig.queuePreviewLimit
        )
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
        todayWatchCoordinator.markVideoOpened(video)

        val update = todayWatchCoordinator.consumeFromPlan(
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
        todayWatchCoordinator.markNotInterested(video)

        val update = todayWatchCoordinator.consumeFromPlan(
            plan = _uiState.value.todayWatchPlan,
            consumedBvid = video.bvid.takeIf { it.isNotBlank() }.orEmpty(),
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
                Result.failure(Exception("无法添加直播或无效视频到稍后再看"))
            }
            onResult(result)
        }
    }

    fun markRecommendNotInterested(video: VideoItem) {
        val nextVisibleVideos = feedController.dismiss(video) ?: return
        _uiState.update { currentState ->
            currentState.copy(videos = nextVisibleVideos)
        }
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
                val nextVisibleVideos = feedController.reapplyPluginFilters()
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

        val candidates = feedController.visibleSnapshot()
        if (candidates.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    todayWatchLoading = true,
                    todayWatchErrorMsg = null
                )
            }
        }

        val result = todayWatchCoordinator.buildPlan(
            candidates = candidates,
            config = state.todayWatchConfig,
            mode = state.todayWatchMode,
            forceReloadHistory = forceReloadHistory,
            isFeedLoading = state.isLoading
        )

        _uiState.update {
            it.copy(
                todayWatchLoading = false,
                todayWatchPlan = result.plan,
                todayWatchErrorMsg = result.errorMessage
            )
        }
    }
}

