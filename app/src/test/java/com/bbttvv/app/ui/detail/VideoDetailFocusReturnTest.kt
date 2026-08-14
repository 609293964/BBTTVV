package com.bbttvv.app.ui.detail

import com.bbttvv.app.ui.focus.TvFocusReturn
import com.bbttvv.app.ui.focus.TvFocusReturnTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VideoDetailFocusReturnTest {
    @Test
    fun `play button return waits for detail target after player navigation`() {
        val focusReturn = TvFocusReturn()
        val key = videoDetailPlayFocusKey("BV1test")
        val target = FakeFocusReturnTarget()

        focusReturn.capture(key)

        assertFalse(focusReturn.restorePending())

        focusReturn.registerTarget(key, target)

        assertEquals(1, target.focusRequests)
        assertFalse(focusReturn.restorePending())
    }

    private class FakeFocusReturnTarget : TvFocusReturnTarget {
        var focusRequests: Int = 0
            private set

        override fun tryRequestFocus(): Boolean {
            focusRequests++
            return true
        }
    }
}
