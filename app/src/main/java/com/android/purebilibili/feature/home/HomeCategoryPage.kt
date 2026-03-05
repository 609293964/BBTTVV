package com.android.purebilibili.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.animation.DissolveAnimationPreset
import com.android.purebilibili.core.ui.animation.DissolvableVideoCard
import com.android.purebilibili.core.ui.animation.jiggleOnDissolve
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.performance.TrackScrollJank
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.util.responsiveContentWidth
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.home.components.cards.ElegantVideoCard
import com.android.purebilibili.feature.home.components.cards.LiveRoomCard
import com.android.purebilibili.feature.home.components.cards.StoryVideoCard

import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import androidx.compose.ui.Alignment
import coil.compose.AsyncImage
import kotlinx.coroutines.yield

@Composable
internal fun HomeCategoryPageContent(
    category: HomeCategory,
    categoryState: CategoryContent,
    gridState: LazyGridState,
    gridColumns: Int,
    contentPadding: PaddingValues,
    dissolvingVideos: Set<String>,
    followingMids: Set<Long>,
    onVideoClick: (HomeVideoClickRequest) -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onLoadMore: () -> Unit,
    onDismissVideo: (String) -> Unit,
    onWatchLater: (String, Long) -> Unit,
    onDissolveComplete: (String) -> Unit,
    longPressCallback: (VideoItem) -> Unit, // [Feature] Long Press
    displayMode: Int,
    cardAnimationEnabled: Boolean,
    cardMotionTier: MotionTier = MotionTier.Normal,
    cardTransitionEnabled: Boolean,
    smartVisualGuardEnabled: Boolean = false,
    isDataSaverActive: Boolean,
    compactStatsOnCover: Boolean = true,
    oldContentAnchorBvid: String? = null,
    oldContentStartIndex: Int? = null,
    todayWatchEnabled: Boolean = false,
    todayWatchMode: TodayWatchMode = TodayWatchMode.RELAX,
    todayWatchPlan: TodayWatchPlan? = null,
    todayWatchLoading: Boolean = false,
    todayWatchError: String? = null,
    todayWatchCollapsed: Boolean = false,
    todayWatchCardConfig: TodayWatchCardUiConfig = TodayWatchCardUiConfig(),
    onTodayWatchModeChange: (TodayWatchMode) -> Unit = {},
    onTodayWatchCollapsedChange: (Boolean) -> Unit = {},
    onTodayWatchRefresh: () -> Unit = {},
    popularSubCategory: PopularSubCategory = PopularSubCategory.COMPREHENSIVE,
    onPopularSubCategoryChange: (PopularSubCategory) -> Unit = {},
    onTodayWatchVideoClick: (VideoItem) -> Unit = { video ->
        onVideoClick(
            HomeVideoClickRequest(
                bvid = video.bvid,
                cid = video.cid,
                coverUrl = video.pic,
                source = HomeVideoClickSource.TODAY_WATCH
            )
        )
    },
    firstGridItemModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val scrollLiteModeEnabled = false
    TrackScrollJank(
        scrollableState = gridState,
        stateName = "home:feed:${category.name.lowercase()}"
    )

    // Check for load more
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4 && !categoryState.isLoading && categoryState.hasMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(gridColumns),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        if (category == HomeCategory.LIVE) {
            // Live Category Content
            
            // 1. Followed Live Rooms
            if (categoryState.followedLiveRooms.isNotEmpty()) {
                item(span = { GridItemSpan(gridColumns) }) {
                    Text(
                        text = "关注",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                
                itemsIndexed(
                    items = categoryState.followedLiveRooms,
                    key = { _, room -> "followed_${room.roomid}" },
                    contentType = { _, _ -> "live_room" }
                ) { index, room ->
                    LiveRoomCard(
                        room = room,
                        index = index,
                        modifier = if (index == 0) firstGridItemModifier else Modifier,
                        onClick = { onLiveClick(room.roomid, room.title, room.uname) } 
                    )
                }
            }
            
            // 2. Popular Live Rooms
            if (categoryState.liveRooms.isNotEmpty()) {
                item(span = { GridItemSpan(gridColumns) }) {
                    Text(
                        text = "推荐直播",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
                
                itemsIndexed(
                    items = categoryState.liveRooms,
                    key = { _, room -> "popular_${room.roomid}" },
                    contentType = { _, _ -> "live_room" }
                ) { index, room ->
                    LiveRoomCard(
                        room = room,
                        index = index,
                        modifier = if (categoryState.followedLiveRooms.isEmpty() && index == 0) {
                            firstGridItemModifier
                        } else {
                            Modifier
                        },
                        onClick = { onLiveClick(room.roomid, room.title, room.uname) } 
                    )
                }
            }
        } else {
            // Video Category Content
            if (category == HomeCategory.RECOMMEND) {
                if (todayWatchEnabled) {
                    item(span = { GridItemSpan(gridColumns) }) {
                        TodayWatchPlanCard(
                            selectedMode = todayWatchMode,
                            plan = todayWatchPlan,
                            isLoading = todayWatchLoading,
                            error = todayWatchError,
                            collapsed = todayWatchCollapsed,
                            cardConfig = todayWatchCardConfig,
                            onModeChange = onTodayWatchModeChange,
                            onCollapsedChange = onTodayWatchCollapsedChange,
                            onRefresh = onTodayWatchRefresh,
                            onVideoClick = onTodayWatchVideoClick
                        )
                    }
                }
            }
            if (category == HomeCategory.POPULAR) {
                item(span = { GridItemSpan(gridColumns) }) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PopularSubCategory.entries.forEach { subCategory ->
                            FilterChip(
                                selected = popularSubCategory == subCategory,
                                onClick = { onPopularSubCategoryChange(subCategory) },
                                label = { Text(subCategory.label) }
                            )
                        }
                    }
                }
            }

            if (categoryState.videos.isNotEmpty()) {
                val shouldShowOldContentDivider = category == HomeCategory.RECOMMEND &&
                    (
                        (oldContentAnchorBvid != null && categoryState.videos.any { it.bvid == oldContentAnchorBvid }) ||
                            (oldContentStartIndex != null && oldContentStartIndex > 0 && oldContentStartIndex < categoryState.videos.size)
                        )

                categoryState.videos.forEachIndexed { index, video ->
                    val shouldInsertDividerHere = shouldShowOldContentDivider && (
                        (oldContentAnchorBvid != null && video.bvid == oldContentAnchorBvid && index > 0) ||
                            (oldContentAnchorBvid == null && index == oldContentStartIndex)
                        )
                    if (shouldInsertDividerHere) {
                        item(
                            key = "old_content_divider_$index",
                            contentType = "home_old_content_divider",
                            span = { GridItemSpan(gridColumns) }
                        ) {
                            OldContentDivider()
                        }
                    }

                    item(
                        key = if (video.bvid.isNotBlank()) video.bvid else "video_${video.id}_$index",
                        contentType = "home_video_card"
                    ) {
                        val isDynamicDetailCard = video.dynamicId.isNotBlank() && !video.bvid.startsWith("BV", ignoreCase = true)
                        val isDissolving = video.bvid in dissolvingVideos

                        DissolvableVideoCard(
                            isDissolving = isDissolving,
                            onDissolveComplete = { onDissolveComplete(video.bvid) },
                            cardId = video.bvid,
                            preset = DissolveAnimationPreset.TELEGRAM_FAST,
                            modifier = Modifier
                                .jiggleOnDissolve(video.bvid)
                                .then(if (index == 0) firstGridItemModifier else Modifier)
                        ) {
                            when (displayMode) {
                                1 -> {
                                    StoryVideoCard(
                                        video = video,
                                        index = index,
                                        animationEnabled = cardAnimationEnabled,
                                        motionTier = cardMotionTier,
                                        transitionEnabled = cardTransitionEnabled,
                                        scrollLiteModeEnabled = scrollLiteModeEnabled,
                                        onDismiss = { onDismissVideo(video.bvid) },
                                        onLongClick = if (isDynamicDetailCard) null else ({ longPressCallback(video) }),
                                        onClick = { bvid, cid ->
                                            onVideoClick(
                                                HomeVideoClickRequest(
                                                    bvid = bvid,
                                                    dynamicId = video.dynamicId,
                                                    cid = cid,
                                                    coverUrl = video.pic,
                                                    source = HomeVideoClickSource.GRID
                                                )
                                            )
                                        }
                                    )
                                }

                                else -> {
                                    ElegantVideoCard(
                                        video = video,
                                        index = index,
                                        isFollowing = video.owner.mid in followingMids && category != HomeCategory.FOLLOW,
                                        animationEnabled = cardAnimationEnabled,
                                        motionTier = cardMotionTier,
                                        transitionEnabled = cardTransitionEnabled,
                                        scrollLiteModeEnabled = scrollLiteModeEnabled,
                                        isDataSaverActive = isDataSaverActive,
                                        compactStatsOnCover = compactStatsOnCover,
                                        onDismiss = { onDismissVideo(video.bvid) },
                                        onWatchLater = if (isDynamicDetailCard) null else ({
                                            onWatchLater(video.bvid, resolveWatchLaterAid(video))
                                        }),
                                        onLongClick = if (isDynamicDetailCard) null else ({ longPressCallback(video) }),
                                        onClick = { bvid, cid ->
                                            onVideoClick(
                                                HomeVideoClickRequest(
                                                    bvid = bvid,
                                                    dynamicId = video.dynamicId,
                                                    cid = cid,
                                                    coverUrl = video.pic,
                                                    source = HomeVideoClickSource.GRID
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loading Indicator at bottom
        if (categoryState.isLoading || categoryState.hasMore) {
             item(span = { GridItemSpan(gridColumns) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (categoryState.isLoading) {
                        CupertinoActivityIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
        
        // Spacer
        item(span = { GridItemSpan(gridColumns) }) {
            Box(modifier = Modifier.fillMaxWidth().height(20.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayWatchPlanCard(
    selectedMode: TodayWatchMode,
    plan: TodayWatchPlan?,
    isLoading: Boolean,
    error: String?,
    collapsed: Boolean,
    cardConfig: TodayWatchCardUiConfig,
    onModeChange: (TodayWatchMode) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onVideoClick: (VideoItem) -> Unit
) {
    var revealContent by remember(plan?.generatedAt, isLoading, cardConfig.enableWaterfallAnimation) {
        mutableStateOf(!cardConfig.enableWaterfallAnimation)
    }
    LaunchedEffect(plan?.generatedAt, isLoading, cardConfig.enableWaterfallAnimation) {
        if (!cardConfig.enableWaterfallAnimation) {
            revealContent = true
            return@LaunchedEffect
        }
        if (isLoading) {
            revealContent = false
            return@LaunchedEffect
        }
        revealContent = false
        yield()
        revealContent = true
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "今日推荐单",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(
                        enabled = !isLoading,
                        onClick = onRefresh
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("刷新")
                    }
                    TextButton(
                        onClick = { onCollapsedChange(!collapsed) }
                    ) {
                        Icon(
                            imageVector = if (collapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (collapsed) "展开" else "收起")
                    }
                }
            }

            if (collapsed) {
                Text(
                    text = "已收起推荐单。展开后恢复自动更新；也可以直接点“刷新”换一批。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                return@Column
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayWatchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            Text(
                text = "点开后会自动从推荐单移除；想换一批可点右上角“刷新”。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.8.dp)
                    Text("正在根据你的历史观看习惯生成推荐…", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            val activePlan = plan ?: return@Column
            var revealIndex = 0

            if (cardConfig.showReasonHint) {
                val hintOrder = revealIndex++
                WaterfallReveal(
                    enabled = cardConfig.enableWaterfallAnimation,
                    visible = revealContent,
                    index = hintOrder,
                    exponent = cardConfig.waterfallExponent
                ) {
                    Text(
                        text = if (activePlan.nightSignalUsed) {
                            "已结合护眼状态：夜间优先短时长、低刺激内容"
                        } else {
                            "当前按你的观看习惯与模式偏好生成"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (cardConfig.showUpRank && activePlan.upRanks.isNotEmpty()) {
                val titleOrder = revealIndex++
                WaterfallReveal(
                    enabled = cardConfig.enableWaterfallAnimation,
                    visible = revealContent,
                    index = titleOrder,
                    exponent = cardConfig.waterfallExponent
                ) {
                    Text("UP主榜", style = MaterialTheme.typography.labelLarge)
                }
                val ranksOrder = revealIndex++
                WaterfallReveal(
                    enabled = cardConfig.enableWaterfallAnimation,
                    visible = revealContent,
                    index = ranksOrder,
                    exponent = cardConfig.waterfallExponent
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activePlan.upRanks.forEachIndexed { index, up ->
                            Text(
                                text = "${index + 1}. ${up.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (activePlan.videoQueue.isNotEmpty()) {
                val queueTitleOrder = revealIndex++
                WaterfallReveal(
                    enabled = cardConfig.enableWaterfallAnimation,
                    visible = revealContent,
                    index = queueTitleOrder,
                    exponent = cardConfig.waterfallExponent
                ) {
                    Text("视频队列", style = MaterialTheme.typography.labelLarge)
                }
                activePlan.videoQueue
                    .take(cardConfig.queuePreviewLimit.coerceAtLeast(1))
                    .forEachIndexed { index, video ->
                        val rowOrder = revealIndex++
                        WaterfallReveal(
                            enabled = cardConfig.enableWaterfallAnimation,
                            visible = revealContent,
                            index = rowOrder,
                            exponent = cardConfig.waterfallExponent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoClick(video) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (video.owner.face.isNotBlank()) {
                                    AsyncImage(
                                        model = video.owner.face,
                                        contentDescription = video.owner.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = video.owner.name.take(1).ifBlank { "UP" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = video.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    UpBadgeName(
                                        name = video.owner.name,
                                        nameStyle = MaterialTheme.typography.labelSmall,
                                        nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                        badgeBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    val explanation = activePlan.explanationByBvid[video.bvid].orEmpty()
                                    if (explanation.isNotBlank()) {
                                        Text(
                                            text = explanation,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
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

@Composable
private fun WaterfallReveal(
    enabled: Boolean,
    visible: Boolean,
    index: Int,
    exponent: Float,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val delay = nonLinearWaterfallDelayMillis(
        index = index,
        baseDelayMs = 52,
        exponent = exponent,
        maxDelayMs = 620
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 280,
                delayMillis = delay,
                easing = LinearOutSlowInEasing
            )
        ) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(
                durationMillis = 420,
                delayMillis = delay,
                easing = FastOutSlowInEasing
            )
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        content()
    }
}

@Composable
private fun OldContentDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
        Text(
            text = "以下是上次最新的视频",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    }
}
