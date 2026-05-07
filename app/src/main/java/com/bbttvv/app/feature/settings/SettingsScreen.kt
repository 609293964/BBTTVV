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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

private object SettingsFocusReturnKeys {
    const val Back = "settings:back"
    const val UserAgent = "settings:user_agent"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val hostView = LocalView.current
    val backFocusRequester = remember { FocusRequester() }

    fun requestBackFocus(): Boolean {
        return runCatching { backFocusRequester.requestFocus() }.getOrDefault(false)
    }

    BackHandler(onBack = onBack)
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
                SettingsBackButton(
                    modifier = Modifier.focusRequester(backFocusRequester),
                    onBack = onBack
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "\u8BBE\u7F6E",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "\u8BBE\u7F6E",
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
    showBuildInfo: Boolean = true,
    initialFocusRequester: FocusRequester? = null,
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
    val initialFocusModifier = initialFocusRequester
        ?.let { requester -> Modifier.focusRequester(requester) }
        ?: Modifier

    RegisterTvFocusReturnTarget(
        key = SettingsFocusReturnKeys.UserAgent,
        focusRequester = userAgentFocusRequester,
    )

    LaunchedEffect(cacheRefreshTick) {
        cacheSize = CacheUtils.getTotalCacheSize(context)
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        contentPadding = PaddingValues(bottom = if (compact) 20.dp else 32.dp)
    ) {
        item { SettingsSectionTitle("\u64AD\u653E", compact = compact) }
        item {
            SettingsRow(
                title = "\u4EC5\u52A0\u8F7D\u6700\u9AD8\u5206\u8FA8\u7387",
                subtitle = "\u64AD\u653E\u65F6\u4F18\u5148\u8BF7\u6C42\u63A5\u53E3\u8FD4\u56DE\u7684\u6700\u9AD8\u753B\u8D28\uFF0C\u5173\u95ED\u540E\u6309\u9ED8\u8BA4\u753B\u8D28\u7B56\u7565\u9009\u62E9\u3002",
                value = onOff(autoHighestQuality),
                compact = compact,
                modifier = initialFocusModifier,
                onClick = {
                    scope.launch {
                        SettingsManager.setAuto1080p(context, !autoHighestQuality)
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "\u8BB0\u4F4F\u4E0A\u6B21\u64AD\u653E\u500D\u901F",
                subtitle = "\u81EA\u52A8\u5E94\u7528\u4E0A\u6B21\u89C2\u770B\u89C6\u9891\u65F6\u9009\u62E9\u7684\u64AD\u653E\u901F\u5EA6\u3002",
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
                title = "\u9ED8\u8BA4\u500D\u901F",
                subtitle = "\u5FAA\u73AF\u5207\u6362 0.75x / 1.0x / 1.25x / 1.5x / 2.0x\u3002",
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
                title = "\u5E94\u7528\u97F3\u91CF\u6821\u51C6",
                subtitle = "\u8C03\u6574 BBTTVV \u64AD\u653E\u8F93\u51FA\u97F3\u91CF\uFF0C\u4E0D\u6539\u53D8\u7535\u89C6\u7CFB\u7EDF\u97F3\u91CF\u3002\u8D85\u8FC7 100% \u53EF\u80FD\u5931\u771F\u3002",
                value = formatPlayerVolumeCalibrationLabel(volumeCalibrationScale),
                compact = compact,
                onClick = {
                    scope.launch {
                        val nextScale = nextPlayerVolumeCalibrationScale(volumeCalibrationScale)
                        PlayerSettingsCache.updateVolumeCalibrationScale(nextScale)
                        PlayerSettingsStore.setVolumeCalibrationScale(context, nextScale)
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "\u97F3\u91CF\u5747\u8861",
                subtitle = "\u81EA\u52A8\u8C03\u6574\u4E0D\u540C\u89C6\u9891\u7684\u97F3\u91CF\u5DEE\u5F02\uFF0C\u907F\u514D\u5207\u6362\u89C6\u9891\u65F6\u97F3\u91CF\u5FFD\u5927\u5FFD\u5C0F\u3002",
                value = audioBalanceLevel.label,
                compact = compact,
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
        item {
            SettingsRow(
                title = "\u97F3\u9891\u76F4\u901A [\u5B9E\u9A8C\u6027]",
                subtitle = "\u5C06\u538B\u7F29\u97F3\u9891\uFF08\u5982\u675C\u6BD4\u5168\u666F\u58F0\u3001Hi-Res\uFF09\u4E0D\u7ECF\u89E3\u7801\u76F4\u63A5\u8F93\u51FA\u5230\u5916\u63A5\u97F3\u9891\u8BBE\u5907\u3002\u5F00\u542F\u540E\u97F3\u91CF\u5747\u8861\u3001\u58F0\u9053\u5E73\u8861\u3001\u500D\u901F\u8C03\u8282\u5C06\u5931\u6548\u3002\u9700\u8981\u8BBE\u5907\u652F\u6301\u5BF9\u5E94\u7F16\u7801\u683C\u5F0F\u3002",
                value = onOff(audioPassthrough),
                compact = compact,
                onClick = {
                    val newValue = !audioPassthrough
                    PlayerSettingsCache.updateAudioPassthrough(newValue)
                    scope.launch {
                        PlayerSettingsStore.setAudioPassthrough(context, newValue)
                    }
                }
            )
        }
        item {
            SettingsRow(
                title = "\u81EA\u52A8\u8DF3\u5230\u4E0A\u6B21\u64AD\u653E\u4F4D\u7F6E",
                subtitle = "\u6253\u5F00\u89C6\u9891\u65F6\uFF0C\u81EA\u52A8\u4ECE\u4E0A\u6B21\u89C2\u770B\u4E2D\u65AD\u5904\u7EE7\u7EED\u64AD\u653E\u3002",
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
        item {
            SettingsRow(
                title = "\u64AD\u653E\u7ED3\u675F\u540E",
                subtitle = resolvePlayerPlaybackEndActionDescription(playerPlaybackEndAction),
                value = playerPlaybackEndAction.label,
                compact = compact,
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

        item { SettingsSectionTitle("\u754C\u9762\u663E\u793A", compact = compact) }
        item {
            SettingsRow(
                title = "\u5728\u7EBF\u89C2\u770B\u4EBA\u6570",
                subtitle = "\u63A7\u5236\u64AD\u653E\u9875\u53F3\u4E0A\u89D2\u662F\u5426\u663E\u793A\u5F53\u524D\u89C6\u9891\u7684\u5728\u7EBF\u89C2\u770B\u4EBA\u6570\u3002",
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
                title = "\u89C6\u9891\u8BE6\u60C5\u9875\u8BC4\u8BBA",
                subtitle = "\u63A7\u5236\u89C6\u9891\u8BE6\u60C5\u9875\u5E95\u90E8\u8BC4\u8BBA\u533A\u662F\u5426\u663E\u793A\u548C\u52A0\u8F7D\u3002",
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
        item {
            SettingsRow(
                title = "\u52A8\u6001\u9875\u663E\u793A\u7BA1\u7406",
                subtitle = resolveDynamicPageDisplayModeDescription(dynamicPageDisplayMode),
                value = dynamicPageDisplayMode.label,
                compact = compact,
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
        item {
            SettingsRow(
                title = "\"\u6211\u7684\"\u9875\u9762\u7126\u70B9\u5237\u65B0",
                subtitle = if (updateContentOnTabFocusEnabled) {
                    "\u5DF2\u5F00\u542F\uFF1A\u5728\u201C\u6211\u7684\u201D\u9875\u5DE6\u4FA7 TAB \u4E0A\u79FB\u52A8\u7126\u70B9\u65F6\uFF0C\u4F1A\u7ACB\u5373\u5237\u65B0\u53F3\u4FA7\u5185\u5BB9\u3002"
                } else {
                    "\u5DF2\u5173\u95ED\uFF1A\u7126\u70B9\u79FB\u52A8\u53EA\u9650\u4E8E TAB\uFF0C\u9700\u6309\u786E\u5B9A\u952E\u540E\u624D\u5237\u65B0\u53F3\u4FA7\u5185\u5BB9\u3002"
                },
                value = if (updateContentOnTabFocusEnabled) "\u5F00\u542F" else "\u5173\u95ED",
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
                title = "\u7A0D\u540E\u518D\u770B\u5165\u53E3\u4F4D\u7F6E",
                subtitle = if (watchLaterInTopTabsEnabled) {
                    "\u5F53\u524D\u663E\u793A\u5728\u9996\u9875\u9876\u90E8 Tabs \u4E2D\uFF0C\u5E76\u4ECE\u201C\u6211\u7684\u201D\u5DE6\u4FA7\u83DC\u5355\u9690\u85CF\u3002"
                } else {
                    "\u5F53\u524D\u663E\u793A\u5728\u201C\u6211\u7684\u201D\u5DE6\u4FA7\u83DC\u5355\u4E2D\uFF0C\u5E76\u4ECE\u9996\u9875\u9876\u90E8 Tabs \u9690\u85CF\u3002"
                },
                value = if (watchLaterInTopTabsEnabled) "\u9876\u90E8 Tabs" else "\u6211\u7684\u83DC\u5355",
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

        item { SettingsSectionTitle("\u9996\u9875\u4E0E\u63A8\u8350", compact = compact) }
        item {
            SettingsRow(
                title = "\u63A8\u8350\u9875\u6570\u636E\u6E90",
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
                title = "\u9996\u9875\u5237\u65B0\u6570\u91CF",
                subtitle = "\u5355\u6B21\u5237\u65B0\u65F6\u62C9\u53D6\u7684\u63A8\u8350\u89C6\u9891\u6570\u91CF\u3002",
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

        item { SettingsSectionTitle("\u7F51\u7EDC", compact = compact) }
        item {
            SettingsRow(
                title = "User-Agent",
                subtitle = "\u5F53\u524D\uFF1A${buildUserAgentPreview(userAgent)}",
                value = if (userAgent == DEFAULT_APP_USER_AGENT) "\u9ED8\u8BA4" else "\u81EA\u5B9A\u4E49",
                compact = compact,
                modifier = Modifier.focusRequester(userAgentFocusRequester),
                onClick = {
                    userAgentDraft = userAgent
                    showUserAgentDialog = true
                }
            )
        }
        item {
            SettingsRow(
                title = "\u662F\u5426\u53EA\u5141\u8BB8\u4F7F\u7528IPV4",
                subtitle = "\u5F00\u542F\u540E\u53EA\u8D70 IPv4 \u89E3\u6790\uFF1B\u5728\u53CC\u6808\u7F51\u7EDC\u5F02\u5E38\u65F6\u53EF\u4F5C\u4E3A\u517C\u5BB9\u5F00\u5173\u3002",
                value = onOff(ipv4OnlyEnabled),
                compact = compact,
                onClick = {
                    scope.launch {
                        SettingsManager.setIpv4OnlyEnabled(context, !ipv4OnlyEnabled)
                    }
                }
            )
        }

        item { SettingsSectionTitle("\u9690\u79C1\u4E0E\u7F13\u5B58", compact = compact) }
        item {
            SettingsRow(
                title = "\u9690\u79C1\u65E0\u75D5\u6A21\u5F0F",
                subtitle = "\u5173\u95ED\u641C\u7D22\u5386\u53F2\u5199\u5165\uFF0C\u5E76\u8DF3\u8FC7\u89C2\u770B\u5FC3\u8DF3\u4E0A\u62A5\u3002",
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
                title = "\u9ED1\u540D\u5355\u6570\u91CF",
                subtitle = "\u5F53\u524D\u5DF2\u5C4F\u853D\u7684 UP \u6570\u91CF\u3002",
                value = blockedUps.size.toString(),
                compact = compact,
                enabled = false,
                onClick = {}
            )
        }
        item {
            SettingsRow(
                title = "\u6E05\u9664\u7F13\u5B58",
                subtitle = if (isClearingCache) {
                    "\u6B63\u5728\u6E05\u7406\u56FE\u7247\u3001\u7F51\u7EDC\u4E0E\u64AD\u653E\u7F13\u5B58..."
                } else {
                    "\u6E05\u7406\u9884\u89C8\u56FE\u3001\u7F51\u7EDC\u7F13\u5B58\u548C\u64AD\u653E\u5668\u6B8B\u7559\u3002"
                },
                value = if (isClearingCache) "\u6E05\u7406\u4E2D..." else cacheSize,
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
            item { SettingsSectionTitle("\u5173\u4E8E", compact = compact) }
            item {
                SettingsRow(
                    title = "\u7248\u672C",
                    subtitle = "\u5F53\u524D\u5B89\u88C5\u5305\u7248\u672C\u53F7\u3002",
                    value = BuildConfig.VERSION_NAME,
                    compact = compact,
                    enabled = false,
                    onClick = {}
                )
            }
            item {
                SettingsRow(
                    title = "\u6784\u5EFA\u7C7B\u578B",
                    subtitle = "\u7528\u4E8E\u533A\u5206 debug / release\u3002",
                    value = BuildConfig.BUILD_TYPE,
                    compact = compact,
                    enabled = false,
                    onClick = {}
                )
            }
            item {
                SettingsRow(
                    title = "\u5E94\u7528\u5305\u540D",
                    subtitle = "\u5F53\u524D\u5B89\u88C5\u5728\u8BBE\u5907\u4E0A\u7684\u5305\u6807\u8BC6\u3002",
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
    Surface(
        onClick = onBack,
        modifier = modifier,
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
            Text(text = "\u8FD4\u56DE", color = Color.White, fontSize = 15.sp)
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val useLightFocusStyle = compact && isFocused
    val titleColor = when {
        !enabled -> Color(0x78FFFFFF)
        useLightFocusStyle -> Color(0xFF111111)
        isFocused -> Color(0xFFF8FBFF)
        else -> Color.White
    }
    val subtitleColor = when {
        !enabled -> Color(0x5FFFFFFF)
        useLightFocusStyle -> Color(0xB0000000)
        isFocused -> Color(0xE1EDF9)
        else -> Color(0x9FFFFFFF)
    }
    val valueContainerColor = when {
        !enabled -> Color(0x0FFFFFFF)
        useLightFocusStyle -> Color(0x14000000)
        isFocused -> Color(0x1FFFFFFF)
        else -> Color(0x0DFFFFFF)
    }
    val valueTextColor = when {
        !enabled -> Color(0x72FFFFFF)
        useLightFocusStyle -> Color(0xFF111111)
        isFocused -> Color(0xFFF8FBFF)
        else -> Color(0xCCFFFFFF)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(if (compact) 24.dp else 28.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x12000000),
            focusedContainerColor = if (compact) Color(0xE9E6EEF4) else Color(0xFF31445E)
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
