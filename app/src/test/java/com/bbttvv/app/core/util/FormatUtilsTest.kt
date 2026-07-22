package com.bbttvv.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {
    @Test
    fun `compact stat removes redundant decimal`() {
        assertEquals("1万", FormatUtils.formatCompactStat(10_000))
        assertEquals("1.2万", FormatUtils.formatCompactStat(12_000))
        assertEquals("1亿", FormatUtils.formatCompactStat(100_000_000))
    }

    @Test
    fun `playback duration keeps unpadded hour style`() {
        assertEquals("00:00", FormatUtils.formatPlaybackDuration(0L))
        assertEquals("1:02:03", FormatUtils.formatPlaybackDuration(3_723_000L))
    }
}
