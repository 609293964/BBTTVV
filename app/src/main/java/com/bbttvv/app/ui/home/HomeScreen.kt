package com.bbttvv.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.bbttvv.app.core.performance.AppPerformanceTracker
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBar
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.stableVideoItemKeys

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
    onVideoClick: (VideoItem) -> Unit,
    onLiveClick: (Long, String?) -> Unit,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onRecommendVideoClick: (String, VideoItem) -> Unit = { _, video -> onVideoClick(video) },
    onOpenSettings: () -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, com.bbttvv.app.data.model.response.VideoItem) -> Unit = { _, _, _ -> },
    onOpenUp: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dynamicViewModel: DynamicViewModel = composeViewModel()
    val tabGridFocusStates = remember { HomeTabGridFocusStates() }
    val recommendGridFocusState = tabGridFocusStates.stateFor(AppTopLevelTab.RECOMMEND)
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
    val restoreTargetTab = restoreVideoFocusTab ?: AppTopLevelTab.RECOMMEND
    LaunchedEffect(visibleTabs) {
        tabGridFocusStates.retainVisibleTabs(visibleTabs.toSet())
        focusCoordinator.retainVisibleTabs(visibleTabs.toSet())
    }
    LaunchedEffect(selectedHomeTab) {
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
    ) {
        focusCoordinator.updateScene(scene)
        onTabSelected(targetTab)
    }

    fun refreshSelectedTopBarTab(tab: AppTopLevelTab) {
        when (tab) {
            AppTopLevelTab.RECOMMEND -> viewModel.refresh()
            AppTopLevelTab.DYNAMIC -> dynamicViewModel.refresh()
            else -> Unit
        }
    }

    BackHandler(enabled = !focusCoordinator.isContentFocused && selectedHomeTab != AppTopLevelTab.RECOMMEND) {
        selectHomeTab(AppTopLevelTab.RECOMMEND, HomeFocusScene.BackToRecommend)
        focusCoordinator.handleContentWantsTopBar(HomeFocusScene.BackToRecommend)
    }

    BackHandler(enabled = focusCoordinator.isContentFocused) {
        focusCoordinator.handleContentWantsTopBar(HomeFocusScene.BackToTopBar)
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

    Column(modifier = Modifier.fillMaxSize().then(backgroundModifier)) {
        AnimatedVisibility(
            visible = focusCoordinator.isTopBarVisible && !shouldResumeVideoContentFocus,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AppTopBar(
                tabs = homeTabs,
                selectedTab = selectedHomeTab,
                onTabSelected = { targetTab ->
                    selectHomeTab(targetTab, HomeFocusScene.TabSwitch)
                },
                onSelectedTabConfirmed = { tab ->
                    refreshSelectedTopBarTab(tab)
                },
                updateSelectedTabOnFocus = updateContentOnTabFocusEnabled,
                onDpadDown = {
                    focusCoordinator.handleTopBarDpadDown()
                },
                focusCoordinator = focusCoordinator,
                onTopBarFocusChanged = { isFocused ->
                    if (isFocused) {
                        focusCoordinator.onTopBarFocused()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        top = AppTopBarDefaults.TopPadding,
                        bottom = AppTopBarDefaults.BottomPadding
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .focusGroup()
                .onFocusChanged { state ->
                    if (state.hasFocus) {
                        focusCoordinator.onContentFocused()
                    }
                }
        ) {
            HomeContentDispatcher(
                selectedHomeTab = selectedHomeTab,
                uiState = uiState,
                viewModel = viewModel,
                dynamicViewModel = dynamicViewModel,
                tabGridFocusStates = tabGridFocusStates,
                recommendGridFocusState = recommendGridFocusState,
                focusCoordinator = focusCoordinator,
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
}
