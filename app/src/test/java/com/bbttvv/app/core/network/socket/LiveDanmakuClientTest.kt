package com.bbttvv.app.core.network.socket

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveDanmakuClientTest {
    @Test
    fun authRequestsBrotliFramesSupportedByDecoder() {
        assertEquals(
            DanmakuProtocol.PROTO_VER_BROTLI,
            LIVE_DANMAKU_AUTH_PROTOCOL_VERSION
        )
    }
}
