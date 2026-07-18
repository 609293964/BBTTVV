package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bbttvv.app.core.plugin.PluginCapability
import com.bbttvv.app.core.plugin.json.JsonPluginManager
import com.bbttvv.app.core.store.TodayWatchFeedbackStore
import com.bbttvv.app.core.store.TodayWatchProfileStore
import com.bbttvv.app.data.model.response.SponsorBlockMarkerMode
import com.bbttvv.app.data.model.response.displayLabel
import com.bbttvv.app.feature.plugin.CDN_REGION_PLUGIN_ID
import com.bbttvv.app.feature.plugin.CdnRegionPlugin
import com.bbttvv.app.feature.plugin.CdnRegionPluginCache
import com.bbttvv.app.feature.plugin.HOME_FEED_ANONYMIZER_PLUGIN_ID
import com.bbttvv.app.feature.plugin.HomeFeedAnonymizerPlugin
import com.bbttvv.app.feature.plugin.SponsorCategoryMode
import com.bbttvv.app.feature.plugin.categoryModeLabel
import com.bbttvv.app.feature.plugin.TodayWatchTasteInsightState
import com.bbttvv.app.feature.plugin.buildHomeFeedAnonymizerCreditRows
import com.bbttvv.app.feature.plugin.buildHomeFeedAnonymizerStatsUiModel
import com.bbttvv.app.feature.plugin.buildTodayWatchTasteInsightState
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvConfirmDialog
import com.bbttvv.app.ui.components.TvDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.components.TvTextInput
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import kotlinx.coroutines.launch

@Composable
private fun SubPluginConfigContainer(
    content: @Composable () -> Unit
) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val bgColor = if (isLightTheme) Color(0x08000000) else Color(0x06FFFFFF)
    val borderColor = if (isLightTheme) Color(0x0D000000) else Color(0x0FFFFFFF)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 4.dp)
            .background(
                color = bgColor,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ProfilePluginCenterPanel(
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    onRequestSidebarFocus: () -> Boolean = { false },
) {
    val scope = rememberCoroutineScope()
    val plugins by com.bbttvv.app.core.plugin.PluginManager.pluginsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val jsonPlugins by JsonPluginManager.plugins.collectAsStateWithLifecycle(initialValue = emptyList())
    val filterStats by JsonPluginManager.filterStats.collectAsStateWithLifecycle(initialValue = emptyMap())
    var expandedPluginId by remember { mutableStateOf<String?>(null) }
    val contentFocusTarget = rememberProfileContentFocusTargetState(
        focusCoordinator = focusCoordinator,
        focusTab = focusTab,
    )

    val builtInPlugins = remember(plugins) {
        listOf(
            com.bbttvv.app.feature.plugin.SPONSOR_BLOCK_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.AD_FILTER_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.DANMAKU_ENHANCE_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.TodayWatchPlugin.PLUGIN_ID,
            CDN_REGION_PLUGIN_ID,
            HOME_FEED_ANONYMIZER_PLUGIN_ID
        ).mapNotNull { id ->
            plugins.firstOrNull { it.plugin.id == id }
        }
    }
    val firstBuiltInPluginId = builtInPlugins.firstOrNull()?.plugin?.id
    val firstJsonPluginId = if (firstBuiltInPluginId == null) {
        jsonPlugins.firstOrNull()?.plugin?.id
    } else {
        null
    }

    var lastFocusedKey by remember { mutableStateOf<String?>(null) }
    val defaultFirstKey = firstBuiltInPluginId ?: firstJsonPluginId ?: "plugin_external_empty"
    val targetFirstKey = lastFocusedKey ?: defaultFirstKey

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isFocusedInRightPanel by remember { mutableStateOf(false) }

    val getIndexForPluginKey: (String) -> Int = { key ->
        val builtinIndex = builtInPlugins.indexOfFirst { it.plugin.id == key }
        if (builtinIndex >= 0) {
            1 + builtinIndex
        } else {
            val jsonIndex = jsonPlugins.indexOfFirst { it.plugin.id == key }
            if (jsonIndex >= 0) {
                2 + builtInPlugins.size + jsonIndex
            } else if (key == "plugin_external_empty") {
                2 + builtInPlugins.size
            } else {
                1
            }
        }
    }

    LaunchedEffect(targetFirstKey, isFocusedInRightPanel) {
        if (!isFocusedInRightPanel) {
            val targetIndex = getIndexForPluginKey(targetFirstKey)
            if (targetIndex >= 0) {
                runCatching {
                    listState.scrollToItem(targetIndex)
                }
            }
        }
    }

    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val titleTextColor = if (isLightTheme) Color(0xFF18191C) else Color.White
    val sectionHeaderColor = if (isLightTheme) Color(0xFF61666D) else Color(0xB3FFFFFF)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .profileContentFocusTarget(
                state = contentFocusTarget,
                focusCoordinator = focusCoordinator,
                focusTab = focusTab,
                onDpadLeft = onRequestSidebarFocus,
            )
            .onFocusChanged { state ->
                isFocusedInRightPanel = state.hasFocus
            }
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { Text(text = "插件中心", color = titleTextColor, style = MaterialTheme.typography.headlineMedium) }
        itemsIndexed(builtInPlugins, key = { _, pluginInfo -> pluginInfo.plugin.id }) { index, pluginInfo ->
            val isExpanded = expandedPluginId == pluginInfo.plugin.id
            Column(
                modifier = Modifier.onFocusChanged { state ->
                    if (state.hasFocus) {
                        lastFocusedKey = pluginInfo.plugin.id
                    }
                },
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PluginCenterRowCard(
                    title = pluginInfo.plugin.name,
                    subtitle = pluginInfo.plugin.description,
                    value = buildString {
                        append(if (pluginInfo.enabled) "已启用" else "已关闭")
                        append(" · ")
                        append(resolvePluginCapabilitySummary(pluginInfo.plugin.capabilityManifest?.capabilities.orEmpty()))
                    },
                    onClick = {
                        expandedPluginId = if (expandedPluginId == pluginInfo.plugin.id) {
                            null
                        } else {
                            pluginInfo.plugin.id
                        }
                    },
                    modifier = if (pluginInfo.plugin.id == targetFirstKey) {
                        Modifier.focusRequester(contentFocusTarget.initialFocusRequester)
                    } else {
                        Modifier
                    },
                    onDpadUp = if (index == 0) {
                        { true }
                    } else {
                        null
                    },
                )
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(150)),
                    exit = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(150))
                ) {
                    SubPluginConfigContainer {
                        when (val plugin = pluginInfo.plugin) {
                            is com.bbttvv.app.feature.plugin.SponsorBlockPlugin -> {
                                SponsorBlockPluginPanel(
                                    plugin = plugin,
                                    enabled = pluginInfo.enabled,
                                    onToggleEnabled = {
                                        scope.launch {
                                            com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                        }
                                    }
                                )
                            }
                            is com.bbttvv.app.feature.plugin.AdFilterPlugin -> {
                                AdFilterPluginPanel(
                                    plugin = plugin,
                                    enabled = pluginInfo.enabled,
                                    onToggleEnabled = {
                                        scope.launch {
                                            com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                        }
                                    }
                                )
                            }
                            is com.bbttvv.app.feature.plugin.DanmakuEnhancePlugin -> {
                                DanmakuEnhancePluginPanel(
                                    plugin = plugin,
                                    enabled = pluginInfo.enabled,
                                    onToggleEnabled = {
                                        scope.launch {
                                            com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                        }
                                    }
                                )
                            }
                            is com.bbttvv.app.feature.plugin.TodayWatchPlugin -> {
                                TodayWatchPluginPanel(
                                    plugin = plugin,
                                    enabled = pluginInfo.enabled,
                                    onToggleEnabled = {
                                        scope.launch {
                                            com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                        }
                                    }
                                )
                            }
                            is CdnRegionPlugin -> {
                                CdnRegionPluginPanel(
                                    plugin = plugin,
                                    enabled = pluginInfo.enabled,
                                    onToggleEnabled = {
                                        scope.launch {
                                            com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                        }
                                    }
                                )
                            }
                            is HomeFeedAnonymizerPlugin -> {
                                HomeFeedAnonymizerPluginPanel(
                                    plugin = plugin,
                                    enabled = pluginInfo.enabled,
                                    onToggleEnabled = {
                                        scope.launch {
                                            com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                        }
                                    }
                                )
                            }
                            else -> {
                                ProfileInfoCard("暂不支持的插件类型", "这个插件已经注册进插件系统，但当前插件中心还没有给它单独的 TV 配置面板。", compact = true)
                            }
                        }
                    }
                }
            }
        }
        item(key = "plugin_external_title") {
            Text(text = "外部规则插件", color = sectionHeaderColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        if (jsonPlugins.isEmpty()) {
            item(key = "plugin_external_empty") {
                PluginCenterRowCard(
                    title = "暂无外部规则插件",
                    subtitle = "系统规则已经就位，后续可在这里接入外部 JSON 规则插件。",
                    value = "列表结束",
                    modifier = if (targetFirstKey == "plugin_external_empty") {
                        Modifier.focusRequester(contentFocusTarget.initialFocusRequester)
                    } else {
                        Modifier
                    },
                    onClick = {},
                    onDpadUp = if (builtInPlugins.isEmpty()) {
                        { true }
                    } else {
                        null
                    },
                    onDpadDown = { true }
                )
            }
        } else {
            itemsIndexed(jsonPlugins, key = { _, loaded -> loaded.plugin.id }) { index, loaded ->
                Column(
                    modifier = Modifier.onFocusChanged { state ->
                        if (state.hasFocus) {
                            lastFocusedKey = loaded.plugin.id
                        }
                    }
                ) {
                    PluginCenterRowCard(
                        title = loaded.plugin.name,
                        subtitle = buildString {
                            append(resolveJsonPluginTypeLabel(loaded.plugin.type))
                            append(" · ")
                            append(loaded.plugin.version)
                            filterStats[loaded.plugin.id]?.takeIf { it > 0 }?.let { count ->
                                append(" · 过滤 ")
                                append(count)
                                append("已")
                            }
                        },
                        value = buildString {
                            append(if (loaded.enabled) "已启用" else "已关闭")
                            append(" · ")
                            append(resolveJsonPluginTypeLabel(loaded.plugin.type))
                            append(" ")
                            append(loaded.plugin.version)
                            filterStats[loaded.plugin.id]?.takeIf { it > 0 }?.let { count ->
                                append(" · 过滤 ")
                                append(count)
                            }
                        },
                        onClick = { JsonPluginManager.setEnabled(loaded.plugin.id, !loaded.enabled) },
                        modifier = if (loaded.plugin.id == targetFirstKey) {
                            Modifier.focusRequester(contentFocusTarget.initialFocusRequester)
                        } else {
                            Modifier
                        },
                        onDpadUp = if (builtInPlugins.isEmpty() && index == 0) {
                            { true }
                        } else {
                            null
                        },
                        onDpadDown = {
                            index == jsonPlugins.lastIndex
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SponsorBlockPluginPanel(
    plugin: com.bbttvv.app.feature.plugin.SponsorBlockPlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val config by plugin.configState.collectAsStateWithLifecycle(initialValue = com.bbttvv.app.feature.plugin.SponsorBlockConfig())
    val nextMarkerMode = remember(config.markerMode) {
        val modes = SponsorBlockMarkerMode.entries
        modes[(modes.indexOf(config.markerMode) + 1).mod(modes.size)]
    }
    val nextMusicOfftopicMode = remember(config.musicOfftopicMode) {
        val modes = SponsorCategoryMode.entries
        modes[(modes.indexOf(config.musicOfftopicMode) + 1).mod(modes.size)]
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PluginCenterRowCard(
            title = "启用空降助手",
            subtitle = "控制空降助手是否参与视频详情页的 SponsorBlock 跳过逻辑。",
            value = if (enabled) "点击关闭" else "点击切换",
            isSubItem = true,
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "自动跳过",
            subtitle = "命中片头、片尾或恰饭片段时直接跳过；关闭后会显示手动跳过提示。",
            value = if (config.autoSkip) "已开启" else "已关闭",
            isSubItem = true,
            onClick = { plugin.setAutoSkip(!config.autoSkip) }
        )
        PluginCenterRowCard(
            title = "进度条标记",
            subtitle = "切换 SponsorBlock 片段在进度条上的提示策略，便于后续预览标记。",
            value = "${config.markerMode.displayLabel()} -> ${nextMarkerMode.displayLabel()}",
            isSubItem = true,
            onClick = { plugin.setMarkerMode(nextMarkerMode) }
        )
        PluginCenterRowCard(
            title = "跳过提示",
            subtitle = "关闭后不再显示右下角“按确认键跳过”提示，但不会影响进度条标记和自动跳过。",
            value = if (config.showSkipPrompt) "已开启" else "已关闭",
            isSubItem = true,
            onClick = { plugin.setShowSkipPrompt(!config.showSkipPrompt) }
        )
        PluginCenterRowCard(
            title = "赞助/恰饭",
            subtitle = "命中 SponsorBlock 的赞助片段时参与跳过。",
            value = if (config.skipSponsor) "已跳过" else "已保留",
            isSubItem = true,
            onClick = { plugin.setSkipSponsor(!config.skipSponsor) }
        )
        PluginCenterRowCard(
            title = "片头动画",
            subtitle = "跳过视频Logo、开场口播和长片头。",
            value = if (config.skipIntro) "已跳过" else "已保留",
            isSubItem = true,
            onClick = { plugin.setSkipIntro(!config.skipIntro) }
        )
        PluginCenterRowCard(
            title = "片尾动画",
            subtitle = "跳过结尾彩蛋前的常规片尾片段。",
            value = if (config.skipOutro) "已跳过" else "已保留",
            isSubItem = true,
            onClick = { plugin.setSkipOutro(!config.skipOutro) }
        )
        PluginCenterRowCard(
            title = "互动提示片段",
            subtitle = "跳过无意义的连播投币点赞 and 下一期提示等互动片段。",
            value = if (config.skipInteraction) "已跳过" else "已保留",
            isSubItem = true,
            onClick = { plugin.setSkipInteraction(!config.skipInteraction) }
        )
        PluginCenterRowCard(
            title = "互动推广",
            subtitle = "跳过关注、群号、店铺和其他互动推广口播。",
            value = if (config.skipSelfPromo) "已跳过" else "已保留",
            isSubItem = true,
            onClick = { plugin.setSkipSelfPromo(!config.skipSelfPromo) }
        )
        PluginCenterRowCard(
            title = "预告/回顾",
            subtitle = "默认跳过片尾广告和重复回顾，默认关闭以免误伤剧情内容。",
            value = if (config.skipPreview) "已跳过" else "已保留",
            isSubItem = true,
            onClick = { plugin.setSkipPreview(!config.skipPreview) }
        )
        PluginCenterRowCard(
            title = "跑题片段",
            subtitle = "默认跳过跑题片段，默认关闭，适合你想更激进一点的时候。",
            value = if (config.skipFiller) "已跳过" else "已保留",
            isSubItem = true,
            onClick = { plugin.setSkipFiller(!config.skipFiller) }
        )
        PluginCenterRowCard(
            title = "音乐中的非音乐片段",
            subtitle = "面向把电视当音乐播放器的场景；默认关闭，避免误伤访谈和现场内容。",
            value = "${config.musicOfftopicMode.categoryModeLabel()} -> ${nextMusicOfftopicMode.categoryModeLabel()}",
            isSubItem = true,
            onClick = { plugin.setMusicOfftopicMode(nextMusicOfftopicMode) }
        )
        ProfileInfoCard(
            "已接入播放链路",
            "空降助手会按当前分 P 加载片段；精彩点与章节会出现在播放器的空降导航中。",
            compact = true
        )
    }
}

@Composable
private fun AdFilterPluginPanel(
    plugin: com.bbttvv.app.feature.plugin.AdFilterPlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val config by plugin.configState.collectAsStateWithLifecycle(initialValue = com.bbttvv.app.feature.plugin.AdFilterConfig())
    val blockedUps by plugin.blockedUps.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddNameDialog by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    var showAddMidDialog by remember { mutableStateOf(false) }
    var inputMid by remember { mutableStateOf("") }
    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var inputKeyword by remember { mutableStateOf("") }
    var showMinViewCountDialog by remember { mutableStateOf(false) }
    var inputMinViewCount by remember { mutableStateOf("") }
    val blockedUpMidSet = remember(blockedUps) { blockedUps.map { it.mid }.toSet() }
    val manualBlockedMids = remember(config.blockedUpMids, blockedUpMidSet) {
        config.blockedUpMids.filterNot { it in blockedUpMidSet }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PluginCenterRowCard(
            title = "启用广告过滤",
            subtitle = "首页推荐、热门分区会先经过去广告增强再展示。",
            value = if (enabled) "点击关闭" else "点击切换",
            isSubItem = true,
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "过滤营销推广",
            subtitle = "过滤商业合作、恰饭推广、官方活动等营销内容。",
            value = if (config.filterSponsored) "已开启" else "已关闭",
            isSubItem = true,
            onClick = { plugin.setFilterSponsored(!config.filterSponsored) }
        )
        PluginCenterRowCard(
            title = "过滤标题党",
            subtitle = "过滤夸张标题、震惊体和常见钓鱼式标题。",
            value = if (config.filterClickbait) "已开启" else "已关闭",
            isSubItem = true,
            onClick = { plugin.setFilterClickbait(!config.filterClickbait) }
        )
        PluginCenterRowCard(
            title = "过滤低播放量",
            subtitle = "默认过滤播放量低于 1000 的内容，适合你想让瀑布流更干净时开启。",
            value = if (config.filterLowQuality) "已开启" else "已关闭",
            isSubItem = true,
            onClick = { plugin.setFilterLowQuality(!config.filterLowQuality) }
        )
        PluginCenterRowCard(
            title = "播放量阈值",
            subtitle = "低播放量过滤的实际生效，当前小于这个值的视频会被隐藏。",
            value = "${config.minViewCount}",
            isSubItem = true,
            onClick = {
                inputMinViewCount = config.minViewCount.toString()
                showMinViewCountDialog = true
            }
        )
        PluginCenterRowCard(
            title = "添加 UP 名称",
            subtitle = "按 UP 名称做模糊匹配拉黑，适合先用遥控器快速录入关键字。",
            value = if (config.blockedUpNames.isEmpty()) "去添加" else "${config.blockedUpNames.size} 个",
            isSubItem = true,
            onClick = { showAddNameDialog = true }
        )
        PluginCenterRowCard(
            title = "添加 UP MID",
            subtitle = "按 UID/MID 精确拉黑，适合你已经知道该 UP 主数字 ID 的情况。",
            value = if (manualBlockedMids.isEmpty()) "去添加" else "${manualBlockedMids.size} 个",
            isSubItem = true,
            onClick = { showAddMidDialog = true }
        )
        PluginCenterRowCard(
            title = "添加标题屏蔽词",
            subtitle = "按关键字直接过滤标题，适合屏蔽某类长期不想看的内容。",
            value = if (config.blockedKeywords.isEmpty()) "去添加" else "${config.blockedKeywords.size} 个",
            isSubItem = true,
            onClick = { showAddKeywordDialog = true }
        )
        if (config.blockedUpNames.isNotEmpty()) {
            Text(text = "名称黑名单", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            config.blockedUpNames.forEach { blockedName ->
                PluginCenterRowCard(
                    title = blockedName,
                    subtitle = "按名称匹配的黑名单规则，点按后移除。",
                    value = "移除",
                    isSubItem = true,
                    onClick = { plugin.removeBlockedUpName(blockedName) }
                )
            }
        }
        if (manualBlockedMids.isNotEmpty()) {
            Text(text = "MID 黑名单", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            manualBlockedMids.forEach { blockedMid ->
                PluginCenterRowCard(
                    title = "MID $blockedMid",
                    subtitle = "按数字 MID 精确匹配，命中后首页和热门都会直接隐藏。",
                    value = "移除",
                    isSubItem = true,
                    onClick = { plugin.removeBlockedUpMid(blockedMid) }
                )
            }
        }
        if (blockedUps.isNotEmpty()) {
            Text(text = "已拉黑该 UP", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            blockedUps.forEach { blocked ->
                PluginCenterRowCard(
                    title = blocked.name,
                    subtitle = "· 这类拉黑会直接命中 UID",
                    value = "移除",
                    isSubItem = true,
                    onClick = {
                        scope.launch {
                            plugin.unblockUploader(blocked.mid)
                        }
                    }
                )
            }
        }
        if (config.blockedKeywords.isNotEmpty()) {
            Text(text = "标题屏蔽词", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            config.blockedKeywords.forEach { blockedKeyword ->
                PluginCenterRowCard(
                    title = blockedKeyword,
                    subtitle = "命中这个关键词的标题会被直接过滤，点按后移除。",
                    value = "移除",
                    isSubItem = true,
                    onClick = { plugin.removeBlockedKeyword(blockedKeyword) }
                )
            }
        }
        if (
            config.blockedUpNames.isEmpty() &&
            manualBlockedMids.isEmpty() &&
            blockedUps.isEmpty() &&
            config.blockedKeywords.isEmpty()
        ) {
            ProfileInfoCard("规则列表还是空的", "你可以在这里维护名称黑名单、MID 黑名单和标题屏蔽词；后续接到视频详情页的“拉黑该 UP 主”动作后，这里也会直接同步显示。", compact = true)
        }
    }

    if (showAddNameDialog) {
        TvDialog(
            title = "添加名称黑名单",
            onDismissRequest = {
                showAddNameDialog = false
                inputName = ""
            },
            content = {
                TvTextInput(
                    value = inputName,
                    onValueChange = { inputName = it },
                    singleLine = true,
                    label = "UP 名称"
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showAddNameDialog = false
                        inputName = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        plugin.addBlockedUpName(inputName)
                        showAddNameDialog = false
                        inputName = ""
                    }
                )
            }
        )
    }

    if (showAddMidDialog) {
        TvDialog(
            title = "添加 MID 黑名单",
            onDismissRequest = {
                showAddMidDialog = false
                inputMid = ""
            },
            content = {
                TvTextInput(
                    value = inputMid,
                    onValueChange = { inputMid = it.filter { char -> char.isDigit() } },
                    singleLine = true,
                    label = "UP MID",
                    keyboardType = KeyboardType.Number
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showAddMidDialog = false
                        inputMid = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        inputMid.toLongOrNull()?.let(plugin::addBlockedUpMid)
                        showAddMidDialog = false
                        inputMid = ""
                    }
                )
            }
        )
    }

    if (showAddKeywordDialog) {
        TvDialog(
            title = "添加标题屏蔽词",
            onDismissRequest = {
                showAddKeywordDialog = false
                inputKeyword = ""
            },
            content = {
                TvTextInput(
                    value = inputKeyword,
                    onValueChange = { inputKeyword = it },
                    singleLine = true,
                    label = "关键字"
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showAddKeywordDialog = false
                        inputKeyword = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        plugin.addBlockedKeyword(inputKeyword)
                        showAddKeywordDialog = false
                        inputKeyword = ""
                    }
                )
            }
        )
    }

    if (showMinViewCountDialog) {
        TvDialog(
            title = "设置低播放量阈值",
            onDismissRequest = {
                showMinViewCountDialog = false
                inputMinViewCount = ""
            },
            content = {
                TvTextInput(
                    value = inputMinViewCount,
                    onValueChange = { inputMinViewCount = it.filter { char -> char.isDigit() } },
                    singleLine = true,
                    label = "最低播放量",
                    keyboardType = KeyboardType.Number
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showMinViewCountDialog = false
                        inputMinViewCount = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        inputMinViewCount.toIntOrNull()?.let(plugin::setMinViewCount)
                        showMinViewCountDialog = false
                        inputMinViewCount = ""
                    }
                )
            }
        )
    }
}

@Composable
private fun DanmakuEnhancePluginPanel(
    plugin: com.bbttvv.app.feature.plugin.DanmakuEnhancePlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val config by plugin.configState.collectAsStateWithLifecycle(initialValue = com.bbttvv.app.feature.plugin.DanmakuEnhanceConfig())
    var editingField by remember { mutableStateOf<String?>(null) }
    var editingValue by remember { mutableStateOf("") }

    fun openEditor(field: String, value: String) {
        editingField = field
        editingValue = value
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PluginCenterRowCard(
            title = "启用弹幕增强",
            subtitle = "会作用到当前播放器的弹幕载入链路，打开后支持热刷新。",
            value = if (enabled) "点击关闭" else "点击切换",
            isSubItem = true,
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "开启关键词屏蔽",
            subtitle = "按关键字隐藏剧透、前方高能等你不想看到的弹幕。",
            value = if (config.enableFilter) "已开启" else "已关闭",
            isSubItem = true,
            onClick = { plugin.setEnableFilter(!config.enableFilter) }
        )
        PluginCenterRowCard(
            title = "开启同传高亮",
            subtitle = "把同传、翻译类弹幕高亮出来，方便电视端远距离阅读。",
            value = if (config.enableHighlight) "已开启" else "已关闭",
            isSubItem = true,
            onClick = { plugin.setEnableHighlight(!config.enableHighlight) }
        )
        PluginCenterRowCard(
            title = "编辑屏蔽关键字",
            subtitle = config.blockedKeywords.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
            isSubItem = true,
            onClick = { openEditor("blocked_keywords", config.blockedKeywords) }
        )
        PluginCenterRowCard(
            title = "编辑屏蔽用户 ID",
            subtitle = config.blockedUserIds.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
            isSubItem = true,
            onClick = { openEditor("blocked_users", config.blockedUserIds) }
        )
        PluginCenterRowCard(
            title = "编辑高亮关键字",
            subtitle = config.highlightKeywords.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
            isSubItem = true,
            onClick = { openEditor("highlight_keywords", config.highlightKeywords) }
        )
    }

    if (editingField != null) {
        TvDialog(
            title = when (editingField) {
                "blocked_keywords" -> "编辑屏蔽关键字"
                "blocked_users" -> "编辑屏蔽用户 ID"
                else -> "编辑高亮关键字"
            },
            onDismissRequest = {
                editingField = null
                editingValue = ""
            },
            content = {
                TvTextInput(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    label = "使用英文逗号分隔",
                    singleLine = false,
                    minLines = 3
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        editingField = null
                        editingValue = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        when (editingField) {
                            "blocked_keywords" -> plugin.setBlockedKeywords(editingValue)
                            "blocked_users" -> plugin.setBlockedUserIds(editingValue)
                            "highlight_keywords" -> plugin.setHighlightKeywords(editingValue)
                        }
                        editingField = null
                        editingValue = ""
                    }
                )
            }
        )
    }
}

@Composable
private fun TodayWatchPluginPanel(
    plugin: com.bbttvv.app.feature.plugin.TodayWatchPlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val context = LocalContext.current
    val config by plugin.configState.collectAsStateWithLifecycle(
        initialValue = com.bbttvv.app.feature.plugin.TodayWatchPluginConfig()
    )
    val feedbackSnapshot = remember(context, config.refreshTriggerToken) {
        TodayWatchFeedbackStore.getSnapshot(context)
    }
    val creatorSignals = remember(context, config.refreshTriggerToken) {
        TodayWatchProfileStore.getCreatorSignals(context, limit = 5)
    }
    val insightState = remember(config.currentMode, feedbackSnapshot, creatorSignals) {
        buildTodayWatchTasteInsightState(
            mode = config.currentMode,
            feedbackSnapshot = feedbackSnapshot,
            creatorSignals = creatorSignals
        )
    }
    var showResetDialog by remember { mutableStateOf(false) }

    val nextMode = remember(config.currentMode) {
        when (config.currentMode) {
            com.bbttvv.app.ui.home.TodayWatchMode.RELAX -> com.bbttvv.app.ui.home.TodayWatchMode.LEARN
            com.bbttvv.app.ui.home.TodayWatchMode.LEARN -> com.bbttvv.app.ui.home.TodayWatchMode.RELAX
        }
    }
    val nextQueueBuildLimit = remember(config.queueBuildLimit) {
        nextCycledOption(config.queueBuildLimit, listOf(12, 20, 30, 40))
    }
    val nextQueuePreviewLimit = remember(config.queuePreviewLimit, config.queueBuildLimit) {
        nextCycledOption(
            config.queuePreviewLimit,
            listOf(4, 6, 8, 10).filter { it <= config.queueBuildLimit }
        )
    }
    val nextHistorySampleLimit = remember(config.historySampleLimit) {
        nextCycledOption(config.historySampleLimit, listOf(40, 80, 120))
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PluginCenterRowCard(
            title = "启用推荐单",
            subtitle = "启用后，会在首页导航栏最外挂载「推荐单」版块。",
            value = if (enabled) "点击关闭" else "点击切换",
            isSubItem = true,
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "推荐单默认模式",
            subtitle = "进入推荐单页时默认选中的二级标签。",
            value = "${config.currentMode.label} -> ${nextMode.label}",
            isSubItem = true,
            onClick = { plugin.setCurrentMode(nextMode) }
        )
        PluginCenterRowCard(
            title = "候选队列长度",
            subtitle = "用于算法内部排序的种子队列规模，越大越容易补齐多样性。",
            value = "${config.queueBuildLimit} -> $nextQueueBuildLimit",
            isSubItem = true,
            onClick = { plugin.setQueueBuildLimit(nextQueueBuildLimit) }
        )
        PluginCenterRowCard(
            title = "首页预览条数",
            subtitle = "推荐单页当前行展示前几条视频卡片。",
            value = "${config.queuePreviewLimit} -> $nextQueuePreviewLimit",
            isSubItem = true,
            onClick = { plugin.setQueuePreviewLimit(nextQueuePreviewLimit) }
        )
        PluginCenterRowCard(
            title = "历史采样数量",
            subtitle = "冷启动生成推荐单时最多抽取最近多少条历史记录。",
            value = "${config.historySampleLimit} -> $nextHistorySampleLimit",
            isSubItem = true,
            onClick = { plugin.setHistorySampleLimit(nextHistorySampleLimit) }
        )

        PluginCenterRowCard(
            title = "显示推荐理由",
            subtitle = "在视频卡片下方显示轻松向 / 学习向等推荐理由。",
            value = if (config.showReasonHint) "已开启" else "已关闭",
            isSubItem = true,
            onClick = { plugin.setShowReasonHint(!config.showReasonHint) }
        )
        TodayWatchTasteInsightSection(insightState)
        PluginCenterRowCard(
            title = "清空推荐画像",
            subtitle = "清空后台学到的创作者偏好和不感兴趣反馈，推荐会重新学习。",
            value = "立即清空",
            isSubItem = true,
            onClick = { showResetDialog = true }
        )
        ProfileInfoCard("已接入独立推荐页", "当前插件不是推荐单页顶部卡片，而是独立的外置 Tab，支持模式切换、手动刷新和 MENU 不感兴趣。", compact = true)
    }

    if (showResetDialog) {
        TvDialog(
            title = "确认清空画像与反馈？",
            onDismissRequest = { showResetDialog = false },
            content = {
                Text(
                    text = "该操作将清空本地记录的近期观看画像与“不感兴趣”视频列表，推荐偏好算法将重置到初始状态重新学习。",
                    color = Color(0xB3FFFFFF),
                    fontSize = 14.sp
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = { showResetDialog = false }
                )
                TvDialogActionButton(
                    text = "确认清空",
                    onClick = {
                        plugin.clearPersonalizationData()
                        showResetDialog = false
                    }
                )
            }
        )
    }
}

@Composable
private fun TodayWatchTasteInsightSection(
    state: TodayWatchTasteInsightState
) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val headerColor = if (isLightTheme) Color(0xFF61666D) else Color(0xE6FFFFFF)
    Text(text = "推荐依据", color = headerColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    PluginCenterStaticInfoCard(
        title = state.modeTitle,
        subtitle = state.modeSummary,
        isSubItem = true
    )
    if (state.preferredCreators.isNotEmpty()) {
        Text(text = "近期偏好 UP", color = headerColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        state.preferredCreators.forEach { signal ->
            PluginCenterStaticInfoCard(
                title = signal.label,
                subtitle = "本地观看画像信号",
                value = signal.value,
                isSubItem = true
            )
        }
    }
    Text(text = "最近不感兴趣", color = headerColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    if (state.recentDislikedVideos.isEmpty()) {
        PluginCenterStaticInfoCard(
            title = "还没有负反馈样本",
            subtitle = "在推荐单视频菜单里选择“不感兴趣”后，这里会显示近期样本。",
            isSubItem = true
        )
    } else {
        state.recentDislikedVideos.forEach { item ->
            PluginCenterStaticInfoCard(
                title = item.title,
                subtitle = item.subtitle,
                value = "已降权",
                isSubItem = true
            )
        }
    }
    if (state.negativeSignals.isNotEmpty()) {
        Text(text = "已降权信号", color = headerColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        state.negativeSignals.forEach { signal ->
            PluginCenterStaticInfoCard(
                title = signal.label,
                subtitle = "用于降低相近 content 排序权重",
                value = signal.value,
                isSubItem = true
            )
        }
    }
}

@Composable
private fun CdnRegionPluginPanel(
    plugin: CdnRegionPlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val cache by plugin.cacheState.collectAsStateWithLifecycle(initialValue = CdnRegionPluginCache())
    val locationLabel = buildCdnRegionLocationLabel(cache)
    val selectedHosts = cache.selectedHosts.take(3).joinToString("、").ifBlank { "尚未命中属地线路" }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PluginCenterRowCard(
            title = "启用属地 CDN",
            subtitle = "启用后，播放地址会先尝试同属地 bilivideo CDN，原始线路仍保留兜底。",
            value = if (enabled) "点击关闭" else "点击切换",
            isSubItem = true,
            onClick = onToggleEnabled
        )
        PluginCenterStaticInfoCard(
            title = "当前属地",
            subtitle = locationLabel,
            value = cache.selectedRegion.ifBlank { "未刷新" },
            isSubItem = true
        )
        PluginCenterStaticInfoCard(
            title = "候选线路",
            subtitle = selectedHosts,
            value = "${cache.selectedHosts.size} 条",
            isSubItem = true
        )
        PluginCenterRowCard(
            title = "刷新属地缓存",
            subtitle = if (enabled) "重新请求 B 站 IP 属地接口并更新本地 CDN 候选缓存。" else "开启插件后再刷新属地缓存。",
            value = if (enabled) "立即刷新" else "未启用",
            isSubItem = true,
            onClick = {
                if (enabled) {
                    plugin.refreshNow()
                }
            }
        )
        cache.lastError?.takeIf { it.isNotBlank() }?.let { error ->
            PluginCenterStaticInfoCard(
                title = "最近刷新失败",
                subtitle = error,
                value = "保留旧缓存",
                isSubItem = true
            )
        }
        ProfileInfoCard("已接入播放链路", "该插件只重排播放候选 URL，不删除原始 baseUrl / backupUrl；线路异常时播放器仍会继续尝试后续候选。", compact = true)
    }
}

@Composable
private fun HomeFeedAnonymizerPluginPanel(
    plugin: HomeFeedAnonymizerPlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    var refreshToken by remember { mutableStateOf(0) }
    val stats = remember(refreshToken, enabled) {
        buildHomeFeedAnonymizerStatsUiModel(
            snapshot = plugin.statsSnapshot,
            enabled = enabled
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PluginCenterRowCard(
            title = "启用匿名推荐",
            subtitle = "启用后仅让 Web 首页推荐接口不携带 Cookie，不影响播放、动态和评论。",
            value = if (enabled) "点击关闭" else "点击切换",
            isSubItem = true,
            onClick = onToggleEnabled
        )
        stats.rows.forEach { row ->
            PluginCenterStaticInfoCard(
                title = row.label,
                subtitle = row.summary,
                value = "",
                isSubItem = true
            )
        }
        PluginCenterRowCard(
            title = "刷新命中统计",
            subtitle = "重新读取本机命中次数和最近命中的推荐接口。",
            value = "刷新",
            isSubItem = true,
            onClick = { refreshToken += 1 }
        )
        PluginCenterRowCard(
            title = "重置命中统计",
            subtitle = "只清空本机统计，不修改插件启用状态和推荐数据源设置。",
            value = "重置",
            isSubItem = true,
            onClick = {
                plugin.resetStats()
                refreshToken += 1
            }
        )
        buildHomeFeedAnonymizerCreditRows().forEach { row ->
            PluginCenterStaticInfoCard(
                title = row.label,
                subtitle = row.fullContent,
                value = row.summary,
                isSubItem = true
            )
        }
        ProfileInfoCard("已接入推荐请求", "该插件只作用于网页端推荐流。若当前设置切到移动端推荐流，它不会改动移动端 access_token 请求。", compact = true)
    }
}

@Composable
private fun PluginCenterStaticInfoCard(
    title: String,
    subtitle: String,
    value: String? = null,
    isSubItem: Boolean = false
) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val containerColor = if (isSubItem) {
        Color.Transparent
    } else {
        if (isLightTheme) Color(0x0C000000) else Color(0x12000000)
    }
    val cardShape = RoundedCornerShape(if (isSubItem) 14.dp else 24.dp)
    
    val titleColor = if (isLightTheme) Color(0xFF18191C) else Color.White
    val valueColor = if (isLightTheme) Color(0xFFFB7299) else Color(0xE8FFFFFF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, cardShape)
            .padding(
                horizontal = if (isSubItem) 14.dp else 18.dp,
                vertical = if (isSubItem) 8.dp else 14.dp
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = if (isSubItem) 14.sp else 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            value?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = it,
                    color = valueColor,
                    fontSize = if (isSubItem) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PluginCenterRowCard(
    title: String,
    subtitle: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSubItem: Boolean = false,
    onDpadUp: (() -> Boolean)? = null,
    onDpadDown: (() -> Boolean)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    
    val containerColor = if (isSubItem) {
        Color.Transparent
    } else {
        if (isLightTheme) Color(0x0C000000) else Color(0x12000000)
    }
    
    val focusedContainerColor = if (isLightTheme) {
        Color(0xFFFB7299)
    } else {
        if (isSubItem) Color(0x1EFFFFFF) else Color(0xE9E6EEF4)
    }
    
    val cardShape = RoundedCornerShape(if (isSubItem) 14.dp else 24.dp)
    
    val titleColor = when {
        focused -> if (isLightTheme) Color.White else (if (isSubItem) Color.White else Color(0xFF111111))
        else -> if (isLightTheme) Color(0xFF18191C) else Color.White
    }

    val valueTextColor = when {
        focused -> if (isLightTheme) Color.White else (if (isSubItem) Color(0xE8FFFFFF) else Color(0xCC000000))
        else -> if (isLightTheme) Color(0xFFFB7299) else Color(0xE8FFFFFF)
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .onPreviewKeyEvent { keyEvent ->
                val nativeEvent = keyEvent.nativeKeyEvent
                if (nativeEvent.action != AndroidKeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }
                when (nativeEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> onDpadUp?.invoke() == true
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> onDpadDown?.invoke() == true
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(cardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = focusedContainerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isSubItem) 14.dp else 18.dp,
                    vertical = if (isSubItem) 8.dp else 14.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = if (isSubItem) 14.sp else 15.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = value,
                color = valueTextColor,
                fontSize = if (isSubItem) 12.sp else 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun resolveJsonPluginTypeLabel(type: String): String {
    return when (type.lowercase()) {
        "feed" -> "信息流控"
        "danmaku" -> "弹幕规则"
        else -> "插件"
    }
}

private fun resolvePluginCapabilitySummary(capabilities: Set<PluginCapability>): String {
    if (capabilities.isEmpty()) return "配置"
    val primaryCapabilities = capabilities
        .filterNot { it == PluginCapability.NETWORK || it == PluginCapability.PLUGIN_STORAGE }
        .ifEmpty { capabilities.toList() }
    return primaryCapabilities
        .take(2)
        .joinToString("、") { capability -> capability.label }
        .ifBlank { "配置" }
}

private val PluginCapability.label: String
    get() = when (this) {
        PluginCapability.PLAYER_STATE -> "播放状态"
        PluginCapability.PLAYER_CONTROL -> "播放控制"
        PluginCapability.DANMAKU_STREAM -> "弹幕流"
        PluginCapability.DANMAKU_MUTATION -> "弹幕增强"
        PluginCapability.PLAYBACK_CDN -> "播放 CDN"
        PluginCapability.RECOMMENDATION_CANDIDATES -> "推荐候选"
        PluginCapability.LOCAL_HISTORY_READ -> "观看历史"
        PluginCapability.LOCAL_FEEDBACK_READ -> "本地反馈"
        PluginCapability.NETWORK -> "网络访问"
        PluginCapability.PLUGIN_STORAGE -> "插件存储"
    }

private fun buildCdnRegionLocationLabel(cache: CdnRegionPluginCache): String {
    val parts = listOf(cache.location.country, cache.location.province, cache.location.city)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return when {
        parts.isNotEmpty() -> buildString {
            append(parts.joinToString(" / "))
            if (cache.fallbackUsed) append(" · 使用兜底地区")
        }
        cache.refreshedAtMs > 0L -> "已刷新，但接口没有返回明确地区。"
        else -> "启用后会自动刷新；也可以在这里手动刷新。"
    }
}

private fun <T> nextCycledOption(current: T, options: List<T>): T {
    if (options.isEmpty()) return current
    val currentIndex = options.indexOf(current)
    return if (currentIndex < 0) {
        options.first()
    } else {
        options[(currentIndex + 1) % options.size]
    }
}
