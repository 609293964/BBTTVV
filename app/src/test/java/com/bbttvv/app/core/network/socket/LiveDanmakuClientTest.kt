package com.bbttvv.app.core.network.socket

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    @Test
    fun disconnectInvalidatesLifecycleCapturedByPendingReconnect() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val client = LiveDanmakuClient(scope)
        try {
            val currentLifecycle = LiveDanmakuClient::class.java
                .getDeclaredMethod("currentLifecycleGeneration")
                .apply { isAccessible = true }
            val isLifecycleCurrent = LiveDanmakuClient::class.java
                .getDeclaredMethod("isLifecycleCurrent", java.lang.Long.TYPE)
                .apply { isAccessible = true }

            val captured = currentLifecycle.invoke(client) as Long
            assertTrue(isLifecycleCurrent.invoke(client, captured) as Boolean)

            client.disconnect()

            assertFalse(isLifecycleCurrent.invoke(client, captured) as Boolean)
        } finally {
            client.release()
            scope.cancel()
        }
    }
}
