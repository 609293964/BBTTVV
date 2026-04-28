package com.bbttvv.app.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.tv.material3.Text
import com.bbttvv.app.feature.video.screen.PanelOption
import com.bbttvv.app.feature.video.screen.PanelOptionPresentation

internal enum class LiveOverlayAction(
    val title: String,
    val symbol: String,
) {
    Danmaku("弹幕开关", "弹"),
    Quality("清晰度", "清"),
    Line("线路选择", "线"),
    Bitrate("直播码率", "码"),
    AudioBalance("音频平衡", "声"),
    PlayerCore("播放器内核", "核"),
}

@Composable
internal fun LiveActionBar(
    actions: List<LiveOverlayAction>,
    selectedIndex: Int,
    hasActivePanel: Boolean,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEachIndexed { index, action ->
            val selected = index == selectedIndex && !hasActivePanel
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = action.symbol,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = action.title,
                    color = if (selected) Color.Black.copy(alpha = 0.78f) else Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun LiveOptionsPanel(
    title: String,
    options: List<PanelOption>,
    selectedIndex: Int,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 360.dp, max = 460.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(options) { index, option ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = option.label,
                        color = if (selected) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    option.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            text = subtitle,
                            color = if (selected) Color.Black.copy(alpha = 0.66f) else Color.White.copy(alpha = 0.62f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

internal fun buildLivePanelOptions(
    activePanel: LiveOverlayAction?,
    uiState: LivePlayerUiState,
): List<PanelOption> {
    return when (activePanel) {
        LiveOverlayAction.Quality -> uiState.qualityOptions.map { option ->
            PanelOption(
                key = option.qn.toString(),
                label = option.label,
                isSelected = option.qn == uiState.selectedQuality,
            )
        }
        LiveOverlayAction.Line -> uiState.lineOptions.map { option ->
            PanelOption(
                key = option.key,
                label = option.label,
                subtitle = option.subtitle,
                isSelected = option.key == uiState.selectedLineKey,
            )
        }
        LiveOverlayAction.Bitrate -> LiveBitrateBoostMode.entries.map { mode ->
            PanelOption(
                key = mode.key,
                label = mode.label,
                subtitle = if (mode.isEnabled) {
                    "优先更激进的直播线路和格式组合。"
                } else {
                    "优先更稳的默认播放组合。"
                },
                isSelected = mode.key == uiState.bitrateModeKey,
                presentation = PanelOptionPresentation.Setting,
            )
        }
        LiveOverlayAction.AudioBalance -> LiveAudioBalanceMode.entries.map { mode ->
            PanelOption(
                key = mode.key,
                label = mode.label,
                subtitle = "适合单侧扬声器或声像偏移场景。",
                isSelected = mode.key == uiState.audioBalanceKey,
                presentation = PanelOptionPresentation.Setting,
            )
        }
        LiveOverlayAction.PlayerCore -> LIVE_PLAYER_CORE_OPTIONS.map { option ->
            PanelOption(
                key = option.key,
                label = option.label,
                subtitle = option.subtitle,
                isSelected = option.key == uiState.playerCoreKey,
                presentation = PanelOptionPresentation.Setting,
            )
        }
        LiveOverlayAction.Danmaku,
        null -> emptyList()
    }
}

internal fun panelTitleFor(action: LiveOverlayAction): String {
    return when (action) {
        LiveOverlayAction.Danmaku -> "弹幕开关"
        LiveOverlayAction.Quality -> "清晰度"
        LiveOverlayAction.Line -> "线路选择"
        LiveOverlayAction.Bitrate -> "直播码率"
        LiveOverlayAction.AudioBalance -> "音频平衡"
        LiveOverlayAction.PlayerCore -> "播放器内核"
    }
}
