@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.core.player.clearPlayerViewReference
import com.bbttvv.app.core.util.formatLongVideoPubDate
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import com.bbttvv.app.feature.video.viewmodel.ProgressHeatmapPoint
import kotlinx.coroutines.delay
import com.bbttvv.app.ui.theme.LocalIsLightTheme

@Composable
internal fun PlayerSurface(
    exoPlayer: ExoPlayer,
    keepScreenOn: Boolean,
    overlayMode: PlayerOverlayMode,
    onHiddenOverlayKey: (KeyEvent) -> Boolean,
    onViewAvailable: (PlayerView) -> Unit,
    onViewReleased: (PlayerView) -> Unit = {},
    onPlayerSurfaceFocusNeeded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val latestOverlayMode = rememberUpdatedState(overlayMode)
    val latestOnHiddenOverlayKey = rememberUpdatedState(onHiddenOverlayKey)
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                player = exoPlayer
                useController = false
                setKeepContentOnPlayerReset(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                this.keepScreenOn = keepScreenOn
                isFocusable = true
                isFocusableInTouchMode = true
                setOnKeyListener { _, _, event ->
                    if (latestOverlayMode.value != PlayerOverlayMode.Hidden) {
                        return@setOnKeyListener false
                    }
                    latestOnHiddenOverlayKey.value(event)
                }
                onViewAvailable(this)
            }
        },
        update = { playerView ->
            onViewAvailable(playerView)
            if (playerView.player !== exoPlayer) {
                playerView.player = exoPlayer
            }
            if (playerView.keepScreenOn != keepScreenOn) {
                playerView.keepScreenOn = keepScreenOn
            }
            if (overlayMode == PlayerOverlayMode.Hidden && !playerView.hasFocus()) {
                onPlayerSurfaceFocusNeeded()
            }
        },
        onRelease = { playerView ->
            clearPlayerViewReference(playerView)
            onViewReleased(playerView)
        },
        modifier = modifier,
    )
}

@Composable
internal fun rememberRealtimePlaybackState(
    exoPlayer: ExoPlayer,
    playbackState: PlayerPlaybackState,
    enabled: Boolean,
): State<PlayerPlaybackState> {
    return produceState(
        initialValue = playbackState,
        key1 = exoPlayer,
        key2 = enabled,
        key3 = playbackState,
    ) {
        value = playbackState
        if (!enabled) return@produceState
        while (true) {
            val next = PlayerPlaybackState(
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = exoPlayer.duration.takeIf { it > 0L } ?: playbackState.durationMs,
                isPlaying = exoPlayer.isPlaying,
                playWhenReady = exoPlayer.playWhenReady,
                isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                playerState = exoPlayer.playbackState,
            )
            if (value != next) {
                value = next
            }
            delay(500L)
        }
    }
}

@Composable
internal fun PlayerInfoOverlay(
    uiState: PlayerUiState,
    playbackState: State<PlayerPlaybackState>,
    sponsorMarkers: List<SponsorProgressMark>,
    isProgressFocused: Boolean,
    progressPreviewState: SimpleSeekState? = null,
    progressModifier: Modifier = Modifier,
    actionBar: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = LocalIsLightTheme.current
    val currentPlaybackState = playbackState.value
    Column(
        modifier = modifier
            .widthIn(max = 1080.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiState.info?.title.orEmpty().ifBlank { "正在加载视频..." },
            color = if (isLightTheme) Color(0xFF18191C) else Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 34.sp,
        )

        if (actionBar != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataRow(
                    uiState = uiState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 32.dp),
                )
                actionBar()
            }
        } else {
            MetadataRow(uiState = uiState)
        }

        if (uiState.capabilityHints.isNotEmpty()) {
            Text(
                text = uiState.capabilityHints.joinToString("  "),
                color = if (isLightTheme) Color(0xFFC04F00) else Color(0xCCEBCB62),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        PlaybackProgressBlock(
            playbackState = currentPlaybackState,
            sponsorMarkers = sponsorMarkers,
            heatmapPoints = uiState.heatmapPoints,
            modifier = progressModifier,
            positionOverrideMs = progressPreviewState?.targetPositionMs,
            isFocused = isProgressFocused,
        )
    }
}

@Composable
private fun MetadataRow(
    uiState: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = LocalIsLightTheme.current
    val pubDateText = remember(uiState.info?.pubdate) {
        formatLongVideoPubDate(uiState.info?.pubdate ?: 0L)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            model = uiState.info?.owner?.face.orEmpty(),
            contentDescription = uiState.info?.owner?.name.orEmpty(),
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isLightTheme) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.16f)),
        )
        Text(
            text = uiState.info?.owner?.name.orEmpty().ifBlank { "未知 UP" },
            color = if (isLightTheme) Color(0xFF404040) else Color(0xDDF2F6FA),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        MetricItem(icon = Icons.Filled.PlayArrow, text = formatCount(uiState.info?.stat?.view ?: 0))
        MetricItem(icon = Icons.Outlined.ThumbUp, text = formatCount(uiState.info?.stat?.danmaku ?: 0))
        MetricItem(icon = Icons.Outlined.DateRange, text = pubDateText)
    }
}

@Composable
private fun MetricItem(
    icon: ImageVector,
    text: String,
) {
    val isLightTheme = LocalIsLightTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isLightTheme) Color(0xFF61666D) else Color(0xBEE7EBF0),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            color = if (isLightTheme) Color(0xFF61666D) else Color(0xCFE7EBF0),
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlaybackProgressBlock(
    playbackState: PlayerPlaybackState,
    sponsorMarkers: List<SponsorProgressMark>,
    heatmapPoints: List<ProgressHeatmapPoint>,
    modifier: Modifier = Modifier,
    positionOverrideMs: Long? = null,
    isFocused: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val isLightTheme = LocalIsLightTheme.current
    val positionMs = positionOverrideMs ?: playbackState.positionMs
    val progress = when {
        playbackState.durationMs <= 0L -> 0f
        else -> (positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    val blockBackground = if (isFocused) {
        if (isLightTheme) Color.Black.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }

    val blockBorderColor = if (isFocused) {
        if (isLightTheme) Color(0xFFFB7299).copy(alpha = 0.42f) else Color.White.copy(alpha = 0.42f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(blockBackground)
            .border(
                width = 1.dp,
                color = blockBorderColor,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedProgressBar(
            progress = progress,
            marks = sponsorMarkers,
            heatmapPoints = heatmapPoints,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (heatmapPoints.isEmpty()) 7.dp else 24.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(positionMs),
                color = if (isLightTheme) Color(0xFF18191C) else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = playbackState.durationMs.takeIf { it > 0L }?.let(::formatDuration) ?: "--:--",
                    color = if (isLightTheme) Color(0xFF61666D) else Color(0xD5FFFFFF),
                    fontSize = 13.sp,
                )
                trailingContent()
            }
        }
    }
}

@Composable
private fun SegmentedProgressBar(
    progress: Float,
    marks: List<SponsorProgressMark>,
    heatmapPoints: List<ProgressHeatmapPoint>,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = LocalIsLightTheme.current
    Canvas(modifier = modifier) {
        val hasHeatmap = heatmapPoints.isNotEmpty()
        val progressHeight = if (hasHeatmap) {
            7.dp.toPx().coerceAtMost(size.height)
        } else {
            size.height
        }
        val progressTop = if (hasHeatmap) {
            (size.height - progressHeight).coerceAtLeast(0f)
        } else {
            0f
        }
        if (hasHeatmap) {
            val heatmapGap = 2.dp.toPx().coerceAtMost(progressTop)
            val heatmapBottom = (progressTop - heatmapGap).coerceAtLeast(0f)
            val heatmapHeight = heatmapBottom.coerceAtLeast(0f)
            if (heatmapHeight > 0f && size.width > 0f) {
                val sampleCount = (size.width / 7.dp.toPx())
                    .toInt()
                    .coerceIn(48, 180)
                val curvePoints = ArrayList<Offset>(sampleCount)
                var sourceIndex = 0

                repeat(sampleCount) { sampleIndex ->
                    val fraction = if (sampleCount <= 1) {
                        0f
                    } else {
                        sampleIndex.toFloat() / (sampleCount - 1).toFloat()
                    }
                    while (
                        sourceIndex < heatmapPoints.lastIndex &&
                        heatmapPoints[sourceIndex + 1].fraction < fraction
                    ) {
                        sourceIndex += 1
                    }

                    val left = heatmapPoints[sourceIndex]
                    val right = heatmapPoints.getOrNull(sourceIndex + 1) ?: left
                    val span = (right.fraction - left.fraction).takeIf { it > 0f } ?: 1f
                    val t = ((fraction - left.fraction) / span).coerceIn(0f, 1f)
                    val intensity = (left.intensity + (right.intensity - left.intensity) * t)
                        .coerceIn(0f, 1f)
                    curvePoints.add(
                        Offset(
                            x = size.width * fraction,
                            y = heatmapBottom - heatmapHeight * intensity,
                        )
                    )
                }

                val fillPath = Path().apply {
                    moveTo(0f, heatmapBottom)
                    val first = curvePoints.first()
                    lineTo(first.x, first.y)
                    for (index in 1 until curvePoints.size) {
                        val previous = curvePoints[index - 1]
                        val current = curvePoints[index]
                        quadraticTo(
                            previous.x,
                            previous.y,
                            (previous.x + current.x) / 2f,
                            (previous.y + current.y) / 2f,
                        )
                    }
                    val last = curvePoints.last()
                    lineTo(last.x, last.y)
                    lineTo(size.width, heatmapBottom)
                    close()
                }
                val strokePath = Path().apply {
                    val first = curvePoints.first()
                    moveTo(first.x, first.y)
                    for (index in 1 until curvePoints.size) {
                        val previous = curvePoints[index - 1]
                        val current = curvePoints[index]
                        quadraticTo(
                            previous.x,
                            previous.y,
                            (previous.x + current.x) / 2f,
                            (previous.y + current.y) / 2f,
                        )
                    }
                    val last = curvePoints.last()
                    lineTo(last.x, last.y)
                }

                val fillColors = if (isLightTheme) {
                    listOf(
                        Color(0xFFFB7299).copy(alpha = 0.36f),
                        Color(0xFFFB7299).copy(alpha = 0.08f),
                        Color.Transparent,
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.36f),
                        Color(0xFFBFE9FF).copy(alpha = 0.11f),
                        Color.Transparent,
                    )
                }

                val strokeColor = if (isLightTheme) {
                    Color(0xFFFB7299).copy(alpha = 0.62f)
                } else {
                    Color.White.copy(alpha = 0.62f)
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = fillColors,
                        startY = 0f,
                        endY = heatmapBottom,
                    ),
                )
                drawPath(
                    path = strokePath,
                    color = strokeColor,
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        val radius = progressHeight / 2f
        val corner = CornerRadius(radius, radius)
        val trackColor = if (isLightTheme) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.16f)
        val progressColor = if (isLightTheme) Color(0xFFFB7299) else Color.White.copy(alpha = 0.92f)

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, progressTop),
            size = Size(width = size.width, height = progressHeight),
            cornerRadius = corner,
        )
        if (isLightTheme) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.05f),
                topLeft = Offset(0f, progressTop),
                size = Size(width = size.width, height = progressHeight),
                cornerRadius = corner,
                style = Stroke(width = 0.5.dp.toPx())
            )
        }
        drawRoundRect(
            color = progressColor,
            topLeft = Offset(0f, progressTop),
            size = Size(width = size.width * progress.coerceIn(0f, 1f), height = progressHeight),
            cornerRadius = corner,
        )
        marks.forEach { mark ->
            val start = (size.width * mark.startFraction).coerceIn(0f, size.width)
            val end = (size.width * mark.endFraction).coerceIn(start, size.width)
            val width = (end - start).coerceAtLeast(2f)
            drawRoundRect(
                color = sponsorMarkColor(mark.category),
                topLeft = Offset(start, progressTop),
                size = Size(width = width, height = progressHeight),
                cornerRadius = corner,
            )
        }
    }
}
