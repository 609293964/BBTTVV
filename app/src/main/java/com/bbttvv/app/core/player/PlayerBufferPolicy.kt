package com.bbttvv.app.core.player

internal data class PlayerBufferPolicy(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
)

// 按进程堆上限给媒体样本分配预算，给图片、弹幕和解码器保留空间。
// 保留现有时间阈值；字节预算优先，避免低内存电视为满足时间窗持续分配。
internal fun resolvePlayerBufferPolicy(
    heapLimitBytes: Long = Runtime.getRuntime().maxMemory(),
): PlayerBufferPolicy = PlayerBufferPolicy(
    minBufferMs = 15_000,
    maxBufferMs = 50_000,
    bufferForPlaybackMs = 1_600,
    bufferForPlaybackAfterRebufferMs = 3_000,
    targetBufferBytes = (heapLimitBytes / 6)
        .coerceIn(16L * 1024 * 1024, 96L * 1024 * 1024)
        .toInt(),
)
