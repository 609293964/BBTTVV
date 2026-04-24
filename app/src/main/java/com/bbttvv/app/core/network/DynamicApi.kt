package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

@kotlinx.serialization.Serializable
data class DynamicThumbRequest(
    val dyn_id_str: String,
    val up: Int,
    val spmid: String = "333.1369.0.0",
    val from_spmid: String = "333.999.0.0"
)

private const val DYNAMIC_FEED_FEATURES =
    "itemOpusStyle,listOnlyfans"

private const val DYNAMIC_DETAIL_FEATURES =
    "itemOpusStyle,listOnlyfans,opusBigCover,commentsNewVersion,onlyfansVote,onlyfansAssetsV2,decorationCard,forwardListHidden,ugcDelete"

interface DynamicApi {
    //  添加 features 参数以获取 rich_text_nodes 表情数据
    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun getDynamicFeed(
        @Query("type") type: String = "all",
        @Query("offset") offset: String = "",
        @Query("page") page: Int = 1,
        @Query("features") features: String = DYNAMIC_FEED_FEATURES
    ): DynamicFeedResponse
    
    //  [新增] 获取指定用户的动态列表
    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun getUserDynamicFeed(
        @QueryMap params: Map<String, String>
    ): DynamicFeedResponse

    //  [新增] 获取单条动态详情（桌面端详情接口）
    @GET("x/polymer/web-dynamic/desktop/v1/detail")
    suspend fun getDynamicDetail(
        @Query("id") id: String,
        @Query("features") features: String = DYNAMIC_DETAIL_FEATURES,
        @Query("timezone_offset") timezoneOffset: Int = -480
    ): DynamicDetailResponse

    //  [降级] 旧版详情接口，某些动态类型在 desktop 接口会返回不支持
    @GET("x/polymer/web-dynamic/v1/detail")
    suspend fun getDynamicDetailFallback(
        @Query("id") id: String,
        @Query("features") features: String = DYNAMIC_DETAIL_FEATURES
    ): DynamicDetailResponse
    
    //  [新增] 获取动态评论列表 (type=17 表示动态)
    @GET("x/v2/reply")
    suspend fun getDynamicReplies(
        @Query("oid") oid: Long,       // 动态 id_str (转为 Long)
        @Query("type") type: Int = 17, // 17 = 动态评论区
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("sort") sort: Int = 0   // 0=按时间, 1=按点赞
    ): ReplyResponse
    
    //  [新增] 发表动态评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/add")
    suspend fun addDynamicReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 17,
        @retrofit2.http.Field("message") message: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [修复] 点赞动态 - 使用新版 API
    @retrofit2.http.POST("x/dynamic/feed/dyn/thumb")
    suspend fun likeDynamic(
        @Query("csrf") csrf: String,
        @Query("csrf_token") csrfToken: String = csrf,
        @retrofit2.http.Body body: DynamicThumbRequest
    ): SimpleApiResponse
    
    //  [新增] 转发动态
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/dynamic/feed/create/dyn")
    suspend fun repostDynamic(
        @retrofit2.http.Field("dyn_id_str") dynIdStr: String,
        @retrofit2.http.Field("dyn_type") dynType: Int = 1,
        @retrofit2.http.Field("content") content: String = "",
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
}

//  [新增] UP主空间 API
