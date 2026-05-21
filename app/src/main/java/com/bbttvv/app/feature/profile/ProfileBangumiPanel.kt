package com.bbttvv.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.recyclerview.widget.RecyclerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.FollowBangumiItem
import com.bbttvv.app.data.model.response.Owner
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvConfirmDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion

@Composable
internal fun ProfileBangumiPanel(
    items: List<FollowBangumiItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    onOpenVideo: (String, VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onUnfollowBangumi: (Long) -> Unit,
    onRequestSidebarFocus: () -> Boolean,
    onBackToTopBar: (() -> Boolean)? = null,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null
) {
    var pendingUnfollowBangumi by remember { mutableStateOf<VideoItem?>(null) }
    var suppressUnfollowDialogConfirmKey by remember { mutableStateOf(false) }

    LaunchedEffect(items) {
        val pending = pendingUnfollowBangumi ?: return@LaunchedEffect
        // 如果列表中已经不存在该番剧，说明已经成功取消追番，关闭对话框
        if (items.none { item -> item.seasonId == pending.id }) {
            pendingUnfollowBangumi = null
        }
    }

    val videoItems = remember(items) {
        items.map { item ->
            VideoItem(
                id = item.seasonId,
                bvid = "ss${item.seasonId}", // Pseudo-Bvid 前缀 ss$seasonId
                title = item.title,
                pic = item.cover.ifBlank { item.squareCover },
                owner = Owner(
                    mid = item.mediaId,
                    name = item.progress.ifBlank { "开始观看" }, // 观看进度渲染在 UP 主位置
                    face = ""
                ),
                collectionSubtitle = item.newEp?.indexShow.orEmpty()
            )
        }
    }

    val bangumiScrollResetKey = remember(items) {
        items.firstOrNull()?.seasonId
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
        Text(text = "我的追番", color = if (isLightTheme) Color(0xFF18191C) else Color.White, style = MaterialTheme.typography.headlineMedium)
        ProfileVideoGrid(
            items = videoItems,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            errorMessage = errorMessage,
            emptyText = "还没有追番，去首页看看吧",
            onOpenVideo = onOpenVideo,
            onLoadMore = onLoadMore,
            onVideoLongClick = { video ->
                suppressUnfollowDialogConfirmKey = true
                pendingUnfollowBangumi = video
            },
            scrollResetKey = bangumiScrollResetKey,
            focusCoordinator = focusCoordinator,
            focusTab = focusTab,
            focusRegion = HomeFocusRegion.ProfileContent,
            videoCardRecycledViewPool = videoCardRecycledViewPool,
            onBackToTopBar = onBackToTopBar,
            onRequestSidebarFocus = onRequestSidebarFocus,
            modifier = Modifier.weight(1f)
        )
    }

    pendingUnfollowBangumi?.let { video ->
        TvConfirmDialog(
            title = "取消追番",
            message = video.title
                .takeIf { it.isNotBlank() }
                ?.let { title -> "确认取消追番「$title」吗？" }
                ?: "确认取消追番该部番剧吗？",
            onDismissRequest = { pendingUnfollowBangumi = null },
            suppressConfirmKey = suppressUnfollowDialogConfirmKey,
            onSuppressConfirmKeyConsumed = { suppressUnfollowDialogConfirmKey = false },
            actions = {
                TvDialogActionButton(
                    text = "保留追番",
                    onClick = { pendingUnfollowBangumi = null }
                )
                TvDialogActionButton(
                    text = "取消追番",
                    contentColor = Color(0xFFFFD0D8),
                    onClick = {
                        onUnfollowBangumi(video.id)
                        pendingUnfollowBangumi = null
                    }
                )
            }
        )
    }
}
