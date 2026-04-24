package com.bbttvv.app.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusProtectionPolicyTest {
    @Test
    fun `focused detached child is parked when recycler can take focus`() {
        assertTrue(
            FocusProtectionPolicy.shouldParkFocusOnDetach(
                recyclerCanTakeFocus = true,
                detachedViewHadFocus = true,
                focusedViewWasInsideDetachedView = false,
                focusedViewWasInsideRecycler = false,
                isProtectedDataSetChange = false,
            )
        )
    }

    @Test
    fun `descendant focus inside detached child is parked`() {
        assertTrue(
            FocusProtectionPolicy.shouldParkFocusOnDetach(
                recyclerCanTakeFocus = true,
                detachedViewHadFocus = false,
                focusedViewWasInsideDetachedView = true,
                focusedViewWasInsideRecycler = true,
                isProtectedDataSetChange = false,
            )
        )
    }

    @Test
    fun `protected data change parks focus that is still inside recycler`() {
        assertTrue(
            FocusProtectionPolicy.shouldParkFocusOnDetach(
                recyclerCanTakeFocus = true,
                detachedViewHadFocus = false,
                focusedViewWasInsideDetachedView = false,
                focusedViewWasInsideRecycler = true,
                isProtectedDataSetChange = true,
            )
        )
    }

    @Test
    fun `detaches without focused recycler descendants are ignored`() {
        assertFalse(
            FocusProtectionPolicy.shouldParkFocusOnDetach(
                recyclerCanTakeFocus = true,
                detachedViewHadFocus = false,
                focusedViewWasInsideDetachedView = false,
                focusedViewWasInsideRecycler = false,
                isProtectedDataSetChange = true,
            )
        )
    }

    @Test
    fun `invalid recycler target never parks focus`() {
        assertFalse(
            FocusProtectionPolicy.shouldParkFocusOnDetach(
                recyclerCanTakeFocus = false,
                detachedViewHadFocus = true,
                focusedViewWasInsideDetachedView = true,
                focusedViewWasInsideRecycler = true,
                isProtectedDataSetChange = true,
            )
        )
    }
}
