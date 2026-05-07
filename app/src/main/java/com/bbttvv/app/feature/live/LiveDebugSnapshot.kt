package com.bbttvv.app.feature.live

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class LiveDebugSnapshot(
    val room: String = "--",
    val source: String = "--",
    val resolution: String = "--",
    val quality: String = "--",
    val route: String = "--",
    val line: String = "--",
    val frameRate: String = "--",
    val videoBitrate: String = "--",
    val audioBitrate: String = "--",
    val videoCodec: String = "--",
    val audioCodec: String = "--",
    val videoDecoder: String = "--",
    val audioDecoder: String = "--",
    val frames: String = "--",
    val bandwidth: String = "--",
    val bufferHealth: String = "--",
    val playbackState: String = "--",
    val playWhenReady: String = "--",
    val isPlaying: String = "--",
    val firstFrame: String = "--",
    val events: String = "--",
    val date: String = "--",
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun buildLiveDebugSnapshot(
    player: ExoPlayer,
    uiState: LivePlayerUiState,
    playbackState: LivePlayerPlaybackState,
    metrics: LivePlaybackDebugMetrics,
): LiveDebugSnapshot {
    val videoSize = player.videoSize
    val videoFormat = player.videoFormat
    val audioFormat = player.audioFormat
    val width = videoSize.width.takeIf { it > 0 }
        ?: metrics.videoInputWidth.takeIf { it > 0 }
        ?: videoFormat?.width?.takeIf { it > 0 }
        ?: 0
    val height = videoSize.height.takeIf { it > 0 }
        ?: metrics.videoInputHeight.takeIf { it > 0 }
        ?: videoFormat?.height?.takeIf { it > 0 }
        ?: 0
    val resolution = if (width > 0 && height > 0) {
        "${width}x${height}"
    } else {
        "--"
    }
    val bufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
    val counters = player.videoDecoderCounters
    counters?.ensureUpdated()
    val renderedFrames = counters?.renderedOutputBufferCount ?: 0
    val droppedFrames = maxOf(metrics.droppedFramesTotal, counters?.droppedBufferCount?.toLong() ?: 0L)
    val videoBitrate = metrics.videoBitrateBps.takeIf { it > 0 }
        ?: videoFormat?.let(::resolveFormatBitrate)?.takeIf { it > 0 }
        ?: 0
    val audioBitrate = metrics.audioBitrateBps.takeIf { it > 0 }
        ?: audioFormat?.let(::resolveFormatBitrate)?.takeIf { it > 0 }
        ?: 0
    val frameRate = metrics.renderFps.takeIf { it > 0f }
        ?: metrics.videoInputFps.takeIf { it > 0f }
        ?: videoFormat?.frameRate?.takeIf { it > 0f }
        ?: 0f
    val quality = listOfNotNull(
        uiState.selectedQualityLabel.takeIf { it.isNotBlank() },
        uiState.bitrateModeLabel.takeIf { it.isNotBlank() }
    ).joinToString(" / ").ifBlank { "--" }
    val route = listOfNotNull(
        uiState.streamProtocolLabel.takeIf { it.isNotBlank() },
        uiState.streamFormatLabel.takeIf { it.isNotBlank() },
        uiState.streamCodecLabel.takeIf { it.isNotBlank() }
    ).joinToString(" / ").ifBlank { "--" }
    val line = listOfNotNull(
        uiState.selectedLineLabel.takeIf { it.isNotBlank() },
        uiState.streamHostLabel.takeIf { it.isNotBlank() }
    ).joinToString(" / ").ifBlank { "--" }
    val source = buildLiveDebugSource(uiState)
    val lastEvents = listOfNotNull(
        metrics.lastVideoEvent.takeIf { it.isNotBlank() }?.let { "v=$it" },
        metrics.lastAudioEvent.takeIf { it.isNotBlank() }?.let { "a=$it" },
    ).joinToString(" / ").ifBlank { "--" }

    return LiveDebugSnapshot(
        room = listOfNotNull(
            uiState.realRoomId.takeIf { it > 0L }?.let { "room=$it" },
            uiState.anchorName.takeIf { it.isNotBlank() }
        ).joinToString(" / ").ifBlank { "--" },
        source = source,
        resolution = resolution,
        frameRate = formatDebugFps(frameRate),
        quality = quality,
        route = route,
        line = line,
        videoBitrate = formatDebugBitrate(videoBitrate.toLong()),
        audioBitrate = formatDebugBitrate(audioBitrate.toLong()),
        videoCodec = metrics.videoCodec.ifBlank { resolveDebugCodecLabel(videoFormat) }.ifBlank { "--" },
        audioCodec = metrics.audioCodec.ifBlank { resolveDebugCodecLabel(audioFormat) }.ifBlank { "--" },
        videoDecoder = metrics.videoDecoderName.ifBlank { "--" },
        audioDecoder = metrics.audioDecoderName.ifBlank { "--" },
        frames = "rendered $renderedFrames / dropped $droppedFrames / rebuffer ${metrics.rebufferCount}",
        bandwidth = formatDebugBitrate(metrics.bandwidthEstimateBps),
        bufferHealth = formatDebugSeconds(bufferMs),
        playbackState = "${formatPlayerState(player.playbackState)} / pos=${formatDuration(playbackState.positionMs)} / speed=${formatSpeed(player.playbackParameters.speed)}",
        playWhenReady = player.playWhenReady.toString(),
        isPlaying = player.isPlaying.toString(),
        firstFrame = if (metrics.firstFrameRendered) "rendered" else "--",
        events = lastEvents,
        date = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date()),
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun resolveFormatBitrate(format: Format): Int {
    return format.averageBitrate.takeIf { it > 0 }
        ?: format.bitrate.takeIf { it > 0 }
        ?: format.peakBitrate.takeIf { it > 0 }
        ?: 0
}

internal fun resolveDebugCodecLabel(format: Format?): String {
    if (format == null) return ""
    return resolveDebugCodecLabel(format.sampleMimeType)
        .ifBlank { resolveDebugCodecLabel(format.codecs) }
}

private fun buildLiveDebugSource(uiState: LivePlayerUiState): String {
    val host = uiState.streamHostLabel.takeIf { it.isNotBlank() }
        ?: extractHostForDebug(uiState.streamUrl)
    return listOfNotNull(
        uiState.streamProtocolLabel.takeIf { it.isNotBlank() },
        uiState.streamFormatLabel.takeIf { it.isNotBlank() },
        host.takeIf { it.isNotBlank() },
    ).joinToString(" / ").ifBlank { "--" }
}

private fun extractHostForDebug(url: String): String {
    val withoutScheme = url.substringAfter("://", missingDelimiterValue = url)
    return withoutScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .ifBlank { "" }
}

private fun resolveDebugCodecLabel(rawValue: String?): String {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return ""
    val normalized = raw.lowercase(Locale.US)
    return when {
        normalized.contains("hevc") || normalized.contains("h265") || normalized.contains("hvc1") -> "HEVC"
        normalized.contains("avc") || normalized.contains("h264") -> "H.264"
        normalized.contains("av01") || normalized.contains("av1") -> "AV1"
        normalized.contains("vp9") -> "VP9"
        normalized.contains("mp4a") || normalized.contains("aac") -> "AAC"
        normalized.contains("opus") -> "Opus"
        normalized.contains("flac") -> "FLAC"
        normalized.contains("eac3") -> "E-AC-3"
        normalized.contains("ac3") -> "AC-3"
        else -> raw.substringAfter('/').uppercase(Locale.US)
    }
}

private fun formatDebugFps(fps: Float): String {
    if (fps <= 0f) return "--"
    return if (fps >= 10f) {
        String.format(Locale.US, "%.1f fps", fps)
    } else {
        String.format(Locale.US, "%.2f fps", fps)
    }
}

private fun formatDebugBitrate(bitrateBps: Long): String {
    if (bitrateBps <= 0L) return "--"
    return if (bitrateBps >= 1_000_000L) {
        String.format(Locale.US, "%.2f Mbps", bitrateBps / 1_000_000f)
    } else {
        String.format(Locale.US, "%.0f kbps", bitrateBps / 1_000f)
    }
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

private fun formatSpeed(speed: Float): String {
    return String.format(Locale.US, "%.2fx", speed)
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
