// 文件路径: data/repository/SponsorBlockRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.data.model.response.SponsorCategory
import com.bbttvv.app.data.model.response.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
     * @param categories 要获取的片段类别，默认获取所有跳过类别
     * @return 片段列表，失败返回空列表
     */
    suspend fun getSegments(
        bvid: String,
        categories: List<String> = SponsorCategory.ALL_SKIP_CATEGORIES
    ): List<SponsorSegment> = withContext(Dispatchers.IO) {
        try {
            // 构建 URL，添加类别参数
            val categoryParams = categories.joinToString("&") { "category=$it" }
            val url = "$BASE_URL/skipSegments?videoID=$bvid&$categoryParams"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "BiliPai/2.4.1")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            when (response.code) {
                200 -> {
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val segments = json.decodeFromString<List<SponsorSegment>>(body)
                    android.util.Log.d(TAG, "获取到 ${segments.size} 个空降片段 for $bvid")
                    segments.filter { it.isSkipType } // 只返回跳过类型的片段
                }
                404 -> {
                    // 没有空降数据，这是正常情况
                    android.util.Log.d(TAG, "视频 $bvid 没有空降数据")
                    emptyList()
                }
                else -> {
                    android.util.Log.w(TAG, "API 返回错误: ${response.code}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "获取空降片段失败: ${e.message}")
            emptyList()
        }
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

