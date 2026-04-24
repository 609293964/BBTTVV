package com.bbttvv.app.ui.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImagePainter
import coil.compose.AsyncImage
import com.bbttvv.app.core.util.formatShortVideoPubDate
import com.bbttvv.app.data.model.response.VideoItem

private val CardShape = RoundedCornerShape(12.dp)
private val TopCardShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
private val MetaPillShape = RoundedCornerShape(4.dp)
private val OverlayGradientColors = listOf(Color.Transparent, Color(0xCC000000))
private val OverlayGradientBrush = Brush.verticalGradient(colors = OverlayGradientColors)
private const val HomeVideoCardFocusAnimationMs = 90

@Immutable
data class HomeVideoCardUiModel(
    val coverUrl: String,
    val title: String,
    val ownerName: String,
    val ownerFaceUrl: String,
    val pubDateText: String,
    val leadingMetaText: String,
    val durationMetaText: String,
    val compactMeta: Boolean
)

fun VideoItem.toHomeVideoCardUiModel(
    showHistoryProgressOnly: Boolean = false
): HomeVideoCardUiModel {
    val isLiveCard = aid == 0L && cid == 0L && duration <= 0
    val showHistoryFallbackMeta = showHistoryProgressOnly || (!isLiveCard &&
        view_at > 0L &&
        stat.view <= 0 &&
        stat.danmaku <= 0)
    val durationMetaText = if (isLiveCard) "直播中" else formatDuration(duration)
    val leadingMetaText = when {
        showHistoryFallbackMeta -> {
            if (progress > 0 && duration > 0) {
                "已看 ${formatDuration(progress.coerceIn(0, duration))}"
            } else {
                "观看记录"
            }
        }
        isLiveCard -> "在线 ${formatCompactCount(stat.view)}"
        else -> "播放 ${formatCompactCount(stat.view)}  弹幕 ${formatCompactCount(stat.danmaku)}"
    }
    return HomeVideoCardUiModel(
        coverUrl = pic,
        title = title,
        ownerName = owner.name,
        ownerFaceUrl = owner.face,
        pubDateText = formatShortVideoPubDate(pubdate),
        leadingMetaText = leadingMetaText,
        durationMetaText = durationMetaText,
        compactMeta = showHistoryProgressOnly
    )
}

fun List<VideoItem>.toHomeVideoCardUiModels(
    showHistoryProgressOnly: Boolean = false
): List<HomeVideoCardUiModel> {
    if (isEmpty()) return emptyList()
    return map { video -> video.toHomeVideoCardUiModel(showHistoryProgressOnly) }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    reportCoverLoaded: Boolean = false,
    onCoverLoaded: () -> Unit = {},
    modifier: Modifier = Modifier,
    showHistoryProgressOnly: Boolean = false,
    onNotInterested: (() -> Unit)? = null,
    onWatchLater: (() -> Unit)? = null,
) {
    val model = remember(video, showHistoryProgressOnly) {
        video.toHomeVideoCardUiModel(showHistoryProgressOnly)
    }
    HomeVideoCard(
        model = model,
        onClick = onClick,
        onFocus = onFocus,
        reportCoverLoaded = reportCoverLoaded,
        onCoverLoaded = onCoverLoaded,
        modifier = modifier,
        onNotInterested = onNotInterested,
        onWatchLater = onWatchLater
    )
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeVideoCard(
    model: HomeVideoCardUiModel,
    onClick: () -> Unit,
    onFocus: () -> Unit = {},
    reportCoverLoaded: Boolean = false,
    onCoverLoaded: () -> Unit = {},
    modifier: Modifier = Modifier,
    onNotInterested: (() -> Unit)? = null,
    onWatchLater: (() -> Unit)? = null,
) {
    val coverModel = rememberSizedImageModel(
        url = model.coverUrl,
        widthPx = 480,
        heightPx = 270
    )
    val latestOnCoverLoaded by rememberUpdatedState(onCoverLoaded)
    val focusBorderColor = MaterialTheme.colorScheme.primary
    val focusBorderWidthPx = with(LocalDensity.current) { 3.dp.toPx() }
    val focusCornerRadiusPx = with(LocalDensity.current) { 12.dp.toPx() }

    var isFocused by remember { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(durationMillis = HomeVideoCardFocusAnimationMs),
        label = "HomeVideoCardFocus"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val cachedStroke = remember(focusBorderWidthPx) { Stroke(width = focusBorderWidthPx) }
    var showContextMenu by remember { mutableStateOf(false) }
    var suppressContextMenuConfirmKeyUp by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .graphicsLayer {
                val focusedScale = 1f + focusProgress * 0.05f
                scaleX = focusedScale
                scaleY = focusedScale
                clip = true
                shape = CardShape
            }
            .background(surfaceColor)
            .onFocusChanged { focusState ->
                val hasFocus = focusState.hasFocus
                if (hasFocus && !isFocused) {
                    onFocus()
                }
                isFocused = hasFocus
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    if (onNotInterested != null || onWatchLater != null) {
                        suppressContextMenuConfirmKeyUp = true
                        showContextMenu = true
                    }
                }
            )
            .drawWithContent {
                drawContent()
                if (focusProgress > 0f) {
                    drawRoundRect(
                        color = focusBorderColor,
                        cornerRadius = CornerRadius(focusCornerRadiusPx, focusCornerRadiusPx),
                        style = cachedStroke,
                        alpha = focusProgress
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = model.title
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(TopCardShape)
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Low,
                    onState = { state ->
                        if (reportCoverLoaded && state is AsyncImagePainter.State.Success) {
                            latestOnCoverLoaded()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(OverlayGradientBrush)
                ) {
                    Text(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, end = 72.dp, bottom = 6.dp),
                        text = model.leadingMetaText,
                        color = Color.White,
                        fontSize = if (model.compactMeta) 9.sp else 10.sp,
                        maxLines = 1
                    )
                    MetaPill(
                        text = model.durationMetaText,
                        compact = model.compactMeta,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = 6.dp)
                    )
                }
            }

            Text(
                text = model.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 7.dp, end = 8.dp)
                    .height(38.dp)
            )

            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (model.ownerFaceUrl.isNotEmpty()) {
                    val faceModel = rememberSizedImageModel(
                        url = model.ownerFaceUrl,
                        widthPx = 64,
                        heightPx = 64
                    )
                    AsyncImage(
                        model = faceModel,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                    )
                }
                Text(
                    text = model.ownerName,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (model.pubDateText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = model.pubDateText,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }

        if (showContextMenu) {
            TvContextMenu(
                actions = buildList {
                    if (onNotInterested != null) {
                        add(
                            TvContextMenuAction("不感兴趣") {
                                showContextMenu = false
                                onNotInterested()
                            }
                        )
                    }
                    if (onWatchLater != null) {
                        add(
                            TvContextMenuAction("稍后播放") {
                                showContextMenu = false
                                onWatchLater()
                            }
                        )
                    }
                },
                modifier = Modifier.align(Alignment.Center),
                suppressConfirmKey = suppressContextMenuConfirmKeyUp,
                onSuppressConfirmKeyConsumed = { suppressContextMenuConfirmKeyUp = false },
                onDismissRequest = { showContextMenu = false },
            )
        }
    }
}

@Composable
private fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(MetaPillShape)
            .background(Color(0x99000000))
            .padding(
                horizontal = if (compact) 5.dp else 6.dp,
                vertical = if (compact) 1.dp else 2.dp
            )
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (compact) 9.sp else 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

private fun formatCompactCount(count: Int): String {
    return if (count >= 10000) {
        val w = count / 10000
        val r = (count % 10000) / 1000
        if (r == 0) "${w}w" else "$w.${r}w"
    } else {
        count.toString()
    }
}

private fun formatDuration(durationSeconds: Int): String {
    if (durationSeconds <= 0) return "--:--"
    val m = durationSeconds / 60
    val s = durationSeconds % 60
    val sStr = if (s < 10) "0$s" else s.toString()
    return if (m >= 60) {
        val h = m / 60
        val remainM = m % 60
        val mStr = if (remainM < 10) "0$remainM" else remainM.toString()
        "$h:$mStr:$sStr"
    } else {
        val mStr = if (m < 10) "0$m" else m.toString()
        "$mStr:$sStr"
    }
}
