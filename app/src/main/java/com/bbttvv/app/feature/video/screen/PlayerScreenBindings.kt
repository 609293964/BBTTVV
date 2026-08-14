package com.bbttvv.app.feature.video.screen

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope

internal fun shouldRoutePlayerKeyToPreviewHandler(event: KeyEvent): Boolean {
    return shouldRoutePlayerKeyCodeToPreviewHandler(event.keyCode)
}

internal fun shouldRoutePlayerKeyCodeToPreviewHandler(keyCode: Int): Boolean {
    return keyCode != KeyEvent.KEYCODE_BACK
}

/**
 * Keeps the tail of a key press inside a modal focus domain after that modal closes on key down.
 */
internal class PlayerModalKeyReleaseGuard {
    private var pendingKeyCode = KeyEvent.KEYCODE_UNKNOWN

    fun recordConsumedModalEvent(
        action: Int,
        keyCode: Int,
        repeatCount: Int,
    ) {
        when (action) {
            KeyEvent.ACTION_DOWN -> if (repeatCount == 0) pendingKeyCode = keyCode
            KeyEvent.ACTION_UP -> if (keyCode == pendingKeyCode) clear()
        }
    }

    fun consumeTrailingEvent(
        action: Int,
        keyCode: Int,
        repeatCount: Int,
    ): Boolean {
        if (keyCode != pendingKeyCode) return false
        if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
            clear()
            return false
        }
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false
        if (action == KeyEvent.ACTION_UP) clear()
        return true
    }

    private fun clear() {
        pendingKeyCode = KeyEvent.KEYCODE_UNKNOWN
    }
}

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
    exitTrace: PlayerExitTrace,
    onPrepareExitPlayer: () -> Unit,
    onExitPlayer: () -> Unit,
): (PlayerOverlayEffect) -> Unit {
    return remember(
        presentationState,
        viewModel,
        context,
        scope,
        exitTrace,
        onPrepareExitPlayer,
        onExitPlayer,
    ) {
        { effect ->
            handlePlayerOverlayEffect(
                effect = effect,
                presentationState = presentationState,
                viewModel = viewModel,
                context = context,
                scope = scope,
                exitTrace = exitTrace,
                onPrepareExitPlayer = onPrepareExitPlayer,
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
