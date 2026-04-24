package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
interface ArticleApi {
    @GET("x/article/view")
    suspend fun getArticleView(
        @QueryMap params: Map<String, String>
    ): com.bbttvv.app.data.model.response.ArticleDetailResponse
}

//  [新增] 故事模式 (竖屏短视频) API
interface StoryApi {
    // 获取故事流 (竖屏短视频列表)
    @GET("x/v2/feed/index/story")
    suspend fun getStoryFeed(
        @Query("fnval") fnval: Int = 4048,         // 视频格式参数
        @Query("fnver") fnver: Int = 0,
        @Query("force_host") forceHost: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("qn") qn: Int = 32,                  // 画质
        @Query("ps") ps: Int = 20,                  // 每页数量
        @Query("aid") aid: Long = 0,                // 可选，从此视频开始
        @Query("bvid") bvid: String = ""            // 可选，从此视频开始
    ): StoryResponse
}
