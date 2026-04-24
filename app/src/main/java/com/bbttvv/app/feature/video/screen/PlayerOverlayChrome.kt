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
import androidx.compose.ui.graphics.Color
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
import com.bbttvv.app.core.util.formatLongVideoPubDate
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import kotlinx.coroutines.delay

@Composable
internal fun PlayerSurface(
    exoPlayer: ExoPlayer,
    keepScreenOn: Boolean,
    overlayMode: PlayerOverlayMode,
    onHiddenOverlayKey: (KeyEvent) -> Boolean,
    onViewAvailable: (PlayerView) -> Unit,
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
    val currentPlaybackState = playbackState.value
    Column(
        modifier = modifier
            .widthIn(max = 1080.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiState.info?.title.orEmpty().ifBlank { "正在加载视频..." },
            color = Color.White,
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
                color = Color(0xCCEBCB62),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        PlaybackProgressBlock(
            playbackState = currentPlaybackState,
            sponsorMarkers = sponsorMarkers,
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
                .background(Color.White.copy(alpha = 0.16f)),
        )
        Text(
            text = uiState.info?.owner?.name.orEmpty().ifBlank { "未知 UP" },
            color = Color(0xDDF2F6FA),
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xBEE7EBF0),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            color = Color(0xCFE7EBF0),
            fontSize = 15.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlaybackProgressBlock(
    playbackState: PlayerPlaybackState,
    sponsorMarkers: List<SponsorProgressMark>,
    modifier: Modifier = Modifier,
    positionOverrideMs: Long? = null,
    isFocused: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val positionMs = positionOverrideMs ?: playbackState.positionMs
    val progress = when {
        playbackState.durationMs <= 0L -> 0f
        else -> (positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) Color.White.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White.copy(alpha = 0.42f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SegmentedProgressBar(
            progress = progress,
            marks = sponsorMarkers,
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(positionMs),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = playbackState.durationMs.takeIf { it > 0L }?.let(::formatDuration) ?: "--:--",
                    color = Color(0xD5FFFFFF),
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
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.height / 2f
        val corner = CornerRadius(radius, radius)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset.Zero,
            size = Size(width = size.width * progress.coerceIn(0f, 1f), height = size.height),
            cornerRadius = corner,
        )
        marks.forEach { mark ->
            val start = (size.width * mark.startFraction).coerceIn(0f, size.width)
            val end = (size.width * mark.endFraction).coerceIn(start, size.width)
            val width = (end - start).coerceAtLeast(2f)
            drawRoundRect(
                color = sponsorMarkColor(mark.category),
                topLeft = Offset(start, 0f),
                size = Size(width = width, height = size.height),
                cornerRadius = corner,
            )
        }
    }
}
