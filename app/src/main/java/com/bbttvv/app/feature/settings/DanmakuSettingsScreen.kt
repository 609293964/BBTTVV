package com.bbttvv.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbttvv.app.core.store.player.DANMAKU_AREA_RATIO_VALUES
import com.bbttvv.app.core.store.player.DANMAKU_OPACITY_VALUES
import com.bbttvv.app.core.store.player.DANMAKU_STROKE_WIDTH_VALUES
import com.bbttvv.app.core.store.player.DANMAKU_TEXT_SIZE_VALUES
import com.bbttvv.app.core.store.player.DanmakuFontWeightPreset
import com.bbttvv.app.core.store.player.DanmakuLaneDensityPreset
import com.bbttvv.app.core.store.player.DanmakuSettings
import com.bbttvv.app.core.store.player.DanmakuSettingsStore
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun TvDanmakuSettingsList(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = DanmakuSettingsStore.getSettings(context)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
        .value

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        contentPadding = PaddingValues(bottom = if (compact) 20.dp else 32.dp)
    ) {
        item { SettingsSectionTitle("基础显示", compact = compact) }
        item {
            SettingsRow(
                title = "默认开启弹幕",
                subtitle = "播放器进入时默认显示弹幕；播放器里的“弹”按钮只影响当前会话。",
                value = onOff(settings.enabled),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(enabled = !current.enabled)
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "弹幕透明度",
                subtitle = "循环切换 0.05 到 1.00，直接映射到当前渲染透明度。",
                value = formatOpacity(settings.opacity),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(opacity = nextOption(DANMAKU_OPACITY_VALUES, current.opacity))
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "弹幕字体大小",
                subtitle = "按现有渲染基准换算，范围 10 到 60。",
                value = settings.textSizeSp.toString(),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(textSizeSp = nextOption(DANMAKU_TEXT_SIZE_VALUES, current.textSizeSp))
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "字体粗细",
                subtitle = "在常规和加粗之间切换。",
                value = formatFontWeight(settings.fontWeight),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(fontWeight = nextOption(DanmakuFontWeightPreset.entries, current.fontWeight))
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "弹幕文字描边粗细",
                subtitle = "循环切换 0 / 2 / 4 / 6，0 会同时关闭描边。",
                value = settings.strokeWidthPx.toString(),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(
                                strokeWidthPx = nextOption(DANMAKU_STROKE_WIDTH_VALUES, current.strokeWidthPx)
                            )
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "弹幕占屏比",
                subtitle = "控制滚动和悬停弹幕可用的垂直区域。",
                value = formatAreaRatio(settings.areaRatio),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(areaRatio = nextOption(DANMAKU_AREA_RATIO_VALUES, current.areaRatio))
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "轨道密度",
                subtitle = "影响每行间距，稀疏更松，密集更紧。",
                value = formatLaneDensity(settings.laneDensity),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(laneDensity = nextOption(DanmakuLaneDensityPreset.entries, current.laneDensity))
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "弹幕速度",
                subtitle = "数字越大越快，最终通过滚动时长映射生效。",
                value = settings.speedLevel.toString(),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            val next = if (current.speedLevel >= 10) 1 else current.speedLevel + 1
                            current.copy(speedLevel = next)
                        }
                    }
                }
            )
        }

        item { SettingsSectionTitle("云端过滤", compact = compact) }
        item {
            SettingsRow(
                title = "跟随B站弹幕屏蔽",
                subtitle = "登录后读取账号弹幕屏蔽规则，并与本地设置叠加生效。",
                value = onOff(settings.followBiliShield),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(followBiliShield = !current.followBiliShield)
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "智能云屏蔽",
                subtitle = "按弹幕权重过滤低质量弹幕；开启后按等级生效。",
                value = onOff(settings.aiShieldEnabled),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(aiShieldEnabled = !current.aiShieldEnabled)
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "智能云屏蔽等级",
                subtitle = "范围 1 到 10，等级越高过滤越严格。",
                value = settings.aiShieldLevel.toString(),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            val next = if (current.aiShieldLevel >= 10) 1 else current.aiShieldLevel + 1
                            current.copy(aiShieldLevel = next)
                        }
                    }
                }
            )
        }

        item { SettingsSectionTitle("类型过滤", compact = compact) }
        item {
            SettingsRow(
                title = "允许滚动弹幕",
                subtitle = "关闭后会过滤普通滚动弹幕。",
                value = onOff(settings.allowScroll),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowScroll = !current.allowScroll)
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "允许顶部悬停弹幕",
                subtitle = "关闭后会过滤顶部悬停弹幕。",
                value = onOff(settings.allowTop),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowTop = !current.allowTop)
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "允许底部悬停弹幕",
                subtitle = "关闭后会过滤底部悬停弹幕。",
                value = onOff(settings.allowBottom),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowBottom = !current.allowBottom)
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "允许彩色弹幕",
                subtitle = "关闭后仅保留白色弹幕，非白色视为彩色。",
                value = onOff(settings.allowColor),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowColor = !current.allowColor)
                        }
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "允许特殊弹幕",
                subtitle = "关闭后会过滤高级/特殊弹幕。",
                value = onOff(settings.allowSpecial),
                compact = compact,
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowSpecial = !current.allowSpecial)
                        }
                    }
                }
            )
        }
    }
}

private fun formatOpacity(value: Float): String = String.format(Locale.US, "%.2f", value)

private fun formatFontWeight(value: DanmakuFontWeightPreset): String {
    return when (value) {
        DanmakuFontWeightPreset.Normal -> "常规"
        DanmakuFontWeightPreset.Bold -> "加粗"
    }
}

private fun formatLaneDensity(value: DanmakuLaneDensityPreset): String {
    return when (value) {
        DanmakuLaneDensityPreset.Sparse -> "稀疏"
        DanmakuLaneDensityPreset.Standard -> "标准"
        DanmakuLaneDensityPreset.Dense -> "密集"
    }
}

private fun formatAreaRatio(value: Float): String {
    return when {
        kotlin.math.abs(value - 1f) < 0.001f -> "不限"
        kotlin.math.abs(value - (1f / 6f)) < 0.001f -> "1/6"
        kotlin.math.abs(value - (1f / 5f)) < 0.001f -> "1/5"
        kotlin.math.abs(value - (1f / 4f)) < 0.001f -> "1/4"
        kotlin.math.abs(value - (1f / 3f)) < 0.001f -> "1/3"
        kotlin.math.abs(value - (2f / 5f)) < 0.001f -> "2/5"
        kotlin.math.abs(value - (1f / 2f)) < 0.001f -> "1/2"
        kotlin.math.abs(value - (3f / 5f)) < 0.001f -> "3/5"
        kotlin.math.abs(value - (2f / 3f)) < 0.001f -> "2/3"
        kotlin.math.abs(value - (3f / 4f)) < 0.001f -> "3/4"
        kotlin.math.abs(value - (4f / 5f)) < 0.001f -> "4/5"
        else -> formatOpacity(value)
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

