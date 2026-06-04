package com.bbttvv.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DanmakuInitialLoadRetryPolicyTest {
    @Test
    fun `empty initial load retries with bounded short delays`() {
        assertEquals(
            600L,
            DanmakuInitialLoadRetryPolicy.nextDelayMs(
                failedAttemptIndex = 0,
                hasParsedPayload = false,
                hasRawData = false,
                isCurrentCid = true,
                isDanmakuEnabled = true,
                hasPublishedPayload = false,
            )
        )
        assertEquals(
            1_500L,
            DanmakuInitialLoadRetryPolicy.nextDelayMs(
                failedAttemptIndex = 1,
                hasParsedPayload = false,
                hasRawData = false,
                isCurrentCid = true,
                isDanmakuEnabled = true,
                hasPublishedPayload = false,
            )
        )
        assertNull(
            DanmakuInitialLoadRetryPolicy.nextDelayMs(
                failedAttemptIndex = 2,
                hasParsedPayload = false,
                hasRawData = false,
                isCurrentCid = true,
                isDanmakuEnabled = true,
                hasPublishedPayload = false,
            )
        )
    }

    @Test
    fun `parsed or raw data cancels retry`() {
        assertNull(
            DanmakuInitialLoadRetryPolicy.nextDelayMs(
                failedAttemptIndex = 0,
                hasParsedPayload = true,
                hasRawData = false,
                isCurrentCid = true,
                isDanmakuEnabled = true,
                hasPublishedPayload = false,
            )
        )
        assertNull(
            DanmakuInitialLoadRetryPolicy.nextDelayMs(
                failedAttemptIndex = 0,
                hasParsedPayload = false,
                hasRawData = true,
                isCurrentCid = true,
                isDanmakuEnabled = true,
                hasPublishedPayload = false,
            )
        )
    }

    @Test
    fun `stale cid disabled danmaku or existing payload cancels retry`() {
        assertNull(
            DanmakuInitialLoadRetryPolicy.nextDelayMs(
                failedAttemptIndex = 0,
                hasParsedPayload = false,
                hasRawData = false,
                isCurrentCid = false,
                isDanmakuEnabled = true,
                hasPublishedPayload = false,
            )
        )
        assertNull(
            DanmakuInitialLoadRetryPolicy.nextDelayMs(
                failedAttemptIndex = 0,
                hasParsedPayload = false,
                hasRawData = false,
                isCurrentCid = true,
                isDanmakuEnabled = false,
                hasPublishedPayload = false,
            )
        )
        assertNull(
            DanmakuInitialLoadRetryPolicy.nextDelayMs(
                failedAttemptIndex = 0,
                hasParsedPayload = false,
                hasRawData = false,
                isCurrentCid = true,
                isDanmakuEnabled = true,
                hasPublishedPayload = true,
            )
        )
    }
}
