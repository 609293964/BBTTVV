package com.bbttvv.app.feature.video.danmaku

import android.graphics.Color
import android.graphics.Typeface
import com.bytedance.danmaku.render.engine.control.DanmakuConfig as EngineConfig

class DanmakuConfig {
    var opacity = 0.88f
    var fontScale = 0.92f
    var itemTextSize = 32f
    var fontWeight = 5
    var speedFactor = 1.0f
    var scrollDurationSeconds = 7.0f
    var displayAreaRatio = 0.55f
    var lineHeight = 1.35f
    var strokeEnabled = true
    var strokeWidth = 1.5f
    var staticDurationSeconds = 4.0f

    fun applyTo(engineConfig: EngineConfig, viewWidth: Int = 0, viewHeight: Int = 0) {
        engineConfig.apply {
            val layoutTextSize = 42f
            common.alpha = (opacity * 255).toInt()
            text.size = layoutTextSize
            text.typeface = resolveDanmakuTypeface(fontWeight)
            text.strokeWidth = if (strokeEnabled) strokeWidth else 0f
            text.strokeColor = Color.BLACK

            val activeHeight = if (viewHeight > 0) {
                viewHeight * displayAreaRatio.coerceIn(1f / 6f, 1.0f)
            } else {
                0f
            }

            scroll.moveTime = resolveScrollDurationMillis(
                scrollDurationSeconds = scrollDurationSeconds,
                speedFactor = speedFactor
            )
            scroll.lineHeight = layoutTextSize * lineHeight.coerceIn(1.0f, 2.2f)
            scroll.marginTop = 0f
            scroll.lineCount = resolveVisibleLineCount(activeHeight, layoutTextSize, lineHeight)

            val pinnedDuration = (staticDurationSeconds.coerceIn(2f, 12f) * 1000f).toLong()
            top.lineHeight = scroll.lineHeight
            top.lineCount = (scroll.lineCount / 2).coerceAtLeast(1)
            top.showTimeMin = pinnedDuration
            top.showTimeMax = pinnedDuration

            bottom.lineHeight = scroll.lineHeight
            bottom.lineCount = (scroll.lineCount / 2).coerceAtLeast(1)
            bottom.showTimeMin = pinnedDuration
            bottom.showTimeMax = pinnedDuration
        }
    }

    private fun resolveVisibleLineCount(
        visibleHeightPx: Float,
        fontSize: Float,
        lineHeightMultiplier: Float
    ): Int {
        if (visibleHeightPx <= 0f) return 8
        val estimatedHeight = (fontSize + 12f) * lineHeightMultiplier.coerceIn(1.0f, 2.0f)
        return (visibleHeightPx / estimatedHeight).toInt().coerceIn(3, 18)
    }

    private fun resolveScrollDurationMillis(
        scrollDurationSeconds: Float,
        speedFactor: Float
    ): Long {
        return (scrollDurationSeconds.coerceIn(3f, 12f) * 1000f * speedFactor.coerceIn(0.6f, 1.6f))
            .toLong()
            .coerceIn(2400L, 18000L)
    }
}

private fun resolveDanmakuTypeface(fontWeight: Int): Typeface {
    return Typeface.create(Typeface.DEFAULT, if (fontWeight >= 6) Typeface.BOLD else Typeface.NORMAL)
}
