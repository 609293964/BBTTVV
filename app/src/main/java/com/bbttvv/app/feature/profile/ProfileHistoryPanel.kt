package com.bbttvv.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.HistoryData
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion

@Composable
internal fun ProfileHistoryPanel(
    historyItems: List<HistoryData>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    onOpenVideo: (VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onRequestSidebarFocus: () -> Boolean,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null
) {
    val videoItems = remember(historyItems) { historyItems.map { item -> item.toVideoItem() } }
    val historyScrollResetKey = remember(historyItems) {
        historyItems.firstOrNull()?.let { item ->
            buildString {
                append(item.history?.business.orEmpty().ifBlank { "archive" })
                append(':')
                append(item.history?.bvid.orEmpty().ifBlank { item.history?.oid?.toString().orEmpty() })
                append(':')
                append(item.history?.cid ?: 0L)
                append(':')
                append(item.view_at)
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "历史记录", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        ProfileVideoGrid(
            items = videoItems,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            errorMessage = errorMessage,
            emptyText = "还没有历史记录",
            onOpenVideo = onOpenVideo,
            onLoadMore = onLoadMore,
            showHistoryProgressOnly = true,
            scrollResetKey = historyScrollResetKey,
            focusCoordinator = focusCoordinator,
            focusTab = focusTab,
            focusRegion = HomeFocusRegion.ProfileContent,
            onRequestSidebarFocus = onRequestSidebarFocus,
            modifier = Modifier.weight(1f)
        )
    }
}
