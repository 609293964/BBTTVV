package com.bbttvv.app.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

internal data class LivePlaybackDebugMetrics(
    val videoDecoderName: String = "",
    val audioDecoderName: String = "",
    val videoInputWidth: Int = 0,
    val videoInputHeight: Int = 0,
    val videoInputFps: Float = 0f,
    val videoBitrateBps: Int = 0,
    val audioBitrateBps: Int = 0,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val firstFrameRendered: Boolean = false,
    val droppedFramesTotal: Long = 0L,
    val bandwidthEstimateBps: Long = 0L,
    val rebufferCount: Int = 0,
    val lastPlaybackState: Int = Player.STATE_IDLE,
    val renderFps: Float = 0f,
    val renderFpsLastAtMs: Long = 0L,
    val lastVideoEvent: String = "",
    val lastAudioEvent: String = "",
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun rememberLivePlaybackDebugMetrics(
    player: ExoPlayer,
    roomId: Long,
    streamUrl: String,
    isDebugOverlayVisible: Boolean,
): LivePlaybackDebugMetrics {
    var metrics by remember(roomId) { mutableStateOf(LivePlaybackDebugMetrics()) }
    val latestShowDebugOverlay = rememberUpdatedState(isDebugOverlayVisible)

    LaunchedEffect(streamUrl) {
        if (streamUrl.isNotBlank()) {
            metrics = LivePlaybackDebugMetrics(lastPlaybackState = player.playbackState)
        }
    }

    LaunchedEffect(isDebugOverlayVisible) {
        if (isDebugOverlayVisible) {
            metrics = metrics.copy(renderFps = 0f, renderFpsLastAtMs = 0L)
        }
    }

    DisposableEffect(player) {
        fun update(transform: (LivePlaybackDebugMetrics) -> LivePlaybackDebugMetrics) {
            metrics = transform(metrics)
        }

        val analyticsListener = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                update { current ->
                    current.copy(
                        videoInputWidth = format.width.takeIf { it > 0 } ?: current.videoInputWidth,
                        videoInputHeight = format.height.takeIf { it > 0 } ?: current.videoInputHeight,
                        videoInputFps = format.frameRate.takeIf { it > 0f } ?: current.videoInputFps,
                        videoBitrateBps = resolveFormatBitrate(format).takeIf { it > 0 } ?: current.videoBitrateBps,
                        videoCodec = resolveDebugCodecLabel(format).ifBlank { current.videoCodec },
                        lastVideoEvent = "video format changed",
                    )
                }
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                update { current ->
                    current.copy(
                        audioBitrateBps = resolveFormatBitrate(format).takeIf { it > 0 } ?: current.audioBitrateBps,
                        audioCodec = resolveDebugCodecLabel(format).ifBlank { current.audioCodec },
                        lastAudioEvent = "audio format changed",
                    )
                }
            }

            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                update { current ->
                    current.copy(
                        videoDecoderName = decoderName,
                        lastVideoEvent = "video decoder initialized",
                    )
                }
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                update { current ->
                    current.copy(
                        audioDecoderName = decoderName,
                        lastAudioEvent = "audio decoder initialized",
                    )
                }
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                if (droppedFrames <= 0) return
                update { current ->
                    current.copy(
                        droppedFramesTotal = current.droppedFramesTotal + droppedFrames.toLong(),
                        lastVideoEvent = "dropped $droppedFrames frames",
                    )
                }
            }

            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long,
            ) {
                if (bitrateEstimate <= 0L) return
                update { current -> current.copy(bandwidthEstimateBps = bitrateEstimate) }
            }

            override fun onVideoFrameProcessingOffset(
                eventTime: AnalyticsListener.EventTime,
                totalProcessingOffsetUs: Long,
                frameCount: Int,
            ) {
                if (!latestShowDebugOverlay.value) return
                val now = eventTime.realtimeMs
                update { current ->
                    val last = current.renderFpsLastAtMs
                    if (last <= 0L) {
                        return@update current.copy(renderFpsLastAtMs = now)
                    }
                    val deltaMs = now - last
                    if (deltaMs <= 0L || deltaMs > 60_000L || frameCount <= 0) {
                        return@update current.copy(renderFpsLastAtMs = now)
                    }
                    current.copy(
                        renderFps = frameCount * 1000f / deltaMs.toFloat(),
                        renderFpsLastAtMs = now,
                    )
                }
            }

            override fun onVideoCodecError(
                eventTime: AnalyticsListener.EventTime,
                videoCodecError: Exception,
            ) {
                update { current -> current.copy(lastVideoEvent = "video codec error") }
            }

            override fun onAudioCodecError(
                eventTime: AnalyticsListener.EventTime,
                audioCodecError: Exception,
            ) {
                update { current -> current.copy(lastAudioEvent = "audio codec error") }
            }
        }

        val playerListener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width <= 0 || videoSize.height <= 0) return
                update { current ->
                    current.copy(
                        videoInputWidth = videoSize.width,
                        videoInputHeight = videoSize.height,
                    )
                }
            }

            override fun onRenderedFirstFrame() {
                update { current ->
                    current.copy(
                        firstFrameRendered = true,
                        lastVideoEvent = "first frame rendered",
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                update { current ->
                    val rebufferCount = if (
                        playbackState == Player.STATE_BUFFERING &&
                        current.lastPlaybackState != Player.STATE_BUFFERING &&
                        player.playWhenReady
                    ) {
                        current.rebufferCount + 1
                    } else {
                        current.rebufferCount
                    }
                    current.copy(
                        rebufferCount = rebufferCount,
                        lastPlaybackState = playbackState,
                    )
                }
            }
        }

        player.addAnalyticsListener(analyticsListener)
        player.addListener(playerListener)
        onDispose {
            player.removeAnalyticsListener(analyticsListener)
            player.removeListener(playerListener)
        }
    }

    return metrics
}
