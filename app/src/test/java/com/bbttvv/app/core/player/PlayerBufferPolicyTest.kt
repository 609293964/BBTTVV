package com.bbttvv.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBufferPolicyTest {
    @Test
    fun `memory budget retains TV startup and recovery thresholds`() {
        assertEquals(
            PlayerBufferPolicy(
                minBufferMs = 15_000,
                maxBufferMs = 50_000,
                bufferForPlaybackMs = 1_600,
                bufferForPlaybackAfterRebufferMs = 3_000,
                targetBufferBytes = 32 * 1024 * 1024,
            ),
            resolvePlayerBufferPolicy(192L * 1024 * 1024),
        )
    }

    @Test
    fun `budget scales with heap and is bounded without integer overflow`() {
        val mib = 1024L * 1024
        listOf(
            0L to 16,
            64 * mib to 16,
            192 * mib to 32,
            384 * mib to 64,
            576 * mib to 96,
            Long.MAX_VALUE to 96,
        ).forEach { (heap, expectedMiB) ->
            assertEquals("heap=$heap", expectedMiB * mib, resolvePlayerBufferPolicy(heap).targetBufferBytes.toLong())
        }
    }
}
