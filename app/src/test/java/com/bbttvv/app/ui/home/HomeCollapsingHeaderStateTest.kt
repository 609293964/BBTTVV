package com.bbttvv.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCollapsingHeaderStateTest {
    @Test
    fun `transient zero and low offsets are ignored while header is fully collapsed`() {
        val state = HomeCollapsingHeaderState()

        state.updateScrollOffset(scrollOffsetPx = 1_200, totalHeaderHeightPx = 600)
        assertEquals(600, state.collapseOffsetPx)

        state.updateScrollOffset(scrollOffsetPx = 0, totalHeaderHeightPx = 600)
        assertEquals(600, state.collapseOffsetPx)

        state.updateScrollOffset(scrollOffsetPx = 180, totalHeaderHeightPx = 600)
        assertEquals(600, state.collapseOffsetPx)
    }

    @Test
    fun `gradual upward scroll below header still updates collapse`() {
        val state = HomeCollapsingHeaderState()

        state.updateScrollOffset(scrollOffsetPx = 1_200, totalHeaderHeightPx = 600)
        state.updateScrollOffset(scrollOffsetPx = 480, totalHeaderHeightPx = 600)

        assertEquals(480, state.collapseOffsetPx)
    }

    @Test
    fun `explicit reset clears collapse and transient suppression`() {
        val state = HomeCollapsingHeaderState()

        state.updateScrollOffset(scrollOffsetPx = 1_200, totalHeaderHeightPx = 600)
        state.updateScrollOffset(scrollOffsetPx = 0, totalHeaderHeightPx = 600)
        state.reset()

        assertEquals(0, state.collapseOffsetPx)
        state.updateScrollOffset(scrollOffsetPx = 180, totalHeaderHeightPx = 600)
        assertEquals(180, state.collapseOffsetPx)
    }
}
