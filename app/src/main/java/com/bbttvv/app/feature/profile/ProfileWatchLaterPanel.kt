package com.bbttvv.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvConfirmDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.VideoCardRecyclerGrid

@Composable
internal fun ProfileWatchLaterPanel(
    items: List<VideoItem>,
    totalCount: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenVideo: (VideoItem) -> Unit,
    onRemoveVideo: (VideoItem) -> Unit,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    focusRegion: HomeFocusRegion = HomeFocusRegion.Grid,
    showHeader: Boolean = true,
    gridColumnCount: Int = PROFILE_VIDEO_GRID_COLUMNS,
    contentPadding: PaddingValues = PaddingValues(top = 4.dp, bottom = 24.dp, end = 8.dp),
    resetGridToTop: Boolean = false,
    onBackToTopBar: (() -> Boolean)? = null,
    onRequestSidebarFocus: () -> Boolean
) {
    var pendingRemoveVideo by remember { mutableStateOf<VideoItem?>(null) }
    var suppressRemoveDialogConfirmKey by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        val pending = pendingRemoveVideo ?: return@LaunchedEffect
        val pendingKey = pending.bvid.ifBlank { pending.aid.toString() }
        if (items.none { item -> item.bvid.ifBlank { item.aid.toString() } == pendingKey }) {
            pendingRemoveVideo = null
        }
    }

    val watchLaterScrollResetKey = remember(items) {
        items.firstOrNull()?.let { item -> item.bvid.ifBlank { item.aid.toString() } }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showHeader) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "稍后再看", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                if (totalCount > 0) {
                    Text(text = "共 $totalCount 个", color = Color(0xB3FFFFFF), fontSize = 13.sp)
                }
            }
        }
        ProfileVideoGrid(
            items = items,
            isLoading = isLoading,
            isLoadingMore = false,
            hasMore = false,
            errorMessage = errorMessage,
            emptyText = "稍后再看里还没有视频",
            onOpenVideo = onOpenVideo,
            onLoadMore = {},
            onVideoLongClick = { video ->
                suppressRemoveDialogConfirmKey = true
                pendingRemoveVideo = video
            },
            scrollResetKey = watchLaterScrollResetKey,
            focusCoordinator = focusCoordinator,
            focusTab = focusTab,
            focusRegion = focusRegion,
            gridColumnCount = gridColumnCount,
            contentPadding = contentPadding,
            resetToTop = resetGridToTop,
            onBackToTopBar = onBackToTopBar,
            onRequestSidebarFocus = onRequestSidebarFocus,
            modifier = Modifier.weight(1f)
        )
    }

    pendingRemoveVideo?.let { video ->
        TvConfirmDialog(
            title = "移除稍后再看",
            message = video.title
                .takeIf { it.isNotBlank() }
                ?.let { title -> "确认从稍后再看移除「$title」吗？" }
                ?: "确认从稍后再看移除这个视频吗？",
            onDismissRequest = { pendingRemoveVideo = null },
            suppressConfirmKey = suppressRemoveDialogConfirmKey,
            onSuppressConfirmKeyConsumed = { suppressRemoveDialogConfirmKey = false },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = { pendingRemoveVideo = null }
                )
                TvDialogActionButton(
                    text = "移除",
                    contentColor = Color(0xFFFFD0D8),
                    onClick = {
                        onRemoveVideo(video)
                        pendingRemoveVideo = null
                    }
                )
            }
        )
    }
}

@Composable
internal fun ProfileVideoGrid(
    items: List<VideoItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    emptyText: String,
    onOpenVideo: (VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onVideoLongClick: ((VideoItem) -> Unit)? = null,
    showHistoryProgressOnly: Boolean = false,
    modifier: Modifier = Modifier,
    scrollResetKey: Any? = null,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    focusRegion: HomeFocusRegion = HomeFocusRegion.Grid,
    gridColumnCount: Int = PROFILE_VIDEO_GRID_COLUMNS,
    contentPadding: PaddingValues = PaddingValues(top = 4.dp, bottom = 24.dp, end = 8.dp),
    resetToTop: Boolean = false,
    onBackToTopBar: (() -> Boolean)? = null,
    onRequestSidebarFocus: () -> Boolean = { false }
) {
    val gridFocusState = remember { com.bbttvv.app.ui.home.HomeRecommendGridFocusState() }
    LaunchedEffect(resetToTop, scrollResetKey) {
        if (resetToTop && scrollResetKey != null) {
            gridFocusState.resetRememberedFocusToTop()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Text("正在加载内容...", color = Color(0xD9FFFFFF))
            }

            !errorMessage.isNullOrBlank() && items.isEmpty() -> {
                Text(errorMessage, color = Color(0xFFFFC4CF))
            }

            items.isEmpty() -> {
                Text(emptyText, color = Color(0xD9FFFFFF))
            }

            else -> {
                VideoCardRecyclerGrid(
                    videos = items,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    gridColumnCount = gridColumnCount,
                    focusState = gridFocusState,
                    scrollResetKey = scrollResetKey,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    focusRegion = focusRegion,
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    showDanmakuCount = false,
                    loadMorePrefetchItems = gridColumnCount + 1,
                    canLoadMore = { hasMore && !isLoading && !isLoadingMore },
                    onLoadMore = onLoadMore,
                    onTopRowDpadUp = onRequestSidebarFocus,
                    consumeTopRowDpadUp = false,
                    onBackToTopBar = onBackToTopBar,
                    onLeftEdgeDpadLeft = onRequestSidebarFocus,
                    onVideoLongClick = onVideoLongClick,
                    onVideoClick = { video, _ ->
                        val bvid = video.bvid.trim()
                        if (bvid.isNotEmpty()) {
                            onOpenVideo(video)
                        }
                    }
                )

                if (isLoadingMore) {
                    Text("正在加载更多...", color = Color(0xD9FFFFFF))
                } else if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, color = Color(0xFFFFC4CF))
                }
            }
        }
    }
}
