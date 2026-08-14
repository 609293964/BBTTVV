package com.bbttvv.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailPrefetchSupportTest {

    @Test
    fun `focus summary prefetch delay adapts to dpad cadence`() {
        assertEquals(0L, FocusSummaryPrefetchDelayPolicy.delayMillis(null, currentFocusAtMs = 1_000L))
        assertEquals(250L, FocusSummaryPrefetchDelayPolicy.delayMillis(950L, currentFocusAtMs = 1_000L))
        assertEquals(100L, FocusSummaryPrefetchDelayPolicy.delayMillis(800L, currentFocusAtMs = 1_000L))
        assertEquals(0L, FocusSummaryPrefetchDelayPolicy.delayMillis(700L, currentFocusAtMs = 1_000L))
        assertEquals(0L, FocusSummaryPrefetchDelayPolicy.delayMillis(1_100L, currentFocusAtMs = 1_000L))
    }
}
