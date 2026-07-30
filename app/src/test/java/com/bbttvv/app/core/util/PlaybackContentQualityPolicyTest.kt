package com.bbttvv.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackContentQualityPolicyTest {
    @Test
    fun `normal video ignores PGC override`() {
        assertEquals(
            80,
            resolveStoredQualityForContent(
                normalStoredQuality = 80,
                pgcStoredQuality = 120,
                isPgc = false,
            ),
        )
    }

    @Test
    fun `PGC uses independent override when configured`() {
        assertEquals(
            120,
            resolveStoredQualityForContent(
                normalStoredQuality = 80,
                pgcStoredQuality = 120,
                isPgc = true,
            ),
        )
    }

    @Test
    fun `PGC inherits normal preference when unset`() {
        assertEquals(
            80,
            resolveStoredQualityForContent(
                normalStoredQuality = 80,
                pgcStoredQuality = null,
                isPgc = true,
            ),
        )
    }
}
