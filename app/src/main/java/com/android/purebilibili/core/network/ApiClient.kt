// 文件路径: core/network/ApiClient.kt
package com.android.purebilibili.core.network

import android.content.Context
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.network.policy.resolveHardcodedDnsFallback
import com.android.purebilibili.core.network.policy.shouldEnableTrustAllCertificates
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.data.model.response.*
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Bilibili 主 API 接口
 * 
 * 功能模块分区:
 * - 用户信息 (L30-45): getNavInfo, getNavStat, getHistoryList, getFavFolders, getFavoriteList
 * - 推荐/热门 (L50-70): getRecommendParams, getPopularVideos, getRegionVideos
 * - 直播 (L75-140): getLiveList, getFollowedLive, getLivePlayUrl 等
 * - 视频播放 (L145-185): getVideoInfo, getPlayUrl, getDanmakuXml 等
 * - 评论 (L195-225): getReplyList, getEmotes, getReplyReply
 * - 用户交互 (L230-295): 点赞/投币/收藏/关注 等
 * - 稍后再看 (L300-320): getWatchLaterList, addToWatchLater, deleteFromWatchLater
 */
interface BilibiliApi {
    // ==================== 用户信息模块 ====================
    @GET("x/web-interface/nav")
    suspend fun getNavInfo(): NavResponse

    @GET("x/web-interface/nav/stat")
    suspend fun getNavStat(): NavStatResponse

    //  [New] 获取用户卡片信息 (轻量级用户信息)
    @GET("x/web-interface/card")
    suspend fun getUserCard(
        @Query("mid") mid: Long,
        @Query("photo") photo: Boolean = true
    ): UserCardResponse

    @GET("x/web-interface/card")
    suspend fun getUserCardRaw(
        @Query("mid") mid: Long,
        @Query("photo") photo: Boolean = true
    ): okhttp3.ResponseBody

    @GET("x/web-interface/history/cursor")
    suspend fun getHistoryList(
        @Query("ps") ps: Int = 30,
        @Query("max") max: Long? = null,            //  游标: 上一页最后一条的 oid
        @Query("view_at") viewAt: Long? = null,     //  游标: 上一页最后一条的 view_at
        @Query("business") business: String? = null //  null=省略该参数
    ): HistoryResponse

    // [新增] 删除单条历史记录
    @retrofit2.http.FormUrlEncoded
    @POST("x/v2/history/delete")
    suspend fun deleteHistoryItem(
        @retrofit2.http.Field("kid") kid: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @GET("x/v3/fav/folder/created/list-all")
    suspend fun getFavFolders(
        @Query("up_mid") mid: Long,
        @Query("type") type: Int? = null,
        @Query("rid") rid: Long? = null
    ): FavFolderResponse

    @GET("x/v3/fav/folder/collected/list")
    suspend fun getCollectedFavFolders(
        @Query("up_mid") mid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("platform") platform: String = "web"
    ): FavFolderResponse

    // [新增] 创建收藏夹
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/folder/add")
    suspend fun createFavFolder(
        @retrofit2.http.Field("title") title: String,
        @retrofit2.http.Field("intro") intro: String = "",
        @retrofit2.http.Field("privacy") privacy: Int = 0, // 0:公开, 1:私密
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @GET("x/v3/fav/resource/list")
    suspend fun getFavoriteList(
        @Query("media_id") mediaId: Long, 
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("keyword") keyword: String? = null,
        @Query("platform") platform: String = "web"
    ): FavoriteResourceResponse

    // [新增] 批量删除收藏资源 (取消收藏)
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/resource/batch-del")
    suspend fun batchDelFavResource(
        @retrofit2.http.Field("media_id") mediaId: Long,
        @retrofit2.http.Field("resources") resources: String, // 格式: oid:type (e.g. "123456:2")
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    // ==================== 推荐/热门模块 ====================
    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun getRecommendParams(@QueryMap params: Map<String, String>): RecommendResponse
    
    //  移动端推荐流 API (需要 access_token + appkey 签名)
    @GET("https://app.bilibili.com/x/v2/feed/index")
    suspend fun getMobileFeed(@QueryMap params: Map<String, String>): MobileFeedResponse
    
    @GET("x/web-interface/popular")
    suspend fun getPopularVideos(
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20
    ): PopularResponse  //  使用专用响应类型

    @GET("x/web-interface/ranking/v2")
    suspend fun getRankingVideos(
        @Query("rid") rid: Int = 0,
        @Query("type") type: String = "all"
    ): RankingResponse

    @GET("x/web-interface/popular/precious")
    suspend fun getPopularPreciousVideos(): PopularPreciousResponse

    @GET("x/web-interface/popular/series/list")
    suspend fun getWeeklySeriesList(): PopularSeriesListResponse

    @GET("x/web-interface/popular/series/one")
    suspend fun getWeeklySeriesVideos(
        @Query("number") number: Int
    ): PopularSeriesOneResponse
    
    //  [修复] 分区视频 - 使用 dynamic/region API 返回完整 stat（包含播放量）
    // 原 newlist API 不返回 stat 数据
    @GET("x/web-interface/dynamic/region")
    suspend fun getRegionVideos(
        @Query("rid") rid: Int,    // 分区 ID (如 4=游戏, 36=知识, 188=科技)
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30
    ): DynamicRegionResponse
    
    // ==================== 直播模块 ====================
    // 直播列表 - 使用 v3 API (经测试确认可用)
    @GET("https://api.live.bilibili.com/room/v3/area/getRoomList")
    suspend fun getLiveList(
        @Query("parent_area_id") parentAreaId: Int = 0,  // 0=全站
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("sort_type") sortType: String = "online"  // 按人气排序
    ): LiveResponse
    
    //  [新增] 获取关注的直播 - 需要登录
    @GET("https://api.live.bilibili.com/xlive/web-ucenter/user/following")
    suspend fun getFollowedLive(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30
    ): FollowedLiveResponse
    
    //  [新增] 获取直播分区列表
    @GET("https://api.live.bilibili.com/room/v1/Area/getList")
    suspend fun getLiveAreaList(): LiveAreaListResponse
    
    //  [新增] 分区推荐直播列表 (xlive API)
    @GET("https://api.live.bilibili.com/xlive/web-interface/v1/second/getList")
    suspend fun getLiveSecondAreaList(
        @Query("platform") platform: String = "web",
        @Query("parent_area_id") parentAreaId: Int,
        @Query("area_id") areaId: Int = 0,
        @Query("page") page: Int = 1,
        @Query("sort_type") sortType: String = "online"
    ): LiveSecondAreaResponse
    
    //  [新增] 获取直播间初始化信息 (真实房间号)
    @GET("https://api.live.bilibili.com/room/v1/Room/room_init")
    suspend fun getLiveRoomInit(
        @Query("id") roomId: Long
    ): LiveRoomInitResponse
    
    //  [新增] 获取直播间详细信息 (含主播信息)
    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getInfoByRoom")
    suspend fun getLiveRoomDetail(
        @Query("room_id") roomId: Long
    ): LiveRoomDetailResponse
    
    //  [新增] 获取直播弹幕 WebSocket 信息
    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo")
    suspend fun getDanmuInfo(
        @Query("id") roomId: Long,
        @Query("type") type: Int = 0
    ): LiveDanmuInfoResponse
    
    //  [新增] 获取直播弹幕 WebSocket 信息 (Wbi 签名版 - 解决 -352 风控)
    @GET("https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo")
    suspend fun getDanmuInfoWbi(
        @QueryMap params: Map<String, String>
    ): LiveDanmuInfoResponse
    
    //  [新增] 获取直播间详情（包含在线人数）
    @GET("https://api.live.bilibili.com/room/v1/Room/get_info")
    suspend fun getRoomInfo(
        @Query("room_id") roomId: Long
    ): RoomInfoResponse
    
    //  [新增] 获取直播流 URL - 使用更可靠的 xlive API
    @GET("https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun getLivePlayUrl(
        @Query("room_id") roomId: Long,
        @Query("protocol") protocol: String = "0,1",  // 0=http_stream, 1=http_hls
        @Query("format") format: String = "0,1,2",    // 0=flv, 1=ts, 2=fmp4
        @Query("codec") codec: String = "0,1",        // 0=avc, 1=hevc
        @Query("qn") quality: Int = 150,              // 150=高清
        @Query("platform") platform: String = "web",
        @Query("ptype") ptype: Int = 8
    ): LivePlayUrlResponse
    
    //  [新增] 旧版直播流 API - 可靠返回 quality_description 画质列表
    @GET("https://api.live.bilibili.com/room/v1/Room/playUrl")
    suspend fun getLivePlayUrlLegacy(
        @Query("cid") cid: Long,              // 房间号 (room_id)
        @Query("qn") qn: Int = 10000,         // 画质: 10000最高, 150高清, 80流畅
        @Query("platform") platform: String = "web"
    ): LivePlayUrlResponse

    //  [新增] 发送直播弹幕
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/msg/send")
    suspend fun sendLiveDanmaku(
        @retrofit2.http.Field("roomid") roomId: Long,
        @retrofit2.http.Field("msg") msg: String,
        @retrofit2.http.Field("color") color: Int = 16777215,
        @retrofit2.http.Field("fontsize") fontsize: Int = 25,
        @retrofit2.http.Field("mode") mode: Int = 1,
        @retrofit2.http.Field("rnd") rnd: Long = System.currentTimeMillis() / 1000,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): SimpleApiResponse


    //  [新增] 直播间点赞 (点亮/点赞上报)
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://api.live.bilibili.com/xlive/web-ucenter/v1/like/like_report_v3")
    suspend fun clickLikeLiveRoom(
        @retrofit2.http.Field("click_time") clickTime: Int = 1, // 点击次数
        @retrofit2.http.Field("room_id") roomId: Long,
        @retrofit2.http.Field("uid") uid: Long,        // 当前用户 UID
        @retrofit2.http.Field("anchor_id") anchorId: Long, // 主播 UID
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): SimpleApiResponse

    //  [新增] 获取直播弹幕表情
    @GET("https://api.live.bilibili.com/xlive/web-ucenter/v2/emoticon/GetEmoticons")
    suspend fun getLiveEmoticons(
        @Query("platform") platform: String = "pc",
        @Query("room_id") roomId: Long
    ): com.android.purebilibili.data.model.response.LiveEmoticonRootResponse


    // ==================== 视频播放模块 ====================
    @GET("x/web-interface/view")
    suspend fun getVideoInfo(@Query("bvid") bvid: String): VideoDetailResponse
    
    // [修复] 通过 aid 获取视频信息 - 用于移动端推荐流（可能只返回 aid）
    @GET("x/web-interface/view")
    suspend fun getVideoInfoByAid(@Query("aid") aid: Long): VideoDetailResponse
    
    @GET("x/tag/archive/tags")
    suspend fun getVideoTags(@Query("bvid") bvid: String): VideoTagResponse

    // [新增] 获取 AI 视频总结 (WBI 签名)
    @GET("x/web-interface/view/conclusion/get")
    suspend fun getAiConclusion(@QueryMap params: Map<String, String>): AiSummaryResponse


    @GET("x/player/wbi/playurl")
    suspend fun getPlayUrl(@QueryMap params: Map<String, String>): PlayUrlResponse
    
    //  HTML5 降级方案 (无 Referer 鉴权，仅 MP4 格式)
    @GET("x/player/wbi/playurl")
    suspend fun getPlayUrlHtml5(@QueryMap params: Map<String, String>): PlayUrlResponse
    
    //  [新增] 上报播放心跳（记录播放历史）
    @POST("x/click-interface/web/heartbeat")
    suspend fun reportHeartbeat(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("played_time") playedTime: Long = 0,  // 播放进度（秒）
        @Query("real_played_time") realPlayedTime: Long = 0,
        @Query("start_ts") startTs: Long = System.currentTimeMillis() / 1000
    ): BaseResponse

    //  [新增] 无 WBI 签名的旧版 API (可能绕过 412)
    @GET("x/player/playurl")
    suspend fun getPlayUrlLegacy(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 80,
        @Query("fnval") fnval: Int = 16,  // MP4 格式
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("platform") platform: String = "html5",
        @Query("high_quality") highQuality: Int = 1
    ): PlayUrlResponse
    
    //  [新增] 通过 aid 获取播放地址 - 用于 Story 模式
    @GET("x/player/playurl")
    suspend fun getPlayUrlByAid(
        @Query("avid") aid: Long,
        @Query("cid") cid: Long,
        @Query("qn") qn: Int = 80,
        @Query("fnval") fnval: Int = 16,  // MP4 格式
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1,
        @Query("platform") platform: String = "html5",
        @Query("high_quality") highQuality: Int = 1
    ): PlayUrlResponse
    
    //  [新增] APP playurl API - 使用 access_token 获取高画质视频流 (4K/HDR/1080P60)
    @GET("https://api.bilibili.com/x/player/playurl")
    suspend fun getPlayUrlApp(@QueryMap params: Map<String, String>): PlayUrlResponse

    //  [新增] TV 投屏 playurl（投屏优先使用）
    @GET("x/tv/playurl")
    suspend fun getTvPlayUrl(@QueryMap params: Map<String, String>): PlayUrlResponse

    @GET("x/player/videoshot")
    suspend fun getVideoshot(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("index") index: Int = 1  // 是否返回时间索引，1=是
    ): VideoshotResponse
    
    //  [修复] 获取播放器信息（包含章节/看点数据）— 使用 WBI 签名版本
    @GET("x/player/wbi/v2")
    suspend fun getPlayerInfo(
        @QueryMap params: Map<String, String>
    ): PlayerInfoResponse

    @GET("x/stein/edgeinfo_v2")
    suspend fun getInteractEdgeInfo(
        @Query("bvid") bvid: String,
        @Query("graph_version") graphVersion: Long,
        @Query("edge_id") edgeId: Long? = null
    ): InteractEdgeInfoResponse

    @GET("x/web-interface/archive/related")
    suspend fun getRelatedVideos(@Query("bvid") bvid: String): RelatedResponse

    //  [修复] 使用 comment.bilibili.com 弹幕端点，避免 412 错误
    @GET("https://comment.bilibili.com/{cid}.xml")
    suspend fun getDanmakuXml(@retrofit2.http.Path("cid") cid: Long): ResponseBody
    
    //  [新增] Protobuf 弹幕 API - 分段加载 (每段 6 分钟)
    @GET("https://api.bilibili.com/x/v2/dm/web/seg.so")
    suspend fun getDanmakuSeg(
        @Query("type") type: Int = 1,              // 视频类型: 1=视频
        @Query("oid") oid: Long,                   // cid
        @Query("segment_index") segmentIndex: Int  // 分段索引 (从 1 开始)
    ): ResponseBody
    
    // [新增] 弹幕元数据 API (x/v2/dm/web/view)
    // 返回 DmWebViewReply Protobuf 数据，包含高级弹幕、代码弹幕 URL、互动指令等
    @GET("https://api.bilibili.com/x/v2/dm/web/view")
    suspend fun getDanmakuView(
        @Query("type") type: Int = 1,
        @Query("oid") oid: Long,
        @Query("pid") pid: Long
    ): ResponseBody

    // [新增] 同步弹幕个人配置（账号云同步）
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/dm/web/config")
    suspend fun updateDanmakuWebConfig(
        @retrofit2.http.Field("dm_switch") dmSwitch: String,
        @retrofit2.http.Field("blockscroll") blockScroll: String,
        @retrofit2.http.Field("blocktop") blockTop: String,
        @retrofit2.http.Field("blockbottom") blockBottom: String,
        @retrofit2.http.Field("blockcolor") blockColor: String,
        @retrofit2.http.Field("blockspecial") blockSpecial: String,
        @retrofit2.http.Field("opacity") opacity: Float,
        @retrofit2.http.Field("dmarea") dmArea: Int,
        @retrofit2.http.Field("speedplus") speedPlus: Float,
        @retrofit2.http.Field("fontsize") fontSize: Float,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // [新增] 发送弹幕
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/dm/post")
    suspend fun sendDanmaku(
        @retrofit2.http.Field("oid") oid: Long,               // 视频 cid
        @retrofit2.http.Field("aid") aid: Long,               // 视频 aid (必需)
        @retrofit2.http.Field("type") type: Int = 1,          // 弹幕类型: 1=视频
        @retrofit2.http.Field("msg") msg: String,             // 弹幕内容
        @retrofit2.http.Field("progress") progress: Long,      // 弹幕出现时间 (毫秒)
        @retrofit2.http.Field("color") color: Int = 16777215,  // 颜色 (十进制RGB，默认白色)
        @retrofit2.http.Field("fontsize") fontsize: Int = 25,  // 字号: 18小/25中/36大
        @retrofit2.http.Field("mode") mode: Int = 1,           // 模式: 1滚动/4底部/5顶部
        @retrofit2.http.Field("pool") pool: Int = 0,           // 弹幕池: 0普通/1字幕/2特殊
        @retrofit2.http.Field("plat") plat: Int = 1,           // 平台: 1=web
        @retrofit2.http.Field("csrf") csrf: String
    ): SendDanmakuResponse

    // [新增] 撤回弹幕 (2分钟内可撤回，每天3次)
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/dm/recall")
    suspend fun recallDanmaku(
        @retrofit2.http.Field("cid") cid: Long,               // 视频 cid
        @retrofit2.http.Field("dmid") dmid: Long,             // 弹幕 ID
        @retrofit2.http.Field("csrf") csrf: String
    ): DanmakuActionResponse

    // [新增] 点赞弹幕
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/dm/thumbup/add")
    suspend fun likeDanmaku(
        @retrofit2.http.Field("oid") oid: Long,               // 视频 cid
        @retrofit2.http.Field("dmid") dmid: Long,             // 弹幕 ID
        @retrofit2.http.Field("op") op: Int = 1,              // 操作: 1点赞/2取消
        @retrofit2.http.Field("platform") platform: String = "web_player",
        @retrofit2.http.Field("csrf") csrf: String
    ): DanmakuActionResponse

    // [新增] 查询弹幕点赞状态与票数
    @GET("x/v2/dm/thumbup/stats")
    suspend fun getDanmakuThumbupStats(
        @Query("oid") oid: Long,                              // 视频 cid
        @Query("ids") ids: String                             // 逗号分隔 dmid 列表
    ): com.android.purebilibili.data.model.response.DanmakuThumbupStatsResponse

    // [新增] 举报弹幕
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/dm/report/add")
    suspend fun reportDanmaku(
        @retrofit2.http.Field("cid") cid: Long,               // 视频 cid
        @retrofit2.http.Field("dmid") dmid: Long,             // 弹幕 ID
        @retrofit2.http.Field("reason") reason: Int,          // 举报原因
        @retrofit2.http.Field("content") content: String = "", // 举报内容描述
        @retrofit2.http.Field("csrf") csrf: String
    ): DanmakuActionResponse

    // ==================== 评论模块 ====================
    // 评论主列表 (需 WBI 签名)
    @GET("x/v2/reply/wbi/main")
    suspend fun getReplyList(@QueryMap params: Map<String, String>): ReplyResponse

    // 评论主列表兼容链路
    @GET("x/v2/reply/main")
    suspend fun getReplyListMain(@QueryMap params: Map<String, String>): ReplyResponse
    
    //  [新增] 旧版评论 API - 用于时间排序 (sort=0)
    // 此 API 不需要 WBI 签名，分页更稳定
    @GET("x/v2/reply")
    suspend fun getReplyListLegacy(
        @Query("oid") oid: Long,
        @Query("type") type: Int = 1,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 20,
        @Query("sort") sort: Int = 0  // 0=按时间, 1=按点赞数, 2=按回复数
    ): ReplyResponse

    @GET("x/v2/reply/reply")
    suspend fun getReplyReply(
        @Query("oid") oid: Long,
        @Query("type") type: Int = 1,
        @Query("root") root: Long, // 根评论 ID (rpid)
        @Query("pn") pn: Int,     // 页码
        @Query("ps") ps: Int = 20 // 每页数量
    ): ReplyResponse

    @GET("x/v2/reply/count")
    suspend fun getReplyCount(
        @Query("oid") oid: Long,
        @Query("type") type: Int
    ): ReplyCountResponse

    // [新增] 发送评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/add")
    suspend fun addReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("message") message: String,
        @retrofit2.http.Field("plat") plat: Int = 1,
        @retrofit2.http.Field("root") root: Long? = null,
        @retrofit2.http.Field("parent") parent: Long? = null,
        @retrofit2.http.Field("pictures") pictures: String? = null,
        @retrofit2.http.Field("csrf") csrf: String
    ): AddReplyResponse

    // [新增] 评论图片上传（复用动态图片上传接口）
    @retrofit2.http.Multipart
    @retrofit2.http.POST("x/dynamic/feed/draw/upload_bfs")
    suspend fun uploadCommentImage(
        @retrofit2.http.Part fileUp: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("category") category: okhttp3.RequestBody,
        @retrofit2.http.Part("biz") biz: okhttp3.RequestBody,
        @retrofit2.http.Part("csrf") csrf: okhttp3.RequestBody
    ): UploadCommentImageResponse

    /**
     * 获取表情包
     */
    @GET("x/emote/user/panel/web")
    suspend fun getEmotes(
        @QueryMap params: Map<String, String>
    ): com.android.purebilibili.data.model.response.EmoteResponse
    
    // [新增] 点赞评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/action")
    suspend fun likeReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("action") action: Int,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // [新增] 点踩评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/hate")
    suspend fun hateReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("action") action: Int,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // [新增] 删除评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/del")
    suspend fun deleteReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // [新增] 举报评论
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/reply/report")
    suspend fun reportReply(
        @retrofit2.http.Field("oid") oid: Long,
        @retrofit2.http.Field("type") type: Int = 1,
        @retrofit2.http.Field("rpid") rpid: Long,
        @retrofit2.http.Field("reason") reason: Int,
        @retrofit2.http.Field("content") content: String = "",
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    // ==================== 用户交互模块 ====================
    // 查询与 UP 主的关注关系
    @GET("x/relation")
    suspend fun getRelation(
        @Query("fid") fid: Long  // UP 主 mid
    ): RelationResponse

    @GET("x/relation/tags")
    suspend fun getRelationTags(): RelationTagsResponse

    @GET("x/relation/tag/user")
    suspend fun getRelationTagUser(
        @Query("fid") fid: Long
    ): RelationTagUserResponse

    @GET("x/relation/tag")
    suspend fun getRelationTagMembers(
        @Query("tagid") tagId: Long,
        @Query("order_type") orderType: String = "",
        @Query("ps") pageSize: Int = 100,
        @Query("pn") page: Int = 1
    ): com.android.purebilibili.data.model.response.RelationTagMembersResponse
    
    //  [新增] 查询视频是否已收藏
    @GET("x/v2/fav/video/favoured")
    suspend fun checkFavoured(
        @Query("aid") aid: Long
    ): FavouredResponse
    
    //  [新增] 关注/取关 UP 主
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/relation/modify")
    suspend fun modifyRelation(
        @retrofit2.http.Field("fid") fid: Long,      // UP 主 mid
        @retrofit2.http.Field("act") act: Int,        // 1=关注, 2=取关
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse

    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/relation/tags/addUsers")
    suspend fun addUsersToRelationTags(
        @retrofit2.http.Field("fids") fids: String,
        @retrofit2.http.Field("tagids") tagIds: String,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 收藏/取消收藏视频
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v3/fav/resource/deal")
    suspend fun dealFavorite(
        @retrofit2.http.Field("rid") rid: Long,                    // 视频 aid
        @retrofit2.http.Field("type") type: Int = 2,               // 资源类型 2=视频
        @retrofit2.http.Field("add_media_ids") addIds: String = "", // 添加到的收藏夹 ID
        @retrofit2.http.Field("del_media_ids") delIds: String = "", // 从收藏夹移除
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 点赞/取消点赞视频
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/web-interface/archive/like")
    suspend fun likeVideo(
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("like") like: Int,   // 1=点赞, 2=取消点赞
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 查询是否已点赞
    @GET("x/web-interface/archive/has/like")
    suspend fun hasLiked(
        @Query("aid") aid: Long
    ): HasLikedResponse
    
    //  [新增] 投币
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/web-interface/coin/add")
    suspend fun coinVideo(
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("multiply") multiply: Int,       // 投币数量 1 或 2
        @retrofit2.http.Field("select_like") selectLike: Int,  // 1=同时点赞, 0=不点赞
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 查询已投币数
    @GET("x/web-interface/archive/coins")
    suspend fun hasCoined(
        @Query("aid") aid: Long
    ): HasCoinedResponse
    
    //  [新增] 获取关注列表（用于首页显示"已关注"标签）
    @GET("x/relation/followings")
    suspend fun getFollowings(
        @Query("vmid") vmid: Long,        // 用户 mid
        @Query("pn") pn: Int = 1,         // 页码
        @Query("ps") ps: Int = 50,        // 每页数量（最大 50）
        @Query("order") order: String = "desc"  // 排序
    ): FollowingsResponse
    
    //  [官方适配] 获取视频在线观看人数
    @GET("x/player/online/total")
    suspend fun getOnlineCount(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long
    ): OnlineResponse
    
    // ==================== 稍后再看模块 ====================
    @GET("x/v2/history/toview")
    suspend fun getWatchLaterList(): WatchLaterResponse
    
    //  [新增] 添加到稍后再看
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/history/toview/add")
    suspend fun addToWatchLater(
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
    
    //  [新增] 从稍后再看删除
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/v2/history/toview/del")
    suspend fun deleteFromWatchLater(
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): SimpleApiResponse
}

//  [新增] Buvid SPI 响应模型 (用于获取正确的设备指纹)
@kotlinx.serialization.Serializable
data class BuvidSpiData(
    val b_3: String = "",  // buvid3
    val b_4: String = ""   // buvid4
)

@kotlinx.serialization.Serializable
data class BuvidSpiResponse(
    val code: Int = 0,
    val data: BuvidSpiData? = null
)

//  [新增] Buvid API
interface BuvidApi {
    @GET("x/frontend/finger/spi")
    suspend fun getSpi(): BuvidSpiResponse
    
    //  Buvid 激活 (PiliPala 中关键的一步)
    @retrofit2.http.FormUrlEncoded
    @POST("x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activateBuvid(
        @retrofit2.http.Field("payload") payload: String
    ): SimpleApiResponse
}

//  [新增] 开屏/壁纸 API
interface SplashApi {
    @GET("https://app.bilibili.com/x/v2/splash/list")
    suspend fun getSplashList(
        @QueryMap params: Map<String, String> // 包含 appkey, ts, sign 等
    ): com.android.purebilibili.data.model.response.SplashResponse
    
    // [新增] 品牌开屏壁纸列表 (无广告，高质量)
    @GET("https://app.bilibili.com/x/v2/splash/brand/list")
    suspend fun getSplashBrandList(
        @QueryMap params: Map<String, String>
    ): com.android.purebilibili.data.model.response.SplashBrandResponse
}

interface SearchApi {
    @GET("x/web-interface/wbi/search/default")
    suspend fun getDefaultSearch(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchDefaultResponse

    @GET("x/web-interface/search/default")
    suspend fun getDefaultSearchLegacy(): com.android.purebilibili.data.model.response.SearchDefaultResponse

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
    suspend fun searchUp(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchUpResponse
    
    //  [新增] 番剧搜索 - search_type=media_bangumi
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchBangumi(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.BangumiSearchResponse

    //  [新增] 影视搜索 - search_type=media_ft
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchMediaFt(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.BangumiSearchResponse
    
    //  [新增] 直播搜索 - search_type=live_room
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchLive(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.LiveRoomSearchResponse

    //  [新增] 专栏搜索 - search_type=article
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchArticle(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SearchArticleResponse
    
    //  搜索建议/联想
    @GET("https://s.search.bilibili.com/main/suggest")
    suspend fun getSearchSuggest(
        @Query("term") term: String,
        @Query("main_ver") mainVer: String = "v1",
        @Query("highlight") highlight: Int = 0
    ): SearchSuggestResponse
}

interface ArticleApi {
    @GET("x/article/view")
    suspend fun getArticleView(
        @QueryMap params: Map<String, String>
    ): com.android.purebilibili.data.model.response.ArticleDetailResponse
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

//  动态 API
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
interface SpaceApi {
    // 获取用户详细信息 (需要 WBI 签名)
    @GET("x/space/wbi/acc/info")
    suspend fun getSpaceInfo(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SpaceInfoResponse

    @GET("x/space/wbi/acc/info")
    suspend fun getSpaceInfoRaw(@QueryMap params: Map<String, String>): okhttp3.ResponseBody
    
    // 获取用户投稿视频列表 (需要 WBI 签名)
    @GET("x/space/wbi/arc/search")
    suspend fun getSpaceVideos(@QueryMap params: Map<String, String>): com.android.purebilibili.data.model.response.SpaceVideoResponse
    
    // 获取关注/粉丝数
    @GET("x/relation/stat")
    suspend fun getRelationStat(@Query("vmid") mid: Long): com.android.purebilibili.data.model.response.RelationStatResponse
    
    // 获取UP主播放量/获赞数
    @GET("x/space/upstat")
    suspend fun getUpStat(@Query("mid") mid: Long): com.android.purebilibili.data.model.response.UpStatResponse
    
    //  获取合集和系列列表
    @GET("x/polymer/web-space/seasons_series_list")
    suspend fun getSeasonsSeriesList(
        @Query("mid") mid: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): com.android.purebilibili.data.model.response.SeasonsSeriesListResponse
    
    //  获取合集内的视频列表
    @GET("x/polymer/web-space/seasons_archives_list")
    suspend fun getSeasonArchives(
        @Query("mid") mid: Long,
        @Query("season_id") seasonId: Long,
        @Query("page_num") pageNum: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("sort_reverse") sortReverse: Boolean = false
    ): com.android.purebilibili.data.model.response.SeasonArchivesResponse
    
    //  获取系列内的视频列表
    @GET("x/series/archives")
    suspend fun getSeriesArchives(
        @Query("mid") mid: Long,
        @Query("series_id") seriesId: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("sort") sort: String = "desc"
    ): com.android.purebilibili.data.model.response.SeriesArchivesResponse
    
    //  置顶视频
    @GET("x/space/top/arc")
    suspend fun getTopArc(
        @Query("vmid") vmid: Long
    ): com.android.purebilibili.data.model.response.SpaceTopArcResponse
    
    //  个人公告
    @GET("x/space/notice")
    suspend fun getNotice(
        @Query("mid") mid: Long
    ): com.android.purebilibili.data.model.response.SpaceNoticeResponse
    
    //  用户动态 (需要登录 Cookie)
    @GET("x/polymer/web-dynamic/v1/feed/space")
    suspend fun getSpaceDynamic(
        @Query("host_mid") hostMid: Long,
        @Query("offset") offset: String = "",
        @Query("timezone_offset") timezoneOffset: Int = -480
    ): com.android.purebilibili.data.model.response.SpaceDynamicResponse
    
    //  [New] Get User Audio List
    @GET("https://api.bilibili.com/audio/music-service/web/song/upper")
    suspend fun getSpaceAudioList(
        @Query("uid") uid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("order") order: Int = 1,  // 1=latest, 2=hot, 3=duration
        @Query("jsonp") jsonp: String = "jsonp"
    ): com.android.purebilibili.data.model.response.SpaceAudioResponse

    //  [New] Get User Article List
    @GET("https://api.bilibili.com/x/article/up/lists")
    suspend fun getSpaceArticleList(
        @Query("mid") mid: Long,
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30,
        @Query("sort") sort: String = "publish_time",  // publish_time, view, fav
        @Query("jsonp") jsonp: String = "jsonp"
    ): com.android.purebilibili.data.model.response.SpaceArticleResponse
}

//  [新增] 番剧/影视 API
interface BangumiApi {
    // 番剧时间表
    @GET("pgc/web/timeline")
    suspend fun getTimeline(
        @Query("types") types: Int,      // 1=番剧 4=国创
        @Query("before") before: Int = 3,
        @Query("after") after: Int = 7
    ): com.android.purebilibili.data.model.response.BangumiTimelineResponse
    
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
    ): com.android.purebilibili.data.model.response.BangumiIndexResponse
    
    // 番剧详情 -  返回 ResponseBody 自行解析，防止 OOM
    @GET("pgc/view/web/season")
    suspend fun getSeasonDetail(
        @Query("season_id") seasonId: Long? = null,
        @Query("ep_id") epId: Long? = null
    ): ResponseBody
    
    // 番剧播放地址 - 使用标准接口
    @GET("pgc/player/web/playurl")
    suspend fun getBangumiPlayUrl(
        @Query("ep_id") epId: Long,
        @Query("qn") qn: Int = 80,
        @Query("fnval") fnval: Int = 4048,
        @Query("fnver") fnver: Int = 0,
        @Query("fourk") fourk: Int = 1
    ): com.android.purebilibili.data.model.response.BangumiPlayUrlResponse
    
    // 追番/追剧
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("pgc/web/follow/add")
    suspend fun followBangumi(
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
    
    // 取消追番/追剧
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("pgc/web/follow/del")
    suspend fun unfollowBangumi(
        @retrofit2.http.Field("season_id") seasonId: Long,
        @retrofit2.http.Field("csrf") csrf: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
    
    //  [新增] 我的追番列表
    @GET("x/space/bangumi/follow/list")
    suspend fun getMyFollowBangumi(
        @Query("vmid") vmid: Long,          // 用户 mid (登录用户的 mid)
        @Query("type") type: Int = 1,        // 1=追番 2=追剧
        @Query("pn") pn: Int = 1,
        @Query("ps") ps: Int = 30
    ): com.android.purebilibili.data.model.response.MyFollowBangumiResponse
}

interface PassportApi {
    // 二维码登录
    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(): QrCodeResponse

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQrCode(@Query("qrcode_key") key: String): Response<PollResponse>
    
    // ==========  极验验证 + 手机号/密码登录 ==========
    
    // 获取极验验证参数 (gt, challenge, token)
    @GET("x/passport-login/captcha")
    suspend fun getCaptcha(
        @Query("source") source: String = "main_web"
    ): CaptchaResponse
    
    // 发送短信验证码
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/web/sms/send")
    suspend fun sendSmsCode(
        @retrofit2.http.Field("cid") cid: Int = 86,           // 国家代码，中国大陆 = 86
        @retrofit2.http.Field("tel") tel: String,              // 手机号
        @retrofit2.http.Field("source") source: String = "main_web",
        @retrofit2.http.Field("token") token: String,          // captcha token
        @retrofit2.http.Field("challenge") challenge: String,  // 极验 challenge
        @retrofit2.http.Field("validate") validate: String,    // 极验验证结果
        @retrofit2.http.Field("seccode") seccode: String       // 极验安全码
    ): SmsCodeResponse
    
    // 短信验证码登录
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/web/login/sms")
    suspend fun loginBySms(
        @retrofit2.http.Field("cid") cid: Int = 86,
        @retrofit2.http.Field("tel") tel: String,
        @retrofit2.http.Field("code") code: Int,                // 短信验证码
        @retrofit2.http.Field("source") source: String = "main_mini",
        @retrofit2.http.Field("captcha_key") captchaKey: String, // sendSmsCode 返回的 key
        @retrofit2.http.Field("keep") keep: Int = 0,
        @retrofit2.http.Field("go_url") goUrl: String = "https://www.bilibili.com"
    ): Response<LoginResponse>  // 使用 Response 以获取 Set-Cookie
    
    // 获取 RSA 公钥 (密码登录用)
    @GET("x/passport-login/web/key")
    suspend fun getWebKey(): WebKeyResponse
    
    // 密码登录
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("x/passport-login/web/login")
    suspend fun loginByPassword(
        @retrofit2.http.Field("username") username: String,     // 手机号
        @retrofit2.http.Field("password") password: String,     // RSA 加密后的密码
        @retrofit2.http.Field("keep") keep: Int = 0,
        @retrofit2.http.Field("token") token: String,
        @retrofit2.http.Field("challenge") challenge: String,
        @retrofit2.http.Field("validate") validate: String,
        @retrofit2.http.Field("seccode") seccode: String,
        @retrofit2.http.Field("source") source: String = "main-fe-header",
        @retrofit2.http.Field("go_url") goUrl: String = "https://www.bilibili.com"
    ): Response<LoginResponse>
    
    // ==========  TV 端登录 (获取 access_token 用于高画质视频) ==========
    
    // TV 端申请二维码
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/auth_code")
    suspend fun generateTvQrCode(
        @retrofit2.http.FieldMap params: Map<String, String>
    ): TvQrCodeResponse
    
    // TV 端轮询登录状态
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/qrcode/poll")
    suspend fun pollTvQrCode(
        @retrofit2.http.FieldMap params: Map<String, String>
    ): TvPollResponse

    //  [新增] TV 端刷新 Token
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("https://passport.bilibili.com/x/passport-tv-login/h5/refresh")
    suspend fun refreshToken(
        @retrofit2.http.FieldMap params: Map<String, String>
    ): com.android.purebilibili.data.model.response.TvTokenRefreshResponse
}

// ==================== 音频 API ====================
interface AudioApi {
    // 🎵 获取音频基本信息
    @GET("audio/music-service-c/web/song/info")
    suspend fun getSongInfo(
        @Query("sid") sid: Long
    ): com.android.purebilibili.data.model.response.SongInfoResponse

    // 🎵 获取音频流地址
    @GET("audio/music-service-c/web/url")
    suspend fun getSongStream(
        @Query("sid") sid: Long,
        @Query("privilege") privilege: Int = 2,
        @Query("quality") quality: Int = 2
    ): com.android.purebilibili.data.model.response.SongStreamResponse

    // 🎵 获取歌词
    @GET("audio/music-service-c/web/song/lyric")
    suspend fun getSongLyric(
        @Query("sid") sid: Long
    ): com.android.purebilibili.data.model.response.SongLyricResponse
}

// ==================== 私信 API (api.vc.bilibili.com) ====================
interface MessageApi {
    // 获取未读私信数
    @GET("session_svr/v1/session_svr/single_unread")
    suspend fun getUnreadCount(
        @Query("unread_type") unreadType: Int = 0,
        @Query("show_unfollow_list") showUnfollowList: Int = 1,
        @Query("show_dustbin") showDustbin: Int = 1,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.android.purebilibili.data.model.response.MessageUnreadResponse

    // 获取会话列表
    @GET("session_svr/v1/session_svr/get_sessions")
    suspend fun getSessions(
        @Query("session_type") sessionType: Int = 4,  // 4=所有
        @Query("group_fold") groupFold: Int = 1,
        @Query("unfollow_fold") unfollowFold: Int = 0,
        @Query("sort_rule") sortRule: Int = 2,
        @Query("size") size: Int = 20,
        @Query("build") build: Int = 0,  //  需要此参数才能获取account_info
        @Query("mobi_app") mobiApp: String = "web",
        @Query("web_location") webLocation: String = "333.999",  //  网页位置标识
        @Query("pn") pn: Int = 1,  //  页码 (第几页)
        @Query("end_ts") endTs: Long = 0  //  结束时间戳 (游标)
    ): com.android.purebilibili.data.model.response.SessionListResponse
    
    // 获取私信消息记录
    @GET("svr_sync/v1/svr_sync/fetch_session_msgs")
    suspend fun fetchSessionMsgs(
        @Query("talker_id") talkerId: Long,
        @Query("session_type") sessionType: Int = 1,
        @Query("size") size: Int = 20,
        @Query("begin_seqno") beginSeqno: Long = 0,
        @Query("end_seqno") endSeqno: Long = 0,
        @Query("mobi_app") mobiApp: String = "web"
    ): com.android.purebilibili.data.model.response.MessageHistoryResponse
    
    // 发送私信
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("web_im/v1/web_im/send_msg")
    suspend fun sendMsg(
        @retrofit2.http.Field("msg[sender_uid]") senderUid: Long,
        @retrofit2.http.Field("msg[receiver_id]") receiverId: Long,
        @retrofit2.http.Field("msg[receiver_type]") receiverType: Int = 1,
        @retrofit2.http.Field("msg[msg_type]") msgType: Int = 1,
        @retrofit2.http.Field("msg[content]") content: String,
        @retrofit2.http.Field("msg[timestamp]") timestamp: Long,
        @retrofit2.http.Field("msg[dev_id]") devId: String,
        @retrofit2.http.Field("msg[new_face_version]") newFaceVersion: Int = 1,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SendMessageResponse
    
    // 设置会话已读
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/update_ack")
    suspend fun updateAck(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("ack_seqno") ackSeqno: Long,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
    
    // 置顶/取消置顶会话
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/set_top")
    suspend fun setTop(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("op_type") opType: Int,  // 0=置顶, 1=取消置顶
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
    
    // 移除会话
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("session_svr/v1/session_svr/remove_session")
    suspend fun removeSession(
        @retrofit2.http.Field("talker_id") talkerId: Long,
        @retrofit2.http.Field("session_type") sessionType: Int,
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String
    ): com.android.purebilibili.data.model.response.SimpleApiResponse
}

object NetworkModule {
    internal var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    internal fun resolveSharedNetworkProtocols(): List<Protocol> {
        return listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
    }

    internal fun resolveApiHttpCacheBudgetBytes(): Long {
        return 32L * 1024 * 1024
    }

    val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .protocols(resolveSharedNetworkProtocols())
            //  [新增] 超时配置，提高网络稳定性
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            //  [性能优化] HTTP 磁盘缓存 - 10MB，减少重复请求
            .cache(okhttp3.Cache(
                directory = java.io.File(appContext?.cacheDir ?: java.io.File("/tmp"), "okhttp_cache"),
                maxSize = resolveApiHttpCacheBudgetBytes()
            ))
            //  [性能优化] 连接池优化 - 保持更多空闲连接
            .connectionPool(okhttp3.ConnectionPool(
                maxIdleConnections = 10,
                keepAliveDuration = 5,
                timeUnit = java.util.concurrent.TimeUnit.MINUTES
            ))
            //  [新增] 自动重试和重定向
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
        
        if (shouldEnableTrustAllCertificates(BuildConfig.DEBUG)) {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
        }
        
        val cookieLock = Any()
        
        builder
            //  [Fix] 自定义 DNS 实现，绕过 OkHttp 可能被混淆内部类的问题，并添加日志
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                try {
                    val addresses = java.net.InetAddress.getAllByName(hostname).toList()
                    com.android.purebilibili.core.util.Logger.d("ApiClient", "DNS resolved: $hostname -> $addresses")
                    return addresses
                } catch (e: Exception) {
                    com.android.purebilibili.core.util.Logger.e("ApiClient", "DNS failed for $hostname: ${e.message}")
                    val fallback = resolveHardcodedDnsFallback(
                        hostname = hostname,
                        allowHardcodedIpFallback = BuildConfig.ALLOW_HARDCODED_DNS_FALLBACK
                    )
                    if (fallback != null) {
                        com.android.purebilibili.core.util.Logger.w(
                            "ApiClient",
                            "⚠️ Using Hardcoded IP for ${fallback.description}: ${fallback.ipAddress}"
                        )
                        return listOf(java.net.InetAddress.getByName(fallback.ipAddress))
                    }
                    throw e
                }
            }})
            //  [关键] 添加 CookieJar 自动管理 Cookie（参考 PiliPala）
            .cookieJar(object : okhttp3.CookieJar {
                private val cookieStore = mutableMapOf<String, MutableList<okhttp3.Cookie>>()
                
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                    val host = url.host
                    synchronized(cookieLock) {
                        val existingCookies = cookieStore.getOrPut(host) { mutableListOf() }
                        cookies.forEach { newCookie ->
                            // 移除同名旧 cookie，添加新 cookie
                            existingCookies.removeAll { it.name == newCookie.name }
                            existingCookies.add(newCookie)
                            com.android.purebilibili.core.util.Logger.d("CookieJar", " Saved cookie: ${newCookie.name} for $host")
                        }
                    }
                }
                
                override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                    val cookies = mutableListOf<okhttp3.Cookie>()
                    
                    // 加载存储的 cookies
                    synchronized(cookieLock) {
                        cookieStore[url.host]?.let { cookies.addAll(it) }
                    }
                    
                    //  确保 buvid3 存在
                    var buvid3 = TokenManager.buvid3Cache
                    if (buvid3.isNullOrEmpty()) {
                        buvid3 = UUID.randomUUID().toString() + "infoc"
                        TokenManager.buvid3Cache = buvid3
                    }
                    if (cookies.none { it.name == "buvid3" }) {
                        cookies.add(okhttp3.Cookie.Builder()
                            .domain(url.host)
                            .name("buvid3")
                            .value(buvid3)
                            .build())
                    }
                    
                    //  [修复] 使用 bilibili.com 域名，确保 Cookie 在所有子域名生效
                    // OkHttp 会自动处理子域名匹配（不需要前导点）
                    val biliBiliDomain = if (url.host.endsWith("bilibili.com")) "bilibili.com" else url.host
                    
                    //  如果有 SESSDATA，添加它
                    val sessData = TokenManager.sessDataCache
                    if (!sessData.isNullOrEmpty() && cookies.none { it.name == "SESSDATA" }) {
                        cookies.add(okhttp3.Cookie.Builder()
                            .domain(biliBiliDomain)
                            .name("SESSDATA")
                            .value(sessData)
                            .build())
                    }
                    
                    //  [新增] 添加 bili_jct (CSRF Token) - VIP 画质验证可能需要
                    val biliJct = TokenManager.csrfCache
                    if (!biliJct.isNullOrEmpty() && cookies.none { it.name == "bili_jct" }) {
                        cookies.add(okhttp3.Cookie.Builder()
                            .domain(biliBiliDomain)
                            .name("bili_jct")
                            .value(biliJct)
                            .build())
                    }
                    
                    //  [调试] 输出 Cookie 信息以便排查 VIP 画质问题
                    if (url.encodedPath.contains("playurl") || url.encodedPath.contains("pgc/view")) {
                        com.android.purebilibili.core.util.Logger.d(
                            "CookieJar",
                            " ${url.encodedPath} request: domain=$biliBiliDomain, hasSess=${!sessData.isNullOrEmpty()}, hasCsrf=${!biliJct.isNullOrEmpty()}"
                        )
                    }
                    
                    return cookies
                }
            })
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                var referer = "https://www.bilibili.com"
                
                //  如果请求中包含 bvid，构造更具体的 Referer (解决 412 问题)
                val bvid = url.queryParameter("bvid")
                if (!bvid.isNullOrEmpty()) {
                    referer = "https://www.bilibili.com/video/$bvid"
                }
                
                //  如果是 Space API 请求，使用 space.bilibili.com 作为 Referer
                val mid = url.queryParameter("mid") ?: url.queryParameter("vmid")
                if (url.encodedPath.contains("/x/space/") && !mid.isNullOrEmpty()) {
                    referer = "https://space.bilibili.com/$mid"
                }
                
                //  [新增] 直播 API Referer 处理
                if (url.host == "api.live.bilibili.com") {
                    val roomId = url.queryParameter("room_id") ?: url.queryParameter("id")
                    referer = if (!roomId.isNullOrEmpty()) {
                        "https://live.bilibili.com/$roomId"
                    } else {
                        "https://live.bilibili.com"
                    }
                }
                
                //  [修复] 弹幕 API 需要使用视频页面作为 Referer (解决 412 问题)
                if (url.encodedPath.contains("/dm/list.so") || url.encodedPath.contains("/x/v1/dm/")) {
                    referer = "https://www.bilibili.com/video/"
                }

                //  [修复] 动态接口使用动态页 Referer/Origin，降低 412 触发概率
                val isDynamicEndpoint = url.encodedPath.contains("/x/polymer/web-dynamic/") ||
                    url.encodedPath.contains("/x/dynamic/")
                if (isDynamicEndpoint) {
                    referer = "https://t.bilibili.com/"
                }

                var origin = "https://www.bilibili.com"
                if (url.host == "api.live.bilibili.com") {
                    origin = "https://live.bilibili.com"
                }
                if (isDynamicEndpoint) {
                    origin = "https://t.bilibili.com"
                }

                val builder = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Origin", origin) //  动态 Origin 头
                
                //  [关键修复] WBI 签名接口绝对不能设置 Referer 头，否则会失败
                // 参考：https://github.com/SocialSisterYi/bilibili-API-collect/blob/master/docs/misc/sign/wbi.md
                val isWbiEndpoint = url.encodedPath.contains("/wbi/")
                if (!isWbiEndpoint) {
                    builder.header("Referer", referer)
                }

                com.android.purebilibili.core.util.Logger.d(
                    "ApiClient",
                    " Sending request to ${original.url}, Referer: ${if (isWbiEndpoint) "OMITTED (WBI)" else referer}, hasSess=${!TokenManager.sessDataCache.isNullOrEmpty()}, hasCsrf=${!TokenManager.csrfCache.isNullOrEmpty()}"
                )

                val request = builder.build()
                try {
                    val response = chain.proceed(request)
                    if (response.code >= 500 || response.code == 429 || response.code == 412) {
                        com.android.purebilibili.core.util.CrashReporter.reportApiError(
                            endpoint = "${request.method} ${request.url.encodedPath}",
                            httpCode = response.code,
                            errorMessage = "HTTP ${response.code}"
                        )
                    }
                    response
                } catch (e: Exception) {
                    com.android.purebilibili.core.util.CrashReporter.reportApiError(
                        endpoint = "${request.method} ${request.url.encodedPath}",
                        httpCode = -1,
                        errorMessage = e.message ?: e.javaClass.simpleName
                    )
                    throw e
                }
            }
            .build()
    }
    
    //  [新增] Guest OkHttpClient - 不带登录凭证，用于风控时的降级
    // 当登录用户遭遇风控 (-351) 时，可以尝试以游客身份获取视频
    val guestOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(resolveSharedNetworkProtocols())
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            //  CookieJar 使用全新的 buvid3，不复用可能被污染的 buvid3Cache
            .cookieJar(object : okhttp3.CookieJar {
                // 为 guest 模式生成独立的 buvid3，避免复用被风控的 buvid3
                private val guestBuvid3: String by lazy { 
                    UUID.randomUUID().toString().replace("-", "") + "infoc"
                }
                
                override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                    // 不保存任何 cookie
                }
                
                override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                    val cookies = mutableListOf<okhttp3.Cookie>()
                    
                    //  使用全新生成的 guestBuvid3，不使用 TokenManager.buvid3Cache
                    cookies.add(okhttp3.Cookie.Builder()
                        .domain(url.host)
                        .name("buvid3")
                        .value(guestBuvid3)
                        .build())
                    
                    com.android.purebilibili.core.util.Logger.d(
                        "GuestCookieJar",
                        " ${url.encodedPath} request: guest mode with fresh buvid3=${guestBuvid3.take(15)}..."
                    )
                    
                    return cookies
                }
            })
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                var referer = "https://www.bilibili.com"
                
                val bvid = url.queryParameter("bvid")
                if (!bvid.isNullOrEmpty()) {
                    referer = "https://www.bilibili.com/video/$bvid"
                }

                val isWbiEndpoint = url.encodedPath.contains("/wbi/")
                val builder = original.newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                    .header("Origin", "https://www.bilibili.com")

                if (!isWbiEndpoint) {
                    builder.header("Referer", referer)
                }
                
                val request = builder.build()
                try {
                    val response = chain.proceed(request)
                    if (response.code >= 500 || response.code == 429 || response.code == 412) {
                        com.android.purebilibili.core.util.CrashReporter.reportApiError(
                            endpoint = "guest ${request.method} ${request.url.encodedPath}",
                            httpCode = response.code,
                            errorMessage = "HTTP ${response.code}"
                        )
                    }
                    response
                } catch (e: Exception) {
                    com.android.purebilibili.core.util.CrashReporter.reportApiError(
                        endpoint = "guest ${request.method} ${request.url.encodedPath}",
                        httpCode = -1,
                        errorMessage = e.message ?: e.javaClass.simpleName
                    )
                    throw e
                }
            }
            .build()
    }
    
    //  [新增] Guest API - 使用 guestOkHttpClient，用于风控降级
    val guestApi: BilibiliApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(guestOkHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(BilibiliApi::class.java)
    }

    val api: BilibiliApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(BilibiliApi::class.java)
    }
    val passportApi: PassportApi by lazy {
        Retrofit.Builder().baseUrl("https://passport.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(PassportApi::class.java)
    }
    val searchApi: SearchApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SearchApi::class.java)
    }

    val articleApi: ArticleApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(ArticleApi::class.java)
    }
    
    //  动态 API
    val dynamicApi: DynamicApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(DynamicApi::class.java)
    }
    
    //  Buvid API (用于获取设备指纹)
    val buvidApi: BuvidApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(BuvidApi::class.java)
    }
    
    //  [新增] UP主空间 API
    val spaceApi: SpaceApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SpaceApi::class.java)
    }
    
    //  [新增] 番剧/影视 API
    val bangumiApi: BangumiApi by lazy {
        Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(BangumiApi::class.java)
    }
    
    //  [新增] 故事模式 (竖屏短视频) API - 使用 app.bilibili.com
    val storyApi: StoryApi by lazy {
        Retrofit.Builder().baseUrl("https://app.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(StoryApi::class.java)
    }
    
    //  [新增] 开屏/壁纸 API
    val splashApi: SplashApi by lazy {
        Retrofit.Builder().baseUrl("https://app.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(SplashApi::class.java)
    }
    
    //  [新增] 私信 API - 使用 api.vc.bilibili.com
    val messageApi: MessageApi by lazy {
        Retrofit.Builder().baseUrl("https://api.vc.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(MessageApi::class.java)
    }
    
    //  [新增] 音频 API - 使用 www.bilibili.com
    val audioApi: AudioApi by lazy {
        Retrofit.Builder().baseUrl("https://www.bilibili.com/").client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
            .create(AudioApi::class.java)
    }
}
