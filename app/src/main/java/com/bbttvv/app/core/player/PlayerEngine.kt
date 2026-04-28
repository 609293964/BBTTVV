package com.bbttvv.app.core.player

import androidx.media3.exoplayer.ExoPlayer

enum class PlayerEngineKind {
    EXO_PLAYER,
}

interface PlayerEngine {
    val kind: PlayerEngineKind
    val currentPosition: Long
    val duration: Long
    val bufferedPosition: Long
    val isPlaying: Boolean
    var playWhenReady: Boolean
    var volume: Float

    fun seekTo(positionMs: Long)
    fun prepare()
    fun play()
    fun pause()
    fun stop()
    fun release()
    fun asExoPlayerOrNull(): ExoPlayer? = null
}

internal class ExoPlayerEngine(
    private val player: ExoPlayer,
) : PlayerEngine {
    override val kind: PlayerEngineKind = PlayerEngineKind.EXO_PLAYER
    override val currentPosition: Long
        get() = player.currentPosition
    override val duration: Long
        get() = player.duration
    override val bufferedPosition: Long
        get() = player.bufferedPosition
    override val isPlaying: Boolean
        get() = player.isPlaying
    override var playWhenReady: Boolean
        get() = player.playWhenReady
        set(value) {
            player.playWhenReady = value
        }
    override var volume: Float
        get() = player.volume
        set(value) {
            player.volume = value
        }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun prepare() {
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
    }

    override fun release() {
        player.release()
    }

    override fun asExoPlayerOrNull(): ExoPlayer = player
}
