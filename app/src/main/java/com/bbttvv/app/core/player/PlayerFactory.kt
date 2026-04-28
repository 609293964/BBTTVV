package com.bbttvv.app.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.resolveAppUserAgent
import com.bbttvv.app.core.store.PlayerSettingsCache
import com.bbttvv.app.core.util.NetworkUtils

private data class PlayerBufferPolicy(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)

private fun resolvePlayerBufferPolicy(isOnWifi: Boolean): PlayerBufferPolicy {
    return if (isOnWifi) {
        PlayerBufferPolicy(
            minBufferMs = 10_000,
            maxBufferMs = 40_000,
            bufferForPlaybackMs = 900,
            bufferForPlaybackAfterRebufferMs = 1_800
        )
    } else {
        PlayerBufferPolicy(
            minBufferMs = 15_000,
            maxBufferMs = 50_000,
            bufferForPlaybackMs = 1_600,
            bufferForPlaybackAfterRebufferMs = 3_000
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun createConfiguredPlayer(
    context: Context,
    transferListener: TransferListener? = null
): ExoPlayer {
    val headers = mapOf(
        "Referer" to "https://www.bilibili.com",
        "User-Agent" to resolveAppUserAgent(context.applicationContext)
    )
    val dataSourceFactory = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
        .setDefaultRequestProperties(headers)
        .apply {
            if (transferListener != null) {
                setTransferListener(transferListener)
            }
        }

    val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    val bufferPolicy = resolvePlayerBufferPolicy(
        isOnWifi = NetworkUtils.isWifi(context)
    )

    return ExoPlayer.Builder(context)
        .setRenderersFactory(
            AppRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)
        )
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    bufferPolicy.minBufferMs,
                    bufferPolicy.maxBufferMs,
                    bufferPolicy.bufferForPlaybackMs,
                    bufferPolicy.bufferForPlaybackAfterRebufferMs
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .setSeekParameters(SeekParameters.CLOSEST_SYNC)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply {
            volume = 1.0f
            playbackParameters = PlaybackParameters(PlayerSettingsCache.getPreferredPlaybackSpeed())
            playWhenReady = true
        }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class AppRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(StereoBalanceAudioProcessor()))
            .build()
    }
}
