// 文件路径: data/repository/StoryRepository.kt
package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.data.model.response.StoryItem
import com.bbttvv.app.data.model.response.getBestAudio
import com.bbttvv.app.core.util.Logger

/**
 * Story 模式播放 URL 数据类
 * @param videoUrl 视频流 URL
 * @param audioUrl 音频流 URL（DASH 格式需要，durl 格式为 null）
 */
data class StoryPlayUrls(
    val videoUrl: String,
    val audioUrl: String?
)

/**
 * 故事模式 (竖屏短视频) 数据仓库
 */
object StoryRepository {
    
    private const val TAG = "StoryRepository"
    
    /**
     * 获取故事流视频列表
     * @param aid 可选，从指定视频开始加载
     * @param pageSize 每页数量
     */
    suspend fun getStoryFeed(
        aid: Long = 0,
        bvid: String = "",
        pageSize: Int = 20
    ): Result<List<StoryItem>> {
        return try {
            val response = NetworkModule.storyApi.getStoryFeed(
                ps = pageSize,
                aid = aid,
                bvid = bvid
            )
            
            if (response.code == 0 && response.data != null) {
                val items = response.data.items ?: emptyList()
                Logger.d(TAG, " 获取故事流成功: ${items.size} 条视频")
                Result.success(items)
            } else {
                Logger.e(TAG, " 获取故事流失败: code=${response.code}, msg=${response.message}")
                Result.failure(Exception("获取失败: ${response.message}"))
            }
        } catch (e: Exception) {
            Logger.e(TAG, " 获取故事流异常", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取视频播放 URL (通过 bvid)
     * @param bvid 视频 BV 号
     * @param cid 视频 CID
     */
    suspend fun getVideoPlayUrl(bvid: String, cid: Long): String? {
        if (bvid.isEmpty()) {
            Logger.e(TAG, " bvid 为空，无法获取播放 URL")
            return null
        }
        return try {
            // 复用 VideoRepository 的播放 URL 获取逻辑
            val playData = PlaybackRepository.getPlayUrlData(bvid, cid, 80)
            extractPlayUrls(playData)?.videoUrl
        } catch (e: Exception) {
            Logger.e(TAG, " 获取播放 URL 异常", e)
            null
        }
    }
    
    /**
     * 获取视频和音频播放 URL (通过 aid) - 用于 Story 模式
     * @param aid 视频 AV 号
     * @param cid 视频 CID
     * @return StoryPlayUrls 包含视频和音频 URL，如果失败返回 null
     */
    suspend fun getVideoPlayUrlByAid(aid: Long, cid: Long): StoryPlayUrls? {
        if (aid <= 0 || cid <= 0) {
            Logger.e(TAG, " aid=$aid 或 cid=$cid 无效")
            return null
        }
        return try {
            Logger.d(TAG, " 获取播放 URL: aid=$aid, cid=$cid")
            
            // 使用 Legacy API 通过 aid 获取播放地址
            val response = NetworkModule.api.getPlayUrlByAid(aid = aid, cid = cid)
            
            if (response.code == 0 && response.data != null) {
                val urls = extractPlayUrls(response.data)
                if (urls != null) {
                    Logger.d(TAG, " 获取播放 URL 成功: video=${urls.videoUrl.take(50)}..., hasAudio=${urls.audioUrl != null}")
                    return urls
                }
            }
            
            Logger.e(TAG, " 获取播放 URL 失败: code=${response.code}")
            null
        } catch (e: Exception) {
            Logger.e(TAG, " 获取播放 URL 异常", e)
            null
        }
    }
    
    /**
     * 从 PlayUrlData 中提取视频和音频播放地址
     */
    private fun extractPlayUrls(playData: com.bbttvv.app.data.model.response.PlayUrlData?): StoryPlayUrls? {
        if (playData == null) return null
        
        // 优先取 durl (MP4) - 音视频合一，无需单独音频流
        val durlUrl = playData.durl?.firstOrNull()?.url
        if (!durlUrl.isNullOrEmpty()) {
            Logger.d(TAG, " durl URL (音视频合一): ${durlUrl.take(50)}...")
            return StoryPlayUrls(videoUrl = durlUrl, audioUrl = null)
        }
        
        // 降级到 DASH 格式 - 需要分别获取视频和音频流
        val dash = playData.dash
        if (dash != null) {
            val videoUrl = dash.video.firstOrNull()?.baseUrl
            val audioUrl = dash.getBestAudio()?.getValidUrl()
            
            if (!videoUrl.isNullOrEmpty()) {
                Logger.d(TAG, " DASH video URL: ${videoUrl.take(50)}...")
                if (!audioUrl.isNullOrEmpty()) {
                    Logger.d(TAG, " DASH audio URL: ${audioUrl.take(50)}...")
                } else {
                    Logger.w(TAG, "⚠️ DASH 格式但无音频流")
                }
                return StoryPlayUrls(videoUrl = videoUrl, audioUrl = audioUrl)
            }
        }
        
        Logger.e(TAG, " 无法获取播放 URL")
        return null
    }
}

