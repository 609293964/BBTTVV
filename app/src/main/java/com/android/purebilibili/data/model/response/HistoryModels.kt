// 文件路径: data/model/response/HistoryModels.kt
package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

/**
 * 历史记录相关数据模型
 * 从 ListModels.kt 拆分出来，提高代码可维护性
 */

// --- 历史记录响应（支持游标分页）---
@Serializable
data class HistoryResponse(
    val code: Int = 0,
    val message: String = "",
    val data: HistoryListData? = null
)

@Serializable
data class HistoryListData(
    val list: List<HistoryData>? = null,
    val cursor: HistoryCursor? = null
)

@Serializable
data class HistoryCursor(
    val max: Long = 0,
    val view_at: Long = 0,
    val business: String = "",
    val ps: Int = 30
)

// --- 历史记录数据项 ---
@Serializable
data class HistoryData(
    val title: String = "",
    val pic: String = "",
    val cover: String = "",
    val covers: List<String> = emptyList(),
    val author_name: String = "",
    val author_face: String = "",
    val author_mid: Long = 0,
    val duration: Int = 0,
    val history: HistoryPage? = null,
    val stat: Stat? = null,
    val progress: Int = -1,
    val view_at: Long = 0
) {
    fun toVideoItem(): VideoItem {
        val rawBusiness = history?.business.orEmpty()
        val business = HistoryBusiness.fromValue(rawBusiness)
        val resolvedArticleId = when {
            rawBusiness == "article-list" && (history?.cid ?: 0) > 0L -> history?.cid ?: 0L
            business == HistoryBusiness.ARTICLE -> history?.oid ?: 0L
            else -> history?.oid ?: 0L
        }
        return VideoItem(
            id = if (business == HistoryBusiness.ARTICLE) resolvedArticleId else (history?.oid ?: 0L),
            bvid = history?.bvid ?: "",
            cid = history?.cid ?: 0,
            title = title,
            pic = listOf(cover, pic, covers.firstOrNull().orEmpty()).firstOrNull { it.isNotBlank() }.orEmpty(),
            owner = Owner(mid = author_mid, name = author_name, face = author_face),
            stat = stat ?: Stat(),
            duration = duration,
            progress = progress,
            view_at = view_at
        )
    }
    
    /**
     * 转换为 UI 层使用的 HistoryItem（包含完整导航信息）
     */
    fun toHistoryItem(): HistoryItem {
        val rawBusiness = history?.business.orEmpty()
        val business = HistoryBusiness.fromValue(rawBusiness)
        return HistoryItem(
            videoItem = toVideoItem(),
            business = business,
            epid = history?.epid ?: 0,
            seasonId = if (business == HistoryBusiness.PGC) (history?.oid ?: 0) else 0,
            roomId = if (business == HistoryBusiness.LIVE) (history?.oid ?: 0) else 0,
            cid = history?.cid ?: 0,
            page = history?.page ?: 1,
            progress = progress
        )
    }
}

@Serializable
data class HistoryPage(
    val oid: Long = 0,
    val bvid: String = "",
    val epid: Long = 0,        // 番剧剧集 ID
    val cid: Long = 0,         // 分 P cid
    val business: String = "", // 内容类型: archive/pgc/live
    val page: Int = 1          // 分 P 号
)

/**
 * 历史记录内容类型
 */
enum class HistoryBusiness(val value: String) {
    ARCHIVE("archive"),   // 普通视频
    PGC("pgc"),           // 番剧/影视
    LIVE("live"),         // 直播
    ARTICLE("article"),   // 文章
    UNKNOWN("");          // 未知
    
    companion object {
        fun fromValue(value: String): HistoryBusiness {
            val normalizedValue = value.trim()
            return when {
                normalizedValue.equals(ARCHIVE.value, ignoreCase = true) -> ARCHIVE
                normalizedValue.equals(PGC.value, ignoreCase = true) -> PGC
                normalizedValue.equals(LIVE.value, ignoreCase = true) -> LIVE
                normalizedValue.contains(ARTICLE.value, ignoreCase = true) -> ARTICLE
                else -> UNKNOWN
            }
        }
    }
}

/**
 * UI 层使用的历史记录项（包含完整导航信息）
 */
data class HistoryItem(
    val videoItem: VideoItem,     // 通用视频信息（兼容现有 UI）
    val business: HistoryBusiness, // 内容类型
    val epid: Long = 0,           // 番剧剧集 ID
    val seasonId: Long = 0,       // 番剧季 ID (oid)
    val roomId: Long = 0,         // 直播间 ID
    val cid: Long = 0,            // 历史分P CID
    val page: Int = 1,            // 历史分P号
    val progress: Int = -1        // 服务端历史进度（秒）
)
