package com.bbttvv.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.HomeSecondaryTabRow

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PopularScreen(
    onVideoClick: (VideoItem) -> Unit,
    onContentRowFocused: (Int) -> Unit = {},
    focusCoordinator: HomeFocusCoordinator,
    gridColumnCount: Int = 4,
    focusState: HomeRecommendGridFocusState = remember { HomeRecommendGridFocusState() },
    topBarHeightPx: Int = 0,
    collapsingHeaderState: HomeCollapsingHeaderState = rememberHomeCollapsingHeaderState()
) {
    val viewModel: PopularViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryFocusRequesters = remember(uiState.categories.size) {
        List(uiState.categories.size) { FocusRequester() }
    }
    val requestCategoryTabFocus = remember(
        uiState.selectedCategoryIndex,
        categoryFocusRequesters,
        onContentRowFocused,
    ) {
        {
            val requester = categoryFocusRequesters.getOrNull(uiState.selectedCategoryIndex)
                ?: return@remember false
            runCatching {
                val focused = requester.requestFocus()
                if (focused) {
                    onContentRowFocused(0)
                }
                focused
            }.getOrDefault(false)
        }
    }

    DisposableEffect(focusCoordinator, requestCategoryTabFocus) {
        val registration = focusCoordinator.registerContentTarget(
            tab = AppTopLevelTab.POPULAR,
            region = HomeFocusRegion.ContentTabs,
            target = object : HomeFocusTarget {
                override fun tryRequestFocus(): Boolean {
                    return requestCategoryTabFocus()
                }
            }
        )
        onDispose {
            registration.unregister()
        }
    }

    HomeCollapsingHeaderGrid(
        topBarHeightPx = topBarHeightPx,
        state = collapsingHeaderState,
        modifier = Modifier.fillMaxSize(),
        localHeader = {
            HomeSecondaryTabRow(
                tabs = uiState.categories.map { it.label },
                selectedIndex = uiState.selectedCategoryIndex,
                onTabSelected = viewModel::selectCategory,
                onSelectedTabConfirmed = viewModel::selectCategory,
                onTabFocused = {
                    collapsingHeaderState.reset()
                    onContentRowFocused(0)
                },
                itemFocusRequesters = categoryFocusRequesters,
                onDpadUp = {
                    collapsingHeaderState.reset()
                    focusCoordinator.handleContentTabsDpadUp(AppTopLevelTab.POPULAR)
                },
                onDpadDown = { index ->
                    focusCoordinator.handleContentTabsDpadDown(
                        tab = AppTopLevelTab.POPULAR,
                        preferredIndex = index,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            )
        }
    ) { topPadding, onScrollOffset ->
        if (uiState.videos.isEmpty() && uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "正在加载热门内容...", color = MaterialTheme.colorScheme.onBackground)
            }
        } else if (uiState.videos.isEmpty() && uiState.isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载失败：${uiState.errorMsg ?: "未知错误"}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            VideoCardRecyclerGrid(
                videos = uiState.videos,
                contentPadding = PaddingValues(
                    start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                    end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                    top = topPadding + 8.dp,
                    bottom = AppTopBarDefaults.HomeVideoGridBottomPadding
                ),
                modifier = Modifier.fillMaxSize(),
                gridColumnCount = gridColumnCount,
                focusState = focusState,
                focusCoordinator = focusCoordinator,
                focusTab = AppTopLevelTab.POPULAR,
                scrollResetKey = uiState.selectedCategoryIndex,
                allowChildDrawingOutsideBounds = false,
                onVerticalScrollOffsetChanged = onScrollOffset,
                canLoadMore = { uiState.hasMore && !uiState.isLoading },
                onLoadMore = viewModel::loadMore,
                onMenuRefresh = viewModel::refresh,
                onVideoFocused = { video, _ ->
                    viewModel.prefetchVideoDetail(video)
                },
                onFocusedRowChanged = onContentRowFocused,
                onTopRowDpadUp = {
                    collapsingHeaderState.reset()
                    focusState.resetRememberedFocusToTopForTopBarReturn()
                    focusCoordinator.handleGridTopEdge(AppTopLevelTab.POPULAR)
                },
                onBackToTopBar = {
                    collapsingHeaderState.reset()
                    focusState.resetRememberedFocusToTopForTopBarReturn()
                    focusCoordinator.handleContentWantsTopBar()
                },
                onVideoClick = { video, _ ->
                    viewModel.primeVideoDetail(video)
                    onVideoClick(video)
                }
            )
        }
    }
}
