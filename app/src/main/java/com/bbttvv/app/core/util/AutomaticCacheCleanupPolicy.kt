package com.bbttvv.app.core.util

private const val AUTOMATIC_CACHE_CLEAN_TARGET_PERCENT = 80L

internal fun resolveAutomaticCacheCleanupTargets(
    imageBytes: Long,
    httpBytes: Long,
    thresholdBytes: Long,
): Set<CacheClearTarget> {
    if (thresholdBytes <= 0L) return emptySet()
    val safeImageBytes = imageBytes.coerceAtLeast(0L)
    val safeHttpBytes = httpBytes.coerceAtLeast(0L)
    var remainingBytes = safeImageBytes + safeHttpBytes
    if (remainingBytes <= thresholdBytes) return emptySet()

    val targetBytes = thresholdBytes * AUTOMATIC_CACHE_CLEAN_TARGET_PERCENT / 100L
    val candidates = listOf(
        CacheClearTarget.IMAGE_PREVIEW to safeImageBytes,
        CacheClearTarget.NETWORK to safeHttpBytes,
    ).sortedByDescending { (_, size) -> size }
    return buildSet {
        candidates.forEach { (target, size) ->
            if (remainingBytes <= targetBytes) return@forEach
            if (size > 0L) {
                add(target)
                remainingBytes -= size
            }
        }
    }
}
