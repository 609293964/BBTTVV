package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridDataSetFocusPolicyTest {
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

    @Test
    fun `menu refresh pending target prefers first position over surviving key`() {
        assertEquals(
            0,
            HomeGridDataSetFocusPolicy.resolvePendingTarget(
                itemCount = 8,
                keyPosition = 5,
                fallbackPosition = HomeGridDataSetFocusPolicy.menuRefreshFocusPosition(itemCount = 8),
                preferFallbackPosition = true,
            )
        )
    }

    @Test
    fun `menu refresh has no focus target for empty list`() {
        assertEquals(
            RecyclerView.NO_POSITION,
            HomeGridDataSetFocusPolicy.menuRefreshFocusPosition(itemCount = 0)
        )
    }
}
