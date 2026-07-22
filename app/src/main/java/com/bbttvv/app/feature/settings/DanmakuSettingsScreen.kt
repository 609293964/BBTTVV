package com.bbttvv.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.bbttvv.app.ui.focus.RegisterTvFocusReturnTarget
import com.bbttvv.app.ui.player.formatDanmakuAreaRatio
import com.bbttvv.app.ui.player.formatDanmakuFontWeight
import com.bbttvv.app.ui.player.formatDanmakuLaneDensity
import com.bbttvv.app.ui.player.formatDanmakuOpacity
import kotlinx.coroutines.launch

private enum class DanmakuChoice(val rowKey: String) {
    OPACITY("danmaku_opacity"),
    TEXT_SIZE("danmaku_text_size"),
    FONT_WEIGHT("danmaku_font_weight"),
    STROKE_WIDTH("danmaku_stroke_width"),
    AREA_RATIO("danmaku_area_ratio"),
    LANE_DENSITY("danmaku_lane_density"),
    SPEED("danmaku_speed"),
    AI_SHIELD_LEVEL("danmaku_ai_shield_level"),
}

@Composable
fun TvDanmakuSettingsList(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    initialFocusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = DanmakuSettingsStore.getSettings(context)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
        .value

    var lastFocusedKey by remember { mutableStateOf<String?>(null) }
    var activeChoice by remember { mutableStateOf<DanmakuChoice?>(null) }
    val choiceFocusRequesters = remember {
        DanmakuChoice.entries.associateWith { FocusRequester() }
    }
    val choiceAnchorBounds = remember { mutableStateMapOf<DanmakuChoice, Rect>() }

    fun Modifier.captureChoiceAnchor(choice: DanmakuChoice): Modifier {
        return onGloballyPositioned { coordinates ->
            choiceAnchorBounds[choice] = coordinates.boundsInWindow()
        }
    }
    val defaultFirstKey = "danmaku_default_enabled"
    val targetFirstKey = lastFocusedKey ?: defaultFirstKey

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isFocusedInRightPanel by remember { mutableStateOf(false) }

    DanmakuChoice.entries.forEach { choice ->
        RegisterTvFocusReturnTarget(
            key = choice.rowKey,
            focusRequester = choiceFocusRequesters.getValue(choice),
        )
    }

    androidx.compose.runtime.LaunchedEffect(targetFirstKey, isFocusedInRightPanel) {
        if (!isFocusedInRightPanel) {
            val targetIndex = DanmakuSettingsCatalog.itemIndex(targetFirstKey)
            runCatching {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    val getRowModifier: (String, Modifier) -> Modifier = { key, customBase ->
        var itemModifier = if (leftFocusRequester != null) {
            customBase.focusProperties { left = leftFocusRequester }
        } else {
            customBase
        }
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
                subtitle = "选择 20% 到 100%，直接映射到当前渲染透明度。",
                value = formatDanmakuOpacity(settings.opacity),
                kind = SettingsRowKind.Choice,
                compact = compact,
                modifier = getRowModifier(
                    "danmaku_opacity",
                    Modifier
                        .focusRequester(choiceFocusRequesters.getValue(DanmakuChoice.OPACITY))
                        .captureChoiceAnchor(DanmakuChoice.OPACITY),
                ),
                onClick = { activeChoice = DanmakuChoice.OPACITY }
            )
        }
        item(key = "danmaku_text_size") {
            SettingsRow(
                title = "弹幕字体大小",
                subtitle = "以字号 22 为 100%，范围 40% 到 290%。",
                value = "${100 + (settings.textSizeSp - 22) * 5}%",
                kind = SettingsRowKind.Choice,
                compact = compact,
                modifier = getRowModifier(
                    "danmaku_text_size",
                    Modifier
                        .focusRequester(choiceFocusRequesters.getValue(DanmakuChoice.TEXT_SIZE))
                        .captureChoiceAnchor(DanmakuChoice.TEXT_SIZE),
                ),
                onClick = { activeChoice = DanmakuChoice.TEXT_SIZE }
            )
        }
        item(key = "danmaku_font_weight") {
            SettingsRow(
                title = "字体粗细",
                subtitle = "在常规和加粗之间切换。",
                value = formatDanmakuFontWeight(settings.fontWeight),
                kind = SettingsRowKind.Choice,
                compact = compact,
                modifier = getRowModifier(
                    "danmaku_font_weight",
                    Modifier
                        .focusRequester(choiceFocusRequesters.getValue(DanmakuChoice.FONT_WEIGHT))
                        .captureChoiceAnchor(DanmakuChoice.FONT_WEIGHT),
                ),
                onClick = { activeChoice = DanmakuChoice.FONT_WEIGHT }
            )
        }
        item(key = "danmaku_stroke_width") {
            SettingsRow(
                title = "弹幕文字描边粗细",
                subtitle = "选择 0 / 2 / 4 / 6，0 会同时关闭描边。",
                value = settings.strokeWidthPx.toString(),
                kind = SettingsRowKind.Choice,
                compact = compact,
                modifier = getRowModifier(
                    "danmaku_stroke_width",
                    Modifier
                        .focusRequester(choiceFocusRequesters.getValue(DanmakuChoice.STROKE_WIDTH))
                        .captureChoiceAnchor(DanmakuChoice.STROKE_WIDTH),
                ),
                onClick = { activeChoice = DanmakuChoice.STROKE_WIDTH }
            )
        }
        item(key = "danmaku_area_ratio") {
            SettingsRow(
                title = "弹幕占屏比",
                subtitle = "控制滚动和悬停弹幕可用的垂直区域。",
                value = formatDanmakuAreaRatio(settings.areaRatio),
                kind = SettingsRowKind.Choice,
                compact = compact,
                modifier = getRowModifier(
                    "danmaku_area_ratio",
                    Modifier
                        .focusRequester(choiceFocusRequesters.getValue(DanmakuChoice.AREA_RATIO))
                        .captureChoiceAnchor(DanmakuChoice.AREA_RATIO),
                ),
                onClick = { activeChoice = DanmakuChoice.AREA_RATIO }
            )
        }
        item(key = "danmaku_lane_density") {
            SettingsRow(
                title = "轨道密度",
                subtitle = "影响每行间距，稀疏更松，密集更紧。",
                value = formatDanmakuLaneDensity(settings.laneDensity),
                kind = SettingsRowKind.Choice,
                compact = compact,
                modifier = getRowModifier(
                    "danmaku_lane_density",
                    Modifier
                        .focusRequester(choiceFocusRequesters.getValue(DanmakuChoice.LANE_DENSITY))
                        .captureChoiceAnchor(DanmakuChoice.LANE_DENSITY),
                ),
                onClick = { activeChoice = DanmakuChoice.LANE_DENSITY }
            )
        }
        item(key = "danmaku_speed") {
            SettingsRow(
                title = "弹幕速度",
                subtitle = "数字越大越快，最终通过滚动时长映射生效。",
                value = settings.speedLevel.toString(),
                kind = SettingsRowKind.Choice,
                compact = compact,
                modifier = getRowModifier(
                    "danmaku_speed",
                    Modifier
                        .focusRequester(choiceFocusRequesters.getValue(DanmakuChoice.SPEED))
                        .captureChoiceAnchor(DanmakuChoice.SPEED),
                ),
                onClick = { activeChoice = DanmakuChoice.SPEED }
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
                kind = SettingsRowKind.Choice,
                compact = compact,
                modifier = getRowModifier(
                    "danmaku_ai_shield_level",
                    Modifier.focusRequester(
                        choiceFocusRequesters.getValue(DanmakuChoice.AI_SHIELD_LEVEL)
                    ).captureChoiceAnchor(DanmakuChoice.AI_SHIELD_LEVEL),
                ),
                onClick = { activeChoice = DanmakuChoice.AI_SHIELD_LEVEL }
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

    when (activeChoice) {
        DanmakuChoice.OPACITY -> TvSettingsChoicePopup(
            title = "选择弹幕透明度",
            options = DANMAKU_OPACITY_VALUES.map { value ->
                TvSettingsChoiceOption(value, formatDanmakuOpacity(value))
            },
            selectedValue = settings.opacity,
            anchorBounds = choiceAnchorBounds[DanmakuChoice.OPACITY],
            returnFocusKey = DanmakuChoice.OPACITY.rowKey,
            onSelect = { value ->
                scope.launch {
                    DanmakuSettingsStore.updateSettings(context) { it.copy(opacity = value) }
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        DanmakuChoice.TEXT_SIZE -> TvSettingsChoicePopup(
            title = "选择弹幕字体大小",
            options = DANMAKU_TEXT_SIZE_VALUES.map { value ->
                TvSettingsChoiceOption(value, "${100 + (value - 22) * 5}%")
            },
            selectedValue = settings.textSizeSp,
            anchorBounds = choiceAnchorBounds[DanmakuChoice.TEXT_SIZE],
            returnFocusKey = DanmakuChoice.TEXT_SIZE.rowKey,
            onSelect = { value ->
                scope.launch {
                    DanmakuSettingsStore.updateSettings(context) { it.copy(textSizeSp = value) }
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        DanmakuChoice.FONT_WEIGHT -> TvSettingsChoicePopup(
            title = "选择弹幕字体粗细",
            options = DanmakuFontWeightPreset.entries.map { value ->
                TvSettingsChoiceOption(value, formatDanmakuFontWeight(value))
            },
            selectedValue = settings.fontWeight,
            anchorBounds = choiceAnchorBounds[DanmakuChoice.FONT_WEIGHT],
            returnFocusKey = DanmakuChoice.FONT_WEIGHT.rowKey,
            onSelect = { value ->
                scope.launch {
                    DanmakuSettingsStore.updateSettings(context) { it.copy(fontWeight = value) }
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        DanmakuChoice.STROKE_WIDTH -> TvSettingsChoicePopup(
            title = "选择弹幕描边粗细",
            options = DANMAKU_STROKE_WIDTH_VALUES.map { value ->
                TvSettingsChoiceOption(value, value.toString())
            },
            selectedValue = settings.strokeWidthPx,
            anchorBounds = choiceAnchorBounds[DanmakuChoice.STROKE_WIDTH],
            returnFocusKey = DanmakuChoice.STROKE_WIDTH.rowKey,
            onSelect = { value ->
                scope.launch {
                    DanmakuSettingsStore.updateSettings(context) { it.copy(strokeWidthPx = value) }
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        DanmakuChoice.AREA_RATIO -> TvSettingsChoicePopup(
            title = "选择弹幕占屏比",
            options = DANMAKU_AREA_RATIO_VALUES.map { value ->
                TvSettingsChoiceOption(value, formatDanmakuAreaRatio(value))
            },
            selectedValue = settings.areaRatio,
            anchorBounds = choiceAnchorBounds[DanmakuChoice.AREA_RATIO],
            returnFocusKey = DanmakuChoice.AREA_RATIO.rowKey,
            onSelect = { value ->
                scope.launch {
                    DanmakuSettingsStore.updateSettings(context) { it.copy(areaRatio = value) }
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        DanmakuChoice.LANE_DENSITY -> TvSettingsChoicePopup(
            title = "选择弹幕轨道密度",
            options = DanmakuLaneDensityPreset.entries.map { value ->
                TvSettingsChoiceOption(value, formatDanmakuLaneDensity(value))
            },
            selectedValue = settings.laneDensity,
            anchorBounds = choiceAnchorBounds[DanmakuChoice.LANE_DENSITY],
            returnFocusKey = DanmakuChoice.LANE_DENSITY.rowKey,
            onSelect = { value ->
                scope.launch {
                    DanmakuSettingsStore.updateSettings(context) { it.copy(laneDensity = value) }
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        DanmakuChoice.SPEED -> TvSettingsChoicePopup(
            title = "选择弹幕速度",
            options = (1..10).map { value -> TvSettingsChoiceOption(value, value.toString()) },
            selectedValue = settings.speedLevel,
            anchorBounds = choiceAnchorBounds[DanmakuChoice.SPEED],
            returnFocusKey = DanmakuChoice.SPEED.rowKey,
            onSelect = { value ->
                scope.launch {
                    DanmakuSettingsStore.updateSettings(context) { it.copy(speedLevel = value) }
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        DanmakuChoice.AI_SHIELD_LEVEL -> TvSettingsChoicePopup(
            title = "选择智能云屏蔽等级",
            options = (1..10).map { value -> TvSettingsChoiceOption(value, value.toString()) },
            selectedValue = settings.aiShieldLevel,
            anchorBounds = choiceAnchorBounds[DanmakuChoice.AI_SHIELD_LEVEL],
            returnFocusKey = DanmakuChoice.AI_SHIELD_LEVEL.rowKey,
            onSelect = { value ->
                scope.launch {
                    DanmakuSettingsStore.updateSettings(context) { it.copy(aiShieldLevel = value) }
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        null -> Unit
    }
}
