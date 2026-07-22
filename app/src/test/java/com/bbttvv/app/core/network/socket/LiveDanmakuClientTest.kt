package com.bbttvv.app.core.network.socket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDanmakuClientTest {
    @Test
    fun authRequestsBrotliFramesSupportedByDecoder() {
        assertEquals(
            DanmakuProtocol.PROTO_VER_BROTLI,
            LIVE_DANMAKU_AUTH_PROTOCOL_VERSION
        )
    }

    @Test
    fun transportReconnectHasFiniteAttemptBoundary() {
        assertTrue(shouldRetryLiveDanmakuTransport(0))
        assertTrue(shouldRetryLiveDanmakuTransport(LIVE_DANMAKU_MAX_TRANSPORT_RETRIES - 1))
        assertFalse(shouldRetryLiveDanmakuTransport(LIVE_DANMAKU_MAX_TRANSPORT_RETRIES))
        assertFalse(shouldRetryLiveDanmakuTransport(LIVE_DANMAKU_MAX_TRANSPORT_RETRIES + 1))
    }
}
