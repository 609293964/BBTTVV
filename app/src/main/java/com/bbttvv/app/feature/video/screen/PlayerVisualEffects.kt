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
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.playerPanelSurfaceEffect(
    state: PlayerVisualEffectsState
): Modifier {
    // TV 播放热路径只保留调用方提供的静态半透明背景。
    // RenderEffect 会创建额外离屏图层，并与视频 Surface、弹幕共同争用 GPU。
    return this
}
