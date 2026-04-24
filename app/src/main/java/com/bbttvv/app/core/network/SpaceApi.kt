package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
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

    @GET("x/space/wbi/acc/info")
    suspend fun getSpaceInfoRaw(@QueryMap params: Map<String, String>): okhttp3.ResponseBody
    
    // 获取用户投稿视频列表 (需要 WBI 签名)
    @GET("x/space/wbi/arc/search")
    suspend fun getSpaceVideos(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.SpaceVideoResponse
    
    // 获取关注/粉丝数
    @GET("x/relation/stat")
    suspend fun getRelationStat(@Query("vmid") mid: Long): com.bbttvv.app.data.model.response.RelationStatResponse
    
    // 获取UP主播放量/获赞数
    @GET("x/space/upstat")
    suspend fun getUpStat(@Query("mid") mid: Long): com.bbttvv.app.data.model.response.UpStatResponse
    
    //  获取合集和系列列表
    @GET("x/polymer/web-space/seasons_series_list")
    suspend fun getSeasonsSeriesList(
        @Query("mid") mid: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): com.bbttvv.app.data.model.response.SeasonsSeriesListResponse
    
    //  获取合集内的视频列表
    @GET("x/polymer/web-space/seasons_archives_list")
    suspend fun getSeasonArchives(
        @Query("mid") mid: Long,
        @Query("season_id") seasonId: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("sort_reverse") sortReverse: Boolean = false
    ): com.bbttvv.app.data.model.response.SeasonArchivesResponse
    
    //  获取系列内的视频列表
    @GET("x/series/archives")
    suspend fun getSeriesArchives(
        @Query("mid") mid: Long,
        @Query("series_id") seriesId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("sort") sort: String = "desc"
    ): com.bbttvv.app.data.model.response.SeriesArchivesResponse
    
    //  置顶视频
    @GET("x/space/top/arc")
    suspend fun getTopArc(
        @Query("vmid") vmid: Long
    ): com.bbttvv.app.data.model.response.SpaceTopArcResponse
    
    //  个人公告
    @GET("x/space/notice")
    suspend fun getNotice(
        @Query("mid") mid: Long
    ): com.bbttvv.app.data.model.response.SpaceNoticeResponse
    
    //  用户动态 (需要登录 Cookie)
    @GET("x/polymer/web-dynamic/v1/feed/space")
    suspend fun getSpaceDynamic(
        @Query("host_mid") hostMid: Long,
        @Query("offset") offset: String = "",
        @Query("timezone_offset") timezoneOffset: Int = -480
    ): com.bbttvv.app.data.model.response.SpaceDynamicResponse
    
    //  [New] Get User Audio List
    @GET("https://api.bilibili.com/audio/music-service/web/song/upper")
    suspend fun getSpaceAudioList(
        @Query("uid") uid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("order") order: Int = 1,  // 1=latest, 2=hot, 3=duration
        @Query("jsonp") jsonp: String = "jsonp"
    ): com.bbttvv.app.data.model.response.SpaceAudioResponse

    //  [New] Get User Article List
    @GET("https://api.bilibili.com/x/article/up/lists")
    suspend fun getSpaceArticleList(
        @Query("mid") mid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("sort") sort: String = "publish_time",  // publish_time, view, fav
        @Query("jsonp") jsonp: String = "jsonp"
    ): com.bbttvv.app.data.model.response.SpaceArticleResponse
}

//  [新增] 番剧/影视 API
