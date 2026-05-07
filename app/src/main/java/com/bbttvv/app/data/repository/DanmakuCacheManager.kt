package com.bbttvv.app.data.repository

import android.app.ActivityManager
import android.content.Context
import java.util.LinkedHashMap

internal data class DanmakuSegmentCacheKey(
    val cid: Long,
    val segmentIndex: Int,
)

internal data class DanmakuCacheProfile(
    val rawXmlMaxBytes: Long,
    val segmentMaxBytes: Long,
) {
    val totalMaxBytes: Long
        get() = rawXmlMaxBytes + segmentMaxBytes

    companion object {
        private const val HIGH_MEMORY_CLASS_MB = 384
        private const val MIB = 1024L * 1024L

        val LOW_RAM = DanmakuCacheProfile(
            rawXmlMaxBytes = 1L * MIB,
            segmentMaxBytes = 4L * MIB,
        )
        val NORMAL = DanmakuCacheProfile(
            rawXmlMaxBytes = 2L * MIB,
            segmentMaxBytes = 8L * MIB,
        )
        val HIGH_MEMORY = DanmakuCacheProfile(
            rawXmlMaxBytes = 4L * MIB,
            segmentMaxBytes = 12L * MIB,
        )

        fun resolve(
            isLowRamDevice: Boolean,
            memoryClassMb: Int,
        ): DanmakuCacheProfile {
            return when {
                isLowRamDevice -> LOW_RAM
                memoryClassMb >= HIGH_MEMORY_CLASS_MB -> HIGH_MEMORY
                else -> NORMAL
            }
        }

        fun fromContext(context: Context): DanmakuCacheProfile {
            val activityManager = context.applicationContext
                .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return resolve(
                isLowRamDevice = activityManager?.isLowRamDevice == true,
                memoryClassMb = activityManager?.memoryClass ?: 0,
            )
        }
    }
}

internal class DanmakuCacheManager(
    initialProfile: DanmakuCacheProfile = DanmakuCacheProfile.NORMAL,
) {
    private val lock = Any()
    private var profile: DanmakuCacheProfile = initialProfile
    private val rawXmlMemoryCache = LinkedHashMap<Long, ByteArray>(4, 0.75f, true)
    private val segmentMemoryCache = LinkedHashMap<DanmakuSegmentCacheKey, ByteArray>(16, 0.75f, true)
    private var rawXmlBytes = 0L
    private var segmentBytes = 0L

    fun configure(nextProfile: DanmakuCacheProfile) {
        synchronized(lock) {
            profile = nextProfile
            trimLocked(nextProfile.totalMaxBytes)
        }
    }

    fun getRawXml(cid: Long): ByteArray? = synchronized(lock) {
        rawXmlMemoryCache[cid]
    }

    fun putRawXml(cid: Long, bytes: ByteArray) {
        if (cid <= 0L || bytes.isEmpty()) return
        synchronized(lock) {
            rawXmlBytes = putLocked(
                cache = rawXmlMemoryCache,
                currentBytes = rawXmlBytes,
                maxBytes = profile.rawXmlMaxBytes,
                key = cid,
                bytes = bytes,
            )
        }
    }

    fun getSegment(key: DanmakuSegmentCacheKey): ByteArray? = synchronized(lock) {
        segmentMemoryCache[key]
    }

    fun putSegment(key: DanmakuSegmentCacheKey, bytes: ByteArray) {
        if (key.cid <= 0L || key.segmentIndex <= 0 || bytes.isEmpty()) return
        synchronized(lock) {
            segmentBytes = putLocked(
                cache = segmentMemoryCache,
                currentBytes = segmentBytes,
                maxBytes = profile.segmentMaxBytes,
                key = key,
                bytes = bytes,
            )
        }
    }

    fun clear() {
        synchronized(lock) {
            rawXmlMemoryCache.clear()
            segmentMemoryCache.clear()
            rawXmlBytes = 0L
            segmentBytes = 0L
        }
    }

    fun trimDanmakuCache(targetBytes: Long) {
        synchronized(lock) {
            trimLocked(targetBytes.coerceAtLeast(0L))
        }
    }

    fun trimDanmakuCache(maxRawBytes: Long, maxSegmentBytes: Long) {
        synchronized(lock) {
            val rawTarget = maxRawBytes.coerceIn(0L, profile.rawXmlMaxBytes)
            val segmentTarget = maxSegmentBytes.coerceIn(0L, profile.segmentMaxBytes)
            rawXmlBytes = trimSingleCacheLocked(rawXmlMemoryCache, rawXmlBytes, rawTarget)
            segmentBytes = trimSingleCacheLocked(segmentMemoryCache, segmentBytes, segmentTarget)
        }
    }

    fun trimToHalf() {
        synchronized(lock) {
            trimLocked(profile.totalMaxBytes / 2L)
        }
    }

    fun trimToSmall() {
        synchronized(lock) {
            trimLocked(DanmakuCacheProfile.LOW_RAM.totalMaxBytes.coerceAtMost(profile.totalMaxBytes))
        }
    }

    fun stats(): DanmakuCacheStats = synchronized(lock) {
        DanmakuCacheStats(
            rawEntryCount = rawXmlMemoryCache.size,
            segmentEntryCount = segmentMemoryCache.size,
            rawBytes = rawXmlBytes,
            segmentBytes = segmentBytes,
            rawMaxBytes = profile.rawXmlMaxBytes,
            segmentMaxBytes = profile.segmentMaxBytes,
            totalBytes = estimateDanmakuCacheBytes(rawXmlBytes, segmentBytes),
        )
    }

    private fun <K> putLocked(
        cache: LinkedHashMap<K, ByteArray>,
        currentBytes: Long,
        maxBytes: Long,
        key: K,
        bytes: ByteArray,
    ): Long {
        var nextBytes = currentBytes
        cache.remove(key)?.let { nextBytes -= it.size.toLong() }
        val entryBytes = bytes.size.toLong()
        if (entryBytes > maxBytes) {
            return nextBytes.coerceAtLeast(0L)
        }
        nextBytes = trimSingleCacheLocked(
            cache = cache,
            currentBytes = nextBytes,
            targetBytes = maxBytes - entryBytes,
        )
        cache[key] = bytes
        return nextBytes + entryBytes
    }

    private fun trimLocked(targetBytes: Long) {
        if (targetBytes <= 0L) {
            rawXmlMemoryCache.clear()
            segmentMemoryCache.clear()
            rawXmlBytes = 0L
            segmentBytes = 0L
            return
        }

        val maxBytes = profile.totalMaxBytes.coerceAtLeast(1L)
        val rawTarget = ((targetBytes * profile.rawXmlMaxBytes) / maxBytes)
            .coerceIn(0L, profile.rawXmlMaxBytes)
        val segmentTarget = (targetBytes - rawTarget)
            .coerceIn(0L, profile.segmentMaxBytes)

        rawXmlBytes = trimSingleCacheLocked(rawXmlMemoryCache, rawXmlBytes, rawTarget)
        segmentBytes = trimSingleCacheLocked(segmentMemoryCache, segmentBytes, segmentTarget)

        while (rawXmlBytes + segmentBytes > targetBytes && segmentMemoryCache.isNotEmpty()) {
            segmentBytes = trimEldestLocked(segmentMemoryCache, segmentBytes)
        }
        while (rawXmlBytes + segmentBytes > targetBytes && rawXmlMemoryCache.isNotEmpty()) {
            rawXmlBytes = trimEldestLocked(rawXmlMemoryCache, rawXmlBytes)
        }
    }

    private fun <K> trimSingleCacheLocked(
        cache: LinkedHashMap<K, ByteArray>,
        currentBytes: Long,
        targetBytes: Long,
    ): Long {
        var nextBytes = currentBytes.coerceAtLeast(0L)
        while (nextBytes > targetBytes && cache.isNotEmpty()) {
            nextBytes = trimEldestLocked(cache, nextBytes)
        }
        return nextBytes
    }

    private fun <K> trimEldestLocked(
        cache: LinkedHashMap<K, ByteArray>,
        currentBytes: Long,
    ): Long {
        val iterator = cache.entries.iterator()
        if (!iterator.hasNext()) return currentBytes.coerceAtLeast(0L)
        val eldest = iterator.next()
        val nextBytes = currentBytes - eldest.value.size.toLong()
        iterator.remove()
        return nextBytes.coerceAtLeast(0L)
    }
}
