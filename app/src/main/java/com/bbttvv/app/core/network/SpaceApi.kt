package com.bbttvv.app.core.network

import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap
interface SpaceApi {
    @GET("https://app.bilibili.com/x/v2/space")
    suspend fun getSpaceAggregate(
        @Query("vmid") mid: Long,
        @Query("build") build: Int = 8430300,
        @Query("version") version: String = "8.43.0",
        @Query("c_locale") cLocale: String = "zh_CN",
        @Query("channel") channel: String = "master",
        @Query("mobi_app") mobiApp: String = "android",
        @Query("platform") platform: String = "android",
        @Query("s_locale") sLocale: String = "zh_CN"
    ): com.bbttvv.app.data.model.response.SpaceAggregateResponse

    // 获取用户详细信息 (需要 WBI 签名)
    @GET("x/space/wbi/acc/info")
    suspend fun getSpaceInfo(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.SpaceInfoResponse

    // 获取用户投稿视频列表 (需要 WBI 签名)
    @GET("x/space/wbi/arc/search")
    suspend fun getSpaceVideos(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.SpaceVideoResponse
}
