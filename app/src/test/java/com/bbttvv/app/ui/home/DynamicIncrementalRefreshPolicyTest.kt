package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.DynamicAuthorModule
import com.bbttvv.app.data.model.response.DynamicItem
import com.bbttvv.app.data.model.response.DynamicModules
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicIncrementalRefreshPolicyTest {
    @Test
    fun newItemsArePrependedAndDuplicatesUseFreshPayload() {
        val oldA = item(id = "a", type = "old-a")
        val oldB = item(id = "b", type = "old-b")
        val freshB = item(id = "b", type = "fresh-b")
        val freshC = item(id = "c", type = "fresh-c")

        val merged = mergeIncrementalDynamicItems(
            existing = listOf(oldA, oldB),
            incoming = listOf(freshC, freshB)
        )

        assertEquals(listOf("c", "b", "a"), merged.map { it.id_str })
        assertEquals("fresh-b", merged[1].type)
    }

    @Test
    fun fallbackKeyKeepsAnonymousItemsStable() {
        val existing = item(id = "", type = "video", mid = 7L, pubTs = 9L)
        val incoming = item(id = "", type = "video", mid = 7L, pubTs = 9L)

        val merged = mergeIncrementalDynamicItems(
            existing = listOf(existing),
            incoming = listOf(incoming)
        )

        assertEquals(1, merged.size)
        assertEquals("video-7-9", dynamicFeedItemKey(merged.single()))
    }

    private fun item(
        id: String,
        type: String,
        mid: Long = 0L,
        pubTs: Long = 0L
    ): DynamicItem {
        return DynamicItem(
            id_str = id,
            type = type,
            modules = DynamicModules(
                module_author = DynamicAuthorModule(
                    mid = mid,
                    pub_ts = pubTs
                )
            )
        )
    }
}
