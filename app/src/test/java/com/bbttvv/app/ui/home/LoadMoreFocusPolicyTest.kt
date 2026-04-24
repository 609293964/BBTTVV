package com.bbttvv.app.ui.home

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LoadMoreFocusPolicyTest {
    @Test
    fun `same column next row is preferred when it is newly appended`() {
        val pending = LoadMoreFocusPolicy.create(
            anchorPosition = 8,
            oldItemCount = 10,
            spanCount = 4,
        )

        assertNotNull(pending)
        assertEquals(
            12,
            LoadMoreFocusPolicy.targetPosition(
                pending = pending!!,
                spanCount = 4,
                itemCount = 13,
            )
        )
    }

    @Test
    fun `same column appended item is preferred before first appended fallback`() {
        val pending = LoadMoreFocusPolicy.create(
            anchorPosition = 9,
            oldItemCount = 10,
            spanCount = 4,
        )

        assertNotNull(pending)
        assertEquals(
            13,
            LoadMoreFocusPolicy.targetPosition(
                pending = pending!!,
                spanCount = 4,
                itemCount = 15,
            )
        )
    }

    @Test
    fun `first appended item is used when same column is unavailable`() {
        val pending = LoadMoreFocusPolicy.create(
            anchorPosition = 9,
            oldItemCount = 10,
            spanCount = 4,
        )

        assertNotNull(pending)
        assertEquals(
            10,
            LoadMoreFocusPolicy.targetPosition(
                pending = pending!!,
                spanCount = 4,
                itemCount = 13,
            )
        )
    }

    @Test
    fun `invalid anchors and missing appended items do not produce focus targets`() {
        assertNull(
            LoadMoreFocusPolicy.create(
                anchorPosition = RecyclerView.NO_POSITION,
                oldItemCount = 10,
                spanCount = 4,
            )
        )
        assertNull(
            LoadMoreFocusPolicy.create(
                anchorPosition = 10,
                oldItemCount = 10,
                spanCount = 4,
            )
        )

        val pending = LoadMoreFocusPolicy.create(
            anchorPosition = 8,
            oldItemCount = 10,
            spanCount = 4,
        )

        assertNotNull(pending)
        assertEquals(
            RecyclerView.NO_POSITION,
            LoadMoreFocusPolicy.targetPosition(
                pending = pending!!,
                spanCount = 4,
                itemCount = 10,
            )
        )
    }
}
