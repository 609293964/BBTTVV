package com.bbttvv.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailFormattingTest {
    @Test
    fun `detail statistics use the shared Chinese units`() {
        assertEquals("9999", formatNumber(9_999))
        assertEquals("1.0万", formatNumber(10_000))
        assertEquals("1930.7万", formatNumber(19_307_000))
        assertEquals("1.0亿", formatNumber(100_000_000))
    }
}
