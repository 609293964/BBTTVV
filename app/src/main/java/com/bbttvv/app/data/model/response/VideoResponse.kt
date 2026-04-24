@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.bbttvv.app.data.model.response

import com.bbttvv.app.core.util.ClosestTargetFallback
import com.bbttvv.app.core.util.findClosestTarget
import com.bbttvv.app.data.model.VideoDecodeFormat
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- 播放地址 Response (参考 PiliPala url.dart) ---
@Serializable
data class PlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayUrlData? = null
)

@Serializable
data class PlayUrlData(
    val quality: Int = 0,
    val format: String = "",
    val timelength: Long = 0,
    @SerialName("accept_format")
    val acceptFormat: String = "",
    @SerialName("accept_description")
    val acceptDescription: List<String> = emptyList(),
    @SerialName("accept_quality") 
    val acceptQuality: List<Int> = emptyList(),
    @SerialName("video_codecid")
    val videoCodecid: Int = 0,
    val durl: List<Durl>? = null,
    val dash: Dash? = null,
    @SerialName("support_formats")
    val supportFormats: List<FormatItem>? = null,
    @SerialName("last_play_time")
    val lastPlayTime: Int? = null,
    @SerialName("last_play_cid")
    val lastPlayCid: Long? = null,
    
    // [New] AI Original Sound Translation
    @SerialName("cur_language")
    val curLanguage: String? = null,
    @SerialName("dolby_type")
    val dolbyType: Int? = null,
    @SerialName("ai_audio")
    val aiAudio: AiAudioInfo? = null
) {
    //  PiliPala 风格：提供便捷的访问方法
    val accept_quality: List<Int> get() = acceptQuality
    val accept_description: List<String> get() = acceptDescription
}

@Serializable
data class AiAudioInfo(
    val title: String = "",
    val items: List<AiAudioItem> = emptyList()
)

@Serializable
data class AiAudioItem(
    @SerialName("lang_code")
    val langCode: String = "",
    @SerialName("lang_doc")
    val langDoc: String = "",
    @SerialName("stream_url")
    val streamUrl: String = "" // Usually empty in playurl response, just indicates availability
)

@Serializable
data class Durl(
    val order: Int = 0,
    val length: Long = 0,
    val size: Long = 0,
    val url: String = "",
    @SerialName("backup_url")
    val backupUrl: List<String>? = null
) {
    val backup_url: List<String>? get() = backupUrl
}

@Serializable
data class Dash(
    val duration: Int = 0,
    @JsonNames("minBufferTime", "min_buffer_time")
    val minBufferTime: Float = 0f,
    val video: List<DashVideo> = emptyList(),
    val audio: List<DashAudio>? = emptyList(),
    val dolby: Dolby? = null,
    val flac: Flac? = null
)

//  DASH 视频流 (重命名避免与 ListModels.VideoItem 冲突)
@Serializable
data class DashVideo(
    val id: Int = 0,
    @JsonNames("baseUrl", "base_url")
    val baseUrl: String = "",
    @JsonNames("backupUrl", "backup_url")
    val backupUrl: List<String>? = null,
    @SerialName("bandwidth")
    val bandwidth: Int = 0,
    @SerialName("mime_type")
    val mimeType: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @JsonNames("frameRate", "frame_rate")
    val frameRate: String = "",
    val sar: String = "",
    @JsonNames("startWithSap", "start_with_sap")
    val startWithSap: Int? = null,
    @JsonNames("segmentBase", "segment_base")
    val segmentBase: SegmentBase? = null,
    val codecid: Int? = null
) {
    fun getValidUrl(): String = baseUrl.takeIf { it.isNotEmpty() }
        ?: backupUrl?.firstOrNull { it.isNotEmpty() } ?: ""
    
    val decodeFormat: VideoDecodeFormat?
        get() = VideoDecodeFormat.fromCodecs(codecs)
}

//  DASH 音频流
@Serializable
data class DashAudio(
    val id: Int = 0,
    @JsonNames("baseUrl", "base_url")
    val baseUrl: String = "",
    @JsonNames("backupUrl", "backup_url")
    val backupUrl: List<String>? = null,
    @SerialName("bandwidth")
    val bandwidth: Int = 0,
    @SerialName("mime_type")
    val mimeType: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @JsonNames("frameRate", "frame_rate")
    val frameRate: String = "",
    val sar: String = "",
    @JsonNames("startWithSap", "start_with_sap")
    val startWithSap: Int? = null,
    @JsonNames("segmentBase", "segment_base")
    val segmentBase: SegmentBase? = null,
    val codecid: Int? = null
) {
    fun getValidUrl(): String = baseUrl.takeIf { it.isNotEmpty() }
        ?: backupUrl?.firstOrNull { it.isNotEmpty() } ?: ""
}

@Serializable
data class SegmentBase(
    val initialization: String? = null,
    @JsonNames("indexRange", "index_range")
    val indexRange: String? = null
)

@Serializable
data class FormatItem(
    val quality: Int = 0,
    val format: String = "",
    @SerialName("new_description")
    val newDescription: String = "",
    @SerialName("display_desc")
    val displayDesc: String = "",
    val codecs: List<String>? = null
)

@Serializable
data class Dolby(
    val type: Int = 0,
    val audio: List<DashAudio>? = null
)

@Serializable
data class Flac(
    val display: Boolean = false,
    val audio: DashAudio? = null
)

// 兼容旧代码的类型别名
typealias DashMedia = DashVideo

//  扩展函数：获取最佳视频流
fun Dash.getBestVideo(
    targetQn: Int,
    preferCodec: String = "hev1",
    secondPreferCodec: String = "avc1",
    isHevcSupported: Boolean = true,
    isAv1Supported: Boolean = false
): DashVideo? {
    if (video.isEmpty()) {
        android.util.Log.w("VideoResponse", " getBestVideo: video list is empty!")
        return null
    }
    
    com.bbttvv.app.core.util.Logger.d("VideoResponse", "🔍 getBestVideo: targetQn=$targetQn, preferCodec=$preferCodec, hevc=$isHevcSupported, av1=$isAv1Supported")
    
    val validVideos = video.filter { it.getValidUrl().isNotEmpty() }
    if (validVideos.isEmpty()) {
        android.util.Log.w("VideoResponse", " getBestVideo: no video has valid URL")
        return video.firstOrNull()
    }
    
    val grouped = validVideos.groupBy { it.id }
    
    // 1. 找到匹配画质的视频列表
    val targetQualityId = grouped.keys.toList().findClosestTarget(
        target = targetQn,
        fallback = ClosestTargetFallback.NEAREST_HIGHER
    )
    val targetVideos = targetQualityId?.let { grouped[it] } ?: validVideos
    
    // 2. 根据编码格式偏好进行排序选择
    // 权重策略：
    // - 优先匹配用户偏好且设备如果支持
    // - 其次降级：AV1 -> HEVC -> AVC
    // - 不支持的格式降权
    
    val selected = targetVideos.maxByOrNull { video ->
        var score = 0
        val codecs = video.codecs.lowercase()
        
        val isAvc = codecs.startsWith("avc")
        val isHevc = codecs.startsWith("hev")
        val isAv1 = codecs.startsWith("av01")
        
        // 基础可用性检查
        val supported = when {
            isAvc -> true // 所有设备都支持 AVC
            isHevc -> isHevcSupported
            isAv1 -> isAv1Supported
            else -> false
        }
        
        if (!supported) {
            score = -100 // 设备不支持，尽量不选
        } else {
            // 设备支持，计算偏好得分
            // 精确匹配用户偏好
            if (codecs.contains(preferCodec, ignoreCase = true)) {
                score += 10
            } else if (secondPreferCodec.isNotBlank() && codecs.contains(secondPreferCodec, ignoreCase = true)) {
                score += 6
            }
            
            // 编码效率加分 (AV1 > HEVC > AVC)
            if (isAv1) score += 3
            else if (isHevc) score += 2
            else if (isAvc) score += 1
        }
        
        score
    }
    
    com.bbttvv.app.core.util.Logger.d("VideoResponse", " getBestVideo: selected id=${selected?.id}, codec=${selected?.codecs}")
    return selected
}

//  扩展函数：获取最佳音频流
fun Dash.getBestAudio(preferQuality: Int = -1): DashAudio? {
    if (audio.isNullOrEmpty()) {
        com.bbttvv.app.core.util.Logger.d("VideoResponse", "ℹ️ getBestAudio: no audio streams")
        return null
    }
    
    val validAudios = audio.filter { it.getValidUrl().isNotEmpty() }
    if (validAudios.isEmpty()) {
        return audio.firstOrNull()
    }
    
    // 如果指定了具体音质（如杜比/Hi-Res/特定码率），优先匹配
    if (preferQuality != -1) {
        // 1. 尝试精确匹配 ID
        val exactMatch = validAudios.find { it.id == preferQuality }
        if (exactMatch != null) return exactMatch
        
        // 2. 杜比/Hi-Res 特殊处理
        if (preferQuality == 30250 && dolby?.audio?.isNotEmpty() == true) {
             return dolby.audio.firstOrNull()
        }
        if (preferQuality == 30251 && flac?.audio != null) {
            return flac.audio
        }
        
        // 3. [统一策略] 使用 findClosestTarget：优先 <= 偏好的最高档，没有则取最近更高档
        val closestId = validAudios.map { it.id }.findClosestTarget(
            target = preferQuality,
            fallback = ClosestTargetFallback.NEAREST_HIGHER
        )
        val closest = closestId?.let { targetId -> validAudios.find { it.id == targetId } }
        if (closest != null) {
            com.bbttvv.app.core.util.Logger.d("VideoResponse", " getBestAudio: exact match failed for $preferQuality, using closest match ${closest.id}")
            return closest
        }
    }
    
    // 默认：最高带宽/码率 (Auto)
    val selected = validAudios.maxByOrNull { it.bandwidth }
    com.bbttvv.app.core.util.Logger.d("VideoResponse", " getBestAudio: selected id=${selected?.id}, bandwidth=${selected?.bandwidth}")
    return selected
}

