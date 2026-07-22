package com.bbttvv.app.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDanmakuPolicyTest {
    @Test
    fun `live danmaku is accepted only during visible active playback`() {
        assertTrue(
            shouldAcceptLiveDanmaku(
                isEnabled = true,
                isPlaying = true,
                isAppInBackground = false,
                isPlaybackSuppressed = false,
            )
        )
        assertFalse(shouldAcceptLiveDanmaku(true, false, false, false))
        assertFalse(shouldAcceptLiveDanmaku(true, true, true, false))
        assertFalse(shouldAcceptLiveDanmaku(true, true, false, true))
    }

    @Test
    fun `resume anchors new message to current playback instead of paused backlog`() {
        assertEquals(
            60_220L,
            resolveLiveDanmakuShowAtMs(
                currentPositionMs = 60_000L,
                lastShowAtMs = 1_000L,
                showLeadMs = 220L,
                minGapMs = 70L,
            )
        )
    }

    @Test
    fun `active burst keeps minimum message gap`() {
        assertEquals(
            10_070L,
            resolveLiveDanmakuShowAtMs(
                currentPositionMs = 9_000L,
                lastShowAtMs = 10_000L,
                showLeadMs = 220L,
                minGapMs = 70L,
            )
        )
    }
}
