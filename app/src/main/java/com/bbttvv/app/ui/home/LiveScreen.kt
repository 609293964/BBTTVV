package com.bbttvv.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.bbttvv.app.data.model.response.LiveRoom
import com.bbttvv.app.data.model.response.Owner
import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.HomeSecondaryTabRow

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun LiveScreen(
    onLiveClick: (Long, String) -> Unit,
    onContentRowFocused: (Int) -> Unit = {},
    focusCoordinator: HomeFocusCoordinator,
    gridColumnCount: Int = 4,
    focusState: HomeRecommendGridFocusState = remember { HomeRecommendGridFocusState() }
) {
    val viewModel: LiveViewModel = viewModel()
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

    LaunchedEffect(Unit) {
        viewModel.onEnter()
    }

    DisposableEffect(focusCoordinator, requestCategoryTabFocus) {
        val registration = focusCoordinator.registerContentTarget(
            tab = AppTopLevelTab.LIVE,
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

    Column(modifier = Modifier.fillMaxSize()) {
        HomeSecondaryTabRow(
            tabs = uiState.categories.map { it.label },
            selectedIndex = uiState.selectedCategoryIndex,
            onTabSelected = viewModel::selectCategory,
            onSelectedTabConfirmed = viewModel::selectCategory,
            onTabFocused = { onContentRowFocused(0) },
            itemFocusRequesters = categoryFocusRequesters,
            onDpadUp = {
                focusCoordinator.handleContentTabsDpadUp(AppTopLevelTab.LIVE)
            },
            onDpadDown = {
                focusCoordinator.handleContentTabsDpadDown(AppTopLevelTab.LIVE)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
        )

        if (uiState.liveRooms.isEmpty() && uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "正在加载直播列表...", color = MaterialTheme.colorScheme.onBackground)
            }
        } else if (uiState.isError && uiState.liveRooms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载失败：${uiState.errorMsg ?: "未知错误"}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            val displayVideos = remember(uiState.liveRooms) {
                uiState.liveRooms.map { it.toVideoItem() }
            }
            VideoCardRecyclerGrid(
                videos = displayVideos,
                contentPadding = PaddingValues(
                    start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                    end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                    top = 8.dp,
                    bottom = AppTopBarDefaults.HomeVideoGridBottomPadding
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                gridColumnCount = gridColumnCount,
                focusState = focusState,
                focusCoordinator = focusCoordinator,
                focusTab = AppTopLevelTab.LIVE,
                scrollResetKey = uiState.selectedCategoryIndex,
                canLoadMore = { uiState.hasMore && !uiState.isLoading },
                onLoadMore = viewModel::loadMore,
                onMenuRefresh = viewModel::refresh,
                onFocusedRowChanged = onContentRowFocused,
                onTopRowDpadUp = {
                    focusCoordinator.handleGridTopEdge(AppTopLevelTab.LIVE)
                },
                onBackToTopBar = { focusCoordinator.handleContentWantsTopBar() },
                onVideoClick = { videoItem, focusKey -> onLiveClick(videoItem.id, focusKey) }
            )
        }
    }
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
