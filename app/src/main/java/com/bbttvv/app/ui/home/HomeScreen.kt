package com.bbttvv.app.ui.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.bbttvv.app.core.performance.AppPerformanceTracker
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBar
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.focus.RegisterLifecycleFocusDrain
import com.bbttvv.app.ui.focus.RegisterTvFocusEscapeTarget
import com.bbttvv.app.ui.focus.isSameOrDescendantOf
import com.bbttvv.app.ui.theme.LocalIsLightTheme
import com.bbttvv.app.ui.theme.LocalSidebarTheme

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun HomeScreen(
    viewModel: HomeViewModel,
    recyclerPools: HomeRecyclerPools,
    visibleTabs: List<AppTopLevelTab>,
    selectedTabIndex: Int,
    selectTopTabOnFocusEnabled: Boolean,
    restoreVideoFocusKey: String? = null,
    restoreVideoFocusTab: AppTopLevelTab? = null,
    hasPendingVideoFocusRestore: Boolean = restoreVideoFocusKey != null,
    onVideoFocusRestored: (String) -> Unit = {},
    onCancelVideoFocusRestore: () -> Unit = {},
    onVideoClick: (VideoItem) -> Unit,
    onLiveClick: (Long, String?) -> Unit,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onRecommendVideoClick: (String, VideoItem) -> Unit = { _, video -> onVideoClick(video) },
    onSearchVideoClick: (String, VideoItem) -> Unit = { _, video -> onVideoClick(video) },
    onDynamicVideoClick: (String, VideoItem) -> Unit = { _, video -> onVideoClick(video) },
    onOpenSettings: (String) -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, com.bbttvv.app.data.model.response.VideoItem) -> Unit = { _, _, _ -> },
    onOpenUp: (Long) -> Unit = {}
) {
    val recommendVideoItems by viewModel.recommendVideoItems.collectAsStateWithLifecycle()
    val dynamicRefreshRequest = remember { mutableIntStateOf(0) }
    val tabGridFocusStates = remember { HomeTabGridFocusStates() }
    val recommendGridFocusState = tabGridFocusStates.stateFor(AppTopLevelTab.RECOMMEND)
    val collapsingHeaderStates = remember { HomeTabCollapsingHeaderStates() }
    val topBarHeightState = remember { mutableIntStateOf(0) }
    val homeTabs = remember(visibleTabs) { visibleTabs.filter(AppTopLevelTab::isHomeContent) }
    val recommendVideoCount = recommendVideoItems.size
    val recommendHasVideos = recommendVideoCount > 0
    val recommendIsEmpty = !recommendHasVideos
    val selectedHomeTab = AppTopLevelTab.resolveVisibleHomeTab(
        index = selectedTabIndex,
        visibleTabs = visibleTabs
    )
    val tabResidencyState = remember {
        HomeTabResidencyState(
            initialSelectedTab = selectedHomeTab,
            // CPU-bound TVs should only measure/layout the visible page. Saveable UI
            // state and the immediately previous tab's ViewModelStore are retained
            // separately, so returning does not require a hidden full-page composition.
            maxResidentTabs = 1,
        )
    }
    val focusCoordinator = remember { HomeFocusCoordinator(selectedHomeTab) }
    DisposableEffect(focusCoordinator, onCancelVideoFocusRestore) {
        focusCoordinator.setPendingRestoreCancelCallback(onCancelVideoFocusRestore)
        onDispose {
            focusCoordinator.setPendingRestoreCancelCallback(null)
        }
    }
    RegisterHomeFocusEscapeGuard(focusCoordinator)
    RegisterLifecycleFocusDrain(key = focusCoordinator) {
        focusCoordinator.drainPendingFocus()
    }
    val restoreTargetTab = restoreVideoFocusTab ?: AppTopLevelTab.RECOMMEND
    val isVideoFocusRestorePending = selectedHomeTab == restoreTargetTab &&
        hasPendingVideoFocusRestore &&
        restoreVideoFocusKey != null
    LaunchedEffect(homeTabs) {
        tabResidencyState.updateVisibleTabs(homeTabs)
    }
    LaunchedEffect(selectedHomeTab) {
        tabResidencyState.select(selectedHomeTab)
    }
    LaunchedEffect(hasPendingVideoFocusRestore, restoreTargetTab) {
        tabResidencyState.updatePinnedTab(
            restoreTargetTab.takeIf { hasPendingVideoFocusRestore }
        )
    }
    LaunchedEffect(visibleTabs) {
        tabGridFocusStates.retainVisibleTabs(visibleTabs.toSet())
        collapsingHeaderStates.retainVisibleTabs(visibleTabs.toSet())
        focusCoordinator.retainVisibleTabs(visibleTabs.toSet())
    }
    LaunchedEffect(selectedHomeTab) {
        if (!isVideoFocusRestorePending) {
            resetCollapsingTabForSwitch(
                tab = selectedHomeTab,
                collapsingHeaderStates = collapsingHeaderStates,
            )
        }
        focusCoordinator.updateSelectedHomeTab(selectedHomeTab)
        viewModel.onHomeTabVisible(selectedHomeTab)
    }
    val effectiveFocusScene = if (isVideoFocusRestorePending) {
        HomeFocusScene.BackReturn
    } else {
        focusCoordinator.scene
    }
    val restoreRecommendVideoIndex = restoreVideoFocusKey?.let { focusKey ->
        recommendVideoItems.indexOfFirst { it.key == focusKey }.takeIf { it >= 0 }
    } ?: -1
    val shouldResumeVideoContentFocus = HomeFocusStrategy.shouldRestoreBackReturnVideoFocus(
        scene = effectiveFocusScene,
        selectedHomeTab = selectedHomeTab,
        restoreVideoFocusTab = restoreTargetTab,
        restoreVideoFocusKey = restoreVideoFocusKey,
        restoreVideoIndex = restoreRecommendVideoIndex,
    )
    val restoreRecommendInitialScrollIndex = if (
        shouldResumeVideoContentFocus &&
        restoreTargetTab == AppTopLevelTab.RECOMMEND
    ) {
        restoreRecommendVideoIndex
    } else {
        -1
    }
    RecommendSkeletonMilestoneEffect(
        selectedHomeTab = selectedHomeTab,
        recommendIsEmpty = recommendIsEmpty,
        viewModel = viewModel,
    )

    LaunchedEffect(selectedHomeTab, recommendHasVideos) {
        if (selectedHomeTab != AppTopLevelTab.RECOMMEND) return@LaunchedEffect
        if (!recommendHasVideos) return@LaunchedEffect
        withFrameNanos { }
        AppPerformanceTracker.markMilestoneOnce(
            key = "home_first_batch_visible",
            extras = "count=$recommendVideoCount"
        )
    }

    LaunchedEffect(restoreVideoFocusKey, restoreTargetTab) {
        if (restoreVideoFocusKey != null) {
            focusCoordinator.updateScene(HomeFocusScene.BackReturn)
        }
    }

    LaunchedEffect(shouldResumeVideoContentFocus) {
        if (shouldResumeVideoContentFocus) {
            focusCoordinator.prepareForContentFocus(HomeFocusScene.BackReturn)
        }
    }

    LaunchedEffect(selectedHomeTab, effectiveFocusScene, isVideoFocusRestorePending, recommendHasVideos) {
        fun shouldResetRecommendScrollNow(): Boolean {
            return HomeFocusStrategy.shouldResetRecommendScroll(
                scene = effectiveFocusScene,
                selectedHomeTab = selectedHomeTab,
                isRestoringBackReturnFocus = isVideoFocusRestorePending &&
                    restoreTargetTab == AppTopLevelTab.RECOMMEND,
                hasContentFocus = focusCoordinator.isContentFocused ||
                    recommendGridFocusState.hasFocusInside(),
                hasRememberedGridFocus = recommendGridFocusState.hasRememberedFocus(),
            )
        }

        if (recommendHasVideos && shouldResetRecommendScrollNow()) {
            withFrameNanos { }
            // First cold load can finish while the user is already moving through the grid.
            if (shouldResetRecommendScrollNow()) {
                recommendGridFocusState.requestScrollToTop()
            }
        }
    }

    LaunchedEffect(
        selectedHomeTab,
        effectiveFocusScene,
        restoreVideoFocusKey,
        restoreTargetTab,
        restoreRecommendVideoIndex
    ) {
        val focusKey = restoreVideoFocusKey
        if (
            !HomeFocusStrategy.shouldRestoreBackReturnVideoFocus(
                scene = effectiveFocusScene,
                selectedHomeTab = selectedHomeTab,
                restoreVideoFocusTab = restoreTargetTab,
                restoreVideoFocusKey = focusKey,
                restoreVideoIndex = restoreRecommendVideoIndex,
            )
        ) {
            return@LaunchedEffect
        }
        if (focusKey == null) return@LaunchedEffect
        focusCoordinator.requestRestoreVideoKey(
            tab = restoreTargetTab,
            key = focusKey,
            onRestored = onVideoFocusRestored,
        )
    }

    fun selectHomeTab(
        targetTab: AppTopLevelTab,
        scene: HomeFocusScene,
        keepTopBarFocus: Boolean = false,
    ) {
        HomeTabSwitchTrace.onSwitchRequested(
            from = selectedHomeTab,
            to = targetTab,
        )
        tabResidencyState.select(targetTab)
        resetCollapsingTabForSwitch(
            tab = targetTab,
            collapsingHeaderStates = collapsingHeaderStates,
        )
        if (keepTopBarFocus) {
            focusCoordinator.requestTopBarTabFocusAfterSwitch(
                tab = targetTab,
                scene = scene,
            )
        } else {
            focusCoordinator.updateScene(scene)
        }
        onTabSelected(targetTab)
    }

    fun refreshSelectedTopBarTab(tab: AppTopLevelTab) {
        when (tab) {
            AppTopLevelTab.RECOMMEND -> viewModel.refresh()
            AppTopLevelTab.DYNAMIC -> dynamicRefreshRequest.intValue += 1
            else -> Unit
        }
    }

    fun requestTopBarFromContent(scene: HomeFocusScene = HomeFocusScene.BackToTopBar): Boolean {
        onCancelVideoFocusRestore()
        collapsingHeaderStates.stateFor(selectedHomeTab).reset()
        return focusCoordinator.handleContentWantsTopBar(scene)
    }

    BackHandler(enabled = !focusCoordinator.isContentFocused && selectedHomeTab != AppTopLevelTab.RECOMMEND) {
        selectHomeTab(AppTopLevelTab.RECOMMEND, HomeFocusScene.BackToRecommend)
        requestTopBarFromContent(HomeFocusScene.BackToRecommend)
    }

    BackHandler(enabled = focusCoordinator.isContentFocused) {
        requestTopBarFromContent(HomeFocusScene.BackToTopBar)
    }

    val isLightTheme = LocalIsLightTheme.current
    val sidebarTheme = LocalSidebarTheme.current
    val popularHeaderViewModel: PopularViewModel? = if (sidebarTheme && selectedHomeTab == AppTopLevelTab.POPULAR) {
        val owner = remember(viewModel) {
            object : androidx.lifecycle.ViewModelStoreOwner {
                override val viewModelStore = viewModel.tabViewModelStore(AppTopLevelTab.POPULAR)
            }
        }
        androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = owner)
    } else null
    val popularHeaderState = popularHeaderViewModel?.uiState?.collectAsStateWithLifecycle()?.value
    val pageBackgroundColor = if (sidebarTheme) Color(0xFFF3F3F5) else if (isLightTheme) Color(0xFFF4F6F8) else MaterialTheme.colorScheme.background
    val sidebarWidth = if (sidebarTheme) 148.dp else 0.dp
    var lastVideoTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(AppTopLevelTab.RECOMMEND.name) }
    LaunchedEffect(selectedHomeTab) {
        if (HomeSidebarPolicy.isVideo(selectedHomeTab)) lastVideoTab = selectedHomeTab.name
    }
    val hasTopHeader = HomeSidebarPolicy.hasTopHeader(sidebarTheme, selectedHomeTab)
    val usesCollapsingHomeHeader = selectedHomeTab.usesCollapsingHomeHeader() && hasTopHeader
    val selectedCollapsingHeaderState = collapsingHeaderStates.stateFor(selectedHomeTab)
    val density = LocalDensity.current
    val fallbackTopBarHeightPx = with(density) { EstimatedHomeTopBarHeight.toPx().toInt() }
    val effectiveTopBarHeightPx = effectiveTopBarHeightPx(
        measuredTopBarHeightPx = topBarHeightState.intValue,
        fallbackTopBarHeightPx = fallbackTopBarHeightPx,
    )
    val topBarHeightDp = with(density) { effectiveTopBarHeightPx.toDp() }
    val topBarLogicVisible = hasTopHeader && focusCoordinator.isTopBarVisible && !isVideoFocusRestorePending
    val topBarRenderVisible = if (usesCollapsingHomeHeader) {
        !isVideoFocusRestorePending
    } else {
        topBarLogicVisible
    }
    val topBarFocusEnabled = hasTopHeader && focusCoordinator.isTopBarFocusable && !isVideoFocusRestorePending
    val collapseHomeHeader = shouldCollapseHomeHeader(
        usesCollapsingHomeHeader = usesCollapsingHomeHeader,
        isContentFocused = focusCoordinator.isContentFocused,
    )
    val topBarCollapseOffsetPx = if (collapseHomeHeader) {
        selectedCollapsingHeaderState.collapseOffsetPx.coerceAtLeast(0)
    } else {
        0
    }
    val shouldComposeTopBar = hasTopHeader && (usesCollapsingHomeHeader || topBarLogicVisible)
    val contentTopPadding = if (!usesCollapsingHomeHeader && shouldComposeTopBar) {
        topBarHeightDp
    } else {
        0.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackgroundColor)
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = sidebarWidth, top = contentTopPadding)
                .focusGroup()
                .onFocusChanged { state ->
                    if (state.hasFocus) {
                        focusCoordinator.onContentFocused()
                    }
                }
        ) {
            HomeContentDispatcher(
                selectedHomeTab = selectedHomeTab,
                visibleHomeTabs = homeTabs,
                recommendVideoItems = recommendVideoItems,
                viewModel = viewModel,
                dynamicRefreshRequestId = dynamicRefreshRequest.intValue,
                tabGridFocusStates = tabGridFocusStates,
                recommendGridFocusState = recommendGridFocusState,
                focusCoordinator = focusCoordinator,
                recyclerPools = recyclerPools,
                tabResidencyState = tabResidencyState,
                // Keep-alive RecyclerView tabs must retain their TopBar padding while
                // Search/Profile is selected, otherwise they can draw under the tabs
                // for a frame when switching back.
                topBarHeightPx = if (hasTopHeader) effectiveTopBarHeightPx else 0,
                collapseHeaderEnabled = collapseHomeHeader,
                collapsingHeaderStates = collapsingHeaderStates,
                onRequestTopBarFocus = ::requestTopBarFromContent,
                onTabSelected = { targetTab ->
                    selectHomeTab(targetTab, HomeFocusScene.TabSwitch)
                },
                onVideoClick = onVideoClick,
                onLiveClick = onLiveClick,
                onRecommendVideoClick = onRecommendVideoClick,
                onSearchVideoClick = onSearchVideoClick,
                onDynamicVideoClick = onDynamicVideoClick,
                onOpenSettings = onOpenSettings,
                onProfileVideoClick = onProfileVideoClick,
                onOpenUp = onOpenUp,
                restoreRecommendInitialScrollIndex = restoreRecommendInitialScrollIndex,
            )
        }

        if (shouldComposeTopBar) {
            HomeTopBarHost(
                tabs = if (sidebarTheme) HomeSidebarPolicy.columns(homeTabs, selectedHomeTab) else homeTabs,
                categories = if (sidebarTheme && HomeSidebarPolicy.isVideo(selectedHomeTab)) defaultPopularCategories.map {
                    com.bbttvv.app.ui.components.AppTopBarCategory(it.label, it.rid?.toLong() ?: 0L)
                } else emptyList(),
                selectedCategoryIndex = popularHeaderState?.selectedCategoryIndex ?: 0,
                onCategorySelected = { index ->
                    // Resolve the current store on entry, rather than retaining a ViewModel
                    // after the home tab cache has evicted its store.
                    val target = popularHeaderViewModel ?: androidx.lifecycle.ViewModelProvider(
                        viewModel.tabViewModelStore(AppTopLevelTab.POPULAR),
                        androidx.lifecycle.ViewModelProvider.NewInstanceFactory(),
                    )[PopularViewModel::class.java]
                    target.selectCategory(index)
                    if (selectedHomeTab != AppTopLevelTab.POPULAR) {
                        selectHomeTab(AppTopLevelTab.POPULAR, HomeFocusScene.TabSwitch, keepTopBarFocus = true)
                    }
                },
                selectedHomeTab = selectedHomeTab,
                updateSelectedTabOnFocus = selectTopTabOnFocusEnabled,
                focusCoordinator = focusCoordinator,
                onTabSelected = { targetTab ->
                    selectHomeTab(
                        targetTab = targetTab,
                        scene = HomeFocusScene.TabSwitch,
                        keepTopBarFocus = true,
                    )
                },
                onSelectedTabConfirmed = ::refreshSelectedTopBarTab,
                onDpadDown = {
                    focusCoordinator.handleTopBarDpadDown()
                },
                onDpadLeft = { sidebarTheme && focusCoordinator.requestSidebarFocus() },
                focusEnabled = topBarFocusEnabled,
                onTopBarFocused = {
                    collapsingHeaderStates.stateFor(selectedHomeTab).reset()
                    if (focusCoordinator.consumeBackToTopBarResetIntent()) {
                        resetHomeTabGridToTopForTopBarReturn(
                            tab = selectedHomeTab,
                            recommendGridFocusState = recommendGridFocusState,
                            tabGridFocusStates = tabGridFocusStates,
                        )
                    }
                    focusCoordinator.onTopBarFocused()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = sidebarWidth)
                    .zIndex(2f)
                    .onSizeChanged { size ->
                        topBarHeightState.intValue = size.height
                    }
                    .graphicsLayer {
                        translationY = if (!topBarRenderVisible) {
                            -effectiveTopBarHeightPx.toFloat()
                        } else if (usesCollapsingHomeHeader) {
                            -topBarCollapseOffsetPx.toFloat()
                        } else if (topBarLogicVisible) {
                            0f
                        } else {
                            -effectiveTopBarHeightPx.toFloat()
                        }
                        alpha = if (!topBarRenderVisible) {
                            0f
                        } else if (usesCollapsingHomeHeader) {
                            if (sidebarTheme) {
                                1f
                            } else if (effectiveTopBarHeightPx > 0) {
                                val collapseProgress = topBarCollapseOffsetPx.toFloat() / effectiveTopBarHeightPx.toFloat()
                                (1f - collapseProgress).coerceIn(0f, 1f)
                            } else {
                                1f
                            }
                        } else {
                            if (topBarLogicVisible) 1f else 0f
                        }
                    }
            )
        }
        if (sidebarTheme) {
            Column(Modifier.align(Alignment.TopStart).width(sidebarWidth).padding(start = 12.dp, top = 24.dp)) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.bbttvv.app.R.drawable.bilibili_official_logo),
                    contentDescription = "哔哩哔哩",
                    modifier = Modifier.padding(start = 12.dp).width(112.dp).height(30.dp),
                )
                Spacer(Modifier.height(24.dp))
                AppTopBar(
                    tabs = HomeSidebarPolicy.sidebarTabs(homeTabs),
                    selectedTab = HomeSidebarPolicy.selectedModule(selectedHomeTab),
                    updateSelectedTabOnFocus = selectTopTabOnFocusEnabled,
                    onTabSelected = { module ->
                        val tab = if (module == AppTopLevelTab.RECOMMEND) {
                            HomeSidebarPolicy.videoEntry(homeTabs, lastVideoTab)
                        } else module
                        selectHomeTab(tab, HomeFocusScene.TabSwitch)
                    },
                    onSelectedTabConfirmed = {
                        if (HomeSidebarPolicy.isVideo(selectedHomeTab)) focusCoordinator.requestTopBarFocus()
                        else focusCoordinator.requestSelectedContentFocus()
                    },
                    onDpadDown = { false },
                    onDpadRight = {
                        if (HomeSidebarPolicy.isVideo(selectedHomeTab)) focusCoordinator.requestTopBarFocus()
                        else focusCoordinator.requestSelectedContentFocus()
                        true
                    },
                    isSidebar = true,
                    registerAsTopBarFallback = !hasTopHeader,
                    focusEnabled = !isVideoFocusRestorePending,
                    focusCoordinator = focusCoordinator,
                    onTopBarFocused = { focusCoordinator.onTopBarFocused() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun RecommendSkeletonMilestoneEffect(
    selectedHomeTab: AppTopLevelTab,
    recommendIsEmpty: Boolean,
    viewModel: HomeViewModel,
) {
    val loadState by viewModel.recommendLoadState.collectAsStateWithLifecycle()
    LaunchedEffect(selectedHomeTab, loadState.isLoading, recommendIsEmpty) {
        if (selectedHomeTab != AppTopLevelTab.RECOMMEND) return@LaunchedEffect
        if (!loadState.isLoading || !recommendIsEmpty) return@LaunchedEffect
        withFrameNanos { }
        AppPerformanceTracker.markMilestoneOnce("home_skeleton_visible")
    }
}

private fun resetHomeTabGridToTopForTopBarReturn(
    tab: AppTopLevelTab,
    recommendGridFocusState: HomeRecommendGridFocusState,
    tabGridFocusStates: HomeTabGridFocusStates,
) {
    when (tab) {
        AppTopLevelTab.RECOMMEND -> {
            recommendGridFocusState.resetRememberedFocusToTopForTopBarReturn()
        }
        AppTopLevelTab.POPULAR,
        AppTopLevelTab.LIVE,
        AppTopLevelTab.TODAY_WATCH,
        AppTopLevelTab.DYNAMIC -> {
            tabGridFocusStates.stateFor(tab).resetRememberedFocusToTopForTopBarReturn()
        }
        else -> Unit
    }
}

private val EstimatedTabPillHeight = 36.dp

private val EstimatedHomeTopBarHeight = AppTopBarDefaults.TopPadding +
    AppTopBarDefaults.BottomPadding +
    AppTopBarDefaults.ContainerVerticalPadding * 2 +
    EstimatedTabPillHeight

internal fun effectiveTopBarHeightPx(
    measuredTopBarHeightPx: Int,
    fallbackTopBarHeightPx: Int,
): Int {
    return measuredTopBarHeightPx.takeIf { it > 0 }
        ?: fallbackTopBarHeightPx.coerceAtLeast(0)
}

internal fun shouldCollapseHomeHeader(
    usesCollapsingHomeHeader: Boolean,
    isContentFocused: Boolean,
): Boolean {
    return usesCollapsingHomeHeader && isContentFocused
}

private fun resetCollapsingTabForSwitch(
    tab: AppTopLevelTab,
    collapsingHeaderStates: HomeTabCollapsingHeaderStates,
) {
    collapsingHeaderStates.stateFor(tab).reset()
}

@Composable
private fun HomeTopBarHost(
    tabs: List<AppTopLevelTab>,
    categories: List<com.bbttvv.app.ui.components.AppTopBarCategory> = emptyList(),
    selectedCategoryIndex: Int = 0,
    onCategorySelected: (Int) -> Unit = {},
    selectedHomeTab: AppTopLevelTab,
    updateSelectedTabOnFocus: Boolean,
    focusCoordinator: HomeFocusCoordinator,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onSelectedTabConfirmed: (AppTopLevelTab) -> Unit,
    onDpadDown: () -> Boolean,
    onDpadLeft: () -> Boolean = { false },
    focusEnabled: Boolean,
    onTopBarFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTopBar(
        tabs = tabs,
        categories = categories,
        selectedCategoryIndex = selectedCategoryIndex,
        onCategorySelected = onCategorySelected,
        selectedTab = selectedHomeTab,
        onTabSelected = onTabSelected,
        onSelectedTabConfirmed = onSelectedTabConfirmed,
        updateSelectedTabOnFocus = updateSelectedTabOnFocus,
        onDpadDown = onDpadDown,
        onDpadLeft = onDpadLeft,
        focusEnabled = focusEnabled,
        focusCoordinator = focusCoordinator,
        onTopBarFocused = onTopBarFocused,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                top = AppTopBarDefaults.TopPadding,
                bottom = AppTopBarDefaults.BottomPadding
            )
    )
}

private fun AppTopLevelTab.usesCollapsingHomeHeader(): Boolean {
    return this == AppTopLevelTab.RECOMMEND ||
        this == AppTopLevelTab.POPULAR ||
        this == AppTopLevelTab.LIVE ||
        this == AppTopLevelTab.DYNAMIC ||
        this == AppTopLevelTab.TODAY_WATCH
}

@Composable
private fun RegisterHomeFocusEscapeGuard(focusCoordinator: HomeFocusCoordinator) {
    val hostView = LocalView.current
    RegisterTvFocusEscapeTarget(
        key = "home",
        acceptsFocus = { focusedView ->
            focusedView.isSameOrDescendantOf(hostView)
        },
        shouldRecoverEscapedFocus = { focusedView ->
            focusedView.rootView === hostView.rootView &&
                !focusedView.isSameOrDescendantOf(hostView)
        },
        recoverFocus = {
            focusCoordinator.recoverFocusAfterEscape()
        },
    )
}
