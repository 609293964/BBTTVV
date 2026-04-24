// 文件路径: core/cache/PlayUrlCache.kt
package com.bbttvv.app.core.cache

import android.util.LruCache
import com.bbttvv.app.data.model.response.PlayUrlData

/**
 * 播放地址缓存管理器
 * 
 * 使用 LruCache 缓存视频播放 URL，减少重复网络请求。
 * 缓存上限 50 条，有效期 10 分钟。
 */
object PlayUrlCache {
    
    private const val TAG = "PlayUrlCache"
    private const val MAX_CACHE_SIZE = 80  //  优化：增加缓存容量
    private const val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 分钟
    
    /**
     * 缓存条目
     */
    data class CachedPlayUrl(
        val bvid: String,
        val cid: Long,
        val data: PlayUrlData,
        val quality: Int,
        val requestedQuality: Int?,
        val requestedAudioLang: String?,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val expiresAt: Long get() = timestamp + CACHE_DURATION_MS
        
        fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    }
    
    /**
     * 生成缓存键
     */
    private fun generateKey(
        bvid: String,
        cid: Long,
        requestedQuality: Int?,
        audioLang: String?
    ): String {
        val qualityKey = requestedQuality?.toString() ?: "auto"
        val audioKey = audioLang ?: "default"
        return "$bvid:$cid:q=$qualityKey:a=$audioKey"
    }

    private fun generateVideoPrefix(bvid: String, cid: Long): String = "$bvid:$cid:"
    
    /**
     * 缓存实例
     */
    private val cache: LruCache<String, CachedPlayUrl> = LruCache(MAX_CACHE_SIZE)
    
    /**
     * 获取缓存的播放地址
     * 
     * @param bvid 视频 BV 号
     * @param cid 视频 CID
     * @param requestedQuality 请求画质（null 表示自动）
     * @param audioLang 音轨语言（null 表示默认）
     * @return 缓存的播放数据，如果缓存不存在或已过期则返回 null
     */
    @Synchronized
    fun get(
        bvid: String,
        cid: Long,
        requestedQuality: Int? = null,
        audioLang: String? = null
    ): PlayUrlData? {
        val key = generateKey(bvid, cid, requestedQuality, audioLang)
        val exact = cache.get(key)

        val (matchedKey, cached) = when {
            exact != null -> key to exact
            requestedQuality == null && audioLang == null -> {
                findNewestValidCacheForVideo(bvid, cid)
                    ?: (key to null)
            }
            else -> key to null
        }

        return when {
            cached == null -> {
                com.bbttvv.app.core.util.Logger.d(
                    TAG,
                    " Cache miss: bvid=$bvid, cid=$cid, reqQ=${requestedQuality ?: "auto"}, lang=${audioLang ?: "default"}"
                )
                null
            }
            cached.isExpired() -> {
                com.bbttvv.app.core.util.Logger.d(
                    TAG,
                    "⏰ Cache expired: bvid=$bvid, cid=$cid, reqQ=${requestedQuality ?: "auto"}, lang=${audioLang ?: "default"}"
                )
                cache.remove(matchedKey)
                null
            }
            else -> {
                val remainingMs = cached.expiresAt - System.currentTimeMillis()
                com.bbttvv.app.core.util.Logger.d(
                    TAG,
                    " Cache hit: bvid=$bvid, cid=$cid, reqQ=${cached.requestedQuality ?: "auto"}, actualQ=${cached.quality}, lang=${cached.requestedAudioLang ?: "default"}, expires in ${remainingMs / 1000}s"
                )
                cached.data
            }
        }
    }
    
    /**
     * 添加播放地址到缓存
     * 
     * @param bvid 视频 BV 号
     * @param cid 视频 CID
     * @param data 播放数据
     * @param quality 请求画质（null 表示自动）
     * @param audioLang 音轨语言（null 表示默认）
     */
    @Synchronized
    fun put(
        bvid: String,
        cid: Long,
        data: PlayUrlData,
        quality: Int? = null,
        audioLang: String? = null
    ) {
        val key = generateKey(bvid, cid, quality, audioLang)
        val entry = CachedPlayUrl(
            bvid = bvid,
            cid = cid,
            data = data,
            quality = data.quality,
            requestedQuality = quality,
            requestedAudioLang = audioLang
        )
        cache.put(key, entry)
        com.bbttvv.app.core.util.Logger.d(
            TAG,
            " Cached: bvid=$bvid, cid=$cid, reqQ=${quality ?: "auto"}, actualQ=${data.quality}, lang=${audioLang ?: "default"}"
        )
    }
    
    /**
     * 使指定视频的缓存失效
     */
    @Synchronized
    fun invalidate(
        bvid: String,
        cid: Long,
        requestedQuality: Int? = null,
        audioLang: String? = null
    ) {
        if (requestedQuality == null && audioLang == null) {
            val prefix = generateVideoPrefix(bvid, cid)
            var removedCount = 0
            val keys = cache.snapshot().keys
            keys.forEach { key ->
                if (key.startsWith(prefix)) {
                    cache.remove(key)
                    removedCount++
                }
            }
            com.bbttvv.app.core.util.Logger.d(
                TAG,
                " Invalidated all variants: bvid=$bvid, cid=$cid, removed=$removedCount"
            )
            return
        }

        val key = generateKey(bvid, cid, requestedQuality, audioLang)
        cache.remove(key)
        com.bbttvv.app.core.util.Logger.d(
            TAG,
            " Invalidated variant: bvid=$bvid, cid=$cid, reqQ=${requestedQuality ?: "auto"}, lang=${audioLang ?: "default"}"
        )
    }
    
    /**
     * 清除所有缓存
     */
    @Synchronized
    fun clear() {
        cache.evictAll()
        com.bbttvv.app.core.util.Logger.d(TAG, " Cache cleared")
    }
    
    /**
     * 获取当前缓存大小
     */
    fun size(): Int = cache.size()
    
    /**
     * 获取缓存统计信息（调试用）
     */
    fun getStats(): String {
        return "PlayUrlCache: size=${size()}, maxSize=$MAX_CACHE_SIZE, " +
               "hitCount=${cache.hitCount()}, missCount=${cache.missCount()}"
    }

    private fun findNewestValidCacheForVideo(bvid: String, cid: Long): Pair<String, CachedPlayUrl>? {
        val prefix = generateVideoPrefix(bvid, cid)
        var candidate: Pair<String, CachedPlayUrl>? = null
        val snapshot = cache.snapshot()

        snapshot.forEach { (key, entry) ->
            if (!key.startsWith(prefix)) return@forEach

            if (entry.isExpired()) {
                cache.remove(key)
                return@forEach
            }

            if (candidate == null || entry.timestamp > candidate.second.timestamp) {
                candidate = key to entry
            }
        }

        return candidate
    }
}

