package com.bbttvv.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RetryStrategyTest {
    @Test
    fun `retry config rejects invalid boundaries`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryStrategy.RetryConfig(maxAttempts = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetryStrategy.RetryConfig(initialDelayMs = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetryStrategy.RetryConfig(initialDelayMs = 1000, maxDelayMs = 500)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetryStrategy.RetryConfig(multiplier = Double.NaN)
        }
    }

    @Test
    fun `next delay is bounded and overflow safe`() {
        assertEquals(1_000L, RetryStrategy.nextDelayMs(500L, 2.0, 5_000L))
        assertEquals(5_000L, RetryStrategy.nextDelayMs(4_000L, 2.0, 5_000L))
        assertEquals(5_000L, RetryStrategy.nextDelayMs(5_000L, 2.0, 5_000L))
    }
}
