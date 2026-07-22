package com.bbttvv.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab

@Composable
internal fun PopularScreen(
    onVideoClick: (VideoItem) -> Unit,
    onContentRowFocused: (Int) -> Unit = {},
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    gridColumnCount: Int = 4,
    focusState: HomeRecommendGridFocusState = remember { HomeRecommendGridFocusState() },
    topBarHeightPx: Int = 0,
    collapseHeaderEnabled: Boolean = true,
    collapsingHeaderState: HomeCollapsingHeaderState = rememberHomeCollapsingHeaderState()
) {
    val viewModel: PopularViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isHomeTabActive = LocalHomeTabActive.current

    LaunchedEffect(isHomeTabActive) {
        if (isHomeTabActive) {
            viewModel.onEnter()
        }
    }

    HomeCategoryVideoGrid(
        tab = AppTopLevelTab.POPULAR,
        categories = uiState.categories.map { it.label },
        selectedCategoryIndex = uiState.selectedCategoryIndex,
        onCategorySelected = viewModel::selectCategory,
        videos = uiState.videos,
        isLoading = uiState.isLoading,
        isError = uiState.isError,
        errorMessage = uiState.errorMsg,
        loadingMessage = "正在加载热门内容...",
        hasMore = uiState.hasMore,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onVideoFocused = { video, _ -> viewModel.prefetchVideoDetail(video) },
        onVideoClick = { video, _ ->
            viewModel.primeVideoDetail(video)
            onVideoClick(video)
        },
        onContentRowFocused = onContentRowFocused,
        focusCoordinator = focusCoordinator,
        focusState = focusState,
        topBarHeightPx = topBarHeightPx,
        collapseHeaderEnabled = collapseHeaderEnabled,
        collapsingHeaderState = collapsingHeaderState,
        gridColumnCount = gridColumnCount,
        videoCardRecycledViewPool = videoCardRecycledViewPool,
    )
}
