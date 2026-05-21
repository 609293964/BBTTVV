package com.bbttvv.app.feature.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.core.store.DEFAULT_APP_USER_AGENT
import com.bbttvv.app.core.store.PlayerSettingsCache
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.store.formatPlayerVolumeCalibrationLabel
import com.bbttvv.app.core.store.nextPlayerVolumeCalibrationScale
import com.bbttvv.app.core.store.player.PlayerSettingsStore
import com.bbttvv.app.core.util.CacheUtils
import com.bbttvv.app.data.repository.BlockedUpRepository
import com.bbttvv.app.ui.components.TvDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.components.TvTextInput
import com.bbttvv.app.ui.focus.RegisterTvFocusEscapeTarget
import com.bbttvv.app.ui.focus.RegisterTvFocusReturnTarget
import com.bbttvv.app.ui.focus.isSameOrDescendantOf
import kotlinx.coroutines.launch

enum class SettingsCategory(val title: String) {
    PLAYBACK("播放设置"),
    AUDIO("音频配置"),
    UI_UX("界面交互"),
    FEED("推荐数据"),
    NETWORK("网络连接"),
    SYSTEM("系统关于")
}

private object SettingsFocusReturnKeys {
    const val Back = "settings:back"
    const val UserAgent = "settings:user_agent"
}

private const val MOBILE_FEED_TOKEN_MISSING_MESSAGE =
    "\u79FB\u52A8\u7AEF\u63A8\u8350\u6D41\u9700\u8981\u91CD\u65B0\u626B\u7801\u767B\u5F55\uFF1B" +
        "\u5F53\u524D\u4F1A\u81EA\u52A8\u56DE\u9000\u7F51\u9875\u7AEF"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val hostView = LocalView.current
    val backFocusRequester = remember { FocusRequester() }

    val categories = SettingsCategory.values()
    var selectedCategory by remember { mutableStateOf(SettingsCategory.PLAYBACK) }
    val categoryFocusRequesters = remember { List(categories.size) { FocusRequester() } }

    val playbackFirstFocus = remember { FocusRequester() }
    val audioFirstFocus = remember { FocusRequester() }
    val uiFirstFocus = remember { FocusRequester() }
    val feedFirstFocus = remember { FocusRequester() }
    val networkFirstFocus = remember { FocusRequester() }
    val systemFirstFocus = remember { FocusRequester() }

    var isFocusedInRightPanel by remember { mutableStateOf(false) }

    fun requestBackFocus(): Boolean {
        return runCatching { backFocusRequester.requestFocus() }.getOrDefault(false)
    }

    BackHandler(enabled = true) {
        if (isFocusedInRightPanel) {
            runCatching {
                categoryFocusRequesters[selectedCategory.ordinal].requestFocus()
            }
        } else {
            onBack()
        }
    }

    RegisterTvFocusReturnTarget(
        key = SettingsFocusReturnKeys.Back,
        focusRequester = backFocusRequester,
    )

    RegisterTvFocusEscapeTarget(
        key = "settings",
        acceptsFocus = { focusedView ->
            focusedView.isSameOrDescendantOf(hostView)
        },
        shouldRecoverEscapedFocus = { focusedView ->
            focusedView.rootView === hostView.rootView &&
                !focusedView.isSameOrDescendantOf(hostView)
        },
        recoverFocus = {
            requestBackFocus()
        },
    )

    LaunchedEffect(Unit) {
        withFrameNanos { }
        requestBackFocus()
    }

    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val backgroundModifier = if (isLightTheme) {
        Modifier.background(Color(0xFFF0F1F5))
    } else {
        Modifier.background(
            Brush.linearGradient(
                colors = listOf(Color(0xFF11151C), Color(0xFF0F1311), Color(0xFF090A0C))
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(backgroundModifier)
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
                SettingsBackButton(
                    modifier = Modifier.focusRequester(backFocusRequester),
                    onBack = onBack
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "设置",
                        color = if (isLightTheme) Color(0xFF18191C) else Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "系统与个性化配置",
                        color = if (isLightTheme) Color(0xFF61666D) else Color(0xB5FFFFFF),
                        fontSize = 13.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // 左侧导航分类栏
                Column(
                    modifier = Modifier
                        .width(180.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEachIndexed { index, category ->
                        val isSelected = selectedCategory == category
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            onClick = { selectedCategory = category },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(categoryFocusRequesters[index])
                                .onFocusChanged { state ->
                                    isFocused = state.isFocused
                                    if (state.isFocused) {
                                        selectedCategory = category
                                    }
                                }
                                .focusProperties {
                                    right = when (category) {
                                        SettingsCategory.PLAYBACK -> playbackFirstFocus
                                        SettingsCategory.AUDIO -> audioFirstFocus
                                        SettingsCategory.UI_UX -> uiFirstFocus
                                        SettingsCategory.FEED -> feedFirstFocus
                                        SettingsCategory.NETWORK -> networkFirstFocus
                                        SettingsCategory.SYSTEM -> systemFirstFocus
                                    }
                                },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isLightTheme) {
                                    if (isSelected) Color(0x14FB7299) else Color(0x0C000000)
                                } else {
                                    if (isSelected) Color(0x1AFFFFFF) else Color(0x05FFFFFF)
                                },
                                focusedContainerColor = if (isLightTheme) Color(0xFFFB7299) else Color(0xFF26354A)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = category.title,
                                    color = if (isFocused) {
                                        Color.White
                                    } else if (isSelected) {
                                        if (isLightTheme) Color(0xFFFB7299) else Color(0xFF00AEEC)
                                    } else {
                                        if (isLightTheme) Color(0xFF18191C) else Color(0xB5FFFFFF)
                                    },
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // 右侧选项详情面板
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { state ->
                            isFocusedInRightPanel = state.hasFocus
                        }
                ) {
                    TvSettingsList(
                        selectedCategory = selectedCategory,
                        modifier = Modifier.fillMaxSize(),
                        compact = true,
                        showBuildInfo = true,
                        categoryFocusRequesters = categoryFocusRequesters,
                        playbackFirstFocus = playbackFirstFocus,
                        audioFirstFocus = audioFirstFocus,
                        uiFirstFocus = uiFirstFocus,
                        feedFirstFocus = feedFirstFocus,
                        networkFirstFocus = networkFirstFocus,
                        systemFirstFocus = systemFirstFocus
                    )
                }
            }
        }
    }
}

@Composable
fun TvSettingsList(
    selectedCategory: SettingsCategory? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showBuildInfo: Boolean = true,
    categoryFocusRequesters: List<FocusRequester> = emptyList(),
    playbackFirstFocus: FocusRequester = remember { FocusRequester() },
    audioFirstFocus: FocusRequester = remember { FocusRequester() },
    uiFirstFocus: FocusRequester = remember { FocusRequester() },
    feedFirstFocus: FocusRequester = remember { FocusRequester() },
    networkFirstFocus: FocusRequester = remember { FocusRequester() },
    systemFirstFocus: FocusRequester = remember { FocusRequester() },
    initialFocusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blockedUpRepository = remember(context) { BlockedUpRepository(context.applicationContext) }

    val autoHighestQuality by SettingsManager.getAuto1080p(context)
        .collectAsStateWithLifecycle(initialValue = true)
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
    val playerPlaybackEndAction by SettingsManager.getPlayerPlaybackEndAction(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.PlayerPlaybackEndAction.NONE)
    val videoDetailCommentsEnabled by SettingsManager.getVideoDetailCommentsEnabled(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getVideoDetailCommentsEnabledSync(context)
        )
    val updateContentOnTabFocusEnabled by SettingsManager.getUpdateContentOnTabFocusEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val watchLaterInTopTabsEnabled by SettingsManager.getWatchLaterInTopTabsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val dynamicPageDisplayMode by SettingsManager.getDynamicPageDisplayMode(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getDynamicPageDisplayModeSync(context)
        )
    val singleBackToHomeEnabled by SettingsManager.getSingleBackToHomeEnabled(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getSingleBackToHomeEnabledSync(context)
        )
    val themeMode by SettingsManager.getThemeMode(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getThemeModeSync(context)
        )
    val rememberLastSpeed by PlayerSettingsStore.getRememberLastPlaybackSpeed(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val preferredSpeed by PlayerSettingsStore.getPreferredPlaybackSpeed(context)
        .collectAsStateWithLifecycle(initialValue = 1.0f)
    val volumeCalibrationScale by PlayerSettingsStore.getVolumeCalibrationScale(context)
        .collectAsStateWithLifecycle(initialValue = 1.0f)
    val audioBalanceLevel by PlayerSettingsStore.getAudioBalanceLevel(context)
        .collectAsStateWithLifecycle(initialValue = com.bbttvv.app.core.player.AudioBalanceLevel.Off)
    val audioPassthrough by PlayerSettingsStore.getAudioPassthrough(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val blockedUps by blockedUpRepository.getAllBlockedUps()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var cacheSize by remember { mutableStateOf("\u8BA1\u7B97\u4E2D...") }
    var cacheRefreshTick by remember { mutableIntStateOf(0) }
    var isClearingCache by remember { mutableStateOf(false) }
    var showUserAgentDialog by remember { mutableStateOf(false) }
    var userAgentDraft by remember { mutableStateOf(DEFAULT_APP_USER_AGENT) }
    val userAgentFocusRequester = remember { FocusRequester() }

    RegisterTvFocusReturnTarget(
        key = SettingsFocusReturnKeys.UserAgent,
        focusRequester = userAgentFocusRequester,
    )

    var lastFocusedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedCategory) {
        lastFocusedKey = null
    }

    val defaultFirstKey = when (selectedCategory) {
        null -> "settings_auto_highest_quality"
        SettingsCategory.PLAYBACK -> "settings_auto_highest_quality"
        SettingsCategory.AUDIO -> "settings_volume_calibration"
        SettingsCategory.UI_UX -> "settings_show_online_count"
        SettingsCategory.FEED -> "settings_feed_api_type"
        SettingsCategory.NETWORK -> "settings_user_agent"
        SettingsCategory.SYSTEM -> "settings_privacy_mode"
    }

    val targetFirstKey = lastFocusedKey ?: defaultFirstKey

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isFocusedInRightPanel by remember { mutableStateOf(false) }

    val currentCategoryFocusRequester = when (selectedCategory) {
        SettingsCategory.PLAYBACK -> playbackFirstFocus
        SettingsCategory.AUDIO -> audioFirstFocus
        SettingsCategory.UI_UX -> uiFirstFocus
        SettingsCategory.FEED -> feedFirstFocus
        SettingsCategory.NETWORK -> networkFirstFocus
        SettingsCategory.SYSTEM -> systemFirstFocus
        null -> null
    }

    LaunchedEffect(targetFirstKey, selectedCategory, isFocusedInRightPanel) {
        if (!isFocusedInRightPanel && selectedCategory != null) {
            val targetIndex = getIndexForKey(targetFirstKey, selectedCategory)
            runCatching {
                listState.scrollToItem(targetIndex)
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
        val leftFocus = if (selectedCategory != null && categoryFocusRequesters.size > selectedCategory.ordinal) {
            categoryFocusRequesters[selectedCategory.ordinal]
        } else {
            null
        }
        val leftModifier = leftFocus?.let { requester ->
            Modifier.focusProperties { left = requester }
        } ?: Modifier

        val getRowModifier: (String, Modifier) -> Modifier = { key, customBase ->
            var itemModifier = customBase
            if (initialFocusRequester != null && targetFirstKey == key) {
                itemModifier = itemModifier.focusRequester(initialFocusRequester)
            }
            if (currentCategoryFocusRequester != null && targetFirstKey == key) {
                itemModifier = itemModifier.focusRequester(currentCategoryFocusRequester)
            }
            itemModifier.onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    lastFocusedKey = key
                }
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.PLAYBACK) {
            item(key = "settings_playback_title") { SettingsSectionTitle("播放设置", compact = compact) }
            item(key = "settings_auto_highest_quality") {
                SettingsRow(
                    title = "仅加载最高分辨率",
                    subtitle = "播放时优先请求接口返回的最高画质，关闭后按默认画质策略选择。",
                    value = onOff(autoHighestQuality),
                    compact = compact,
                    modifier = getRowModifier("settings_auto_highest_quality", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setAuto1080p(context, !autoHighestQuality)
                        }
                    }
                )
            }
            item(key = "settings_remember_last_speed") {
                SettingsRow(
                    title = "记住上次播放倍速",
                    subtitle = "自动应用上次观看视频时选择的播放速度。",
                    value = onOff(rememberLastSpeed),
                    compact = compact,
                    modifier = getRowModifier("settings_remember_last_speed", leftModifier),
                    onClick = {
                        scope.launch {
                            PlayerSettingsStore.setRememberLastPlaybackSpeed(context, !rememberLastSpeed)
                        }
                    }
                )
            }
            item(key = "settings_default_speed") {
                SettingsRow(
                    title = "默认倍速",
                    subtitle = "循环切换 0.75x / 1.0x / 1.25x / 1.5x / 2.0x。",
                    value = "${preferredSpeed}x",
                    compact = compact,
                    modifier = getRowModifier("settings_default_speed", leftModifier),
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
            item(key = "settings_auto_resume") {
                SettingsRow(
                    title = "自动跳到上次播放位置",
                    subtitle = "打开视频时，自动从上次观看中断处继续播放。",
                    value = onOff(playerAutoResumeEnabled),
                    compact = compact,
                    modifier = getRowModifier("settings_auto_resume", leftModifier),
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
            item(key = "settings_playback_end") {
                SettingsRow(
                    title = "播放结束后",
                    subtitle = resolvePlayerPlaybackEndActionDescription(playerPlaybackEndAction),
                    value = playerPlaybackEndAction.label,
                    compact = compact,
                    modifier = getRowModifier("settings_playback_end", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setPlayerPlaybackEndAction(
                                context,
                                nextPlayerPlaybackEndAction(playerPlaybackEndAction)
                            )
                        }
                    }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.AUDIO) {
            item(key = "settings_audio_title") { SettingsSectionTitle("音频配置", compact = compact) }
            item(key = "settings_volume_calibration") {
                SettingsRow(
                    title = "应用音量校准",
                    subtitle = "调整 BBTTVV 播放输出音量，不改变电视系统音量。超过 100% 可能失真。",
                    value = formatPlayerVolumeCalibrationLabel(volumeCalibrationScale),
                    compact = compact,
                    modifier = getRowModifier("settings_volume_calibration", leftModifier),
                    onClick = {
                        scope.launch {
                            val nextScale = nextPlayerVolumeCalibrationScale(volumeCalibrationScale)
                            PlayerSettingsCache.updateVolumeCalibrationScale(nextScale)
                            PlayerSettingsStore.setVolumeCalibrationScale(context, nextScale)
                        }
                    }
                )
            }
            item(key = "settings_volume_balance") {
                SettingsRow(
                    title = "音量均衡",
                    subtitle = "自动调整不同视频的音量差异，避免切换视频时音量忽大忽小。",
                    value = audioBalanceLevel.label,
                    compact = compact,
                    modifier = getRowModifier("settings_volume_balance", leftModifier),
                    onClick = {
                        val nextLevel = nextAudioBalanceLevel(audioBalanceLevel)
                        com.bbttvv.app.core.player.VolumeBalanceController.setLevel(nextLevel)
                        PlayerSettingsCache.updateAudioBalanceLevel(nextLevel)
                        scope.launch {
                            PlayerSettingsStore.setAudioBalanceLevel(context, nextLevel)
                        }
                    }
                )
            }
            item(key = "settings_audio_passthrough") {
                SettingsRow(
                    title = "音频直通 [实验性]",
                    subtitle = "将压缩音频（如杜比全景声、Hi-Res）不经解码直接输出到外接音频设备。开启后音量均衡、倍速调节将失效。需要设备支持对应编码格式。",
                    value = onOff(audioPassthrough),
                    compact = compact,
                    modifier = getRowModifier("settings_audio_passthrough", leftModifier),
                    onClick = {
                        val newValue = !audioPassthrough
                        PlayerSettingsCache.updateAudioPassthrough(newValue)
                        scope.launch {
                            PlayerSettingsStore.setAudioPassthrough(context, newValue)
                        }
                    }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.UI_UX) {
            item(key = "settings_ui_ux_title") { SettingsSectionTitle("界面与交互", compact = compact) }
            item(key = "settings_show_online_count") {
                SettingsRow(
                    title = "在线观看人数",
                    subtitle = "控制播放页右上角是否显示当前视频的在线观看人数。",
                    value = onOff(showOnlineCount),
                    compact = compact,
                    modifier = getRowModifier("settings_show_online_count", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setShowOnlineCount(context, !showOnlineCount)
                        }
                    }
                )
            }
            item(key = "settings_video_detail_comments") {
                SettingsRow(
                    title = "视频详情页评论",
                    subtitle = "控制视频详情页底部评论区是否显示和加载。",
                    value = onOff(videoDetailCommentsEnabled),
                    compact = compact,
                    modifier = getRowModifier("settings_video_detail_comments", leftModifier),
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
            item(key = "settings_update_content_on_tab_focus") {
                SettingsRow(
                    title = "“我的”页面焦点刷新",
                    subtitle = if (updateContentOnTabFocusEnabled) {
                        "已开启：在“我的”页左侧 TAB 上移动焦点时，会立即刷新右侧内容。"
                    } else {
                        "已关闭：焦点移动只限于 TAB，需按确定键后才刷新右侧内容。"
                    },
                    value = if (updateContentOnTabFocusEnabled) "开启" else "关闭",
                    compact = compact,
                    modifier = getRowModifier("settings_update_content_on_tab_focus", leftModifier),
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
            item(key = "settings_watch_later_in_top_tabs") {
                SettingsRow(
                    title = "稍后再看入口位置",
                    subtitle = if (watchLaterInTopTabsEnabled) {
                        "当前显示在首页顶部 Tabs 中，并从“我的”左侧菜单隐藏。"
                    } else {
                        "当前显示在“我的”左侧菜单中，并从首页顶部 Tabs 隐藏。"
                    },
                    value = if (watchLaterInTopTabsEnabled) "顶部 Tabs" else "我的菜单",
                    compact = compact,
                    modifier = getRowModifier("settings_watch_later_in_top_tabs", leftModifier),
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
            item(key = "settings_single_back_to_home") {
                SettingsRow(
                    title = "相关视频一键回首页",
                    subtitle = "开启后，在相关视频多次跳转时，点击一次返回键即可直接回到首页。",
                    value = onOff(singleBackToHomeEnabled),
                    compact = compact,
                    modifier = getRowModifier("settings_single_back_to_home", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setSingleBackToHomeEnabled(
                                context,
                                !singleBackToHomeEnabled
                            )
                        }
                    }
                )
            }
            item(key = "settings_theme_mode") {
                SettingsRow(
                    title = "系统主题模式",
                    subtitle = "切换应用的全局主题界面。亮色主题下播放器、评论 and 设置等区域都将升级为高级磨砂白玻璃拟态加黑色文字界面。",
                    value = themeMode.label,
                    compact = compact,
                    modifier = getRowModifier("settings_theme_mode", leftModifier),
                    onClick = {
                        scope.launch {
                            val next = if (themeMode == SettingsManager.ThemeMode.DARK) {
                                SettingsManager.ThemeMode.LIGHT
                            } else {
                                SettingsManager.ThemeMode.DARK
                            }
                            SettingsManager.setThemeMode(context, next)
                        }
                    }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.FEED) {
            item(key = "settings_feed_title") { SettingsSectionTitle("推荐与数据", compact = compact) }
            item(key = "settings_feed_api_type") {
                SettingsRow(
                    title = "推荐页数据源",
                    subtitle = resolveFeedApiTypeDescription(feedApiType),
                    value = resolveFeedApiTypeLabel(feedApiType),
                    compact = compact,
                    modifier = getRowModifier("settings_feed_api_type", leftModifier),
                    onClick = {
                        scope.launch {
                            val next = nextFeedApiType(feedApiType)
                            if (
                                next == SettingsManager.FeedApiType.MOBILE &&
                                TokenManager.accessTokenCache.isNullOrBlank()
                            ) {
                                Toast.makeText(
                                    context,
                                    MOBILE_FEED_TOKEN_MISSING_MESSAGE,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            SettingsManager.setFeedApiType(context, next)
                        }
                    }
                )
            }
            item(key = "settings_home_refresh_count") {
                SettingsRow(
                    title = "首页刷新数量",
                    subtitle = "单次刷新时拉取的推荐视频数量。",
                    value = homeRefreshCount.toString(),
                    compact = compact,
                    modifier = getRowModifier("settings_home_refresh_count", leftModifier),
                    onClick = {
                        scope.launch {
                            val next = if (homeRefreshCount >= 40) 10 else homeRefreshCount + 5
                            SettingsManager.setHomeRefreshCount(context, next)
                        }
                    }
                )
            }
            item(key = "settings_dynamic_page_display_mode") {
                SettingsRow(
                    title = "动态页显示管理",
                    subtitle = resolveDynamicPageDisplayModeDescription(dynamicPageDisplayMode),
                    value = dynamicPageDisplayMode.label,
                    compact = compact,
                    modifier = getRowModifier("settings_dynamic_page_display_mode", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setDynamicPageDisplayMode(
                                context,
                                nextDynamicPageDisplayMode(dynamicPageDisplayMode)
                            )
                        }
                    }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.NETWORK) {
            item(key = "settings_network_title") { SettingsSectionTitle("网络与连接", compact = compact) }
            item(key = "settings_user_agent") {
                SettingsRow(
                    title = "User-Agent",
                    subtitle = "当前：${buildUserAgentPreview(userAgent)}",
                    value = if (userAgent == DEFAULT_APP_USER_AGENT) "默认" else "自定义",
                    compact = compact,
                    modifier = getRowModifier(
                        "settings_user_agent",
                        leftModifier.focusRequester(userAgentFocusRequester)
                    ),
                    onClick = {
                        userAgentDraft = userAgent
                        showUserAgentDialog = true
                    }
                )
            }
            item(key = "settings_ipv4_only") {
                SettingsRow(
                    title = "是否只允许使用IPV4",
                    subtitle = "开启后只走 IPv4 解析；在双栈网络异常时可作为兼容开关。",
                    value = onOff(ipv4OnlyEnabled),
                    compact = compact,
                    modifier = getRowModifier("settings_ipv4_only", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setIpv4OnlyEnabled(context, !ipv4OnlyEnabled)
                        }
                    }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.SYSTEM) {
            item(key = "settings_system_title") { SettingsSectionTitle("系统与关于", compact = compact) }
            item(key = "settings_privacy_mode") {
                SettingsRow(
                    title = "隐私无痕模式",
                    subtitle = "关闭搜索历史写入，并跳过观看心跳上报。",
                    value = onOff(privacyMode),
                    compact = compact,
                    modifier = getRowModifier("settings_privacy_mode", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setPrivacyModeEnabled(context, !privacyMode)
                        }
                    }
                )
            }
            item(key = "settings_blocked_ups_count") {
                SettingsRow(
                    title = "黑名单数量",
                    subtitle = "当前已屏蔽的 UP 数量。",
                    value = blockedUps.size.toString(),
                    compact = compact,
                    modifier = getRowModifier("settings_blocked_ups_count", leftModifier),
                    enabled = false,
                    onClick = {}
                )
            }
            item(key = "settings_clear_cache") {
                SettingsRow(
                    title = "清除缓存",
                    subtitle = if (isClearingCache) {
                        "正在清理图片、网络与播放缓存..."
                    } else {
                        "清理预览图、网络缓存和播放器残留。"
                    },
                    value = if (isClearingCache) "清理中..." else cacheSize,
                    compact = compact,
                    modifier = getRowModifier("settings_clear_cache", leftModifier),
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
                item(key = "settings_version") {
                    SettingsRow(
                        title = "版本",
                        subtitle = "当前安装包版本号。",
                        value = BuildConfig.VERSION_NAME,
                        compact = compact,
                        modifier = getRowModifier("settings_version", leftModifier),
                        enabled = false,
                        onClick = {}
                    )
                }
                item(key = "settings_build_type") {
                    SettingsRow(
                        title = "构建类型",
                        subtitle = "用于区分 debug / release。",
                        value = BuildConfig.BUILD_TYPE,
                        compact = compact,
                        modifier = leftModifier,
                        enabled = false,
                        onClick = {}
                    )
                }
                item(key = "settings_package_name") {
                    SettingsRow(
                        title = "应用包名",
                        subtitle = "当前安装在设备上的包标识。",
                        value = context.packageName,
                        compact = compact,
                        modifier = leftModifier,
                        enabled = false,
                        onClick = {}
                    )
                }
            }
        }
    }

    if (showUserAgentDialog) {
        TvDialog(
            title = "\u7F16\u8F91 User-Agent",
            onDismissRequest = { showUserAgentDialog = false },
            returnFocusKey = SettingsFocusReturnKeys.UserAgent,
            returnFocusFallbackKeys = listOf(SettingsFocusReturnKeys.Back),
            content = {
                TvTextInput(
                    value = userAgentDraft,
                    onValueChange = { userAgentDraft = it },
                    label = "User-Agent",
                    supportingText = "\u7559\u7A7A\u4F1A\u81EA\u52A8\u56DE\u9000\u5230\u9ED8\u8BA4\u6D4F\u89C8\u5668\u6807\u8BC6\u3002",
                    singleLine = false,
                    minLines = 3,
                    keyboardType = KeyboardType.Uri
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "\u6062\u590D\u9ED8\u8BA4",
                    onClick = {
                        userAgentDraft = DEFAULT_APP_USER_AGENT
                        scope.launch {
                            SettingsManager.setUserAgent(context, DEFAULT_APP_USER_AGENT)
                            showUserAgentDialog = false
                        }
                    }
                )
                TvDialogActionButton(
                    text = "\u53D6\u6D88",
                    onClick = { showUserAgentDialog = false }
                )
                TvDialogActionButton(
                    text = "\u4FDD\u5B58",
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
private fun SettingsBackButton(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current

    val containerColor = if (isLightTheme) Color(0x0C000000) else Color(0x14000000)
    val focusedContainerColor = if (isLightTheme) Color(0xFFFB7299) else Color.White
    
    val textColor = when {
        isFocused -> if (isLightTheme) Color.White else Color(0xFF111111)
        else -> if (isLightTheme) Color(0xFF18191C) else Color.White
    }

    Surface(
        onClick = onBack,
        modifier = modifier,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = focusedContainerColor
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\u8FD4\u56DE", color = textColor, fontSize = 15.sp)
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String, compact: Boolean) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Text(
        text = title,
        color = if (isLightTheme) Color(0xFF61666D) else Color(0x8FFFFFFF),
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current

    val titleColor = when {
        isLightTheme -> when {
            !enabled -> Color(0x78000000)
            isFocused -> Color.White
            else -> Color(0xFF18191C)
        }
        else -> when {
            !enabled -> Color(0x78FFFFFF)
            isFocused -> Color.White
            else -> Color.White
        }
    }
    
    val subtitleColor = when {
        isLightTheme -> when {
            !enabled -> Color(0x5F000000)
            isFocused -> Color(0xB3FFFFFF)
            else -> Color(0xFF61666D)
        }
        else -> when {
            !enabled -> Color(0x5FFFFFFF)
            isFocused -> Color(0xB5FFFFFF)
            else -> Color(0x8FFFFFFF)
        }
    }
    
    val valueContainerColor = when {
        isLightTheme -> when {
            !enabled -> Color(0x0A000000)
            isFocused -> Color(0x24FFFFFF)
            else -> Color(0x0F000000)
        }
        else -> when {
            !enabled -> Color(0x0FFFFFFF)
            isFocused -> Color(0x24FFFFFF)
            else -> Color(0x0DFFFFFF)
        }
    }
    
    val valueTextColor = when {
        isLightTheme -> when {
            !enabled -> Color(0x72000000)
            isFocused -> Color.White
            else -> Color(0xFFFB7299)
        }
        else -> when {
            !enabled -> Color(0x72FFFFFF)
            isFocused -> Color.White
            else -> Color(0xCCFFFFFF)
        }
    }

    val containerColor = if (isLightTheme) Color(0x0C000000) else Color(0x12000000)
    val focusedContainerColor = if (isLightTheme) Color(0xFFFB7299) else Color(0xFF26354A)

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(if (compact) 10.dp else 12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = focusedContainerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 44.dp else 62.dp)
                .padding(horizontal = if (compact) 14.dp else 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 2.dp)
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!compact) {
                    Text(
                        text = subtitle,
                        color = subtitleColor,
                        fontSize = if (compact) 9.sp else 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .background(
                        color = valueContainerColor,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(
                        horizontal = if (compact) 10.dp else 12.dp,
                        vertical = if (compact) 5.dp else 6.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    color = valueTextColor,
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

internal fun onOff(enabled: Boolean): String = if (enabled) "\u5F00" else "\u5173"

private fun nextFeedApiType(type: SettingsManager.FeedApiType): SettingsManager.FeedApiType {
    return when (type) {
        SettingsManager.FeedApiType.WEB -> SettingsManager.FeedApiType.MOBILE
        SettingsManager.FeedApiType.MOBILE -> SettingsManager.FeedApiType.WEB
        SettingsManager.FeedApiType.NORMAL -> SettingsManager.FeedApiType.WEB
    }
}

private fun resolveFeedApiTypeLabel(type: SettingsManager.FeedApiType): String {
    return when (type) {
        SettingsManager.FeedApiType.WEB -> "\u7F51\u9875\u7AEF (Web)"
        SettingsManager.FeedApiType.MOBILE -> "\u79FB\u52A8\u7AEF (App)"
        SettingsManager.FeedApiType.NORMAL -> "\u517C\u5BB9\u6A21\u5F0F"
    }
}

private fun resolveFeedApiTypeDescription(type: SettingsManager.FeedApiType): String {
    return when (type) {
        SettingsManager.FeedApiType.WEB -> "\u63A8\u8350\u9875\u4F7F\u7528\u7F51\u9875\u7AEF\u63A8\u8350\u63A5\u53E3\u3002"
        SettingsManager.FeedApiType.MOBILE -> "\u63A8\u8350\u9875\u4F18\u5148\u4F7F\u7528\u79FB\u52A8\u7AEF\u63A8\u8350\u63A5\u53E3\uFF0C\u5931\u8D25\u540E\u56DE\u9000\u7F51\u9875\u7AEF\u3002"
        SettingsManager.FeedApiType.NORMAL -> "\u63A8\u8350\u9875\u4F7F\u7528\u517C\u5BB9\u6A21\u5F0F\u3002"
    }
}

private fun nextDynamicPageDisplayMode(
    mode: SettingsManager.DynamicPageDisplayMode
): SettingsManager.DynamicPageDisplayMode {
    return when (mode) {
        SettingsManager.DynamicPageDisplayMode.LIVE -> SettingsManager.DynamicPageDisplayMode.DYNAMIC
        SettingsManager.DynamicPageDisplayMode.DYNAMIC -> SettingsManager.DynamicPageDisplayMode.ALL
        SettingsManager.DynamicPageDisplayMode.ALL -> SettingsManager.DynamicPageDisplayMode.NONE
        SettingsManager.DynamicPageDisplayMode.NONE -> SettingsManager.DynamicPageDisplayMode.LIVE
    }
}

private fun resolveDynamicPageDisplayModeDescription(
    mode: SettingsManager.DynamicPageDisplayMode
): String {
    return when (mode) {
        SettingsManager.DynamicPageDisplayMode.LIVE -> "\u52A8\u6001\u9875\u4EC5\u663E\u793A\u5173\u6CE8\u76F4\u64AD\u6A2A\u680F\uFF0C\u9690\u85CF\u5173\u6CE8\u66F4\u65B0\u6A2A\u680F\u3002"
        SettingsManager.DynamicPageDisplayMode.DYNAMIC -> "\u52A8\u6001\u9875\u4EC5\u663E\u793A\u5173\u6CE8\u66F4\u65B0\u6A2A\u680F\uFF0C\u9690\u85CF\u5173\u6CE8\u76F4\u64AD\u6A2A\u680F\u3002"
        SettingsManager.DynamicPageDisplayMode.ALL -> "\u52A8\u6001\u9875\u540C\u65F6\u663E\u793A\u5173\u6CE8\u76F4\u64AD\u4E0E\u5173\u6CE8\u66F4\u65B0\u6A2A\u680F\u3002"
        SettingsManager.DynamicPageDisplayMode.NONE -> "\u52A8\u6001\u9875\u9690\u85CF\u5173\u6CE8\u76F4\u64AD\u4E0E\u5173\u6CE8\u66F4\u65B0\u6A2A\u680F\uFF0C\u53EA\u4FDD\u7559\u89C6\u9891\u5217\u8868\u3002"
    }
}

private fun nextPlayerPlaybackEndAction(
    action: SettingsManager.PlayerPlaybackEndAction
): SettingsManager.PlayerPlaybackEndAction {
    return when (action) {
        SettingsManager.PlayerPlaybackEndAction.NONE -> SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT
        SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT -> SettingsManager.PlayerPlaybackEndAction.LOOP_ONE
        SettingsManager.PlayerPlaybackEndAction.LOOP_ONE -> SettingsManager.PlayerPlaybackEndAction.RETURN
        SettingsManager.PlayerPlaybackEndAction.RETURN -> SettingsManager.PlayerPlaybackEndAction.NONE
    }
}

private fun resolvePlayerPlaybackEndActionDescription(
    action: SettingsManager.PlayerPlaybackEndAction
): String {
    return when (action) {
        SettingsManager.PlayerPlaybackEndAction.NONE -> "\u64AD\u653E\u7ED3\u675F\u540E\u505C\u7559\u5728\u64AD\u653E\u5668\uFF0C\u6309\u786E\u8BA4\u952E\u53EF\u4ECE\u5934\u64AD\u653E\u3002"
        SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT -> "\u4F18\u5148\u64AD\u653E\u4E0B\u4E00\u5206P\uFF0C\u6CA1\u6709\u4E0B\u4E00\u5206P\u65F6\u64AD\u653E\u76F8\u5173\u63A8\u8350\u3002"
        SettingsManager.PlayerPlaybackEndAction.LOOP_ONE -> "\u5F53\u524D\u89C6\u9891\u7ED3\u675F\u540E\u81EA\u52A8\u4ECE\u5934\u5FAA\u73AF\u64AD\u653E\u3002"
        SettingsManager.PlayerPlaybackEndAction.RETURN -> "\u64AD\u653E\u7ED3\u675F\u540E\u8FD4\u56DE\u4E0A\u4E00\u9875\uFF1B\u4ECE\u8BE6\u60C5\u9875\u8FDB\u5165\u65F6\u4F1A\u56DE\u5230\u8BE6\u60C5\u9875\u3002"
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

private fun nextAudioBalanceLevel(current: com.bbttvv.app.core.player.AudioBalanceLevel): com.bbttvv.app.core.player.AudioBalanceLevel {
    val ordered = com.bbttvv.app.core.player.AudioBalanceLevel.ordered
    val index = ordered.indexOf(current)
    return if (index == -1 || index == ordered.lastIndex) ordered.first() else ordered[index + 1]
}

private fun buildUserAgentPreview(userAgent: String): String {
    return userAgent.trim().replace(Regex("\\s+"), " ").take(52)
}

private fun getIndexForKey(key: String, category: SettingsCategory): Int {
    return when (category) {
        SettingsCategory.PLAYBACK -> when (key) {
            "settings_playback_title" -> 0
            "settings_auto_highest_quality" -> 1
            "settings_remember_last_speed" -> 2
            "settings_default_speed" -> 3
            "settings_auto_resume" -> 4
            "settings_playback_end" -> 5
            else -> 1
        }
        SettingsCategory.AUDIO -> when (key) {
            "settings_audio_title" -> 0
            "settings_volume_calibration" -> 1
            "settings_volume_balance" -> 2
            "settings_audio_passthrough" -> 3
            else -> 1
        }
        SettingsCategory.UI_UX -> when (key) {
            "settings_ui_ux_title" -> 0
            "settings_show_online_count" -> 1
            "settings_video_detail_comments" -> 2
            "settings_update_content_on_tab_focus" -> 3
            "settings_watch_later_in_top_tabs" -> 4
            "settings_single_back_to_home" -> 5
            "settings_theme_mode" -> 6
            else -> 1
        }
        SettingsCategory.FEED -> when (key) {
            "settings_feed_title" -> 0
            "settings_feed_api_type" -> 1
            "settings_home_refresh_count" -> 2
            "settings_dynamic_page_display_mode" -> 3
            else -> 1
        }
        SettingsCategory.NETWORK -> when (key) {
            "settings_network_title" -> 0
            "settings_user_agent" -> 1
            "settings_ipv4_only" -> 2
            else -> 1
        }
        SettingsCategory.SYSTEM -> when (key) {
            "settings_system_title" -> 0
            "settings_privacy_mode" -> 1
            "settings_blocked_ups_count" -> 2
            "settings_clear_cache" -> 3
            "settings_version" -> 4
            "settings_build_type" -> 5
            "settings_package_name" -> 6
            else -> 1
        }
    }
}
