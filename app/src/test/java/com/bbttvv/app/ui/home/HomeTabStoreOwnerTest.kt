package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTabStoreOwnerTest {
    @Test
    fun `idle policy keeps previous tab during ttl window`() {
        var nowMs = 0L
        val owner = HomeTabStoreOwner(
            policy = TabStorePolicy.KeepSelectedOnlyAfterIdle(idleTtlMs = 30_000L),
            elapsedRealtimeMs = { nowMs }
        )

        owner.getOrCreate(AppTopLevelTab.RECOMMEND)
        assertNull(owner.trimForSelected(AppTopLevelTab.RECOMMEND))

        nowMs = 1_000L
        owner.getOrCreate(AppTopLevelTab.POPULAR)
        val delayMs = owner.trimForSelected(AppTopLevelTab.POPULAR)

        assertEquals(30_000L, delayMs)
        assertEquals(2, owner.storeCount())
        assertTrue(owner.hasStore(AppTopLevelTab.RECOMMEND))
        assertTrue(owner.hasStore(AppTopLevelTab.POPULAR))
    }

    @Test
    fun `idle policy trims previous tab after ttl expires`() {
        var nowMs = 0L
        val owner = HomeTabStoreOwner(
            policy = TabStorePolicy.KeepSelectedOnlyAfterIdle(idleTtlMs = 30_000L),
            elapsedRealtimeMs = { nowMs }
        )

        owner.getOrCreate(AppTopLevelTab.RECOMMEND)
        owner.trimForSelected(AppTopLevelTab.RECOMMEND)
        nowMs = 1_000L
        owner.getOrCreate(AppTopLevelTab.POPULAR)
        owner.trimForSelected(AppTopLevelTab.POPULAR)

        nowMs = 31_000L
        assertNull(owner.trimForSelected(AppTopLevelTab.POPULAR))

        assertEquals(1, owner.storeCount())
        assertFalse(owner.hasStore(AppTopLevelTab.RECOMMEND))
        assertTrue(owner.hasStore(AppTopLevelTab.POPULAR))
    }

    @Test
    fun `idle policy protects only the immediate previous tab`() {
        var nowMs = 0L
        val owner = HomeTabStoreOwner(
            policy = TabStorePolicy.KeepSelectedOnlyAfterIdle(idleTtlMs = 30_000L),
            elapsedRealtimeMs = { nowMs }
        )

        owner.getOrCreate(AppTopLevelTab.RECOMMEND)
        owner.trimForSelected(AppTopLevelTab.RECOMMEND)
        nowMs = 1_000L
        owner.getOrCreate(AppTopLevelTab.POPULAR)
        owner.trimForSelected(AppTopLevelTab.POPULAR)
        nowMs = 2_000L
        owner.getOrCreate(AppTopLevelTab.LIVE)
        owner.trimForSelected(AppTopLevelTab.LIVE)

        assertEquals(2, owner.storeCount())
        assertFalse(owner.hasStore(AppTopLevelTab.RECOMMEND))
        assertTrue(owner.hasStore(AppTopLevelTab.POPULAR))
        assertTrue(owner.hasStore(AppTopLevelTab.LIVE))
    }
}
