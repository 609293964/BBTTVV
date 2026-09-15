package com.bbttvv.app.core.coroutines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestGenerationTest {
    @Test
    fun `only latest generation remains current`() {
        val guard = RequestGeneration()
        val first = guard.next()
        val second = guard.next()

        assertFalse(guard.isCurrent(first))
        assertTrue(guard.isCurrent(second))
    }

    @Test
    fun `invalidate makes previously captured request stale`() {
        val guard = RequestGeneration()
        val request = guard.next()

        guard.invalidate()

        assertFalse(guard.isCurrent(request))
    }
}
