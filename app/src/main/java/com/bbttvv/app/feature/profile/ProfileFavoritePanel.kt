package com.bbttvv.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.FavFolder
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion

@Composable
internal fun ProfileFavoritePanel(
    folders: List<FavFolder>,
    selectedFolderKey: String?,
    items: List<VideoItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    onSelectFolder: (String) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onRequestSidebarFocus: () -> Boolean,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "我的收藏", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        if (folders.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(
                    items = folders,
                    key = { folder -> resolveProfileFavoriteFolderKey(folder) }
                ) { folder ->
                    val folderKey = resolveProfileFavoriteFolderKey(folder)
                    FavoriteFolderChip(
                        title = resolveProfileFavoriteFolderLabel(folder),
                        count = folder.media_count,
                        selected = folderKey == selectedFolderKey,
                        onClick = { onSelectFolder(folderKey) }
                    )
                }
            }
        }
        ProfileVideoGrid(
            items = items,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            errorMessage = errorMessage,
            emptyText = if (folders.isEmpty()) "还没有收藏夹" else "当前收藏夹还没有内容",
            onOpenVideo = onOpenVideo,
            onLoadMore = onLoadMore,
            scrollResetKey = selectedFolderKey,
            focusCoordinator = focusCoordinator,
            focusTab = focusTab,
            focusRegion = HomeFocusRegion.ProfileContent,
            onRequestSidebarFocus = onRequestSidebarFocus,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavoriteFolderChip(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xE8E5EEF4) else Color(0x12000000),
            focusedContainerColor = Color(0xF2EEF6FB)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = if (selected) Color(0xFF111111) else Color.White,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    color = if (selected) Color(0x99000000) else Color(0xB3FFFFFF),
                    fontSize = 12.sp
                )
            }
        }
    }
}
