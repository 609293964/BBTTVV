// 文件路径: data/repository/VideoRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.response.*
import com.bbttvv.app.data.service.video.VideoCacheService
import com.bbttvv.app.data.service.video.VideoSessionService
import com.bbttvv.app.core.util.SubtitleCue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private const val SUBTITLE_CUE_CACHE_MAX_ENTRIES = 512
private const val SUBTITLE_CUE_CACHE_ENTRY_OVERHEAD_BYTES = 512L
private const val SUBTITLE_CUE_ESTIMATED_BYTES_PER_CUE = 160L

internal fun shouldStartHomePreload(
    hasPreloadedData: Boolean,
    hasActivePreloadTask: Boolean
): Boolean {
    return !hasPreloadedData && !hasActivePreloadTask
}

internal fun shouldPrimeBuvidForHomePreload(feedApiType: SettingsManager.FeedApiType): Boolean {
    return feedApiType == SettingsManager.FeedApiType.MOBILE
}

internal fun shouldReuseInFlightPreloadForHomeRequest(
    idx: Int,
    isPreloading: Boolean,
    hasPreloadedData: Boolean
): Boolean {
    return idx == 0 && isPreloading && !hasPreloadedData
}

internal fun shouldReportHomeDataReadyForSplash(
    hasCompletedPreload: Boolean,
    hasPreloadedData: Boolean
): Boolean {
    return hasCompletedPreload || hasPreloadedData
}

internal fun resolveHomeFeedWbiKeys(
    cachedKeys: Pair<String, String>?,
    navWbiImg: WbiImg?
): Pair<String, String>? {
    if (cachedKeys != null) return cachedKeys
    val wbiImg = navWbiImg ?: return null
    val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
    val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")
    return if (imgKey.isNotEmpty() && subKey.isNotEmpty()) imgKey to subKey else null
}

internal fun buildSubtitleCueCacheKey(
    bvid: String,
    cid: Long,
    subtitleId: Long,
    subtitleIdStr: String,
    subtitleLan: String,
    normalizedSubtitleUrl: String
): String {
    val urlHash = MessageDigest.getInstance("SHA-1")
        .digest(normalizedSubtitleUrl.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }
    val idPart = subtitleIdStr.takeIf { it.isNotBlank() }
        ?: subtitleId.takeIf { it > 0L }?.toString()
        ?: "no-id"
    return "${bvid.ifBlank { "unknown" }}:${cid.coerceAtLeast(0L)}:${idPart}:${subtitleLan.ifBlank { "unknown" }}:$urlHash"
}

internal fun estimateSubtitleCueCacheBytes(
    entryCount: Int,
    totalCueCount: Int
): Long {
    val normalizedEntryCount = entryCount.coerceAtLeast(0)
    val normalizedCueCount = totalCueCount.coerceAtLeast(0)
    return normalizedEntryCount * SUBTITLE_CUE_CACHE_ENTRY_OVERHEAD_BYTES +
        normalizedCueCount * SUBTITLE_CUE_ESTIMATED_BYTES_PER_CUE
}

data class SubtitleCueCacheStats(
    val entryCount: Int,
    val totalCueCount: Int,
    val estimatedBytes: Long
)

data class CreatorCardStats(
    val followerCount: Int,
    val videoCount: Int
)

object VideoRepository {
    private val api get() = VideoSessionService.api
    private val subtitleCueCache get() = VideoCacheService.subtitleCueCache
    private val videoInfoCache get() = VideoCacheService.videoInfoCache
    private val videoPreviewCache get() = VideoCacheService.videoPreviewCache
    private val videoInfoInFlight get() = VideoCacheService.videoInfoInFlight
    private val relatedVideosCache get() = VideoCacheService.relatedVideosCache
    private val relatedVideosInFlight get() = VideoCacheService.relatedVideosInFlight

    fun getSubtitleCueCacheStats(): SubtitleCueCacheStats {
        val snapshot = subtitleCueCache.valuesSnapshot()
        val entryCount = snapshot.size
        val totalCueCount = snapshot.sumOf { it.size }
        return SubtitleCueCacheStats(
            entryCount = entryCount,
            totalCueCount = totalCueCount,
            estimatedBytes = estimateSubtitleCueCacheBytes(
                entryCount = entryCount,
                totalCueCount = totalCueCount
            )
        )
    }

    fun clearSubtitleCueCache() {
        subtitleCueCache.clear()
    }

    fun invalidateAccountScopedCaches() {
        SubtitleAndAuxRepository.invalidateAccountScopedCaches()
    }

    internal fun getAppApiCooldownRemainingMs(nowMs: Long = System.currentTimeMillis()): Long {
        return PlaybackSessionManager.getAppApiCooldownRemainingMs(nowMs)
    }

    internal fun isAppApiCoolingDown(nowMs: Long = System.currentTimeMillis()): Boolean {
        return PlaybackSessionManager.isAppApiCoolingDown(nowMs)
    }

    suspend fun getVideoTitle(
        bvid: String,
        aid: Long = 0L
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val lookup = resolveVideoInfoLookupInput(rawBvid = bvid, aid = aid)
                ?: throw Exception("无效的视频标识: bvid=$bvid, aid=$aid")
            val response = if (lookup.bvid.isNotEmpty()) {
                getVideoInfo(lookup.bvid)
            } else {
                api.getVideoInfoByAid(lookup.aid)
            }
            val info = response.data ?: throw Exception("视频详情为空: ${response.code}")
            val title = info.title.trim()
            if (title.isEmpty()) throw Exception("视频标题为空")
            Result.success(title)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 公开的 buvid3 初始化函数 - 供其他 Repository 调用
     */
    suspend fun ensureBuvid3() {
        PlaybackSessionManager.ensureBuvid3()
    }

    fun isHomeDataReady(): Boolean = FeedRepository.isHomeDataReady()

    fun preloadHomeData(scope: CoroutineScope = AppScope.ioScope) {
        FeedRepository.preloadHomeData(scope)
    }

    suspend fun getHomeVideos(idx: Int = 0): Result<List<VideoItem>> {
        return FeedRepository.getHomeVideos(idx = idx)
    }

    suspend fun getPopularVideos(page: Int = 1): Result<List<VideoItem>> =
        FeedRepository.getPopularVideos(page = page)

    suspend fun getRankingVideos(rid: Int = 0, type: String = "all"): Result<List<VideoItem>> =
        FeedRepository.getRankingVideos(rid = rid, type = type)

    suspend fun getPreciousVideos(): Result<List<VideoItem>> =
        FeedRepository.getPreciousVideos()

    suspend fun getWeeklyMustWatchVideos(number: Int? = null): Result<List<VideoItem>> =
        FeedRepository.getWeeklyMustWatchVideos(number = number)

    suspend fun getRegionVideos(tid: Int, page: Int = 1): Result<List<VideoItem>> =
        FeedRepository.getRegionVideos(tid = tid, page = page)
    
    //  [新增] 上报播放心跳（记录到历史记录）
    suspend fun reportPlayHeartbeat(
        bvid: String,
        cid: Long,
        playedTime: Long = 0,
        realPlayedTime: Long = playedTime,
        startTsSec: Long = System.currentTimeMillis() / 1000L
    ) = PlaybackRepository.reportPlayHeartbeat(
        bvid = bvid,
        cid = cid,
        playedTime = playedTime,
        realPlayedTime = realPlayedTime,
        startTsSec = startTsSec
    )

    suspend fun reportPlayHistoryProgress(
        aid: Long,
        cid: Long,
        progressSec: Long = 0,
        platform: String = "android"
    ) = PlaybackRepository.reportPlayHistoryProgress(
        aid = aid,
        cid = cid,
        progressSec = progressSec
    )
    

    suspend fun getNavInfo(): Result<NavData> = SubtitleAndAuxRepository.getNavInfo()

    suspend fun refreshVipStatusForPreferredQualityIfNeeded(
        isLoggedIn: Boolean,
        cachedIsVip: Boolean,
        storedQuality: Int,
        autoHighestEnabled: Boolean
    ): Boolean {
        return PlaybackRepository.refreshVipStatusForPreferredQualityIfNeeded(
            isLoggedIn = isLoggedIn,
            cachedIsVip = cachedIsVip,
            storedQuality = storedQuality,
            autoHighestEnabled = autoHighestEnabled
        )
    }

    suspend fun getCreatorCardStats(mid: Long): Result<CreatorCardStats> =
        SubtitleAndAuxRepository.getCreatorCardStats(mid)

    fun getCachedCreatorCardStats(mid: Long): CreatorCardStats? =
        SubtitleAndAuxRepository.getCachedCreatorCardStats(mid)

    fun getCachedNavInfo(): NavData? = SubtitleAndAuxRepository.getCachedNavInfo()

    fun prefetchDetailAuxiliaryData(
        ownerMid: Long,
        scope: CoroutineScope = AppScope.ioScope
    ) {
        SubtitleAndAuxRepository.prefetchDetailAuxiliaryData(ownerMid = ownerMid, scope = scope)
    }

    // [修复] 添加 aid 参数支持，修复移动端推荐流视频播放失败问题
    suspend fun getVideoDetails(
        bvid: String,
        aid: Long = 0,
        requestedCid: Long = 0L,
        targetQuality: Int? = null,
        audioLang: String? = null
    ): Result<Pair<ViewInfo, PlayUrlData>> = PlaybackRepository.getVideoDetails(
        bvid = bvid,
        aid = aid,
        requestedCid = requestedCid,
        targetQuality = targetQuality ?: 80,
        audioLang = audioLang
    )

    // [新增] 获取 AI 视频总结
    suspend fun getAiSummary(bvid: String, cid: Long, upMid: Long): Result<AiSummaryResponse> =
        SubtitleAndAuxRepository.getAiSummary(bvid = bvid, cid = cid, upMid = upMid)

    suspend fun getPlayUrlData(bvid: String, cid: Long, qn: Int, audioLang: String? = null): PlayUrlData? {
        return PlayUrlResolver.getPlayUrlData(
            bvid = bvid,
            cid = cid,
            qn = qn,
            audioLang = audioLang
        )
    }

    fun init(context: android.content.Context) {
        PlaybackSessionManager.init(context)
    }

    suspend fun getPreviewVideoUrl(bvid: String, cid: Long): String? {
        return PlayUrlResolver.getPreviewVideoUrl(bvid = bvid, cid = cid)
    }

    /**
     * 获取视频预览图数据 (Videoshot API)
     * 
     * 用于进度条拖动时显示视频缩略图预览
     * @param bvid 视频 BV 号
     * @param cid 视频 CID
     * @return VideoshotData 或 null（如果获取失败）
     */
    suspend fun getVideoshot(bvid: String, cid: Long): VideoshotData? =
        SubtitleAndAuxRepository.getVideoshot(bvid = bvid, cid = cid)

    // [修复] 获取播放器信息 (BGM/ViewPoints/Etc) — WBI 签名
    suspend fun getPlayerInfo(bvid: String, cid: Long): Result<PlayerInfoData> =
        SubtitleAndAuxRepository.getPlayerInfo(bvid = bvid, cid = cid)

    suspend fun getOnlineCountText(bvid: String, cid: Long): String? =
        SubtitleAndAuxRepository.getOnlineCountText(bvid = bvid, cid = cid)

    suspend fun getSubtitleCues(
        subtitleUrl: String,
        bvid: String,
        cid: Long,
        subtitleId: Long = 0L,
        subtitleIdStr: String = "",
        subtitleLan: String = ""
    ): Result<List<SubtitleCue>> = SubtitleAndAuxRepository.getSubtitleCues(
        bvid = bvid,
        cid = cid,
        subtitleUrl = subtitleUrl,
        subtitleId = subtitleId,
        subtitleIdStr = subtitleIdStr,
        subtitleLan = subtitleLan
    )

    suspend fun getInteractEdgeInfo(
        bvid: String,
        graphVersion: Long,
        edgeId: Long? = null
    ): Result<InteractEdgeInfoData> = withContext(Dispatchers.IO) {
        try {
            val response = api.getInteractEdgeInfo(bvid = bvid, graphVersion = graphVersion, edgeId = edgeId)
            if (response.code == 0 && response.data != null) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message.ifBlank { "互动分支信息加载失败(${response.code})" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun cacheVideoPreview(video: VideoItem) {
        val cacheKey = video.bvid.trim()
        if (cacheKey.isBlank() || videoInfoCache.containsKey(cacheKey)) return
        videoPreviewCache[cacheKey] = video.toPreviewViewInfo()
    }

    fun cacheVideoPreview(video: RelatedVideo) {
        val cacheKey = video.bvid.trim()
        if (cacheKey.isBlank() || videoInfoCache.containsKey(cacheKey)) return
        videoPreviewCache[cacheKey] = video.toPreviewViewInfo()
    }

    fun getCachedDetailViewInfo(bvid: String): ViewInfo? {
        val cacheKey = bvid.trim()
        if (cacheKey.isBlank()) return null
        return videoInfoCache[cacheKey]?.data ?: videoPreviewCache[cacheKey]
    }

    fun getCachedRelatedVideos(bvid: String): List<RelatedVideo>? {
        val cacheKey = bvid.trim()
        if (cacheKey.isBlank()) return null
        return relatedVideosCache[cacheKey]
    }

    suspend fun getRelatedVideos(bvid: String): List<RelatedVideo> = withContext(Dispatchers.IO) {
        val cacheKey = bvid.trim()
        if (cacheKey.isBlank()) return@withContext emptyList()
        relatedVideosCache[cacheKey]?.let { return@withContext it }
        val request = relatedVideosInFlight.computeIfAbsent(cacheKey) {
            AppScope.ioScope.async { fetchAndCacheRelatedVideos(cacheKey) }
        }
        try {
            request.await()
        } finally {
            if (request.isCompleted) {
                relatedVideosInFlight.remove(cacheKey, request)
            }
        }
    }

    fun prefetchRelatedVideos(bvid: String, scope: CoroutineScope = AppScope.ioScope) {
        val cacheKey = bvid.trim()
        if (
            cacheKey.isBlank() ||
            relatedVideosCache.containsKey(cacheKey) ||
            relatedVideosInFlight.containsKey(cacheKey)
        ) {
            return
        }
        scope.launch {
            runCatching { getRelatedVideos(cacheKey) }
        }
    }

    suspend fun getVideoInfo(bvid: String): VideoDetailResponse = withContext(Dispatchers.IO) {
        val cacheKey = bvid.trim()
        if (cacheKey.isBlank()) {
            return@withContext VideoDetailResponse(code = -400, message = "请求参数错误")
        }
        videoInfoCache[cacheKey]?.let { return@withContext it }
        val request = videoInfoInFlight.computeIfAbsent(cacheKey) {
            AppScope.ioScope.async { api.getVideoInfo(cacheKey) }
        }
        val response = try {
            request.await()
        } finally {
            if (request.isCompleted) {
                videoInfoInFlight.remove(cacheKey, request)
            }
        }
        if (response.code == 0 && response.data != null && cacheKey.isNotBlank()) {
            videoInfoCache[cacheKey] = response
        }
        response
    }

    fun prefetchVideoInfo(bvid: String, scope: CoroutineScope = AppScope.ioScope) {
        val cacheKey = bvid.trim()
        if (cacheKey.isBlank() || videoInfoCache.containsKey(cacheKey) || videoInfoInFlight.containsKey(cacheKey)) return
        scope.launch {
            runCatching { getVideoInfo(cacheKey) }
        }
    }

    fun prefetchDetailSummary(video: VideoItem, scope: CoroutineScope = AppScope.ioScope) {
        val cacheKey = video.bvid.trim()
        if (cacheKey.isBlank()) return
        cacheVideoPreview(video)
        prefetchVideoInfo(cacheKey, scope)
    }

    fun prefetchDetailSummaries(videos: Iterable<VideoItem>, scope: CoroutineScope = AppScope.ioScope) {
        videos.asSequence()
            .filter { video -> video.bvid.isNotBlank() }
            .distinctBy { video -> video.bvid.trim() }
            .take(3)
            .forEach { video -> prefetchDetailSummary(video, scope) }
    }

    fun prefetchDetailLanding(video: VideoItem, scope: CoroutineScope = AppScope.ioScope) {
        val cacheKey = video.bvid.trim()
        if (cacheKey.isBlank()) return
        cacheVideoPreview(video)
        prefetchDetailAuxiliaryData(video.owner.mid, scope)
        prefetchVideoInfo(cacheKey, scope)
        prefetchRelatedVideos(cacheKey, scope)
    }

    fun prefetchDetailLanding(video: RelatedVideo, scope: CoroutineScope = AppScope.ioScope) {
        val cacheKey = video.bvid.trim()
        if (cacheKey.isBlank()) return
        cacheVideoPreview(video)
        prefetchDetailAuxiliaryData(video.owner.mid, scope)
        prefetchVideoInfo(cacheKey, scope)
        prefetchRelatedVideos(cacheKey, scope)
    }

    fun prefetchDetailLandings(videos: Iterable<VideoItem>, scope: CoroutineScope = AppScope.ioScope) {
        videos.asSequence()
            .filter { video -> video.bvid.isNotBlank() }
            .distinctBy { video -> video.bvid.trim() }
            .take(6)
            .forEach { video -> prefetchDetailLanding(video, scope) }
    }

    private fun VideoItem.toPreviewViewInfo(): ViewInfo {
        return ViewInfo(
            bvid = bvid,
            aid = aid,
            cid = cid,
            title = title,
            pic = pic,
            pubdate = pubdate,
            owner = owner,
            stat = stat,
            pages = previewPages(
                cid = cid,
                title = title,
                duration = duration
            )
        )
    }

    private fun RelatedVideo.toPreviewViewInfo(): ViewInfo {
        return ViewInfo(
            bvid = bvid,
            aid = aid,
            cid = cid,
            title = title,
            pic = pic,
            owner = owner,
            stat = stat,
            pages = previewPages(
                cid = cid,
                title = title,
                duration = duration
            )
        )
    }

    private fun previewPages(cid: Long, title: String, duration: Int): List<Page> {
        return if (cid > 0L || duration > 0) {
            listOf(
                Page(
                    cid = cid,
                    page = 1,
                    part = title,
                    duration = duration.toLong()
                )
            )
        } else {
            emptyList()
        }
    }

    private suspend fun fetchAndCacheRelatedVideos(cacheKey: String): List<RelatedVideo> {
        return try {
            val related = api.getRelatedVideos(cacheKey).data ?: emptyList()
            if (related.isNotEmpty()) {
                relatedVideosCache[cacheKey] = related
                related.forEach(::cacheVideoPreview)
            }
            related
        } catch (e: Exception) {
            emptyList()
        }
    }

}


