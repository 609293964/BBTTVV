package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.core.util.safeApiCall
import com.bbttvv.app.core.util.safeApiCallOrNull
import com.bbttvv.app.data.model.response.SongInfoResponse
import com.bbttvv.app.data.model.response.SongLyricResponse
import com.bbttvv.app.data.model.response.SongStreamResponse
import com.bbttvv.app.data.model.response.getBestAudio

object AudioRepository {
    private const val TAG = "AudioRepo"
    
    suspend fun getSongInfo(sid: Long): SongInfoResponse {
        return safeApiCall(
            tag = TAG,
            errorMessage = { "getSongInfo failed: sid=$sid" }
        ) {
            NetworkModule.audioApi.getSongInfo(sid)
        }.getOrElse { e ->
            SongInfoResponse(code = -1, msg = e.message ?: "Unknown Error")
        }
    }

    suspend fun getSongStream(sid: Long): SongStreamResponse {
        return safeApiCall(
            tag = TAG,
            errorMessage = { "getSongStream failed: sid=$sid" }
        ) {
            NetworkModule.audioApi.getSongStream(sid)
        }.getOrElse { e ->
            SongStreamResponse(code = -1, msg = e.message ?: "Unknown Error")
        }
    }

    suspend fun getSongLyric(sid: Long): SongLyricResponse {
        return safeApiCall(
            tag = TAG,
            errorMessage = { "getSongLyric failed: sid=$sid" }
        ) {
            NetworkModule.audioApi.getSongLyric(sid)
        }.getOrElse { e ->
            SongLyricResponse(code = -1, msg = e.message ?: "Unknown Error")
        }
    }
    
    /**
     * 从视频的 DASH 流中提取音频 URL（用于 MA 格式音乐播放）
     * 
     * MA 格式的背景音乐没有独立的音频 API，但 jumpUrl 中包含关联视频的 aid 和 cid。
     * 我们可以通过获取该视频的 DASH 流，提取其最佳音频轨道来播放。
     * 
     * @param bvid 视频的 BV 号
     * @param cid 视频的分 P CID
     * @return 音频流 URL，如果获取失败则返回 null
     */
    suspend fun getAudioStreamFromVideo(bvid: String, cid: Long): String? {
        return safeApiCallOrNull(
            tag = TAG,
            errorMessage = { "getAudioStreamFromVideo failed: bvid=$bvid, cid=$cid" }
        ) {
            Logger.d(TAG, "🎵 getAudioStreamFromVideo: bvid=$bvid, cid=$cid")
            
            // 获取视频的 DASH 播放数据
            val playData = PlaybackRepository.getPlayUrlData(bvid, cid, 64)
            Logger.d(TAG, "🎵 PlayData: ${if (playData != null) "success" else "null"}")
            
            // 提取最佳音频流 URL
            val audioUrl = playData?.dash?.getBestAudio()?.getValidUrl()
            Logger.d(TAG, "🎵 AudioUrl: ${audioUrl?.take(50)}...")
            
            audioUrl
        }
    }
}


