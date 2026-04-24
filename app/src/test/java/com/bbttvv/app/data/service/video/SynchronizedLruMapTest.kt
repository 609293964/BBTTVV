package com.bbttvv.app.data.service.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynchronizedLruMapTest {
    @Test
    fun `evicts least recently used entry when over capacity`() {
        val cache = SynchronizedLruMap<String, Int>(maxEntries = 2)

        cache["a"] = 1
        cache["b"] = 2
        assertEquals(1, cache["a"])

        cache["c"] = 3

        assertTrue(cache.containsKey("a"))
        assertFalse(cache.containsKey("b"))
        assertTrue(cache.containsKey("c"))
    }

    @Test
    fun `expires entries after ttl`() {
        var nowMs = 1_000L
        val cache = SynchronizedLruMap<String, Int>(
            maxEntries = 2,
            ttlMillis = 500L,
            nowProvider = { nowMs }
        )

        cache["a"] = 1
        assertEquals(1, cache["a"])

        nowMs += 500L

        assertNull(cache["a"])
        assertFalse(cache.containsKey("a"))
        assertEquals(0, cache.size())
    }
}
