// 文件路径: data/model/response/DynamicModels.kt
package com.bbttvv.app.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 *  动态页面数据模型
 * API: x/polymer/web-dynamic/v1/feed/all
 */

// --- 顶层响应 ---
@Serializable
data class DynamicFeedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicFeedData? = null
)

@Serializable
data class DynamicFeedData(
    val items: List<DynamicItem> = emptyList(),
    val offset: String = "", // 分页偏移量
    val has_more: Boolean = false,
    val update_baseline: String = "",
    val update_num: Int = 0
)

@Serializable
data class DynamicDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicDetailData? = null
)

@Serializable
data class DynamicDetailData(
    val item: DynamicItem? = null
)

// --- 动态卡片 ---
@Serializable
data class DynamicItem(
    val id_str: String = "",
    val type: String = "", // DYNAMIC_TYPE_AV, DYNAMIC_TYPE_DRAW, DYNAMIC_TYPE_WORD, DYNAMIC_TYPE_FORWARD
    val visible: Boolean = true,
    @Serializable(with = DynamicModulesFlexibleSerializer::class)
    val modules: DynamicModules = DynamicModules(),
    val orig: DynamicItem? = null,  //  转发动态的原始内容
    val basic: DynamicBasic? = null  //  [新增] 评论区参数
)

object DynamicModulesFlexibleSerializer : KSerializer<DynamicModules> {
    override val descriptor: SerialDescriptor = DynamicModules.serializer().descriptor

    override fun deserialize(decoder: Decoder): DynamicModules {
        val jsonDecoder = decoder as? JsonDecoder ?: return DynamicModules.serializer().deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()

        return when (element) {
            is JsonObject -> jsonDecoder.json.decodeFromJsonElement(DynamicModules.serializer(), element)
            is JsonArray -> {
                var merged = DynamicModules()
                var opusTitle: String? = null
                val opusParagraphTexts = mutableListOf<String>()
                val opusPics = mutableListOf<OpusPic>()
                element.forEach { node ->
                    val obj = node as? JsonObject ?: return@forEach
                    val parsed = jsonDecoder.json.decodeFromJsonElement(DynamicModules.serializer(), obj)
                    merged = merged.copy(
                        module_author = parsed.module_author ?: merged.module_author,
                        module_dynamic = parsed.module_dynamic ?: merged.module_dynamic,
                        module_stat = parsed.module_stat ?: merged.module_stat
                    )

                    val moduleType = obj["module_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (moduleType == "MODULE_TYPE_TITLE" || obj["module_title"] != null) {
                        opusTitle = obj["module_title"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                    }
                    if (moduleType == "MODULE_TYPE_CONTENT" || obj["module_content"] != null) {
                        val paragraphs = obj["module_content"]?.jsonObject?.get("paragraphs") as? JsonArray
                        paragraphs?.forEach { paragraphNode ->
                            val paragraph = paragraphNode as? JsonObject ?: return@forEach
                            extractParagraphText(paragraph)?.let { opusParagraphTexts += it }
                            opusPics += extractParagraphPics(paragraph)
                        }
                    }
                }
                merged.normalizeWithOpusModules(
                    title = opusTitle,
                    paragraphTexts = opusParagraphTexts,
                    pics = opusPics
                )
            }
            else -> DynamicModules()
        }
    }

    override fun serialize(encoder: Encoder, value: DynamicModules) {
        DynamicModules.serializer().serialize(encoder, value)
    }

    private fun extractParagraphText(paragraph: JsonObject): String? {
        val nodes = paragraph["text"]?.jsonObject?.get("nodes") as? JsonArray ?: return null
        val text = buildString {
            nodes.forEach { node ->
                val words = (node as? JsonObject)
                    ?.get("word")
                    ?.jsonObject
                    ?.get("words")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: return@forEach
                append(words)
            }
        }.trim()
        return text.takeIf { it.isNotBlank() }
    }

    private fun extractParagraphPics(paragraph: JsonObject): List<OpusPic> {
        val pics = paragraph["pic"]?.jsonObject?.get("pics") as? JsonArray ?: return emptyList()
        return pics.mapNotNull { picNode ->
            val pic = picNode as? JsonObject ?: return@mapNotNull null
            val url = pic["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (url.isEmpty()) return@mapNotNull null
            OpusPic(
                url = url,
                width = pic["width"]?.jsonPrimitive?.intOrNull ?: 0,
                height = pic["height"]?.jsonPrimitive?.intOrNull ?: 0,
                size = pic["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            )
        }
    }

    private fun DynamicModules.normalizeWithOpusModules(
        title: String?,
        paragraphTexts: List<String>,
        pics: List<OpusPic>
    ): DynamicModules {
        val existing = module_dynamic
        val descText = paragraphTexts.joinToString(separator = "\n").trim()
        val cleanTitle = title?.trim().takeUnless { it.isNullOrBlank() }
        val hasDerivedContent = descText.isNotBlank() || pics.isNotEmpty() || cleanTitle != null
        if (!hasDerivedContent) return this

        val existingDesc = existing?.desc
        val existingDescText = existingDesc?.text.orEmpty()
        val mergedDescText = when {
            descText.isBlank() -> existingDescText
            existingDescText.isBlank() -> descText
            descText.length > existingDescText.length -> descText
            else -> existingDescText
        }
        val mergedDesc = if (mergedDescText.isNotBlank()) {
            DynamicDesc(
                text = mergedDescText,
                rich_text_nodes = if (mergedDescText == existingDescText) {
                    existingDesc?.rich_text_nodes.orEmpty()
                } else {
                    emptyList()
                }
            )
        } else {
            null
        }

        val existingMajor = existing?.major
        val existingOpus = existingMajor?.opus
        val mergedOpus = OpusMajor(
            jump_url = existingOpus?.jump_url.orEmpty(),
            title = cleanTitle ?: existingOpus?.title,
            summary = when {
                mergedDescText.isNotBlank() -> OpusSummary(text = mergedDescText)
                existingOpus?.summary != null -> existingOpus.summary
                else -> null
            },
            pics = if (pics.isNotEmpty()) pics else existingOpus?.pics.orEmpty()
        )
        val mergedMajor = DynamicMajor(
            type = "MAJOR_TYPE_OPUS",
            archive = existingMajor?.archive,
            draw = existingMajor?.draw,
            live_rcmd = existingMajor?.live_rcmd,
            opus = mergedOpus,
            ugc_season = existingMajor?.ugc_season
        )
        return copy(
            module_dynamic = DynamicContentModule(
                desc = mergedDesc ?: existingDesc,
                major = if (existingMajor == null || existingMajor.type.isBlank() || existingMajor.type == "MAJOR_TYPE_OPUS") {
                    mergedMajor
                } else {
                    existingMajor
                }
            )
        )
    }
}

//  [新增] 动态基础信息 - 包含评论区参数
@Serializable
data class DynamicBasic(
    val comment_id_str: String = "",   // 评论区 oid
    val comment_type: Int = 0,         // 评论区 type (1=视频, 11=图片, 17=动态)
    val rid_str: String = ""           // 资源 id
)

// --- 动态模块集合 ---
@Serializable
data class DynamicModules(
    val module_author: DynamicAuthorModule? = null,
    val module_dynamic: DynamicContentModule? = null,
    val module_stat: DynamicStatModule? = null
)

// --- 作者模块 ---
@Serializable
data class DynamicAuthorModule(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val pub_time: String = "", // "昨天 18:00"
    val pub_ts: Long = 0, // 时间戳
    val following: Boolean? = null,
    val official_verify: DynamicOfficialVerify? = null,
    val vip: DynamicVipInfo? = null,
    val decorate: DecorateInfo? = null
)

@Serializable
data class DynamicOfficialVerify(
    val type: Int = -1, // 0: 个人认证, 1: 机构认证, -1: 无
    val desc: String = ""
)

@Serializable
data class DynamicVipInfo(
    val type: Int = 0, // 0: 无, 1: 月度, 2: 年度
    val status: Int = 0,
    val nickname_color: String = "" // "#FB7299"
)

@Serializable
data class DecorateInfo(
    val card_url: String = "", // 装扮卡片 URL
    val name: String = ""
)

// --- 内容模块 ---
@Serializable
data class DynamicContentModule(
    val desc: DynamicDesc? = null,
    val major: DynamicMajor? = null
)

@Serializable
data class DynamicDesc(
    val text: String = "", // 动态文字内容
    val rich_text_nodes: List<RichTextNode> = emptyList()
)

@Serializable
data class RichTextNode(
    val type: String = "", // TEXT, EMOJI, AT, TOPIC
    val text: String = "",
    val emoji: EmojiInfo? = null,
    val jump_url: String? = null
)

@Serializable
data class EmojiInfo(
    val icon_url: String = "",
    val size: Int = 1,
    val text: String = ""
)

// --- 主要内容 (视频/图片/直播/图文) ---
@Serializable
data class DynamicMajor(
    val type: String = "", // MAJOR_TYPE_ARCHIVE, MAJOR_TYPE_DRAW, MAJOR_TYPE_LIVE_RCMD, MAJOR_TYPE_OPUS, MAJOR_TYPE_NONE
    val archive: ArchiveMajor? = null, // 视频
    val draw: DrawMajor? = null, // 图片
    val live_rcmd: LiveRcmdMajor? = null, //  直播
    val opus: OpusMajor? = null, //  [新增] 图文动态 (新版格式)
    val ugc_season: UgcSeasonMajor? = null // [新增] 合集
)

//  [新增] 图文动态 (MAJOR_TYPE_OPUS) - B站新版图文格式
@Serializable
data class OpusMajor(
    val jump_url: String = "",
    val pics: List<OpusPic> = emptyList(), // 图片列表
    val summary: OpusSummary? = null, // 文字摘要
    val title: String? = null // 标题 (可选)
)

@Serializable
data class OpusPic(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val size: Double = 0.0
)

@Serializable
data class OpusSummary(
    val text: String = "",
    val rich_text_nodes: List<RichTextNode> = emptyList()
)

//  直播推荐
@Serializable
data class LiveRcmdMajor(
    val content: String = "" // JSON string，需要解析
)

//  [新增] 合集/剧集 (MAJOR_TYPE_UGC_SEASON)
@Serializable
data class UgcSeasonMajor(
    @Serializable(with = FlexibleLongSerializer::class)
    val aid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val duration_text: String = "",
    val jump_url: String = "",
    val intro: String = "",
    val id: Long = 0, // season_id
    val sign_state: Int = 0,
    val type: Int = 0, // 1=合集
    val stat: UgcSeasonStat = UgcSeasonStat(),
    val archive: ArchiveMajor? = null // 播放第一集或最新一集
)

@Serializable
data class UgcSeasonStat(
    val play: String = "0",
    val danmaku: String = "0"
)

@Serializable
data class ArchiveMajor(
    val aid: String = "",
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val duration_text: String = "", // "10:24"
    val stat: ArchiveStat = ArchiveStat(),
    val jump_url: String = ""
)

@Serializable
data class ArchiveStat(
    val play: String = "0", // "123.4万"
    val danmaku: String = "0"
)

@Serializable
data class DrawMajor(
    val id: Long = 0,
    val items: List<DrawItem> = emptyList()
)

@Serializable
data class DrawItem(
    val src: String = "", // 图片 URL
    val width: Int = 0,
    val height: Int = 0
)

// --- 统计模块 ---
@Serializable
data class DynamicStatModule(
    val comment: StatItem = StatItem(),
    val forward: StatItem = StatItem(),
    val like: StatItem = StatItem()
)

@Serializable
data class StatItem(
    val count: Int = 0,
    val forbidden: Boolean = false
)

// --- 动态类型枚举 ---
enum class DynamicType(val apiValue: String) {
    VIDEO("DYNAMIC_TYPE_AV"),
    DRAW("DYNAMIC_TYPE_DRAW"),
    WORD("DYNAMIC_TYPE_WORD"),
    FORWARD("DYNAMIC_TYPE_FORWARD"),
    LIVE("DYNAMIC_TYPE_LIVE_RCMD"),
    OPUS("DYNAMIC_TYPE_DRAW"),  //  [新增] 图文动态 (使用 DRAW 类型，但 major 为 opus)
    UGC_SEASON("DYNAMIC_TYPE_UGC_SEASON"), // [新增] 合集/剧集
    UNKNOWN("UNKNOWN");
    
    companion object {
        fun fromApiValue(value: String): DynamicType {
            return entries.find { it.apiValue == value } ?: UNKNOWN
        }
    }
}

