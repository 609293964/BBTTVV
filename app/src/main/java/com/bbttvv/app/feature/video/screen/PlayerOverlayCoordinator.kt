package com.bbttvv.app.feature.video.screen

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bbttvv.app.core.store.player.DANMAKU_AREA_RATIO_VALUES
import com.bbttvv.app.core.store.player.DANMAKU_OPACITY_VALUES
import com.bbttvv.app.core.store.player.DANMAKU_STROKE_WIDTH_VALUES
import com.bbttvv.app.core.store.player.DANMAKU_TEXT_SIZE_VALUES
import com.bbttvv.app.core.store.player.DanmakuFontWeightPreset
import com.bbttvv.app.core.store.player.DanmakuLaneDensityPreset
import com.bbttvv.app.core.store.player.DanmakuSettings
import com.bbttvv.app.core.store.player.DanmakuSettingsStore
import com.bbttvv.app.feature.video.viewmodel.PlayerOption
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerViewModel
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val DANMAKU_PANEL_SESSION_ENABLED = "session_enabled"
internal const val DANMAKU_PANEL_OPACITY = "opacity"
internal const val DANMAKU_PANEL_TEXT_SIZE = "text_size"
internal const val DANMAKU_PANEL_SPEED = "speed"
internal const val DANMAKU_PANEL_AREA = "area"
internal const val DANMAKU_PANEL_STROKE_WIDTH = "stroke_width"
internal const val DANMAKU_PANEL_FONT_WEIGHT = "font_weight"
internal const val DANMAKU_PANEL_LANE_DENSITY = "lane_density"
internal const val DANMAKU_PANEL_FOLLOW_BILI_SHIELD = "follow_bili_shield"
internal const val DANMAKU_PANEL_AI_SHIELD_ENABLED = "ai_shield_enabled"
internal const val DANMAKU_PANEL_AI_SHIELD_LEVEL = "ai_shield_level"
internal const val DANMAKU_PANEL_ALLOW_SCROLL = "allow_scroll"
internal const val DANMAKU_PANEL_ALLOW_TOP = "allow_top"
internal const val DANMAKU_PANEL_ALLOW_BOTTOM = "allow_bottom"
internal const val DANMAKU_PANEL_ALLOW_COLOR = "allow_color"
internal const val DANMAKU_PANEL_ALLOW_SPECIAL = "allow_special"

@Stable
internal class PlayerOverlayPresentationState(
    initialDanmakuSettings: DanmakuSettings,
) {
    var danmakuSettings by mutableStateOf(initialDanmakuSettings)
        private set

    var isCommentsPanelVisible by mutableStateOf(false)
        private set

    fun syncStoredDanmakuSettings(storedSettings: DanmakuSettings) {
        if (storedSettings != danmakuSettings) {
            danmakuSettings = storedSettings
        }
    }

    fun resetForNewVideo() {
        isCommentsPanelVisible = false
    }

    fun syncOverlayVisibility(overlayUiState: PlayerOverlayUiState) {
        if (overlayUiState.activePanel != null || overlayUiState.overlayMode != PlayerOverlayMode.FullControls) {
            isCommentsPanelVisible = false
        }
    }

    fun showCommentsPanel() {
        isCommentsPanelVisible = true
    }

    fun hideCommentsPanel() {
        isCommentsPanelVisible = false
    }

    fun updateDanmakuSettings(settings: DanmakuSettings) {
        danmakuSettings = settings
    }
}

@Composable
internal fun rememberPlayerOverlayPresentationState(
    storedDanmakuSettings: DanmakuSettings,
): PlayerOverlayPresentationState {
    val presentationState = remember { PlayerOverlayPresentationState(storedDanmakuSettings) }
    LaunchedEffect(storedDanmakuSettings) {
        presentationState.syncStoredDanmakuSettings(storedDanmakuSettings)
    }
    return presentationState
}

internal fun buildPlayerPanelOptions(
    activePanel: PlayerAction?,
    uiState: PlayerUiState,
    danmakuSettings: DanmakuSettings,
    isDanmakuEnabled: Boolean,
): List<PanelOption> {
    return when (activePanel) {
        PlayerAction.Speed -> listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
            .map { speed ->
                PanelOption(
                    key = speed.toString(),
                    label = formatSpeed(speed),
                    isSelected = abs(uiState.playbackSpeed - speed) < 0.001f,
                )
            }

        PlayerAction.Quality -> uiState.qualityOptions.map { option ->
            PanelOption(
                key = option.id.toString(),
                label = option.label,
                subtitle = option.unsupportedReason,
                isSelected = option.id == uiState.selectedQuality,
                isEnabled = option.isSupported,
            )
        }

        PlayerAction.Audio -> uiState.audioOptions.map(PlayerOption::toPanelOption)
        PlayerAction.Codec -> uiState.videoCodecOptions.map(PlayerOption::toPanelOption)
        PlayerAction.Danmaku -> buildDanmakuPanelOptions(
            danmakuSettings = danmakuSettings,
            isDanmakuEnabled = isDanmakuEnabled,
        )
        PlayerAction.Detail,
        PlayerAction.Comments,
        null -> emptyList()
    }
}

internal fun panelTitleFor(action: PlayerAction?): String {
    return when (action) {
        PlayerAction.Danmaku -> "弹幕设置"
        else -> action?.label.orEmpty()
    }
}

internal fun handlePlayerOverlayEffect(
    effect: PlayerOverlayEffect,
    presentationState: PlayerOverlayPresentationState,
    viewModel: PlayerViewModel,
    context: Context,
    scope: CoroutineScope,
    onExitPlayer: () -> Unit,
) {
    when (effect) {
        PlayerOverlayEffect.ClearSeekPreview -> viewModel.clearSeekPreview()
        is PlayerOverlayEffect.RequestSeekPreview -> viewModel.requestSeekPreview(effect.targetPositionMs)
        PlayerOverlayEffect.TogglePlayback -> viewModel.togglePlayback()
        is PlayerOverlayEffect.SeekBy -> viewModel.seekBy(effect.deltaMs)
        is PlayerOverlayEffect.FinishSeekScrub -> {
            viewModel.finishSeekScrub(
                effect.targetPositionMs,
                effect.resumePlaybackAfterScrub,
            )
        }

        PlayerOverlayEffect.ToggleDanmaku -> {
            presentationState.hideCommentsPanel()
            viewModel.toggleDanmaku()
        }

        PlayerOverlayEffect.OpenComments -> {
            presentationState.showCommentsPanel()
            viewModel.ensureCommentsLoaded()
        }

        is PlayerOverlayEffect.SetPlaybackSpeed -> {
            presentationState.hideCommentsPanel()
            viewModel.setPlaybackSpeed(effect.speed)
        }

        is PlayerOverlayEffect.ChangeQuality -> {
            presentationState.hideCommentsPanel()
            viewModel.changeQuality(effect.qualityId)
        }

        is PlayerOverlayEffect.ChangeAudioQuality -> {
            presentationState.hideCommentsPanel()
            viewModel.changeAudioQuality(effect.qualityId)
        }

        is PlayerOverlayEffect.ChangeVideoCodec -> {
            presentationState.hideCommentsPanel()
            viewModel.changeVideoCodec(effect.codecId)
        }

        is PlayerOverlayEffect.ActivateDanmakuSetting -> {
            presentationState.hideCommentsPanel()
            handleDanmakuPanelAction(
                key = effect.key,
                currentSettings = presentationState.danmakuSettings,
                onSettingsChanged = presentationState::updateDanmakuSettings,
                viewModel = viewModel,
                context = context,
                scope = scope,
            )
        }

        PlayerOverlayEffect.ExitPlayer -> {
            viewModel.finishPlaybackSession(reason = "back_pressed")
            onExitPlayer()
        }
    }
}

private fun PlayerOption.toPanelOption(): PanelOption {
    return PanelOption(
        key = key,
        label = label,
        subtitle = subtitle ?: disabledReason,
        isSelected = isSelected,
        isEnabled = isEnabled,
    )
}

private fun buildDanmakuPanelOptions(
    danmakuSettings: DanmakuSettings,
    isDanmakuEnabled: Boolean,
): List<PanelOption> {
    return listOf(
        PanelOption(
            key = DANMAKU_PANEL_SESSION_ENABLED,
            label = "当前会话弹幕",
            subtitle = "只影响当前播放，不改默认值。",
            valueText = onOff(isDanmakuEnabled),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_OPACITY,
            label = "弹幕透明度",
            subtitle = "范围 0.05 到 1.00。",
            valueText = formatDanmakuOpacity(danmakuSettings.opacity),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_TEXT_SIZE,
            label = "弹幕字体大小",
            subtitle = "按当前 TV 渲染基准生效。",
            valueText = danmakuSettings.textSizeSp.toString(),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_SPEED,
            label = "弹幕速度",
            subtitle = "数字越大越快。",
            valueText = danmakuSettings.speedLevel.toString(),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_AREA,
            label = "弹幕占屏比",
            subtitle = "控制弹幕可用的垂直区域。",
            valueText = formatDanmakuAreaRatio(danmakuSettings.areaRatio),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_STROKE_WIDTH,
            label = "弹幕文字描边粗细",
            subtitle = "0 会同时关闭描边。",
            valueText = danmakuSettings.strokeWidthPx.toString(),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_FONT_WEIGHT,
            label = "字体粗细",
            subtitle = "常规和加粗之间切换。",
            valueText = formatDanmakuFontWeight(danmakuSettings.fontWeight),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_LANE_DENSITY,
            label = "轨道密度",
            subtitle = "稀疏更松，密集更紧。",
            valueText = formatDanmakuLaneDensity(danmakuSettings.laneDensity),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_FOLLOW_BILI_SHIELD,
            label = "跟随B站弹幕屏蔽",
            subtitle = "叠加账号云端过滤规则。",
            valueText = onOff(danmakuSettings.followBiliShield),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_AI_SHIELD_ENABLED,
            label = "智能云屏蔽",
            subtitle = "按权重过滤低质量弹幕。",
            valueText = onOff(danmakuSettings.aiShieldEnabled),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_AI_SHIELD_LEVEL,
            label = "智能云屏蔽等级",
            subtitle = "范围 1 到 10，越高越严格。",
            valueText = danmakuSettings.aiShieldLevel.toString(),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_ALLOW_SCROLL,
            label = "允许滚动弹幕",
            valueText = onOff(danmakuSettings.allowScroll),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_ALLOW_TOP,
            label = "允许顶部悬停弹幕",
            valueText = onOff(danmakuSettings.allowTop),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_ALLOW_BOTTOM,
            label = "允许底部悬停弹幕",
            valueText = onOff(danmakuSettings.allowBottom),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_ALLOW_COLOR,
            label = "允许彩色弹幕",
            valueText = onOff(danmakuSettings.allowColor),
            presentation = PanelOptionPresentation.Setting,
        ),
        PanelOption(
            key = DANMAKU_PANEL_ALLOW_SPECIAL,
            label = "允许特殊弹幕",
            valueText = onOff(danmakuSettings.allowSpecial),
            presentation = PanelOptionPresentation.Setting,
        ),
    )
}

private fun handleDanmakuPanelAction(
    key: String,
    currentSettings: DanmakuSettings,
    onSettingsChanged: (DanmakuSettings) -> Unit,
    viewModel: PlayerViewModel,
    context: Context,
    scope: CoroutineScope,
) {
    if (key == DANMAKU_PANEL_SESSION_ENABLED) {
        viewModel.toggleDanmaku()
        return
    }

    val updatedSettings = when (key) {
        DANMAKU_PANEL_OPACITY -> currentSettings.copy(
            opacity = nextOption(DANMAKU_OPACITY_VALUES, currentSettings.opacity)
        )

        DANMAKU_PANEL_TEXT_SIZE -> currentSettings.copy(
            textSizeSp = nextOption(DANMAKU_TEXT_SIZE_VALUES, currentSettings.textSizeSp)
        )

        DANMAKU_PANEL_SPEED -> currentSettings.copy(
            speedLevel = if (currentSettings.speedLevel >= 10) 1 else currentSettings.speedLevel + 1
        )

        DANMAKU_PANEL_AREA -> currentSettings.copy(
            areaRatio = nextOption(DANMAKU_AREA_RATIO_VALUES, currentSettings.areaRatio)
        )

        DANMAKU_PANEL_STROKE_WIDTH -> currentSettings.copy(
            strokeWidthPx = nextOption(DANMAKU_STROKE_WIDTH_VALUES, currentSettings.strokeWidthPx)
        )

        DANMAKU_PANEL_FONT_WEIGHT -> currentSettings.copy(
            fontWeight = nextOption(DanmakuFontWeightPreset.entries, currentSettings.fontWeight)
        )

        DANMAKU_PANEL_LANE_DENSITY -> currentSettings.copy(
            laneDensity = nextOption(DanmakuLaneDensityPreset.entries, currentSettings.laneDensity)
        )

        DANMAKU_PANEL_FOLLOW_BILI_SHIELD -> currentSettings.copy(
            followBiliShield = !currentSettings.followBiliShield
        )

        DANMAKU_PANEL_AI_SHIELD_ENABLED -> currentSettings.copy(
            aiShieldEnabled = !currentSettings.aiShieldEnabled
        )

        DANMAKU_PANEL_AI_SHIELD_LEVEL -> currentSettings.copy(
            aiShieldLevel = if (currentSettings.aiShieldLevel >= 10) 1 else currentSettings.aiShieldLevel + 1
        )

        DANMAKU_PANEL_ALLOW_SCROLL -> currentSettings.copy(
            allowScroll = !currentSettings.allowScroll
        )

        DANMAKU_PANEL_ALLOW_TOP -> currentSettings.copy(
            allowTop = !currentSettings.allowTop
        )

        DANMAKU_PANEL_ALLOW_BOTTOM -> currentSettings.copy(
            allowBottom = !currentSettings.allowBottom
        )

        DANMAKU_PANEL_ALLOW_COLOR -> currentSettings.copy(
            allowColor = !currentSettings.allowColor
        )

        DANMAKU_PANEL_ALLOW_SPECIAL -> currentSettings.copy(
            allowSpecial = !currentSettings.allowSpecial
        )

        else -> return
    }

    onSettingsChanged(updatedSettings)
    scope.launch {
        DanmakuSettingsStore.updateSettings(context) { updatedSettings }
    }
}

private fun onOff(value: Boolean): String = if (value) "开" else "关"

private fun formatDanmakuOpacity(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun formatDanmakuFontWeight(value: DanmakuFontWeightPreset): String {
    return when (value) {
        DanmakuFontWeightPreset.Normal -> "常规"
        DanmakuFontWeightPreset.Bold -> "加粗"
    }
}

private fun formatDanmakuLaneDensity(value: DanmakuLaneDensityPreset): String {
    return when (value) {
        DanmakuLaneDensityPreset.Sparse -> "稀疏"
        DanmakuLaneDensityPreset.Standard -> "标准"
        DanmakuLaneDensityPreset.Dense -> "密集"
    }
}

private fun formatDanmakuAreaRatio(value: Float): String {
    return when {
        abs(value - 1f) < 0.001f -> "不限"
        abs(value - (1f / 6f)) < 0.001f -> "1/6"
        abs(value - (1f / 5f)) < 0.001f -> "1/5"
        abs(value - (1f / 4f)) < 0.001f -> "1/4"
        abs(value - (1f / 3f)) < 0.001f -> "1/3"
        abs(value - (2f / 5f)) < 0.001f -> "2/5"
        abs(value - (1f / 2f)) < 0.001f -> "1/2"
        abs(value - (3f / 5f)) < 0.001f -> "3/5"
        abs(value - (2f / 3f)) < 0.001f -> "2/3"
        abs(value - (3f / 4f)) < 0.001f -> "3/4"
        abs(value - (4f / 5f)) < 0.001f -> "4/5"
        else -> formatDanmakuOpacity(value)
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}倍"
    } else {
        "${speed}倍"
    }
}

private fun <T> nextOption(options: List<T>, current: T): T {
    val currentIndex = options.indexOf(current)
    return if (currentIndex == -1 || currentIndex == options.lastIndex) {
        options.first()
    } else {
        options[currentIndex + 1]
    }
}
