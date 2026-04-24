package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 播放器信息 API 响应
 * 
 * API: GET https://api.bilibili.com/x/player/v2
 * 参数: bvid, cid (或 aid, cid)
 * 
 * 主要用途:
 * - 获取视频章节信息 (view_points)
 * - 获取字幕信息 (subtitle)
 * - 获取在线人数 (online_count)
 */
@Serializable
data class PlayerInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayerInfoData? = null
)

@Serializable
data class PlayerInfoData(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    @SerialName("view_points")
    val viewPoints: List<ViewPoint> = emptyList(),
    @SerialName("online_count")
    val onlineCount: Int = 0,
    val subtitle: SubtitleInfo? = null,
    val interaction: PlayerInteractionInfo? = null,
    @SerialName("bgm_info")
    val bgmInfo: BgmInfo? = null
)

@Serializable
data class PlayerInteractionInfo(
    @SerialName("graph_version")
    val graphVersion: Long = 0,
    val msg: String = "",
    @SerialName("error_toast")
    val errorToast: String = "",
    val mark: Int = 0,
    @SerialName("need_reload")
    val needReload: Int = 0
)

@Serializable
data class BgmInfo(
    @SerialName("music_id")
    val musicId: String = "",
    @SerialName("music_title")
    val musicTitle: String = "",
    @SerialName("jump_url")
    val jumpUrl: String = ""
)

/**
 * 视频章节/看点信息
 * 
 * 用于在进度条上显示章节标记
 */
@Serializable
data class ViewPoint(
    val content: String = "",      // 章节名称
    val from: Int = 0,             // 开始秒数
    val to: Int = 0,               // 结束秒数
    val imgUrl: String = "",       // 章节缩略图 URL
    val logoUrl: String = "",
    val type: Int = 0
) {
    /** 章节开始时间（毫秒） */
    val fromMs: Long get() = from * 1000L
    
    /** 章节结束时间（毫秒） */
    val toMs: Long get() = to * 1000L
    
    /** 章节时长（秒） */
    val duration: Int get() = to - from
}

/**
 * 字幕信息
 */
@Serializable
data class SubtitleInfo(
    @SerialName("allow_submit")
    val allowSubmit: Boolean = false,
    val lan: String = "",
    @SerialName("lan_doc")
    val lanDoc: String = "",
    val subtitles: List<SubtitleItem> = emptyList()
)

@Serializable
data class SubtitleItem(
    val id: Long = 0,
    @SerialName("id_str")
    val idStr: String = "",
    val lan: String = "",
    @SerialName("lan_doc")
    val lanDoc: String = "",
    @SerialName("subtitle_url")
    val subtitleUrl: String = "",
    @SerialName("ai_status")
    val aiStatus: Int = 0,
    @SerialName("ai_type")
    val aiType: Int = 0,
    @SerialName("is_lock")
    val isLock: Boolean = false,
    val type: Int = 0
)

