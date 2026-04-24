package com.bbttvv.app.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

internal class PlayerVisualEffectsState internal constructor()

@Composable
internal fun rememberPlayerVisualEffectsState(): PlayerVisualEffectsState {
    return remember { PlayerVisualEffectsState() }
}

@Composable
internal fun Modifier.playerBackdropSource(
    state: PlayerVisualEffectsState
): Modifier {
    return this
}

@Composable
internal fun Modifier.playerPanelSurfaceEffect(
    state: PlayerVisualEffectsState
): Modifier {
    return this
}
