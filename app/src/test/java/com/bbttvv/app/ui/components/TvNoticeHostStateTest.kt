package com.bbttvv.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvNoticeHostStateTest {
    @Test
    fun `new notice replaces previous notice`() {
        val state = TvNoticeHostState()

        state.show("第一条")
        val latestId = state.show("第二条", TvNoticeKind.Error)

        assertEquals(latestId, state.currentNotice?.id)
        assertEquals("第二条", state.currentNotice?.message)
        assertEquals(TvNoticeKind.Error, state.currentNotice?.kind)
        assertEquals(TV_NOTICE_ERROR_DURATION_MS, state.currentNotice?.durationMs)
    }

    @Test
    fun `stale timeout cannot dismiss newer notice`() {
        val state = TvNoticeHostState()
        val staleId = state.show("旧消息")
        val latestId = state.show("新消息")

        state.dismiss(staleId)

        assertEquals(latestId, state.currentNotice?.id)
        state.dismiss(latestId)
        assertNull(state.currentNotice)
    }
}
