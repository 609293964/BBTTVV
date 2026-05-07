package com.bbttvv.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CdnCandidateCursorTest {
    @Test
    fun `preferred index is clamped to available candidate range`() {
        var count = 3
        val cursor = CdnCandidateCursor { count }

        cursor.prefer(10)

        assertEquals(2, cursor.preferredIndex())

        count = 1

        assertEquals(0, cursor.preferredIndex())
    }

    @Test
    fun `failure advances preferred index to next candidate`() {
        val cursor = CdnCandidateCursor { 3 }

        cursor.prefer(1)
        cursor.advanceAfterFailure(1)

        assertEquals(2, cursor.preferredIndex())

        cursor.advanceAfterFailure(2)

        assertEquals(0, cursor.preferredIndex())
    }

    @Test
    fun `stale failure does not move a newer preferred candidate`() {
        val cursor = CdnCandidateCursor { 3 }

        cursor.prefer(2)
        cursor.advanceAfterFailure(1)

        assertEquals(2, cursor.preferredIndex())
    }

    @Test
    fun `single candidate remains selected after failure`() {
        val cursor = CdnCandidateCursor { 1 }

        cursor.prefer(0)
        cursor.advanceAfterFailure(0)

        assertEquals(0, cursor.preferredIndex())
    }
}
