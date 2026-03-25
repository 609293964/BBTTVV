package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// --- 1. 热搜模型 (保持不变) ---
@Serializable
data class HotSearchResponse(
    val data: HotSearchData? = null
)

@Serializable
data class HotSearchData(
    val trending: TrendingData? = null
)

@Serializable
data class TrendingData(
    val list: List<HotItem>? = null
)

@Serializable
data class HotItem(
    val keyword: String = "",
    val show_name: String = "",
    val icon: String = ""
)

// --- 1.1 默认搜索占位词 ---
@Serializable
data class SearchDefaultResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchDefaultData? = null
)

@Serializable
data class SearchDefaultData(
    @SerialName("show_name")
    val showName: String = "",
    val url: String = ""
)

// --- 2. 搜索结果模型 ---
@Serializable
data class SearchResponse(
    val data: SearchData? = null
)

@Serializable
data class SearchData(
    val result: List<SearchResultCategory>? = null
)

@Serializable
data class SearchResultCategory(
    val result_type: String = "",
    val data: List<SearchVideoItem>? = null
)

//  [新增] 分类搜索响应 (search/type API)
@Serializable
data class SearchTypeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchTypeData? = null
)

@Serializable
data class SearchTypeData(
    val page: Int = 1,
    val pagesize: Int = 20,
    val numResults: Int = 0,
    val numPages: Int = 0,
    val result: List<SearchVideoItem>? = null  // 直接返回视频列表
)

@Serializable
data class SearchVideoItem(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val author: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val play: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val video_review: Int = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    val duration: String = "",
    //  新增：发布时间戳（秒）
    //  新增：发布时间戳（秒）
    @Serializable(with = FlexibleLongSerializer::class)
    val pubdate: Long = 0,
    //  [修复] 添加 mid 字段，用于屏蔽过滤
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0
) {
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = id,
            bvid = bvid,
            //  核心修复：使用正则表达式清洗 HTML 标签和转义字符 
            title = title.replace(Regex("<.*?>"), "") // 去除 <em class="..."> 和 </em>
                .replace("&quot;", "\"")      // 修复双引号转义
                .replace("&amp;", "&")        // 修复 & 符号转义
                .replace("&lt;", "<")         // 修复 < 符号
                .replace("&gt;", ">"),        // 修复 > 符号

            pic = if (pic.startsWith("//")) "https:$pic" else pic,
            owner = Owner(mid = mid, name = author),
            stat = Stat(view = play, danmaku = video_review),
            duration = parseDuration(duration),
            //  传递发布时间
            pubdate = pubdate
        )
    }

    private fun parseDuration(raw: String): Int {
        if (raw.isBlank()) return 0
        if (raw.all { it.isDigit() }) return raw.toIntOrNull() ?: 0
        val parts = raw.split(":")
        return when (parts.size) {
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            else -> 0
        }
    }
}

private fun parseFlexibleNumber(raw: String): Double? {
    val text = raw.trim().replace(",", "")
    if (text.isEmpty()) return null
    return when {
        text.endsWith("万") -> text.removeSuffix("万").toDoubleOrNull()?.times(10_000.0)
        text.endsWith("亿") -> text.removeSuffix("亿").toDoubleOrNull()?.times(100_000_000.0)
        else -> text.toDoubleOrNull()
    }
}

object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        if (decoder !is JsonDecoder) return decoder.decodeLong()
        val element = decoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return 0L
        val content = runCatching { primitive.content }.getOrNull() ?: return 0L
        return parseFlexibleNumber(content)?.toLong() ?: 0L
    }
}

object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val element = decoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return 0
        val content = runCatching { primitive.content }.getOrNull() ?: return 0
        return parseFlexibleNumber(content)?.toInt() ?: 0
    }
}

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        if (decoder !is JsonDecoder) return decoder.decodeString()
        val element = decoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return ""
        return runCatching { primitive.content }.getOrNull() ?: ""
    }
}

object FlexibleImageUrlSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleImageUrl", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        if (decoder !is JsonDecoder) return decoder.decodeString()
        val element = decoder.decodeJsonElement()
        return extractFlexibleImageUrl(element) ?: ""
    }
}

private val PREFERRED_IMAGE_KEYS = listOf(
    "day", "night", "light", "dark",
    "image", "image_small", "img", "img_url", "url", "src", "default"
)

private fun extractFlexibleImageUrl(element: JsonElement?, depth: Int = 0): String? {
    if (element == null || depth > 8) return null
    return when (element) {
        is JsonPrimitive -> normalizeImageUrlCandidate(runCatching { element.content }.getOrNull())
        is JsonObject -> {
            PREFERRED_IMAGE_KEYS.firstNotNullOfOrNull { key ->
                extractFlexibleImageUrl(element[key], depth + 1)
            } ?: element.values.firstNotNullOfOrNull { child ->
                extractFlexibleImageUrl(child, depth + 1)
            }
        }
        is JsonArray -> element.firstNotNullOfOrNull { child ->
            extractFlexibleImageUrl(child, depth + 1)
        }
        else -> null
    }
}

private fun normalizeImageUrlCandidate(raw: String?): String? {
    val text = raw?.trim().orEmpty()
    if (text.isBlank()) return null
    if (text.equals("null", ignoreCase = true)) return null
    return if (
        text.startsWith("http://") ||
        text.startsWith("https://") ||
        text.startsWith("//") ||
        text.startsWith("/") ||
        text.contains("/")
    ) {
        text
    } else {
        null
    }
}

//  [新增] UP主搜索响应模型
@Serializable
data class SearchUpResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchUpData? = null
)

@Serializable
data class SearchArticleResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SearchArticleData? = null
)

@Serializable
data class SearchArticleData(
    val page: Int = 1,
    val pagesize: Int = 20,
    val numResults: Int = 0,
    val numPages: Int = 0,
    val result: List<SearchArticleItem>? = null
)

@Serializable
data class SearchArticleItem(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    val title: String = "",
    @SerialName("desc")
    val description: String = "",
    @SerialName("pub_time")
    @Serializable(with = FlexibleLongSerializer::class)
    val pubTime: Long = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val view: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val reply: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val like: Int = 0,
    @SerialName("image_urls")
    val imageUrls: List<String> = emptyList(),
    @SerialName("category_name")
    val categoryName: String = "",
    @SerialName("category_id")
    @Serializable(with = FlexibleIntSerializer::class)
    val categoryId: Int = 0,
    @SerialName("templateId")
    @Serializable(with = FlexibleIntSerializer::class)
    val templateId: Int = 0
) {
    fun cleanupFields(): SearchArticleItem {
        return copy(
            title = title.replace(Regex("<.*?>"), ""),
            imageUrls = imageUrls.mapNotNull { url ->
                normalizeImageUrlCandidate(url)?.let { normalized ->
                    when {
                        normalized.startsWith("//") -> "https:$normalized"
                        normalized.startsWith("http://") -> normalized.replace("http://", "https://")
                        else -> normalized
                    }
                }
            }
        )
    }
}

@Serializable
data class SearchUpData(
    val page: Int = 1,
    val pagesize: Int = 20,
    val numResults: Int = 0,
    val numPages: Int = 0,
    val result: List<SearchUpItem>? = null  // 直接返回 UP 主列表
)

// --- 3.  UP主 搜索结果模型 ---
@Serializable
data class SearchUpItem(
    val mid: Long = 0,
    val uname: String = "",
    val usign: String = "", // 个性签名
    val upic: String = "", // 头像
    val fans: Int = 0, // 粉丝数
    val videos: Int = 0, // 视频数
    val level: Int = 0, // 等级
    val official_verify: SearchOfficialVerify? = null,
    val is_senior_member: Int = 0 // 是否硬核会员
) {
    fun cleanupFields(): SearchUpItem {
        return this.copy(
            uname = uname.replace(Regex("<.*?>"), ""),
            usign = usign.replace(Regex("<.*?>"), ""),
            upic = if (upic.startsWith("//")) "https:$upic" else upic
        )
    }
}

@Serializable
data class SearchOfficialVerify(
    val type: Int = -1, // 0: 个人, 1: 机构, -1: 无
    val desc: String = ""
)

// --- 4.  搜索类型枚举 ---
enum class SearchType(val value: String, val displayName: String) {
    VIDEO("video", "视频"),
    UP("bili_user", "UP主"),
    BANGUMI("media_bangumi", "番剧"),
    MEDIA_FT("media_ft", "影视"),
    LIVE("live_room", "直播"),
    ARTICLE("article", "专栏");
    
    companion object {
        fun fromValue(value: String): SearchType {
            return entries.find { it.value == value } ?: VIDEO
        }
    }
}

// --- 5.  搜索建议模型 ---
@Serializable
data class SearchSuggestResponse(
    val code: Int = 0,
    val result: SearchSuggestResult? = null
)

@Serializable
data class SearchSuggestResult(
    val tag: List<SearchSuggestTag>? = null
)

@Serializable
data class SearchSuggestTag(
    val value: String = "",    // 搜索建议词
    val name: String = "",     // 显示名称 (可能包含高亮)
    val ref: Int = 0,
    val spid: Int = 0
)

// --- 6. [新增] 直播搜索结果模型 ---
@Serializable
data class LiveRoomSearchResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveRoomSearchData? = null
)

@Serializable
data class LiveRoomSearchData(
    val page: Int = 1,
    val pagesize: Int = 20,
    val numResults: Int = 0,
    val numPages: Int = 0,
    val result: List<LiveRoomSearchItem>? = null
)

@Serializable
data class LiveRoomSearchItem(
    val roomid: Long = 0,           // 房间号
    val uid: Long = 0,              // 主播 UID
    val title: String = "",         // 直播标题
    val uname: String = "",         // 主播名
    val uface: String = "",         // 主播头像
    val cover: String = "",         // 直播封面 (user_cover)
    val online: Int = 0,            // 在线人数
    val live_status: Int = 0,       // 直播状态 0=未开播 1=直播中 2=轮播
    val short_id: Int = 0,          // 短号
    val area_v2_name: String = "",  // 分区名
    val area_v2_parent_name: String = "", // 父分区名
    val cate_name: String = "",     // 分类名 (备用)
    val tags: String = "",          // 标签
    val hit_columns: List<String>? = null  // 高亮字段
) {
    fun cleanupFields(): LiveRoomSearchItem {
        return this.copy(
            title = title.replace(Regex("<.*?>"), ""),
            uname = uname.replace(Regex("<.*?>"), ""),
            uface = if (uface.startsWith("//")) "https:$uface" else uface,
            cover = if (cover.startsWith("//")) "https:$cover" else cover
        )
    }
}
