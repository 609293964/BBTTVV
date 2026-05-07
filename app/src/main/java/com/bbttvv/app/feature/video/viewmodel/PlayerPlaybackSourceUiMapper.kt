package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.core.util.MediaUtils
import com.bbttvv.app.data.model.AudioQuality
import com.bbttvv.app.data.model.response.DashAudio
import com.bbttvv.app.data.model.response.DashVideo
import com.bbttvv.app.feature.video.usecase.PlaybackQualityInfo
import com.bbttvv.app.feature.video.usecase.PlaybackSource
import java.net.URI

internal fun PlayerUiState.withPlaybackSource(
    source: PlaybackSource,
    isLoading: Boolean,
    statusMessage: String?
): PlayerUiState {
    return copy(
        isLoading = isLoading,
        selectedQuality = source.actualQuality,
        qualityOptions = source.qualityOptions.map(::toQualityOption),
        audioOptions = buildAudioOptions(source),
        videoCodecOptions = buildVideoCodecOptions(source),
        selectedVideoCodecId = source.selectedVideoCodecId,
        selectedAudioQualityId = source.selectedAudioQualityId,
        selectedAudioCodecId = source.selectedAudioCodecId,
        statusMessage = statusMessage,
        playbackBadges = buildPlaybackBadges(source),
        capabilityHints = source.capabilityHints,
        selectedVideoCodecLabel = source.selectedVideoCodec,
        selectedAudioCodecLabel = source.selectedAudioCodec,
        activeDynamicRangeLabel = source.activeDynamicRangeLabel,
        videoCdnHost = source.videoUrl.toHostLabel(),
        audioCdnHost = source.audioUrl.orEmpty().toHostLabel(),
        selectedVideoWidth = source.selectedVideoWidth,
        selectedVideoHeight = source.selectedVideoHeight,
        selectedVideoFrameRate = source.selectedVideoFrameRate,
        selectedVideoBandwidth = source.selectedVideoBandwidth,
        selectedAudioBandwidth = source.selectedAudioBandwidth,
        selectedQualityLabel = source.qualityOptions
            .firstOrNull { it.id == source.actualQuality }
            ?.label
            ?: source.actualQuality.toString()
    )
}

private fun toQualityOption(option: PlaybackQualityInfo): QualityOption {
    return QualityOption(
        id = option.id,
        label = option.label,
        isSupported = option.isSupported,
        unsupportedReason = option.unsupportedReason
    )
}

private fun buildAudioOptions(source: PlaybackSource): List<PlayerOption> {
    return source.dashAudios
        .filter { it.getValidUrl().isNotBlank() }
        .distinctBy { it.id }
        .sortedWith(compareByDescending<DashAudio> { it.id }.thenByDescending { it.bandwidth })
        .map { audio ->
            PlayerOption(
                key = audio.id.toString(),
                label = resolveAudioLabel(audio),
                subtitle = buildAudioSubtitle(audio),
                isSelected = audio.id == source.selectedAudioQualityId
            )
        }
}

private fun buildVideoCodecOptions(source: PlaybackSource): List<PlayerOption> {
    val hevcSupported = MediaUtils.isHevcSupported()
    val av1Supported = MediaUtils.isAv1Supported()
    return source.dashVideos
        .filter { it.id == source.actualQuality && it.getValidUrl().isNotBlank() }
        .distinctBy { it.videoCodecSelectionKey() }
        .map { video ->
            val supported = video.isSupportedByDevice(
                isHevcSupported = hevcSupported,
                isAv1Supported = av1Supported
            )
            PlayerOption(
                key = video.videoCodecSelectionKey().toString(),
                label = resolveCodecLabel(video.codecs, video.codecid).ifBlank { "未知编码" },
                subtitle = buildVideoCodecSubtitle(video),
                isSelected = video.videoCodecSelectionKey() == source.selectedVideoCodecId,
                isEnabled = supported,
                disabledReason = if (supported) null else "当前设备暂不支持该编码"
            )
        }
}

private fun buildPlaybackBadges(source: PlaybackSource): List<PlaybackBadge> {
    return buildList {
        source.activeDynamicRangeLabel?.let { add(PlaybackBadge(it)) }
        if (source.hasDolbyVisionTrack && source.activeDynamicRangeLabel != "杜比视界") {
            add(PlaybackBadge("杜比视界可用", isActive = false))
        }
        if (source.hasHdrTrack && source.activeDynamicRangeLabel != "HDR 真彩") {
            add(PlaybackBadge("HDR 可用", isActive = false))
        }
        if (source.hasDolbyAudioTrack) {
            val isDolbyActive = source.selectedAudioCodec.contains("杜比", ignoreCase = true) ||
                source.selectedAudioCodec.contains("Dolby", ignoreCase = true)
            add(PlaybackBadge("杜比音频", isActive = isDolbyActive))
        }
        source.selectedVideoCodec.takeIf { it.isNotBlank() }?.let {
            add(PlaybackBadge(it))
        }
        source.selectedAudioCodec.takeIf { it.isNotBlank() }?.let {
            add(PlaybackBadge(it))
        }
    }
}

internal fun resolveAudioLabel(audio: DashAudio): String {
    return AudioQuality.fromCode(audio.id)?.description
        ?: audio.bandwidth.takeIf { it > 0 }?.let { "${it / 1000}K" }
        ?: "音频 ${audio.id}"
}

private fun buildAudioSubtitle(audio: DashAudio): String? {
    return buildList {
        resolveCodecLabel(audio.codecs, audio.codecid).takeIf { it.isNotBlank() }?.let(::add)
        audio.bandwidth.takeIf { it > 0 }?.let { add("${it / 1000} kbps") }
    }.joinToString(" · ").ifBlank { null }
}

private fun buildVideoCodecSubtitle(video: DashVideo): String? {
    return buildList {
        if (video.width > 0 && video.height > 0) {
            add("${video.width}x${video.height}")
        }
        video.frameRate.takeIf { it.isNotBlank() }?.let(::add)
        video.bandwidth.takeIf { it > 0 }?.let { add("${it / 1000} kbps") }
    }.joinToString(" · ").ifBlank { null }
}

private fun String.toHostLabel(): String {
    if (isBlank()) return ""
    return runCatching { URI(this).host.orEmpty() }
        .getOrElse {
            substringAfter("://", this)
                .substringBefore("/")
                .substringBefore("?")
        }
}

private fun DashVideo.videoCodecSelectionKey(): Int {
    return codecid ?: codecs.lowercase().hashCode()
}

private fun DashVideo.isSupportedByDevice(
    isHevcSupported: Boolean,
    isAv1Supported: Boolean
): Boolean {
    val codecText = codecs.lowercase()
    return when {
        codecText.startsWith("avc") -> true
        codecText.startsWith("hev") || codecText.startsWith("hvc") -> isHevcSupported
        codecText.startsWith("av01") -> isAv1Supported
        codecText.startsWith("dvh1") || codecText.startsWith("dvhe") -> isHevcSupported
        else -> true
    }
}

private fun resolveCodecLabel(codecs: String?, codecId: Int?): String {
    val codecText = codecs.orEmpty().lowercase()
    return when {
        codecText.startsWith("dvh1") || codecText.startsWith("dvhe") -> "Dolby Vision"
        codecText.startsWith("av01") -> "AV1"
        codecText.startsWith("hev1") || codecText.startsWith("hvc1") -> "HEVC"
        codecText.startsWith("avc1") -> "AVC"
        codecText.contains("ec-3") || codecText.contains("eac-3") -> "杜比音频"
        codecText.contains("flac") -> "FLAC"
        codecText.contains("mp4a") -> "AAC"
        codecId == 12 -> "HEVC"
        codecId == 13 -> "AV1"
        codecId == 7 -> "AVC"
        else -> codecs.orEmpty()
    }
}

internal fun formatSpeed(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}倍"
    } else {
        "${speed}倍"
    }
}
