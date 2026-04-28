package com.bbttvv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicPaginationRegistryTest {
    @Test
    fun staleScopeGenerationCannotOverwriteResetState() {
        val registry = DynamicFeedPaginationRegistry()
        val staleGeneration = registry.generation(DynamicFeedScope.DYNAMIC_SCREEN)
        val currentGeneration = registry.reset(DynamicFeedScope.DYNAMIC_SCREEN)

        val staleApplied = registry.update(
            scope = DynamicFeedScope.DYNAMIC_SCREEN,
            offset = "stale",
            hasMore = false,
            generation = staleGeneration
        )

        assertFalse(staleApplied)
        assertEquals("", registry.offset(DynamicFeedScope.DYNAMIC_SCREEN))
        assertTrue(registry.hasMore(DynamicFeedScope.DYNAMIC_SCREEN))

        val currentApplied = registry.update(
            scope = DynamicFeedScope.DYNAMIC_SCREEN,
            offset = "current",
            hasMore = false,
            generation = currentGeneration
        )

        assertTrue(currentApplied)
        assertEquals("current", registry.offset(DynamicFeedScope.DYNAMIC_SCREEN))
        assertFalse(registry.hasMore(DynamicFeedScope.DYNAMIC_SCREEN))
    }

    @Test
    fun staleUserGenerationCannotOverwriteResetState() {
        val registry = DynamicUserPaginationRegistry()
        val hostMid = 42L
        val staleGeneration = registry.generation(hostMid)
        val currentGeneration = registry.reset(hostMid)

        val staleApplied = registry.update(
            hostMid = hostMid,
            offset = "stale",
            hasMore = false,
            generation = staleGeneration
        )

        assertFalse(staleApplied)
        assertEquals("", registry.offset(hostMid))
        assertTrue(registry.hasMore(hostMid))

        val currentApplied = registry.update(
            hostMid = hostMid,
            offset = "current",
            hasMore = false,
            generation = currentGeneration
        )

        assertTrue(currentApplied)
        assertEquals("current", registry.offset(hostMid))
        assertFalse(registry.hasMore(hostMid))
    }

    @Test
    fun staleUserGenerationCannotOverwriteResetAllState() {
        val registry = DynamicUserPaginationRegistry()
        val hostMid = 42L
        val staleGeneration = registry.generation(hostMid)

        registry.resetAll()

        val staleApplied = registry.update(
            hostMid = hostMid,
            offset = "stale",
            hasMore = false,
            generation = staleGeneration
        )

        assertFalse(staleApplied)
        assertEquals("", registry.offset(hostMid))
        assertTrue(registry.hasMore(hostMid))
    }
}
