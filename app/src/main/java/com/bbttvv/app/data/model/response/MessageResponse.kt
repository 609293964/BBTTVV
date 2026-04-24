// 私信模块响应模型
package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// ==================== 未读私信数 ====================

@Serializable
data class MessageUnreadResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: MessageUnreadData? = null
)

@Serializable
data class MessageUnreadData(
    val unfollow_unread: Int = 0,    // 未读未关注用户私信数
    val follow_unread: Int = 0,       // 未读已关注用户私信数
    val unfollow_push_msg: Int = 0,   // 未读未关注用户推送消息数
    val dustbin_unread: Int = 0,      // 未读被拦截私信数
    val custom_unread: Int = 0        // 未读客服消息数
)

// ==================== 会话列表 ====================

@Serializable
data class SessionListResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: SessionListData? = null
)

@Serializable
data class SessionListData(
    val session_list: List<SessionItem>? = null,
    val has_more: Int = 0,
    val group_count: Int = 0,
    val system_msg: SessionSystemMsg? = null
)

@Serializable
data class SessionItem(
    val talker_id: Long = 0,           // 聊天对象ID
    val session_type: Int = 1,          // 1=用户, 2=粉丝团
    val at_seqno: Long = 0,
    val top_ts: Long = 0,              // 置顶时间戳 (0=未置顶)
    val group_name: String = "",
    val group_cover: String = "",
    val is_follow: Int = 0,            // 是否关注了对方
    val is_dnd: Int = 0,               // 是否免打扰
    val ack_seqno: Long = 0,           // 最近已读消息序列号
    val ack_ts: Long = 0,
    val session_ts: Long = 0,
    val unread_count: Int = 0,          // 未读消息数
    val last_msg: SessionMessage? = null,  // 最近一条消息
    val group_type: Int = 0,
    val can_fold: Int = 0,
    val status: Int = 0,
    val max_seqno: Long = 0,
    val new_push_msg: Int = 0,
    val setting: Int = 0,
    val is_guardian: Int = 0,
    val is_intercept: Int = 0,
    val live_status: Int = 0,          // 是否正在直播
    val account_info: SessionAccountInfo? = null,  // 用户信息
    val biz: Int = 0
)

@Serializable
data class SessionMessage(
    val sender_uid: Long = 0,           // 发送者MID
    val receiver_type: Int = 1,         // 接收者类型
    val receiver_id: Long = 0,          // 接收者ID
    val msg_type: Int = 1,              // 消息类型
    val content: String = "",           // 消息内容 (JSON字符串)
    val msg_seqno: Long = 0,            // 消息序列号
    val timestamp: Long = 0,            // 发送时间戳
    val at_uids: List<Long>? = null,
    val msg_key: Long = 0,              // 消息唯一ID
    val msg_status: Int = 0,            // 0=正常, 1=已撤回
    val notify_code: String = "",
    val new_face_version: Int = 0,
    val msg_source: Int = 0             // 消息来源
)

@Serializable
data class SessionAccountInfo(
    @SerialName("name")
    val name: String = "",              // 用户昵称
    @SerialName("pic_url")
    val pic_url: String = "",            // 用户头像 (Web端字段名)
    @SerialName("face")
    val face: String = ""                // 用户头像 (备用字段名, 部分API返回这个)
) {
    // 智能获取头像URL (优先使用pic_url，回退使用face)
    val avatarUrl: String
        get() = pic_url.ifEmpty { face }
}

@Serializable
data class SessionSystemMsg(
    val msg_count: Int = 0
)

// ==================== 私信消息记录 ====================

@Serializable
data class MessageHistoryResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: MessageHistoryData? = null
)

@Serializable
data class MessageHistoryData(
    val messages: List<PrivateMessageItem>? = null,
    val has_more: Int = 0,
    val min_seqno: Long = 0,
    val max_seqno: Long = 0,
    val e_infos: List<EmoteInfo>? = null  // 表情信息
)

@Serializable
data class PrivateMessageItem(
    val sender_uid: Long = 0,           // 发送者MID
    val receiver_type: Int = 1,         // 接收者类型
    val receiver_id: Long = 0,          // 接收者ID
    val msg_type: Int = 1,              // 消息类型
    val content: String = "",           // 消息内容 (JSON字符串)
    val msg_seqno: Long = 0,            // 消息序列号
    val timestamp: Long = 0,            // 发送时间戳
    val at_uids: List<Long>? = null,
    val msg_key: Long = 0,              // 消息唯一ID
    val msg_status: Int = 0,            // 0=正常, 1=已撤回
    val sys_cancel: Boolean = false,    // 系统撤回
    val notify_code: String = "",
    val new_face_version: Int = 0,
    val msg_source: Int = 0             // 消息来源
)

@Serializable
data class EmoteInfo(
    val text: String = "",              // 表情名称，如 [doge]
    val url: String = "",               // 表情图片URL
    val size: Int = 1                   // 表情尺寸：1=小, 2=大
)

// ==================== 发送私信 ====================

@Serializable
data class SendMessageResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: SendMessageData? = null
)

@Serializable
data class SendMessageData(
    val msg_key: Long = 0,              // 消息唯一ID
    val msg_content: String = "",       // 发送的私信内容
    val e_infos: List<EmoteInfo>? = null,
    val key_hit_infos: KeyHitInfo? = null
)

@Serializable
data class KeyHitInfo(
    val toast: String = "",             // 提示信息
    val rule_id: Int = 0
)

// ==================== 消息中心顶部未读 ====================

@Serializable
data class MessageFeedUnreadResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: MessageFeedUnreadData? = null
)

@Serializable
data class MessageFeedUnreadData(
    val reply: Int = 0,
    val at: Int = 0,
    val like: Int = 0,
    @SerialName("sys_msg")
    val sysMsg: Int = 0
)

// ==================== 回复我的 / @我 / 收到的赞 ====================

@Serializable
data class MessageFeedCursor(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val time: Long = 0,
    @SerialName("is_end")
    val isEnd: Boolean = false
)

@Serializable
data class MessageFeedUser(
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    val nickname: String = "",
    @Serializable(with = MessageFeedImageUrlSerializer::class)
    val avatar: String = ""
)

@Serializable
data class MessageFeedReplyResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: MessageFeedReplyData? = null
)

@Serializable
data class MessageFeedReplyData(
    val cursor: MessageFeedCursor? = null,
    val items: List<MessageFeedReplyItem>? = null,
    @SerialName("last_view_at")
    @Serializable(with = FlexibleLongSerializer::class)
    val lastViewAt: Long = 0
)

@Serializable
data class MessageFeedReplyItem(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val user: MessageFeedUser? = null,
    val item: MessageFeedReplyContent? = null,
    val counts: Int = 0,
    @SerialName("is_multi")
    val isMulti: Int = 0,
    @SerialName("reply_time")
    val replyTime: Int = 0
)

@Serializable
data class MessageFeedReplyContent(
    val business: String = "",
    @SerialName("business_id")
    val businessId: Int = 0,
    val title: String = "",
    @Serializable(with = MessageFeedImageUrlSerializer::class)
    val image: String = "",
    val uri: String = "",
    @SerialName("native_uri")
    val nativeUri: String = "",
    @SerialName("root_reply_content")
    val rootReplyContent: String = "",
    @SerialName("source_content")
    val sourceContent: String = "",
    @SerialName("target_reply_content")
    val targetReplyContent: String = ""
)

@Serializable
data class MessageFeedAtResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: MessageFeedAtData? = null
)

@Serializable
data class MessageFeedAtData(
    val cursor: MessageFeedCursor? = null,
    val items: List<MessageFeedAtItem>? = null
)

@Serializable
data class MessageFeedAtItem(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val user: MessageFeedUser? = null,
    val item: MessageFeedAtContent? = null,
    @SerialName("at_time")
    @Serializable(with = FlexibleLongSerializer::class)
    val atTime: Long = 0
)

@Serializable
data class MessageFeedAtContent(
    val type: String = "",
    val business: String = "",
    @SerialName("business_id")
    @Serializable(with = FlexibleLongSerializer::class)
    val businessId: Long = 0,
    val title: String = "",
    @Serializable(with = MessageFeedImageUrlSerializer::class)
    val image: String = "",
    val uri: String = "",
    @SerialName("native_uri")
    val nativeUri: String = "",
    @SerialName("subject_id")
    @Serializable(with = FlexibleLongSerializer::class)
    val subjectId: Long = 0,
    @SerialName("root_id")
    @Serializable(with = FlexibleLongSerializer::class)
    val rootId: Long = 0,
    @SerialName("target_id")
    @Serializable(with = FlexibleLongSerializer::class)
    val targetId: Long = 0,
    @SerialName("source_id")
    @Serializable(with = FlexibleLongSerializer::class)
    val sourceId: Long = 0,
    @SerialName("source_content")
    val sourceContent: String = "",
    @SerialName("hide_reply_button")
    val hideReplyButton: Boolean = false
)

@Serializable
data class MessageFeedLikeResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: MessageFeedLikeData? = null
)

@Serializable
data class MessageFeedLikeData(
    val latest: MessageFeedLikeBucket? = null,
    val total: MessageFeedLikeBucket? = null
)

@Serializable
data class MessageFeedLikeBucket(
    val cursor: MessageFeedCursor? = null,
    val items: List<MessageFeedLikeItem>? = null
)

@Serializable
data class MessageFeedLikeItem(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val users: List<MessageFeedUser>? = null,
    val item: MessageFeedLikeContent? = null,
    val counts: Int = 0,
    @SerialName("like_time")
    val likeTime: Int = 0,
    @SerialName("notice_state")
    val noticeState: Int = 0
)

@Serializable
data class MessageFeedLikeContent(
    val business: String = "",
    @SerialName("business_id")
    val businessId: Int = 0,
    val title: String = "",
    @Serializable(with = MessageFeedImageUrlSerializer::class)
    val image: String = "",
    val uri: String = "",
    @SerialName("native_uri")
    val nativeUri: String = ""
)

// ==================== 系统通知 ====================

@Serializable
data class SystemNoticeResponse(
    val code: Int = 0,
    val message: String = "",
    val msg: String = "",
    val ttl: Int = 1,
    val data: List<SystemNoticeItem>? = null
)

@Serializable
data class SystemNoticeItem(
    val id: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val cursor: Long = 0,
    val title: String = "",
    @Serializable(with = SystemNoticeContentSerializer::class)
    val content: String = "",
    @SerialName("time_at")
    val timeAt: String = ""
)

object SystemNoticeContentSerializer : kotlinx.serialization.KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SystemNoticeContent", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val raw = decoder.decodeString()
        return runCatching {
            val element = Json.parseToJsonElement(raw)
            if (element is JsonObject) {
                element["web"]?.jsonPrimitive?.contentOrNull ?: raw
            } else {
                raw
            }
        }.getOrDefault(raw)
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

object MessageFeedImageUrlSerializer : kotlinx.serialization.KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MessageFeedImageUrl", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val raw = if (decoder is kotlinx.serialization.json.JsonDecoder) {
            decoder.decodeJsonElement().jsonPrimitive.contentOrNull.orEmpty()
        } else {
            decoder.decodeString()
        }
        val value = raw.trim()
        return when {
            value.startsWith("//") -> "https:$value"
            value.startsWith("http://") || value.startsWith("https://") -> value
            else -> value
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

