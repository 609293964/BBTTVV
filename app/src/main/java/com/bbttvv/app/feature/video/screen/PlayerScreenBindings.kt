package com.bbttvv.app.feature.video.screen

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope

internal data class PlayerScreenFocusBindings(
    val progressFocusRequester: FocusRequester,
    val actionFocusRequesters: List<FocusRequester>,
    val panelFocusRequesters: List<FocusRequester>,
    val commentsPanelPrimaryFocusRequester: FocusRequester,
)

@Composable
internal fun rememberPlayerScreenFocusBindings(
    actionsCount: Int,
    panelOptionsCount: Int,
): PlayerScreenFocusBindings {
    val progressFocusRequester = remember { FocusRequester() }
    val actionFocusRequesters = remember(actionsCount) {
        List(actionsCount) { FocusRequester() }
    }
    val panelFocusRequesters = remember(panelOptionsCount) {
        List(panelOptionsCount) { FocusRequester() }
    }
    val commentsPanelPrimaryFocusRequester = remember { FocusRequester() }

    return PlayerScreenFocusBindings(
        progressFocusRequester = progressFocusRequester,
        actionFocusRequesters = actionFocusRequesters,
        panelFocusRequesters = panelFocusRequesters,
        commentsPanelPrimaryFocusRequester = commentsPanelPrimaryFocusRequester,
    )
}

@Composable
internal fun rememberPlayerOverlayEffectHandler(
    presentationState: PlayerOverlayPresentationState,
    viewModel: PlayerViewModel,
    context: Context,
    scope: CoroutineScope,
    onExitPlayer: () -> Unit,
): (PlayerOverlayEffect) -> Unit {
    return remember(
        presentationState,
        viewModel,
        context,
        scope,
        onExitPlayer,
    ) {
        { effect ->
            handlePlayerOverlayEffect(
                effect = effect,
                presentationState = presentationState,
                viewModel = viewModel,
                context = context,
                scope = scope,
                onExitPlayer = onExitPlayer,
            )
        }
    }
}

@Composable
internal fun rememberPlayerOverlayKeyHandler(
    overlayStateMachine: PlayerOverlayStateMachine,
    playbackSnapshotProvider: () -> PlayerPlaybackState,
    actions: List<PlayerAction>,
    panelOptions: List<PanelOption>,
    pauseForSeekScrub: () -> Boolean,
    onEffect: (PlayerOverlayEffect) -> Unit,
): (KeyEvent) -> Boolean {
    return remember(
        overlayStateMachine,
        playbackSnapshotProvider,
        actions,
        panelOptions,
        pauseForSeekScrub,
        onEffect,
    ) {
        { event ->
            overlayStateMachine.handleKeyEvent(
                event = event,
                playbackState = playbackSnapshotProvider(),
                actions = actions,
                panelOptions = panelOptions,
                pauseForSeekScrub = pauseForSeekScrub,
                onEffect = onEffect,
            )
        }
    }
}
