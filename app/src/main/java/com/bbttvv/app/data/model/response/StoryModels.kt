// 文件路径: data/model/response/StoryModels.kt
package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * B站故事模式 (Story Mode) 竖屏短视频数据模型
 * API: app.bilibili.com/x/v2/feed/index/story
 */

@Serializable
data class StoryResponse(
    val code: Int = 0,
    val message: String = "",
    val data: StoryData? = null
)

@Serializable
data class StoryData(
    val items: List<StoryItem>? = null
)

@Serializable
data class StoryItem(
    val id: Long = 0,
    val goto: String = "",  // "av" = 视频
    val uri: String = "",
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    
    // 视频参数
    @SerialName("player_args")
    val playerArgs: StoryPlayerArgs? = null,
    
    // 统计数据
    val stat: StoryStat? = null,
    
    // UP 主信息
    val owner: StoryOwner? = null,
    
    // 标签
    val tag: StoryTag? = null,
    
    // 时长 (秒)
    val duration: Int = 0
) {
    /**
     * 转换为通用 VideoItem 以便复用现有播放器
     */
    fun toVideoItem(): VideoItem {
        return VideoItem(
            bvid = playerArgs?.bvid ?: "",
            title = title,
            pic = cover,
            duration = duration,
            owner = Owner(
                mid = owner?.mid ?: 0,
                name = owner?.name ?: "",
                face = owner?.face ?: ""
            ),
            stat = Stat(
                view = stat?.view ?: 0,
                danmaku = stat?.danmaku ?: 0,
                reply = stat?.reply ?: 0,
                favorite = stat?.favorite ?: 0,
                coin = stat?.coin ?: 0,
                share = stat?.share ?: 0,
                like = stat?.like ?: 0
            )
        )
    }
}

@Serializable
data class StoryPlayerArgs(
    val aid: Long = 0,
    val cid: Long = 0,
    val bvid: String = "",
    val type: String = "av"  // av = 普通视频
)

@Serializable
data class StoryStat(
    val view: Int = 0,
    val like: Int = 0,
    val reply: Int = 0,
    val share: Int = 0,
    val coin: Int = 0,
    val favorite: Int = 0,
    val danmaku: Int = 0
)

@Serializable
data class StoryOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val fans: Int = 0,
    
    @SerialName("official_verify")
    val officialVerify: OfficialVerify? = null
)

@Serializable
data class OfficialVerify(
    val type: Int = -1,  // 0=个人认证, 1=机构认证, -1=无
    val desc: String = ""
)

@Serializable
data class StoryTag(
    @SerialName("tag_id")
    val tagId: Long = 0,
    @SerialName("tag_name")
    val tagName: String = ""
)

