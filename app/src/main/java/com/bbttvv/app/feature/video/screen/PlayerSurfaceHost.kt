package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bbttvv.app.feature.video.danmaku.DanmakuConfig
import com.bbttvv.app.feature.video.danmaku.DanmakuOverlay
import com.bbttvv.app.feature.video.danmaku.DanmakuRenderPayload
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState

@Composable
internal fun PlayerSurfaceHost(
    exoPlayer: ExoPlayer,
    keepScreenOn: Boolean,
    overlayMode: PlayerOverlayMode,
    onHiddenOverlayKey: (KeyEvent) -> Boolean,
    onViewAvailable: (PlayerView) -> Unit,
    onPlayerSurfaceFocusNeeded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSurface(
        exoPlayer = exoPlayer,
        keepScreenOn = keepScreenOn,
        overlayMode = overlayMode,
        onHiddenOverlayKey = onHiddenOverlayKey,
        onViewAvailable = onViewAvailable,
        onPlayerSurfaceFocusNeeded = onPlayerSurfaceFocusNeeded,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
internal fun PlayerDanmakuOverlayHost(
    payload: DanmakuRenderPayload?,
    isEnabled: Boolean,
    playbackState: State<PlayerPlaybackState>,
    config: DanmakuConfig,
    modifier: Modifier = Modifier,
) {
    if (payload == null) return

    val currentPlaybackState = playbackState.value
    DanmakuOverlay(
        payload = payload,
        isEnabled = isEnabled,
        isPlaying = currentPlaybackState.isPlaying,
        playbackPositionMs = currentPlaybackState.positionMs,
        config = config,
        modifier = modifier.fillMaxSize(),
    )
}
