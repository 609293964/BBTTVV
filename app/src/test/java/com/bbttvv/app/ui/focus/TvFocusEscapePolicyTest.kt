package com.bbttvv.app.ui.focus

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFocusEscapePolicyTest {
    @Test
    fun `missing focus on first directional dpad press requests recovery`() {
        assertTrue(
            TvFocusEscapePolicy.shouldRecoverOnDirectionalKey(
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                hasCurrentFocus = false,
                focusNeedsRecovery = true,
                hasRecoveryTarget = true,
            )
        )
    }

    @Test
    fun `expected focus does not trigger recovery`() {
        assertFalse(
            TvFocusEscapePolicy.shouldRecoverOnDirectionalKey(
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                hasCurrentFocus = true,
                focusNeedsRecovery = false,
                hasRecoveryTarget = true,
            )
        )
    }

    @Test
    fun `repeat dpad events are not consumed by escape recovery`() {
        assertFalse(
            TvFocusEscapePolicy.shouldRecoverOnDirectionalKey(
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 1,
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                hasCurrentFocus = false,
                focusNeedsRecovery = true,
                hasRecoveryTarget = true,
            )
        )
    }

    @Test
    fun `center key is left to the focused control`() {
        assertFalse(
            TvFocusEscapePolicy.shouldRecoverOnDirectionalKey(
                action = KeyEvent.ACTION_DOWN,
                repeatCount = 0,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                hasCurrentFocus = false,
                focusNeedsRecovery = true,
                hasRecoveryTarget = true,
            )
        )
    }
}

