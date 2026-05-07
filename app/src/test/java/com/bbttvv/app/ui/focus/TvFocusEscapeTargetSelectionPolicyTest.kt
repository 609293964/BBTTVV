package com.bbttvv.app.ui.focus

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvFocusEscapeTargetSelectionPolicyTest {
    @Test
    fun `missing focus prefers higher priority target over remembered lower target`() {
        val home = entry(key = "home", priority = 0, order = 0)
        val dialog = entry(key = "resume_dialog", priority = 100, order = 1)

        val selected = TvFocusEscapeTargetSelectionPolicy.selectForMissingFocus(
            entries = listOf(home, dialog),
            lastFocusedTargetKey = "home",
        )

        assertEquals("resume_dialog", selected?.key)
    }

    @Test
    fun `missing focus keeps remembered target when no higher priority target exists`() {
        val home = entry(key = "home", priority = 0, order = 0)
        val details = entry(key = "details", priority = 0, order = 1)

        val selected = TvFocusEscapeTargetSelectionPolicy.selectForMissingFocus(
            entries = listOf(home, details),
            lastFocusedTargetKey = "home",
        )

        assertEquals("home", selected?.key)
    }

    @Test
    fun `missing focus falls back to latest registration within highest priority`() {
        val menu = entry(key = "menu", priority = 80, order = 1)
        val dialog = entry(key = "resume_dialog", priority = 80, order = 2)

        val selected = TvFocusEscapeTargetSelectionPolicy.selectForMissingFocus(
            entries = listOf(menu, dialog),
            lastFocusedTargetKey = null,
        )

        assertEquals("resume_dialog", selected?.key)
    }

    @Test
    fun `missing focus returns no target when registry is empty`() {
        val selected = TvFocusEscapeTargetSelectionPolicy.selectForMissingFocus(
            entries = emptyList(),
            lastFocusedTargetKey = "home",
        )

        assertNull(selected)
    }

    private fun entry(
        key: String,
        priority: Int,
        order: Long,
    ): TvFocusEscapeTargetEntry {
        return TvFocusEscapeTargetEntry(
            key = key,
            priority = priority,
            order = order,
            target = object : TvFocusEscapeTarget {
                override fun acceptsFocus(focusedView: View): Boolean = false

                override fun recoverFocus(reason: TvFocusEscapeReason): Boolean = false
            },
        )
    }
}
