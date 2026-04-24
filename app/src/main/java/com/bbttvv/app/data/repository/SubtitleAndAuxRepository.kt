package com.bbttvv.app.data.repository

import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.WbiUtils
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.SubtitleCue
import com.bbttvv.app.data.model.response.AiSummaryResponse
import com.bbttvv.app.data.model.response.NavData
import com.bbttvv.app.data.model.response.PlayerInfoData
import com.bbttvv.app.data.model.response.VideoshotData
import com.bbttvv.app.data.service.video.VideoCacheService
import com.bbttvv.app.data.service.video.VideoSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request

object SubtitleAndAuxRepository {
    private val api get() = VideoSessionService.api
    private val subtitleCueCache get() = VideoCacheService.subtitleCueCache
    private val creatorCardStatsCache get() = VideoCacheService.creatorCardStatsCache
    private var cachedNavInfoState: NavData?
        get() = VideoCacheService.cachedNavInfo
        set(value) {
            VideoCacheService.cachedNavInfo = value
        }

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
        VideoCacheService.invalidateAccountScopedCaches()
        PlaybackSessionManager.resetSessionDerivedState()
    }

    suspend fun ensureBuvid3() {
        PlaybackSessionManager.ensureBuvid3()
    }

    suspend fun getNavInfo(): Result<NavData> = withContext(Dispatchers.IO) {
        try {
            val resp = api.getNavInfo()
            if (resp.code == 0 && resp.data != null) {
                cachedNavInfoState = resp.data
                Result.success(resp.data)
            } else {
                if (resp.code == -101) {
                    val loggedOutNavData = NavData(isLogin = false)
                    cachedNavInfoState = loggedOutNavData
                    Result.success(loggedOutNavData)
                } else {
                    Result.failure(Exception("错误码: ${resp.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCachedNavInfo(): NavData? = cachedNavInfoState

    suspend fun getCreatorCardStats(mid: Long): Result<CreatorCardStats> {
        return withContext(Dispatchers.IO) {
            if (mid <= 0L) return@withContext Result.failure(IllegalArgumentException("Invalid mid"))
            creatorCardStatsCache[mid]?.let { cachedStats ->
                return@withContext Result.success(cachedStats)
            }
            try {
                val response = api.getUserCard(mid = mid, photo = false)
                val data = response.data
                if (response.code == 0 && data != null) {
                    val stats = CreatorCardStats(
                        followerCount = data.follower.coerceAtLeast(0),
                        videoCount = data.archive_count.coerceAtLeast(0)
                    )
                    creatorCardStatsCache[mid] = stats
                    Result.success(stats)
                } else {
                    Result.failure(Exception(response.message.ifBlank { "UP主信息加载失败(${response.code})" }))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getCachedCreatorCardStats(mid: Long): CreatorCardStats? {
        if (mid <= 0L) return null
        return creatorCardStatsCache[mid]
    }

    fun prefetchDetailAuxiliaryData(ownerMid: Long, scope: CoroutineScope = AppScope.ioScope) {
        val shouldFetchCreatorStats = ownerMid > 0L && !creatorCardStatsCache.containsKey(ownerMid)
        val shouldFetchNavInfo = cachedNavInfoState == null
        if (!shouldFetchCreatorStats && !shouldFetchNavInfo) return

        scope.launch {
            val creatorStatsTask = if (shouldFetchCreatorStats) {
                async { runCatching { getCreatorCardStats(ownerMid) } }
            } else {
                null
            }
            val navInfoTask = if (shouldFetchNavInfo) {
                async { runCatching { getNavInfo() } }
            } else {
                null
            }
            creatorStatsTask?.await()
            navInfoTask?.await()
        }
    }

    suspend fun getAiSummary(bvid: String, cid: Long, upMid: Long): Result<AiSummaryResponse> {
        return withContext(Dispatchers.IO) {
            PlaybackSessionManager.ensureBuvid3()
            logAiSummaryPreflight(
                bvid = bvid,
                cid = cid,
                upMid = upMid
            )

            var attempt = 1
            var lastError: Throwable? = null

            while (attempt <= 2) {
                try {
                    if (attempt > 1) {
                        PlaybackSessionManager.invalidateWbiKeys()
                        kotlinx.coroutines.delay(350L)
                    }

                    val (imgKey, subKey) = PlaybackSessionManager.getWbiKeys()
                    val params = buildAiSummaryParams(
                        bvid = bvid,
                        cid = cid,
                        upMid = upMid
                    )
                    val signedParams = WbiUtils.sign(params, imgKey, subKey)

                    com.bbttvv.app.core.util.Logger.d(
                        "SubtitleAndAuxRepo",
                        "🤖 AI Summary request: attempt=$attempt bvid=$bvid cid=$cid upMidPresent=${upMid > 0L}"
                    )
                    val response = api.getAiConclusion(signedParams)
                    val diagnosis = diagnoseAiSummaryResponse(response)
                    logAiSummaryResponse(
                        bvid = bvid,
                        cid = cid,
                        attempt = attempt,
                        diagnosis = diagnosis,
                        hasModelResult = response.data?.modelResult != null,
                        summaryLength = response.data?.modelResult?.summary?.length ?: 0,
                        outlineCount = response.data?.modelResult?.outline?.size ?: 0
                    )

                    return@withContext if (response.code == 0) {
                        Result.success(response)
                    } else {
                        Result.failure(Exception("AI Summary API error: code=${response.code}, msg=${response.message}"))
                    }
                } catch (e: Exception) {
                    lastError = e
                    val diagnosis = diagnoseAiSummaryFailure(e)
                    com.bbttvv.app.core.util.Logger.w(
                        "SubtitleAndAuxRepo",
                        "🤖 AI Summary request failed: attempt=$attempt bvid=$bvid cid=$cid status=${diagnosis.status} reason=${diagnosis.reason} retryable=${diagnosis.shouldRetryRequest}"
                    )
                    if (attempt == 1 && diagnosis.shouldRetryRequest) {
                        com.bbttvv.app.core.util.Logger.i(
                            "SubtitleAndAuxRepo",
                            "🤖 AI Summary retry scheduled: bvid=$bvid cid=$cid reason=${diagnosis.reason}"
                        )
                        attempt++
                        continue
                    }
                    return@withContext Result.failure(e)
                }
            }

            Result.failure(lastError ?: IllegalStateException("AI Summary unknown failure"))
        }
    }

    suspend fun getVideoshot(bvid: String, cid: Long): VideoshotData? {
        return withContext(Dispatchers.IO) {
            try {
                com.bbttvv.app.core.util.Logger.d("SubtitleAndAuxRepo", "🖼️ getVideoshot: bvid=$bvid, cid=$cid")
                val response = api.getVideoshot(bvid = bvid, cid = cid)
                if (response.code == 0 && response.data != null && response.data.isValid) {
                    com.bbttvv.app.core.util.Logger.d(
                        "SubtitleAndAuxRepo",
                        "🖼️ Videoshot success: ${response.data.image.size} images, ${response.data.index.size} frames"
                    )
                    response.data
                } else {
                    com.bbttvv.app.core.util.Logger.d("SubtitleAndAuxRepo", "🖼️ Videoshot failed: code=${response.code}")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w("SubtitleAndAuxRepo", "🖼️ Videoshot exception: ${e.message}")
                null
            }
        }
    }

    suspend fun getPlayerInfo(bvid: String, cid: Long): Result<PlayerInfoData> {
        return withContext(Dispatchers.IO) {
            try {
                val (imgKey, subKey) = PlaybackSessionManager.getWbiKeys()
                val params = mapOf(
                    "bvid" to bvid,
                    "cid" to cid.toString()
                )
                val signedParams = WbiUtils.sign(params, imgKey, subKey)
                val response = api.getPlayerInfo(signedParams)
                if (response.code == 0 && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception("PlayerInfo error: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getOnlineCountText(bvid: String, cid: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getOnlineCount(bvid = bvid, cid = cid)
                if (response.code != 0) {
                    return@withContext null
                }
                val raw = response.data?.total
                    ?.takeIf { it.isNotBlank() }
                    ?: response.data?.count?.takeIf { it.isNotBlank() }
                    ?: return@withContext null
                normalizeOnlineCountText(raw)
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun getSubtitleCues(
        bvid: String,
        cid: Long,
        subtitleUrl: String,
        subtitleId: Long = 0L,
        subtitleIdStr: String = "",
        subtitleLan: String = ""
    ): Result<List<SubtitleCue>> {
        return withContext(Dispatchers.IO) {
            try {
                if (bvid.isBlank() || cid <= 0L) {
                    return@withContext Result.failure(
                        IllegalArgumentException("字幕归属视频信息缺失: bvid=$bvid cid=$cid")
                    )
                }
                val normalizedUrl = com.bbttvv.app.core.util.normalizeBilibiliSubtitleUrl(subtitleUrl)
                if (normalizedUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("字幕 URL 为空"))
                }

                val cacheKey = buildSubtitleCueCacheKey(
                    bvid = bvid,
                    cid = cid,
                    subtitleId = subtitleId,
                    subtitleIdStr = subtitleIdStr,
                    subtitleLan = subtitleLan,
                    normalizedSubtitleUrl = normalizedUrl
                )
                subtitleCueCache[cacheKey]?.let { cached ->
                    return@withContext Result.success(cached)
                }

                val request = Request.Builder()
                    .url(normalizedUrl)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .get()
                    .header("Referer", "https://www.bilibili.com")
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .build()

                val response = NetworkModule.okHttpClient.newCall(request).execute()
                response.use { call ->
                    if (!call.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("字幕请求失败: HTTP ${call.code}")
                        )
                    }
                    val rawJson = call.body?.string().orEmpty()
                    val cues = com.bbttvv.app.core.util.parseBiliSubtitleBody(rawJson)
                    subtitleCueCache[cacheKey] = cues
                    Result.success(cues)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun buildAiSummaryParams(
        bvid: String,
        cid: Long,
        upMid: Long
    ): Map<String, String> {
        val params = linkedMapOf(
            "bvid" to bvid,
            "cid" to cid.toString()
        )
        if (upMid > 0L) {
            params["up_mid"] = upMid.toString()
        }
        return params
    }

    private fun logAiSummaryPreflight(
        bvid: String,
        cid: Long,
        upMid: Long
    ) {
        val hasSess = !TokenManager.sessDataCache.isNullOrEmpty()
        val hasCsrf = !TokenManager.csrfCache.isNullOrEmpty()
        val hasBuvid = !TokenManager.buvid3Cache.isNullOrEmpty()
        val hasAccessToken = !TokenManager.accessTokenCache.isNullOrEmpty()
        com.bbttvv.app.core.util.Logger.i(
            "SubtitleAndAuxRepo",
            "🤖 AI Summary preflight: bvid=$bvid cid=$cid upMidPresent=${upMid > 0L} hasSess=$hasSess hasCsrf=$hasCsrf hasBuvid=$hasBuvid hasAccessToken=$hasAccessToken buvidInitialized=${PlaybackSessionManager.isBuvidInitialized}"
        )
    }

    private fun logAiSummaryResponse(
        bvid: String,
        cid: Long,
        attempt: Int,
        diagnosis: AiSummaryFetchDiagnosis,
        hasModelResult: Boolean,
        summaryLength: Int,
        outlineCount: Int
    ) {
        com.bbttvv.app.core.util.Logger.i(
            "SubtitleAndAuxRepo",
            "🤖 AI Summary response: attempt=$attempt bvid=$bvid cid=$cid status=${diagnosis.status} reason=${diagnosis.reason} rootCode=${diagnosis.rootCode} dataCode=${diagnosis.dataCode} stid=${diagnosis.stid ?: ""} hasModelResult=$hasModelResult summaryLength=$summaryLength outlineCount=$outlineCount retryLater=${diagnosis.shouldRetryLater}"
        )
    }

    private fun normalizeOnlineCountText(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        return when {
            value.contains("正在观看") -> value
            value.contains("正在看") -> value.replace("正在看", "正在观看")
            value.contains("在线") -> value.replace("在线", "正在观看")
            value.all { it.isDigit() } -> "${value}人正在观看"
            else -> "${value}人正在观看"
        }
    }
}
