package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.bbttvv.app.core.player.BufferingSpeedMeter
import com.bbttvv.app.core.player.clearPlayerViewReference
import com.bbttvv.app.core.player.createConfiguredPlayer
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.player.DanmakuSettings
import com.bbttvv.app.core.store.player.DanmakuSettingsStore
import com.bbttvv.app.core.store.player.toEngineConfig
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.feature.video.danmaku.DanmakuConfig
import com.bbttvv.app.feature.video.viewmodel.PlaybackBadge
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentsUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerSponsorUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerViewModel
import com.bbttvv.app.ui.focus.RegisterLifecycleFocusDrain
import com.bbttvv.app.ui.focus.RegisterTvFocusEscapeTarget
import com.bbttvv.app.ui.focus.isSameOrDescendantOf
import kotlinx.coroutines.delay

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
    val sponsorUiState by viewModel.sponsorUiState.collectAsStateWithLifecycle()
    val isDanmakuEnabled by viewModel.isDanmakuEnabled.collectAsStateWithLifecycle()
    val storedDanmakuSettings by DanmakuSettingsStore.getSettings(context)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val presentationState = rememberPlayerOverlayPresentationState(storedDanmakuSettings)
    val showOnlineCount by SettingsManager.getShowOnlineCount(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val showSponsorSkipNotice = sponsorUiState.showSkipNotice

    val visualEffectsState = rememberPlayerVisualEffectsState()
    val overlayStateMachine = remember { PlayerOverlayStateMachine() }
    val overlayUiState = overlayStateMachine.uiState
    val isDebugOverlayVisible = overlayUiState.showDebugOverlay
    val actions = remember {
        buildPlayerActions()
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
    RegisterLifecycleFocusDrain(key = playerFocusCoordinator) {
        playerFocusCoordinator.drainPendingFocus()
    }
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
        onPrepareExitPlayer = {
            clearPlayerViewReference(playerViewRef)
        },
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
    val handlePlayerPreviewKey = remember(handlePlayerKey) {
        { event: KeyEvent ->
            if (shouldRoutePlayerKeyToPreviewHandler(event)) {
                handlePlayerKey(event)
            } else {
                false
            }
        }
    }
    val isCommentsPanelVisible = presentationState.isCommentsPanelVisible
    val danmakuConfig = remember(presentationState.danmakuSettings) {
        presentationState.danmakuSettings.toEngineConfig()
    }
    LaunchedEffect(showSponsorSkipNotice, sponsorUiState.currentSegmentUuid) {
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
        onExitPlayer = onBack,
        onDebugSnapshotChange = { debugSnapshot = it },
        args = PlayerScreenEffectArgs(
            session = session,
            uiState = uiState,
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
                val nativeEvent = keyEvent.nativeKeyEvent
                if (shouldRoutePlayerKeyToPreviewHandler(nativeEvent)) {
                    handleSponsorSkipNoticeKey(nativeEvent)
                } else {
                    false
                }
            },
    ) {
        PlayerSurfaceSection(
            viewModel = viewModel,
            exoPlayer = exoPlayer,
            overlayMode = overlayUiState.overlayMode,
            handlePlayerKey = handlePlayerKey,
            playerFocusCoordinator = playerFocusCoordinator,
            onViewAvailable = { playerViewRef = it },
            onViewReleased = { releasedView ->
                if (playerViewRef === releasedView) {
                    playerViewRef = null
                }
            },
        )

        PlayerDanmakuSection(
            viewModel = viewModel,
            exoPlayer = exoPlayer,
            isEnabled = isDanmakuEnabled,
            config = danmakuConfig,
        )

        PlayerOverlaySection(
            viewModel = viewModel,
            exoPlayer = exoPlayer,
            uiState = uiState,
            overlayUiState = overlayUiState,
            actions = actions,
            panelOptions = panelOptions,
            sponsorUiState = sponsorUiState,
            topRightBadges = topRightBadges,
            showOnlineCount = showOnlineCount,
            isDebugOverlayVisible = isDebugOverlayVisible,
            isDanmakuEnabled = isDanmakuEnabled,
            isCommentsPanelVisible = isCommentsPanelVisible,
            visualEffectsState = visualEffectsState,
            focusBindings = focusBindings,
            handlePlayerKey = handlePlayerPreviewKey,
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

        PlayerTransientMessageSection(
            viewModel = viewModel,
            uiState = uiState,
            bufferingSpeedMeter = bufferingSpeedMeter,
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

@Composable
private fun PlayerSurfaceSection(
    viewModel: PlayerViewModel,
    exoPlayer: ExoPlayer,
    overlayMode: PlayerOverlayMode,
    handlePlayerKey: (KeyEvent) -> Boolean,
    playerFocusCoordinator: PlayerFocusCoordinator,
    onViewAvailable: (PlayerView) -> Unit,
    onViewReleased: (PlayerView) -> Unit,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    PlayerSurfaceHost(
        exoPlayer = exoPlayer,
        keepScreenOn = playbackState.isPlaybackActive,
        overlayMode = overlayMode,
        onHiddenOverlayKey = handlePlayerKey,
        onViewAvailable = onViewAvailable,
        onViewReleased = onViewReleased,
        onPlayerSurfaceFocusNeeded = {
            playerFocusCoordinator.requestFocus(PlayerFocusIntent.FocusPlayerSurface)
        },
    )
}

@Composable
private fun PlayerDanmakuSection(
    viewModel: PlayerViewModel,
    exoPlayer: ExoPlayer,
    isEnabled: Boolean,
    config: DanmakuConfig,
) {
    val payload by viewModel.danmakuPayload.collectAsStateWithLifecycle()
    if (payload == null) return

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val livePlaybackState = rememberRealtimePlaybackState(
        exoPlayer = exoPlayer,
        playbackState = playbackState,
        enabled = isEnabled,
    )

    PlayerDanmakuOverlayHost(
        payload = payload,
        isEnabled = isEnabled,
        playbackState = livePlaybackState,
        config = config,
    )
}

@Composable
private fun BoxScope.PlayerOverlaySection(
    viewModel: PlayerViewModel,
    exoPlayer: ExoPlayer,
    uiState: PlayerUiState,
    overlayUiState: PlayerOverlayUiState,
    actions: List<PlayerAction>,
    panelOptions: List<PanelOption>,
    sponsorUiState: PlayerSponsorUiState,
    topRightBadges: List<PlaybackBadge>,
    showOnlineCount: Boolean,
    isDebugOverlayVisible: Boolean,
    isDanmakuEnabled: Boolean,
    isCommentsPanelVisible: Boolean,
    visualEffectsState: PlayerVisualEffectsState,
    focusBindings: PlayerScreenFocusBindings,
    handlePlayerKey: (KeyEvent) -> Boolean,
    onToggleCommentSort: () -> Unit,
    onRetryComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onOpenCommentThread: (ReplyItem) -> Unit,
    onBackFromCommentThread: () -> Unit,
) {
    if (overlayUiState.overlayMode != PlayerOverlayMode.FullControls) return

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val seekPreviewFrame by viewModel.seekPreviewFrame.collectAsStateWithLifecycle()
    val commentsUiState = if (isCommentsPanelVisible) {
        val collectedCommentsUiState by viewModel.commentsUiState.collectAsStateWithLifecycle()
        collectedCommentsUiState
    } else {
        remember { PlayerCommentsUiState() }
    }
    val livePlaybackState = rememberRealtimePlaybackState(
        exoPlayer = exoPlayer,
        playbackState = playbackState,
        enabled = true,
    )
    val sponsorMarkers = remember(
        sponsorUiState.segments,
        playbackState.durationMs,
        sponsorUiState.enabled,
        sponsorUiState.config,
    ) {
        buildSponsorProgressMarks(
            segments = sponsorUiState.segments,
            durationMs = playbackState.durationMs,
            enabled = sponsorUiState.enabled,
            config = sponsorUiState.config,
        )
    }

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
        onToggleCommentSort = onToggleCommentSort,
        onRetryComments = onRetryComments,
        onLoadMoreComments = onLoadMoreComments,
        onOpenCommentThread = onOpenCommentThread,
        onBackFromCommentThread = onBackFromCommentThread,
    )
}

@Composable
private fun BoxScope.PlayerTransientMessageSection(
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    bufferingSpeedMeter: BufferingSpeedMeter,
    showDebugOverlay: Boolean,
    showSponsorSkipNotice: Boolean,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val bufferingOverlayText by rememberBufferingOverlayText(
        isBuffering = playbackState.isBuffering &&
            !uiState.isLoading &&
            uiState.errorMessage.isNullOrBlank(),
        speedMeter = bufferingSpeedMeter,
    )

    PlayerTransientOverlayMessages(
        uiState = uiState,
        bufferingOverlayText = bufferingOverlayText,
        showDebugOverlay = showDebugOverlay,
        showSponsorSkipNotice = showSponsorSkipNotice,
    )
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
