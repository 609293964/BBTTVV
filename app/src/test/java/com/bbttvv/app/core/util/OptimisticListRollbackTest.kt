package com.bbttvv.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class OptimisticListRollbackTest {
    @Test
    fun `failed mutation restores only its own item`() {
        val currentAfterOtherSuccess = listOf("A", "D")

        val restored = restoreItemIfMissing(
            current = currentAfterOtherSuccess,
            item = "B",
            originalIndex = 1,
            sameItem = { it == "B" }
        )

        assertEquals(listOf("A", "B", "D"), restored)
    }

    @Test
    fun `rollback does not duplicate item restored by newer state`() {
        val current = listOf("A", "B", "D")

        val restored = restoreItemIfMissing(
            current = current,
            item = "B",
            originalIndex = 1,
            sameItem = { it == "B" }
        )

        assertEquals(current, restored)
    }
}
