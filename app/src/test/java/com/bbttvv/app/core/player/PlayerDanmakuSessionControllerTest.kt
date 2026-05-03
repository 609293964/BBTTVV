package com.bbttvv.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDanmakuSessionControllerTest {
    @Test
    fun `begin resolves initial segment from start position`() {
        val controller = PlayerDanmakuSessionController()

        val initialSegment = controller.begin(
            cid = 100L,
            aid = 200L,
            startPositionMs = 720_000L,
        )

        assertEquals(3, initialSegment)
        assertEquals(100L, controller.snapshot().cid)
        assertEquals(200L, controller.snapshot().aid)
    }

    @Test
    fun `prefetch window returns current plus nearby unloaded segments`() {
        val controller = PlayerDanmakuSessionController(windowRadius = 1)
        val initialSegment = controller.begin(
            cid = 100L,
            aid = 200L,
            startPositionMs = 360_000L,
        )
        controller.markLoaded(initialSegment)

        assertEquals(listOf(1, 3), controller.prefetchWindow(positionMs = 360_000L))
    }

    @Test
    fun `loading and failed segments are not scheduled again`() {
        val controller = PlayerDanmakuSessionController(windowRadius = 1)
        controller.begin(cid = 100L, aid = 200L, startPositionMs = 360_000L)
        controller.markLoaded(2)

        assertTrue(controller.markLoading(1))
        assertFalse(controller.markLoading(1))
        controller.markFailed(3)

        assertEquals(emptyList<Int>(), controller.prefetchWindow(positionMs = 360_000L))
        val snapshot = controller.snapshot()
        assertEquals(setOf(1), snapshot.loadingSegments)
        assertEquals(setOf(3), snapshot.failedSegments)
    }

    @Test
    fun `clear resets all session state`() {
        val controller = PlayerDanmakuSessionController()
        controller.begin(cid = 100L, aid = 200L, startPositionMs = 0L)
        controller.markLoaded(1)
        controller.markLoading(2)
        controller.markFailed(3)

        controller.clear()

        val snapshot = controller.snapshot()
        assertEquals(0L, snapshot.cid)
        assertEquals(0L, snapshot.aid)
        assertTrue(snapshot.loadedSegments.isEmpty())
        assertTrue(snapshot.loadingSegments.isEmpty())
        assertTrue(snapshot.failedSegments.isEmpty())
        assertFalse(controller.matches(100L))
    }
}
