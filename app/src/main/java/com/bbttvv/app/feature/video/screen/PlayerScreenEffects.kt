package com.bbttvv.app.feature.video.screen

import android.content.Context
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bbttvv.app.core.performance.AppPerformanceTracker
import com.bbttvv.app.core.player.BufferingSpeedMeter
import com.bbttvv.app.core.util.ScreenUtils
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentsUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private const val CONTROLS_AUTO_HIDE_MS = 3500L
private const val SIMPLE_SEEK_HIDE_MS = 1200L

internal data class PlayerScreenSession(
    val bvid: String,
    val cid: Long,
    val aid: Long,
    val startPositionMs: Long,
)

internal data class PlayerScreenEffectArgs(
    val session: PlayerScreenSession,
    val uiState: PlayerUiState,
    val commentsUiState: PlayerCommentsUiState,
    val playbackState: PlayerPlaybackState,
    val overlayUiState: PlayerOverlayUiState,
    val panelOptions: List<PanelOption>,
    val panelOptionsFocusKey: String,
    val isCommentsPanelVisible: Boolean,
    val showSponsorSkipNotice: Boolean,
    val playerView: PlayerView?,
    val exoPlayer: ExoPlayer,
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
    onDebugSnapshotChange: (PlayerDebugSnapshot) -> Unit,
    args: PlayerScreenEffectArgs,
) {
    val latestDebugSnapshotChange = rememberUpdatedState(onDebugSnapshotChange)

    BackHandler {
        when {
            args.uiState.resumePrompt != null -> viewModel.dismissResumePlaybackPrompt()
            args.isCommentsPanelVisible && args.commentsUiState.isViewingThread -> viewModel.closeCommentThread()
            args.isCommentsPanelVisible -> presentationState.hideCommentsPanel()
            else -> overlayStateMachine.handleBack(handleOverlayEffect)
        }
    }

    LaunchedEffect(args.playbackState.isPlaybackActive) {
        ScreenUtils.setPlaybackKeepScreenOn(
            context = context,
            keepScreenOn = args.playbackState.isPlaybackActive,
        )
    }

    DisposableEffect(args.exoPlayer) {
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
            ScreenUtils.setPlaybackKeepScreenOn(context = context, keepScreenOn = false)
            viewModel.finishPlaybackSession(reason = "screen_dispose")
            args.exoPlayer.removeListener(perfListener)
            args.exoPlayer.release()
        }
    }

    PlayerScreenFocusRegistrationEffects(
        playerFocusCoordinator = playerFocusCoordinator,
        focusBindings = focusBindings,
        playerView = args.playerView,
    )

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

    LaunchedEffect(args.isCommentsPanelVisible, args.uiState.info?.aid) {
        if (args.isCommentsPanelVisible && (args.uiState.info?.aid ?: 0L) > 0L) {
            viewModel.ensureCommentsLoaded()
        }
    }

    LaunchedEffect(args.overlayUiState.activePanel, args.overlayUiState.overlayMode) {
        presentationState.syncOverlayVisibility(args.overlayUiState)
    }

    LaunchedEffect(args.overlayUiState.showDebugOverlay, args.exoPlayer) {
        if (!args.overlayUiState.showDebugOverlay) return@LaunchedEffect
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

    LaunchedEffect(args.playbackState.isPlaying) {
        if (!args.playbackState.isPlaying) {
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
        args.commentsUiState.isViewingThread,
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
        args.playbackState.isPlaying,
        args.uiState.isLoading,
        args.uiState.errorMessage,
        args.showSponsorSkipNotice,
        args.overlayUiState.activePanel,
        args.isCommentsPanelVisible,
    ) {
        val shouldHide = args.overlayUiState.overlayMode == PlayerOverlayMode.FullControls &&
            args.overlayUiState.activePanel == null &&
            !args.isCommentsPanelVisible &&
            args.playbackState.isPlaying &&
            !args.uiState.isLoading &&
            args.uiState.errorMessage.isNullOrBlank() &&
            !args.showSponsorSkipNotice
        if (shouldHide) {
            delay(CONTROLS_AUTO_HIDE_MS)
            val latestOverlayState = overlayStateMachine.uiState
            if (args.playbackState.isPlaying && latestOverlayState.activePanel == null && !args.isCommentsPanelVisible) {
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
