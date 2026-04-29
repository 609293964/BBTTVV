package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.core.player.BufferingSpeedMeter
import com.bbttvv.app.core.player.createConfiguredPlayer
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.player.DanmakuSettings
import com.bbttvv.app.core.store.player.DanmakuSettingsStore
import com.bbttvv.app.core.store.player.toEngineConfig
import com.bbttvv.app.feature.plugin.SponsorBlockConfig
import com.bbttvv.app.feature.plugin.SponsorBlockPlugin
import com.bbttvv.app.feature.plugin.findSponsorBlockPluginInfo
import com.bbttvv.app.feature.video.viewmodel.PlayerViewModel
import com.bbttvv.app.ui.focus.RegisterTvFocusEscapeTarget
import com.bbttvv.app.ui.focus.isSameOrDescendantOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

private const val PLAYER_FOCUS_ESCAPE_PRIORITY = 20
private const val SPONSOR_SKIP_NOTICE_AUTO_DISMISS_MS = 3_000L

@OptIn(ExperimentalTvMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    bvid: String,
    cid: Long,
    aid: Long,
    startPositionMs: Long,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val commentsUiState by viewModel.commentsUiState.collectAsStateWithLifecycle()
    val showSkipButton by viewModel.showSkipButton.collectAsStateWithLifecycle()
    val currentSponsorSegment by viewModel.currentSponsorSegment.collectAsStateWithLifecycle()
    val sponsorSegments by viewModel.sponsorSegments.collectAsStateWithLifecycle()
    val danmakuPayload by viewModel.danmakuPayload.collectAsStateWithLifecycle()
    val isDanmakuEnabled by viewModel.isDanmakuEnabled.collectAsStateWithLifecycle()
    val storedDanmakuSettings by DanmakuSettingsStore.getSettings(context)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val presentationState = rememberPlayerOverlayPresentationState(storedDanmakuSettings)
    val seekPreviewFrame by viewModel.seekPreviewFrame.collectAsStateWithLifecycle()
    val showOnlineCount by SettingsManager.getShowOnlineCount(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val plugins by PluginManager.pluginsFlow.collectAsStateWithLifecycle(initialValue = PluginManager.plugins)
    val sponsorPluginInfo = remember(plugins) {
        plugins.findSponsorBlockPluginInfo()
    }
    val sponsorPlugin = sponsorPluginInfo?.plugin as? SponsorBlockPlugin
    val sponsorBlockEnabled = sponsorPluginInfo?.enabled == true
    val fallbackSponsorConfigFlow = remember { MutableStateFlow(SponsorBlockConfig()) }
    val sponsorConfig by (sponsorPlugin?.configState ?: fallbackSponsorConfigFlow).collectAsStateWithLifecycle()
    val showSponsorSkipNotice = remember(
        showSkipButton,
        currentSponsorSegment,
        sponsorBlockEnabled,
        sponsorConfig.showSkipPrompt,
    ) {
        showSkipButton &&
            currentSponsorSegment != null &&
            sponsorBlockEnabled &&
            sponsorConfig.showSkipPrompt
    }

    val visualEffectsState = rememberPlayerVisualEffectsState()
    val overlayStateMachine = remember { PlayerOverlayStateMachine() }
    val overlayUiState = overlayStateMachine.uiState
    val isDebugOverlayVisible = BuildConfig.DEBUG && overlayUiState.showDebugOverlay
    val actions = remember {
        buildPlayerActions(
            isDebugBuild = BuildConfig.DEBUG,
        )
    }
    LaunchedEffect(actions) {
        overlayStateMachine.syncActions(actions)
    }
    val panelOptions = remember(
        overlayUiState.activePanel,
        uiState,
        presentationState.danmakuSettings,
        isDanmakuEnabled,
    ) {
        buildPlayerPanelOptions(
            activePanel = overlayUiState.activePanel,
            uiState = uiState,
            danmakuSettings = presentationState.danmakuSettings,
            isDanmakuEnabled = isDanmakuEnabled,
        )
    }
    val panelOptionsFocusKey = buildPanelOptionsFocusKey(panelOptions)
    val sponsorMarkers = remember(
        sponsorSegments,
        playbackState.durationMs,
        sponsorBlockEnabled,
        sponsorConfig,
    ) {
        buildSponsorProgressMarks(
            segments = sponsorSegments,
            durationMs = playbackState.durationMs,
            enabled = sponsorBlockEnabled,
            config = sponsorConfig,
        )
    }
    val topRightBadges = remember(uiState, isDanmakuEnabled) {
        buildTopRightBadges(
            uiState = uiState,
            isDanmakuEnabled = isDanmakuEnabled,
        )
    }
    val focusBindings = rememberPlayerScreenFocusBindings(
        actionsCount = actions.size,
        panelOptionsCount = panelOptions.size,
    )
    val playerFocusCoordinator = remember { PlayerFocusCoordinator() }
    var debugSnapshot by remember { mutableStateOf(PlayerDebugSnapshot()) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val session = remember(bvid, cid, aid, startPositionMs) {
        PlayerScreenSession(
            bvid = bvid,
            cid = cid,
            aid = aid,
            startPositionMs = startPositionMs,
        )
    }
    val exitTrace = remember(session) { PlayerExitTrace(session) }

    val handleOverlayEffect = rememberPlayerOverlayEffectHandler(
        presentationState = presentationState,
        viewModel = viewModel,
        context = context,
        scope = scope,
        exitTrace = exitTrace,
        onExitPlayer = onBack,
    )
    val latestHandleOverlayEffect = rememberUpdatedState(handleOverlayEffect)
    val latestUiState = rememberUpdatedState(uiState)

    val bufferingSpeedMeter = remember { BufferingSpeedMeter() }
    val exoPlayer = remember(context, bufferingSpeedMeter) {
        createConfiguredPlayer(
            context = context,
            transferListener = bufferingSpeedMeter,
        )
    }
    val playbackSnapshotProvider = remember(viewModel) { { viewModel.snapshotPlaybackState() } }
    val handleOverlayKey = rememberPlayerOverlayKeyHandler(
        overlayStateMachine = overlayStateMachine,
        playbackSnapshotProvider = playbackSnapshotProvider,
        actions = actions,
        panelOptions = panelOptions,
        pauseForSeekScrub = viewModel::pauseForSeekScrub,
        onEffect = handleOverlayEffect,
    )
    val handleSponsorSkipNoticeKey = remember(showSponsorSkipNotice, viewModel) {
        { event: KeyEvent ->
            handleSponsorSkipNoticeKeyEvent(
                event = event,
                showSponsorSkipNotice = showSponsorSkipNotice,
                onSkipSponsor = viewModel::skipSponsor,
                onDismissSponsorNotice = viewModel::dismissSponsorNotice,
            )
        }
    }
    val handlePlayerKey = remember(handleOverlayKey, handleSponsorSkipNoticeKey) {
        { event: KeyEvent ->
            handleSponsorSkipNoticeKey(event) || handleOverlayKey(event)
        }
    }
    val useRealtimePlaybackState = overlayUiState.overlayMode == PlayerOverlayMode.FullControls ||
        (danmakuPayload != null && isDanmakuEnabled) ||
        isDebugOverlayVisible
    val livePlaybackState = rememberRealtimePlaybackState(
        exoPlayer = exoPlayer,
        playbackState = playbackState,
        enabled = useRealtimePlaybackState,
    )
    val bufferingOverlayText by rememberBufferingOverlayText(
        isBuffering = livePlaybackState.value.isBuffering &&
            !uiState.isLoading &&
            uiState.errorMessage.isNullOrBlank(),
        speedMeter = bufferingSpeedMeter,
    )
    val isCommentsPanelVisible = presentationState.isCommentsPanelVisible
    val danmakuConfig = remember(presentationState.danmakuSettings) {
        presentationState.danmakuSettings.toEngineConfig()
    }
    LaunchedEffect(showSponsorSkipNotice, currentSponsorSegment?.UUID) {
        if (!showSponsorSkipNotice) return@LaunchedEffect
        delay(SPONSOR_SKIP_NOTICE_AUTO_DISMISS_MS)
        viewModel.dismissSponsorNotice()
    }

    if (uiState.resumePrompt == null) {
        RegisterTvFocusEscapeTarget(
            key = "video_player",
            priority = PLAYER_FOCUS_ESCAPE_PRIORITY,
            acceptsFocus = { focusedView ->
                focusedView.isSameOrDescendantOf(hostView)
            },
            shouldRecoverEscapedFocus = { focusedView ->
                focusedView.rootView === hostView.rootView &&
                    !focusedView.isSameOrDescendantOf(hostView)
            },
            recoverFocus = {
                playerFocusCoordinator.requestFocus(
                    resolvePlayerEscapeFocusIntent(
                        overlayUiState = overlayUiState,
                        isCommentsPanelVisible = isCommentsPanelVisible,
                    )
                )
            },
        )
    }

    PlayerScreenEffectHost(
        context = context,
        viewModel = viewModel,
        presentationState = presentationState,
        overlayStateMachine = overlayStateMachine,
        playerFocusCoordinator = playerFocusCoordinator,
        focusBindings = focusBindings,
        handleOverlayEffect = handleOverlayEffect,
        onDebugSnapshotChange = { debugSnapshot = it },
        args = PlayerScreenEffectArgs(
            session = session,
            uiState = uiState,
            commentsUiState = commentsUiState,
            playbackState = playbackState,
            overlayUiState = overlayUiState,
            panelOptions = panelOptions,
            panelOptionsFocusKey = panelOptionsFocusKey,
            isCommentsPanelVisible = isCommentsPanelVisible,
            showSponsorSkipNotice = showSponsorSkipNotice,
            playerView = playerViewRef,
            exoPlayer = exoPlayer,
            exitTrace = exitTrace,
            isDebugOverlayVisible = isDebugOverlayVisible,
            bufferingSpeedMeter = bufferingSpeedMeter,
            playbackSnapshotProvider = playbackSnapshotProvider,
            latestHandleOverlayEffect = latestHandleOverlayEffect,
            latestUiState = latestUiState,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .playerBackdropSource(visualEffectsState)
            .onPreviewKeyEvent { keyEvent ->
                handleSponsorSkipNoticeKey(keyEvent.nativeKeyEvent)
            },
    ) {
        PlayerSurfaceHost(
            exoPlayer = exoPlayer,
            keepScreenOn = playbackState.isPlaybackActive,
            overlayMode = overlayUiState.overlayMode,
            onHiddenOverlayKey = handlePlayerKey,
            onViewAvailable = { playerViewRef = it },
            onPlayerSurfaceFocusNeeded = {
                playerFocusCoordinator.requestFocus(PlayerFocusIntent.FocusPlayerSurface)
            },
        )

        PlayerDanmakuOverlayHost(
            payload = danmakuPayload,
            isEnabled = isDanmakuEnabled,
            playbackState = livePlaybackState,
            config = danmakuConfig,
        )

        PlayerOverlayHost(
            uiState = uiState,
            commentsUiState = commentsUiState,
            overlayUiState = overlayUiState,
            playbackState = livePlaybackState,
            actions = actions,
            panelOptions = panelOptions,
            sponsorMarkers = sponsorMarkers,
            topRightBadges = topRightBadges,
            seekPreviewFrame = seekPreviewFrame,
            showOnlineCount = showOnlineCount,
            isDebugOverlayVisible = isDebugOverlayVisible,
            isDanmakuEnabled = isDanmakuEnabled,
            isCommentsPanelVisible = isCommentsPanelVisible,
            visualEffectsState = visualEffectsState,
            progressFocusRequester = focusBindings.progressFocusRequester,
            actionFocusRequesters = focusBindings.actionFocusRequesters,
            panelFocusRequesters = focusBindings.panelFocusRequesters,
            commentsPanelPrimaryFocusRequester = focusBindings.commentsPanelPrimaryFocusRequester,
            onOverlayKey = handlePlayerKey,
            onToggleCommentSort = viewModel::toggleCommentSort,
            onRetryComments = viewModel::refreshComments,
            onLoadMoreComments = viewModel::loadMoreComments,
            onOpenCommentThread = viewModel::openCommentThread,
            onBackFromCommentThread = viewModel::closeCommentThread,
        )

        if (isDebugOverlayVisible) {
            PlayerDebugOverlay(
                snapshot = debugSnapshot,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = PlayerLayoutTokens.debugTopPadding,
                        end = PlayerLayoutTokens.debugEdgePadding,
                    ),
            )
        }

        PlayerTransientOverlayMessages(
            uiState = uiState,
            bufferingOverlayText = bufferingOverlayText,
            showDebugOverlay = isDebugOverlayVisible,
            showSponsorSkipNotice = showSponsorSkipNotice,
        )
    }

    uiState.resumePrompt?.let { prompt ->
        ResumePlaybackDialog(
            prompt = prompt,
            onConfirm = viewModel::confirmResumePlayback,
            onDismiss = viewModel::dismissResumePlaybackPrompt,
        )
    }
}

private fun resolvePlayerEscapeFocusIntent(
    overlayUiState: PlayerOverlayUiState,
    isCommentsPanelVisible: Boolean,
): PlayerFocusIntent {
    return when {
        overlayUiState.overlayMode != PlayerOverlayMode.FullControls -> {
            PlayerFocusIntent.FocusPlayerSurface
        }

        isCommentsPanelVisible -> {
            PlayerFocusIntent.FocusCommentsPanel
        }

        overlayUiState.activePanel != null -> {
            PlayerFocusIntent.FocusPanelOption(overlayUiState.selectedPanelIndex)
        }

        overlayUiState.fullControlsFocus == PlayerFullControlsFocus.Progress -> {
            PlayerFocusIntent.FocusProgress
        }

        else -> {
            PlayerFocusIntent.FocusAction(overlayUiState.selectedActionIndex)
        }
    }
}
