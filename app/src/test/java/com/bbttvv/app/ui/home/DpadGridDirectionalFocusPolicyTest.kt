package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DpadGridDirectionalFocusPolicyTest {
    @Test
    fun `parked directional target wins over missing position and remembered focus`() {
        val resolved = DpadGridDirectionalFocusPolicy.resolvePosition(
            candidatePosition = RecyclerView.NO_POSITION,
            itemCount = 20,
            spanCount = 4,
            lastKnownFocusedPosition = 4,
            lastKnownFocusedColumn = 0,
            directionalScrollTargetPosition = 8,
            isDirectionalScrollParked = true,
        )

        assertEquals(8, resolved)
    }

    @Test
    fun `parked directional target wins over fallback child focus`() {
        val resolved = DpadGridDirectionalFocusPolicy.resolvePosition(
            candidatePosition = 0,
            itemCount = 20,
            spanCount = 4,
            lastKnownFocusedPosition = 4,
            lastKnownFocusedColumn = 0,
            directionalScrollTargetPosition = 8,
            isDirectionalScrollParked = true,
        )

        assertEquals(8, resolved)
    }

    @Test
    fun `current candidate wins after directional parking ends`() {
        val resolved = DpadGridDirectionalFocusPolicy.resolvePosition(
            candidatePosition = 0,
            itemCount = 20,
            spanCount = 4,
            lastKnownFocusedPosition = 4,
            lastKnownFocusedColumn = 0,
            directionalScrollTargetPosition = 8,
            isDirectionalScrollParked = false,
        )

        assertEquals(0, resolved)
    }

    @Test
    fun `fallback focus is rejected only while parked target is valid`() {
        assertTrue(
            DpadGridDirectionalFocusPolicy.shouldRejectFallbackFocus(
                position = 0,
                itemCount = 20,
                directionalScrollTargetPosition = 8,
                isDirectionalScrollParked = true,
            )
        )
        assertFalse(
            DpadGridDirectionalFocusPolicy.shouldRejectFallbackFocus(
                position = 8,
                itemCount = 20,
                directionalScrollTargetPosition = 8,
                isDirectionalScrollParked = true,
            )
        )
        assertFalse(
            DpadGridDirectionalFocusPolicy.shouldRejectFallbackFocus(
                position = 0,
                itemCount = 4,
                directionalScrollTargetPosition = 8,
                isDirectionalScrollParked = true,
            )
        )
    }

    @Test
    fun `directional smooth scroll stops after intended target receives focus`() {
        assertTrue(
            DpadGridDirectionalFocusPolicy.shouldStopScrollAfterTargetFocus(
                focusedPosition = 8,
                directionalScrollTargetPosition = 8,
                isDirectionalScrollParked = true,
            )
        )
        assertFalse(
            DpadGridDirectionalFocusPolicy.shouldStopScrollAfterTargetFocus(
                focusedPosition = 4,
                directionalScrollTargetPosition = 8,
                isDirectionalScrollParked = true,
            )
        )
        assertFalse(
            DpadGridDirectionalFocusPolicy.shouldStopScrollAfterTargetFocus(
                focusedPosition = 8,
                directionalScrollTargetPosition = RecyclerView.NO_POSITION,
                isDirectionalScrollParked = true,
            )
        )
        assertFalse(
            DpadGridDirectionalFocusPolicy.shouldStopScrollAfterTargetFocus(
                focusedPosition = 8,
                directionalScrollTargetPosition = 8,
                isDirectionalScrollParked = false,
            )
        )
    }

    @Test
    fun `attached partial target defers focus until smooth scroll can run`() {
        assertTrue(
            DpadGridDirectionalFocusPolicy.shouldDeferAttachedTargetFocusForSmoothScroll(
                isTargetFullyVisible = false,
                scrollDistancePx = 240,
                canScrollInDirection = true,
            )
        )
        assertTrue(
            DpadGridDirectionalFocusPolicy.shouldDeferAttachedTargetFocusForSmoothScroll(
                isTargetFullyVisible = false,
                scrollDistancePx = -240,
                canScrollInDirection = true,
            )
        )
    }

    @Test
    fun `attached target focuses immediately when no smooth scroll is needed`() {
        assertFalse(
            DpadGridDirectionalFocusPolicy.shouldDeferAttachedTargetFocusForSmoothScroll(
                isTargetFullyVisible = true,
                scrollDistancePx = 240,
                canScrollInDirection = true,
            )
        )
        assertFalse(
            DpadGridDirectionalFocusPolicy.shouldDeferAttachedTargetFocusForSmoothScroll(
                isTargetFullyVisible = false,
                scrollDistancePx = 0,
                canScrollInDirection = true,
            )
        )
        assertFalse(
            DpadGridDirectionalFocusPolicy.shouldDeferAttachedTargetFocusForSmoothScroll(
                isTargetFullyVisible = false,
                scrollDistancePx = 240,
                canScrollInDirection = false,
            )
        )
    }

    @Test
    fun `terminal fallback keeps the requested column and chooses the nearest visible row`() {
        assertEquals(
            8,
            DpadGridDirectionalIntentPolicy.fallbackPosition(
                targetPosition = 12,
                lastKnownFocusedPosition = 4,
                lastKnownFocusedColumn = 0,
                firstVisiblePosition = 4,
                lastVisiblePosition = 11,
                itemCount = 20,
                spanCount = 4,
            ),
        )
    }

    @Test
    fun `terminal fallback uses remembered focus when layout exposes no visible range`() {
        assertEquals(
            6,
            DpadGridDirectionalIntentPolicy.fallbackPosition(
                targetPosition = 10,
                lastKnownFocusedPosition = 6,
                lastKnownFocusedColumn = 2,
                firstVisiblePosition = RecyclerView.NO_POSITION,
                lastVisiblePosition = RecyclerView.NO_POSITION,
                itemCount = 20,
                spanCount = 4,
            ),
        )
    }

    @Test
    fun `terminal fallback never leaves the visible adapter range`() {
        assertEquals(
            8,
            DpadGridDirectionalIntentPolicy.fallbackPosition(
                targetPosition = 3,
                lastKnownFocusedPosition = 4,
                lastKnownFocusedColumn = 3,
                firstVisiblePosition = 8,
                lastVisiblePosition = 9,
                itemCount = 10,
                spanCount = 4,
            ),
        )
    }
}
