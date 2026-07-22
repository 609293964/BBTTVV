package com.bbttvv.app.feature.video.screen

import androidx.compose.ui.graphics.Color
import com.bbttvv.app.core.util.FormatUtils
import com.bbttvv.app.data.model.response.SponsorCategory

internal fun sponsorMarkColor(category: String): Color {
    return when (category) {
        SponsorCategory.SPONSOR -> Color(0xFFFB7299).copy(alpha = 0.88f)
        SponsorCategory.INTRO,
        SponsorCategory.OUTRO -> Color(0xFF38BDF8).copy(alpha = 0.82f)
        SponsorCategory.INTERACTION -> Color(0xFFFBBF24).copy(alpha = 0.84f)
        SponsorCategory.SELFPROMO -> Color(0xFFA78BFA).copy(alpha = 0.84f)
        SponsorCategory.MUSIC_OFFTOPIC -> Color(0xFF22C55E).copy(alpha = 0.84f)
        SponsorCategory.POI_HIGHLIGHT -> Color(0xFFFFD166).copy(alpha = 0.94f)
        SponsorCategory.CHAPTER -> Color(0xFF60A5FA).copy(alpha = 0.94f)
        else -> Color(0xFF34D399).copy(alpha = 0.82f)
    }
}

internal fun formatCount(value: Int): String {
    return FormatUtils.formatCompactStat(value.toLong())
}

internal fun formatDuration(durationMs: Long): String {
    return FormatUtils.formatPlaybackDuration(durationMs)
}

internal fun formatPlaybackSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}倍速"
    } else {
        "${speed}倍速"
    }
}
