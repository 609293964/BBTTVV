package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class VideoDetailResponse(
    //  之前报错是因为缺了下面这行
    val code: Int = 0,
    val message: String = "",
    //  补上就好了
    val data: ViewInfo? = null
)

/**
 *  视频尺寸信息
 * 用于判断横竖屏
 */
@Serializable
data class Dimension(
    val width: Int = 0,
    val height: Int = 0,
    val rotate: Int = 0
) {
    /** 是否为竖屏视频（考虑 rotate=90/270 的场景） */
    val isVertical: Boolean
        get() {
            if (width <= 0 || height <= 0) return false
            val normalizedRotate = ((rotate % 360) + 360) % 360
            val shouldSwap = normalizedRotate == 90 || normalizedRotate == 270
            val effectiveWidth = if (shouldSwap) height else width
            val effectiveHeight = if (shouldSwap) width else height
            return effectiveHeight > effectiveWidth
        }
}

@Serializable
data class ViewInfo(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    val pic: String = "",
    val pubdate: Long = 0,  //  发布时间戳 (秒)
    val tname: String = "", //  分区名称
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    val pages: List<Page> = emptyList(),
    @SerialName("is_stein_gate")
    val isSteinGate: Int = 0,
    val dimension: Dimension? = null,  //  视频尺寸信息
    val ugc_season: UgcSeason? = null  //  [新增] 视频合集信息
)

@Serializable
data class Page(
    val cid: Long = 0,
    val page: Int = 0,
    val from: String = "",
    val part: String = "",
    val duration: Long = 0
)

//  视频标签响应
@Serializable
data class VideoTagResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<VideoTag>? = null
)

@Serializable
data class VideoTag(
    val tag_id: Long = 0,
    val tag_name: String = "",
    val cover: String = "",
    val content: String = "",
    val short_content: String = "",
    val type: Int = 0,
    val state: Int = 0,
    val count: VideoTagCount? = null
)

@Serializable
data class VideoTagCount(
    val view: Int = 0,
    val use: Int = 0,
    val atten: Int = 0
)

//  [新增] 视频合集 (UGC Season) 数据结构
@Serializable
data class UgcSeason(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    val mid: Long = 0,
    val ep_count: Int = 0,  // 总集数
    val sections: List<UgcSection> = emptyList()
)

@Serializable
data class UgcSection(
    val season_id: Long = 0,
    val id: Long = 0,
    val title: String = "",
    val episodes: List<UgcEpisode> = emptyList()
)

@Serializable
data class UgcEpisode(
    val id: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val arc: UgcEpisodeArc? = null
)

@Serializable
data class UgcEpisodeArc(
    val aid: Long = 0,
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val stat: Stat? = null
)

