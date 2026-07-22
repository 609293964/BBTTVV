package com.bbttvv.app.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.HomeSecondaryTabRow

@Composable
internal fun HomeCategoryVideoGrid(
    tab: AppTopLevelTab,
    categories: List<String>,
    selectedCategoryIndex: Int,
    onCategorySelected: (Int) -> Unit,
    videos: List<VideoItem>,
    isLoading: Boolean,
    isError: Boolean,
    errorMessage: String?,
    loadingMessage: String,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onVideoClick: (VideoItem, String) -> Unit,
    onContentRowFocused: (Int) -> Unit,
    focusCoordinator: HomeFocusCoordinator,
    focusState: HomeRecommendGridFocusState,
    topBarHeightPx: Int,
    collapseHeaderEnabled: Boolean,
    collapsingHeaderState: HomeCollapsingHeaderState,
    gridColumnCount: Int,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    onVideoFocused: (VideoItem, String) -> Unit = { _, _ -> },
) {
    val isHomeTabActive = LocalHomeTabActive.current
    val categoryFocusRequesters = remember(categories.size) {
        List(categories.size) { FocusRequester() }
    }
    val requestCategoryTabFocus = remember(
        selectedCategoryIndex,
        categoryFocusRequesters,
        onContentRowFocused,
        isHomeTabActive,
    ) {
        {
            if (!isHomeTabActive) return@remember false
            val requester = categoryFocusRequesters.getOrNull(selectedCategoryIndex)
                ?: return@remember false
            runCatching {
                val focused = requester.requestFocus()
                if (focused) onContentRowFocused(0)
                focused
            }.getOrDefault(false)
        }
    }

    DisposableEffect(focusCoordinator, requestCategoryTabFocus, isHomeTabActive, tab) {
        val registration = if (isHomeTabActive) {
            focusCoordinator.registerContentTarget(
                tab = tab,
                region = HomeFocusRegion.ContentTabs,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean = requestCategoryTabFocus()
                },
            )
        } else {
            null
        }
        onDispose { registration?.unregister() }
    }

    HomeCollapsingHeaderGrid(
        topBarHeightPx = topBarHeightPx,
        state = collapsingHeaderState,
        modifier = Modifier.fillMaxSize(),
        collapseEnabled = collapseHeaderEnabled,
        localHeader = {
            HomeSecondaryTabRow(
                tabs = categories,
                selectedIndex = selectedCategoryIndex,
                onTabSelected = onCategorySelected,
                onSelectedTabConfirmed = onCategorySelected,
                onTabFocused = {
                    if (isHomeTabActive) {
                        collapsingHeaderState.reset()
                        onContentRowFocused(0)
                    }
                },
                itemFocusRequesters = categoryFocusRequesters,
                onDpadUp = {
                    if (isHomeTabActive) {
                        collapsingHeaderState.reset()
                        focusCoordinator.handleContentTabsDpadUp(tab)
                    } else {
                        false
                    }
                },
                onDpadDown = { index ->
                    isHomeTabActive && focusCoordinator.handleContentTabsDpadDown(
                        tab = tab,
                        preferredIndex = index,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
            )
        },
    ) { topPadding, onScrollOffset ->
        when {
            videos.isEmpty() && isLoading -> HomeEmptyFocusTarget(
                tab = tab,
                focusCoordinator = focusCoordinator,
                isActive = isHomeTabActive,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
            ) {
                Text(
                    text = loadingMessage,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            videos.isEmpty() && isError -> HomeEmptyFocusTarget(
                tab = tab,
                focusCoordinator = focusCoordinator,
                isActive = isHomeTabActive,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
            ) {
                Text(
                    text = "加载失败：${errorMessage ?: "未知错误"}",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> VideoCardRecyclerGrid(
                videos = videos,
                contentPadding = PaddingValues(
                    start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                    end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                    top = topPadding + 8.dp,
                    bottom = AppTopBarDefaults.HomeVideoGridBottomPadding,
                ),
                modifier = Modifier.fillMaxSize(),
                gridColumnCount = gridColumnCount,
                focusState = focusState,
                focusCoordinator = focusCoordinator,
                focusTab = tab,
                scrollResetKey = selectedCategoryIndex,
                allowChildDrawingOutsideBounds = false,
                videoCardRecycledViewPool = videoCardRecycledViewPool,
                onVerticalScrollOffsetChanged = onScrollOffset,
                canLoadMore = { hasMore && !isLoading },
                loadMoreInProgress = isLoading,
                onLoadMore = onLoadMore,
                onMenuRefresh = onRefresh,
                onVideoFocused = onVideoFocused,
                onFocusedRowChanged = onContentRowFocused,
                onTopRowDpadUp = {
                    collapsingHeaderState.reset()
                    focusState.resetRememberedFocusToTopForTopBarReturn()
                    focusCoordinator.handleGridTopEdge(tab)
                },
                onBackToTopBar = {
                    collapsingHeaderState.reset()
                    focusState.resetRememberedFocusToTopForTopBarReturn()
                    focusCoordinator.handleContentWantsTopBar()
                },
                onVideoClick = onVideoClick,
            )
        }
    }
}
