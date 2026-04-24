package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.core.performance.AppPerformanceTracker
import com.bbttvv.app.core.player.BufferingSpeedMeter
import com.bbttvv.app.core.player.createConfiguredPlayer
import com.bbttvv.app.core.util.ScreenUtils
import com.bbttvv.app.core.util.formatLongVideoPubDate
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.player.DanmakuSettings
import com.bbttvv.app.core.store.player.DanmakuSettingsStore
import com.bbttvv.app.core.store.player.toEngineConfig
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.data.model.response.SponsorBlockMarkerMode
import com.bbttvv.app.data.model.response.SponsorCategory
import com.bbttvv.app.data.model.response.SponsorSegment
import com.bbttvv.app.feature.plugin.SponsorBlockConfig
import com.bbttvv.app.feature.plugin.SponsorBlockPlugin
import com.bbttvv.app.feature.plugin.findSponsorBlockPluginInfo
import com.bbttvv.app.feature.video.danmaku.DanmakuOverlay
import com.bbttvv.app.feature.video.viewmodel.PlaybackBadge
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentSortMode
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentsUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerOption
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerViewModel
import com.bbttvv.app.feature.video.viewmodel.ResumePlaybackPrompt
import com.bbttvv.app.feature.video.videoshot.VideoShotFrame
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val CONTROLS_AUTO_HIDE_MS = 3500L
private const val SIMPLE_SEEK_HIDE_MS = 1200L
private const val BUFFERING_OVERLAY_DELAY_MS = 1_000L
private const val BUFFERING_SPEED_REFRESH_MS = 500L

internal data class SponsorProgressMark(
    val startFraction: Float,
    val endFraction: Float,
    val category: String,
)

private data class PlayerDebugSnapshot(
    val videoId: String = "--",
    val viewport: String = "--",
    val frames: String = "--",
    val currentOptimal: String = "--",
    val bufferHealth: String = "--",
    val codecs: String = "--",
    val quality: String = "--",
    val trackBitrate: String = "--",
    val cdn: String = "--",
    val playback: String = "--",
    val date: String = "--",
)

@OptIn(
    ExperimentalTvMaterial3Api::class
)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    bvid: String,
    cid: Long,
    aid: Long,
    startPositionMs: Long,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val context = LocalContext.current
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
        sponsorConfig.showSkipPrompt
    ) {
        showSkipButton &&
            currentSponsorSegment != null &&
            sponsorBlockEnabled &&
            sponsorConfig.showSkipPrompt
    }
    val sponsorMarkers = remember(
        sponsorSegments,
        playbackState.durationMs,
        sponsorBlockEnabled,
        sponsorPluginInfo?.enabled,
        sponsorConfig
    ) {
        buildSponsorProgressMarks(
            segments = sponsorSegments,
            durationMs = playbackState.durationMs,
            enabled = sponsorBlockEnabled,
            config = sponsorConfig
        )
    }
    val topRightBadges = remember(uiState, isDanmakuEnabled) {
        buildTopRightBadges(
            uiState = uiState,
            isDanmakuEnabled = isDanmakuEnabled,
        )
    }
    val visualEffectsState = rememberPlayerVisualEffectsState()
    val overlayStateMachine = remember { PlayerOverlayStateMachine() }
    val overlayUiState = overlayStateMachine.uiState
    var debugSnapshot by remember { mutableStateOf(PlayerDebugSnapshot()) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val actions = remember {
        PlayerAction.entries.filterNot { it == PlayerAction.Audio || it == PlayerAction.Codec }
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
    val progressFocusRequester = remember { FocusRequester() }
    val actionFocusRequesters = remember(actions.size) { List(actions.size) { FocusRequester() } }
    val panelFocusRequesters = remember(panelOptions.size) { List(panelOptions.size) { FocusRequester() } }
    val commentsPanelPrimaryFocusRequester = remember { FocusRequester() }
    val playerFocusCoordinator = remember { PlayerFocusCoordinator() }
    val handleOverlayEffect: (PlayerOverlayEffect) -> Unit = { effect ->
        handlePlayerOverlayEffect(
            effect = effect,
            presentationState = presentationState,
            viewModel = viewModel,
            context = context,
            scope = scope,
            onExitPlayer = onBack,
        )
    }
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
    val useRealtimePlaybackState = overlayUiState.overlayMode == PlayerOverlayMode.FullControls ||
        (danmakuPayload != null && isDanmakuEnabled) ||
        overlayUiState.showDebugOverlay
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

    LaunchedEffect(playbackState.isPlaybackActive) {
        ScreenUtils.setPlaybackKeepScreenOn(
            context = context,
            keepScreenOn = playbackState.isPlaybackActive,
        )
    }

    BackHandler {
        when {
            uiState.resumePrompt != null -> viewModel.dismissResumePlaybackPrompt()
            isCommentsPanelVisible && commentsUiState.isViewingThread -> viewModel.closeCommentThread()
            isCommentsPanelVisible -> presentationState.hideCommentsPanel()
            else -> overlayStateMachine.handleBack(handleOverlayEffect)
        }
    }

    DisposableEffect(exoPlayer) {
        val perfListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                AppPerformanceTracker.endSpanOnce(
                    key = "first_playback_open",
                    milestone = "first_playback_first_frame",
                    extras = "bvid=$bvid cid=$cid aid=$aid"
                )
            }
        }
        exoPlayer.addListener(perfListener)
        viewModel.attachPlayer(exoPlayer)
        onDispose {
            ScreenUtils.setPlaybackKeepScreenOn(context = context, keepScreenOn = false)
            viewModel.finishPlaybackSession(reason = "screen_dispose")
            exoPlayer.removeListener(perfListener)
            exoPlayer.release()
        }
    }

    DisposableEffect(playerFocusCoordinator, playerViewRef) {
        val playerView = playerViewRef
        val registration = playerView?.let {
            playerFocusCoordinator.registerPlayerSurfaceTarget(
                object : PlayerFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return it.requestFocus()
                    }
                }
            )
        }
        onDispose {
            registration?.unregister()
        }
    }

    DisposableEffect(playerFocusCoordinator, progressFocusRequester) {
        val registration = playerFocusCoordinator.registerProgressTarget(
            object : PlayerFocusTarget {
                override fun tryRequestFocus(): Boolean {
                    return runCatching {
                        progressFocusRequester.requestFocus()
                    }.getOrDefault(false)
                }
            }
        )
        onDispose {
            registration.unregister()
        }
    }

    DisposableEffect(playerFocusCoordinator, actionFocusRequesters) {
        val registrations = actionFocusRequesters.mapIndexed { index, requester ->
            playerFocusCoordinator.registerActionTarget(
                index = index,
                target = object : PlayerFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return runCatching {
                            requester.requestFocus()
                        }.getOrDefault(false)
                    }
                }
            )
        }
        onDispose {
            registrations.forEach(PlayerFocusTargetRegistration::unregister)
        }
    }

    DisposableEffect(playerFocusCoordinator, panelFocusRequesters) {
        val registrations = panelFocusRequesters.mapIndexed { index, requester ->
            playerFocusCoordinator.registerPanelOptionTarget(
                index = index,
                target = object : PlayerFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return runCatching {
                            requester.requestFocus()
                        }.getOrDefault(false)
                    }
                }
            )
        }
        onDispose {
            registrations.forEach(PlayerFocusTargetRegistration::unregister)
        }
    }

    DisposableEffect(playerFocusCoordinator, commentsPanelPrimaryFocusRequester) {
        val registration = playerFocusCoordinator.registerCommentsPanelTarget(
            object : PlayerFocusTarget {
                override fun tryRequestFocus(): Boolean {
                    return runCatching {
                        commentsPanelPrimaryFocusRequester.requestFocus()
                    }.getOrDefault(false)
                }
            }
        )
        onDispose {
            registration.unregister()
        }
    }

    LaunchedEffect(bvid, cid, aid, startPositionMs) {
        AppPerformanceTracker.beginSpanOnce("first_playback_open")
        bufferingSpeedMeter.reset()
        overlayStateMachine.resetForNewVideo(handleOverlayEffect)
        debugSnapshot = PlayerDebugSnapshot()
        presentationState.resetForNewVideo()
        viewModel.loadVideo(
            bvid = bvid,
            aid = aid,
            cid = cid,
            startPositionMs = startPositionMs
        )
    }

    LaunchedEffect(isCommentsPanelVisible, uiState.info?.aid) {
        if (isCommentsPanelVisible && (uiState.info?.aid ?: 0L) > 0L) {
            viewModel.ensureCommentsLoaded()
        }
    }

    LaunchedEffect(overlayUiState.activePanel, overlayUiState.overlayMode) {
        presentationState.syncOverlayVisibility(overlayUiState)
    }

    LaunchedEffect(overlayUiState.showDebugOverlay, exoPlayer) {
        if (!overlayUiState.showDebugOverlay) return@LaunchedEffect
        while (overlayStateMachine.uiState.showDebugOverlay) {
            debugSnapshot = buildPlayerDebugSnapshot(
                player = exoPlayer,
                uiState = uiState,
                playbackState = playbackSnapshotProvider(),
            )
            delay(500L)
        }
    }

    LaunchedEffect(playbackState.isPlaying) {
        if (!playbackState.isPlaying) {
            overlayStateMachine.onPlaybackPaused()
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { latestUiState.value.statusMessage.orEmpty() }
            .distinctUntilChanged()
            .collectLatest { statusMessage ->
                if (statusMessage.isBlank()) return@collectLatest
                overlayStateMachine.showFullOverlay(
                    focus = PlayerFullControlsFocus.Progress,
                    onEffect = latestHandleOverlayEffect.value,
                )
                delay(1800L)
                viewModel.clearStatusMessage()
            }
    }

    LaunchedEffect(overlayUiState.activePanel, panelOptionsFocusKey) {
        overlayStateMachine.syncPanelOptions(panelOptions)
    }

    LaunchedEffect(
        overlayUiState.overlayMode,
        overlayUiState.fullControlsFocus,
        overlayUiState.selectedActionIndex,
        overlayUiState.activePanel,
        overlayUiState.selectedPanelIndex,
        panelOptions.size,
        isCommentsPanelVisible,
        commentsUiState.isViewingThread,
    ) {
        val focusIntent = when {
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
        playerFocusCoordinator.requestFocus(focusIntent)
        withFrameNanos { }
        playerFocusCoordinator.drainPendingFocus()
    }

    LaunchedEffect(
        overlayUiState.interactionToken,
        overlayUiState.overlayMode,
        playbackState.isPlaying,
        uiState.isLoading,
        uiState.errorMessage,
        showSponsorSkipNotice,
        overlayUiState.activePanel,
        isCommentsPanelVisible,
    ) {
        val shouldHide = overlayUiState.overlayMode == PlayerOverlayMode.FullControls &&
            overlayUiState.activePanel == null &&
            !isCommentsPanelVisible &&
            playbackState.isPlaying &&
            !uiState.isLoading &&
            uiState.errorMessage.isNullOrBlank() &&
            !showSponsorSkipNotice
        if (shouldHide) {
            delay(CONTROLS_AUTO_HIDE_MS)
            val latestOverlayState = overlayStateMachine.uiState
            if (playbackState.isPlaying && latestOverlayState.activePanel == null && !isCommentsPanelVisible) {
                overlayStateMachine.hideOverlay(onEffect = handleOverlayEffect)
            }
        }
    }

    LaunchedEffect(overlayUiState.simpleSeekState, overlayUiState.isScrubbing, overlayUiState.overlayMode) {
        if (
            overlayUiState.simpleSeekState != null &&
            overlayUiState.overlayMode == PlayerOverlayMode.FullControls &&
            !overlayUiState.isScrubbing
        ) {
            delay(SIMPLE_SEEK_HIDE_MS)
            val latestOverlayState = overlayStateMachine.uiState
            if (
                latestOverlayState.overlayMode == PlayerOverlayMode.FullControls &&
                !latestOverlayState.isScrubbing
            ) {
                overlayStateMachine.dismissSeekPreview(onEffect = handleOverlayEffect)
            }
        }
    }

    LaunchedEffect(overlayUiState.isScrubbing, overlayUiState.scrubDirection) {
        if (!overlayUiState.isScrubbing || overlayUiState.scrubDirection == 0) return@LaunchedEffect
        while (overlayStateMachine.uiState.isScrubbing) {
            overlayStateMachine.tickScrubPreview(
                playbackState = playbackSnapshotProvider(),
                onEffect = handleOverlayEffect,
            )
            delay(HOLD_SCRUB_TICK_MS)
        }
    }

    LaunchedEffect(overlayUiState.pendingSeekKeyCode, overlayUiState.pendingSeekDirection) {
        if (
            overlayUiState.pendingSeekDirection == 0 ||
            overlayUiState.pendingSeekKeyCode == android.view.KeyEvent.KEYCODE_UNKNOWN
        ) {
            return@LaunchedEffect
        }
        delay(ViewConfiguration.getLongPressTimeout().toLong())
        val latestOverlayState = overlayStateMachine.uiState
        if (
            latestOverlayState.pendingSeekDirection != 0 &&
            latestOverlayState.pendingSeekKeyCode != android.view.KeyEvent.KEYCODE_UNKNOWN &&
            !latestOverlayState.isScrubbing
        ) {
            overlayStateMachine.promotePendingSeekToScrub(
                playbackState = playbackSnapshotProvider(),
                pauseForSeekScrub = viewModel::pauseForSeekScrub,
                onEffect = handleOverlayEffect,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .playerBackdropSource(visualEffectsState),
    ) {
        PlayerSurface(
            exoPlayer = exoPlayer,
            keepScreenOn = playbackState.isPlaybackActive,
            overlayMode = overlayUiState.overlayMode,
            onHiddenOverlayKey = { event ->
                overlayStateMachine.handleKeyEvent(
                    event = event,
                    playbackState = playbackSnapshotProvider(),
                    actions = actions,
                    panelOptions = panelOptions,
                    pauseForSeekScrub = viewModel::pauseForSeekScrub,
                    onEffect = handleOverlayEffect,
                )
            },
            onViewAvailable = { playerViewRef = it },
            onPlayerSurfaceFocusNeeded = {
                playerFocusCoordinator.requestFocus(PlayerFocusIntent.FocusPlayerSurface)
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (danmakuPayload != null) {
            DanmakuOverlay(
                payload = danmakuPayload,
                isEnabled = isDanmakuEnabled,
                isPlaying = livePlaybackState.value.isPlaying,
                playbackPositionMs = livePlaybackState.value.positionMs,
                config = danmakuConfig,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (overlayUiState.overlayMode == PlayerOverlayMode.FullControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                    ),
            )
        }

        if (overlayUiState.overlayMode == PlayerOverlayMode.FullControls && showOnlineCount && uiState.onlineCountText.isNotBlank()) {
            OnlineCountPill(
                text = uiState.onlineCountText,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = PlayerLayoutTokens.topOnlinePadding,
                        top = PlayerLayoutTokens.topBadgePadding,
                    ),
            )
        }

        if (overlayUiState.overlayMode == PlayerOverlayMode.FullControls && topRightBadges.isNotEmpty() && !overlayUiState.showDebugOverlay) {
            BadgeRow(
                badges = topRightBadges,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = PlayerLayoutTokens.topBadgePadding,
                        end = PlayerLayoutTokens.topOnlinePadding,
                    ),
            )
        }

        if (overlayUiState.showDebugOverlay) {
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

        if (overlayUiState.overlayMode == PlayerOverlayMode.FullControls) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { keyEvent ->
                        if (isCommentsPanelVisible) {
                            false
                        } else {
                            overlayStateMachine.handleKeyEvent(
                                event = keyEvent.nativeKeyEvent,
                                playbackState = playbackSnapshotProvider(),
                                actions = actions,
                                panelOptions = panelOptions,
                                pauseForSeekScrub = viewModel::pauseForSeekScrub,
                                onEffect = handleOverlayEffect,
                            )
                        }
                    },
            ) {
                    PlayerInfoOverlay(
                        uiState = uiState,
                        playbackState = livePlaybackState,
                    sponsorMarkers = sponsorMarkers,
                    isProgressFocused = overlayUiState.fullControlsFocus == PlayerFullControlsFocus.Progress &&
                        overlayUiState.activePanel == null,
                    progressPreviewState = overlayUiState.simpleSeekState,
                    progressModifier = Modifier
                        .focusRequester(progressFocusRequester)
                        .focusable(),
                    actionBar = {
                        PlayerActionBar(
                            actions = actions,
                            selectedIndex = overlayUiState.selectedActionIndex,
                            hasFocus = overlayUiState.fullControlsFocus == PlayerFullControlsFocus.Actions ||
                                overlayUiState.activePanel != null,
                            isDanmakuEnabled = isDanmakuEnabled,
                            actionFocusRequesters = actionFocusRequesters,
                        )
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = PlayerLayoutTokens.overlayHorizontalPadding,
                            end = PlayerLayoutTokens.overlayHorizontalPadding,
                            bottom = PlayerLayoutTokens.overlayBottomPadding,
                        ),
                )

                val currentSeekPreviewState = overlayUiState.simpleSeekState
                if (currentSeekPreviewState != null) {
                    SimpleSeekOverlay(
                        state = currentSeekPreviewState,
                        previewFrame = seekPreviewFrame,
                        playbackState = livePlaybackState,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = PlayerLayoutTokens.overlayHorizontalPadding,
                                end = PlayerLayoutTokens.overlayHorizontalPadding,
                                bottom = PlayerLayoutTokens.seekPreviewBottomPadding,
                            )
                            .widthIn(max = 1080.dp)
                            .fillMaxWidth(),
                    )
                }

                if (overlayUiState.activePanel != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = PlayerLayoutTokens.overlayHorizontalPadding,
                                end = PlayerLayoutTokens.overlayHorizontalPadding,
                                bottom = PlayerLayoutTokens.panelBottomPadding,
                            )
                            .widthIn(max = 1080.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        PlayerOptionsPanel(
                            title = panelTitleFor(overlayUiState.activePanel),
                            options = panelOptions,
                            selectedIndex = overlayUiState.selectedPanelIndex,
                            visualEffectsState = visualEffectsState,
                            optionFocusRequesters = panelFocusRequesters,
                        )
                    }
                }

                if (isCommentsPanelVisible) {
                    PlayerCommentsPanel(
                        uiState = commentsUiState,
                        totalCommentCount = maxOf(commentsUiState.totalCount, uiState.info?.stat?.reply ?: 0),
                        primaryFocusRequester = commentsPanelPrimaryFocusRequester,
                        onToggleSort = viewModel::toggleCommentSort,
                        onRetry = viewModel::refreshComments,
                        onLoadMore = viewModel::loadMoreComments,
                        onOpenThread = viewModel::openCommentThread,
                        onBackFromThread = viewModel::closeCommentThread,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(
                                top = PlayerLayoutTokens.commentsPanelEdgePadding,
                                end = PlayerLayoutTokens.commentsPanelEdgePadding,
                                bottom = PlayerLayoutTokens.commentsPanelEdgePadding,
                            ),
                    )
                }
            }
        }

        if (uiState.isLoading) {
            OverlayMessage(
                text = "正在加载播放信息...",
                modifier = Modifier.align(Alignment.Center),
            )
        }

        bufferingOverlayText?.let { text ->
            OverlayMessage(
                text = text,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
            OverlayMessage(
                text = uiState.errorMessage.orEmpty(),
                containerColor = Color(0xCC7F1D1D),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (!uiState.statusMessage.isNullOrBlank()) {
            OverlayMessage(
                text = uiState.statusMessage.orEmpty(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = if (overlayUiState.showDebugOverlay) 210.dp else PlayerLayoutTokens.statusMessageTopPadding,
                        end = PlayerLayoutTokens.debugEdgePadding,
                    ),
            )
        }

        if (showSponsorSkipNotice) {
            OverlayMessage(
                text = "检测到可跳过片段，按确认键可跳过",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = PlayerLayoutTokens.sponsorNoticeBottomPadding,
                        bottom = PlayerLayoutTokens.sponsorNoticeBottomPadding,
                    ),
            )
        }
    }

    uiState.resumePrompt?.let { prompt ->
        ResumePlaybackDialog(
            prompt = prompt,
            onConfirm = viewModel::confirmResumePlayback,
            onDismiss = viewModel::dismissResumePlaybackPrompt,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)


@Composable
private fun PlayerActionBar(
    actions: List<PlayerAction>,
    selectedIndex: Int,
    hasFocus: Boolean,
    isDanmakuEnabled: Boolean,
    actionFocusRequesters: List<FocusRequester>,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            PlayerActionButton(
                action = action,
                selected = hasFocus && index == selectedIndex,
                active = action != PlayerAction.Danmaku || isDanmakuEnabled,
                modifier = Modifier
                    .focusRequester(actionFocusRequesters[index])
                    .focusable(),
            )
        }
    }
}

@Composable
private fun PlayerActionButton(
    action: PlayerAction,
    selected: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) {
                    Color.White.copy(alpha = 0.94f)
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.tv.material3.Icon(
            imageVector = actionIcon(action),
            contentDescription = null,
            tint = if (selected) Color(0xFF111111) else Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = actionButtonLabel(action),
            color = if (selected) Color(0xFF111111) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun actionButtonLabel(action: PlayerAction): String {
    return when (action) {
        PlayerAction.Detail -> "详情"
        PlayerAction.Comments -> "评论"
        PlayerAction.Speed -> "倍速"
        PlayerAction.Quality -> "画质"
        PlayerAction.Danmaku -> "弹幕"
        PlayerAction.Audio -> "音频"
        PlayerAction.Codec -> "码率"
    }
}

private fun actionIcon(action: PlayerAction): androidx.compose.ui.graphics.vector.ImageVector {
    return when (action) {
        PlayerAction.Detail -> Icons.Outlined.Info
        PlayerAction.Comments -> Icons.Outlined.Email
        PlayerAction.Speed -> Icons.Outlined.PlayArrow
        PlayerAction.Quality -> Icons.Outlined.Settings
        PlayerAction.Danmaku -> Icons.Outlined.Create
        PlayerAction.Audio -> Icons.Outlined.Settings
        PlayerAction.Codec -> Icons.Outlined.Info
    }
}

private fun actionSecondaryText(
    action: PlayerAction,
    uiState: PlayerUiState,
    isDanmakuEnabled: Boolean,
): String? {
    return when (action) {
        PlayerAction.Comments -> null
        PlayerAction.Speed -> formatSpeed(uiState.playbackSpeed)
        PlayerAction.Quality -> uiState.selectedQualityLabel
            .ifBlank { null }
            ?.let(::compactActionValue)
        PlayerAction.Audio -> selectedAudioActionValue(uiState)?.let(::compactActionValue)
        PlayerAction.Codec -> buildCodecActionValue(uiState)?.let(::compactActionValue)
        PlayerAction.Danmaku -> if (isDanmakuEnabled) "开启" else "关闭"
        PlayerAction.Detail -> null
    }
}

private fun buildTopRightBadges(
    uiState: PlayerUiState,
    isDanmakuEnabled: Boolean,
): List<PlaybackBadge> {
    val seen = linkedSetOf<String>()
    val duplicatedCodecLabels = buildSet {
        uiState.selectedVideoCodecLabel.trim().takeIf { it.isNotBlank() }?.let(::add)
        uiState.selectedAudioCodecLabel.trim().takeIf { it.isNotBlank() }?.let(::add)
    }
    return buildList {
        fun addUnique(label: String?, isActive: Boolean = false) {
            val normalized = label?.trim().orEmpty()
            if (normalized.isBlank() || !seen.add(normalized)) return
            add(PlaybackBadge(label = normalized, isActive = isActive))
        }

        addUnique(
            actionSecondaryText(
                action = PlayerAction.Quality,
                uiState = uiState,
                isDanmakuEnabled = isDanmakuEnabled,
            )?.let { "质量 $it" }
        )
        addUnique(if (isDanmakuEnabled) "弹幕 开启" else "弹幕 关闭", isActive = isDanmakuEnabled)
        addUnique(
            actionSecondaryText(
                action = PlayerAction.Audio,
                uiState = uiState,
                isDanmakuEnabled = isDanmakuEnabled,
            )?.let { "音频 $it" }
        )
        addUnique(
            actionSecondaryText(
                action = PlayerAction.Codec,
                uiState = uiState,
                isDanmakuEnabled = isDanmakuEnabled,
            )?.let { "码率 $it" }
        )

        uiState.playbackBadges.forEach { badge ->
            val normalized = badge.label.trim()
            if (normalized.isBlank()) return@forEach
            val isDuplicatedCodecBadge = duplicatedCodecLabels.any { it.equals(normalized, ignoreCase = true) }
            if (!isDuplicatedCodecBadge && seen.add(normalized)) {
                add(badge.copy(label = normalized))
            }
        }
    }
}

private fun selectedAudioActionValue(uiState: PlayerUiState): String? {
    return uiState.audioOptions.firstOrNull { it.isSelected }?.label
        ?.takeIf { it.isNotBlank() }
        ?: uiState.selectedAudioCodecLabel.takeIf { it.isNotBlank() }
}

private fun buildCodecActionValue(uiState: PlayerUiState): String? {
    val codecLabel = uiState.selectedVideoCodecLabel.takeIf { it.isNotBlank() }
        ?: uiState.videoCodecOptions.firstOrNull { it.isSelected }?.label?.takeIf { it.isNotBlank() }
    val totalBitrate = uiState.selectedVideoBandwidth.coerceAtLeast(0) +
        uiState.selectedAudioBandwidth.coerceAtLeast(0)
    val bitrateLabel = totalBitrate.takeIf { it > 0 }?.let(::formatCompactBitrate)
    return listOfNotNull(codecLabel, bitrateLabel).joinToString(" ").ifBlank { null }
}

private fun compactActionValue(value: String): String {
    return value
        .replace("高码率", "高码")
        .replace("高码率", "低码")
        .replace("音频", "音频")
        .replace("码率", "码率")
        .replace(" ", "")
}

private fun formatCompactBitrate(bitrate: Int): String {
    if (bitrate <= 0) return "--"
    val kbps = bitrate / 1000f
    return if (kbps >= 1000f) {
        String.format(Locale.US, "%.1f", kbps / 1000f).removeSuffix(".0") + "M"
    } else {
        "${kbps.roundToInt()}K"
    }
}

@Composable
private fun PlayerOptionsPanel(
    title: String,
    options: List<PanelOption>,
    selectedIndex: Int,
    visualEffectsState: PlayerVisualEffectsState,
    optionFocusRequesters: List<FocusRequester>,
    modifier: Modifier = Modifier,
) {
    val usesSettingRows = options.any { it.presentation == PanelOptionPresentation.Setting }
    val panelWidth = if (usesSettingRows) 236.dp else 168.dp
    val panelMaxHeight = if (usesSettingRows) 156.dp else 118.dp
    val panelCorner = if (usesSettingRows) 18.dp else 16.dp
    Column(
        modifier = modifier
            .width(panelWidth)
            .clip(RoundedCornerShape(panelCorner))
            .playerPanelSurfaceEffect(visualEffectsState)
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(panelCorner))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (options.isEmpty()) {
            Text(
                text = "当前格式不支持切换",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 10.sp,
            )
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = panelMaxHeight),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                itemsIndexed(options) { index, option ->
                    PlayerOptionRow(
                        option = option,
                        selected = index == selectedIndex,
                        modifier = Modifier
                            .focusRequester(optionFocusRequesters[index])
                            .focusable(enabled = option.isEnabled),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerOptionRow(
    option: PanelOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (option.presentation == PanelOptionPresentation.Setting) {
        PlayerSettingOptionRow(
            option = option,
            selected = selected,
            modifier = modifier,
        )
        return
    }
    val contentColor = when {
        selected -> Color(0xFF111111)
        option.isEnabled -> Color.White
        else -> Color.White.copy(alpha = 0.42f)
    }
    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White.copy(alpha = 0.94f) else Color.Transparent)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (option.isSelected) "✓" else " ",
            color = contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(13.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = option.label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.subtitle?.let {
                Text(
                    text = it,
                    color = contentColor.copy(alpha = if (selected) 0.72f else 0.62f),
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PlayerSettingOptionRow(
    option: PanelOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val titleColor = when {
        selected -> Color.White
        option.isEnabled -> Color(0xF2FFFFFF)
        else -> Color.White.copy(alpha = 0.42f)
    }
    val subtitleColor = when {
        selected -> Color(0xFFB9D1FF)
        option.isEnabled -> Color(0x99E4EBF5)
        else -> Color.White.copy(alpha = 0.32f)
    }
    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    Color.White.copy(alpha = 0.20f)
                } else {
                    Color.White.copy(alpha = 0.05f)
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.White.copy(alpha = 0.40f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = option.label,
                color = titleColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            option.subtitle?.let {
                Text(
                    text = it,
                    color = subtitleColor,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        option.valueText?.let {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.24f),
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = it,
                    color = if (selected) Color.White else Color(0xFFE5ECF7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}


@Composable
private fun SimpleSeekOverlay(
    state: SimpleSeekState,
    previewFrame: VideoShotFrame?,
    playbackState: State<PlayerPlaybackState>,
    modifier: Modifier = Modifier,
) {
    val currentPlaybackState = playbackState.value
    if (previewFrame == null || currentPlaybackState.durationMs <= 0L) return

    val progress = when {
        currentPlaybackState.durationMs <= 0L -> 0f
        else -> (state.targetPositionMs.toFloat() / currentPlaybackState.durationMs.toFloat()).coerceIn(0f, 1f)
    }
    val previewWidth = 165.dp

    BoxWithConstraints(
        modifier = modifier.height(96.dp),
    ) {
        val maxOffset = (maxWidth - previewWidth).coerceAtLeast(0.dp)
        val previewOffset = ((maxWidth * progress) - (previewWidth / 2)).coerceIn(0.dp, maxOffset)

        VideoShotPreviewImage(
            frame = previewFrame,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = previewOffset, y = (-6).dp)
                .width(previewWidth),
        )
    }
}

@Composable
private fun VideoShotPreviewImage(
    frame: VideoShotFrame,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(frame.spriteSheet) { frame.spriteSheet.asImageBitmap() }
    Canvas(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
    ) {
        val crop = calculateCenteredCrop(
            left = frame.srcRect.left,
            top = frame.srcRect.top,
            width = frame.srcRect.width(),
            height = frame.srcRect.height(),
            targetAspect = 16f / 9f,
        )
        drawImage(
            image = imageBitmap,
            srcOffset = IntOffset(crop.left, crop.top),
            srcSize = IntSize(crop.width, crop.height),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        )
    }
}

private data class CropRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

private fun calculateCenteredCrop(
    left: Int,
    top: Int,
    width: Int,
    height: Int,
    targetAspect: Float,
): CropRect {
    val sourceWidth = width.coerceAtLeast(1)
    val sourceHeight = height.coerceAtLeast(1)
    val sourceAspect = sourceWidth.toFloat() / sourceHeight.toFloat()
    if (abs(sourceAspect - targetAspect) < 0.001f) {
        return CropRect(left = left, top = top, width = sourceWidth, height = sourceHeight)
    }
    return if (sourceAspect > targetAspect) {
        val croppedWidth = (sourceHeight * targetAspect).roundToInt().coerceIn(1, sourceWidth)
        val inset = ((sourceWidth - croppedWidth) / 2).coerceAtLeast(0)
        CropRect(left = left + inset, top = top, width = croppedWidth, height = sourceHeight)
    } else {
        val croppedHeight = (sourceWidth / targetAspect).roundToInt().coerceIn(1, sourceHeight)
        val inset = ((sourceHeight - croppedHeight) / 2).coerceAtLeast(0)
        CropRect(left = left, top = top + inset, width = sourceWidth, height = croppedHeight)
    }
}

@Composable
private fun PlayerDebugOverlay(
    snapshot: PlayerDebugSnapshot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(min = 420.dp, max = 520.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DebugInfoLine("Video ID / CID", snapshot.videoId)
        DebugInfoLine("Viewport / Frames", "${snapshot.viewport} / ${snapshot.frames}")
        DebugInfoLine("Current / Optimal Res", snapshot.currentOptimal)
        DebugInfoLine("Buffer Health", snapshot.bufferHealth)
        DebugInfoLine("Codecs", snapshot.codecs)
        DebugInfoLine("Quality", snapshot.quality)
        DebugInfoLine("Track Bitrate", snapshot.trackBitrate)
        DebugInfoLine("CDN", snapshot.cdn)
        DebugInfoLine("Playback", snapshot.playback)
        DebugInfoLine("Date", snapshot.date)
    }
}

@Composable
private fun DebugInfoLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(126.dp),
            maxLines = 1,
        )
        Text(
            text = value.ifBlank { "--" },
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OnlineCountPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Color(0xFFFB7299)),
        )
        Text(
            text = text,
            color = Color(0xEAF7FAFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun rememberBufferingOverlayText(
    isBuffering: Boolean,
    speedMeter: BufferingSpeedMeter,
): State<String?> {
    return produceState<String?>(
        initialValue = null,
        key1 = isBuffering,
        key2 = speedMeter,
    ) {
        value = null
        if (!isBuffering) return@produceState

        speedMeter.reset()
        delay(BUFFERING_OVERLAY_DELAY_MS)

        while (true) {
            val bytesPerSecond = speedMeter.bytesPerSecond()
            value = if (bytesPerSecond > 0L) {
                "正在缓冲 ${formatTransferSpeed(bytesPerSecond)}"
            } else {
                "正在缓冲..."
            }
            delay(BUFFERING_SPEED_REFRESH_MS)
        }
    }
}

private fun formatTransferSpeed(bytesPerSecond: Long): String {
    val kilobyte = 1024L
    val megabyte = kilobyte * 1024L
    return when {
        bytesPerSecond >= megabyte -> String.format(
            Locale.US,
            "%.1f MB/s",
            bytesPerSecond.toDouble() / megabyte.toDouble(),
        )
        bytesPerSecond >= kilobyte -> String.format(
            Locale.US,
            "%.0f KB/s",
            bytesPerSecond.toDouble() / kilobyte.toDouble(),
        )
        else -> "$bytesPerSecond B/s"
    }
}

@Composable
private fun OverlayMessage(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Black.copy(alpha = 0.64f),
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun BadgeRow(
    badges: List<PlaybackBadge>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(badges) { badge ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = if (badge.isActive) 0.28f else 0.15f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = badge.label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun buildSponsorProgressMarks(
    segments: List<SponsorSegment>,
    durationMs: Long,
    enabled: Boolean,
    config: SponsorBlockConfig,
): List<SponsorProgressMark> {
    if (!enabled || durationMs <= 0L || config.markerMode == SponsorBlockMarkerMode.OFF) {
        return emptyList()
    }
    return segments.mapNotNull { segment ->
        if (!segment.isSkipType || !config.isCategoryEnabled(segment.category)) return@mapNotNull null
        if (config.markerMode == SponsorBlockMarkerMode.SPONSOR_ONLY && segment.category != SponsorCategory.SPONSOR) {
            return@mapNotNull null
        }
        val start = segment.startTimeMs.coerceAtLeast(0L)
        val end = segment.endTimeMs.coerceAtLeast(0L)
        if (end <= start) return@mapNotNull null
        val startFraction = (start.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        val endFraction = (end.toFloat() / durationMs.toFloat()).coerceIn(startFraction, 1f)
        SponsorProgressMark(
            startFraction = startFraction,
            endFraction = endFraction,
            category = segment.category,
        )
    }
}

internal fun sponsorMarkColor(category: String): Color {
    return when (category) {
        SponsorCategory.SPONSOR -> Color(0xFFFB7299).copy(alpha = 0.88f)
        SponsorCategory.INTRO,
        SponsorCategory.OUTRO -> Color(0xFF38BDF8).copy(alpha = 0.82f)
        SponsorCategory.INTERACTION -> Color(0xFFFBBF24).copy(alpha = 0.84f)
        SponsorCategory.SELFPROMO -> Color(0xFFA78BFA).copy(alpha = 0.84f)
        else -> Color(0xFF34D399).copy(alpha = 0.82f)
    }
}

internal fun formatCount(value: Int): String {
    return when {
        value >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", value / 100_000_000f)
        value >= 10_000 -> String.format(Locale.CHINA, "%.1f万", value / 10_000f)
        else -> value.toString()
    }.removeSuffix(".0亿").removeSuffix(".0万")
}

internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

internal fun formatRemainingDuration(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0L) return "--:--"
    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
    return "-${formatDuration(remaining)}"
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun buildPlayerDebugSnapshot(
    player: ExoPlayer,
    uiState: PlayerUiState,
    playbackState: PlayerPlaybackState,
): PlayerDebugSnapshot {
    val videoSize = player.videoSize
    val width = uiState.selectedVideoWidth.takeIf { it > 0 } ?: videoSize.width
    val height = uiState.selectedVideoHeight.takeIf { it > 0 } ?: videoSize.height
    val resolution = if (width > 0 && height > 0) "${width}x${height}" else "--"
    val frameRate = uiState.selectedVideoFrameRate.takeIf { it.isNotBlank() }
    val optimal = buildList {
        add(resolution)
        frameRate?.let { add(it) }
    }.joinToString(" ")
    val counters = player.videoDecoderCounters
    counters?.ensureUpdated()
    val droppedFrames = counters?.droppedBufferCount ?: 0
    val renderedFrames = counters?.renderedOutputBufferCount ?: 0
    val bufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
    val totalBitrate = uiState.selectedVideoBandwidth.coerceAtLeast(0) +
        uiState.selectedAudioBandwidth.coerceAtLeast(0)
    val playbackLabel = "${formatPlayerState(player.playbackState)} / " +
        "playing=${player.isPlaying} / speed=${formatSpeed(player.playbackParameters.speed)}"

    return PlayerDebugSnapshot(
        videoId = listOfNotNull(
            uiState.info?.bvid?.takeIf { it.isNotBlank() },
            uiState.info?.cid?.takeIf { it > 0L }?.let { "cid=$it" }
        ).joinToString(" / ").ifBlank { "--" },
        viewport = resolution,
        frames = "rendered $renderedFrames / dropped $droppedFrames",
        currentOptimal = "${formatDuration(playbackState.positionMs)} / $optimal",
        bufferHealth = formatDebugSeconds(bufferMs),
        codecs = buildDebugCodecs(uiState),
        quality = uiState.selectedQualityLabel.ifBlank { uiState.selectedQuality.toString() },
        trackBitrate = if (totalBitrate > 0) "${totalBitrate / 1000} Kbps" else "--",
        cdn = buildDebugCdn(uiState),
        playback = playbackLabel,
        date = SimpleDateFormat("MMM d yyyy HH:mm:ss", Locale.US).format(Date()),
    )
}

private fun buildDebugCodecs(uiState: PlayerUiState): String {
    return buildList {
        uiState.selectedVideoCodecLabel.takeIf { it.isNotBlank() }?.let { add("video=$it") }
        uiState.selectedAudioCodecLabel.takeIf { it.isNotBlank() }?.let { add("audio=$it") }
    }.joinToString(" / ").ifBlank { "--" }
}

private fun buildDebugCdn(uiState: PlayerUiState): String {
    return buildList {
        uiState.videoCdnHost.takeIf { it.isNotBlank() }?.let { add("v=$it") }
        uiState.audioCdnHost.takeIf { it.isNotBlank() }?.let { add("a=$it") }
    }.joinToString(" / ").ifBlank { "--" }
}

private fun formatDebugSeconds(durationMs: Long): String {
    return String.format(Locale.US, "%.2f s", durationMs.coerceAtLeast(0L) / 1000f)
}

private fun formatPlayerState(state: Int): String {
    return when (state) {
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        Player.STATE_IDLE -> "idle"
        else -> "unknown"
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}倍速"
    } else {
        "${speed}倍速"
    }
}

