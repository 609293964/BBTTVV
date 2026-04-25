package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class PlayerDebugSnapshot(
    val videoId: String = "--",
    val viewport: String = "--",
    val frames: String = "--",
    val currentOptimal: String = "--",
    val bufferHealth: String = "--",
    val codecs: String = "--",
    val quality: String = "--",
    val trackBitrate: String = "--",
    val cdn: String = "--",
    val playback: String = "--",
    val date: String = "--",
)

@Composable
internal fun PlayerDebugOverlay(
    snapshot: PlayerDebugSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 420.dp, max = 520.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DebugInfoLine("Video ID / CID", snapshot.videoId)
        DebugInfoLine("Viewport / Frames", "${snapshot.viewport} / ${snapshot.frames}")
        DebugInfoLine("Current / Optimal Res", snapshot.currentOptimal)
        DebugInfoLine("Buffer Health", snapshot.bufferHealth)
        DebugInfoLine("Codecs", snapshot.codecs)
        DebugInfoLine("Quality", snapshot.quality)
        DebugInfoLine("Track Bitrate", snapshot.trackBitrate)
        DebugInfoLine("CDN", snapshot.cdn)
        DebugInfoLine("Playback", snapshot.playback)
        DebugInfoLine("Date", snapshot.date)
    }
}

@Composable
private fun DebugInfoLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(126.dp),
            maxLines = 1,
        )
        Text(
            text = value.ifBlank { "--" },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun buildPlayerDebugSnapshot(
    player: ExoPlayer,
    uiState: PlayerUiState,
    playbackState: PlayerPlaybackState,
): PlayerDebugSnapshot {
    val videoSize = player.videoSize
    val width = uiState.selectedVideoWidth.takeIf { it > 0 } ?: videoSize.width
    val height = uiState.selectedVideoHeight.takeIf { it > 0 } ?: videoSize.height
    val resolution = if (width > 0 && height > 0) "${width}x${height}" else "--"
    val frameRate = uiState.selectedVideoFrameRate.takeIf { it.isNotBlank() }
    val optimal = buildList {
        add(resolution)
        frameRate?.let { add(it) }
    }.joinToString(" ")
    val counters = player.videoDecoderCounters
    counters?.ensureUpdated()
    val droppedFrames = counters?.droppedBufferCount ?: 0
    val renderedFrames = counters?.renderedOutputBufferCount ?: 0
    val bufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
    val totalBitrate = uiState.selectedVideoBandwidth.coerceAtLeast(0) +
        uiState.selectedAudioBandwidth.coerceAtLeast(0)
    val playbackLabel = "${formatPlayerState(player.playbackState)} / " +
        "playing=${player.isPlaying} / speed=${formatPlaybackSpeedLabel(player.playbackParameters.speed)}"

    return PlayerDebugSnapshot(
        videoId = listOfNotNull(
            uiState.info?.bvid?.takeIf { it.isNotBlank() },
            uiState.info?.cid?.takeIf { it > 0L }?.let { "cid=$it" }
        ).joinToString(" / ").ifBlank { "--" },
        viewport = resolution,
        frames = "rendered $renderedFrames / dropped $droppedFrames",
        currentOptimal = "${formatDuration(playbackState.positionMs)} / $optimal",
        bufferHealth = formatDebugSeconds(bufferMs),
        codecs = buildDebugCodecs(uiState),
        quality = uiState.selectedQualityLabel.ifBlank { uiState.selectedQuality.toString() },
        trackBitrate = if (totalBitrate > 0) "${totalBitrate / 1000} Kbps" else "--",
        cdn = buildDebugCdn(uiState),
        playback = playbackLabel,
        date = SimpleDateFormat("MMM d yyyy HH:mm:ss", Locale.US).format(Date()),
    )
}

private fun buildDebugCodecs(uiState: PlayerUiState): String {
    return buildList {
        uiState.selectedVideoCodecLabel.takeIf { it.isNotBlank() }?.let { add("video=$it") }
        uiState.selectedAudioCodecLabel.takeIf { it.isNotBlank() }?.let { add("audio=$it") }
    }.joinToString(" / ").ifBlank { "--" }
}

private fun buildDebugCdn(uiState: PlayerUiState): String {
    return buildList {
        uiState.videoCdnHost.takeIf { it.isNotBlank() }?.let { add("v=$it") }
        uiState.audioCdnHost.takeIf { it.isNotBlank() }?.let { add("a=$it") }
    }.joinToString(" / ").ifBlank { "--" }
}

private fun formatDebugSeconds(durationMs: Long): String {
    return String.format(Locale.US, "%.2f s", durationMs.coerceAtLeast(0L) / 1000f)
}

private fun formatPlayerState(state: Int): String {
    return when (state) {
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        Player.STATE_IDLE -> "idle"
        else -> "unknown"
    }
}
