package com.bbttvv.app.feature.video.screen

import androidx.compose.ui.graphics.Color
import com.bbttvv.app.data.model.response.SponsorCategory
import java.util.Locale

internal fun sponsorMarkColor(category: String): Color {
    return when (category) {
        SponsorCategory.SPONSOR -> Color(0xFFFB7299).copy(alpha = 0.88f)
        SponsorCategory.INTRO,
        SponsorCategory.OUTRO -> Color(0xFF38BDF8).copy(alpha = 0.82f)
        SponsorCategory.INTERACTION -> Color(0xFFFBBF24).copy(alpha = 0.84f)
        SponsorCategory.SELFPROMO -> Color(0xFFA78BFA).copy(alpha = 0.84f)
        else -> Color(0xFF34D399).copy(alpha = 0.82f)
    }
}

internal fun formatCount(value: Int): String {
    return when {
        value >= 100_000_000 -> String.format(Locale.CHINA, "%.1f亿", value / 100_000_000f)
        value >= 10_000 -> String.format(Locale.CHINA, "%.1f万", value / 10_000f)
        else -> value.toString()
    }.removeSuffix(".0亿").removeSuffix(".0万")
}

internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "00:00"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

internal fun formatPlaybackSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}倍速"
    } else {
        "${speed}倍速"
    }
}
