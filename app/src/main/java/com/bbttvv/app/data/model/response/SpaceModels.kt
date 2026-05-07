package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============== UP主空间 API 响应模型 ===============

// /x/space/wbi/acc/info 用户信息响应
@Serializable
data class SpaceInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceUserInfo? = null
)

@Serializable
data class SpaceUserInfo(
    val mid: Long = 0,
    val name: String = "",
    val sex: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int = 0,
    @SerialName("fans_badge")
    val fansBadge: Boolean = false,
    val official: SpaceOfficial = SpaceOfficial(),
    val vip: SpaceVip = SpaceVip(),
    @SerialName("is_followed")
    val isFollowed: Boolean = false,
    @SerialName("top_photo")
    val topPhoto: String = "",
    @SerialName("live_room")
    val liveRoom: SpaceLiveRoom? = null,
    @SerialName("live_place")
    val livePlace: String? = null,
    @SerialName("ip_location")
    val ipLocation: String? = null
)

@Serializable
data class SpaceOfficial(
    val role: Int = 0,
    val title: String = "",
    val desc: String = "",
    val type: Int = -1  // -1无认证 0个人认证 1机构认证
)

@Serializable
data class SpaceVip(
    val type: Int = 0,  // 0无 1月度 2年度及以上
    val status: Int = 0,
    val label: SpaceVipLabel = SpaceVipLabel()
)

@Serializable
data class SpaceVipLabel(
    val text: String = ""
)

@Serializable
data class SpaceLiveRoom(
    val roomStatus: Int = 0,  // 0无房间 1有房间
    val liveStatus: Int = 0,  // 0未开播 1直播中
    val url: String = "",
    val title: String = "",
    val cover: String = "",
    @SerialName("roomid")
    val roomId: Long = 0
)

// /x/v2/space 聚合空间首屏响应
@Serializable
data class SpaceAggregateResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceAggregateData? = null
)

@Serializable
data class SpaceAggregateData(
    @SerialName("default_tab")
    val defaultTab: String = "",
    val card: SpaceAggregateCard? = null,
    val images: SpaceAggregateImages? = null,
    val live: SpaceLiveRoom? = null,
    val archive: SpaceAggregateArchive? = null,
    val article: SpaceAggregateArticleSection? = null,
    val audios: SpaceAggregateAudioSection? = null
)

@Serializable
data class SpaceAggregateCard(
    val mid: String = "",
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val sex: String = "",
    val attention: Int = 0,
    val fans: Int = 0,
    @SerialName("official_verify")
    val officialVerify: SpaceOfficial = SpaceOfficial(),
    val vip: SpaceVip = SpaceVip(),
    @SerialName("level_info")
    val levelInfo: SpaceAggregateLevelInfo = SpaceAggregateLevelInfo(),
    val likes: SpaceAggregateLikes = SpaceAggregateLikes(),
    val relation: SpaceAggregateRelation = SpaceAggregateRelation()
)

@Serializable
data class SpaceAggregateLevelInfo(
    @SerialName("current_level")
    val currentLevel: Int = 0
)

@Serializable
data class SpaceAggregateLikes(
    @SerialName("like_num")
    val likeNum: Long = 0
)

@Serializable
data class SpaceAggregateRelation(
    val status: Int = 0,
    @SerialName("is_follow")
    val isFollow: Int = 0
)

@Serializable
data class SpaceAggregateImages(
    val imgUrl: String = "",
    @SerialName("night_imgurl")
    val nightImgUrl: String = ""
)

@Serializable
data class SpaceAggregateArchive(
    val count: Int = 0,
    val item: List<SpaceAggregateArchiveItem> = emptyList()
)

@Serializable
data class SpaceAggregateArchiveItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val author: String = "",
    val length: String = "",
    val play: Int = 0,
    val reply: Int = 0,
    val ctime: Long = 0,
    val tname: String = ""
)

@Serializable
data class SpaceAggregateArticleSection(
    val count: Int = 0,
    val item: List<SpaceArticleItem> = emptyList()
)

@Serializable
data class SpaceAggregateAudioSection(
    val count: Int = 0,
    val item: List<SpaceAudioItem> = emptyList()
)

// /x/space/wbi/arc/search UP主投稿视频列表
@Serializable
data class SpaceVideoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceVideoData? = null
)

@Serializable
data class SpaceVideoData(
    val list: SpaceVideoList = SpaceVideoList(),
    val page: SpacePage = SpacePage()
)

@Serializable
data class SpaceVideoList(
    val vlist: List<SpaceVideoItem> = emptyList()
)

@Serializable
data class SpacePage(
    val pn: Int = 1,  // 当前页
    val ps: Int = 30, // 每页数量
    val count: Int = 0 // 总视频数
)

@Serializable
data class SpaceVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val description: String = "",
    val play: Int = 0,
    val comment: Int = 0,
    val length: String = "",  // "10:24" 格式
    val created: Long = 0,    // 发布时间戳
    val author: String = "",
    val typeid: Int = 0,      //  分区 ID
    val typename: String = "" //  分区名称
)

// /x/relation/stat 粉丝关注数
@Serializable
data class RelationStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RelationStatData? = null
)

@Serializable
data class RelationStatData(
    val mid: Long = 0,
    val following: Int = 0,
    val follower: Int = 0
)

// /x/space/upstat UP主播放量获赞数
@Serializable
data class UpStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UpStatData? = null
)

@Serializable
data class UpStatData(
    val archive: ArchiveStatInfo = ArchiveStatInfo(),
    val likes: Long = 0
)

@Serializable
data class ArchiveStatInfo(
    val view: Long = 0  // 总播放量
)

//  视频分类
data class SpaceVideoCategory(
    val tid: Int,       // 分类 ID
    val name: String,   // 分类名称
    val count: Int      // 该分类下的视频数量
)

//  视频排序方式
enum class VideoSortOrder(val apiValue: String, val displayName: String) {
    PUBDATE("pubdate", "最新发布"),
    OLDEST_PUBDATE("pubdate", "最早发布"),
    CLICK("click", "最多播放"),
    STOW("stow", "最多收藏")
}

// ==========  合集和系列 Models ==========

@kotlinx.serialization.Serializable
data class SeasonsSeriesListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SeasonsSeriesData? = null
)

@kotlinx.serialization.Serializable
data class SeasonsSeriesData(
    val items_lists: SeasonsSeriesItems? = null
)

@kotlinx.serialization.Serializable
data class SeasonsSeriesItems(
    val seasons_list: List<SeasonItem> = emptyList(),  // 合集列表
    val series_list: List<SeriesItem> = emptyList()    // 系列列表
)

@kotlinx.serialization.Serializable
data class SeasonItem(
    val meta: SeasonMeta = SeasonMeta(),
    val recent_aids: List<Long> = emptyList()
)

@kotlinx.serialization.Serializable
data class SeasonMeta(
    val season_id: Long = 0,
    val name: String = "",
    val cover: String = "",
    val total: Int = 0,
    val description: String = "",
    val mid: Long = 0
)

@kotlinx.serialization.Serializable
data class SeriesItem(
    val meta: SeriesMeta = SeriesMeta(),
    val recent_aids: List<Long> = emptyList()
)

@kotlinx.serialization.Serializable
data class SeriesMeta(
    val series_id: Long = 0,
    val name: String = "",
    val cover: String = "",
    val total: Int = 0,
    val description: String = "",
    val creator: String = "",
    val ctime: Long = 0,
    val mtime: Long = 0,
    val mid: Long = 0
)

// 合集/系列内视频列表响应
@kotlinx.serialization.Serializable
data class SeasonArchivesResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SeasonArchivesData? = null
)

@kotlinx.serialization.Serializable
data class SeasonArchivesData(
    val aids: List<Long> = emptyList(),
    val archives: List<SeasonArchiveItem> = emptyList(),
    val meta: SeasonMeta = SeasonMeta(),
    val page: SeasonPage = SeasonPage()
)

@kotlinx.serialization.Serializable
data class SeasonArchiveItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: SeasonArchiveStat = SeasonArchiveStat()
)

@kotlinx.serialization.Serializable
data class SeasonArchiveStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0
)

@kotlinx.serialization.Serializable
data class SeasonPage(
    val page_num: Int = 1,
    val page_size: Int = 30,
    val total: Int = 0
)

//  系列视频列表响应
@kotlinx.serialization.Serializable
data class SeriesArchivesResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SeriesArchivesData? = null
)

@kotlinx.serialization.Serializable
data class SeriesArchivesData(
    val aids: List<Long> = emptyList(),
    val archives: List<SeriesArchiveItem> = emptyList(),
    val page: SeriesPage = SeriesPage()
)

@kotlinx.serialization.Serializable
data class SeriesArchiveItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: SeriesArchiveStat = SeriesArchiveStat()
)

@kotlinx.serialization.Serializable
data class SeriesArchiveStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0
)

@kotlinx.serialization.Serializable
data class SeriesPage(
    val num: Int = 1,
    val size: Int = 30,
    val total: Int = 0
)

// ==========  主页 Tab Models ==========

// 置顶视频响应 /x/space/top/arc
@kotlinx.serialization.Serializable
data class SpaceTopArcResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceTopArcData? = null
)

@kotlinx.serialization.Serializable
data class SpaceTopArcData(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: SpaceTopArcStat = SpaceTopArcStat(),
    val reason: String = ""  // 置顶理由
)

@kotlinx.serialization.Serializable
data class SpaceTopArcStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val favorite: Long = 0,
    val coin: Long = 0,
    val share: Long = 0,
    val like: Long = 0
)

// 个人公告响应 /x/space/notice
@kotlinx.serialization.Serializable
data class SpaceNoticeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: String = ""  // 公告内容（纯文本或 HTML）
)

// ==========  动态 Tab Models ==========

// 用户动态响应 /x/polymer/web-dynamic/v1/feed/space
@kotlinx.serialization.Serializable
data class SpaceDynamicResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceDynamicData? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicData(
    val has_more: Boolean = false,
    val offset: String = "",
    val items: List<SpaceDynamicItem> = emptyList()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicItem(
    val id_str: String = "",
    val modules: SpaceDynamicModules = SpaceDynamicModules(),
    val type: String = "",  // DYNAMIC_TYPE_AV, DYNAMIC_TYPE_DRAW, DYNAMIC_TYPE_WORD 等
    val visible: Boolean = true
)

@kotlinx.serialization.Serializable
data class SpaceDynamicModules(
    val module_author: SpaceDynamicAuthor? = null,
    val module_dynamic: SpaceDynamicContent? = null,
    val module_stat: SpaceDynamicStat? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicAuthor(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val pub_time: String = "",
    val pub_ts: Long = 0
)

@kotlinx.serialization.Serializable
data class SpaceDynamicContent(
    val desc: SpaceDynamicDesc? = null,
    val major: SpaceDynamicMajor? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicDesc(
    val text: String = "",
    val rich_text_nodes: List<SpaceDynamicRichText> = emptyList()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicRichText(
    val type: String = "",  // RICH_TEXT_NODE_TYPE_TEXT, RICH_TEXT_NODE_TYPE_EMOJI 等
    val text: String = "",
    val orig_text: String = "",
    val emoji: SpaceDynamicEmoji? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicEmoji(
    val icon_url: String = "",
    val size: Int = 1,
    val text: String = ""
)

@kotlinx.serialization.Serializable
data class SpaceDynamicMajor(
    val type: String = "",  // MAJOR_TYPE_ARCHIVE, MAJOR_TYPE_DRAW, MAJOR_TYPE_OPUS 等
    val archive: SpaceDynamicArchive? = null,
    val draw: SpaceDynamicDraw? = null,
    val opus: SpaceDynamicOpus? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicArchive(
    val aid: String = "",
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val duration_text: String = "",
    val stat: SpaceDynamicArchiveStat = SpaceDynamicArchiveStat()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicArchiveStat(
    val play: String = "",
    val danmaku: String = ""
)

@kotlinx.serialization.Serializable
data class SpaceDynamicDraw(
    val id: Long = 0,
    val items: List<SpaceDynamicDrawItem> = emptyList()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicDrawItem(
    val src: String = "",
    val width: Int = 0,
    val height: Int = 0
)

@kotlinx.serialization.Serializable
data class SpaceDynamicOpus(
    val summary: SpaceDynamicOpusSummary? = null,
    val pics: List<SpaceDynamicDrawItem> = emptyList(),
    val title: String = ""
)

@kotlinx.serialization.Serializable
data class SpaceDynamicOpusSummary(
    val text: String = "",
    val rich_text_nodes: List<SpaceDynamicRichText> = emptyList()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicStat(
    val comment: SpaceDynamicCount = SpaceDynamicCount(),
    val forward: SpaceDynamicCount = SpaceDynamicCount(),
    val like: SpaceDynamicCount = SpaceDynamicCount()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicCount(
    val count: Int = 0,
    val forbidden: Boolean = false
)

// ==========  Space Audio Models ==========

@Serializable
data class SpaceAudioResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: SpaceAudioData? = null
)

@Serializable
data class SpaceAudioData(
    val curPage: Int = 1,
    val pageCount: Int = 0,
    val totalSize: Int = 0,
    val pageSize: Int = 30,
    val data: List<SpaceAudioItem>? = null
)

@Serializable
data class SpaceAudioItem(
    val id: Long = 0,
    val uid: Long = 0,
    val uname: String = "",
    val author: String = "",
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val lyric: String = "",
    val crtype: Int = 0,
    val duration: Int = 0,
    val passtime: Long = 0,
    val curtime: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val ctime: Long = 0,
    val coin_num: Int = 0,
    val play_count: Int = 0,
    val reply_count: Int = 0,
    val share_count: Int = 0,
    val collect_count: Int = 0
)

// ==========  Space Article Models ==========

@Serializable
data class SpaceArticleResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceArticleData? = null
)

@Serializable
data class SpaceArticleData(
    val lists: List<SpaceArticleItem> = emptyList(),
    val pn: Int = 1,
    val ps: Int = 30,
    val total: Int = 0
)

@Serializable
data class SpaceArticleItem(
    val id: Long = 0,
    val category: SpaceArticleCategory? = null,
    val title: String = "",
    val summary: String = "",
    val banner_url: String = "",
    val template_id: Int = 0,
    val state: Int = 0,
    val author: SpaceArticleAuthor? = null,
    val stats: SpaceArticleStats? = null,
    val publish_time: Long = 0,
    val ctime: Long = 0,
    val mtime: Long = 0,
    val is_like: Boolean = false,
    val image_urls: List<String> = emptyList()
)

@Serializable
data class SpaceArticleCategory(
    val id: Int = 0,
    val parent_id: Int = 0,
    val name: String = ""
)

@Serializable
data class SpaceArticleAuthor(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class SpaceArticleStats(
    val view: Int = 0,
    val favorite: Int = 0,
    val like: Int = 0,
    val dislike: Int = 0,
    val reply: Int = 0,
    val share: Int = 0,
    val coin: Int = 0,
    val dynamic: Int = 0
)

