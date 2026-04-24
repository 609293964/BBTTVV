package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
interface BangumiApi {
    // 番剧时间表
    @GET("pgc/web/timeline")
    suspend fun getTimeline(
        @Query("types") types: Int,      // 1=番剧 4=国创
        @Query("before") before: Int = 3,
        @Query("after") after: Int = 7
    ): com.bbttvv.app.data.model.response.BangumiTimelineResponse
    
    // 番剧索引/筛选 -  需要 st 参数（与 season_type 相同值）
    @GET("pgc/season/index/result")
    suspend fun getBangumiIndex(
        @Query("season_type") seasonType: Int,   // 1=番剧 2=电影 3=纪录片 4=国创 5=电视剧 7=综艺
        @Query("st") st: Int,                    //  [修复] 必需参数，与 season_type 相同
        @Query("page") page: Int = 1,
        @Query("pagesize") pageSize: Int = 20,
        @Query("order") order: Int = 2,          // 2=播放量排序（默认更热门）
        @Query("season_version") seasonVersion: Int = -1,  // -1=全部
        @Query("spoken_language_type") spokenLanguageType: Int = -1,  // -1=全部
        @Query("area") area: Int = -1,           // -1=全部地区
        @Query("is_finish") isFinish: Int = -1,  // -1=全部
        @Query("copyright") copyright: Int = -1, // -1=全部
        @Query("season_status") seasonStatus: Int = -1,  // -1=全部
        @Query("season_month") seasonMonth: Int = -1,    // -1=全部
        @Query("year") year: String = "-1",      // -1=全部
        @Query("release_date") releaseDate: String = "-1", // -1=全部
        @Query("style_id") styleId: Int = -1,    // -1=全部
        @Query("sort") sort: Int = 0,
        @Query("type") type: Int = 1
    ): com.bbttvv.app.data.model.response.BangumiIndexResponse
    
    // 番剧详情 -  返回 ResponseBody 自行解析，防止 OOM
    @GET("pgc/view/web/season")
    suspend fun getSeasonDetail(
        @Query("season_id") seasonId: Long? = null,
        @Query("ep_id") epId: Long? = null
    ): ResponseBody
    
    // 番剧播放地址 - PiliPlus parity path
    @GET(BANGUMI_PLAY_URL_PATH)
    suspend fun getBangumiPlayUrl(
        @QueryMap params: Map<String, String>
    ): ResponseBody
    
    // 追番/追剧
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("pgc/web/follow/add")
    suspend fun followBangumi(
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): com.bbttvv.app.data.model.response.SimpleApiResponse
    
    // 取消追番/追剧
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("pgc/web/follow/del")
    suspend fun unfollowBangumi(
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): com.bbttvv.app.data.model.response.SimpleApiResponse
    
    //  [新增] 我的追番列表
    @GET("x/space/bangumi/follow/list")
    suspend fun getMyFollowBangumi(
        @Query("vmid") vmid: Long,          // 用户 mid (登录用户的 mid)
        @Query("type") type: Int = 1,        // 1=追番 2=追剧
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30
    ): com.bbttvv.app.data.model.response.MyFollowBangumiResponse
}
