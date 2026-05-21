package com.bbttvv.app.feature.video.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bbttvv.app.core.performance.AppPerformanceTracker
import com.bbttvv.app.core.player.BufferingSpeedMeter
import com.bbttvv.app.core.player.ExoPlayerReleaseGuard
import com.bbttvv.app.core.player.clearPlayerViewReference
import com.bbttvv.app.core.util.ScreenUtils
import com.bbttvv.app.feature.video.viewmodel.PlayerEvent
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val CONTROLS_AUTO_HIDE_MS = 3500L
private const val SIMPLE_SEEK_HIDE_MS = 1200L
private const val AUTO_NEXT_PROMPT_DELAY_MS = 2_000L

internal data class PlayerScreenSession(
    val bvid: String,
    val cid: Long,
    val aid: Long,
    val startPositionMs: Long,
)

internal data class PlayerScreenEffectArgs(
    val session: PlayerScreenSession,
    val uiState: PlayerUiState,
    val overlayUiState: PlayerOverlayUiState,
    val panelOptions: List<PanelOption>,
    val panelOptionsFocusKey: String,
    val isCommentsPanelVisible: Boolean,
    val showSponsorSkipNotice: Boolean,
    val playerView: PlayerView?,
    val exoPlayer: ExoPlayer,
    val exitTrace: PlayerExitTrace,
    val isDebugOverlayVisible: Boolean,
    val bufferingSpeedMeter: BufferingSpeedMeter,
    val playbackSnapshotProvider: () -> PlayerPlaybackState,
    val latestHandleOverlayEffect: State<(PlayerOverlayEffect) -> Unit>,
    val latestUiState: State<PlayerUiState>,
)

@Composable
internal fun PlayerScreenEffectHost(
    context: Context,
    viewModel: PlayerViewModel,
    presentationState: PlayerOverlayPresentationState,
    overlayStateMachine: PlayerOverlayStateMachine,
    playerFocusCoordinator: PlayerFocusCoordinator,
    focusBindings: PlayerScreenFocusBindings,
    handleOverlayEffect: (PlayerOverlayEffect) -> Unit,
    onExitPlayer: () -> Unit,
    onDebugSnapshotChange: (PlayerDebugSnapshot) -> Unit,
    args: PlayerScreenEffectArgs,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isViewingCommentThread by remember(viewModel) {
        viewModel.commentsUiState
            .map { commentsUiState -> commentsUiState.isViewingThread }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val latestDebugSnapshotChange = rememberUpdatedState(onDebugSnapshotChange)
    val latestExitTrace = rememberUpdatedState(args.exitTrace)
    val latestPlayerView = rememberUpdatedState(args.playerView)
    val latestOnExitPlayer = rememberUpdatedState(onExitPlayer)
    val playerReleaseGuard = remember(args.exoPlayer) { ExoPlayerReleaseGuard(args.exoPlayer) }

    BackHandler {
        when {
            args.uiState.resumePrompt != null -> viewModel.dismissResumePlaybackPrompt()
            args.uiState.autoNextPrompt != null -> viewModel.cancelAutoNextPrompt()
            args.showSponsorSkipNotice -> viewModel.dismissSponsorNotice()
            args.isCommentsPanelVisible && isViewingCommentThread -> viewModel.closeCommentThread()
            args.isCommentsPanelVisible -> presentationState.hideCommentsPanel()
            else -> overlayStateMachine.handleBack(handleOverlayEffect)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlayerEvent.ExitPlayer -> {
                    val exitTrace = latestExitTrace.value
                    exitTrace.start(event.reason)
                    exitTrace.measure("playerView:pre_exit_detach") {
                        clearPlayerViewReference(latestPlayerView.value)
                    }
                    exitTrace.mark("navigateBack:request")
                    latestOnExitPlayer.value()
                }
            }
        }
    }

    LaunchedEffect(playbackState.isPlaybackActive) {
        ScreenUtils.setPlaybackKeepScreenOn(
            context = context,
            keepScreenOn = playbackState.isPlaybackActive,
        )
    }

    DisposableEffect(args.exoPlayer, playerReleaseGuard) {
        val perfListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                AppPerformanceTracker.endSpanOnce(
                    key = "first_playback_open",
                    milestone = "first_playback_first_frame",
                    extras = "bvid=${args.session.bvid} cid=${args.session.cid} aid=${args.session.aid}",
                )
            }
        }
        args.exoPlayer.addListener(perfListener)
        viewModel.attachPlayer(args.exoPlayer)
        onDispose {
            val exitTrace = latestExitTrace.value
            exitTrace.start("screen_dispose")
            try {
                playerReleaseGuard.releaseOnce(
                    finishSession = {
                        exitTrace.measure("keepScreenOn:false") {
                            ScreenUtils.setPlaybackKeepScreenOn(context = context, keepScreenOn = false)
                        }
                        exitTrace.measure("finishPlaybackSession:dispose") {
                            viewModel.finishPlaybackSession(reason = "screen_dispose")
                        }
                    },
                    detachOwner = { player ->
                        exitTrace.measure("viewModel:detachPlayer") {
                            viewModel.detachPlayer(player)
                        }
                    },
                    removeListeners = { player ->
                        exitTrace.measure("listener:remove") {
                            player.removeListener(perfListener)
                        }
                    },
                    detachPlayerView = {
                        exitTrace.measure("playerView:detach") {
                            clearPlayerViewReference(latestPlayerView.value)
                        }
                    },
                    releasePlayer = { player ->
                        com.bbttvv.app.core.player.VolumeBalanceController.unregisterProcessor()
                        exitTrace.measure("exoPlayer:release") {
                            player.release()
                        }
                    },
                )
            } finally {
                exitTrace.complete("screen_dispose")
            }
        }
    }

    PlayerScreenFocusRegistrationEffects(
        playerFocusCoordinator = playerFocusCoordinator,
        focusBindings = focusBindings,
        playerView = args.playerView,
    )

    LaunchedEffect(
        args.uiState.autoNextPrompt?.id,
        args.uiState.resumePrompt,
        args.showSponsorSkipNotice,
        args.overlayUiState.activePanel,
        args.isCommentsPanelVisible,
        isViewingCommentThread,
    ) {
        val prompt = args.uiState.autoNextPrompt ?: return@LaunchedEffect
        val isBlocked = args.uiState.resumePrompt != null ||
            args.showSponsorSkipNotice ||
            args.overlayUiState.activePanel != null ||
            args.isCommentsPanelVisible ||
            isViewingCommentThread
        if (isBlocked) return@LaunchedEffect
        delay(AUTO_NEXT_PROMPT_DELAY_MS)
        if (args.latestUiState.value.autoNextPrompt?.id == prompt.id) {
            viewModel.confirmAutoNextPrompt(prompt.id)
        }
    }

    LaunchedEffect(args.session) {
        AppPerformanceTracker.beginSpanOnce("first_playback_open")
        args.bufferingSpeedMeter.reset()
        overlayStateMachine.resetForNewVideo(handleOverlayEffect)
        latestDebugSnapshotChange.value(PlayerDebugSnapshot())
        presentationState.resetForNewVideo()
        viewModel.loadVideo(
            bvid = args.session.bvid,
            aid = args.session.aid,
            cid = args.session.cid,
            startPositionMs = args.session.startPositionMs,
        )
    }

    LaunchedEffect(args.uiState.info?.bvid, args.uiState.info?.cid) {
        if (args.uiState.info == null) return@LaunchedEffect
        overlayStateMachine.resetForNewVideo(handleOverlayEffect)
        latestDebugSnapshotChange.value(PlayerDebugSnapshot())
        presentationState.resetForNewVideo()
    }

    LaunchedEffect(args.isCommentsPanelVisible, args.uiState.info?.aid) {
        if (args.isCommentsPanelVisible && (args.uiState.info?.aid ?: 0L) > 0L) {
            viewModel.ensureCommentsLoaded()
        }
    }

    LaunchedEffect(args.overlayUiState.activePanel, args.overlayUiState.overlayMode) {
        presentationState.syncOverlayVisibility(args.overlayUiState)
    }

    LaunchedEffect(args.isDebugOverlayVisible, args.exoPlayer) {
        if (!args.isDebugOverlayVisible) return@LaunchedEffect
        while (overlayStateMachine.uiState.showDebugOverlay) {
            latestDebugSnapshotChange.value(
                buildPlayerDebugSnapshot(
                    player = args.exoPlayer,
                    uiState = args.latestUiState.value,
                    playbackState = args.playbackSnapshotProvider(),
                )
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
        snapshotFlow { args.latestUiState.value.statusMessage.orEmpty() }
            .distinctUntilChanged()
            .collectLatest { statusMessage ->
                if (statusMessage.isBlank()) return@collectLatest
                overlayStateMachine.showFullOverlay(
                    focus = PlayerFullControlsFocus.Progress,
                    onEffect = args.latestHandleOverlayEffect.value,
                )
                delay(1800L)
                viewModel.clearStatusMessage()
            }
    }

    LaunchedEffect(args.overlayUiState.activePanel, args.panelOptionsFocusKey) {
        overlayStateMachine.syncPanelOptions(args.panelOptions)
    }

    LaunchedEffect(
        args.overlayUiState.overlayMode,
        args.overlayUiState.fullControlsFocus,
        args.overlayUiState.selectedActionIndex,
        args.overlayUiState.activePanel,
        args.overlayUiState.selectedPanelIndex,
        args.panelOptions.size,
        args.isCommentsPanelVisible,
        isViewingCommentThread,
    ) {
        val focusIntent = when {
            args.overlayUiState.overlayMode != PlayerOverlayMode.FullControls -> {
                PlayerFocusIntent.FocusPlayerSurface
            }

            args.isCommentsPanelVisible -> {
                PlayerFocusIntent.FocusCommentsPanel
            }

            args.overlayUiState.activePanel != null -> {
                PlayerFocusIntent.FocusPanelOption(args.overlayUiState.selectedPanelIndex)
            }

            args.overlayUiState.fullControlsFocus == PlayerFullControlsFocus.Progress -> {
                PlayerFocusIntent.FocusProgress
            }

            else -> {
                PlayerFocusIntent.FocusAction(args.overlayUiState.selectedActionIndex)
            }
        }
        playerFocusCoordinator.requestFocus(focusIntent)
        withFrameNanos { }
        playerFocusCoordinator.drainPendingFocus()
    }

    LaunchedEffect(
        args.overlayUiState.interactionToken,
        args.overlayUiState.overlayMode,
        playbackState.isPlaying,
        args.uiState.isLoading,
        args.uiState.errorMessage,
        args.showSponsorSkipNotice,
        args.overlayUiState.activePanel,
        args.isCommentsPanelVisible,
        args.isDebugOverlayVisible,
    ) {
        val shouldHide = shouldAutoHidePlayerControls(
            overlayUiState = args.overlayUiState,
            playbackState = playbackState,
            isLoading = args.uiState.isLoading,
            errorMessage = args.uiState.errorMessage,
            showSponsorSkipNotice = args.showSponsorSkipNotice,
            isCommentsPanelVisible = args.isCommentsPanelVisible,
            isDebugOverlayVisible = args.isDebugOverlayVisible,
        )
        if (shouldHide) {
            delay(CONTROLS_AUTO_HIDE_MS)
            val latestOverlayState = overlayStateMachine.uiState
            if (
                shouldAutoHidePlayerControls(
                    overlayUiState = latestOverlayState,
                    playbackState = playbackState,
                    isLoading = args.uiState.isLoading,
                    errorMessage = args.uiState.errorMessage,
                    showSponsorSkipNotice = args.showSponsorSkipNotice,
                    isCommentsPanelVisible = args.isCommentsPanelVisible,
                    isDebugOverlayVisible = latestOverlayState.showDebugOverlay,
                )
            ) {
                overlayStateMachine.hideOverlay(onEffect = handleOverlayEffect)
            }
        }
    }

    LaunchedEffect(
        args.overlayUiState.simpleSeekState,
        args.overlayUiState.isScrubbing,
        args.overlayUiState.overlayMode,
    ) {
        if (
            args.overlayUiState.simpleSeekState != null &&
            args.overlayUiState.overlayMode == PlayerOverlayMode.FullControls &&
            !args.overlayUiState.isScrubbing
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

    LaunchedEffect(args.overlayUiState.isScrubbing, args.overlayUiState.scrubDirection) {
        if (!args.overlayUiState.isScrubbing || args.overlayUiState.scrubDirection == 0) return@LaunchedEffect
        while (overlayStateMachine.uiState.isScrubbing) {
            overlayStateMachine.tickScrubPreview(
                playbackState = args.playbackSnapshotProvider(),
                onEffect = handleOverlayEffect,
            )
            delay(HOLD_SCRUB_TICK_MS)
        }
    }

    LaunchedEffect(args.overlayUiState.pendingSeekKeyCode, args.overlayUiState.pendingSeekDirection) {
        if (
            args.overlayUiState.pendingSeekDirection == 0 ||
            args.overlayUiState.pendingSeekKeyCode == android.view.KeyEvent.KEYCODE_UNKNOWN
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
                playbackState = args.playbackSnapshotProvider(),
                pauseForSeekScrub = viewModel::pauseForSeekScrub,
                onEffect = handleOverlayEffect,
            )
        }
    }
}

@Composable
private fun PlayerScreenFocusRegistrationEffects(
    playerFocusCoordinator: PlayerFocusCoordinator,
    focusBindings: PlayerScreenFocusBindings,
    playerView: PlayerView?,
) {
    DisposableEffect(playerFocusCoordinator, playerView) {
        val registration = playerView?.let {
            playerFocusCoordinator.registerPlayerSurfaceTarget(playerViewFocusTarget(it))
        }
        onDispose {
            registration?.unregister()
        }
    }

    DisposableEffect(playerFocusCoordinator, focusBindings.progressFocusRequester) {
        val registration = playerFocusCoordinator.registerProgressTarget(
            requesterFocusTarget(focusBindings.progressFocusRequester)
        )
        onDispose {
            registration.unregister()
        }
    }

    DisposableEffect(playerFocusCoordinator, focusBindings.actionFocusRequesters) {
        val registrations = focusBindings.actionFocusRequesters.mapIndexed { index, requester ->
            playerFocusCoordinator.registerActionTarget(
                index = index,
                target = requesterFocusTarget(requester),
            )
        }
        onDispose {
            registrations.forEach(PlayerFocusTargetRegistration::unregister)
        }
    }

    DisposableEffect(playerFocusCoordinator, focusBindings.panelFocusRequesters) {
        val registrations = focusBindings.panelFocusRequesters.mapIndexed { index, requester ->
            playerFocusCoordinator.registerPanelOptionTarget(
                index = index,
                target = requesterFocusTarget(requester),
            )
        }
        onDispose {
            registrations.forEach(PlayerFocusTargetRegistration::unregister)
        }
    }

    DisposableEffect(playerFocusCoordinator, focusBindings.commentsPanelPrimaryFocusRequester) {
        val registration = playerFocusCoordinator.registerCommentsPanelTarget(
            requesterFocusTarget(focusBindings.commentsPanelPrimaryFocusRequester)
        )
        onDispose {
            registration.unregister()
        }
    }
}

private fun playerViewFocusTarget(playerView: PlayerView): PlayerFocusTarget {
    return object : PlayerFocusTarget {
        override fun tryRequestFocus(): Boolean {
            return playerView.requestFocus()
        }
    }
}

private fun requesterFocusTarget(requester: FocusRequester): PlayerFocusTarget {
    return object : PlayerFocusTarget {
        override fun tryRequestFocus(): Boolean {
            return runCatching {
                requester.requestFocus()
            }.getOrDefault(false)
        }
    }
}

internal fun shouldAutoHidePlayerControls(
    overlayUiState: PlayerOverlayUiState,
    playbackState: PlayerPlaybackState,
    isLoading: Boolean,
    errorMessage: String?,
    showSponsorSkipNotice: Boolean,
    isCommentsPanelVisible: Boolean,
    isDebugOverlayVisible: Boolean,
): Boolean {
    return overlayUiState.overlayMode == PlayerOverlayMode.FullControls &&
        overlayUiState.activePanel == null &&
        !isCommentsPanelVisible &&
        !isDebugOverlayVisible &&
        playbackState.isPlaying &&
        !isLoading &&
        errorMessage.isNullOrBlank() &&
        !showSponsorSkipNotice
}
