package com.android.purebilibili.feature.video.playback.loader

import com.android.purebilibili.feature.video.usecase.VideoLoadResult
import com.android.purebilibili.feature.video.usecase.VideoPlaybackUseCase

internal data class PlaybackLoadConfig(
    val defaultQuality: Int,
    val audioQualityPreference: Int,
    val videoCodecPreference: String,
    val videoSecondCodecPreference: String,
    val playWhenReady: Boolean,
    val isAv1Supported: Boolean,
    val isHdrSupported: Boolean,
    val isDolbyVisionSupported: Boolean
)

internal class PlaybackLoader(
    private val loadVideo: suspend (PlaybackRequest, PlaybackLoadConfig) -> VideoLoadResult
) {

    suspend fun load(
        request: PlaybackRequest,
        cachedPositionMs: Long,
        config: PlaybackLoadConfig
    ): PlaybackLoadResult {
        val result = loadVideo(request, config)
        return PlaybackLoadResult.from(
            request = request,
            cachedPositionMs = cachedPositionMs,
            result = result
        )
    }

    companion object {
        fun from(useCase: VideoPlaybackUseCase): PlaybackLoader {
            return PlaybackLoader { request, config ->
                useCase.loadVideo(
                    bvid = request.bvid,
                    aid = request.aid,
                    cid = request.cid,
                    defaultQuality = config.defaultQuality,
                    audioQualityPreference = config.audioQualityPreference,
                    videoCodecPreference = config.videoCodecPreference,
                    videoSecondCodecPreference = config.videoSecondCodecPreference,
                    audioLang = request.audioLang,
                    playWhenReady = config.playWhenReady,
                    isAv1SupportedOverride = config.isAv1Supported,
                    isHdrSupportedOverride = config.isHdrSupported,
                    isDolbyVisionSupportedOverride = config.isDolbyVisionSupported
                )
            }
        }
    }
}
