package com.bbttvv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.bbttvv.app.core.performance.AppPerformanceTracker
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBar
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.stableVideoItemKeys
import com.bbttvv.app.ui.focus.RegisterTvFocusEscapeTarget
import com.bbttvv.app.ui.focus.isSameOrDescendantOf

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    visibleTabs: List<AppTopLevelTab>,
    selectedTabIndex: Int,
    updateContentOnTabFocusEnabled: Boolean,
    restoreVideoFocusKey: String? = null,
    restoreVideoFocusTab: AppTopLevelTab? = null,
    hasPendingVideoFocusRestore: Boolean = restoreVideoFocusKey != null,
    onVideoFocusRestored: (String) -> Unit = {},
    onCancelVideoFocusRestore: () -> Unit = {},
    onVideoClick: (VideoItem) -> Unit,
    onLiveClick: (Long, String?) -> Unit,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onRecommendVideoClick: (String, VideoItem) -> Unit = { _, video -> onVideoClick(video) },
    onOpenSettings: () -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, com.bbttvv.app.data.model.response.VideoItem) -> Unit = { _, _, _ -> },
    onOpenUp: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dynamicRefreshRequest = remember { mutableIntStateOf(0) }
    val tabGridFocusStates = remember { HomeTabGridFocusStates() }
    val recommendGridFocusState = tabGridFocusStates.stateFor(AppTopLevelTab.RECOMMEND)
    val collapsingHeaderState = rememberHomeCollapsingHeaderState()
    val topBarHeightState = remember { mutableIntStateOf(0) }
    val homeTabs = remember(visibleTabs) { visibleTabs.filter(AppTopLevelTab::isHomeContent) }
    val recommendVideos = uiState.videos
    val recommendVideoKeys = remember(recommendVideos) { recommendVideos.stableVideoItemKeys() }
    val recommendVideoCount = recommendVideos.size
    val recommendHasVideos = recommendVideoCount > 0
    val recommendIsEmpty = !recommendHasVideos
    val selectedHomeTab = AppTopLevelTab.resolveVisibleHomeTab(
        index = selectedTabIndex,
        visibleTabs = visibleTabs
    )
    val focusCoordinator = remember { HomeFocusCoordinator(selectedHomeTab) }
    RegisterHomeFocusEscapeGuard(focusCoordinator)
    val restoreTargetTab = restoreVideoFocusTab ?: AppTopLevelTab.RECOMMEND
    LaunchedEffect(visibleTabs) {
        tabGridFocusStates.retainVisibleTabs(visibleTabs.toSet())
        focusCoordinator.retainVisibleTabs(visibleTabs.toSet())
    }
    LaunchedEffect(selectedHomeTab) {
        resetCollapsingTabForSwitch(
            tab = selectedHomeTab,
            collapsingHeaderState = collapsingHeaderState,
            recommendGridFocusState = recommendGridFocusState,
            tabGridFocusStates = tabGridFocusStates,
        )
        focusCoordinator.updateSelectedHomeTab(selectedHomeTab)
        viewModel.onHomeTabVisible(selectedHomeTab)
    }
    val isVideoFocusRestorePending = selectedHomeTab == restoreTargetTab &&
        hasPendingVideoFocusRestore &&
        restoreVideoFocusKey != null
    val effectiveFocusScene = if (isVideoFocusRestorePending) {
        HomeFocusScene.BackReturn
    } else {
        focusCoordinator.scene
    }
    val restoreRecommendVideoIndex = restoreVideoFocusKey?.let { focusKey ->
        recommendVideoKeys.indexOf(focusKey).takeIf { it >= 0 }
    } ?: -1
    val shouldResumeVideoContentFocus = HomeFocusStrategy.shouldRestoreBackReturnVideoFocus(
        scene = effectiveFocusScene,
        selectedHomeTab = selectedHomeTab,
        restoreVideoFocusTab = restoreTargetTab,
        restoreVideoFocusKey = restoreVideoFocusKey,
        restoreVideoIndex = restoreRecommendVideoIndex,
    )
    LaunchedEffect(selectedHomeTab, uiState.isLoading, recommendIsEmpty) {
        if (selectedHomeTab != AppTopLevelTab.RECOMMEND) return@LaunchedEffect
        if (!uiState.isLoading || !recommendIsEmpty) return@LaunchedEffect
        withFrameNanos { }
        AppPerformanceTracker.markMilestoneOnce("home_skeleton_visible")
    }

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
        resetCollapsingTabForSwitch(
            tab = targetTab,
            collapsingHeaderState = collapsingHeaderState,
            recommendGridFocusState = recommendGridFocusState,
            tabGridFocusStates = tabGridFocusStates,
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
        collapsingHeaderState.reset()
        return focusCoordinator.handleContentWantsTopBar(scene)
    }

    BackHandler(enabled = !focusCoordinator.isContentFocused && selectedHomeTab != AppTopLevelTab.RECOMMEND) {
        selectHomeTab(AppTopLevelTab.RECOMMEND, HomeFocusScene.BackToRecommend)
        requestTopBarFromContent(HomeFocusScene.BackToRecommend)
    }

    BackHandler(enabled = focusCoordinator.isContentFocused) {
        requestTopBarFromContent(HomeFocusScene.BackToTopBar)
    }

    val backgroundModifier = remember(selectedHomeTab) {
        if (selectedHomeTab == AppTopLevelTab.PROFILE) {
            Modifier.background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(
                        androidx.compose.ui.graphics.Color(0xFF18222A),
                        androidx.compose.ui.graphics.Color(0xFF161A16),
                        androidx.compose.ui.graphics.Color(0xFF090A0B)
                    )
                )
            )
        } else {
            Modifier
        }
    }
    val usesCollapsingHomeHeader = selectedHomeTab.usesCollapsingHomeHeader()
    val topBarHeightDp = with(LocalDensity.current) { topBarHeightState.intValue.toDp() }
    val shouldComposeTopBar = !shouldResumeVideoContentFocus &&
        (usesCollapsingHomeHeader || focusCoordinator.isTopBarVisible)
    val contentTopPadding = if (!usesCollapsingHomeHeader && shouldComposeTopBar) {
        topBarHeightDp
    } else {
        0.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentTopPadding)
                .focusGroup()
                .onFocusChanged { state ->
                    if (state.hasFocus) {
                        focusCoordinator.onContentFocused()
                    }
                }
        ) {
            key(selectedHomeTab) {
                HomeContentDispatcher(
                    selectedHomeTab = selectedHomeTab,
                    uiState = uiState,
                    viewModel = viewModel,
                    dynamicRefreshRequestId = dynamicRefreshRequest.intValue,
                    tabGridFocusStates = tabGridFocusStates,
                    recommendGridFocusState = recommendGridFocusState,
                    focusCoordinator = focusCoordinator,
                    topBarHeightPx = if (usesCollapsingHomeHeader && !shouldResumeVideoContentFocus) {
                        topBarHeightState.intValue
                    } else {
                        0
                    },
                    collapsingHeaderState = collapsingHeaderState,
                    onRequestTopBarFocus = ::requestTopBarFromContent,
                    onTabSelected = { targetTab ->
                        selectHomeTab(targetTab, HomeFocusScene.TabSwitch)
                    },
                    onVideoClick = onVideoClick,
                    onLiveClick = onLiveClick,
                    onRecommendVideoClick = onRecommendVideoClick,
                    onOpenSettings = onOpenSettings,
                    onProfileVideoClick = onProfileVideoClick,
                    onOpenUp = onOpenUp
                )
            }
        }

        if (shouldComposeTopBar) {
            HomeTopBarHost(
                tabs = homeTabs,
                selectedHomeTab = selectedHomeTab,
                updateSelectedTabOnFocus = updateContentOnTabFocusEnabled,
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
                onTopBarFocusChanged = { isFocused ->
                    if (isFocused) {
                        collapsingHeaderState.reset()
                        focusCoordinator.onTopBarFocused()
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
                    .onSizeChanged { size ->
                        topBarHeightState.intValue = size.height
                    }
                    .graphicsLayer {
                        translationY = if (usesCollapsingHomeHeader) {
                            -collapsingHeaderState.collapseOffsetPx.toFloat()
                        } else if (focusCoordinator.isTopBarVisible) {
                            0f
                        } else {
                            -topBarHeightState.intValue.toFloat()
                        }
                    }
            )
        }
    }
}

private fun resetCollapsingTabForSwitch(
    tab: AppTopLevelTab,
    collapsingHeaderState: HomeCollapsingHeaderState,
    recommendGridFocusState: HomeRecommendGridFocusState,
    tabGridFocusStates: HomeTabGridFocusStates,
) {
    collapsingHeaderState.reset()
    when (tab) {
        AppTopLevelTab.RECOMMEND -> {
            recommendGridFocusState.resetRememberedFocusToTopForTopBarReturn()
        }
        AppTopLevelTab.POPULAR,
        AppTopLevelTab.LIVE,
        AppTopLevelTab.DYNAMIC -> {
            tabGridFocusStates.stateFor(tab).resetRememberedFocusToTopForTopBarReturn()
        }
        else -> Unit
    }
}

@Composable
private fun HomeTopBarHost(
    tabs: List<AppTopLevelTab>,
    selectedHomeTab: AppTopLevelTab,
    updateSelectedTabOnFocus: Boolean,
    focusCoordinator: HomeFocusCoordinator,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onSelectedTabConfirmed: (AppTopLevelTab) -> Unit,
    onDpadDown: () -> Boolean,
    onTopBarFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppTopBar(
        tabs = tabs,
        selectedTab = selectedHomeTab,
        onTabSelected = onTabSelected,
        onSelectedTabConfirmed = onSelectedTabConfirmed,
        updateSelectedTabOnFocus = updateSelectedTabOnFocus,
        onDpadDown = onDpadDown,
        focusCoordinator = focusCoordinator,
        onTopBarFocusChanged = onTopBarFocusChanged,
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
