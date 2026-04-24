package com.bbttvv.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DpadGridEdgePolicyTest {
    @Test
    fun firstRowAndFirstColumnAreDetected() {
        val edge = DpadGridEdgePolicy.resolve(position = 0, itemCount = 12, spanCount = 4)

        assertTrue(edge!!.isTop)
        assertTrue(edge.isLeft)
        assertFalse(edge.isRight)
        assertFalse(edge.isBottom)
    }

    @Test
    fun rightEdgeIncludesLastColumnAndLastItemInShortRow() {
        val lastFullRowColumn = DpadGridEdgePolicy.resolve(position = 3, itemCount = 10, spanCount = 4)
        val lastItemInShortRow = DpadGridEdgePolicy.resolve(position = 9, itemCount = 10, spanCount = 4)
        val firstItemInShortRow = DpadGridEdgePolicy.resolve(position = 8, itemCount = 10, spanCount = 4)

        assertTrue(lastFullRowColumn!!.isRight)
        assertTrue(lastItemInShortRow!!.isRight)
        assertFalse(firstItemInShortRow!!.isRight)
    }

    @Test
    fun bottomEdgeIncludesEveryItemInLastRow() {
        val firstItemInLastRow = DpadGridEdgePolicy.resolve(position = 8, itemCount = 10, spanCount = 4)
        val lastItemInLastRow = DpadGridEdgePolicy.resolve(position = 9, itemCount = 10, spanCount = 4)
        val previousRowItem = DpadGridEdgePolicy.resolve(position = 7, itemCount = 10, spanCount = 4)

        assertTrue(firstItemInLastRow!!.isBottom)
        assertTrue(lastItemInLastRow!!.isBottom)
        assertFalse(previousRowItem!!.isBottom)
    }

    @Test
    fun invalidGridInputsReturnNull() {
        assertNull(DpadGridEdgePolicy.resolve(position = -1, itemCount = 10, spanCount = 4))
        assertNull(DpadGridEdgePolicy.resolve(position = 10, itemCount = 10, spanCount = 4))
        assertNull(DpadGridEdgePolicy.resolve(position = 0, itemCount = 0, spanCount = 4))
        assertNull(DpadGridEdgePolicy.resolve(position = 0, itemCount = 10, spanCount = 0))
    }

    @Test
    fun dpadDownPreloadReturnsNextTwoRows() {
        val positions = DpadGridPreloadPolicy.positionsAhead(
            position = 5,
            itemCount = 20,
            spanCount = 4,
            rowCount = 2,
        )

        assertEquals((8 until 16).toList(), positions!!.toList())
    }

    @Test
    fun dpadDownPreloadClampsAtItemCount() {
        val positions = DpadGridPreloadPolicy.positionsAhead(
            position = 7,
            itemCount = 10,
            spanCount = 4,
            rowCount = 2,
        )

        assertEquals(listOf(8, 9), positions!!.toList())
    }

    @Test
    fun dpadDownPreloadReturnsNullAtBottomOrInvalidInputs() {
        assertNull(DpadGridPreloadPolicy.positionsAhead(position = 8, itemCount = 10, spanCount = 4, rowCount = 2))
        assertNull(DpadGridPreloadPolicy.positionsAhead(position = -1, itemCount = 10, spanCount = 4, rowCount = 2))
        assertNull(DpadGridPreloadPolicy.positionsAhead(position = 0, itemCount = 0, spanCount = 4, rowCount = 2))
        assertNull(DpadGridPreloadPolicy.positionsAhead(position = 0, itemCount = 10, spanCount = 0, rowCount = 2))
        assertNull(DpadGridPreloadPolicy.positionsAhead(position = 0, itemCount = 10, spanCount = 4, rowCount = 0))
    }
}
