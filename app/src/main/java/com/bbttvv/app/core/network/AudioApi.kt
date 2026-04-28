package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
interface AudioApi {
    // 🎵 获取音频基本信息
    @GET("audio/music-service-c/web/song/info")
    suspend fun getSongInfo(
        @Query("sid") sid: Long
    ): com.bbttvv.app.data.model.response.SongInfoResponse

    // 🎵 获取音频流地址
    @GET("audio/music-service-c/web/url")
    suspend fun getSongStream(
        @Query("sid") sid: Long,
        @Query("privilege") privilege: Int = 2,
        @Query("quality") quality: Int = 2
    ): com.bbttvv.app.data.model.response.SongStreamResponse

    // 🎵 获取歌词
    @GET("audio/music-service-c/web/song/lyric")
    suspend fun getSongLyric(
        @Query("sid") sid: Long
    ): com.bbttvv.app.data.model.response.SongLyricResponse
}

// ==================== 私信 API (api.vc.bilibili.com) ====================
