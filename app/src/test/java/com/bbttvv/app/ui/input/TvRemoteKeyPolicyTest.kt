package com.bbttvv.app.ui.input

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvRemoteKeyPolicyTest {
    @Test
    fun `key families include common tv remote variants`() {
        listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B).forEach {
            assertTrue(isTvBackKey(it))
        }
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER).forEach {
            assertTrue(isTvConfirmKey(it))
        }
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
        ).forEach {
            assertTrue(isTvDirectionalKey(it))
        }
    }

    @Test
    fun `system back is excluded from modal preview dismiss keys`() {
        assertFalse(isTvModalDismissKey(KeyEvent.KEYCODE_BACK))
        assertTrue(isTvModalDismissKey(KeyEvent.KEYCODE_ESCAPE))
        assertTrue(isTvModalDismissKey(KeyEvent.KEYCODE_MENU))
        assertTrue(isTvModalDismissKey(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test
    fun `single press consumes full cycle and triggers only initial down`() {
        val down = resolveTvSinglePress(KeyEvent.ACTION_DOWN, repeatCount = 0, isHandledKey = true)
        val repeat = resolveTvSinglePress(KeyEvent.ACTION_DOWN, repeatCount = 2, isHandledKey = true)
        val up = resolveTvSinglePress(KeyEvent.ACTION_UP, repeatCount = 0, isHandledKey = true)

        assertTrue(down.isConsumed)
        assertTrue(down.shouldTrigger)
        assertTrue(repeat.isConsumed)
        assertFalse(repeat.shouldTrigger)
        assertTrue(up.isConsumed)
        assertFalse(up.shouldTrigger)
    }

    @Test
    fun `unmapped press falls through`() {
        val decision = resolveTvSinglePress(
            action = KeyEvent.ACTION_DOWN,
            repeatCount = 0,
            isHandledKey = false,
        )

        assertFalse(decision.isConsumed)
        assertFalse(decision.shouldTrigger)
    }
}
