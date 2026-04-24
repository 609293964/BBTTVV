package com.bbttvv.app.data.service.video

import android.content.Context
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.util.SubtitleCue
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.NavData
import com.bbttvv.app.data.model.response.RelatedVideo
import com.bbttvv.app.data.model.response.VideoDetailResponse
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.data.repository.CreatorCardStats
import kotlinx.coroutines.Deferred
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

internal class SynchronizedLruMap<K, V>(
    private val maxEntries: Int,
    private val ttlMillis: Long = NO_TTL,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    private data class Entry<V>(
        val value: V,
        val storedAtMs: Long
    )

    private val lock = Any()
    private val delegate = object : LinkedHashMap<K, Entry<V>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?): Boolean {
            return size > maxEntries
        }
    }

    operator fun get(key: K): V? = synchronized(lock) {
        val entry = delegate[key] ?: return@synchronized null
        if (isExpired(entry)) {
            delegate.remove(key)
            return@synchronized null
        }
        entry.value
    }

    operator fun set(key: K, value: V) {
        synchronized(lock) {
            delegate[key] = Entry(
                value = value,
                storedAtMs = nowProvider()
            )
        }
    }

    fun containsKey(key: K): Boolean = synchronized(lock) {
        val entry = delegate[key] ?: return@synchronized false
        if (isExpired(entry)) {
            delegate.remove(key)
            return@synchronized false
        }
        true
    }

    fun remove(key: K): V? = synchronized(lock) { delegate.remove(key)?.value }

    fun clear() {
        synchronized(lock) {
            delegate.clear()
        }
    }

    fun valuesSnapshot(): List<V> = synchronized(lock) {
        pruneExpiredLocked()
        delegate.values.map { it.value }
    }

    fun size(): Int = synchronized(lock) {
        pruneExpiredLocked()
        delegate.size
    }

    private fun pruneExpiredLocked() {
        if (ttlMillis <= 0L || delegate.isEmpty()) return
        val iterator = delegate.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (isExpired(entry.value)) {
                iterator.remove()
            }
        }
    }

    private fun isExpired(entry: Entry<V>): Boolean {
        return ttlMillis > 0L && nowProvider() - entry.storedAtMs >= ttlMillis
    }

    private companion object {
        const val NO_TTL = -1L
    }
}

internal object VideoCacheService {
    private const val SUBTITLE_CUE_CACHE_MAX_ENTRIES = 512
    private const val VIDEO_INFO_CACHE_MAX_ENTRIES = 240
    private const val VIDEO_PREVIEW_CACHE_MAX_ENTRIES = 360
    private const val RELATED_VIDEOS_CACHE_MAX_ENTRIES = 160
    private const val CREATOR_CARD_STATS_CACHE_MAX_ENTRIES = 240

    private val SUBTITLE_CUE_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(45)
    private val VIDEO_INFO_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(20)
    private val VIDEO_PREVIEW_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(20)
    private val RELATED_VIDEOS_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15)
    private val CREATOR_CARD_STATS_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(30)

    val subtitleCueCache = SynchronizedLruMap<String, List<SubtitleCue>>(
        maxEntries = SUBTITLE_CUE_CACHE_MAX_ENTRIES,
        ttlMillis = SUBTITLE_CUE_CACHE_TTL_MS
    )
    val videoInfoCache = SynchronizedLruMap<String, VideoDetailResponse>(
        maxEntries = VIDEO_INFO_CACHE_MAX_ENTRIES,
        ttlMillis = VIDEO_INFO_CACHE_TTL_MS
    )
    val videoPreviewCache = SynchronizedLruMap<String, ViewInfo>(
        maxEntries = VIDEO_PREVIEW_CACHE_MAX_ENTRIES,
        ttlMillis = VIDEO_PREVIEW_CACHE_TTL_MS
    )
    val videoInfoInFlight = ConcurrentHashMap<String, Deferred<VideoDetailResponse>>()
    val relatedVideosCache = SynchronizedLruMap<String, List<RelatedVideo>>(
        maxEntries = RELATED_VIDEOS_CACHE_MAX_ENTRIES,
        ttlMillis = RELATED_VIDEOS_CACHE_TTL_MS
    )
    val relatedVideosInFlight = ConcurrentHashMap<String, Deferred<List<RelatedVideo>>>()
    val creatorCardStatsCache = SynchronizedLruMap<Long, CreatorCardStats>(
        maxEntries = CREATOR_CARD_STATS_CACHE_MAX_ENTRIES,
        ttlMillis = CREATOR_CARD_STATS_CACHE_TTL_MS
    )

    @Volatile
    var cachedNavInfo: NavData? = null

    fun invalidateAccountScopedCaches() {
        subtitleCueCache.clear()
        videoInfoCache.clear()
        videoPreviewCache.clear()
        relatedVideosCache.clear()
        creatorCardStatsCache.clear()
        videoInfoInFlight.clear()
        relatedVideosInFlight.clear()
        cachedNavInfo = null
    }
}

internal object VideoSessionService {
    val api get() = NetworkModule.api
    val buvidApi get() = NetworkModule.buvidApi

    @Volatile
    var appApiCooldownUntilMs: Long = 0L

    @Volatile
    var buvidInitialized: Boolean = false

    @Volatile
    var applicationContext: Context? = null

    @Volatile
    var wbiKeysCache: Pair<String, String>? = null

    @Volatile
    var wbiKeysTimestamp: Long = 0L

    @Volatile
    var last412Time: Long = 0L
}

internal object VideoFeedStateService {
    @Volatile
    var preloadedHomeVideos: Result<List<VideoItem>>? = null

    @Volatile
    var homePreloadDeferred: Deferred<Result<List<VideoItem>>>? = null

    @Volatile
    var hasCompletedHomePreload: Boolean = false
}
