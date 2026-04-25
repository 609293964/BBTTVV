package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bbttvv.app.core.player.BufferingSpeedMeter
import com.bbttvv.app.data.model.response.SponsorBlockMarkerMode
import com.bbttvv.app.data.model.response.SponsorCategory
import com.bbttvv.app.data.model.response.SponsorSegment
import com.bbttvv.app.feature.plugin.SponsorBlockConfig
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import java.util.Locale
import kotlinx.coroutines.delay

private const val SPONSOR_PROMPT_BUFFERING_DELAY_MS = 1_000L
private const val SPONSOR_PROMPT_BUFFERING_REFRESH_MS = 500L

internal data class SponsorProgressMark(
    val startFraction: Float,
    val endFraction: Float,
    val category: String,
)

internal fun buildSponsorProgressMarks(
    segments: List<SponsorSegment>,
    durationMs: Long,
    enabled: Boolean,
    config: SponsorBlockConfig,
): List<SponsorProgressMark> {
    if (!enabled || durationMs <= 0L || config.markerMode == SponsorBlockMarkerMode.OFF) {
        return emptyList()
    }
    return segments.mapNotNull { segment ->
        if (!segment.isSkipType || !config.isCategoryEnabled(segment.category)) return@mapNotNull null
        if (config.markerMode == SponsorBlockMarkerMode.SPONSOR_ONLY && segment.category != SponsorCategory.SPONSOR) {
            return@mapNotNull null
        }
        val start = segment.startTimeMs.coerceAtLeast(0L)
        val end = segment.endTimeMs.coerceAtLeast(0L)
        if (end <= start) return@mapNotNull null
        val startFraction = (start.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val endFraction = (end.toFloat() / durationMs.toFloat()).coerceIn(startFraction, 1f)
        SponsorProgressMark(
            startFraction = startFraction,
            endFraction = endFraction,
            category = segment.category,
        )
    }
}

@Composable
internal fun rememberBufferingOverlayText(
    isBuffering: Boolean,
    speedMeter: BufferingSpeedMeter,
): State<String?> {
    return produceState<String?>(
        initialValue = null,
        key1 = isBuffering,
        key2 = speedMeter,
    ) {
        value = null
        if (!isBuffering) return@produceState

        speedMeter.reset()
        delay(SPONSOR_PROMPT_BUFFERING_DELAY_MS)

        while (true) {
            val bytesPerSecond = speedMeter.bytesPerSecond()
            value = if (bytesPerSecond > 0L) {
                "正在缓冲 ${formatTransferSpeed(bytesPerSecond)}"
            } else {
                "正在缓冲..."
            }
            delay(SPONSOR_PROMPT_BUFFERING_REFRESH_MS)
        }
    }
}

@Composable
internal fun BoxScope.PlayerTransientOverlayMessages(
    uiState: PlayerUiState,
    bufferingOverlayText: String?,
    showDebugOverlay: Boolean,
    showSponsorSkipNotice: Boolean,
) {
    if (uiState.isLoading) {
        OverlayMessage(
            text = "正在加载播放信息...",
            modifier = Modifier.align(Alignment.Center),
        )
    }

    bufferingOverlayText?.let { text ->
        OverlayMessage(
            text = text,
            modifier = Modifier.align(Alignment.Center),
        )
    }

    if (!uiState.errorMessage.isNullOrBlank()) {
        OverlayMessage(
            text = uiState.errorMessage.orEmpty(),
            containerColor = Color(0xCC7F1D1D),
            modifier = Modifier.align(Alignment.Center),
        )
    }

    if (!uiState.statusMessage.isNullOrBlank()) {
        OverlayMessage(
            text = uiState.statusMessage.orEmpty(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = if (showDebugOverlay) 210.dp else PlayerLayoutTokens.statusMessageTopPadding,
                    end = PlayerLayoutTokens.debugEdgePadding,
                ),
        )
    }

    if (showSponsorSkipNotice) {
        OverlayMessage(
            text = "检测到可跳过片段，按确认键可跳过",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = PlayerLayoutTokens.sponsorNoticeBottomPadding,
                    bottom = PlayerLayoutTokens.sponsorNoticeBottomPadding,
                ),
        )
    }
}

@Composable
private fun OverlayMessage(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Black.copy(alpha = 0.64f),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}

private fun formatTransferSpeed(bytesPerSecond: Long): String {
    val kilobyte = 1024L
    val megabyte = kilobyte * 1024L
    return when {
        bytesPerSecond >= megabyte -> String.format(
            Locale.US,
            "%.1f MB/s",
            bytesPerSecond.toDouble() / megabyte.toDouble(),
        )
        bytesPerSecond >= kilobyte -> String.format(
            Locale.US,
            "%.0f KB/s",
            bytesPerSecond.toDouble() / kilobyte.toDouble(),
        )
        else -> "$bytesPerSecond B/s"
    }
}
