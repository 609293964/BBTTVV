package com.android.purebilibili.feature.video.playback.policy

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.feature.video.viewmodel.normalizeCodecFamilyKey

data class AdaptiveDashTrackSet(
    val videoTracks: List<DashVideo>,
    val audioTracks: List<DashAudio>
)

fun buildAdaptiveDashTrackSet(
    dash: Dash,
    mode: PlaybackQualityMode,
    autoQualityCap: Int,
    preferredAudioQuality: Int,
    preferredVideoCodec: String,
    secondaryVideoCodec: String,
    isHevcSupported: Boolean,
    isAv1Supported: Boolean
): AdaptiveDashTrackSet {
    val preferredCodec = normalizeCodecFamilyKey(preferredVideoCodec)
    val secondaryCodec = normalizeCodecFamilyKey(secondaryVideoCodec)

    val supportedVideos = dash.video
        .filter { it.getValidUrl().isNotBlank() }
        .filter { video ->
            when (normalizeCodecFamilyKey(video.codecs)) {
                null -> true
                "avc1" -> true
                "hev1" -> isHevcSupported
                "av01" -> isAv1Supported
                else -> false
            }
        }

    val candidateVideos = when (mode) {
        PlaybackQualityMode.AUTO -> {
            val capped = supportedVideos.filter { it.id <= autoQualityCap }
            if (capped.isNotEmpty()) capped else supportedVideos
        }
        is PlaybackQualityMode.LOCKED -> supportedVideos.filter { it.id == mode.qualityId }
    }

    val sortedVideos = candidateVideos.sortedWith(
        compareByDescending<DashVideo> { it.id }
            .thenByDescending { scoreCodecPreference(it.codecs, preferredCodec, secondaryCodec) }
            .thenByDescending { it.bandwidth }
    )

    val sortedAudios = dash.audio
        .orEmpty()
        .filter { it.getValidUrl().isNotBlank() }
        .sortedWith(
            compareByDescending<DashAudio> {
                if (preferredAudioQuality > 0 && it.id == preferredAudioQuality) 1 else 0
            }.thenByDescending { it.bandwidth }
        )

    return AdaptiveDashTrackSet(
        videoTracks = sortedVideos,
        audioTracks = sortedAudios
    )
}

private fun scoreCodecPreference(
    codecs: String,
    preferredCodec: String?,
    secondaryCodec: String?
): Int {
    return when (normalizeCodecFamilyKey(codecs)) {
        preferredCodec -> 2
        secondaryCodec -> 1
        else -> 0
    }
}
