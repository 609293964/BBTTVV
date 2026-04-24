package com.bbttvv.app.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvContextMenu
import com.bbttvv.app.ui.components.TvContextMenuAction
import com.bbttvv.app.data.model.response.VideoItem

@Composable
fun TabViewModelScope(
    tabState: AppTopLevelTab,
    homeViewModel: com.bbttvv.app.ui.home.HomeViewModel,
    content: @Composable () -> Unit
) {
    val store = remember(tabState, homeViewModel) { 
        homeViewModel.tabViewModelStores.getOrPut(tabState) { ViewModelStore() }
    }
    
    // Clean up memory from OTHER tabs to ensure recycling memory.
    LaunchedEffect(tabState, homeViewModel) {
        homeViewModel.clearUnselectedTabStores(tabState)
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
    dynamicViewModel: DynamicViewModel,
    tabGridFocusStates: HomeTabGridFocusStates,
    recommendGridFocusState: HomeRecommendGridFocusState,
    focusCoordinator: HomeFocusCoordinator,
    onTabSelected: (AppTopLevelTab) -> Unit,
    onVideoClick: (String) -> Unit,
    onLiveClick: (Long, String?) -> Unit,
    onRecommendVideoClick: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onProfileVideoClick: (AppTopLevelTab, String, VideoItem) -> Unit,
    onOpenUp: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    var pendingRecommendMenuVideo by remember { mutableStateOf<VideoItem?>(null) }
    var suppressRecommendMenuConfirmKeyUp by remember { mutableStateOf(false) }

    LaunchedEffect(selectedHomeTab) {
        if (selectedHomeTab != AppTopLevelTab.RECOMMEND) {
            pendingRecommendMenuVideo = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedHomeTab) {
            AppTopLevelTab.SEARCH -> {
                TabViewModelScope(selectedHomeTab, viewModel) {
                    com.bbttvv.app.feature.search.SearchScreen(
                        onBack = { onTabSelected(AppTopLevelTab.RECOMMEND) },
                        onRequestTopBarFocus = { focusCoordinator.handleContentWantsTopBar() },
                        onOpenVideo = onVideoClick,
                        onOpenUp = onOpenUp
                    )
                }
            }

            AppTopLevelTab.RECOMMEND -> {
                if (uiState.videos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    VideoCardRecyclerGrid(
                        videos = uiState.videos,
                        modifier = Modifier.fillMaxSize(),
                        gridColumnCount = 4,
                        contentPadding = PaddingValues(
                            start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                            end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                            top = AppTopBarDefaults.HeaderContentTopPadding,
                            bottom = AppTopBarDefaults.HomeVideoGridBottomPadding
                        ),
                        focusState = recommendGridFocusState,
                        focusCoordinator = focusCoordinator,
                        focusTab = AppTopLevelTab.RECOMMEND,
                        canLoadMore = { uiState.videos.size > 4 && !uiState.isLoading },
                        onLoadMore = viewModel::loadMore,
                        onMenuRefresh = viewModel::refresh,
                        onVideoFocused = { video, _ ->
                            viewModel.prefetchVideoDetail(video)
                        },
                        onFocusedRowChanged = focusCoordinator::onContentRowFocused,
                        onTopRowDpadUp = { focusCoordinator.handleContentWantsTopBar() },
                        onBackToTopBar = {
                            focusCoordinator.handleContentWantsTopBar()
                        },
                        onVideoLongClick = { video ->
                            suppressRecommendMenuConfirmKeyUp = true
                            pendingRecommendMenuVideo = video
                        },
                        onVideoClick = { video, key ->
                            viewModel.primeVideoDetail(video)
                            onRecommendVideoClick(key, video.bvid)
                        }
                    )
                }
            }

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
                        onVideoClick(video.bvid)
                    },
                    onNotInterested = viewModel::markTodayWatchNotInterested,
                    onVideoFocused = viewModel::prefetchVideoDetail,
                    onContentRowFocused = focusCoordinator::onContentRowFocused,
                    focusCoordinator = focusCoordinator
                )
            }

            AppTopLevelTab.POPULAR -> {
                PopularScreen(
                    onVideoClick = onVideoClick,
                    onContentRowFocused = focusCoordinator::onContentRowFocused,
                    focusCoordinator = focusCoordinator,
                    gridColumnCount = 4,
                    focusState = tabGridFocusStates.stateFor(AppTopLevelTab.POPULAR)
                )
            }

            AppTopLevelTab.LIVE -> {
                LiveScreen(
                    onLiveClick = onLiveClick,
                    onContentRowFocused = focusCoordinator::onContentRowFocused,
                    focusCoordinator = focusCoordinator,
                    gridColumnCount = 4,
                    focusState = tabGridFocusStates.stateFor(AppTopLevelTab.LIVE)
                )
            }

            AppTopLevelTab.DYNAMIC -> {
                DynamicScreen(
                    onVideoClick = onVideoClick,
                    onLiveClick = { roomId -> onLiveClick(roomId, null) },
                    viewModel = dynamicViewModel,
                    onContentRowFocused = focusCoordinator::onContentRowFocused,
                    focusCoordinator = focusCoordinator,
                    gridColumnCount = 4,
                    focusState = tabGridFocusStates.stateFor(AppTopLevelTab.DYNAMIC)
                )
            }

            AppTopLevelTab.WATCH_LATER -> {
                TabViewModelScope(selectedHomeTab, viewModel) {
                    com.bbttvv.app.feature.profile.WatchLaterVideosScreen(
                        onOpenVideo = { focusKey, video ->
                            onProfileVideoClick(AppTopLevelTab.WATCH_LATER, focusKey, video)
                        },
                        onRequestTopBarFocus = { focusCoordinator.handleContentWantsTopBar() },
                        focusCoordinator = focusCoordinator,
                        focusTab = AppTopLevelTab.WATCH_LATER
                    )
                }
            }

            AppTopLevelTab.PROFILE -> {
                TabViewModelScope(selectedHomeTab, viewModel) {
                    com.bbttvv.app.feature.profile.ProfileScreen(
                        onOpenSettings = onOpenSettings,
                        onOpenVideo = { video ->
                            onProfileVideoClick(AppTopLevelTab.PROFILE, video.bvid.ifBlank { video.aid.toString() }, video)
                        },
                        onRequestTopBarFocus = { focusCoordinator.handleContentWantsTopBar() }
                    )
                }
            }
        }

        pendingRecommendMenuVideo?.let { video ->
            TvContextMenu(
                actions = listOf(
                    TvContextMenuAction("稍后再看") {
                        pendingRecommendMenuVideo = null
                        suppressRecommendMenuConfirmKeyUp = false
                        viewModel.markWatchLater(video) { result ->
                            val message = if (result.isSuccess) {
                                "已添加到稍后再看"
                            } else {
                                result.exceptionOrNull()?.message ?: "添加稍后再看失败"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    TvContextMenuAction("不感兴趣") {
                        pendingRecommendMenuVideo = null
                        suppressRecommendMenuConfirmKeyUp = false
                        viewModel.markRecommendNotInterested(video)
                        Toast.makeText(context, "已移除该推荐", Toast.LENGTH_SHORT).show()
                    },
                ),
                modifier = Modifier.align(Alignment.Center),
                suppressConfirmKey = suppressRecommendMenuConfirmKeyUp,
                onSuppressConfirmKeyConsumed = { suppressRecommendMenuConfirmKeyUp = false },
                onDismissRequest = {
                    pendingRecommendMenuVideo = null
                    suppressRecommendMenuConfirmKeyUp = false
                },
            )
        }
    }
}
