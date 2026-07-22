package com.bbttvv.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.RecyclerView
import com.bbttvv.app.data.model.response.LiveRoom
import com.bbttvv.app.data.model.response.Owner
import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab

@Composable
internal fun LiveScreen(
    onLiveClick: (Long, String) -> Unit,
    onContentRowFocused: (Int) -> Unit = {},
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    gridColumnCount: Int = 4,
    focusState: HomeRecommendGridFocusState = remember { HomeRecommendGridFocusState() },
    topBarHeightPx: Int = 0,
    collapseHeaderEnabled: Boolean = true,
    collapsingHeaderState: HomeCollapsingHeaderState = rememberHomeCollapsingHeaderState()
) {
    val viewModel: LiveViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isHomeTabActive = LocalHomeTabActive.current

    LaunchedEffect(isHomeTabActive) {
        if (isHomeTabActive) {
            viewModel.onEnter()
        }
    }
    val displayVideos = remember(uiState.liveRooms) {
        uiState.liveRooms.map { it.toVideoItem() }
    }

    HomeCategoryVideoGrid(
        tab = AppTopLevelTab.LIVE,
        categories = uiState.categories.map { it.label },
        selectedCategoryIndex = uiState.selectedCategoryIndex,
        onCategorySelected = viewModel::selectCategory,
        videos = displayVideos,
        isLoading = uiState.isLoading,
        isError = uiState.isError,
        errorMessage = uiState.errorMsg,
        loadingMessage = "正在加载直播列表...",
        hasMore = uiState.hasMore,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onVideoClick = { videoItem, focusKey -> onLiveClick(videoItem.id, focusKey) },
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

private fun LiveRoom.toVideoItem(): VideoItem {
    return VideoItem(
        id = roomid,
        bvid = roomid.toString(), // Encode roomid as bvid for unified string-based navigation if needed
        aid = 0L,
        cid = 0L,
        title = title,
        // Prefer TV-friendly 16:9 covers for live cards.
        pic = listOf(userCover, keyframe, cover, face).firstOrNull { it.isNotEmpty() } ?: "",
        owner = Owner(
            mid = uid,
            name = uname,
            face = face
        ),
        stat = Stat(
            view = online,     // Display online viewers as "views"
            danmaku = 0        // Not applicable here without explicit Danmaku fetch
        ),
        duration = 0,          // Live doesn't have standard duration
        pubdate = 0L           // Live is currently active
    )
}
