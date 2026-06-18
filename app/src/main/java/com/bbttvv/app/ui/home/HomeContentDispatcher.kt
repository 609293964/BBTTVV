package com.bbttvv.app.ui.home

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.recyclerview.widget.RecyclerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvContextMenu
import com.bbttvv.app.ui.components.TvContextMenuAction
import kotlinx.coroutines.delay

@Composable
fun TabViewModelScope(
    tabState: AppTopLevelTab,
    homeViewModel: com.bbttvv.app.ui.home.HomeViewModel,
    content: @Composable () -> Unit
) {
    val store = remember(tabState, homeViewModel) {
        homeViewModel.tabViewModelStore(tabState)
    }

    val parentOwner = LocalViewModelStoreOwner.current
    val parentHasFactory = parentOwner as? androidx.lifecycle.HasDefaultViewModelProviderFactory

    val owner = remember(store, parentHasFactory) {
        if (parentHasFactory != null) {
            object : ViewModelStoreOwner, androidx.lifecycle.HasDefaultViewModelProviderFactory {
                override val viewModelStore: ViewModelStore = store
                override val defaultViewModelProviderFactory = parentHasFactory.defaultViewModelProviderFactory
                override val defaultViewModelCreationExtras = parentHasFactory.defaultViewModelCreationExtras
            }
        } else {
            object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = store
            }
        }
    }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner
    ) {
        content()
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun HomeContentDispatcher(
    selectedHomeTab: AppTopLevelTab,
    visibleHomeTabs: List<AppTopLevelTab>,
    recommendVideoItems: List<HomeRecommendVideoCardItem>,
    viewModel: HomeViewModel,
    dynamicRefreshRequestId: Int,
    tabGridFocusStates: HomeTabGridFocusStates,
    recommendGridFocusState: HomeRecommendGridFocusState,
    focusCoordinator: HomeFocusCoordinator,
    recyclerPools: HomeRecyclerPools,
    tabResidencyState: HomeTabResidencyState,
    topBarHeightPx: Int,
    collapseHeaderEnabled: Boolean,
    collapsingHeaderStates: HomeTabCollapsingHeaderStates,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onLiveClick: (Long, String?) -> Unit,
    onRecommendVideoClick: (String, VideoItem) -> Unit,
    onSearchVideoClick: (String, VideoItem) -> Unit,
    onDynamicVideoClick: (String, VideoItem) -> Unit,
    onOpenSettings: () -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, VideoItem) -> Unit,
    onOpenUp: (Long) -> Unit = {},
    restoreRecommendInitialScrollIndex: Int = -1,
) {
    var pendingRecommendMenuRequest by remember { mutableStateOf<RecommendContextMenuRequest?>(null) }
    var pendingRecommendFocusReturnKey by remember { mutableStateOf<String?>(null) }
    var suppressRecommendMenuConfirmKeyUp by remember { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val composedHomeTabs = HomeTabCompositionPolicy.resolve(
        visibleTabs = visibleHomeTabs,
        selectedTab = selectedHomeTab,
        residentTabs = tabResidencyState.residentTabsInDisplayOrder(),
    )
    val context = LocalContext.current

    LaunchedEffect(
        recommendVideoItems.isNotEmpty(),
        tabResidencyState.generation,
        visibleHomeTabs,
    ) {
        if (recommendVideoItems.isEmpty()) return@LaunchedEffect
        delay(HomeTabPrewarmIdleDelayMs)
        withFrameNanos { }
        tabResidencyState.prewarmNextAdjacent()
    }

    DisposableEffect(context.applicationContext, tabResidencyState, recyclerPools) {
        val applicationContext = context.applicationContext
        val callbacks = object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            override fun onLowMemory() {
                tabResidencyState.trimToSelected()
                recyclerPools.clear()
            }

            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    tabResidencyState.trimToSelected()
                    recyclerPools.clear()
                }
            }
        }
        applicationContext.registerComponentCallbacks(callbacks)
        onDispose {
            applicationContext.unregisterComponentCallbacks(callbacks)
        }
    }

    LaunchedEffect(selectedHomeTab, composedHomeTabs) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        Log.d(
            "HomeResidency",
            "selected=$selectedHomeTab resident=${composedHomeTabs.joinToString()}",
        )
    }

    LaunchedEffect(selectedHomeTab) {
        if (selectedHomeTab != AppTopLevelTab.RECOMMEND) {
            pendingRecommendMenuRequest = null
            pendingRecommendFocusReturnKey = null
            suppressRecommendMenuConfirmKeyUp = false
        }
    }

    LaunchedEffect(pendingRecommendFocusReturnKey, selectedHomeTab, recommendVideoItems) {
        val focusKey = pendingRecommendFocusReturnKey ?: return@LaunchedEffect
        if (selectedHomeTab != AppTopLevelTab.RECOMMEND) {
            pendingRecommendFocusReturnKey = null
            return@LaunchedEffect
        }
        withFrameNanos { }
        recommendGridFocusState.tryFocusKeyOrFallback(focusKey)
        pendingRecommendFocusReturnKey = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        composedHomeTabs.forEach { tab ->
            saveableStateHolder.SaveableStateProvider(tab.name) {
                key(tab) {
                    KeepAliveHomeTabPage(
                        tab = tab,
                        selectedHomeTab = selectedHomeTab,
                    ) {
                        HomeTabContent(
                            tab = tab,
                            recommendVideoItems = recommendVideoItems,
                            viewModel = viewModel,
                            dynamicRefreshRequestId = dynamicRefreshRequestId,
                            tabGridFocusStates = tabGridFocusStates,
                            recommendGridFocusState = recommendGridFocusState,
                            focusCoordinator = focusCoordinator,
                            recyclerPools = recyclerPools,
                            topBarHeightPx = topBarHeightPx,
                            collapseHeaderEnabled = collapseHeaderEnabled && tab == selectedHomeTab,
                            collapsingHeaderState = collapsingHeaderStates.stateFor(tab),
                            onRequestTopBarFocus = onRequestTopBarFocus,
                            onTabSelected = onTabSelected,
                            onVideoClick = onVideoClick,
                            onLiveClick = onLiveClick,
                            onRecommendVideoClick = onRecommendVideoClick,
                            onSearchVideoClick = onSearchVideoClick,
                            onDynamicVideoClick = onDynamicVideoClick,
                            onOpenSettings = onOpenSettings,
                            onProfileVideoClick = onProfileVideoClick,
                            onOpenUp = onOpenUp,
                            restoreRecommendInitialScrollIndex = if (tab == selectedHomeTab) {
                                restoreRecommendInitialScrollIndex
                            } else {
                                RecyclerView.NO_POSITION
                            },
                            onOpenRecommendMenu = { video, focusKey ->
                                suppressRecommendMenuConfirmKeyUp = true
                                pendingRecommendMenuRequest = RecommendContextMenuRequest(
                                    video = video,
                                    focusKey = focusKey,
                                )
                            },
                        )
                    }
                }
            }
        }

        RecommendContextMenuHost(
            request = pendingRecommendMenuRequest,
            viewModel = viewModel,
            suppressConfirmKey = suppressRecommendMenuConfirmKeyUp,
            modifier = Modifier.align(Alignment.Center),
            onSuppressConfirmKeyConsumed = { suppressRecommendMenuConfirmKeyUp = false },
            onDismissRequest = {
                pendingRecommendFocusReturnKey = pendingRecommendMenuRequest?.focusKey
                pendingRecommendMenuRequest = null
                suppressRecommendMenuConfirmKeyUp = false
            }
        )
    }
}

internal object HomeTabCompositionPolicy {
    fun resolve(
        visibleTabs: List<AppTopLevelTab>,
        selectedTab: AppTopLevelTab,
        residentTabs: Collection<AppTopLevelTab>,
    ): List<AppTopLevelTab> {
        return visibleTabs.filter { tab ->
            tab == selectedTab || tab in residentTabs
        }
    }
}

@Composable
private fun BoxScope.KeepAliveHomeTabPage(
    tab: AppTopLevelTab,
    selectedHomeTab: AppTopLevelTab,
    content: @Composable () -> Unit,
) {
    val isSelected = tab == selectedHomeTab
    LaunchedEffect(isSelected) {
        if (!isSelected) return@LaunchedEffect
        withFrameNanos { }
        HomeTabSwitchTrace.onTabVisible(tab)
    }
    CompositionLocalProvider(LocalHomeTabActive provides isSelected) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (isSelected) 1f else 0f)
                .graphicsLayer {
                    alpha = if (isSelected) 1f else 0f
                }
                .focusProperties {
                    canFocus = isSelected
                }
        ) {
            content()
        }
    }
}

internal object HomeTabSwitchTrace {
    private data class PendingSwitch(
        val target: AppTopLevelTab,
        val requestedAtMs: Long,
    )

    private var pendingSwitch: PendingSwitch? = null

    fun onSwitchRequested(
        from: AppTopLevelTab,
        to: AppTopLevelTab,
    ) {
        if (!BuildConfig.DEBUG || from == to) return
        val requestedAtMs = SystemClock.uptimeMillis()
        pendingSwitch = PendingSwitch(
            target = to,
            requestedAtMs = requestedAtMs,
        )
        Log.d(
            HomeTabSwitchTag,
            "request from=$from to=$to uptimeMs=$requestedAtMs",
        )
    }

    fun onTabVisible(tab: AppTopLevelTab) {
        if (!BuildConfig.DEBUG) return
        val nowMs = SystemClock.uptimeMillis()
        val pending = pendingSwitch?.takeIf { it.target == tab } ?: return
        val latencyMs = (nowMs - pending.requestedAtMs).coerceAtLeast(0L)
        pendingSwitch = null
        Log.d(
            HomeTabSwitchTag,
            "visible tab=$tab latencyMs=$latencyMs uptimeMs=$nowMs",
        )
    }
}

private const val HomeTabPrewarmIdleDelayMs = 180L
private const val HomeTabSwitchTag = "HomeTabSwitch"

@Composable
private fun HomeTabContent(
    tab: AppTopLevelTab,
    recommendVideoItems: List<HomeRecommendVideoCardItem>,
    viewModel: HomeViewModel,
    dynamicRefreshRequestId: Int,
    tabGridFocusStates: HomeTabGridFocusStates,
    recommendGridFocusState: HomeRecommendGridFocusState,
    focusCoordinator: HomeFocusCoordinator,
    recyclerPools: HomeRecyclerPools,
    topBarHeightPx: Int,
    collapseHeaderEnabled: Boolean,
    collapsingHeaderState: HomeCollapsingHeaderState,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onLiveClick: (Long, String?) -> Unit,
    onRecommendVideoClick: (String, VideoItem) -> Unit,
    onSearchVideoClick: (String, VideoItem) -> Unit,
    onDynamicVideoClick: (String, VideoItem) -> Unit,
    onOpenSettings: () -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, VideoItem) -> Unit,
    onOpenUp: (Long) -> Unit,
    restoreRecommendInitialScrollIndex: Int,
    onOpenRecommendMenu: (VideoItem, String) -> Unit,
) {
    when (tab) {
        AppTopLevelTab.SEARCH -> SearchTabContent(
            selectedHomeTab = tab,
            viewModel = viewModel,
            focusCoordinator = focusCoordinator,
            videoCardRecycledViewPool = recyclerPools.videoCardPool,
            onRequestTopBarFocus = onRequestTopBarFocus,
            onTabSelected = onTabSelected,
            onVideoClick = onSearchVideoClick,
            onOpenUp = onOpenUp
        )

        AppTopLevelTab.RECOMMEND -> RecommendTabContent(
            recommendVideoItems = recommendVideoItems,
            viewModel = viewModel,
            recommendGridFocusState = recommendGridFocusState,
            focusCoordinator = focusCoordinator,
            videoCardRecycledViewPool = recyclerPools.videoCardPool,
            topBarHeightPx = topBarHeightPx,
            collapseHeaderEnabled = collapseHeaderEnabled,
            collapsingHeaderState = collapsingHeaderState,
            restoreInitialScrollIndex = restoreRecommendInitialScrollIndex,
            onRequestTopBarFocus = onRequestTopBarFocus,
            onRecommendVideoClick = onRecommendVideoClick,
            onOpenRecommendMenu = onOpenRecommendMenu,
        )

        AppTopLevelTab.TODAY_WATCH -> {
            TodayWatchTabContent(
                viewModel = viewModel,
                onVideoClick = onVideoClick,
                onContentRowFocused = focusCoordinator::onContentRowFocused,
                focusCoordinator = focusCoordinator,
                videoCardRecycledViewPool = recyclerPools.videoCardPool,
                topBarHeightPx = topBarHeightPx,
                collapseHeaderEnabled = collapseHeaderEnabled,
                collapsingHeaderState = collapsingHeaderState
            )
        }

        AppTopLevelTab.POPULAR -> {
            PopularScreen(
                onVideoClick = onVideoClick,
                onContentRowFocused = focusCoordinator::onContentRowFocused,
                focusCoordinator = focusCoordinator,
                videoCardRecycledViewPool = recyclerPools.videoCardPool,
                gridColumnCount = 4,
                focusState = tabGridFocusStates.stateFor(AppTopLevelTab.POPULAR),
                topBarHeightPx = topBarHeightPx,
                collapseHeaderEnabled = collapseHeaderEnabled,
                collapsingHeaderState = collapsingHeaderState
            )
        }

        AppTopLevelTab.LIVE -> {
            LiveScreen(
                onLiveClick = onLiveClick,
                onContentRowFocused = focusCoordinator::onContentRowFocused,
                focusCoordinator = focusCoordinator,
                videoCardRecycledViewPool = recyclerPools.videoCardPool,
                gridColumnCount = 4,
                focusState = tabGridFocusStates.stateFor(AppTopLevelTab.LIVE),
                topBarHeightPx = topBarHeightPx,
                collapseHeaderEnabled = collapseHeaderEnabled,
                collapsingHeaderState = collapsingHeaderState
            )
        }

        AppTopLevelTab.DYNAMIC -> {
            DynamicTabContent(
                selectedHomeTab = tab,
                viewModel = viewModel,
                onVideoClick = onDynamicVideoClick,
                onLiveClick = { roomId -> onLiveClick(roomId, null) },
                onOpenUp = onOpenUp,
                dynamicRefreshRequestId = dynamicRefreshRequestId,
                onContentRowFocused = focusCoordinator::onContentRowFocused,
                focusCoordinator = focusCoordinator,
                videoCardRecycledViewPool = recyclerPools.videoCardPool,
                gridColumnCount = 4,
                focusState = tabGridFocusStates.stateFor(AppTopLevelTab.DYNAMIC),
                topBarHeightPx = topBarHeightPx,
                collapseHeaderEnabled = collapseHeaderEnabled,
                collapsingHeaderState = collapsingHeaderState
            )
        }

        AppTopLevelTab.WATCH_LATER -> WatchLaterTabContent(
            selectedHomeTab = tab,
            viewModel = viewModel,
            focusCoordinator = focusCoordinator,
            videoCardRecycledViewPool = recyclerPools.videoCardPool,
            onRequestTopBarFocus = onRequestTopBarFocus,
            onProfileVideoClick = onProfileVideoClick
        )

        AppTopLevelTab.PROFILE -> ProfileTabContent(
            selectedHomeTab = tab,
            viewModel = viewModel,
            focusCoordinator = focusCoordinator,
            videoCardRecycledViewPool = recyclerPools.videoCardPool,
            onRequestTopBarFocus = onRequestTopBarFocus,
            onOpenSettings = onOpenSettings,
            onProfileVideoClick = onProfileVideoClick
        )
    }
}

@Composable
private fun TodayWatchTabContent(
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onContentRowFocused: (Int) -> Unit,
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    topBarHeightPx: Int,
    collapseHeaderEnabled: Boolean,
    collapsingHeaderState: HomeCollapsingHeaderState
) {
    val todayWatchState by viewModel.todayWatchState.collectAsStateWithLifecycle()

    TodayWatchScreen(
        plan = todayWatchState.plan,
        config = todayWatchState.config,
        selectedMode = todayWatchState.mode,
        isLoading = todayWatchState.isLoading,
        errorMessage = todayWatchState.errorMessage,
        onModeActivated = { mode ->
            if (todayWatchState.mode == mode) {
                viewModel.refreshTodayWatchOnly()
            } else {
                viewModel.setTodayWatchMode(mode)
            }
        },
        onOpenVideo = { video ->
            viewModel.primeVideoDetail(video)
            viewModel.markTodayWatchVideoOpened(video)
            onVideoClick(video)
        },
        onNotInterested = viewModel::markTodayWatchNotInterested,
        onVideoFocused = viewModel::prefetchVideoDetail,
        onContentRowFocused = onContentRowFocused,
        focusCoordinator = focusCoordinator,
        videoCardRecycledViewPool = videoCardRecycledViewPool,
        topBarHeightPx = topBarHeightPx,
        collapseHeaderEnabled = collapseHeaderEnabled,
        collapsingHeaderState = collapsingHeaderState
    )
}

@Composable
private fun DynamicTabContent(
    selectedHomeTab: AppTopLevelTab,
    viewModel: HomeViewModel,
    onVideoClick: (String, VideoItem) -> Unit,
    onLiveClick: (Long) -> Unit,
    onOpenUp: (Long) -> Unit,
    dynamicRefreshRequestId: Int,
    onContentRowFocused: (Int) -> Unit,
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    gridColumnCount: Int,
    focusState: HomeRecommendGridFocusState,
    topBarHeightPx: Int,
    collapseHeaderEnabled: Boolean,
    collapsingHeaderState: HomeCollapsingHeaderState
) {
    TabViewModelScope(selectedHomeTab, viewModel) {
        val dynamicViewModel: DynamicViewModel = composeViewModel()
        LaunchedEffect(dynamicRefreshRequestId) {
            dynamicViewModel.consumeRefreshRequest(dynamicRefreshRequestId)
        }
        DynamicScreen(
            onVideoClick = onVideoClick,
            onLiveClick = onLiveClick,
            onOpenUp = onOpenUp,
            viewModel = dynamicViewModel,
            onContentRowFocused = onContentRowFocused,
            focusCoordinator = focusCoordinator,
            videoCardRecycledViewPool = videoCardRecycledViewPool,
            gridColumnCount = gridColumnCount,
            focusState = focusState,
            topBarHeightPx = topBarHeightPx,
            collapseHeaderEnabled = collapseHeaderEnabled,
            collapsingHeaderState = collapsingHeaderState
        )
    }
}

@Composable
private fun SearchTabContent(
    selectedHomeTab: AppTopLevelTab,
    viewModel: HomeViewModel,
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onVideoClick: (String, VideoItem) -> Unit,
    onOpenUp: (Long) -> Unit
) {
    TabViewModelScope(selectedHomeTab, viewModel) {
        com.bbttvv.app.feature.search.SearchScreen(
            onBack = { onTabSelected(AppTopLevelTab.RECOMMEND) },
            onRequestTopBarFocus = { onRequestTopBarFocus(HomeFocusScene.BackToTopBar) },
            onOpenVideo = onVideoClick,
            onOpenUp = onOpenUp,
            focusCoordinator = focusCoordinator,
            focusTab = selectedHomeTab,
            videoCardRecycledViewPool = videoCardRecycledViewPool
        )
    }
}

@Composable
private fun RecommendTabContent(
    recommendVideoItems: List<HomeRecommendVideoCardItem>,
    viewModel: HomeViewModel,
    recommendGridFocusState: HomeRecommendGridFocusState,
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    topBarHeightPx: Int,
    collapseHeaderEnabled: Boolean,
    collapsingHeaderState: HomeCollapsingHeaderState,
    restoreInitialScrollIndex: Int,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onRecommendVideoClick: (String, VideoItem) -> Unit,
    onOpenRecommendMenu: (VideoItem, String) -> Unit
) {
    HomeCollapsingHeaderGrid(
        topBarHeightPx = topBarHeightPx,
        state = collapsingHeaderState,
        modifier = Modifier.fillMaxSize(),
        collapseEnabled = collapseHeaderEnabled,
        localHeader = null,
    ) { topPadding, onScrollOffset ->
        if (recommendVideoItems.isEmpty()) {
            RecommendEmptyState(
                viewModel = viewModel,
                focusCoordinator = focusCoordinator,
                topPadding = topPadding,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                VideoCardRecyclerGridItems(
                    items = recommendVideoItems,
                    modifier = Modifier.fillMaxSize(),
                    gridColumnCount = 4,
                    contentPadding = PaddingValues(
                        start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        top = topPadding + AppTopBarDefaults.HeaderContentTopPadding,
                        bottom = AppTopBarDefaults.HomeVideoGridBottomPadding
                    ),
                    focusState = recommendGridFocusState,
                    focusCoordinator = focusCoordinator,
                    focusTab = AppTopLevelTab.RECOMMEND,
                    initialScrollPosition = restoreInitialScrollIndex,
                    allowChildDrawingOutsideBounds = false,
                    videoCardRecycledViewPool = videoCardRecycledViewPool,
                    onVerticalScrollOffsetChanged = onScrollOffset,
                    canLoadMore = viewModel::canLoadMoreRecommend,
                    onLoadMore = viewModel::loadMore,
                    onMenuRefresh = viewModel::refresh,
                    onVideoFocused = { video, _ ->
                        viewModel.prefetchVideoDetail(video)
                    },
                    onFocusedRowChanged = focusCoordinator::onContentRowFocused,
                    onTopRowDpadUp = {
                        collapsingHeaderState.reset()
                        onRequestTopBarFocus(HomeFocusScene.BackToTopBar)
                    },
                    onBackToTopBar = {
                        HomeRecommendBackReturnPolicy.handleBackToTopBar(
                            resetGridToTop = {
                                collapsingHeaderState.reset()
                                recommendGridFocusState.resetRememberedFocusToTopForTopBarReturn()
                            },
                            requestTopBarFocus = { onRequestTopBarFocus(HomeFocusScene.BackToTopBar) },
                        )
                    },
                    onVideoLongClick = { video, key ->
                        onOpenRecommendMenu(video, key)
                    },
                    onVideoClick = { video, key ->
                        viewModel.primeVideoDetail(video)
                        onRecommendVideoClick(key, video)
                    }
                )
                RecommendRefreshErrorBanner(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun RecommendEmptyState(
    viewModel: HomeViewModel,
    focusCoordinator: HomeFocusCoordinator,
    topPadding: Dp,
) {
    val loadState by viewModel.recommendLoadState.collectAsStateWithLifecycle()
    HomeEmptyFocusTarget(
        tab = AppTopLevelTab.RECOMMEND,
        focusCoordinator = focusCoordinator,
        isActive = LocalHomeTabActive.current,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding),
    ) {
        if (loadState.isLoading) {
            Text(
                text = "正在加载推荐内容...",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onBackground
            )
        } else if (loadState.isError) {
            Text(
                text = "加载失败：${loadState.errorMsg ?: "未知错误"}",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = "暂无推荐内容",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun BoxScope.RecommendRefreshErrorBanner(
    viewModel: HomeViewModel,
) {
    val message by viewModel.refreshErrorMessage.collectAsStateWithLifecycle()
    message?.let { errorMessage ->
        LaunchedEffect(errorMessage) {
            kotlinx.coroutines.delay(3_000L)
            viewModel.clearRefreshErrorMessage()
        }
        Text(
            text = "刷新失败：$errorMessage",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = AppTopBarDefaults.HeaderContentTopPadding,
                    end = AppTopBarDefaults.HeaderContentHorizontalPadding
                )
                .background(
                    color = Color(0xCC2A1515),
                    shape = RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun WatchLaterTabContent(
    selectedHomeTab: AppTopLevelTab,
    viewModel: HomeViewModel,
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onProfileVideoClick: (AppTopLevelTab, String, VideoItem) -> Unit
) {
    TabViewModelScope(selectedHomeTab, viewModel) {
        com.bbttvv.app.feature.profile.WatchLaterVideosScreen(
            onOpenVideo = { focusKey, video ->
                onProfileVideoClick(AppTopLevelTab.WATCH_LATER, focusKey, video)
            },
            onRequestTopBarFocus = { onRequestTopBarFocus(HomeFocusScene.BackToTopBar) },
            focusCoordinator = focusCoordinator,
            focusTab = AppTopLevelTab.WATCH_LATER,
            videoCardRecycledViewPool = videoCardRecycledViewPool
        )
    }
}

@Composable
private fun ProfileTabContent(
    selectedHomeTab: AppTopLevelTab,
    viewModel: HomeViewModel,
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onOpenSettings: () -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, VideoItem) -> Unit
) {
    TabViewModelScope(selectedHomeTab, viewModel) {
        com.bbttvv.app.feature.profile.ProfileScreen(
            onOpenSettings = onOpenSettings,
            onOpenVideo = { focusKey, video ->
                onProfileVideoClick(AppTopLevelTab.PROFILE, focusKey, video)
            },
            onRequestTopBarFocus = { onRequestTopBarFocus(HomeFocusScene.BackToTopBar) },
            focusCoordinator = focusCoordinator,
            focusTab = selectedHomeTab,
            videoCardRecycledViewPool = videoCardRecycledViewPool
        )
    }
}

private data class RecommendContextMenuRequest(
    val video: VideoItem,
    val focusKey: String,
)

@Composable
private fun RecommendContextMenuHost(
    request: RecommendContextMenuRequest?,
    viewModel: HomeViewModel,
    suppressConfirmKey: Boolean,
    modifier: Modifier = Modifier,
    onSuppressConfirmKeyConsumed: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current

    val video = request?.video ?: return

    TvContextMenu(
        actions = listOf(
            TvContextMenuAction(
                text = "稍后再看",
                supportingText = "加入列表，稍后从我的页面继续观看",
                accentColor = Color(0xFF7CCBFF),
            ) {
                onDismissRequest()
                viewModel.markWatchLater(video) { result ->
                    val message = if (result.isSuccess) {
                        "已添加到稍后再看"
                    } else {
                        result.exceptionOrNull()?.message ?: "添加稍后再看失败"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            TvContextMenuAction(
                text = "不感兴趣",
                supportingText = "减少类似推荐，并从当前列表移除",
                accentColor = Color(0xFFFF8EA3),
            ) {
                onDismissRequest()
                viewModel.markRecommendNotInterested(video)
                Toast.makeText(context, "已移除该推荐", Toast.LENGTH_SHORT).show()
            },
        ),
        modifier = modifier,
        suppressConfirmKey = suppressConfirmKey,
        onSuppressConfirmKeyConsumed = onSuppressConfirmKeyConsumed,
        onDismissRequest = onDismissRequest,
    )
}
