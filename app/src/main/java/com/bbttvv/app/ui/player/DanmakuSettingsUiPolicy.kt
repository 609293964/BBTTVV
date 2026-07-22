package com.bbttvv.app.ui.player

import com.bbttvv.app.core.store.player.DanmakuFontWeightPreset
import com.bbttvv.app.core.store.player.DanmakuLaneDensityPreset
import java.util.Locale
import kotlin.math.abs

internal fun formatDanmakuOpacity(value: Float): String =
    String.format(Locale.US, "%.0f%%", value * 100)

internal fun formatDanmakuFontWeight(value: DanmakuFontWeightPreset): String {
    return when (value) {
        DanmakuFontWeightPreset.Normal -> "常规"
        DanmakuFontWeightPreset.Bold -> "加粗"
    }
}

internal fun formatDanmakuLaneDensity(value: DanmakuLaneDensityPreset): String {
    return when (value) {
        DanmakuLaneDensityPreset.Sparse -> "稀疏"
        DanmakuLaneDensityPreset.Standard -> "标准"
        DanmakuLaneDensityPreset.Dense -> "密集"
    }
}

internal fun formatDanmakuAreaRatio(value: Float): String {
    return when {
        abs(value - 1f) < 0.001f -> "不限"
        abs(value - (1f / 6f)) < 0.001f -> "1/6"
        abs(value - (1f / 5f)) < 0.001f -> "1/5"
        abs(value - (1f / 4f)) < 0.001f -> "1/4"
        abs(value - (1f / 3f)) < 0.001f -> "1/3"
        abs(value - (2f / 5f)) < 0.001f -> "2/5"
        abs(value - (1f / 2f)) < 0.001f -> "1/2"
        abs(value - (3f / 5f)) < 0.001f -> "3/5"
        abs(value - (2f / 3f)) < 0.001f -> "2/3"
        abs(value - (3f / 4f)) < 0.001f -> "3/4"
        abs(value - (4f / 5f)) < 0.001f -> "4/5"
        else -> formatDanmakuOpacity(value)
    }
}

internal fun <T> nextTvOption(options: List<T>, current: T): T {
    require(options.isNotEmpty()) { "options must not be empty" }
    val currentIndex = options.indexOf(current)
    return if (currentIndex == -1 || currentIndex == options.lastIndex) {
        options.first()
    } else {
        options[currentIndex + 1]
    }
}
