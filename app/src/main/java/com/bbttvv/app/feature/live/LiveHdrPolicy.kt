package com.bbttvv.app.feature.live

/** qn remains the menu's stable business key for SDR; HDR variants get distinct positive keys. */
internal fun liveQualityKey(qn: Int, hdrType: Int = 0, eotf: Int = 0): Int =
    qn + hdrType * 100_000 + eotf * 10_000_000

internal fun liveQualityQn(key: Int): Int = key % 100_000
internal fun liveQualityHdrType(key: Int): Int = key / 100_000 % 100
internal fun liveQualityEotf(key: Int): Int = key / 10_000_000

data class LiveQualityOption(val qn: Int, val label: String)

internal fun isPlayableLiveVariant(
    format: String,
    codec: String,
    hdrType: Int,
    eotf: Int,
    supportsHdr: Boolean,
    protocol: String = "http_hls",
): Boolean {
    if (format == "flv" && codec.equals("hevc", ignoreCase = true)) return false
    if (hdrType == 0) return eotf == 0
    val supportedContainer = (protocol == "http_hls" && format in setOf("fmp4", "ts")) ||
        (protocol == "http_stream" && format == "fmp4")
    return hdrType in 1..2 && eotf in 1..2 && supportsHdr && supportedContainer
}

internal fun buildLiveQualityOptions(
    candidates: List<LiveStreamCandidate>,
    labels: Map<Int, String>,
): List<LiveQualityOption> = candidates.flatMap { candidate ->
    (candidate.acceptedQns + candidate.currentQn).filter { it in 1..99_999 }.distinct().map { qn ->
        val key = liveQualityKey(qn, candidate.hdrType, candidate.eotf)
        val baseLabel = labels[key]?.takeIf(String::isNotBlank)
            ?: labels[qn]?.takeIf(String::isNotBlank)
            ?: when (qn) { 80 -> "流畅"; 150 -> "高清"; 250 -> "超清"; 400 -> "蓝光"; 10000 -> "原画"; else -> qn.toString() }
        val suffix = when (candidate.eotf) { 1 -> "HDR10"; 2 -> "HLG"; else -> "" }
        LiveQualityOption(key, if (candidate.hdrType != 0) "$baseLabel $suffix" else baseLabel)
    }
}.distinctBy { it.qn }.sortedWith(compareByDescending<LiveQualityOption> { liveQualityQn(it.qn) }.thenBy { it.qn })

internal fun scopeLiveVariants(candidates: List<LiveStreamCandidate>, requestedKey: Int): List<LiveStreamCandidate> {
    val matching = candidates.filter {
        it.hdrType == liveQualityHdrType(requestedKey) && it.eotf == liveQualityEotf(requestedKey)
    }
    return matching.ifEmpty { candidates.filter { it.hdrType == 0 && it.eotf == 0 } }
}
