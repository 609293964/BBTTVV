package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerModalKeyReleaseGuardTest {
    @Test
    fun `confirm key up remains consumed after modal closes`() {
        val guard = PlayerModalKeyReleaseGuard()

        guard.recordConsumedModalEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            repeatCount = 0,
        )

        assertTrue(
            guard.consumeTrailingEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                repeatCount = 0,
            ),
        )
        assertFalse(
            guard.consumeTrailingEvent(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                repeatCount = 0,
            ),
        )
    }

    @Test
    fun `fresh press is not consumed when previous key up was lost`() {
        val guard = PlayerModalKeyReleaseGuard()
        guard.recordConsumedModalEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_ENTER,
            repeatCount = 0,
        )

        assertFalse(
            guard.consumeTrailingEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_ENTER,
                repeatCount = 0,
            ),
        )
    }

    @Test
    fun `unrelated player key is not consumed`() {
        val guard = PlayerModalKeyReleaseGuard()
        guard.recordConsumedModalEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            repeatCount = 0,
        )

        assertFalse(
            guard.consumeTrailingEvent(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_DPAD_UP,
                repeatCount = 0,
            ),
        )
    }
}
