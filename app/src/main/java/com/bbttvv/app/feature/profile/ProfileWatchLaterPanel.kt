package com.bbttvv.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.recyclerview.widget.RecyclerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvConfirmDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.home.HomeEmptyFocusTarget
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.LocalHomeTabActive
import com.bbttvv.app.ui.home.VideoCardRecyclerGrid

@Composable
internal fun ProfileWatchLaterPanel(
    items: List<VideoItem>,
    totalCount: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenVideo: (String, VideoItem) -> Unit,
    onRemoveVideo: (VideoItem) -> Unit,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    focusRegion: HomeFocusRegion = HomeFocusRegion.Grid,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    showHeader: Boolean = true,
    gridColumnCount: Int = PROFILE_VIDEO_GRID_COLUMNS,
    contentPadding: PaddingValues = PaddingValues(top = 4.dp, bottom = 24.dp),
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

    val contentFocusTarget = rememberProfileContentFocusTargetState(
        focusCoordinator = focusCoordinator,
        focusTab = focusTab,
    )
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .profileContentFocusTarget(
                state = contentFocusTarget,
                focusCoordinator = focusCoordinator,
                focusTab = focusTab,
                onDpadLeft = onRequestSidebarFocus,
            )
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showHeader) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "稍后再看", color = if (isLightTheme) Color(0xFF18191C) else Color.White, style = MaterialTheme.typography.headlineMedium)
                if (totalCount > 0) {
                    Text(text = "共 $totalCount 个", color = if (isLightTheme) Color(0xFF61666D) else Color(0xB3FFFFFF), fontSize = 13.sp)
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
            videoCardRecycledViewPool = videoCardRecycledViewPool,
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
    onOpenVideo: (String, VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onVideoLongClick: ((VideoItem) -> Unit)? = null,
    showHistoryProgressOnly: Boolean = false,
    modifier: Modifier = Modifier,
    scrollResetKey: Any? = null,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    focusRegion: HomeFocusRegion = HomeFocusRegion.Grid,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    gridColumnCount: Int = PROFILE_VIDEO_GRID_COLUMNS,
    contentPadding: PaddingValues = PaddingValues(top = 12.dp, bottom = 24.dp),
    resetToTop: Boolean = false,
    onBackToTopBar: (() -> Boolean)? = null,
    onRequestSidebarFocus: () -> Boolean = { false }
) {
    val gridFocusState = remember { com.bbttvv.app.ui.home.HomeRecommendGridFocusState() }
    val isHomeTabActive = LocalHomeTabActive.current
    LaunchedEffect(resetToTop, scrollResetKey) {
        if (resetToTop && scrollResetKey != null) {
            gridFocusState.resetRememberedFocusToTop()
        }
    }

    Column(modifier = modifier.fillMaxSize().clipToBounds()) {
        when {
            isLoading && items.isEmpty() -> {
                ProfileVideoGridStatus(
                    text = "正在加载内容...",
                    color = Color(0xD9FFFFFF),
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    focusRegion = focusRegion,
                    isHomeTabActive = isHomeTabActive,
                    onDpadUp = onBackToTopBar ?: onRequestSidebarFocus,
                )
            }

            !errorMessage.isNullOrBlank() && items.isEmpty() -> {
                ProfileVideoGridStatus(
                    text = errorMessage,
                    color = Color(0xFFFFC4CF),
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    focusRegion = focusRegion,
                    isHomeTabActive = isHomeTabActive,
                    onDpadUp = onBackToTopBar ?: onRequestSidebarFocus,
                )
            }

            items.isEmpty() -> {
                ProfileVideoGridStatus(
                    text = emptyText,
                    color = Color(0xD9FFFFFF),
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    focusRegion = focusRegion,
                    isHomeTabActive = isHomeTabActive,
                    onDpadUp = onBackToTopBar ?: onRequestSidebarFocus,
                )
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
                    allowChildDrawingOutsideBounds = false,
                    videoCardRecycledViewPool = videoCardRecycledViewPool,
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    showDanmakuCount = false,
                    loadMorePrefetchItems = gridColumnCount + 1,
                    canLoadMore = { hasMore && !isLoading && !isLoadingMore },
                    onLoadMore = onLoadMore,
                    onTopRowDpadUp = onBackToTopBar ?: onRequestSidebarFocus,
                    consumeTopRowDpadUp = true,
                    onBackToTopBar = onBackToTopBar,
                    onLeftEdgeDpadLeft = onRequestSidebarFocus,
                    onVideoLongClick = onVideoLongClick?.let { longClick ->
                        { video, _ -> longClick(video) }
                    },
                    onVideoClick = { video, focusKey ->
                        val bvid = video.bvid.trim()
                        if (bvid.isNotEmpty()) {
                            onOpenVideo(focusKey, video)
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

@Composable
private fun ProfileVideoGridStatus(
    text: String,
    color: Color,
    focusCoordinator: HomeFocusCoordinator?,
    focusTab: AppTopLevelTab?,
    focusRegion: HomeFocusRegion,
    isHomeTabActive: Boolean,
    onDpadUp: () -> Boolean,
) {
    if (focusCoordinator != null && focusTab != null) {
        HomeEmptyFocusTarget(
            tab = focusTab,
            focusCoordinator = focusCoordinator,
            isActive = isHomeTabActive,
            region = focusRegion,
            modifier = Modifier.fillMaxSize(),
            onDpadUp = onDpadUp,
        ) {
            Text(
                text = text,
                color = color,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = text, color = color)
        }
    }
}
