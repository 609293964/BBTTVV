package com.bbttvv.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogFocusTargetStateTest {
    @Test
    fun `attached shown enabled focusable target is restorable`() {
        val state = DialogFocusTargetState(
            isAttachedToWindow = true,
            isShown = true,
            isEnabled = true,
            isFocusable = true,
        )

        assertTrue(state.isRestorableDialogFocusTarget())
    }

    @Test
    fun `detached target is not restorable`() {
        val state = restorableState().copy(isAttachedToWindow = false)

        assertFalse(state.isRestorableDialogFocusTarget())
    }

    @Test
    fun `hidden target is not restorable`() {
        val state = restorableState().copy(isShown = false)

        assertFalse(state.isRestorableDialogFocusTarget())
    }

    @Test
    fun `disabled target is not restorable`() {
        val state = restorableState().copy(isEnabled = false)

        assertFalse(state.isRestorableDialogFocusTarget())
    }

    @Test
    fun `non focusable target is not restorable`() {
        val state = restorableState().copy(isFocusable = false)

        assertFalse(state.isRestorableDialogFocusTarget())
    }

    private fun restorableState(): DialogFocusTargetState {
        return DialogFocusTargetState(
            isAttachedToWindow = true,
            isShown = true,
            isEnabled = true,
            isFocusable = true,
        )
    }
}
