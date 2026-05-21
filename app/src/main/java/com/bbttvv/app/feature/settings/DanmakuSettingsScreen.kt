package com.bbttvv.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
    initialFocusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = DanmakuSettingsStore.getSettings(context)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
        .value

    var lastFocusedKey by remember { mutableStateOf<String?>(null) }
    val defaultFirstKey = "danmaku_default_enabled"
    val targetFirstKey = lastFocusedKey ?: defaultFirstKey

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isFocusedInRightPanel by remember { mutableStateOf(false) }

    val getIndexForKey: (String) -> Int = { key ->
        val keys = listOf(
            "danmaku_basic_title",
            "danmaku_default_enabled",
            "danmaku_opacity",
            "danmaku_text_size",
            "danmaku_font_weight",
            "danmaku_stroke_width",
            "danmaku_area_ratio",
            "danmaku_lane_density",
            "danmaku_speed",
            "danmaku_cloud_title",
            "danmaku_follow_bili",
            "danmaku_ai_shield",
            "danmaku_ai_shield_level",
            "danmaku_type_title",
            "danmaku_allow_scroll",
            "danmaku_allow_top",
            "danmaku_allow_bottom",
            "danmaku_allow_color",
            "danmaku_allow_special"
        )
        val index = keys.indexOf(key)
        if (index >= 0) index else 0
    }

    androidx.compose.runtime.LaunchedEffect(targetFirstKey, isFocusedInRightPanel) {
        if (!isFocusedInRightPanel) {
            val targetIndex = getIndexForKey(targetFirstKey)
            runCatching {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    val getRowModifier: (String, Modifier) -> Modifier = { key, customBase ->
        var itemModifier = customBase
        if (initialFocusRequester != null && targetFirstKey == key) {
            itemModifier = itemModifier.focusRequester(initialFocusRequester)
        }
        itemModifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                lastFocusedKey = key
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.onFocusChanged { state ->
            isFocusedInRightPanel = state.hasFocus
        },
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        contentPadding = PaddingValues(bottom = if (compact) 20.dp else 32.dp)
    ) {
        item(key = "danmaku_basic_title") { SettingsSectionTitle("基础显示", compact = compact) }
        item(key = "danmaku_default_enabled") {
            SettingsRow(
                title = "默认开启弹幕",
                subtitle = "播放器进入时默认显示弹幕；播放器里的“弹”按钮只影响当前会话。",
                value = onOff(settings.enabled),
                compact = compact,
                modifier = getRowModifier("danmaku_default_enabled", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(enabled = !current.enabled)
                        }
                    }
                }
            )
        }
        item(key = "danmaku_opacity") {
            SettingsRow(
                title = "弹幕透明度",
                subtitle = "循环切换 20% 到 100%，直接映射到当前渲染透明度。",
                value = formatOpacity(settings.opacity),
                compact = compact,
                modifier = getRowModifier("danmaku_opacity", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(opacity = nextOption(DANMAKU_OPACITY_VALUES, current.opacity))
                        }
                    }
                }
            )
        }
        item(key = "danmaku_text_size") {
            SettingsRow(
                title = "弹幕字体大小",
                subtitle = "以字号 22 为 100%，范围 40% 到 290%。",
                value = "${100 + (settings.textSizeSp - 22) * 5}%",
                compact = compact,
                modifier = getRowModifier("danmaku_text_size", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(textSizeSp = nextOption(DANMAKU_TEXT_SIZE_VALUES, current.textSizeSp))
                        }
                    }
                }
            )
        }
        item(key = "danmaku_font_weight") {
            SettingsRow(
                title = "字体粗细",
                subtitle = "在常规和加粗之间切换。",
                value = formatFontWeight(settings.fontWeight),
                compact = compact,
                modifier = getRowModifier("danmaku_font_weight", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(fontWeight = nextOption(DanmakuFontWeightPreset.entries, current.fontWeight))
                        }
                    }
                }
            )
        }
        item(key = "danmaku_stroke_width") {
            SettingsRow(
                title = "弹幕文字描边粗细",
                subtitle = "循环切换 0 / 2 / 4 / 6，0 会同时关闭描边。",
                value = settings.strokeWidthPx.toString(),
                compact = compact,
                modifier = getRowModifier("danmaku_stroke_width", Modifier),
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
        item(key = "danmaku_area_ratio") {
            SettingsRow(
                title = "弹幕占屏比",
                subtitle = "控制滚动和悬停弹幕可用的垂直区域。",
                value = formatAreaRatio(settings.areaRatio),
                compact = compact,
                modifier = getRowModifier("danmaku_area_ratio", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(areaRatio = nextOption(DANMAKU_AREA_RATIO_VALUES, current.areaRatio))
                        }
                    }
                }
            )
        }
        item(key = "danmaku_lane_density") {
            SettingsRow(
                title = "轨道密度",
                subtitle = "影响每行间距，稀疏更松，密集更紧。",
                value = formatLaneDensity(settings.laneDensity),
                compact = compact,
                modifier = getRowModifier("danmaku_lane_density", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(laneDensity = nextOption(DanmakuLaneDensityPreset.entries, current.laneDensity))
                        }
                    }
                }
            )
        }
        item(key = "danmaku_speed") {
            SettingsRow(
                title = "弹幕速度",
                subtitle = "数字越大越快，最终通过滚动时长映射生效。",
                value = settings.speedLevel.toString(),
                compact = compact,
                modifier = getRowModifier("danmaku_speed", Modifier),
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

        item(key = "danmaku_cloud_title") { SettingsSectionTitle("云端过滤", compact = compact) }
        item(key = "danmaku_follow_bili") {
            SettingsRow(
                title = "跟随B站弹幕屏蔽",
                subtitle = "登录后读取账号弹幕屏蔽规则，并与本地设置叠加生效。",
                value = onOff(settings.followBiliShield),
                compact = compact,
                modifier = getRowModifier("danmaku_follow_bili", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(followBiliShield = !current.followBiliShield)
                        }
                    }
                }
            )
        }
        item(key = "danmaku_ai_shield") {
            SettingsRow(
                title = "智能云屏蔽",
                subtitle = "按弹幕权重过滤低质量弹幕；开启后按等级生效。",
                value = onOff(settings.aiShieldEnabled),
                compact = compact,
                modifier = getRowModifier("danmaku_ai_shield", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(aiShieldEnabled = !current.aiShieldEnabled)
                        }
                    }
                }
            )
        }
        item(key = "danmaku_ai_shield_level") {
            SettingsRow(
                title = "智能云屏蔽等级",
                subtitle = "范围 1 到 10，等级越高过滤越严格。",
                value = settings.aiShieldLevel.toString(),
                compact = compact,
                modifier = getRowModifier("danmaku_ai_shield_level", Modifier),
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

        item(key = "danmaku_type_title") { SettingsSectionTitle("类型过滤", compact = compact) }
        item(key = "danmaku_allow_scroll") {
            SettingsRow(
                title = "允许滚动弹幕",
                subtitle = "关闭后会过滤普通滚动弹幕。",
                value = onOff(settings.allowScroll),
                compact = compact,
                modifier = getRowModifier("danmaku_allow_scroll", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowScroll = !current.allowScroll)
                        }
                    }
                }
            )
        }
        item(key = "danmaku_allow_top") {
            SettingsRow(
                title = "允许顶部悬停弹幕",
                subtitle = "关闭后会过滤顶部悬停弹幕。",
                value = onOff(settings.allowTop),
                compact = compact,
                modifier = getRowModifier("danmaku_allow_top", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowTop = !current.allowTop)
                        }
                    }
                }
            )
        }
        item(key = "danmaku_allow_bottom") {
            SettingsRow(
                title = "允许底部悬停弹幕",
                subtitle = "关闭后会过滤底部悬停弹幕。",
                value = onOff(settings.allowBottom),
                compact = compact,
                modifier = getRowModifier("danmaku_allow_bottom", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowBottom = !current.allowBottom)
                        }
                    }
                }
            )
        }
        item(key = "danmaku_allow_color") {
            SettingsRow(
                title = "允许彩色弹幕",
                subtitle = "关闭后仅保留白色弹幕，非白色视为彩色。",
                value = onOff(settings.allowColor),
                compact = compact,
                modifier = getRowModifier("danmaku_allow_color", Modifier),
                onClick = {
                    scope.launch {
                        DanmakuSettingsStore.updateSettings(context) { current ->
                            current.copy(allowColor = !current.allowColor)
                        }
                    }
                }
            )
        }
        item(key = "danmaku_allow_special") {
            SettingsRow(
                title = "允许特殊弹幕",
                subtitle = "关闭后会过滤高级/特殊弹幕。",
                value = onOff(settings.allowSpecial),
                compact = compact,
                modifier = getRowModifier("danmaku_allow_special", Modifier),
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

private fun formatOpacity(value: Float): String = String.format(Locale.US, "%.0f%%", value * 100)

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

