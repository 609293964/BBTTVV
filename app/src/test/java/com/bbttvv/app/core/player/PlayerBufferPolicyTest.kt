package com.bbttvv.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBufferPolicyTest {
    @Test
    fun `TV buffer policy is independent of network transport`() {
        assertEquals(
            PlayerBufferPolicy(
                minBufferMs = 15_000,
                maxBufferMs = 50_000,
                bufferForPlaybackMs = 1_600,
                bufferForPlaybackAfterRebufferMs = 3_000,
            ),
            resolvePlayerBufferPolicy(),
        )
    }
}
