package com.bbttvv.app.ui.detail

import android.view.KeyEvent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bbttvv.app.data.model.response.RelatedVideo
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.ui.home.VideoCardRecyclerRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DetailTripleHoldDurationMs = 1_500

@Composable
internal fun DetailCoverBackdrop(model: ImageRequest) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val gradientColors = if (isLightTheme) {
        listOf(
            Color(0xB3F4F6F8), // 70% opacity white-gray
            Color(0xD9F4F6F8), // 85% opacity
            Color(0xF2F4F6F8)  // 95% opacity
        )
    } else {
        DetailBackdropGradientColors
    }

    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.Low,
        modifier = Modifier.fillMaxSize()
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = gradientColors
                )
            )
    )
}

@Composable
internal fun DetailHeroSection(
    viewInfo: ViewInfo,
    ownerAvatarModel: ImageRequest,
    coverModel: ImageRequest,
    followerCount: Int?,
    isFollowing: Boolean,
    isFollowActionLoading: Boolean,
    isLiked: Boolean,
    coinCount: Int,
    isFavoured: Boolean,
    isActionLoading: Boolean,
    playButtonFocusRequester: FocusRequester,
    onActionRowFocusChanged: (Boolean) -> Unit,
    onPlayButtonPlaced: () -> Unit = {},
    onHorizontalRailFocusChanged: (Dp?) -> Unit,
    onPlay: (String, Long, Long) -> Unit,
    onOpenPublisher: (Long, String, String) -> Unit,
    onToggleFollow: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenCoinDialog: () -> Unit,
    onToggleFavourite: () -> Unit,
    onTripleAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
            Text(
                text = viewInfo.title.replace("\n", " "),
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = if (isLightTheme) Color(0xFF18191C) else Color.White,
                maxLines = 2,
                lineHeight = 38.sp,
                overflow = TextOverflow.Ellipsis
            )

            DetailOwnerSection(
                avatarModel = ownerAvatarModel,
                ownerMid = viewInfo.owner.mid,
                ownerName = viewInfo.owner.name,
                ownerFace = viewInfo.owner.face,
                followerCount = followerCount,
                isFollowing = isFollowing,
                isFollowActionLoading = isFollowActionLoading,
                onOpenPublisher = onOpenPublisher,
                onToggleFollow = onToggleFollow
            )

            val dateFormat = remember { SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA) }
            val dateStr = if (viewInfo.pubdate > 0L) {
                dateFormat.format(Date(viewInfo.pubdate * 1000L))
            } else {
                null
            }
            DetailMetricRow(
                publishTime = dateStr,
                views = formatNumber(viewInfo.stat.view),
                danmaku = formatNumber(viewInfo.stat.danmaku),
                coins = formatNumber(viewInfo.stat.coin)
            )

            if (viewInfo.desc.isNotBlank() && viewInfo.desc != "-") {
                Text(
                    text = viewInfo.desc.take(220),
                    fontSize = 16.sp,
                    color = if (isLightTheme) Color(0xFF61666D) else DetailMutedTextColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 24.sp
                )
            }

            DetailStaticFocusArea {
                Row(
                    modifier = Modifier
                        .focusGroup()
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                when (event.nativeKeyEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        onActionRowFocusChanged(true)
                                    }

                                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        onActionRowFocusChanged(false)
                                    }
                                }
                            }
                            false
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                onActionRowFocusChanged(true)
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailCompactActionButton(
                        label = "播放",
                        modifier = Modifier.onPlaced { onPlayButtonPlaced() },
                        focusRequester = playButtonFocusRequester,
                        onClick = { onPlay(viewInfo.bvid, viewInfo.aid, viewInfo.cid) },
                        leadingContent = { tint ->
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    DetailTripleLikeActionButton(
                        label = formatNumber(viewInfo.stat.like),
                        active = isLiked,
                        enabled = !isActionLoading,
                        onClick = onToggleLike,
                        onTripleAction = onTripleAction,
                        leadingContent = { tint ->
                            Icon(
                                imageVector = Icons.Outlined.ThumbUp,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    DetailCompactActionButton(
                        label = formatNumber(viewInfo.stat.coin),
                        active = coinCount > 0,
                        enabled = !isActionLoading,
                        onClick = onOpenCoinDialog,
                        leadingContent = { tint ->
                            CoinMetricIcon(tint = tint, modifier = Modifier.size(16.dp))
                        }
                    )
                    DetailCompactActionButton(
                        label = formatNumber(viewInfo.stat.favorite),
                        active = isFavoured,
                        onClick = onToggleFavourite,
                        leadingContent = { tint ->
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        DetailPreviewCover(
            coverModel = coverModel,
            durationSeconds = viewInfo.pages.firstOrNull()?.duration?.toInt() ?: 0
        )
    }
}

@Composable
private fun DetailOwnerSection(
    avatarModel: ImageRequest,
    ownerMid: Long,
    ownerName: String,
    ownerFace: String,
    followerCount: Int?,
    isFollowing: Boolean,
    isFollowActionLoading: Boolean,
    onOpenPublisher: (Long, String, String) -> Unit,
    onToggleFollow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = { onOpenPublisher(ownerMid, ownerName, ownerFace) },
            enabled = ownerMid > 0L,
            modifier = Modifier.weight(1f),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color(0x22FFFFFF),
                pressedContainerColor = Color.Transparent
            ),
            glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None, Glow.None)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = avatarModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
                    Text(
                        text = ownerName,
                        fontSize = 16.sp,
                        color = if (isLightTheme) Color(0xFF18191C) else Color(0xFFF1F1F1),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = followerCount?.let { count ->
                            "${formatChineseCompactCount(count)}粉丝"
                        } ?: " ",
                        fontSize = 14.sp,
                        color = if (followerCount != null) {
                            if (isLightTheme) Color(0xFF61666D) else DetailMutedTextColor
                        } else {
                            (if (isLightTheme) Color(0xFF61666D) else DetailMutedTextColor).copy(alpha = 0f)
                        },
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        DetailFollowButton(
            isFollowing = isFollowing,
            isLoading = isFollowActionLoading,
            onClick = onToggleFollow
        )
    }
}

@Composable
private fun DetailFollowButton(
    isFollowing: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    var isFocused by remember { mutableStateOf(false) }
    val iconTint = when {
        isFocused -> {
            if (isLightTheme) Color.White else DetailPrimaryTextColor
        }
        isFollowing -> DetailAccentColor
        else -> {
            if (isLightTheme) Color(0xFF18191C) else Color.White
        }
    }

    val containerColor = if (isFocused) {
        if (isLightTheme) DetailAccentColor else DetailPrimaryPillColor
    } else {
        Color.Transparent
    }
    val borderWidth = if (isFocused) 0.dp else 1.5.dp
    val borderColor = if (isFocused) {
        Color.Transparent
    } else {
        if (isLightTheme) Color(0xFFCCCCCC) else Color(0x66FFFFFF)
    }

    Box(
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = if (isFollowing) "取消关注" else "关注"
            }
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = CircleShape
            )
            .onFocusChanged { focusState -> isFocused = focusState.hasFocus }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFollowing) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = if (isLoading) iconTint.copy(alpha = 0.65f) else iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DetailPreviewCover(
    coverModel: ImageRequest,
    durationSeconds: Int
) {
    Box(
        modifier = Modifier
            .width(360.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x26000000))
    ) {
        AsyncImage(
            model = coverModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize()
        )

        val min = durationSeconds / 60
        val sec = durationSeconds % 60
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .background(Color(0x99000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%02d:%02d", min, sec),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun RelatedVideosSection(
    videos: List<RelatedVideo>,
    isLoading: Boolean,
    onRailFocusChanged: (Boolean) -> Unit,
    onHorizontalRailFocusChanged: (Dp?) -> Unit,
    onVideoFocus: (RelatedVideo) -> Unit,
    onVideoClick: (RelatedVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
        Text(
            text = "相关视频",
            fontSize = 20.sp,
            color = if (isLightTheme) Color(0xFF18191C) else Color.White,
            fontWeight = FontWeight.Bold
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val relatedCardWidth = (maxWidth - 48.dp) / 4

            when {
                videos.isNotEmpty() -> {
                    val relatedVideoItems = remember(videos) { videos.map { related -> related.toVideoItem() } }
                    val relatedByVideoKey = remember(videos) {
                        videos.associateBy { related -> related.bvid.ifBlank { related.aid.toString() } }
                    }
                    VideoCardRecyclerRow(
                        videos = relatedVideoItems,
                        cardWidth = relatedCardWidth,
                        contentPadding = PaddingValues(0.dp),
                        horizontalContainerWidth = maxWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(relatedCardWidth * 0.7f + 92.dp)
                            .then(modifier),
                        onRailFocusChanged = onRailFocusChanged,
                        onHorizontalRailFocusChanged = onHorizontalRailFocusChanged,
                        onVideoFocused = { video, _ ->
                            relatedByVideoKey[video.bvid.ifBlank { video.id.toString() }]?.let(onVideoFocus)
                        },
                        onVideoClick = { video, _ ->
                            relatedByVideoKey[video.bvid.ifBlank { video.id.toString() }]?.let(onVideoClick)
                        }
                    )
                }

                isLoading -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) {
                            RelatedVideoPlaceholderCard(modifier = Modifier.width(relatedCardWidth))
                        }
                    }
                }

                else -> {
                    DetailMessageCard(text = "暂无相关视频")
                }
            }
        }
    }
}

@Composable
private fun RelatedVideoPlaceholderCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DetailCardColor)
            .padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.09f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.07f))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                )
            }
        }
    }
}

@Composable
private fun DetailTripleLikeActionButton(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onTripleAction: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable (Color) -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var isTriplePressing by remember { mutableStateOf(false) }
    var tripleCompleted by remember { mutableStateOf(false) }

    fun startTriplePress() {
        if (!enabled) return
        tripleCompleted = false
        isTriplePressing = shouldStartDetailTriplePress(longPressConfirmed = true)
    }

    fun finishPress(released: Boolean) {
        val completed = tripleCompleted
        if (
            shouldCancelDetailTriplePressOnRelease(
                isTriplePressing = isTriplePressing,
                tripleCompleted = completed,
            )
        ) {
            isTriplePressing = false
        }
        if (completed) {
            tripleCompleted = false
            return
        }
        if (released && enabled) onClick()
    }

    val animatedTripleProgress by animateFloatAsState(
        targetValue = if (isTriplePressing) 1f else 0f,
        animationSpec = if (isTriplePressing) {
            tween(durationMillis = DetailTripleHoldDurationMs, easing = LinearEasing)
        } else {
            tween(durationMillis = 180, easing = FastOutSlowInEasing)
        },
        label = "detailTripleLikeProgress",
        finishedListener = { progress ->
            if (progress >= 1f && isTriplePressing && !tripleCompleted) {
                tripleCompleted = true
                isTriplePressing = false
                onTripleAction()
            }
        },
    )

    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val contentColor = when {
        isFocused -> {
            if (isLightTheme) Color.White else DetailPrimaryTextColor
        }
        active -> DetailAccentColor
        else -> {
            if (isLightTheme) Color(0xFF18191C) else Color.White
        }
    }.copy(alpha = if (enabled) 1f else 0.68f)
    val containerColor = if (isFocused) {
        if (isLightTheme) DetailAccentColor else DetailPrimaryPillColor
    } else {
        Color.Transparent
    }
    val borderWidth = if (isFocused) 0.dp else 1.5.dp
    val borderColor = if (isFocused) {
        Color.Transparent
    } else {
        if (isLightTheme) Color(0xFFCCCCCC) else Color(0x66FFFFFF)
    }

    Box(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "点赞"
            }
            .defaultMinSize(minWidth = DetailCompactActionPillMetrics.minWidth)
            .height(DetailCompactActionPillMetrics.height)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(999.dp),
            )
            .onFocusChanged { focusState ->
                isFocused = focusState.hasFocus
                if (!focusState.hasFocus) {
                    isTriplePressing = false
                    tripleCompleted = false
                }
            }
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                if (!isDetailTripleConfirmKey(nativeEvent.keyCode)) return@onPreviewKeyEvent false
                when (nativeEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (nativeEvent.repeatCount == 0) startTriplePress()
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        finishPress(released = true)
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = press@{
                        if (!enabled) return@press
                        startTriplePress()
                        val released = tryAwaitRelease()
                        finishPress(released = released)
                    },
                )
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DetailCompactActionPillMetrics.horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent?.let { content ->
                Box(
                    modifier = Modifier.size(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DetailTripleProgressRing(
                        progress = animatedTripleProgress,
                        modifier = Modifier.size(22.dp),
                    )
                    content(contentColor)
                }
                Spacer(modifier = Modifier.width(DetailCompactActionPillMetrics.iconSpacing))
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = DetailCompactActionPillMetrics.textSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailTripleProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0f) return
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val diameter = size.minDimension - stroke
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        drawArc(
            color = DetailAccentColor.copy(alpha = 0.22f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = DetailAccentColor,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

private fun isDetailTripleConfirmKey(keyCode: Int): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_BUTTON_A,
        -> true
        else -> false
    }
}

@Composable
internal fun DetailCompactActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    leadingContent: (@Composable (Color) -> Unit)? = null
) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    var isFocused by remember { mutableStateOf(false) }
    val contentColor = when {
        isFocused -> {
            if (isLightTheme) Color.White else DetailPrimaryTextColor
        }
        active -> DetailAccentColor
        else -> {
            if (isLightTheme) Color(0xFF18191C) else Color.White
        }
    }.copy(alpha = if (enabled) 1f else 0.62f)
    val composedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }

    val containerColor = if (isFocused) {
        if (isLightTheme) DetailAccentColor else DetailPrimaryPillColor
    } else {
        Color.Transparent
    }
    val borderWidth = if (isFocused) 0.dp else 1.5.dp
    val borderColor = if (isFocused) {
        Color.Transparent
    } else {
        if (isLightTheme) Color(0xFFCCCCCC) else Color(0x66FFFFFF)
    }

    Box(
        modifier = composedModifier
            .defaultMinSize(minWidth = DetailCompactActionPillMetrics.minWidth)
            .height(DetailCompactActionPillMetrics.height)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(999.dp)
            )
            .onFocusChanged { focusState -> isFocused = focusState.hasFocus }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DetailCompactActionPillMetrics.horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingContent?.let { content ->
                content(contentColor)
                Spacer(modifier = Modifier.width(DetailCompactActionPillMetrics.iconSpacing))
            }
            Text(
                text = label,
                color = contentColor,
                fontSize = DetailCompactActionPillMetrics.textSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailMetricRow(
    publishTime: String?,
    views: String,
    danmaku: String,
    coins: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (publishTime != null) {
            DetailMetricItem(icon = DetailMetricIcon.Date, text = publishTime)
            Spacer(modifier = Modifier.width(16.dp))
        }
        DetailMetricItem(icon = DetailMetricIcon.Play, text = views)
        Spacer(modifier = Modifier.width(16.dp))
        DetailMetricItem(icon = DetailMetricIcon.Danmaku, text = danmaku)
        Spacer(modifier = Modifier.width(16.dp))
        DetailMetricItem(icon = DetailMetricIcon.Coin, text = coins)
    }
}

@Composable
private fun DetailMetricItem(
    icon: DetailMetricIcon,
    text: String,
    tint: Color = if (com.bbttvv.app.ui.theme.LocalIsLightTheme.current) Color(0xFF61666D) else Color(0xFFAAAAAA)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DetailMetricIcon(icon = icon, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, color = tint, fontSize = 14.sp)
    }
}

@Composable
private fun DetailMetricIcon(
    icon: DetailMetricIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    when (icon) {
        DetailMetricIcon.Date -> Icon(
            Icons.Outlined.DateRange,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
        DetailMetricIcon.Play -> Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
        DetailMetricIcon.Danmaku -> DanmakuMetricIcon(tint = tint, modifier = modifier)
        DetailMetricIcon.Coin -> CoinMetricIcon(tint = tint, modifier = modifier)
    }
}

private enum class DetailMetricIcon {
    Date,
    Play,
    Danmaku,
    Coin
}

@Composable
private fun DanmakuMetricIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.11f
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.08f, size.height * 0.2f),
            size = Size(size.width * 0.84f, size.height * 0.54f),
            cornerRadius = CornerRadius(size.width * 0.16f, size.height * 0.16f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.25f, size.height * 0.45f),
            end = Offset(size.width * 0.75f, size.height * 0.45f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.32f, size.height * 0.63f),
            end = Offset(size.width * 0.62f, size.height * 0.63f),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
internal fun CoinMetricIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        drawOval(
            color = tint,
            topLeft = Offset(size.width * 0.18f, size.height * 0.12f),
            size = Size(size.width * 0.64f, size.height * 0.76f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.5f, size.height * 0.3f),
            end = Offset(size.width * 0.5f, size.height * 0.7f),
            strokeWidth = strokeWidth
        )
    }
}

private fun formatChineseCompactCount(number: Int): String {
    return if (number >= 10000) {
        val value = number / 10000.0
        if (value >= 100) {
            String.format(Locale.CHINA, "%.0f万", value)
        } else {
            String.format(Locale.CHINA, "%.1f万", value)
        }
    } else {
        number.toString()
    }
}

private fun RelatedVideo.toVideoItem(): VideoItem {
    return VideoItem(
        id = aid,
        bvid = bvid,
        aid = aid,
        cid = cid,
        title = title,
        pic = pic,
        owner = owner,
        stat = stat,
        duration = duration
    )
}

@Composable
internal fun DetailPagesSection(
    viewInfo: ViewInfo,
    firstPageFocusRequester: FocusRequester,
    playButtonFocusRequester: FocusRequester,
    relatedVideosFocusRequester: FocusRequester,
    onPlay: (String, Long, Long) -> Unit,
    onRailFocusChanged: (Boolean) -> Unit = {},
    onPageFocus: () -> Unit = {},
    onHorizontalRailFocusChanged: (Dp?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
        Text(
            text = "分P",
            fontSize = 20.sp,
            color = if (isLightTheme) Color(0xFF18191C) else Color.White,
            fontWeight = FontWeight.Bold
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                modifier = Modifier
                    .detailHorizontalFocusRail(
                        horizontalContainerWidth = maxWidth,
                        onRailFocusChanged = onRailFocusChanged,
                        onHorizontalRailFocusChanged = onHorizontalRailFocusChanged
                    )
                    .focusProperties {
                        up = playButtonFocusRequester
                        down = relatedVideosFocusRequester
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 48.dp)
            ) {
                itemsIndexed(
                    items = viewInfo.pages,
                    key = { _, page -> page.cid }
                ) { index, page ->
                    val baseModifier = if (page.page == 1) {
                        Modifier.focusRequester(firstPageFocusRequester)
                    } else {
                        Modifier
                    }
                    val isFirst = index == 0
                    val isLast = index == viewInfo.pages.lastIndex
                    val edgeKeyModifier = Modifier.onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                            val keyCode = event.nativeKeyEvent.keyCode
                            if (isFirst && keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                                return@onPreviewKeyEvent true
                            }
                            if (isLast && keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    val pillModifier = baseModifier
                        .then(edgeKeyModifier)
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                onPageFocus()
                            }
                        }
                        .focusProperties {
                            up = playButtonFocusRequester
                            down = relatedVideosFocusRequester
                        }
                    DetailPillButton(
                        label = "P${page.page} ${page.part.ifBlank { "分段 ${page.page}" }}",
                        onClick = { onPlay(viewInfo.bvid, viewInfo.aid, page.cid) },
                        modifier = pillModifier
                    )
                }
            }
        }
    }
}
