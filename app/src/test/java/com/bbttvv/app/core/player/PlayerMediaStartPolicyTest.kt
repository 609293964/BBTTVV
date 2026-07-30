package com.bbttvv.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerMediaStartPolicyTest {
    @Test
    fun `positive resume position is loaded as the media initial position`() {
        assertEquals(
            PlayerMediaStart(initialPositionMs = 42_000L, resetPosition = true),
            resolvePlayerMediaStart(requestedPositionMs = 42_000L, resetPosition = true),
        )
    }

    @Test
    fun `zero position preserves the caller reset policy`() {
        val preserve = resolvePlayerMediaStart(requestedPositionMs = 0L, resetPosition = false)
        assertNull(preserve.initialPositionMs)
        assertEquals(false, preserve.resetPosition)

        val reset = resolvePlayerMediaStart(requestedPositionMs = -1L, resetPosition = true)
        assertNull(reset.initialPositionMs)
        assertEquals(true, reset.resetPosition)
    }
}
