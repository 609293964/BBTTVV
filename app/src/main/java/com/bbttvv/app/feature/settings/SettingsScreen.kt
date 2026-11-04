package com.bbttvv.app.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.core.store.DEFAULT_APP_USER_AGENT
import com.bbttvv.app.core.store.PLAYER_PLAYBACK_SPEED_PRESETS
import com.bbttvv.app.core.store.PLAYER_VOLUME_CALIBRATION_SCALES
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.store.formatPlayerVolumeCalibrationLabel
import com.bbttvv.app.core.store.player.PlayerSettingsStore
import com.bbttvv.app.core.update.AppUpdateCheckResult
import com.bbttvv.app.core.update.AppUpdateDownloadResult
import com.bbttvv.app.core.update.AppUpdateInstaller
import com.bbttvv.app.core.update.AppUpdateRepository
import com.bbttvv.app.core.update.AvailableAppUpdate
import com.bbttvv.app.core.update.resolveUpdateNetworkError
import com.bbttvv.app.core.util.CacheUtils
import com.bbttvv.app.data.repository.BlockedUpRepository
import com.bbttvv.app.data.model.VideoQuality
import com.bbttvv.app.feature.plugin.isAllowedCustomCdnHost
import com.bbttvv.app.ui.components.HomeTabLayout
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.LocalTvNoticeHostState
import com.bbttvv.app.ui.components.TvDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.components.TvNoticeKind
import com.bbttvv.app.ui.components.TvTextInput
import com.bbttvv.app.ui.focus.RegisterTvFocusEscapeTarget
import com.bbttvv.app.ui.focus.RegisterTvFocusReturnTarget
import com.bbttvv.app.ui.focus.isSameOrDescendantOf
import com.bbttvv.app.ui.theme.LocalTvSemanticColors
import kotlinx.coroutines.launch

private object SettingsFocusReturnKeys {
    const val Back = "settings:back"
    const val UserAgent = "settings:user_agent"
    const val CustomCdnHost = "settings:custom_cdn_host"
    const val CheckUpdate = "settings:check_update"
}

private enum class SettingsChoice(val rowKey: String) {
    THEME("settings_theme_mode"),
    PGC_QUALITY("settings_pgc_preferred_quality"),
    DEFAULT_SPEED("settings_default_speed"),
    PLAYBACK_END("settings_playback_end"),
    VOLUME_CALIBRATION("settings_volume_calibration"),
    AUDIO_BALANCE("settings_volume_balance"),
    FEED_API("settings_feed_api_type"),
    HOME_REFRESH_COUNT("settings_home_refresh_count"),
    DYNAMIC_PAGE_MODE("settings_dynamic_page_display_mode"),
}

internal enum class SettingsRowKind {
    Toggle,
    Choice,
    Navigation,
    Action,
    Info,
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
    var selectedCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SettingsCategory.PLAYBACK) }
    val categoryFocusRequesters = remember { List(categories.size) { FocusRequester() } }

    val playbackFirstFocus = remember { FocusRequester() }
    val danmakuFirstFocus = remember { FocusRequester() }
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
            runCatching {
                categoryFocusRequesters[selectedCategory.ordinal].requestFocus()
            }.getOrDefault(false) || requestBackFocus()
        },
    )

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { categoryFocusRequesters[selectedCategory.ordinal].requestFocus() }
    }

    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val semanticColors = LocalTvSemanticColors.current
    val backgroundModifier = if (isLightTheme) {
        Modifier.background(semanticColors.pageBackground)
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
                        color = semanticColors.primaryText,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "选择分类，按右键进入",
                        color = semanticColors.secondaryText,
                        fontSize = 13.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 左侧导航分类栏
                Column(
                    modifier = Modifier
                        .width(148.dp)
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
                                        SettingsCategory.DANMAKU -> danmakuFirstFocus
                                        SettingsCategory.AUDIO -> audioFirstFocus
                                        SettingsCategory.UI_UX -> uiFirstFocus
                                        SettingsCategory.FEED -> feedFirstFocus
                                        SettingsCategory.NETWORK -> networkFirstFocus
                                        SettingsCategory.SYSTEM -> systemFirstFocus
                                    }
                                },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            border = ClickableSurfaceDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, semanticColors.accent),
                                    shape = RoundedCornerShape(10.dp),
                                ),
                            ),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isLightTheme) {
                                    if (isSelected) Color(0x14FB7299) else Color(0x0C000000)
                                } else {
                                    if (isSelected) Color(0x1AFFFFFF) else Color(0x05FFFFFF)
                                },
                                focusedContainerColor = semanticColors.focusContainer
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
                                        semanticColors.focusContent
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
                    if (selectedCategory == SettingsCategory.DANMAKU) {
                        TvDanmakuSettingsList(
                            modifier = Modifier.fillMaxSize(),
                            leftFocusRequester = categoryFocusRequesters[selectedCategory.ordinal],
                            initialFocusRequester = danmakuFirstFocus,
                        )
                    } else {
                        TvSettingsList(
                            selectedCategory = selectedCategory,
                            modifier = Modifier.fillMaxSize(),
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
}

@Composable
fun TvSettingsList(
    modifier: Modifier = Modifier,
    selectedCategory: SettingsCategory? = null,
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
    val tvNoticeHostState = LocalTvNoticeHostState.current
    val scope = rememberCoroutineScope()
    val blockedUpRepository = remember(context) { BlockedUpRepository(context.applicationContext) }
    val updateRepository = remember(context) { AppUpdateRepository(context.applicationContext) }

    val appSettings by SettingsManager.getSettingsSnapshot(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getSettingsSnapshotSync(context))
    val playerSettings by PlayerSettingsStore.getSettingsSnapshot(context)
        .collectAsStateWithLifecycle(initialValue = PlayerSettingsStore.getSettingsSnapshotSync(context))
    val autoHighestQuality = appSettings.autoHighestQuality
    val showOnlineCount = appSettings.showOnlineCount
    val privacyMode = appSettings.privacyMode
    val feedApiType = appSettings.feedApiType
    val homeRefreshCount = appSettings.homeRefreshCount
    val userAgent = appSettings.userAgent
    val ipv4OnlyEnabled = appSettings.ipv4OnlyEnabled
    val strictCustomCdnEnabled = appSettings.strictCustomCdnEnabled
    val customCdnHost = appSettings.customCdnHost
    val playerAutoResumeEnabled = appSettings.playerAutoResumeEnabled
    val playerPlaybackEndAction = appSettings.playerPlaybackEndAction
    val videoDetailCommentsEnabled = appSettings.videoDetailCommentsEnabled
    val updateContentOnTabFocusEnabled = appSettings.updateContentOnTabFocusEnabled
    val homeTopTabSelectOnFocusEnabled = appSettings.homeTopTabSelectOnFocusEnabled
    var showTabEditor by remember { mutableStateOf(false) }
    val tabEditorFocus = remember { FocusRequester() }
    RegisterTvFocusReturnTarget(key = "settings_home_tabs", focusRequester = tabEditorFocus)
    val dynamicPageDisplayMode = appSettings.dynamicPageDisplayMode
    val singleBackToHomeEnabled = appSettings.singleBackToHomeEnabled
    val themeMode = appSettings.themeMode
    val automaticCacheThresholdMb = appSettings.automaticCacheThresholdMb
    val rememberLastSpeed = playerSettings.rememberLastPlaybackSpeed
    val defaultPlaybackSpeed = playerSettings.defaultPlaybackSpeed
    val interactiveVideoEnabled = playerSettings.interactiveVideoEnabled
    val pgcPreferredQuality = playerSettings.pgcPreferredQuality
    val volumeCalibrationScale = playerSettings.volumeCalibrationScale
    val audioBalanceLevel = playerSettings.audioBalanceLevel
    val audioPassthrough = playerSettings.audioPassthrough
    val blockedUps by blockedUpRepository.getAllBlockedUps()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var cacheSize by remember { mutableStateOf("\u8BA1\u7B97\u4E2D...") }
    var cacheRefreshTick by remember { mutableIntStateOf(0) }
    var isClearingCache by remember { mutableStateOf(false) }
    var showUserAgentDialog by remember { mutableStateOf(false) }
    var showCustomCdnDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AvailableAppUpdate?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var customCdnValidationMessage by remember { mutableStateOf<String?>(null) }
    var activeChoice by remember { mutableStateOf<SettingsChoice?>(null) }
    var userAgentDraft by remember { mutableStateOf(DEFAULT_APP_USER_AGENT) }
    var customCdnHostDraft by remember { mutableStateOf("") }
    val userAgentFocusRequester = remember { FocusRequester() }
    val customCdnHostFocusRequester = remember { FocusRequester() }
    val checkUpdateFocusRequester = remember { FocusRequester() }
    val choiceFocusRequesters = remember {
        SettingsChoice.entries.associateWith { FocusRequester() }
    }
    val choiceAnchorBounds = remember { mutableStateMapOf<SettingsChoice, Rect>() }

    fun Modifier.captureChoiceAnchor(choice: SettingsChoice): Modifier {
        return onGloballyPositioned { coordinates ->
            choiceAnchorBounds[choice] = coordinates.boundsInWindow()
        }
    }

    LaunchedEffect(context, cacheRefreshTick) {
        cacheSize = "\u8BA1\u7B97\u4E2D..."
        cacheSize = runCatching {
            CacheUtils.getTotalCacheSize(context.applicationContext)
        }.getOrElse {
            "\u8BA1\u7B97\u5931\u8D25"
        }
    }

    LaunchedEffect(feedApiType) {
        if (feedApiType == SettingsManager.FeedApiType.NORMAL) {
            SettingsManager.setFeedApiType(context, SettingsManager.FeedApiType.WEB)
        }
    }

    RegisterTvFocusReturnTarget(
        key = SettingsFocusReturnKeys.UserAgent,
        focusRequester = userAgentFocusRequester,
    )
    RegisterTvFocusReturnTarget(
        key = SettingsFocusReturnKeys.CheckUpdate,
        focusRequester = checkUpdateFocusRequester,
    )
    RegisterTvFocusReturnTarget(
        key = SettingsFocusReturnKeys.CustomCdnHost,
        focusRequester = customCdnHostFocusRequester,
    )
    SettingsChoice.entries.forEach { choice ->
        RegisterTvFocusReturnTarget(
            key = choice.rowKey,
            focusRequester = choiceFocusRequesters.getValue(choice),
        )
    }

    var lastFocusedKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    val defaultFirstKey = when (selectedCategory) {
        null -> "settings_auto_highest_quality"
        SettingsCategory.PLAYBACK -> "settings_auto_highest_quality"
        SettingsCategory.DANMAKU -> "danmaku_default_enabled"
        SettingsCategory.AUDIO -> "settings_volume_calibration"
        SettingsCategory.UI_UX -> "settings_show_online_count"
        SettingsCategory.FEED -> "settings_feed_api_type"
        SettingsCategory.NETWORK -> "settings_user_agent"
        SettingsCategory.SYSTEM -> "settings_privacy_mode"
    }

    val targetFirstKey = lastFocusedKey
        ?.takeIf { key ->
            selectedCategory == null || key in SettingsCatalog.keysByCategory.getValue(selectedCategory)
        }
        ?: defaultFirstKey

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var isFocusedInRightPanel by remember { mutableStateOf(false) }

    val currentCategoryFocusRequester = when (selectedCategory) {
        SettingsCategory.PLAYBACK -> playbackFirstFocus
        SettingsCategory.DANMAKU -> null
        SettingsCategory.AUDIO -> audioFirstFocus
        SettingsCategory.UI_UX -> uiFirstFocus
        SettingsCategory.FEED -> feedFirstFocus
        SettingsCategory.NETWORK -> networkFirstFocus
        SettingsCategory.SYSTEM -> systemFirstFocus
        null -> null
    }

    LaunchedEffect(targetFirstKey, selectedCategory, isFocusedInRightPanel) {
        if (!isFocusedInRightPanel && selectedCategory != null) {
            val targetIndex = SettingsCatalog.itemIndex(selectedCategory, targetFirstKey)
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
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
            item(key = "settings_playback_title") { SettingsSectionTitle("播放设置") }
            item(key = "settings_auto_highest_quality") {
                SettingsRow(
                    title = "优先最高画质",
                    value = onOff(autoHighestQuality),
                    modifier = getRowModifier("settings_auto_highest_quality", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setAuto1080p(context, !autoHighestQuality)
                        }
                    }
                )
            }
            item(key = "settings_pgc_preferred_quality") {
                SettingsRow(
                    title = "番剧和影视画质",
                    value = pgcPreferredQuality
                        ?.let { VideoQuality.fromCode(it)?.description ?: it.toString() }
                        ?: "跟随普通视频",
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_pgc_preferred_quality",
                        leftModifier.focusRequester(
                            choiceFocusRequesters.getValue(SettingsChoice.PGC_QUALITY)
                        ).captureChoiceAnchor(SettingsChoice.PGC_QUALITY),
                    ),
                    onClick = { activeChoice = SettingsChoice.PGC_QUALITY },
                )
            }
            item(key = "settings_default_speed") {
                SettingsRow(
                    title = "起始倍速",
                    value = if (rememberLastSpeed) "记住上次" else "${defaultPlaybackSpeed}x",
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_default_speed",
                        leftModifier.focusRequester(
                            choiceFocusRequesters.getValue(SettingsChoice.DEFAULT_SPEED)
                        ).captureChoiceAnchor(SettingsChoice.DEFAULT_SPEED),
                    ),
                    onClick = { activeChoice = SettingsChoice.DEFAULT_SPEED }
                )
            }
            item(key = "settings_auto_resume") {
                SettingsRow(
                    title = "自动续播",
                    value = onOff(playerAutoResumeEnabled),
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
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_playback_end",
                        leftModifier.focusRequester(
                            choiceFocusRequesters.getValue(SettingsChoice.PLAYBACK_END)
                        ).captureChoiceAnchor(SettingsChoice.PLAYBACK_END),
                    ),
                    onClick = { activeChoice = SettingsChoice.PLAYBACK_END }
                )
            }
            item(key = "settings_interactive_video") {
                SettingsRow(
                    title = "视频互动卡片",
                    subtitle = "包含投票、评分和剧情分支。",
                    value = onOff(interactiveVideoEnabled),
                    modifier = getRowModifier("settings_interactive_video", leftModifier),
                    onClick = {
                        scope.launch {
                            PlayerSettingsStore.setInteractiveVideoEnabled(context, !interactiveVideoEnabled)
                        }
                    }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.AUDIO) {
            item(key = "settings_audio_title") { SettingsSectionTitle("音频配置") }
            item(key = "settings_volume_calibration") {
                SettingsRow(
                    title = "应用音量",
                    subtitle = "超过 100% 可能失真。",
                    value = formatPlayerVolumeCalibrationLabel(volumeCalibrationScale),
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_volume_calibration",
                        leftModifier.focusRequester(
                            choiceFocusRequesters.getValue(SettingsChoice.VOLUME_CALIBRATION)
                        ).captureChoiceAnchor(SettingsChoice.VOLUME_CALIBRATION),
                    ),
                    onClick = { activeChoice = SettingsChoice.VOLUME_CALIBRATION }
                )
            }
            item(key = "settings_volume_balance") {
                SettingsRow(
                    title = "音量均衡",
                    subtitle = "减小不同视频间的音量差异。",
                    value = audioBalanceLevel.label,
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_volume_balance",
                        leftModifier.focusRequester(
                            choiceFocusRequesters.getValue(SettingsChoice.AUDIO_BALANCE)
                        ).captureChoiceAnchor(SettingsChoice.AUDIO_BALANCE),
                    ),
                    onClick = { activeChoice = SettingsChoice.AUDIO_BALANCE }
                )
            }
            item(key = "settings_audio_passthrough") {
                SettingsRow(
                    title = "音频直通（实验）",
                    subtitle = "需音源和外接设备支持；跳过音量均衡，下次进入播放器生效。",
                    value = onOff(audioPassthrough),
                    modifier = getRowModifier("settings_audio_passthrough", leftModifier),
                    onClick = {
                        val newValue = !audioPassthrough
                        scope.launch {
                            PlayerSettingsStore.setAudioPassthrough(context, newValue)
                        }
                    }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.UI_UX) {
            item(key = "settings_ui_ux_title") { SettingsSectionTitle("界面与交互") }
            item(key = "settings_show_online_count") {
                SettingsRow(
                    title = "在线观看人数",
                    value = onOff(showOnlineCount),
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
                    title = "详情页评论",
                    value = onOff(videoDetailCommentsEnabled),
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
                    title = "我的栏目切换",
                    value = if (updateContentOnTabFocusEnabled) "移到即切换" else "按确认切换",
                    modifier = getRowModifier("settings_update_content_on_tab_focus", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setProfileUpdateContentOnTabFocusEnabled(
                                context,
                                !updateContentOnTabFocusEnabled
                            )
                        }
                    }
                )
            }
            item(key = "settings_home_top_tab_select_on_focus") {
                SettingsRow(
                    title = "首页栏目切换",
                    value = if (homeTopTabSelectOnFocusEnabled) "移到即切换" else "按确认切换",
                    modifier = getRowModifier("settings_home_top_tab_select_on_focus", leftModifier),
                    onClick = {
                        scope.launch {
                            SettingsManager.setHomeTopTabSelectOnFocusEnabled(
                                context,
                                !homeTopTabSelectOnFocusEnabled,
                            )
                        }
                    }
                )
            }
            item(key = "settings_home_tabs") {
                SettingsRow(
                    title = "编辑导航栏",
                    value = "编辑",
                    modifier = getRowModifier("settings_home_tabs", leftModifier.focusRequester(tabEditorFocus)),
                    onClick = { showTabEditor = true },
                )
            }
            item(key = "settings_single_back_to_home") {
                SettingsRow(
                    title = "相关视频一键回首页",
                    subtitle = "从相关视频连续跳转后，按返回直接回首页。",
                    value = onOff(singleBackToHomeEnabled),
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
                    title = "主题",
                    value = themeMode.label,
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_theme_mode",
                        leftModifier.focusRequester(choiceFocusRequesters.getValue(SettingsChoice.THEME))
                            .captureChoiceAnchor(SettingsChoice.THEME),
                    ),
                    onClick = { activeChoice = SettingsChoice.THEME }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.FEED) {
            item(key = "settings_feed_title") { SettingsSectionTitle("推荐与数据") }
            item(key = "settings_feed_api_type") {
                SettingsRow(
                    title = "推荐数据源",
                    value = resolveFeedApiTypeLabel(feedApiType),
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_feed_api_type",
                        leftModifier.focusRequester(
                            choiceFocusRequesters.getValue(SettingsChoice.FEED_API)
                        ).captureChoiceAnchor(SettingsChoice.FEED_API),
                    ),
                    onClick = { activeChoice = SettingsChoice.FEED_API }
                )
            }
            item(key = "settings_home_refresh_count") {
                SettingsRow(
                    title = "推荐请求数量",
                    subtitle = if (feedApiType == SettingsManager.FeedApiType.MERGED) "分别向网页端和移动端请求，合并后去重。" else "",
                    value = homeRefreshCount.toString(),
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_home_refresh_count",
                        leftModifier.focusRequester(
                            choiceFocusRequesters.getValue(SettingsChoice.HOME_REFRESH_COUNT)
                        ).captureChoiceAnchor(SettingsChoice.HOME_REFRESH_COUNT),
                    ),
                    onClick = { activeChoice = SettingsChoice.HOME_REFRESH_COUNT }
                )
            }
            item(key = "settings_dynamic_page_display_mode") {
                SettingsRow(
                    title = "动态页内容",
                    value = dynamicPageDisplayMode.label,
                    kind = SettingsRowKind.Choice,
                    modifier = getRowModifier(
                        "settings_dynamic_page_display_mode",
                        leftModifier.focusRequester(
                            choiceFocusRequesters.getValue(SettingsChoice.DYNAMIC_PAGE_MODE)
                        ).captureChoiceAnchor(SettingsChoice.DYNAMIC_PAGE_MODE),
                    ),
                    onClick = { activeChoice = SettingsChoice.DYNAMIC_PAGE_MODE }
                )
            }
        }

        if (selectedCategory == null || selectedCategory == SettingsCategory.NETWORK) {
            item(key = "settings_network_title") { SettingsSectionTitle("网络与连接") }
            item(key = "settings_custom_cdn_host") {
                SettingsRow(
                    title = "自定义 CDN",
                    subtitle = "仅支持 bilivideo.com 域名。",
                    value = customCdnHost.ifBlank { "未配置" },
                    kind = SettingsRowKind.Navigation,
                    modifier = getRowModifier(
                        "settings_custom_cdn_host",
                        leftModifier.focusRequester(customCdnHostFocusRequester),
                    ),
                    onClick = {
                        customCdnHostDraft = customCdnHost
                        customCdnValidationMessage = null
                        showCustomCdnDialog = true
                    },
                )
            }
            item(key = "settings_strict_custom_cdn") {
                SettingsRow(
                    title = "严格使用自定义 CDN",
                    subtitle = "失败时也不切换到其他线路。",
                    value = onOff(strictCustomCdnEnabled),
                    modifier = getRowModifier("settings_strict_custom_cdn", leftModifier),
                    onClick = {
                        if (!strictCustomCdnEnabled && !isAllowedCustomCdnHost(customCdnHost)) {
                            tvNoticeHostState.show(
                                message = "请先配置有效的 bilivideo.com CDN 主机",
                                kind = TvNoticeKind.Error,
                            )
                        } else {
                            scope.launch {
                                SettingsManager.setStrictCustomCdnEnabled(
                                    context,
                                    !strictCustomCdnEnabled,
                                )
                            }
                        }
                    },
                )
            }
            item(key = "settings_user_agent") {
                SettingsRow(
                    title = "User-Agent",
                    value = if (userAgent == DEFAULT_APP_USER_AGENT) "默认" else "自定义",
                    kind = SettingsRowKind.Navigation,
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
                    title = "仅使用 IPv4",
                    subtitle = "网络连接异常时可尝试开启。",
                    value = onOff(ipv4OnlyEnabled),
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
            item(key = "settings_system_title") { SettingsSectionTitle("系统与关于") }
            item(key = "settings_privacy_mode") {
                SettingsRow(
                    title = "无痕模式",
                    subtitle = "不记录搜索历史，不上报观看进度。",
                    value = onOff(privacyMode),
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
                    title = "已屏蔽的 UP 主",
                    value = blockedUps.size.toString(),
                    kind = SettingsRowKind.Info,
                    modifier = getRowModifier("settings_blocked_ups_count", leftModifier),
                    enabled = false,
                    onClick = {}
                )
            }
            item(key = "settings_automatic_cache_cleanup") {
                SettingsRow(
                    title = "启动时清理缓存",
                    subtitle = "启动时检查图片和网络缓存，超出右侧容量则清理。",
                    value = if (automaticCacheThresholdMb <= 0) {
                        "关闭"
                    } else {
                        "${automaticCacheThresholdMb} MB"
                    },
                    modifier = getRowModifier("settings_automatic_cache_cleanup", leftModifier),
                    onClick = {
                        scope.launch {
                            val options = SettingsManager.AUTOMATIC_CACHE_THRESHOLD_OPTIONS_MB
                            val currentIndex = options.indexOf(automaticCacheThresholdMb).coerceAtLeast(0)
                            val next = options[(currentIndex + 1) % options.size]
                            SettingsManager.setAutomaticCacheThresholdMb(context, next)
                            if (next > 0) {
                                CacheUtils.runAutomaticCacheCleanup(context.applicationContext, next)
                                cacheRefreshTick += 1
                            }
                        }
                    }
                )
            }
            item(key = "settings_clear_cache") {
                SettingsRow(
                    title = "清除缓存",
                    value = if (isClearingCache) "清理中..." else cacheSize,
                    kind = SettingsRowKind.Action,
                    modifier = getRowModifier("settings_clear_cache", leftModifier),
                    enabled = !isClearingCache,
                    onClick = {
                        scope.launch {
                            isClearingCache = true
                            try {
                                CacheUtils.clearAllCache(context.applicationContext).fold(
                                    onSuccess = { tvNoticeHostState.show("缓存已清理") },
                                    onFailure = { tvNoticeHostState.show("缓存清理失败，请重试", kind = TvNoticeKind.Error) },
                                )
                                cacheRefreshTick += 1
                            } finally {
                                isClearingCache = false
                            }
                        }
                    }
                )
            }
            item(key = "settings_check_update") {
                SettingsRow(
                    title = "检查更新",
                    value = when {
                        isCheckingUpdate -> "检查中"
                        isDownloadingUpdate -> "下载中"
                        else -> "检查"
                    },
                    kind = SettingsRowKind.Action,
                    modifier = getRowModifier(
                        "settings_check_update",
                        leftModifier.focusRequester(checkUpdateFocusRequester),
                    ),
                    enabled = !isCheckingUpdate && !isDownloadingUpdate,
                    onClick = {
                        isCheckingUpdate = true
                        updateError = null
                        scope.launch {
                            try {
                                when (val result = updateRepository.checkLatest()) {
                                    AppUpdateCheckResult.NoCompatibleApk -> {
                                        tvNoticeHostState.show(
                                            "当前电视架构没有可用的更新包",
                                            kind = TvNoticeKind.Error,
                                        )
                                    }
                                    is AppUpdateCheckResult.UpdateAvailable -> {
                                        availableUpdate = result.update
                                        showUpdateDialog = true
                                    }
                                }
                            } catch (error: Exception) {
                                updateError = resolveUpdateNetworkError(error)
                                tvNoticeHostState.show(updateError.orEmpty(), kind = TvNoticeKind.Error)
                            } finally {
                                isCheckingUpdate = false
                            }
                        }
                    },
                )
            }

            if (showBuildInfo) {
                item(key = "settings_version") {
                    SettingsRow(
                        title = "版本",
                            value = BuildConfig.VERSION_NAME,
                        modifier = getRowModifier("settings_version", leftModifier),
                        enabled = false,
                        onClick = {}
                    )
                }
                item(key = "settings_build_type") {
                    SettingsRow(
                        title = "构建类型",
                            value = BuildConfig.BUILD_TYPE,
                        modifier = leftModifier,
                        enabled = false,
                        onClick = {}
                    )
                }
                item(key = "settings_package_name") {
                    SettingsRow(
                        title = "应用包名",
                            value = context.packageName,
                        modifier = leftModifier,
                        enabled = false,
                        onClick = {}
                    )
                }
            }
        }
    }

    if (showTabEditor) {
        HomeTabEditor(
            initialLayout = HomeTabLayout.restore(
                watchLaterInTopTabsEnabled = appSettings.watchLaterInTopTabsEnabled,
                savedLayout = appSettings.homeTabLayout,
            ),
            onDismiss = { showTabEditor = false },
            onSave = { layout ->
                scope.launch {
                    SettingsManager.setHomeTabLayout(
                        context, layout.encode(), AppTopLevelTab.WATCH_LATER in layout.visible,
                    )
                    showTabEditor = false
                }
            },
        )
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

    if (showUpdateDialog) {
        val update = availableUpdate
        TvDialog(
            title = "发现新版本 ${update?.versionName.orEmpty()}",
            onDismissRequest = { showUpdateDialog = false },
            returnFocusKey = SettingsFocusReturnKeys.CheckUpdate,
            returnFocusFallbackKeys = listOf(SettingsFocusReturnKeys.Back),
            content = {
                Text(
                    text = update?.releaseNotes?.takeIf { it.isNotBlank() }
                        ?: "已发现适用于此电视的更新包。",
                    color = if (com.bbttvv.app.ui.theme.LocalIsLightTheme.current) {
                        Color(0xFF61666D)
                    } else {
                        Color(0xDDEAF2F8)
                    },
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                if (updateError != null) {
                    Text(
                        text = updateError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                    )
                }
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = { showUpdateDialog = false },
                )
                TvDialogActionButton(
                    text = if (isDownloadingUpdate) "下载中" else "下载更新",
                    onClick = {
                        val updateToDownload = update ?: return@TvDialogActionButton
                        if (isDownloadingUpdate) return@TvDialogActionButton
                        if (!AppUpdateInstaller.canRequestInstalls(context)) {
                            updateError = "请先在系统设置中允许 BBTTVV 安装未知应用，然后返回此处下载。"
                            AppUpdateInstaller.openUnknownSourcesSettings(context)
                            return@TvDialogActionButton
                        }
                        isDownloadingUpdate = true
                        updateError = null
                        scope.launch {
                            when (val result = updateRepository.downloadAndVerify(updateToDownload)) {
                                is AppUpdateDownloadResult.Rejected -> {
                                    updateError = result.reason
                                    isDownloadingUpdate = false
                                }
                                    is AppUpdateDownloadResult.ReadyToInstall -> {
                                        isDownloadingUpdate = false
                                        showUpdateDialog = false
                                        AppUpdateInstaller.requestInstall(context, result.apkFile)
                                    }
                            }
                        }
                    },
                )
            },
        )
    }

    if (showCustomCdnDialog) {
        TvDialog(
            title = "编辑自定义 CDN 主机",
            onDismissRequest = {
                showCustomCdnDialog = false
                customCdnValidationMessage = null
            },
            returnFocusKey = SettingsFocusReturnKeys.CustomCdnHost,
            returnFocusFallbackKeys = listOf(SettingsFocusReturnKeys.Back),
            content = {
                TvTextInput(
                    value = customCdnHostDraft,
                    onValueChange = {
                        customCdnHostDraft = it
                        customCdnValidationMessage = null
                    },
                    label = "CDN 主机",
                    supportingText = "示例：upos-sz-mirroralib.bilivideo.com；留空会清除配置并关闭严格模式。",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri,
                )
                customCdnValidationMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                    )
                }
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showCustomCdnDialog = false
                        customCdnValidationMessage = null
                    },
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        val normalized = customCdnHostDraft.trim()
                        if (normalized.isNotBlank() && !isAllowedCustomCdnHost(normalized)) {
                            customCdnValidationMessage = "仅允许 bilivideo.com CDN 主机"
                        } else {
                            scope.launch {
                                SettingsManager.setCustomCdnHost(context, normalized)
                                if (normalized.isBlank()) {
                                    SettingsManager.setStrictCustomCdnEnabled(context, false)
                                }
                                customCdnValidationMessage = null
                                showCustomCdnDialog = false
                            }
                        }
                    },
                )
            },
        )
    }

    when (activeChoice) {
        SettingsChoice.THEME -> TvSettingsChoicePopup(
            title = "选择主题",
            options = SettingsManager.ThemeMode.entries.map { TvSettingsChoiceOption(it, it.label) },
            selectedValue = themeMode,
            anchorBounds = choiceAnchorBounds[SettingsChoice.THEME],
            returnFocusKey = SettingsChoice.THEME.rowKey,
            onSelect = { selectedTheme ->
                activeChoice = null
                if (selectedTheme != themeMode) scope.launch {
                    SettingsManager.setThemeMode(context, selectedTheme)
                    generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
                        .filterIsInstance<android.app.Activity>().firstOrNull()?.recreate()
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        SettingsChoice.PGC_QUALITY -> TvSettingsChoicePopup(
            title = "番剧和影视画质",
            options = listOf(TvSettingsChoiceOption(-1, "跟随普通视频")) +
                VideoQuality.entries
                    .sortedByDescending { quality -> quality.code }
                    .map { quality -> TvSettingsChoiceOption(quality.code, quality.description) },
            selectedValue = pgcPreferredQuality ?: -1,
            anchorBounds = choiceAnchorBounds[SettingsChoice.PGC_QUALITY],
            returnFocusKey = SettingsChoice.PGC_QUALITY.rowKey,
            onSelect = { qualityId ->
                scope.launch {
                    PlayerSettingsStore.setPgcPreferredQuality(
                        context = context,
                        qualityId = qualityId.takeIf { it > 0 },
                    )
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        SettingsChoice.DEFAULT_SPEED -> TvSettingsChoicePopup(
            title = "起始倍速",
            options = listOf(TvSettingsChoiceOption(-1f, "记住上次")) + (PLAYER_PLAYBACK_SPEED_PRESETS + defaultPlaybackSpeed)
                .distinct()
                .sorted()
                .map { speed ->
                    TvSettingsChoiceOption(speed, "${speed}x")
            },
            selectedValue = if (rememberLastSpeed) -1f else defaultPlaybackSpeed,
            anchorBounds = choiceAnchorBounds[SettingsChoice.DEFAULT_SPEED],
            returnFocusKey = SettingsChoice.DEFAULT_SPEED.rowKey,
            onSelect = { speed ->
                scope.launch {
                    PlayerSettingsStore.setStartingPlaybackSpeed(context, speed.takeIf { it > 0f })
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        SettingsChoice.PLAYBACK_END -> TvSettingsChoicePopup(
            title = "选择播放结束行为",
            options = SettingsManager.PlayerPlaybackEndAction.entries.map { action ->
                TvSettingsChoiceOption(action, action.label)
            },
            selectedValue = playerPlaybackEndAction,
            anchorBounds = choiceAnchorBounds[SettingsChoice.PLAYBACK_END],
            returnFocusKey = SettingsChoice.PLAYBACK_END.rowKey,
            onSelect = { action ->
                scope.launch {
                    SettingsManager.setPlayerPlaybackEndAction(context, action)
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        SettingsChoice.VOLUME_CALIBRATION -> TvSettingsChoicePopup(
            title = "选择应用音量",
            options = PLAYER_VOLUME_CALIBRATION_SCALES.map { scale ->
                TvSettingsChoiceOption(scale, formatPlayerVolumeCalibrationLabel(scale))
            },
            selectedValue = volumeCalibrationScale,
            anchorBounds = choiceAnchorBounds[SettingsChoice.VOLUME_CALIBRATION],
            returnFocusKey = SettingsChoice.VOLUME_CALIBRATION.rowKey,
            onSelect = { scale ->
                scope.launch {
                    PlayerSettingsStore.setVolumeCalibrationScale(context, scale)
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        SettingsChoice.AUDIO_BALANCE -> TvSettingsChoicePopup(
            title = "选择音量均衡",
            options = com.bbttvv.app.core.player.AudioBalanceLevel.ordered.map { level ->
                TvSettingsChoiceOption(level, level.label)
            },
            selectedValue = audioBalanceLevel,
            anchorBounds = choiceAnchorBounds[SettingsChoice.AUDIO_BALANCE],
            returnFocusKey = SettingsChoice.AUDIO_BALANCE.rowKey,
            onSelect = { level ->
                com.bbttvv.app.core.player.VolumeBalanceController.setLevel(level)
                scope.launch {
                    PlayerSettingsStore.setAudioBalanceLevel(context, level)
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        SettingsChoice.FEED_API -> TvSettingsChoicePopup(
            title = "选择推荐页数据源",
            options = listOf(
                SettingsManager.FeedApiType.WEB,
                SettingsManager.FeedApiType.MOBILE,
                SettingsManager.FeedApiType.MERGED,
            ).map { type -> TvSettingsChoiceOption(type, resolveFeedApiTypeLabel(type)) },
            selectedValue = feedApiType,
            anchorBounds = choiceAnchorBounds[SettingsChoice.FEED_API],
            returnFocusKey = SettingsChoice.FEED_API.rowKey,
            onSelect = { type ->
                if (
                    type == SettingsManager.FeedApiType.MOBILE &&
                    TokenManager.accessTokenCache.isNullOrBlank()
                ) {
                    tvNoticeHostState.show(
                        message = MOBILE_FEED_TOKEN_MISSING_MESSAGE,
                        kind = TvNoticeKind.Error,
                    )
                } else {
                    scope.launch {
                        SettingsManager.setFeedApiType(context, type)
                        activeChoice = null
                    }
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        SettingsChoice.HOME_REFRESH_COUNT -> TvSettingsChoicePopup(
            title = "选择首页刷新数量",
            options = (10..40 step 5).map { count ->
                TvSettingsChoiceOption(count, "$count 条")
            },
            selectedValue = homeRefreshCount,
            anchorBounds = choiceAnchorBounds[SettingsChoice.HOME_REFRESH_COUNT],
            returnFocusKey = SettingsChoice.HOME_REFRESH_COUNT.rowKey,
            onSelect = { count ->
                scope.launch {
                    SettingsManager.setHomeRefreshCount(context, count)
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        SettingsChoice.DYNAMIC_PAGE_MODE -> TvSettingsChoicePopup(
            title = "选择动态页显示内容",
            options = SettingsManager.DynamicPageDisplayMode.entries.map { mode ->
                TvSettingsChoiceOption(mode, mode.label)
            },
            selectedValue = dynamicPageDisplayMode,
            anchorBounds = choiceAnchorBounds[SettingsChoice.DYNAMIC_PAGE_MODE],
            returnFocusKey = SettingsChoice.DYNAMIC_PAGE_MODE.rowKey,
            onSelect = { mode ->
                scope.launch {
                    SettingsManager.setDynamicPageDisplayMode(context, mode)
                    activeChoice = null
                }
            },
            onDismissRequest = { activeChoice = null },
        )

        null -> Unit
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
    val semanticColors = LocalTvSemanticColors.current
    val textColor = if (isFocused) semanticColors.focusContent else semanticColors.primaryText

    Surface(
        onClick = onBack,
        modifier = modifier,
        interactionSource = interactionSource,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = semanticColors.surfaceSubtle,
            focusedContainerColor = semanticColors.focusContainer
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
internal fun SettingsSectionTitle(title: String) {
    val semanticColors = LocalTvSemanticColors.current
    Text(
        text = title,
        color = semanticColors.secondaryText,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 6.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String = "",
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: SettingsRowKind = SettingsRowKind.Toggle,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val semanticColors = LocalTvSemanticColors.current
    val titleColor = when {
        !enabled -> semanticColors.disabledText
        isFocused -> semanticColors.focusContent
        else -> semanticColors.primaryText
    }
    val subtitleColor = when {
        !enabled -> semanticColors.disabledText
        isFocused -> semanticColors.focusContent.copy(alpha = 0.72f)
        else -> semanticColors.secondaryText
    }
    val valueContainerColor = when {
        !enabled -> semanticColors.surfaceSubtle.copy(alpha = 0.55f)
        isFocused -> semanticColors.focusContent.copy(alpha = 0.14f)
        else -> semanticColors.surfaceSubtle
    }
    val valueTextColor = when {
        !enabled -> semanticColors.disabledText
        isFocused -> semanticColors.focusContent
        else -> semanticColors.accent
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = semanticColors.surfaceSubtle,
            focusedContainerColor = semanticColors.focusContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (subtitle.isBlank()) 56.dp else 78.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) Text(
                    text = subtitle,
                    color = subtitleColor,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .background(
                        color = valueContainerColor,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(
                        horizontal = 12.dp,
                        vertical = 6.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (kind) {
                        SettingsRowKind.Choice -> "$value  ▾"
                        SettingsRowKind.Navigation -> "$value  ›"
                        else -> value
                    },
                    color = valueTextColor,
                    fontSize = 14.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal fun onOff(enabled: Boolean): String = if (enabled) "\u5F00" else "\u5173"

private fun resolveFeedApiTypeLabel(type: SettingsManager.FeedApiType): String {
    return when (type) {
        SettingsManager.FeedApiType.MERGED -> "合并"
        SettingsManager.FeedApiType.WEB -> "网页端"
        SettingsManager.FeedApiType.MOBILE -> "移动端"
        SettingsManager.FeedApiType.NORMAL -> "\u517C\u5BB9\u6A21\u5F0F"
    }
}

private fun resolvePlayerPlaybackEndActionDescription(
    action: SettingsManager.PlayerPlaybackEndAction
): String {
    return when (action) {
        SettingsManager.PlayerPlaybackEndAction.NONE -> ""
        SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT -> "优先下一分 P，其次相关推荐。"
        SettingsManager.PlayerPlaybackEndAction.LOOP_ONE -> ""
        SettingsManager.PlayerPlaybackEndAction.RETURN -> ""
    }
}
