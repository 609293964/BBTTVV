package com.bbttvv.app.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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

/**
 * 创建配置好的 ExoPlayer 实例
 *
 * 配置包括：OkHttp DataSource、WiFi/移动网络不同缓冲策略、
 * AppRenderersFactory 解码器 fallback、SeekParameters.CLOSEST_SYNC、
 * VolumeBalanceAudioProcessor 音量均衡。
 *
 * @param context Android 上下文
 * @param transferListener 数据传输监听器
 * @param audioBalanceLevel 音量均衡级别
 * @param audioPassthrough 是否音频直通
 * @return 配置好的 ExoPlayer 实例
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun createConfiguredPlayer(
    context: Context,
    transferListener: TransferListener? = null,
    audioBalanceLevel: AudioBalanceLevel = VolumeBalanceController.getLevel(),
    audioPassthrough: Boolean = PlayerSettingsCache.getAudioPassthrough(),
): ExoPlayer {
    val appContext = context.applicationContext
    val headers = mapOf(
        "Referer" to "https://www.bilibili.com",
        "User-Agent" to resolveAppUserAgent(appContext)
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
        isOnWifi = NetworkUtils.isWifi(appContext)
    )

    val volumeBalanceProcessor = VolumeBalanceAudioProcessor(level = audioBalanceLevel)
    VolumeBalanceController.registerProcessor(volumeBalanceProcessor)

    val renderersFactory = AppRenderersFactory(appContext, volumeBalanceProcessor, audioPassthrough)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)

    val trackSelector = if (audioPassthrough) {
        DefaultTrackSelector(appContext).apply {
            setParameters(
                buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            )
        }
    } else {
        DefaultTrackSelector(appContext)
    }

    return ExoPlayer.Builder(appContext)
        .setRenderersFactory(renderersFactory)
        .setTrackSelector(trackSelector)
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
            volume = PlayerSettingsCache.getVolumeCalibrationScale()
            if (!audioPassthrough) {
                playbackParameters = PlaybackParameters(PlayerSettingsCache.getPreferredPlaybackSpeed())
            }
            playWhenReady = true
        }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class AppRenderersFactory(
    context: Context,
    private val volumeBalanceProcessor: VolumeBalanceAudioProcessor,
    private val audioPassthrough: Boolean,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        val audioProcessors: Array<AudioProcessor> = if (audioPassthrough) {
            emptyArray()
        } else {
            arrayOf(StereoBalanceAudioProcessor(), volumeBalanceProcessor)
        }
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
            .setAudioProcessors(audioProcessors)
            .build()
    }
}
