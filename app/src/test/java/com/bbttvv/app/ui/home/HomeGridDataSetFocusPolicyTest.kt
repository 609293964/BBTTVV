package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGridDataSetFocusPolicyTest {
    @Test
    fun `focused child is kept when its key survives the next list`() {
        assertTrue(
            HomeGridDataSetFocusPolicy.shouldKeepFocusedChild(
                focusIsRecyclerContainer = false,
                focusedKey = "bvid:1",
                nextPositionForKey = 8,
            )
        )
    }

    @Test
    fun `container focus must be restored to a child even when the key survives`() {
        assertFalse(
            HomeGridDataSetFocusPolicy.shouldKeepFocusedChild(
                focusIsRecyclerContainer = true,
                focusedKey = "bvid:1",
                nextPositionForKey = 8,
            )
        )
    }

    @Test
    fun `pending restore prefers surviving stable key position`() {
        assertEquals(
            8,
            HomeGridDataSetFocusPolicy.pendingPosition(
                nextItemCount = 12,
                nextPositionForKey = 8,
                focusedPosition = 0,
                rememberedPosition = 4,
                firstVisiblePosition = 0,
            )
        )
    }

    @Test
    fun `pending restore falls back to previous focus position when key disappears`() {
        assertEquals(
            6,
            HomeGridDataSetFocusPolicy.pendingPosition(
                nextItemCount = 10,
                nextPositionForKey = null,
                focusedPosition = 6,
                rememberedPosition = 2,
                firstVisiblePosition = 0,
            )
        )
    }

    @Test
    fun `pending restore clamps fallback to next item bounds`() {
        assertEquals(
            4,
            HomeGridDataSetFocusPolicy.pendingPosition(
                nextItemCount = 5,
                nextPositionForKey = null,
                focusedPosition = 9,
                rememberedPosition = RecyclerView.NO_POSITION,
                firstVisiblePosition = RecyclerView.NO_POSITION,
            )
        )
    }

    @Test
    fun `pending target resolves key before fallback`() {
        assertEquals(
            3,
            HomeGridDataSetFocusPolicy.resolvePendingTarget(
                itemCount = 8,
                keyPosition = 3,
                fallbackPosition = 6,
            )
        )
    }
}
