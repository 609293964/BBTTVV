package com.bbttvv.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomaticCacheCleanupPolicyTest {
    @Test
    fun `disabled and under threshold cache are retained`() {
        assertEquals(emptySet<CacheClearTarget>(), resolveAutomaticCacheCleanupTargets(100L, 100L, 0L))
        assertEquals(emptySet<CacheClearTarget>(), resolveAutomaticCacheCleanupTargets(40L, 50L, 100L))
    }

    @Test
    fun `largest managed cache is cleared first to create headroom`() {
        assertEquals(
            setOf(CacheClearTarget.IMAGE_PREVIEW),
            resolveAutomaticCacheCleanupTargets(imageBytes = 90L, httpBytes = 30L, thresholdBytes = 100L)
        )
    }

    @Test
    fun `both managed caches are cleared when one is insufficient`() {
        assertEquals(
            setOf(CacheClearTarget.IMAGE_PREVIEW, CacheClearTarget.NETWORK),
            resolveAutomaticCacheCleanupTargets(imageBytes = 60L, httpBytes = 55L, thresholdBytes = 50L)
        )
    }
}
