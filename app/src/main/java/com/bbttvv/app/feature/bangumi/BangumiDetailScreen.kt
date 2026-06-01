package com.bbttvv.app.feature.bangumi

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.data.model.response.BangumiEpisode
import com.bbttvv.app.data.model.response.SeasonInfo
import com.bbttvv.app.ui.components.rememberSizedImageModel
import com.bbttvv.app.ui.theme.LocalIsLightTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BangumiDetailScreen(
    seasonId: Long,
    epId: Long,
    onBack: () -> Unit,
    onPlayEpisode: (Long, Long, Long) -> Unit,
    viewModel: BangumiViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLightTheme = LocalIsLightTheme.current
    val pageBackgroundColor = if (isLightTheme) Color(0xFFF4F6F8) else Color(0xFF111315)
    val primaryTextColor = if (isLightTheme) Color(0xFF18191C) else Color.White
    val secondaryTextColor = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.7f)
    val mutedTextColor = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.5f)
    val focusedControlContainerColor = if (isLightTheme) Color(0xFFFB7299) else Color.White
    val focusedControlContentColor = if (isLightTheme) Color.White else Color(0xFF111315)
    val secondaryControlContainerColor = if (isLightTheme) Color(0x0C000000) else Color(0x33FFFFFF)
    val pressedControlContainerColor = if (isLightTheme) Color(0xFFE25E83) else Color.White.copy(alpha = 0.8f)
    val posterBorderColor = if (isLightTheme) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.2f)
    val coverPlaceholderColor = if (isLightTheme) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.4f)
    val backdropGradientColors = if (isLightTheme) {
        listOf(
            Color(0xD9F4F6F8),
            Color(0xF7F4F6F8)
        )
    } else {
        listOf(
            Color.Black.copy(alpha = 0.5f),
            Color(0xFF111315).copy(alpha = 0.95f)
        )
    }

    val playButtonFocusRequester = remember { FocusRequester() }
    val followButtonFocusRequester = remember { FocusRequester() }
    val seasonTabFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }

    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(seasonId, epId) {
        viewModel.load(seasonId, epId)
    }

    LaunchedEffect(uiState.detail) {
        if (uiState.detail != null && !initialFocusRequested) {
            playButtonFocusRequester.requestFocus()
            initialFocusRequested = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackgroundColor)
    ) {
        // 1. 背景层：高斯模糊的大封面海报，配合暗色渐变压底
        uiState.detail?.cover?.let { coverUrl ->
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp)
                    .background(coverPlaceholderColor)
            )
        }

        // 压底渐变罩，产生高级的毛玻璃叠加暗黑色感
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = backdropGradientColors
                    )
                )
        )

        // 2. 内容层：左右布局
        if (uiState.isLoading && uiState.detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "正在努力加载番剧详情...", color = primaryTextColor, fontSize = 18.sp)
            }
        } else if (uiState.error != null && uiState.detail == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(onClick = { viewModel.load(seasonId, epId) }) {
                        Text(text = "重新加载", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        } else {
            uiState.detail?.let { detail ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // 左侧占 30%：展示精美封面图、评分、追番/已追番按钮、简介
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.3f),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // 海报图
                        AsyncImage(
                            model = rememberSizedImageModel(
                                url = detail.cover.ifBlank { detail.squareCover },
                                widthPx = 360,
                                heightPx = 480
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 180.dp, height = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, posterBorderColor, RoundedCornerShape(12.dp))
                        )

                        // 评分
                        detail.rating?.let { rating ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = String.format("%.1f", rating.score),
                                    color = Color(0xFFFFB300),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "分 (${rating.count}人评)",
                                    color = secondaryTextColor,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // 按钮组
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 播放首集 / 继续播放按钮
                            var isPlayFocused by remember { mutableStateOf(false) }
                            val lastEpId = detail.userStatus?.progress?.lastEpId ?: 0L
                            val lastEpIndex = detail.userStatus?.progress?.lastEpIndex.orEmpty()
                            val playButtonText = if (lastEpId > 0L) {
                                if (lastEpIndex.isNotBlank()) "继续播放 $lastEpIndex" else "继续播放"
                            } else {
                                "开始播放"
                            }
                            Surface(
                                onClick = {
                                    if (lastEpId > 0L) {
                                        val targetEp = detail.episodes?.find { it.id == lastEpId }
                                            ?: detail.episodes?.firstOrNull()
                                        if (targetEp != null) {
                                            onPlayEpisode(targetEp.id, targetEp.cid, targetEp.aid)
                                        }
                                    } else {
                                        val firstEp = detail.episodes?.firstOrNull()
                                        if (firstEp != null) {
                                            onPlayEpisode(firstEp.id, firstEp.cid, firstEp.aid)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(playButtonFocusRequester)
                                    .onFocusChanged { isPlayFocused = it.hasFocus }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                            keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                                        ) {
                                            // 限制向下，聚焦到追番按钮
                                            followButtonFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isPlayFocused) focusedControlContainerColor else Color(0xFF1E88E5),
                                    focusedContainerColor = focusedControlContainerColor,
                                    pressedContainerColor = pressedControlContainerColor
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = playButtonText,
                                    color = if (isPlayFocused) focusedControlContentColor else Color.White,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            // 追番按钮
                            var isFollowFocused by remember { mutableStateOf(false) }
                            val followText = if (uiState.isFollowed) "已追番" else "追番"
                            Surface(
                                onClick = { viewModel.toggleFollow() },
                                modifier = Modifier
                                    .focusRequester(followButtonFocusRequester)
                                    .onFocusChanged { isFollowFocused = it.hasFocus }
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                            keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                                        ) {
                                            playButtonFocusRequester.requestFocus()
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isFollowFocused) focusedControlContainerColor else secondaryControlContainerColor,
                                    focusedContainerColor = focusedControlContainerColor,
                                    pressedContainerColor = pressedControlContainerColor
                                ),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isFollowed) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (uiState.isFollowed) Color(0xFFFF4081) else if (isFollowFocused) focusedControlContentColor else primaryTextColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = followText,
                                        color = if (isFollowFocused) focusedControlContentColor else primaryTextColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        // 简介（折叠或固定，TV 上展示前几行）
                        Text(
                            text = detail.evaluate.ifBlank { "暂无番剧简介" },
                            color = secondaryTextColor,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // 右侧占 70%：展示大标题、分类、季度 Tab（多季番剧）、剧集网格列表
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.7f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. 番剧标题和基础信息
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = detail.title,
                                color = primaryTextColor,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // 类型、地区等标签
                            val tagText = buildString {
                                val area = detail.areas?.firstOrNull()?.name
                                if (!area.isNullOrBlank()) {
                                    append(area)
                                    append(" | ")
                                }
                                val styles = detail.styles
                                if (!styles.isNullOrEmpty()) {
                                    append(styles.joinToString(" / "))
                                    append(" | ")
                                }
                                if (detail.total > 0) {
                                    append("共 ${detail.total} 话")
                                } else {
                                    append(detail.newEp?.desc ?: "连载中")
                                }
                            }
                            Text(
                                text = tagText,
                                color = mutedTextColor,
                                fontSize = 13.sp
                            )
                        }

                        // 2. 季度 Tab（如果是多季番剧，或者当前单季不需要 Tab）
                        val seasons = detail.seasons
                        if (!seasons.isNullOrEmpty() && seasons.size > 1) {
                            var selectedIndex by remember(seasons, uiState.selectedSeasonId) {
                                mutableIntStateOf(seasons.indexOfFirst { it.seasonId == uiState.selectedSeasonId }.coerceAtLeast(0))
                            }

                            TabRow(
                                selectedTabIndex = selectedIndex,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .focusRequester(seasonTabFocusRequester),
                                indicator = { _, _ -> } // 隐藏自带的粗条
                            ) {
                                seasons.forEachIndexed { index, season ->
                                    val isSelected = selectedIndex == index
                                    var isTabFocused by remember { mutableStateOf(false) }

                                    Tab(
                                        selected = isSelected,
                                        onFocus = {
                                            selectedIndex = index
                                            viewModel.switchSeason(season.seasonId)
                                        },
                                        onClick = {
                                            selectedIndex = index
                                            viewModel.switchSeason(season.seasonId)
                                        },
                                        modifier = Modifier
                                            .onFocusChanged { isTabFocused = it.hasFocus }
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                                                ) {
                                                    episodesFocusRequester.requestFocus()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    when {
                                                        isTabFocused -> focusedControlContainerColor
                                                        isSelected -> if (isLightTheme) Color(0x14FB7299) else Color.White.copy(alpha = 0.2f)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = season.seasonTitle.ifBlank { season.title },
                                                color = when {
                                                    isTabFocused -> focusedControlContentColor
                                                    isSelected -> if (isLightTheme) Color(0xFFFB7299) else Color.White
                                                    else -> secondaryTextColor
                                                },
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. 剧集 Episodes 平铺网格
                        val episodes = uiState.episodes
                        if (episodes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (uiState.isLoading) "正在载入剧集..." else "该季度暂无剧集",
                                    color = mutedTextColor
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .focusRequester(episodesFocusRequester),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(episodes, key = { _, ep -> ep.id }) { index, ep ->
                                    EpisodeCard(
                                        episode = ep,
                                        isLeftEdge = index % 4 == 0,
                                        isTopEdge = index < 4,
                                        onClick = { onPlayEpisode(ep.id, ep.cid, ep.aid) },
                                        onLeftEdge = {
                                            // 网格的最左侧按左折返，聚焦到左侧的播放首集/追番按钮上
                                            playButtonFocusRequester.requestFocus()
                                            true
                                        },
                                        onTopEdge = {
                                            // 网格最上层向上，聚焦到季度 Tab 或者播放按钮上
                                            if (!seasons.isNullOrEmpty() && seasons.size > 1) {
                                                seasonTabFocusRequester.requestFocus()
                                            } else {
                                                playButtonFocusRequester.requestFocus()
                                            }
                                            true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EpisodeCard(
    episode: BangumiEpisode,
    isLeftEdge: Boolean,
    isTopEdge: Boolean,
    onClick: () -> Unit,
    onLeftEdge: () -> Boolean,
    onTopEdge: () -> Boolean
) {
    var isFocused by remember { mutableStateOf(false) }
    val isLightTheme = LocalIsLightTheme.current
    val containerColor = if (isLightTheme) Color.White else Color(0x1AFFFFFF)
    val focusedContainerColor = if (isLightTheme) Color(0xFFFB7299) else Color.White
    val pressedContainerColor = if (isLightTheme) Color(0xFFE25E83) else Color.White.copy(alpha = 0.8f)
    val primaryTextColor = if (isLightTheme) Color(0xFF18191C) else Color.White
    val secondaryTextColor = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.5f)
    val focusedTextColor = if (isLightTheme) Color.White else Color(0xFF111315)
    val focusedSecondaryTextColor = if (isLightTheme) Color.White.copy(alpha = 0.82f) else Color(0x99111315)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .onFocusChanged { isFocused = it.hasFocus }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isLeftEdge) {
                                onLeftEdge()
                            } else {
                                false
                            }
                        }
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            if (isTopEdge) {
                                onTopEdge()
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = focusedContainerColor,
            pressedContainerColor = pressedContainerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // 集数标题 ("第1话")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = episode.title,
                    color = if (isFocused) focusedTextColor else primaryTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 会员专享等角标
                if (episode.badge.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isFocused) Color(0xFFFF4081) else Color(0xFFFF4081).copy(alpha = 0.2f)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = episode.badge,
                            color = if (isFocused) Color.White else Color(0xFFFF4081),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 长标题 ("开始的地方")
            if (episode.longTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.longTitle,
                    color = if (isFocused) focusedSecondaryTextColor else secondaryTextColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
