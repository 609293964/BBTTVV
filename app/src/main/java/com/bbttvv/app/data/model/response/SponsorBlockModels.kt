// 文件路径: data/model/response/SponsorBlockModels.kt
package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable

/**
 * 空降助手 (BilibiliSponsorBlock) 数据模型
 * API 文档: https://github.com/hanydd/BilibiliSponsorBlock/wiki/API
 */

/**
 * 片段类别
 */
object SponsorCategory {
    const val SPONSOR = "sponsor"           // 赞助/恰饭广告
    const val SELFPROMO = "selfpromo"       // 自我推广（关注、点赞提示）
    const val INTRO = "intro"               // 开场动画
    const val OUTRO = "outro"               // 片尾动画
    const val INTERACTION = "interaction"   // 一键三连提示
    const val PREVIEW = "preview"           // 预告/回顾片段
    const val FILLER = "filler"             // 无关片段/跑题
    const val POI_HIGHLIGHT = "poi_highlight" // 精彩片段标记
    
    val ALL_SKIP_CATEGORIES = listOf(
        SPONSOR, SELFPROMO, INTRO, OUTRO, INTERACTION, PREVIEW, FILLER
    )
    
    fun getCategoryName(category: String): String = when (category) {
        SPONSOR -> "广告/恰饭"
        SELFPROMO -> "自我推广"
        INTRO -> "开场动画"
        OUTRO -> "片尾动画"
        INTERACTION -> "互动提示"
        PREVIEW -> "预告/回顾"
        FILLER -> "无关片段"
        POI_HIGHLIGHT -> "精彩片段"
        else -> category
    }
}

/**
 * 片段动作类型
 */
object SponsorActionType {
    const val SKIP = "skip"     // 自动跳过
    const val MUTE = "mute"     // 静音
    const val FULL = "full"     // 整个视频标记
    const val POI = "poi"       // 精彩片段点
}

@Serializable
enum class SponsorBlockMarkerMode {
    OFF,
    SPONSOR_ONLY,
    ALL_SKIPPABLE
}

fun resolveSponsorBlockMarkerMode(rawValue: String?): SponsorBlockMarkerMode {
    return SponsorBlockMarkerMode.entries.firstOrNull { entry ->
        entry.name.equals(rawValue, ignoreCase = true)
    } ?: SponsorBlockMarkerMode.SPONSOR_ONLY
}

fun SponsorBlockMarkerMode.displayLabel(): String = when (this) {
    SponsorBlockMarkerMode.OFF -> "关闭"
    SponsorBlockMarkerMode.SPONSOR_ONLY -> "仅恰饭"
    SponsorBlockMarkerMode.ALL_SKIPPABLE -> "全部可跳过"
}

/**
 * 空降片段数据
 */
@Serializable
data class SponsorSegment(
    val segment: List<Float>,         // [起始时间, 结束时间] 秒
    val UUID: String,                 // 片段唯一标识
    val category: String,             // 片段类别
    val actionType: String,           // 动作类型
    val locked: Int = 0,              // 是否锁定
    val votes: Int = 0,               // 投票数
    val videoDuration: Float = 0f     // 提交时的视频时长
) {
    /** 起始时间（秒）*/
    val startTime: Float get() = segment.getOrNull(0) ?: 0f
    
    /** 结束时间（秒）*/
    val endTime: Float get() = segment.getOrNull(1) ?: 0f
    
    /** 起始时间（毫秒）*/
    val startTimeMs: Long get() = (startTime * 1000).toLong()
    
    /** 结束时间（毫秒）*/
    val endTimeMs: Long get() = (endTime * 1000).toLong()
    
    /** 片段时长（秒）*/
    val duration: Float get() = endTime - startTime
    
    /** 类别显示名称 */
    val categoryName: String get() = SponsorCategory.getCategoryName(category)
    
    /** 是否为跳过类型 */
    val isSkipType: Boolean get() = actionType == SponsorActionType.SKIP
}

data class SponsorProgressMarker(
    val segmentId: String,
    val category: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

