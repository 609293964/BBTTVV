package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.DashAudio
import com.bbttvv.app.feature.video.usecase.PlaybackSource
import java.net.URI

internal data class PlaybackCdnFallbackState(
    val selectedVideoUrl: String = "",
    val selectedAudioUrl: String? = null,
    val fallbackVideoUrl: String? = null,
    val fallbackAudioUrl: String? = null,
    val regionLabel: String? = null,
    val fallbackConsumed: Boolean = false
) {
    val usesCdnRewrite: Boolean
        get() = !fallbackConsumed &&
            !fallbackVideoUrl.isNullOrBlank() &&
            (selectedVideoUrl != fallbackVideoUrl || selectedAudioUrl != fallbackAudioUrl)

    fun markFallbackConsumed(): PlaybackCdnFallbackState = copy(fallbackConsumed = true)

    companion object {
        val Inactive = PlaybackCdnFallbackState()
    }
}

internal fun buildPlaybackCdnFallbackState(source: PlaybackSource): PlaybackCdnFallbackState {
    return buildPlaybackCdnFallbackState(
        selectedVideoUrl = source.videoUrl,
        selectedAudioUrl = source.audioUrl,
        originalVideoUrl = source.fallbackVideoUrl.orEmpty(),
        originalAudioUrl = source.fallbackAudioUrl,
        regionLabel = source.playbackCdnRegionLabel
    )
}

internal fun buildPlaybackCdnFallbackState(
    selectedVideoUrl: String,
    selectedAudioUrl: String?,
    originalVideoUrl: String,
    originalAudioUrl: String?,
    regionLabel: String?,
    audioFallbackUrl: String? = null
): PlaybackCdnFallbackState {
    val fallbackAudioUrl = when {
        selectedAudioUrl != originalAudioUrl -> originalAudioUrl
        !audioFallbackUrl.isNullOrBlank() -> audioFallbackUrl
        else -> originalAudioUrl
    }
    return PlaybackCdnFallbackState(
        selectedVideoUrl = selectedVideoUrl,
        selectedAudioUrl = selectedAudioUrl,
        fallbackVideoUrl = originalVideoUrl.takeIf { it.isNotBlank() },
        fallbackAudioUrl = fallbackAudioUrl,
        regionLabel = regionLabel
    )
}

internal fun buildPlaybackAudioUrlCandidates(
    audioUrl: String?,
    cachedDashAudios: List<DashAudio>
): List<String> {
    val selectedAudio = audioUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { selectedUrl ->
            cachedDashAudios.firstOrNull { audio ->
                audio.getValidUrl() == selectedUrl ||
                    audio.backupUrl.orEmpty().any { backupUrl -> backupUrl == selectedUrl }
            }
        }

    return buildList {
        audioUrl?.takeIf { it.isNotBlank() }?.let(::add)
        selectedAudio
            ?.backupUrl
            .orEmpty()
            .filter { it.isNotBlank() }
            .let(::addAll)
    }.distinct()
}

internal fun shouldFallbackFromCdnRewrite(
    state: PlaybackCdnFallbackState,
    playbackReady: Boolean
): Boolean {
    return state.usesCdnRewrite && !playbackReady
}

internal fun shouldFallbackFromCdnRewrite(
    state: PlaybackCdnFallbackState,
    playbackReady: Boolean,
    expectedAudioTrack: Boolean,
    hasSelectedAudioTrack: Boolean,
    audioRendererError: Boolean
): Boolean {
    if (!state.usesCdnRewrite) return false
    if (!playbackReady) return true
    if (audioRendererError) return true
    return expectedAudioTrack && !hasSelectedAudioTrack
}

internal fun hostForPlaybackLog(url: String?): String {
    val value = url?.takeIf { it.isNotBlank() } ?: return ""
    return runCatching { URI(value).host.orEmpty() }.getOrDefault("")
}
