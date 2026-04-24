package com.bbttvv.app.feature.video.danmaku

import com.bytedance.danmaku.render.engine.data.DanmakuData

data class AdvancedDanmakuData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val startTimeMs: Long,
    val durationMs: Long,
    val startX: Float,
    val startY: Float,
    val endX: Float = startX,
    val endY: Float = startY,
    val fontSize: Float = 25f,
    val color: Int = 0xFFFFFF,
    val alpha: Float = 1.0f,
    val motionType: String = "Linear",
    val rotateZ: Float = 0f,
    val rotateY: Float = 0f,
    val maxCount: Int = 0,
    val accumulationDurationMs: Long = 0L
) {
    fun isActive(currentPos: Long): Boolean {
        return currentPos >= startTimeMs && currentPos <= startTimeMs + durationMs
    }
}

data class ParsedDanmaku(
    val standardList: List<DanmakuData>,
    val advancedList: List<AdvancedDanmakuData>
) {
    fun mergeWith(other: ParsedDanmaku): ParsedDanmaku {
        return ParsedDanmaku(
            standardList = this.standardList + other.standardList,
            advancedList = this.advancedList + other.advancedList
        )
    }
}

data class DanmakuRenderPayload(
    val standardList: List<DanmakuData>,
    val advancedList: List<AdvancedDanmakuData>,
    val sourceLabel: String,
    val totalCount: Int
)
