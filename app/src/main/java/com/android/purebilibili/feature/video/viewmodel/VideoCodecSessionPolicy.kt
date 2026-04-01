package com.android.purebilibili.feature.video.viewmodel

internal const val AVC_CODEC_KEY = "avc1"
internal const val AV1_CODEC_KEY = "av01"
internal const val HEVC_CODEC_KEY = "hev1"

internal fun normalizeCodecFamilyKey(codec: String?): String? {
    val normalized = codec?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        normalized.startsWith("av01") -> AV1_CODEC_KEY
        normalized.startsWith("avc") || normalized.startsWith("h264") -> AVC_CODEC_KEY
        normalized.startsWith("hev") || normalized.startsWith("hvc") -> HEVC_CODEC_KEY
        else -> normalized.substringBefore('.')
    }
}

internal fun resolveEffectiveVideoCodecPreference(
    requestCodecOverride: String?,
    settingsCodecPreference: String,
    sessionBlockedCodecs: Set<String>
): String {
    val requestCodec = normalizeCodecFamilyKey(requestCodecOverride)
    if (requestCodec != null) {
        return requestCodec
    }

    val settingsCodec = normalizeCodecFamilyKey(settingsCodecPreference) ?: HEVC_CODEC_KEY
    return if (AV1_CODEC_KEY in sessionBlockedCodecs && settingsCodec == AV1_CODEC_KEY) {
        AVC_CODEC_KEY
    } else {
        settingsCodec
    }
}

internal fun resolveEffectiveAv1Support(
    deviceSupportsAv1: Boolean,
    sessionBlockedCodecs: Set<String>
): Boolean {
    return deviceSupportsAv1 && AV1_CODEC_KEY !in sessionBlockedCodecs
}
