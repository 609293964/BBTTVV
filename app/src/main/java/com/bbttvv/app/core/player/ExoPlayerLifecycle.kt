package com.bbttvv.app.core.player

import android.view.View
import androidx.annotation.MainThread
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bbttvv.app.core.util.Logger

private const val EXO_PLAYER_LIFECYCLE_TAG = "ExoPlayerLifecycle"

internal class ExoPlayerReleaseGuard(
    private val player: ExoPlayer
) {
    private var isReleased = false

    @MainThread
    fun releaseOnce(
        finishSession: () -> Unit = {},
        detachOwner: (ExoPlayer) -> Unit = {},
        removeListeners: (ExoPlayer) -> Unit = {},
        detachPlayerView: (ExoPlayer) -> Unit = {},
        releasePlayer: (ExoPlayer) -> Unit = { it.release() },
    ) {
        if (isReleased) return
        isReleased = true
        runReleaseStep("finish session", finishSession)
        runReleaseStep("detach player owner") { detachOwner(player) }
        runReleaseStep("remove player listeners") { removeListeners(player) }
        runReleaseStep("detach PlayerView") { detachPlayerView(player) }
        runReleaseStep("release ExoPlayer") { releasePlayer(player) }
    }

    private inline fun runReleaseStep(
        stepName: String,
        block: () -> Unit
    ) {
        runCatching(block).onFailure { error ->
            Logger.e(EXO_PLAYER_LIFECYCLE_TAG, "Failed to $stepName", error)
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun clearPlayerViewReference(playerView: PlayerView?) {
    playerView ?: return
    playerView.visibility = View.INVISIBLE
    playerView.setKeepContentOnPlayerReset(false)
    playerView.setOnKeyListener(null)
    playerView.keepScreenOn = false
    playerView.player = null
}
