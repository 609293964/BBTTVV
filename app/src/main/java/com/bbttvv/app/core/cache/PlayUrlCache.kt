// 文件路径: core/cache/PlayUrlCache.kt
package com.bbttvv.app.core.cache

import android.util.LruCache
import com.bbttvv.app.core.store.AccountSessionEpoch
import com.bbttvv.app.data.model.response.PlayUrlData

/**
 * 播放地址缓存管理器。
 *
 * 缓存严格绑定到账号会话 epoch。账号切换后，旧请求即使晚到也不能把旧账号的
 * 签名/权益相关播放地址重新写入新账号缓存。
 */
object PlayUrlCache {
    private const val TAG = "PlayUrlCache"
    private const val MAX_CACHE_SIZE = 80
    private const val CACHE_DURATION_MS = 10 * 60 * 1000L

    data class CachedPlayUrl(
        val bvid: String,
        val cid: Long,
        val data: PlayUrlData,
        val quality: Int,
        val requestedQuality: Int?,
        val requestedAudioLang: String?,
        val sessionEpoch: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val expiresAt: Long get() = timestamp + CACHE_DURATION_MS
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }

    private fun generateKey(
        bvid: String,
        cid: Long,
        requestedQuality: Int?,
        audioLang: String?,
        sessionEpoch: Long
    ): String {
        val qualityKey = requestedQuality?.toString() ?: "auto"
        val audioKey = audioLang ?: "default"
        return "e=$sessionEpoch:$bvid:$cid:q=$qualityKey:a=$audioKey"
    }

    private fun generateVideoPrefix(
        bvid: String,
        cid: Long,
        sessionEpoch: Long
    ): String = "e=$sessionEpoch:$bvid:$cid:"

    private val cache: LruCache<String, CachedPlayUrl> = LruCache(MAX_CACHE_SIZE)

    @Synchronized
    fun get(
        bvid: String,
        cid: Long,
        requestedQuality: Int? = null,
        audioLang: String? = null,
        sessionEpoch: Long = AccountSessionEpoch.current()
    ): PlayUrlData? {
        if (!AccountSessionEpoch.isCurrent(sessionEpoch)) {
            com.bbttvv.app.core.util.Logger.d(TAG, " Skip stale cache read for epoch=$sessionEpoch")
            return null
        }

        val key = generateKey(bvid, cid, requestedQuality, audioLang, sessionEpoch)
        val exact = cache.get(key)
        val (matchedKey, cached) = when {
            exact != null -> key to exact
            requestedQuality == null && audioLang == null -> {
                findNewestValidCacheForVideo(bvid, cid, sessionEpoch) ?: (key to null)
            }
            else -> key to null
        }

        return when {
            cached == null -> {
                com.bbttvv.app.core.util.Logger.d(
                    TAG,
                    " Cache miss: bvid=$bvid, cid=$cid, epoch=$sessionEpoch, reqQ=${requestedQuality ?: "auto"}, lang=${audioLang ?: "default"}"
                )
                null
            }
            cached.isExpired() -> {
                cache.remove(matchedKey)
                null
            }
            else -> cached.data
        }
    }

    @Synchronized
    fun put(
        bvid: String,
        cid: Long,
        data: PlayUrlData,
        quality: Int? = null,
        audioLang: String? = null,
        sessionEpoch: Long = AccountSessionEpoch.current()
    ) {
        if (!AccountSessionEpoch.isCurrent(sessionEpoch)) {
            com.bbttvv.app.core.util.Logger.d(
                TAG,
                " Skip stale cache write: bvid=$bvid, cid=$cid, epoch=$sessionEpoch, current=${AccountSessionEpoch.current()}"
            )
            return
        }

        val key = generateKey(bvid, cid, quality, audioLang, sessionEpoch)
        cache.put(
            key,
            CachedPlayUrl(
                bvid = bvid,
                cid = cid,
                data = data,
                quality = data.quality,
                requestedQuality = quality,
                requestedAudioLang = audioLang,
                sessionEpoch = sessionEpoch
            )
        )
    }

    @Synchronized
    fun invalidate(
        bvid: String,
        cid: Long,
        requestedQuality: Int? = null,
        audioLang: String? = null,
        sessionEpoch: Long = AccountSessionEpoch.current()
    ) {
        if (requestedQuality == null && audioLang == null) {
            val prefix = generateVideoPrefix(bvid, cid, sessionEpoch)
            cache.snapshot().keys
                .filter { it.startsWith(prefix) }
                .forEach(cache::remove)
            return
        }

        cache.remove(generateKey(bvid, cid, requestedQuality, audioLang, sessionEpoch))
    }

    /** Clears all play URLs without changing the account generation. */
    @Synchronized
    fun clear() {
        cache.evictAll()
        com.bbttvv.app.core.util.Logger.d(TAG, " Cache cleared")
    }

    /**
     * Commits an account boundary. This must be called exactly when a new account session
     * becomes active (or the active account is cleared).
     */
    @Synchronized
    fun rotateAccountScope(): Long {
        val nextEpoch = AccountSessionEpoch.advance()
        cache.evictAll()
        com.bbttvv.app.core.util.Logger.d(TAG, " Account cache scope rotated: epoch=$nextEpoch")
        return nextEpoch
    }

    @Synchronized
    fun trimToSize(maxEntries: Int) {
        if (maxEntries <= 0) {
            clear()
            return
        }

        val target = maxEntries.coerceAtMost(MAX_CACHE_SIZE)
        if (cache.size() <= target) return
        cache.resize(target)
        cache.resize(MAX_CACHE_SIZE)
    }

    fun size(): Int = cache.size()

    fun getStats(): String {
        return "PlayUrlCache: size=${size()}, maxSize=$MAX_CACHE_SIZE, " +
            "epoch=${AccountSessionEpoch.current()}, hitCount=${cache.hitCount()}, missCount=${cache.missCount()}"
    }

    private fun findNewestValidCacheForVideo(
        bvid: String,
        cid: Long,
        sessionEpoch: Long
    ): Pair<String, CachedPlayUrl>? {
        val prefix = generateVideoPrefix(bvid, cid, sessionEpoch)
        var candidate: Pair<String, CachedPlayUrl>? = null

        cache.snapshot().forEach { (key, entry) ->
            if (!key.startsWith(prefix)) return@forEach
            if (entry.sessionEpoch != sessionEpoch || entry.isExpired()) {
                cache.remove(key)
                return@forEach
            }
            if (candidate == null || entry.timestamp > candidate!!.second.timestamp) {
                candidate = key to entry
            }
        }
        return candidate
    }
}
