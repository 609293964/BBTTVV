package com.bbttvv.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackKeepScreenOnOwnerRegistryTest {
    @Test
    fun `releasing one owner keeps screen on while another owner remains active`() {
        val registry = PlaybackKeepScreenOnOwnerRegistry()
        val firstOwner = PlaybackKeepScreenOnOwner()
        val secondOwner = PlaybackKeepScreenOnOwner()

        assertTrue(registry.update(firstOwner, keepScreenOn = true))
        assertTrue(registry.update(secondOwner, keepScreenOn = true))
        assertTrue(registry.update(firstOwner, keepScreenOn = false))
        assertEquals(1, registry.activeOwnerCount)
    }

    @Test
    fun `releasing last owner allows screen to turn off`() {
        val registry = PlaybackKeepScreenOnOwnerRegistry()
        val owner = PlaybackKeepScreenOnOwner()

        assertTrue(registry.update(owner, keepScreenOn = true))
        assertFalse(registry.update(owner, keepScreenOn = false))
        assertEquals(0, registry.activeOwnerCount)
    }

    @Test
    fun `duplicate activation and stale release are idempotent`() {
        val registry = PlaybackKeepScreenOnOwnerRegistry()
        val owner = PlaybackKeepScreenOnOwner()
        val staleOwner = PlaybackKeepScreenOnOwner()

        assertTrue(registry.update(owner, keepScreenOn = true))
        assertTrue(registry.update(owner, keepScreenOn = true))
        assertTrue(registry.update(staleOwner, keepScreenOn = false))
        assertEquals(1, registry.activeOwnerCount)
    }
}
