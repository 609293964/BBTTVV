package com.bbttvv.app.core.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenWarmupGateTest {
    @Test
    fun `failed warmup becomes retryable`() {
        val gate = TokenWarmupGate()

        assertTrue(gate.tryStart())
        assertEquals(TokenWarmupState.RUNNING, gate.state())
        gate.markFailed()
        assertEquals(TokenWarmupState.FAILED, gate.state())

        assertTrue(gate.tryStart())
        assertEquals(TokenWarmupState.RUNNING, gate.state())
    }

    @Test
    fun `successful warmup is one shot`() {
        val gate = TokenWarmupGate()

        assertTrue(gate.tryStart())
        gate.markSucceeded()

        assertEquals(TokenWarmupState.SUCCEEDED, gate.state())
        assertFalse(gate.tryStart())
    }

    @Test
    fun `running warmup cannot be claimed twice`() {
        val gate = TokenWarmupGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.tryStart())
        assertEquals(TokenWarmupState.RUNNING, gate.state())
    }
}
