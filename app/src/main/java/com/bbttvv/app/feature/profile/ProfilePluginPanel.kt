package com.bbttvv.app.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.bbttvv.app.core.plugin.json.JsonPluginManager
import com.bbttvv.app.data.model.response.SponsorBlockMarkerMode
import com.bbttvv.app.data.model.response.displayLabel
import com.bbttvv.app.ui.components.TvConfirmDialog
import com.bbttvv.app.ui.components.TvDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.components.TvTextInput
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ProfilePluginCenterPanel() {
    val scope = rememberCoroutineScope()
    val plugins by com.bbttvv.app.core.plugin.PluginManager.pluginsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val jsonPlugins by JsonPluginManager.plugins.collectAsStateWithLifecycle(initialValue = emptyList())
    val filterStats by JsonPluginManager.filterStats.collectAsStateWithLifecycle(initialValue = emptyMap())
    val lastFilteredCount by JsonPluginManager.lastFilteredCount.collectAsStateWithLifecycle(initialValue = 0)
    var expandedPluginId by remember { mutableStateOf<String?>(null) }

    val builtInPlugins = remember(plugins) {
        listOf(
            com.bbttvv.app.feature.plugin.SPONSOR_BLOCK_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.AD_FILTER_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.DANMAKU_ENHANCE_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.TodayWatchPlugin.PLUGIN_ID
        ).mapNotNull { id ->
            plugins.firstOrNull { it.plugin.id == id }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(text = "插件中心", color = Color.White, style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PluginCenterSummaryCard("内置插件", builtInPlugins.size.toString(), Modifier.weight(1f))
                PluginCenterSummaryCard("已启用", (builtInPlugins.count { it.enabled } + jsonPlugins.count { it.enabled }).toString(), Modifier.weight(1f))
                PluginCenterSummaryCard("最近过期", lastFilteredCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text(text = "内置插件", color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        items(builtInPlugins, key = { it.plugin.id }) { pluginInfo ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PluginCenterRowCard(
                    title = pluginInfo.plugin.name,
                    subtitle = pluginInfo.plugin.description,
                    value = buildString {
                        append(if (pluginInfo.enabled) "已启用" else "已关闭")
                        append(" · 配置")
                    },
                    onClick = {
                        expandedPluginId = if (expandedPluginId == pluginInfo.plugin.id) {
                            null
                        } else {
                            pluginInfo.plugin.id
                        }
                    }
                )
                if (expandedPluginId == pluginInfo.plugin.id) {
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
                        else -> {
                            ProfileInfoCard("暂不支持的插件类型", "这个插件已经注册进插件系统，但当前插件中心还没有给它单独的 TV 配置面板。", compact = true)
                        }
                    }
                }
            }
        }
        item {
            Text(text = "外部规则插件", color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        if (jsonPlugins.isEmpty()) {
            item { ProfileInfoCard("当前还没有导入外部插件", "内置规则插件已经就位，后续如果还有外部 JSON 规则插件，可以继续在这里向下扩展。", compact = true) }
        } else {
            items(jsonPlugins, key = { it.plugin.id }) { loaded ->
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
                    value = if (loaded.enabled) "已启用" else "已关闭",
                    onClick = { JsonPluginManager.setEnabled(loaded.plugin.id, !loaded.enabled) }
                )
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PluginCenterRowCard(
            title = "插件状态",
            subtitle = "控制空降助手是否参与视频详情页的 SponsorBlock 跳过逻辑。",
            value = if (enabled) "点击关闭" else "点击切换",
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "手动跳过",
            subtitle = "命中片头、片尾或恰饭片段时直接跳过；关闭后会显示手动跳过提示。",
            value = if (config.autoSkip) "已开启" else "已关闭",
            onClick = { plugin.setAutoSkip(!config.autoSkip) }
        )
        PluginCenterRowCard(
            title = "进度条提示",
            subtitle = "切换 SponsorBlock 片段在进度条上的提示策略，便于后续预览标记。",
            value = "${config.markerMode.displayLabel()} -> ${nextMarkerMode.displayLabel()}",
            onClick = { plugin.setMarkerMode(nextMarkerMode) }
        )
        PluginCenterRowCard(
            title = "手动跳过提示",
            subtitle = "关闭后不再显示右下角“按上键跳过”提示，但不会影响进度条标记和自动跳过。",
            value = if (config.showSkipPrompt) "已开启" else "已关闭",
            onClick = { plugin.setShowSkipPrompt(!config.showSkipPrompt) }
        )
        PluginCenterRowCard(
            title = "广告 / 恰饭",
            subtitle = "命中 SponsorBlock 的赞助片段时参与跳过。",
            value = if (config.skipSponsor) "已跳过" else "已保留",
            onClick = { plugin.setSkipSponsor(!config.skipSponsor) }
        )
        PluginCenterRowCard(
            title = "片头动画",
            subtitle = "跳过视频Logo、开场口播和长片头。",
            value = if (config.skipIntro) "已跳过" else "已保留",
            onClick = { plugin.setSkipIntro(!config.skipIntro) }
        )
        PluginCenterRowCard(
            title = "片尾动画",
            subtitle = "跳过结尾彩蛋前的常规片尾片段。",
            value = if (config.skipOutro) "已跳过" else "已保留",
            onClick = { plugin.setSkipOutro(!config.skipOutro) }
        )
        PluginCenterRowCard(
            title = "互动提示",
            subtitle = "跳过无意义的连播投币点赞和下一期提示等互动片段。",
            value = if (config.skipInteraction) "已跳过" else "已保留",
            onClick = { plugin.setSkipInteraction(!config.skipInteraction) }
        )
        PluginCenterRowCard(
            title = "互动推广",
            subtitle = "跳过关注、群号、店铺和其他互动推广口播。",
            value = if (config.skipSelfPromo) "已跳过" else "已保留",
            onClick = { plugin.setSkipSelfPromo(!config.skipSelfPromo) }
        )
        PluginCenterRowCard(
            title = "预告 / 回顾",
            subtitle = "默认跳过片尾广告和重复回顾，默认关闭以免误伤剧情内容。",
            value = if (config.skipPreview) "已跳过" else "已保留",
            onClick = { plugin.setSkipPreview(!config.skipPreview) }
        )
        PluginCenterRowCard(
            title = "无关片段",
            subtitle = "默认跳过跑题片段，默认关闭，适合你想更激进一点的时候。",
            value = if (config.skipFiller) "已跳过" else "已保留",
            onClick = { plugin.setSkipFiller(!config.skipFiller) }
        )
        ProfileInfoCard("已接入播放链路", "现在插件管理器里的空降助手和实际播放器走的是同一套开关，打开后会直接作用到视频播放。", compact = true)
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PluginCenterRowCard(
            title = "插件状态",
            subtitle = "首页推荐、热门分区会先经过去广告增强再展示。",
            value = if (enabled) "点击关闭" else "点击切换",
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "营销推广过滤",
            subtitle = "过滤商业合作、恰饭推广、官方活动等营销内容。",
            value = if (config.filterSponsored) "已开启" else "已关闭",
            onClick = { plugin.setFilterSponsored(!config.filterSponsored) }
        )
        PluginCenterRowCard(
            title = "标题党过滤",
            subtitle = "过滤夸张标题、震惊体和常见钓鱼式标题。",
            value = if (config.filterClickbait) "已开启" else "已关闭",
            onClick = { plugin.setFilterClickbait(!config.filterClickbait) }
        )
        PluginCenterRowCard(
            title = "低播放量过滤",
            subtitle = "默认过滤播放量低于 1000 的内容，适合你想让瀑布流更干净时开启。",
            value = if (config.filterLowQuality) "已开启" else "已关闭",
            onClick = { plugin.setFilterLowQuality(!config.filterLowQuality) }
        )
        PluginCenterRowCard(
            title = "低播放量阈值",
            subtitle = "低播放量过滤的实际生效，当前小于这个值的视频会被隐藏。",
            value = "${config.minViewCount}",
            onClick = {
                inputMinViewCount = config.minViewCount.toString()
                showMinViewCountDialog = true
            }
        )
        PluginCenterRowCard(
            title = "添加名称黑名单",
            subtitle = "按 UP 名称做模糊匹配拉黑，适合先用遥控器快速录入关键字。",
            value = if (config.blockedUpNames.isEmpty()) "去添加" else "个",
            onClick = { showAddNameDialog = true }
        )
        PluginCenterRowCard(
            title = "添加 MID 黑名单",
            subtitle = "按 UID/MID 精确拉黑，适合你已经知道该 UP 主数字 ID 的情况。",
            value = if (manualBlockedMids.isEmpty()) "去添加" else "个",
            onClick = { showAddMidDialog = true }
        )
        PluginCenterRowCard(
            title = "标题屏蔽词",
            subtitle = "按关键字直接过滤标题，适合屏蔽某类长期不想看的内容。",
            value = if (config.blockedKeywords.isEmpty()) "去添加" else "个",
            onClick = { showAddKeywordDialog = true }
        )
        if (config.blockedUpNames.isNotEmpty()) {
            Text(text = "名称黑名单", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            config.blockedUpNames.forEach { blockedName ->
                PluginCenterRowCard(
                    title = blockedName,
                    subtitle = "按名称匹配的黑名单规则，点按后移除。",
                    value = "移除",
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PluginCenterRowCard(
            title = "插件状态",
            subtitle = "会作用到当前播放器的弹幕载入链路，打开后支持热刷新。",
            value = if (enabled) "点击关闭" else "点击切换",
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "关键词屏蔽",
            subtitle = "按关键字隐藏剧透、前方高能等你不想看到的弹幕。",
            value = if (config.enableFilter) "已开启" else "已关闭",
            onClick = { plugin.setEnableFilter(!config.enableFilter) }
        )
        PluginCenterRowCard(
            title = "同传高亮",
            subtitle = "把同传、翻译类弹幕高亮出来，方便电视端远距离阅读。",
            value = if (config.enableHighlight) "已开启" else "已关闭",
            onClick = { plugin.setEnableHighlight(!config.enableHighlight) }
        )
        PluginCenterRowCard(
            title = "屏蔽关键字",
            subtitle = config.blockedKeywords.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
            onClick = { openEditor("blocked_keywords", config.blockedKeywords) }
        )
        PluginCenterRowCard(
            title = "屏蔽用户 ID",
            subtitle = config.blockedUserIds.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
            onClick = { openEditor("blocked_users", config.blockedUserIds) }
        )
        PluginCenterRowCard(
            title = "高亮关键字",
            subtitle = config.highlightKeywords.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
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
    val config by plugin.configState.collectAsStateWithLifecycle(
        initialValue = com.bbttvv.app.feature.plugin.TodayWatchPluginConfig()
    )
    var showResetDialog by remember { mutableStateOf(false) }

    val nextMode = remember(config.currentMode) {
        when (config.currentMode) {
            com.bbttvv.app.ui.home.TodayWatchMode.RELAX -> com.bbttvv.app.ui.home.TodayWatchMode.LEARN
            com.bbttvv.app.ui.home.TodayWatchMode.LEARN -> com.bbttvv.app.ui.home.TodayWatchMode.RELAX
        }
    }
    val nextUpRankLimit = remember(config.upRankLimit) {
        nextCycledOption(config.upRankLimit, listOf(3, 5, 8, 10))
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PluginCenterRowCard(
            title = "插件状态",
            subtitle = "启用后，会在首页导航栏最外挂载「推荐单」版块。",
            value = if (enabled) "点击关闭" else "点击切换",
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "默认模式",
            subtitle = "进入推荐单页时默认选中的二级标签。",
            value = "${config.currentMode.label} -> ${nextMode.label}",
            onClick = { plugin.setCurrentMode(nextMode) }
        )
        PluginCenterRowCard(
            title = "偏好 UP 榜数量",
            subtitle = "控制头部摘要里显示多少位近期偏好创作者。",
            value = "${config.upRankLimit} -> $nextUpRankLimit",
            onClick = { plugin.setUpRankLimit(nextUpRankLimit) }
        )
        PluginCenterRowCard(
            title = "队列生成长度",
            subtitle = "用于算法内部排序的种子队列规模，越大越容易补齐多样性。",
            value = "${config.queueBuildLimit} -> $nextQueueBuildLimit",
            onClick = { plugin.setQueueBuildLimit(nextQueueBuildLimit) }
        )
        PluginCenterRowCard(
            title = "预览展示条数",
            subtitle = "推荐单页当前行展示前几条视频卡片。",
            value = "${config.queuePreviewLimit} -> $nextQueuePreviewLimit",
            onClick = { plugin.setQueuePreviewLimit(nextQueuePreviewLimit) }
        )
        PluginCenterRowCard(
            title = "历史样本数",
            subtitle = "冷启动生成推荐单时最多抽取最近多少条历史记录。",
            value = "${config.historySampleLimit} -> $nextHistorySampleLimit",
            onClick = { plugin.setHistorySampleLimit(nextHistorySampleLimit) }
        )
        PluginCenterRowCard(
            title = "显示偏好 UP 榜",
            subtitle = "在推荐单头部展示你近期更偏好的创作者摘要。",
            value = if (config.showUpRank) "已开启" else "已关闭",
            onClick = { plugin.setShowUpRank(!config.showUpRank) }
        )
        PluginCenterRowCard(
            title = "显示推荐理由",
            subtitle = "在视频卡片下方显示轻松向 / 学习向等推荐理由。",
            value = if (config.showReasonHint) "已开启" else "已关闭",
            onClick = { plugin.setShowReasonHint(!config.showReasonHint) }
        )
        PluginCenterRowCard(
            title = "清空画像与反馈",
            subtitle = "清空后台学到的创作者偏好和不感兴趣反馈，推荐会重新学习。",
            value = "立即清空",
            onClick = { showResetDialog = true }
        )
        ProfileInfoCard("已接入独立推荐页", "当前插件不是推荐单页顶部卡片，而是独立的外置 Tab，支持模式切换、手动刷新和 MENU 不感兴趣。", compact = true)
    }

    if (showResetDialog) {
        TvConfirmDialog(
            title = "清空推荐画像",
            message = "确认清空当前推荐画像与不感兴趣反馈吗？",
            onDismissRequest = { showResetDialog = false },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = { showResetDialog = false }
                )
                TvDialogActionButton(
                    text = "确认",
                    contentColor = Color(0xFFFFD0D8),
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
private fun PluginCenterSummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0x12000000), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PluginCenterRowCard(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x12000000), focusedContainerColor = Color(0xE9E6EEF4))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, color = Color(0xB3FFFFFF), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = value, color = Color(0xE8FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
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

private fun <T> nextCycledOption(current: T, options: List<T>): T {
    if (options.isEmpty()) return current
    val currentIndex = options.indexOf(current)
    return if (currentIndex < 0) {
        options.first()
    } else {
        options[(currentIndex + 1) % options.size]
    }
}
