package com.bbttvv.app.feature.video.screen

import androidx.media3.exoplayer.ExoPlayer

private const val DEBUG_FRAME_SAMPLE_WINDOW_MS = 900L
private const val DEBUG_FRAME_SAMPLE_MAX_GAP_MS = 5_000L

internal data class PlayerDebugFrameSample(
    val renderFps: Float? = null,
    val renderedFramesDelta: Int = 0,
    val droppedFramesDelta: Int = 0,
    val dropRatePercent: Float? = null,
    val totalRenderedFrames: Int = 0,
    val totalDroppedFrames: Int = 0,
    val rebufferCount: Int = 0,
)

/**
 * Converts cumulative decoder counters into a small rolling diagnostic window.
 * The sampler is used only while the debug overlay is visible.
 */
internal class PlayerDebugFrameSampler {
    private var windowStartedAtMs = 0L
    private var windowRenderedFrames = 0
    private var windowDroppedFrames = 0
    private var wasPlaying = false
    private var wasBuffering = false
    private var hasObservedPlaying = false
    private var rebufferCount = 0
    private var lastWindowSample = PlayerDebugFrameSample()

    fun reset() {
        windowStartedAtMs = 0L
        windowRenderedFrames = 0
        windowDroppedFrames = 0
        wasPlaying = false
        wasBuffering = false
        hasObservedPlaying = false
        rebufferCount = 0
        lastWindowSample = PlayerDebugFrameSample()
    }

    fun sample(
        nowMs: Long,
        renderedFrames: Int,
        droppedFrames: Int,
        isPlaying: Boolean,
        isBuffering: Boolean,
        playWhenReady: Boolean,
    ): PlayerDebugFrameSample {
        val safeRenderedFrames = renderedFrames.coerceAtLeast(0)
        val safeDroppedFrames = droppedFrames.coerceAtLeast(0)

        if (isBuffering && !wasBuffering && playWhenReady && hasObservedPlaying) {
            rebufferCount += 1
        }
        wasBuffering = isBuffering
        if (isPlaying) {
            hasObservedPlaying = true
        }

        val countersReset = safeRenderedFrames < windowRenderedFrames ||
            safeDroppedFrames < windowDroppedFrames
        val invalidGap = windowStartedAtMs > 0L &&
            nowMs - windowStartedAtMs > DEBUG_FRAME_SAMPLE_MAX_GAP_MS
        if (
            windowStartedAtMs <= 0L ||
            countersReset ||
            invalidGap ||
            !isPlaying ||
            !wasPlaying
        ) {
            resetWindow(nowMs, safeRenderedFrames, safeDroppedFrames)
            wasPlaying = isPlaying
            lastWindowSample = PlayerDebugFrameSample(
                totalRenderedFrames = safeRenderedFrames,
                totalDroppedFrames = safeDroppedFrames,
                rebufferCount = rebufferCount,
            )
            return lastWindowSample
        }

        wasPlaying = true
        val elapsedMs = nowMs - windowStartedAtMs
        if (elapsedMs < DEBUG_FRAME_SAMPLE_WINDOW_MS) {
            return lastWindowSample.copy(
                totalRenderedFrames = safeRenderedFrames,
                totalDroppedFrames = safeDroppedFrames,
                rebufferCount = rebufferCount,
            )
        }

        val renderedDelta = (safeRenderedFrames - windowRenderedFrames).coerceAtLeast(0)
        val droppedDelta = (safeDroppedFrames - windowDroppedFrames).coerceAtLeast(0)
        val attemptedFrames = renderedDelta + droppedDelta
        lastWindowSample = PlayerDebugFrameSample(
            renderFps = renderedDelta * 1_000f / elapsedMs.toFloat(),
            renderedFramesDelta = renderedDelta,
            droppedFramesDelta = droppedDelta,
            dropRatePercent = if (attemptedFrames > 0) {
                droppedDelta * 100f / attemptedFrames.toFloat()
            } else {
                0f
            },
            totalRenderedFrames = safeRenderedFrames,
            totalDroppedFrames = safeDroppedFrames,
            rebufferCount = rebufferCount,
        )
        resetWindow(nowMs, safeRenderedFrames, safeDroppedFrames)
        return lastWindowSample
    }

    private fun resetWindow(nowMs: Long, renderedFrames: Int, droppedFrames: Int) {
        windowStartedAtMs = nowMs
        windowRenderedFrames = renderedFrames
        windowDroppedFrames = droppedFrames
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun samplePlayerDebugFrames(
    player: ExoPlayer,
    sampler: PlayerDebugFrameSampler,
    nowMs: Long,
): PlayerDebugFrameSample {
    val counters = player.videoDecoderCounters
    counters?.ensureUpdated()
    return sampler.sample(
        nowMs = nowMs,
        renderedFrames = counters?.renderedOutputBufferCount ?: 0,
        droppedFrames = counters?.droppedBufferCount ?: 0,
        isPlaying = player.isPlaying,
        isBuffering = player.playbackState == androidx.media3.common.Player.STATE_BUFFERING,
        playWhenReady = player.playWhenReady,
    )
}
