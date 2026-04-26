package com.bbttvv.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.core.store.DEFAULT_APP_USER_AGENT
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.player.PlayerSettingsStore
import com.bbttvv.app.core.util.CacheUtils
import com.bbttvv.app.data.repository.BlockedUpRepository
import com.bbttvv.app.ui.components.TvDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.components.TvTextInput
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF11151C), Color(0xFF0F1311), Color(0xFF090A0C))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 46.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsBackButton(onBack = onBack)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "设置",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "设置",
                        color = Color(0xB5FFFFFF),
                        fontSize = 15.sp
                    )
                }
            }

            TvSettingsList(
                modifier = Modifier.fillMaxSize(),
                compact = false,
                showBuildInfo = true
            )
        }
    }
}

@Composable
fun TvSettingsList(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showBuildInfo: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blockedUpRepository = remember(context) { BlockedUpRepository(context.applicationContext) }

    val autoHighestQuality by SettingsManager.getAuto1080p(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val playerCdnPreference by SettingsManager.getPlayerCdnPreference(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.PlayerCdnPreference.BILIVIDEO)
    val showOnlineCount by SettingsManager.getShowOnlineCount(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val privacyMode by SettingsManager.getPrivacyModeEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val feedApiType by SettingsManager.getFeedApiType(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.FeedApiType.WEB)
    val homeRefreshCount by SettingsManager.getHomeRefreshCount(context)
        .collectAsStateWithLifecycle(initialValue = 20)
    val userAgent by SettingsManager.getUserAgent(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_APP_USER_AGENT)
    val ipv4OnlyEnabled by SettingsManager.getIpv4OnlyEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val playerAutoResumeEnabled by SettingsManager.getPlayerAutoResumeEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val videoDetailCommentsEnabled by SettingsManager.getVideoDetailCommentsEnabled(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getVideoDetailCommentsEnabledSync(context)
        )
    val updateContentOnTabFocusEnabled by SettingsManager.getUpdateContentOnTabFocusEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val watchLaterInTopTabsEnabled by SettingsManager.getWatchLaterInTopTabsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val rememberLastSpeed by PlayerSettingsStore.getRememberLastPlaybackSpeed(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val preferredSpeed by PlayerSettingsStore.getPreferredPlaybackSpeed(context)
        .collectAsStateWithLifecycle(initialValue = 1.0f)
    val blockedUps by blockedUpRepository.getAllBlockedUps()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var cacheSize by remember { mutableStateOf("计算中...") }
    var cacheRefreshTick by remember { mutableIntStateOf(0) }
    var isClearingCache by remember { mutableStateOf(false) }
    var showUserAgentDialog by remember { mutableStateOf(false) }
    var userAgentDraft by remember { mutableStateOf(DEFAULT_APP_USER_AGENT) }

    LaunchedEffect(cacheRefreshTick) {
        cacheSize = CacheUtils.getTotalCacheSize(context)
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        contentPadding = PaddingValues(bottom = if (compact) 20.dp else 32.dp)
    ) {
        item { SettingsSectionTitle("播放与界面", compact = compact) }
        item {
            SettingsRow(
                title = "仅加载最高分辨率",
                subtitle = "播放时优先请求接口返回的最高画质，关闭后按默认画质策略选择。",
                value = onOff(autoHighestQuality),
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setAuto1080p(context, !autoHighestQuality)
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "CDN线路",
                subtitle = resolvePlayerCdnDescription(playerCdnPreference),
                value = playerCdnPreference.label,
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setPlayerCdnPreference(
                            context = context,
                            preference = nextPlayerCdnPreference(playerCdnPreference)
                        )
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "在线观看人数",
                subtitle = "控制播放页右上角是否显示当前视频的在线观看人数。",
                value = onOff(showOnlineCount),
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setShowOnlineCount(context, !showOnlineCount)
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "记住上次播放倍速",
                subtitle = "自动应用上次观看视频时选择的播放速度。",
                value = onOff(rememberLastSpeed),
                compact = compact,
                onClick = {
                    scope.launch {
                        PlayerSettingsStore.setRememberLastPlaybackSpeed(context, !rememberLastSpeed)
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "默认倍速",
                subtitle = "循环切换 0.75x / 1.0x / 1.25x / 1.5x / 2.0x。",
                value = "${preferredSpeed}x",
                compact = compact,
                onClick = {
                    scope.launch {
                        PlayerSettingsStore.setDefaultPlaybackSpeed(
                            context,
                            nextPlaybackSpeed(preferredSpeed)
                        )
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "自动跳到上次播放位置",
                subtitle = "打开视频时，自动从上次观看中断处继续播放。",
                value = onOff(playerAutoResumeEnabled),
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setPlayerAutoResumeEnabled(
                            context,
                            !playerAutoResumeEnabled
                        )
                    }
                }
            )
        }

        item { SettingsSectionTitle("详情页", compact = compact) }
        item {
            SettingsRow(
                title = "视频详情页评论",
                subtitle = "控制视频详情页底部评论区是否显示和加载。",
                value = onOff(videoDetailCommentsEnabled),
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setVideoDetailCommentsEnabled(
                            context,
                            !videoDetailCommentsEnabled
                        )
                    }
                }
            )
        }

        item { SettingsSectionTitle("动态与首页", compact = compact) }
        item {
            SettingsRow(
                title = "动态数据源",
                subtitle = resolveFeedApiTypeDescription(feedApiType),
                value = resolveFeedApiTypeLabel(feedApiType),
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setFeedApiType(context, nextFeedApiType(feedApiType))
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "首页刷新数量",
                subtitle = "单次刷新时拉取的推荐视频数量。",
                value = homeRefreshCount.toString(),
                compact = compact,
                onClick = {
                    scope.launch {
                        val next = if (homeRefreshCount >= 40) 10 else homeRefreshCount + 5
                        SettingsManager.setHomeRefreshCount(context, next)
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "\"我的\"页面焦点刷新",
                subtitle = if (updateContentOnTabFocusEnabled) {
                    "已开启：在“我的”页左侧 TAB 上移动焦点时，会立即刷新右侧内容。"
                } else {
                    "已关闭：焦点移动只限于 TAB，需按确定键后才刷新右侧内容。"
                },
                value = if (updateContentOnTabFocusEnabled) "开启" else "关闭",
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setUpdateContentOnTabFocusEnabled(
                            context,
                            !updateContentOnTabFocusEnabled
                        )
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "稍后再看入口位置",
                subtitle = if (watchLaterInTopTabsEnabled) {
                    "当前显示在首页顶部 Tabs 中，并从“我的”左侧菜单隐藏。"
                } else {
                    "当前显示在“我的”左侧菜单中，并从首页顶部 Tabs 隐藏。"
                },
                value = if (watchLaterInTopTabsEnabled) "顶部 Tabs" else "我的菜单",
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setWatchLaterInTopTabsEnabled(
                            context,
                            !watchLaterInTopTabsEnabled
                        )
                    }
                }
            )
        }

        item { SettingsSectionTitle("网络访问", compact = compact) }
        item {
            SettingsRow(
                title = "User-Agent",
                subtitle = "当前：${buildUserAgentPreview(userAgent)}",
                value = if (userAgent == DEFAULT_APP_USER_AGENT) "默认" else "自定义",
                compact = compact,
                onClick = {
                    userAgentDraft = userAgent
                    showUserAgentDialog = true
                }
            )
        }
        item {
            SettingsRow(
                title = "是否只允许使用IPV4",
                subtitle = "开启后只走 IPv4 解析；在双栈网络异常时可作为兼容开关。",
                value = onOff(ipv4OnlyEnabled),
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setIpv4OnlyEnabled(context, !ipv4OnlyEnabled)
                    }
                }
            )
        }

        item { SettingsSectionTitle("隐私与缓存", compact = compact) }
        item {
            SettingsRow(
                title = "隐私无痕模式",
                subtitle = "关闭搜索历史写入，并跳过观看心跳上报。",
                value = onOff(privacyMode),
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setPrivacyModeEnabled(context, !privacyMode)
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "黑名单数量",
                subtitle = "当前已屏蔽的 UP 数量。",
                value = blockedUps.size.toString(),
                compact = compact,
                enabled = false,
                onClick = {}
            )
        }
        item {
            SettingsRow(
                title = "清除缓存",
                subtitle = if (isClearingCache) {
                    "正在清理图片、网络与播放缓存..."
                } else {
                    "清理预览图、网络缓存和播放器残留。"
                },
                value = if (isClearingCache) "清理中..." else cacheSize,
                compact = compact,
                enabled = !isClearingCache,
                onClick = {
                    scope.launch {
                        isClearingCache = true
                        CacheUtils.clearAllCache(context)
                        cacheRefreshTick += 1
                        isClearingCache = false
                    }
                }
            )
        }

        if (showBuildInfo) {
            item { SettingsSectionTitle("当前构建", compact = compact) }
            item {
                SettingsRow(
                    title = "版本",
                    subtitle = "当前安装包版本号。",
                    value = BuildConfig.VERSION_NAME,
                    compact = compact,
                    enabled = false,
                    onClick = {}
                )
            }
            item {
                SettingsRow(
                    title = "构建类型",
                    subtitle = "用于区分 debug / release。",
                    value = BuildConfig.BUILD_TYPE,
                    compact = compact,
                    enabled = false,
                    onClick = {}
                )
            }
            item {
                SettingsRow(
                    title = "应用包名",
                    subtitle = "当前安装在设备上的包标识。",
                    value = context.packageName,
                    compact = compact,
                    enabled = false,
                    onClick = {}
                )
            }
        }
    }

    if (showUserAgentDialog) {
        TvDialog(
            title = "编辑 User-Agent",
            onDismissRequest = { showUserAgentDialog = false },
            content = {
                TvTextInput(
                    value = userAgentDraft,
                    onValueChange = { userAgentDraft = it },
                    label = "User-Agent",
                    supportingText = "留空会自动回退到默认浏览器标识。",
                    singleLine = false,
                    minLines = 3,
                    keyboardType = KeyboardType.Uri
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "恢复默认",
                    onClick = {
                        userAgentDraft = DEFAULT_APP_USER_AGENT
                        scope.launch {
                            SettingsManager.setUserAgent(context, DEFAULT_APP_USER_AGENT)
                            showUserAgentDialog = false
                        }
                    }
                )
                TvDialogActionButton(
                    text = "取消",
                    onClick = { showUserAgentDialog = false }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        scope.launch {
                            SettingsManager.setUserAgent(context, userAgentDraft)
                            showUserAgentDialog = false
                        }
                    }
                )
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsBackButton(onBack: () -> Unit) {
    Surface(
        onClick = onBack,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14000000),
            focusedContainerColor = Color.White
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "返回", color = Color.White, fontSize = 15.sp)
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String, compact: Boolean) {
    Text(
        text = title,
        color = Color(0x8FFFFFFF),
        fontSize = if (compact) 11.sp else 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 6.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String,
    value: String,
    compact: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val titleColor = when {
        !enabled -> Color(0x78FFFFFF)
        isFocused -> Color(0xFFF8FBFF)
        else -> Color.White
    }
    val subtitleColor = when {
        !enabled -> Color(0x5FFFFFFF)
        isFocused -> Color(0xE1EDF9)
        else -> Color(0x9FFFFFFF)
    }
    val valueContainerColor = when {
        !enabled -> Color(0x0FFFFFFF)
        isFocused -> Color(0x1FFFFFFF)
        else -> Color(0x0DFFFFFF)
    }
    val valueTextColor = when {
        !enabled -> Color(0x72FFFFFF)
        isFocused -> Color(0xFFF8FBFF)
        else -> Color(0xCCFFFFFF)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(if (compact) 24.dp else 28.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x12000000),
            focusedContainerColor = Color(0xFF31445E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 70.dp else 82.dp)
                .padding(horizontal = if (compact) 18.dp else 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = if (compact) 15.sp else 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = if (compact) 11.sp else 13.sp,
                    maxLines = 2
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        color = valueContainerColor,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(
                        horizontal = if (compact) 12.dp else 14.dp,
                        vertical = if (compact) 7.dp else 8.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    color = valueTextColor,
                    fontSize = if (compact) 13.sp else 16.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

internal fun onOff(enabled: Boolean): String = if (enabled) "开" else "关"

private fun nextFeedApiType(type: SettingsManager.FeedApiType): SettingsManager.FeedApiType {
    return when (type) {
        SettingsManager.FeedApiType.WEB -> SettingsManager.FeedApiType.MOBILE
        SettingsManager.FeedApiType.MOBILE -> SettingsManager.FeedApiType.WEB
        SettingsManager.FeedApiType.NORMAL -> SettingsManager.FeedApiType.WEB
    }
}

private fun resolveFeedApiTypeLabel(type: SettingsManager.FeedApiType): String {
    return when (type) {
        SettingsManager.FeedApiType.WEB -> "网页端 (Web)"
        SettingsManager.FeedApiType.MOBILE -> "移动端 (App)"
        SettingsManager.FeedApiType.NORMAL -> "兼容模式"
    }
}

private fun resolveFeedApiTypeDescription(type: SettingsManager.FeedApiType): String {
    return when (type) {
        SettingsManager.FeedApiType.WEB -> "网页端 (Web)"
        SettingsManager.FeedApiType.MOBILE -> "移动端 (App)"
        SettingsManager.FeedApiType.NORMAL -> "兼容模式"
    }
}

private fun nextPlayerCdnPreference(
    preference: SettingsManager.PlayerCdnPreference
): SettingsManager.PlayerCdnPreference {
    return when (preference) {
        SettingsManager.PlayerCdnPreference.BILIVIDEO -> SettingsManager.PlayerCdnPreference.MCDN
        SettingsManager.PlayerCdnPreference.MCDN -> SettingsManager.PlayerCdnPreference.BILIVIDEO
    }
}

private fun resolvePlayerCdnDescription(
    preference: SettingsManager.PlayerCdnPreference
): String {
    return when (preference) {
        SettingsManager.PlayerCdnPreference.BILIVIDEO -> "默认线路，兼容性更稳；遇到卡顿可尝试切到 mcdn。"
        SettingsManager.PlayerCdnPreference.MCDN -> "部分网络可能更快或更慢；若播放异常请切回 bilivideo。"
    }
}

private fun nextPlaybackSpeed(speed: Float): Float {
    return when (speed) {
        0.75f -> 1.0f
        1.0f -> 1.25f
        1.25f -> 1.5f
        1.5f -> 2.0f
        else -> 0.75f
    }
}

private fun buildUserAgentPreview(userAgent: String): String {
    return userAgent.trim().replace(Regex("\\s+"), " ").take(52)
}

