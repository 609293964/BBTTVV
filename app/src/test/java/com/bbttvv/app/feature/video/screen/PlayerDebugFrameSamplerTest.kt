package com.bbttvv.app.feature.video.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerDebugFrameSamplerTest {
    @Test
    fun `rolling window reports rendered fps and drop rate`() {
        val sampler = PlayerDebugFrameSampler()

        sampler.sample(
            nowMs = 1_000L,
            renderedFrames = 100,
            droppedFrames = 5,
            isPlaying = true,
            isBuffering = false,
            playWhenReady = true,
        )
        val sample = sampler.sample(
            nowMs = 2_000L,
            renderedFrames = 129,
            droppedFrames = 6,
            isPlaying = true,
            isBuffering = false,
            playWhenReady = true,
        )

        assertEquals(29f, sample.renderFps ?: 0f, 0.001f)
        assertEquals(29, sample.renderedFramesDelta)
        assertEquals(1, sample.droppedFramesDelta)
        assertEquals(100f / 30f, sample.dropRatePercent ?: 0f, 0.001f)
        assertEquals(129, sample.totalRenderedFrames)
        assertEquals(6, sample.totalDroppedFrames)
    }

    @Test
    fun `pause resets the timing baseline instead of reporting false low fps`() {
        val sampler = PlayerDebugFrameSampler()
        sampler.sample(1_000L, 100, 0, true, false, true)
        sampler.sample(2_000L, 130, 0, false, false, false)

        val resumedBaseline = sampler.sample(12_000L, 131, 0, true, false, true)
        val resumedWindow = sampler.sample(13_000L, 161, 0, true, false, true)

        assertNull(resumedBaseline.renderFps)
        assertEquals(30f, resumedWindow.renderFps ?: 0f, 0.001f)
    }

    @Test
    fun `decoder counter reset starts a new sample window`() {
        val sampler = PlayerDebugFrameSampler()
        sampler.sample(1_000L, 500, 10, true, false, true)

        val resetSample = sampler.sample(2_000L, 2, 0, true, false, true)

        assertNull(resetSample.renderFps)
        assertEquals(2, resetSample.totalRenderedFrames)
        assertEquals(0, resetSample.totalDroppedFrames)
    }

    @Test
    fun `rebuffer counts only playback buffering transitions`() {
        val sampler = PlayerDebugFrameSampler()
        sampler.sample(1_000L, 0, 0, false, false, true)
        sampler.sample(1_500L, 0, 0, false, true, true)
        val startupBuffer = sampler.sample(2_000L, 0, 0, false, true, true)
        sampler.sample(2_500L, 0, 0, true, false, true)
        val rebuffer = sampler.sample(3_000L, 10, 0, false, true, true)

        assertEquals(0, startupBuffer.rebufferCount)
        assertEquals(1, rebuffer.rebufferCount)
    }

    @Test
    fun `source frame rate formatter distinguishes static source metadata`() {
        assertEquals("60.00 fps", formatSourceFrameRate("60"))
        assertEquals("23.98 fps", formatSourceFrameRate("24000/1001"))
        assertEquals("--", formatSourceFrameRate(""))
    }
}
