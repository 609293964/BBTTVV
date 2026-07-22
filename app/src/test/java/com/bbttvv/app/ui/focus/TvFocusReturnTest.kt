package com.bbttvv.app.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFocusReturnTest {
    @Test
    fun `restore focuses captured key and clears capture`() {
        val focusReturn = TvFocusReturn()
        val target = FakeFocusReturnTarget(canFocus = true)
        focusReturn.registerTarget("settings:user_agent", target)

        focusReturn.capture("settings:user_agent")

        assertTrue(focusReturn.restore())
        assertEquals(1, target.focusRequests)
        assertFalse(focusReturn.restore())
        assertEquals(1, target.focusRequests)
    }

    @Test
    fun `restore falls back when captured key is missing`() {
        val focusReturn = TvFocusReturn()
        val fallback = FakeFocusReturnTarget(canFocus = true)
        focusReturn.registerTarget("settings:back", fallback)

        focusReturn.capture(
            key = "settings:user_agent",
            fallbackKeys = listOf("settings:back"),
        )

        assertTrue(focusReturn.restore())
        assertEquals(1, fallback.focusRequests)
    }

    @Test
    fun `restore tries fallback when captured target cannot focus`() {
        val focusReturn = TvFocusReturn()
        val primary = FakeFocusReturnTarget(canFocus = false)
        val fallback = FakeFocusReturnTarget(canFocus = true)
        focusReturn.registerTarget("settings:user_agent", primary)
        focusReturn.registerTarget("settings:back", fallback)

        focusReturn.capture(
            key = "settings:user_agent",
            fallbackKeys = listOf("settings:back"),
        )

        assertTrue(focusReturn.restore())
        assertEquals(1, primary.focusRequests)
        assertEquals(1, fallback.focusRequests)
    }

    @Test
    fun `unregistered target is not restored`() {
        val focusReturn = TvFocusReturn()
        val target = FakeFocusReturnTarget(canFocus = true)
        val registration = focusReturn.registerTarget("settings:user_agent", target)

        focusReturn.capture("settings:user_agent")
        registration.unregister()

        assertFalse(focusReturn.restore())
        assertEquals(0, target.focusRequests)
    }

    @Test
    fun `pending restore survives until primary target registers`() {
        val focusReturn = TvFocusReturn()

        focusReturn.capture("settings:user_agent")

        assertFalse(focusReturn.restorePending())
        val target = FakeFocusReturnTarget(canFocus = true)
        focusReturn.registerTarget("settings:user_agent", target)

        assertEquals(1, target.focusRequests)
        assertFalse(focusReturn.restorePending())
    }

    @Test
    fun `failed pending restore remains available for a later target`() {
        val focusReturn = TvFocusReturn()
        val unavailable = FakeFocusReturnTarget(canFocus = false)
        focusReturn.registerTarget("settings:user_agent", unavailable)
        focusReturn.capture("settings:user_agent")

        assertFalse(focusReturn.restorePending())
        val available = FakeFocusReturnTarget(canFocus = true)
        focusReturn.registerTarget("settings:user_agent", available)

        assertEquals(1, unavailable.focusRequests)
        assertEquals(1, available.focusRequests)
        assertFalse(focusReturn.restorePending())
    }

    private class FakeFocusReturnTarget(
        private val canFocus: Boolean,
    ) : TvFocusReturnTarget {
        var focusRequests: Int = 0
            private set

        override fun tryRequestFocus(): Boolean {
            focusRequests++
            return canFocus
        }
    }
}
