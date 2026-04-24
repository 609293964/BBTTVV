// 文件路径: core/network/ApiClient.kt
package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

internal const val BANGUMI_PLAY_URL_PATH = "pgc/player/web/v2/playurl"

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

    @retrofit2.http.Headers(
        "Cache-Control: no-cache",
        "Pragma: no-cache"
    )
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
    ): com.bbttvv.app.data.model.response.LiveEmoticonRootResponse


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

    @retrofit2.http.FormUrlEncoded
    @POST("x/v2/history/report")
    suspend fun reportHistoryProgress(
        @retrofit2.http.Field("aid") aid: Long,
        @retrofit2.http.Field("cid") cid: Long,
        @retrofit2.http.Field("progress") progress: Long = 0,
        @retrofit2.http.Field("platform") platform: String = "android",
        @retrofit2.http.Field("csrf") csrf: String,
        @retrofit2.http.Field("csrf_token") csrfToken: String = csrf
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

    @GET("https://api.bilibili.com/x/dm/filter/user")
    suspend fun getDanmakuFilterUser(): ResponseBody

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
    ): com.bbttvv.app.data.model.response.DanmakuThumbupStatsResponse

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

    @retrofit2.http.Multipart
    @retrofit2.http.POST("x/dynamic/feed/draw/upload_bfs")
    suspend fun uploadPrivateMessageImage(
        @retrofit2.http.Part fileUp: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("biz") biz: okhttp3.RequestBody,
        @retrofit2.http.Part("csrf") csrf: okhttp3.RequestBody
    ): UploadCommentImageResponse

    /**
     * 获取表情包
     */
    @GET("x/emote/user/panel/web")
    suspend fun getEmotes(
        @QueryMap params: Map<String, String>
    ): com.bbttvv.app.data.model.response.EmoteResponse
    
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
    ): com.bbttvv.app.data.model.response.RelationTagMembersResponse
    
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
