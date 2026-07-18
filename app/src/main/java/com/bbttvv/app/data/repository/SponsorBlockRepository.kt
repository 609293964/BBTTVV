// 文件路径: data/repository/SponsorBlockRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.SponsorCategory
import com.bbttvv.app.data.model.response.SponsorActionType
import com.bbttvv.app.data.model.response.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

internal fun buildSponsorBlockHttpClient(baseClient: OkHttpClient): OkHttpClient {
    return baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
}

/**
 * 空降助手 (BilibiliSponsorBlock) 数据仓库
 * API 文档: https://github.com/hanydd/BilibiliSponsorBlock/wiki/API
 */
object SponsorBlockRepository {
    
    private const val BASE_URL = "https://bsbsb.top/api"
    private const val TAG = "SponsorBlock"
    
    private val client = buildSponsorBlockHttpClient(NetworkModule.okHttpClient)
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    /**
     * 获取视频的空降片段
     * @param bvid 视频 BV 号
     * @param cid 当前分 P 的 CID
     * @param categories 要获取的片段类别
     * @return 片段列表，失败返回空列表
     */
    suspend fun getSegments(
        bvid: String,
        cid: Long,
        categories: List<String> = SponsorCategory.PLAYBACK_CATEGORIES,
        actionTypes: List<String> = SponsorActionType.PLAYBACK_ACTION_TYPES,
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        try {
            val url = buildSponsorBlockSegmentsUrl(
                bvid = bvid,
                cid = cid,
                categories = categories,
                actionTypes = actionTypes,
            )
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BBTTVV/2.4.1")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val body = response.body.string()
                        val segments = json.decodeFromString<List<SponsorSegment>>(body)
                        val currentCidSegments = filterSponsorSegmentsForCid(segments, cid)
                        Logger.d(TAG) {
                            "获取到 ${currentCidSegments.size} 个空降片段 for $bvid cid=$cid"
                        }
                        currentCidSegments
                    }
                    404 -> {
                        Logger.d(TAG) { "视频 $bvid cid=$cid 没有空降数据" }
                        emptyList()
                    }
                    else -> {
                        android.util.Log.w(TAG, "API 返回错误: ${response.code}")
                        emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "获取空降片段失败: ${e.message}")
            emptyList()
        }
    }

    suspend fun reportViewedSegment(segmentUuid: String): Boolean = withContext(Dispatchers.IO) {
        if (segmentUuid.isBlank()) return@withContext false
        runCatching {
            val url = "$BASE_URL/viewedVideoSponsorTime".toHttpUrl().newBuilder()
                .addQueryParameter("UUID", segmentUuid)
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BBTTVV/2.4.1")
                .post(ByteArray(0).toRequestBody())
                .build()
            client.newCall(request).execute().use { response -> response.isSuccessful }
        }.onFailure { error ->
            Logger.w(TAG, "空降片段跳过次数上报失败: ${error.message}")
        }.getOrDefault(false)
    }
    
    /**
     * 检查当前播放位置是否在某个空降片段内
     * @param segments 片段列表
     * @param currentPositionMs 当前播放位置（毫秒）
     * @return 匹配的片段，没有则返回 null
     */
    fun findSegmentAtPosition(
        segments: List<SponsorSegment>,
        currentPositionMs: Long
    ): SponsorSegment? {
        val currentSeconds = currentPositionMs / 1000f
        return segments.find { segment ->
            currentSeconds >= segment.startTime && currentSeconds < segment.endTime - 0.5f
        }
    }
    
    /**
     * 获取下一个即将到来的空降片段
     * @param segments 片段列表
     * @param currentPositionMs 当前播放位置（毫秒）
     * @param lookAheadMs 提前多少毫秒提示
     * @return 即将到来的片段，没有则返回 null
     */
    fun findUpcomingSegment(
        segments: List<SponsorSegment>,
        currentPositionMs: Long,
        lookAheadMs: Long = 2000
    ): SponsorSegment? {
        val currentSeconds = currentPositionMs / 1000f
        val lookAheadSeconds = lookAheadMs / 1000f
        
        return segments.find { segment ->
            val timeToStart = segment.startTime - currentSeconds
            timeToStart > 0 && timeToStart <= lookAheadSeconds
        }
    }
}

internal fun buildSponsorBlockSegmentsUrl(
    bvid: String,
    cid: Long,
    categories: List<String>,
    actionTypes: List<String>,
): HttpUrl {
    return "https://bsbsb.top/api/skipSegments".toHttpUrl().newBuilder()
        .addQueryParameter("videoID", bvid.trim())
        .apply {
            if (cid > 0L) addQueryParameter("cid", cid.toString())
            categories.distinct().forEach { category -> addQueryParameter("category", category) }
            actionTypes.distinct().forEach { actionType -> addQueryParameter("actionType", actionType) }
        }
        .build()
}

internal fun filterSponsorSegmentsForCid(
    segments: List<SponsorSegment>,
    cid: Long,
): List<SponsorSegment> {
    if (cid <= 0L) return segments
    return segments.filter { segment -> segment.cid <= 0L || segment.cid == cid }
}
