package com.bbttvv.app.core.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.PlayerView
import com.bbttvv.app.core.util.PlaybackKeepScreenOnOwner
import com.bbttvv.app.core.util.ScreenUtils

@Composable
internal fun PlaybackKeepScreenOnEffect(
    isPlaybackActive: Boolean,
    playerView: PlayerView?,
) {
    val context = LocalContext.current
    val owner = remember { PlaybackKeepScreenOnOwner() }

    DisposableEffect(context, owner, isPlaybackActive) {
        ScreenUtils.setPlaybackKeepScreenOn(
            context = context,
            owner = owner,
            keepScreenOn = isPlaybackActive,
        )
        onDispose {
            ScreenUtils.releasePlaybackKeepScreenOn(
                context = context,
                owner = owner,
            )
        }
    }

    DisposableEffect(playerView, isPlaybackActive) {
        playerView?.keepScreenOn = isPlaybackActive
        onDispose {
            playerView?.keepScreenOn = false
        }
    }
}
