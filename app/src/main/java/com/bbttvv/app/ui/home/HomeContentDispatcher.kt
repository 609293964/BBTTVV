package com.bbttvv.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvContextMenu
import com.bbttvv.app.ui.components.TvContextMenuAction

@Composable
fun TabViewModelScope(
    tabState: AppTopLevelTab,
    homeViewModel: com.bbttvv.app.ui.home.HomeViewModel,
    content: @Composable () -> Unit
) {
    val store = remember(tabState, homeViewModel) {
        homeViewModel.tabViewModelStore(tabState)
    }

    // Clean up memory from OTHER tabs to ensure recycling memory.
    LaunchedEffect(tabState, homeViewModel) {
        homeViewModel.trimTabViewModelStores(tabState)
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
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    dynamicRefreshRequestId: Int,
    tabGridFocusStates: HomeTabGridFocusStates,
    recommendGridFocusState: HomeRecommendGridFocusState,
    focusCoordinator: HomeFocusCoordinator,
    topBarHeightPx: Int,
    collapsingHeaderState: HomeCollapsingHeaderState,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onLiveClick: (Long, String?) -> Unit,
    onRecommendVideoClick: (String, VideoItem) -> Unit,
    onOpenSettings: () -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, VideoItem) -> Unit,
    onOpenUp: (Long) -> Unit = {},
    restoreRecommendInitialScrollIndex: Int = -1,
) {
    var pendingRecommendMenuRequest by remember { mutableStateOf<RecommendContextMenuRequest?>(null) }
    var pendingRecommendFocusReturnKey by remember { mutableStateOf<String?>(null) }
    var suppressRecommendMenuConfirmKeyUp by remember { mutableStateOf(false) }

    LaunchedEffect(selectedHomeTab) {
        if (selectedHomeTab != AppTopLevelTab.RECOMMEND) {
            pendingRecommendMenuRequest = null
            pendingRecommendFocusReturnKey = null
            suppressRecommendMenuConfirmKeyUp = false
        }
    }

    LaunchedEffect(pendingRecommendFocusReturnKey, selectedHomeTab, uiState.videos) {
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
        when (selectedHomeTab) {
            AppTopLevelTab.SEARCH -> SearchTabContent(
                selectedHomeTab = selectedHomeTab,
                viewModel = viewModel,
                focusCoordinator = focusCoordinator,
                onRequestTopBarFocus = onRequestTopBarFocus,
                onTabSelected = onTabSelected,
                onVideoClick = onVideoClick,
                onOpenUp = onOpenUp
            )

            AppTopLevelTab.RECOMMEND -> RecommendTabContent(
                uiState = uiState,
                viewModel = viewModel,
                recommendGridFocusState = recommendGridFocusState,
                focusCoordinator = focusCoordinator,
                topBarHeightPx = topBarHeightPx,
                collapsingHeaderState = collapsingHeaderState,
                restoreInitialScrollIndex = restoreRecommendInitialScrollIndex,
                onRequestTopBarFocus = onRequestTopBarFocus,
                onRecommendVideoClick = onRecommendVideoClick,
                onOpenRecommendMenu = { video, focusKey ->
                    suppressRecommendMenuConfirmKeyUp = true
                    pendingRecommendMenuRequest = RecommendContextMenuRequest(
                        video = video,
                        focusKey = focusKey,
                    )
                }
            )

            AppTopLevelTab.TODAY_WATCH -> {
                TodayWatchScreen(
                    plan = uiState.todayWatchPlan,
                    config = uiState.todayWatchConfig,
                    selectedMode = uiState.todayWatchMode,
                    isLoading = uiState.todayWatchLoading,
                    errorMessage = uiState.todayWatchErrorMsg,
                    onModeActivated = { mode ->
                        if (uiState.todayWatchMode == mode) {
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
                    onContentRowFocused = focusCoordinator::onContentRowFocused,
                    focusCoordinator = focusCoordinator,
                    topBarHeightPx = topBarHeightPx,
                    collapsingHeaderState = collapsingHeaderState
                )
            }

            AppTopLevelTab.POPULAR -> {
                PopularScreen(
                    onVideoClick = onVideoClick,
                    onContentRowFocused = focusCoordinator::onContentRowFocused,
                    focusCoordinator = focusCoordinator,
                    gridColumnCount = 4,
                    focusState = tabGridFocusStates.stateFor(AppTopLevelTab.POPULAR),
                    topBarHeightPx = topBarHeightPx,
                    collapsingHeaderState = collapsingHeaderState
                )
            }

            AppTopLevelTab.LIVE -> {
                LiveScreen(
                    onLiveClick = onLiveClick,
                    onContentRowFocused = focusCoordinator::onContentRowFocused,
                    focusCoordinator = focusCoordinator,
                    gridColumnCount = 4,
                    focusState = tabGridFocusStates.stateFor(AppTopLevelTab.LIVE),
                    topBarHeightPx = topBarHeightPx,
                    collapsingHeaderState = collapsingHeaderState
                )
            }

            AppTopLevelTab.DYNAMIC -> {
                DynamicTabContent(
                    selectedHomeTab = selectedHomeTab,
                    viewModel = viewModel,
                    onVideoClick = onVideoClick,
                    onLiveClick = { roomId -> onLiveClick(roomId, null) },
                    onOpenUp = onOpenUp,
                    dynamicRefreshRequestId = dynamicRefreshRequestId,
                    onContentRowFocused = focusCoordinator::onContentRowFocused,
                    focusCoordinator = focusCoordinator,
                    gridColumnCount = 4,
                    focusState = tabGridFocusStates.stateFor(AppTopLevelTab.DYNAMIC),
                    topBarHeightPx = topBarHeightPx,
                    collapsingHeaderState = collapsingHeaderState
                )
            }

            AppTopLevelTab.WATCH_LATER -> WatchLaterTabContent(
                selectedHomeTab = selectedHomeTab,
                viewModel = viewModel,
                focusCoordinator = focusCoordinator,
                onRequestTopBarFocus = onRequestTopBarFocus,
                onProfileVideoClick = onProfileVideoClick
            )

            AppTopLevelTab.PROFILE -> ProfileTabContent(
                selectedHomeTab = selectedHomeTab,
                viewModel = viewModel,
                focusCoordinator = focusCoordinator,
                onRequestTopBarFocus = onRequestTopBarFocus,
                onOpenSettings = onOpenSettings,
                onProfileVideoClick = onProfileVideoClick
            )
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

@Composable
private fun DynamicTabContent(
    selectedHomeTab: AppTopLevelTab,
    viewModel: HomeViewModel,
    onVideoClick: (VideoItem) -> Unit,
    onLiveClick: (Long) -> Unit,
    onOpenUp: (Long) -> Unit,
    dynamicRefreshRequestId: Int,
    onContentRowFocused: (Int) -> Unit,
    focusCoordinator: HomeFocusCoordinator,
    gridColumnCount: Int,
    focusState: HomeRecommendGridFocusState,
    topBarHeightPx: Int,
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
            gridColumnCount = gridColumnCount,
            focusState = focusState,
            topBarHeightPx = topBarHeightPx,
            collapsingHeaderState = collapsingHeaderState
        )
    }
}

@Composable
private fun SearchTabContent(
    selectedHomeTab: AppTopLevelTab,
    viewModel: HomeViewModel,
    focusCoordinator: HomeFocusCoordinator,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onOpenUp: (Long) -> Unit
) {
    TabViewModelScope(selectedHomeTab, viewModel) {
        com.bbttvv.app.feature.search.SearchScreen(
            onBack = { onTabSelected(AppTopLevelTab.RECOMMEND) },
            onRequestTopBarFocus = { onRequestTopBarFocus(HomeFocusScene.BackToTopBar) },
            onOpenVideo = onVideoClick,
            onOpenUp = onOpenUp,
            focusCoordinator = focusCoordinator,
            focusTab = selectedHomeTab
        )
    }
}

@Composable
private fun RecommendTabContent(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    recommendGridFocusState: HomeRecommendGridFocusState,
    focusCoordinator: HomeFocusCoordinator,
    topBarHeightPx: Int,
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
        localHeader = null,
    ) { topPadding, onScrollOffset ->
        if (uiState.videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    Text(text = "正在加载推荐内容...", color = MaterialTheme.colorScheme.onBackground)
                } else if (uiState.isError) {
                    Text(
                        text = "加载失败：${uiState.errorMsg ?: "未知错误"}",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(text = "暂无推荐内容", color = MaterialTheme.colorScheme.onBackground)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
            VideoCardRecyclerGrid(
                videos = uiState.videos,
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
                onVerticalScrollOffsetChanged = onScrollOffset,
                canLoadMore = { uiState.hasMore && !uiState.isLoading },
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
            uiState.refreshErrorMessage?.let { message ->
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(3_000L)
                    viewModel.clearRefreshErrorMessage()
                }
                Text(
                    text = "刷新失败：$message",
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
    }
    }
}

@Composable
private fun WatchLaterTabContent(
    selectedHomeTab: AppTopLevelTab,
    viewModel: HomeViewModel,
    focusCoordinator: HomeFocusCoordinator,
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
            focusTab = AppTopLevelTab.WATCH_LATER
        )
    }
}

@Composable
private fun ProfileTabContent(
    selectedHomeTab: AppTopLevelTab,
    viewModel: HomeViewModel,
    focusCoordinator: HomeFocusCoordinator,
    onRequestTopBarFocus: (HomeFocusScene) -> Boolean,
    onOpenSettings: () -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, VideoItem) -> Unit
) {
    TabViewModelScope(selectedHomeTab, viewModel) {
        com.bbttvv.app.feature.profile.ProfileScreen(
            onOpenSettings = onOpenSettings,
            onOpenVideo = { video ->
                onProfileVideoClick(AppTopLevelTab.PROFILE, video.bvid.ifBlank { video.aid.toString() }, video)
            },
            onRequestTopBarFocus = { onRequestTopBarFocus(HomeFocusScene.BackToTopBar) },
            focusCoordinator = focusCoordinator,
            focusTab = selectedHomeTab
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
