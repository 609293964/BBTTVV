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
}
