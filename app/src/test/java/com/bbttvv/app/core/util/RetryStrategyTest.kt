package com.bbttvv.app.core.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
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

    @Test
    fun `executeWithRetry propagates cancellation on final attempt`() = runBlocking {
        var attempts = 0
        try {
            RetryStrategy.executeWithRetry<Unit>(
                config = RetryStrategy.RetryConfig(maxAttempts = 1)
            ) {
                attempts += 1
                throw CancellationException("cancel")
            }
            fail("CancellationException should propagate")
        } catch (_: CancellationException) {
            // expected
        }
        assertEquals(1, attempts)
    }

    @Test
    fun `runSuspendCatching never converts cancellation to failure`() = runBlocking {
        try {
            runSuspendCatching<Unit> {
                throw CancellationException("cancel")
            }
            fail("CancellationException should propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
