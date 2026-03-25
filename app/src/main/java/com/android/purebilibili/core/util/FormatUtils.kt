package com.android.purebilibili.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FormatUtils {
    private const val DEFAULT_IMAGE_WIDTH = 640
    private const val DEFAULT_IMAGE_HEIGHT = 400

    /**
     * 将数字格式化为 B站风格 (例如: 1.2万)
     */
    fun formatStat(count: Long): String {
        return when {
            count >= 100000000 -> String.format("%.1f亿", count / 100000000.0)
            count >= 10000 -> String.format("%.1f万", count / 10000.0)
            else -> count.toString()
        }
    }

    /**
     * 将秒数格式化为 HH:MM:SS
     */
    fun formatDuration(seconds: Int): String {
        return formatDurationFromSeconds(seconds.toLong())
    }

    /**
     * 将毫秒数格式化为 HH:MM:SS
     */
    fun formatDuration(milliseconds: Long): String {
        return formatDurationFromSeconds(milliseconds / 1000L)
    }

    private fun formatDurationFromSeconds(rawSeconds: Long): String {
        val seconds = rawSeconds.coerceAtLeast(0L)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    /**
     * 修复图片 URL (核心修复)
     * 1. 补全 https 前缀
     * 2. 自动添加缩放后缀节省流量
     */
    fun fixImageUrl(url: String?): String {
        return buildSizedImageUrl(url, width = DEFAULT_IMAGE_WIDTH, height = DEFAULT_IMAGE_HEIGHT)
    }

    fun buildSizedImageUrl(
        url: String?,
        width: Int,
        height: Int,
        format: String = "webp"
    ): String {
        val normalized = normalizeImageUrl(url)
        if (normalized.isEmpty() || width <= 0 || height <= 0) return normalized
        return "$normalized@${width}w_${height}h.$format"
    }

    private fun normalizeImageUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""

        val withProtocol = if (url.startsWith("//")) {
            "https:$url"
        } else if (url.startsWith("http://")) {
            url.replace("http://", "https://")
        } else {
            url
        }

        return withProtocol.substringBefore("@")
    }

    /**
     * 格式化观看进度
     */
    fun formatProgress(progress: Int, duration: Int): String {
        if (duration <= 0) return "已看"
        if (progress == -1) return "已看" // finish
        if (progress == 0) return "未观看"
        val percent = (progress.toFloat() / duration.toFloat() * 100).toInt()
        return if (percent >= 99) "已看完" else "已看$percent%"
    }
    
    /**
     *  格式化发布时间 (相对时间 + 日期)
     * 例如: "3小时前" / "昨天" / "2024-01-15"
     */
    fun formatPublishTime(
        timestampSeconds: Long,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        if (timestampSeconds <= 0) return ""
        
        val pubTime = timestampSeconds * 1000L
        val diff = (nowMs - pubTime).coerceAtLeast(0L)
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            seconds < 60 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days == 1L -> "昨天"
            days < 7 -> "${days}天前"
            else -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.format(Date(pubTime))
            }
        }
    }

    fun formatPrecisePublishTime(
        timestampSeconds: Long,
        pattern: String = "yyyy-MM-dd HH:mm",
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        if (timestampSeconds <= 0) return ""

        return SimpleDateFormat(pattern, locale).apply {
            this.timeZone = timeZone
        }.format(Date(timestampSeconds * 1000L))
    }
}
