package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
interface SearchApi {
    @GET("x/web-interface/wbi/search/default")
    suspend fun getDefaultSearch(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.SearchDefaultResponse

    @GET("x/web-interface/search/default")
    suspend fun getDefaultSearchLegacy(): com.bbttvv.app.data.model.response.SearchDefaultResponse

    @GET("x/web-interface/search/square")
    suspend fun getHotSearch(@Query("limit") limit: Int = 10): HotSearchResponse

    //  综合搜索 (不支持排序)
    @GET("x/web-interface/search/all/v2")
    suspend fun searchAll(@QueryMap params: Map<String, String>): SearchResponse
    
    //  [修复] 分类搜索 - 支持排序和时长筛选
    @GET("x/web-interface/wbi/search/type")
    suspend fun search(@QueryMap params: Map<String, String>): SearchTypeResponse
    
    //  [新增] UP主搜索 - 专用解析
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchUp(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.SearchUpResponse
    
    //  [新增] 番剧搜索 - search_type=media_bangumi
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchBangumi(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.BangumiSearchResponse

    //  [新增] 影视搜索 - search_type=media_ft
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchMediaFt(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.BangumiSearchResponse
    
    //  [新增] 直播搜索 - search_type=live_room
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchLive(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.LiveRoomSearchResponse

    //  [新增] 专栏搜索 - search_type=article
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchArticle(@QueryMap params: Map<String, String>): com.bbttvv.app.data.model.response.SearchArticleResponse
    
    //  搜索建议/联想
    @GET("https://s.search.bilibili.com/main/suggest")
    suspend fun getSearchSuggest(
        @Query("term") term: String,
        @Query("main_ver") mainVer: String = "v1",
        @Query("highlight") highlight: Int = 0
    ): SearchSuggestResponse
}
