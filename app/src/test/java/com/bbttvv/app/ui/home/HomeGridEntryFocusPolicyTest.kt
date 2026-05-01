package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGridEntryFocusPolicyTest {
    @Test
    fun `preferred index maps to same visible grid column`() {
        val target = HomeGridEntryFocusPolicy.targetPosition(
            itemCount = 20,
            spanCount = 4,
            firstVisiblePosition = 0,
            preferredIndex = 2,
        )

        assertEquals(2, target)
    }

    @Test
    fun `preferred index is clamped to last grid column`() {
        val target = HomeGridEntryFocusPolicy.targetPosition(
            itemCount = 20,
            spanCount = 4,
            firstVisiblePosition = 0,
            preferredIndex = 9,
        )

        assertEquals(3, target)
    }

    @Test
    fun `entry targets the first visible row`() {
        val target = HomeGridEntryFocusPolicy.targetPosition(
            itemCount = 20,
            spanCount = 4,
            firstVisiblePosition = 8,
            preferredIndex = 1,
        )

        assertEquals(9, target)
    }

    @Test
    fun `entry clamps inside a short final row`() {
        val target = HomeGridEntryFocusPolicy.targetPosition(
            itemCount = 10,
            spanCount = 4,
            firstVisiblePosition = 8,
            preferredIndex = 3,
        )

        assertEquals(9, target)
    }

    @Test
    fun `invalid grid inputs return no position`() {
        assertEquals(
            RecyclerView.NO_POSITION,
            HomeGridEntryFocusPolicy.targetPosition(
                itemCount = 0,
                spanCount = 4,
                firstVisiblePosition = 0,
                preferredIndex = 0,
            )
        )
        assertEquals(
            RecyclerView.NO_POSITION,
            HomeGridEntryFocusPolicy.targetPosition(
                itemCount = 10,
                spanCount = 0,
                firstVisiblePosition = 0,
                preferredIndex = 0,
            )
        )
    }
}
