package com.bbttvv.app.data.model

/**
 * 视频画质枚举 - 参考 PiliPala quality.dart
 */
enum class VideoQuality(val code: Int, val description: String) {
    SPEED_240(6, "240P 极速"),
    FLUENT_360(16, "360P 流畅"),
    CLEAR_480(32, "480P 清晰"),
    HIGH_720(64, "720P 高清"),
    HIGH_720_60(74, "720P60 高帧率"),
    HIGH_1080(80, "1080P 高清"),
    HIGH_1080_PLUS(112, "1080P+ 高码率"),
    HIGH_1080_60(116, "1080P60 高帧率"),
    SUPER_4K(120, "4K 超清"),
    HDR(125, "HDR 真彩色"),
    DOLBY_VISION(126, "杜比视界"),
    SUPER_8K(127, "8K 超高清");

    companion object {
        fun fromCode(code: Int): VideoQuality? = values().find { it.code == code }
        
        // 获取最接近的画质
        fun findClosest(code: Int): VideoQuality {
            return values().minByOrNull { kotlin.math.abs(it.code - code) } ?: HIGH_720
        }
        
        // 默认画质链（降级顺序）
        val qualityChain = listOf(
            SUPER_4K, HIGH_1080_60, HIGH_1080_PLUS, HIGH_1080,
            HIGH_720_60, HIGH_720, CLEAR_480, FLUENT_360, SPEED_240
        )
    }
}

/**
 * 音频画质枚举 - 参考 PiliPala quality.dart
 */
enum class AudioQuality(val code: Int, val description: String) {
    K64(30216, "64K"),
    K132(30232, "132K"),
    K192(30280, "192K"),
    DOLBY(30250, "杜比全景声"),
    HI_RES(30251, "Hi-Res无损");

    companion object {
        fun fromCode(code: Int): AudioQuality? = values().find { it.code == code }
    }
}

/**
 * 视频解码格式 - 参考 PiliPala quality.dart
 */
enum class VideoDecodeFormat(val codecs: String, val description: String) {
    DVH1("dvh1", "DVH1"),
    AV1("av01", "AV1"),
    HEVC("hev1", "HEVC"),
    AVC("avc1", "AVC");

    companion object {
        fun fromCodecs(codecs: String): VideoDecodeFormat? = 
            values().find { codecs.startsWith(it.codecs) }
        
        // AVC 兼容性最好，优先选择
        val preferredOrder = listOf(AVC, HEVC, AV1, DVH1)
    }
}

