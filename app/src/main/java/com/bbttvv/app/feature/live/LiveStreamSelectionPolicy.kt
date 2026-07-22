package com.bbttvv.app.feature.live

import com.bbttvv.app.core.store.SettingsManager

internal data class LiveStreamCandidate(
    val key: String,
    val lineKey: String,
    val lineBaseLabel: String,
    val lineSubtitle: String?,
    val protocolName: String,
    val protocolLabel: String,
    val formatName: String,
    val formatLabel: String,
    val codecName: String,
    val codecLabel: String,
    val currentQn: Int,
    val host: String,
    val hostLabel: String,
    val url: String,
)

internal fun selectLiveStreamCandidate(
    candidates: List<LiveStreamCandidate>,
    preferredLineKey: String?,
    preferHighBitrate: Boolean,
    cdnPreference: SettingsManager.PlayerCdnPreference,
): LiveStreamCandidate? {
    val scopedCandidates = preferredLineKey
        ?.takeIf { key -> candidates.any { it.lineKey == key } }
        ?.let { key -> candidates.filter { it.lineKey == key } }
        ?: candidates
    return rankLiveStreamCandidates(
        candidates = scopedCandidates,
        preferHighBitrate = preferHighBitrate,
        cdnPreference = cdnPreference,
    ).firstOrNull()
}

internal fun selectLiveRecoveryCandidate(
    candidates: List<LiveStreamCandidate>,
    failedCandidateKeys: Set<String>,
    currentLineKey: String?,
    preferHighBitrate: Boolean,
    cdnPreference: SettingsManager.PlayerCdnPreference,
    preferCompatibleCodec: Boolean,
): LiveStreamCandidate? {
    var remaining = candidates.filterNot { it.key in failedCandidateKeys }
    if (preferCompatibleCodec && remaining.any { it.codecName.equals("avc", ignoreCase = true) }) {
        remaining = remaining.filter { it.codecName.equals("avc", ignoreCase = true) }
    }
    val sameLine = currentLineKey
        ?.let { key -> remaining.filter { it.lineKey == key } }
        .orEmpty()
    val otherLines = if (sameLine.isEmpty()) {
        remaining
    } else {
        remaining.filterNot { it.lineKey == currentLineKey }
    }
    return rankLiveStreamCandidates(
        candidates = sameLine,
        preferHighBitrate = preferHighBitrate,
        cdnPreference = cdnPreference,
    ).firstOrNull() ?: rankLiveStreamCandidates(
        candidates = otherLines,
        preferHighBitrate = preferHighBitrate,
        cdnPreference = cdnPreference,
    ).firstOrNull()
}

internal fun rankLiveStreamCandidates(
    candidates: List<LiveStreamCandidate>,
    preferHighBitrate: Boolean,
    cdnPreference: SettingsManager.PlayerCdnPreference,
): List<LiveStreamCandidate> {
    return candidates.sortedWith(
        compareByDescending<LiveStreamCandidate> {
            scoreLiveStreamCandidate(
                candidate = it,
                preferHighBitrate = preferHighBitrate,
                cdnPreference = cdnPreference,
            )
        }
            .thenByDescending { it.currentQn }
            .thenBy { it.hostLabel }
            .thenBy { it.key }
    )
}

internal fun resolveEffectiveLiveQuality(
    candidate: LiveStreamCandidate,
    responseQuality: Int,
): Int {
    return candidate.currentQn.takeIf { it > 0 } ?: responseQuality.coerceAtLeast(0)
}

internal fun scoreLiveStreamCandidate(
    candidate: LiveStreamCandidate,
    preferHighBitrate: Boolean,
    cdnPreference: SettingsManager.PlayerCdnPreference,
): Int {
    val host = candidate.hostLabel.lowercase()
    val matchesPreferredCdn = when (cdnPreference) {
        SettingsManager.PlayerCdnPreference.BILIVIDEO -> {
            host.contains("bilivideo") || host.contains("upos") || host.contains("cn-")
        }
        SettingsManager.PlayerCdnPreference.MCDN -> host.contains("mcdn")
    }
    val protocolScore = when (candidate.protocolName) {
        "http_hls" -> if (preferHighBitrate) 120 else 220
        "http_stream" -> if (preferHighBitrate) 220 else 140
        else -> 80
    }
    val formatScore = when (candidate.formatName) {
        "ts" -> if (preferHighBitrate) 240 else 200
        "flv" -> if (preferHighBitrate) 220 else 160
        "fmp4" -> if (preferHighBitrate) 160 else 190
        else -> 100
    }
    val codecScore = when (candidate.codecName.lowercase()) {
        "hevc" -> if (preferHighBitrate) 200 else 120
        "avc" -> if (preferHighBitrate) 150 else 190
        else -> 110
    }
    return candidate.currentQn * 1000 +
        protocolScore +
        formatScore +
        codecScore +
        if (matchesPreferredCdn) 300 else 0
}
