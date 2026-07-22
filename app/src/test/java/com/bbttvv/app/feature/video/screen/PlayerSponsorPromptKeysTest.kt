package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSponsorPromptKeysTest {
    @Test
    fun `confirm key skips sponsor notice`() {
        var skipCount = 0
        var dismissCount = 0

        val handled = handleSponsorSkipNoticeKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            showSponsorSkipNotice = true,
            onSkipSponsor = { skipCount++ },
            onDismissSponsorNotice = { dismissCount++ },
        )

        assertTrue(handled)
        assertEquals(1, skipCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun `back key dismisses sponsor notice`() {
        var skipCount = 0
        var dismissCount = 0

        val handled = handleSponsorSkipNoticeKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_BACK,
            showSponsorSkipNotice = true,
            onSkipSponsor = { skipCount++ },
            onDismissSponsorNotice = { dismissCount++ },
        )

        assertTrue(handled)
        assertEquals(0, skipCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun `keys fall through when sponsor notice is hidden`() {
        var skipCount = 0
        var dismissCount = 0

        val handled = handleSponsorSkipNoticeKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_ENTER,
            showSponsorSkipNotice = false,
            onSkipSponsor = { skipCount++ },
            onDismissSponsorNotice = { dismissCount++ },
        )

        assertFalse(handled)
        assertEquals(0, skipCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun `key up is consumed without triggering sponsor notice again`() {
        var skipCount = 0
        var dismissCount = 0

        val handled = handleSponsorSkipNoticeKeyEvent(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            showSponsorSkipNotice = true,
            onSkipSponsor = { skipCount++ },
            onDismissSponsorNotice = { dismissCount++ },
        )

        assertTrue(handled)
        assertEquals(0, skipCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun `confirm repeat is consumed without skipping twice`() {
        var skipCount = 0

        val handled = handleSponsorSkipNoticeKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            repeatCount = 1,
            showSponsorSkipNotice = true,
            onSkipSponsor = { skipCount++ },
            onDismissSponsorNotice = {},
        )

        assertTrue(handled)
        assertEquals(0, skipCount)
    }

    @Test
    fun `gamepad back dismisses sponsor notice`() {
        var dismissCount = 0

        val handled = handleSponsorSkipNoticeKeyEvent(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_BUTTON_B,
            showSponsorSkipNotice = true,
            onSkipSponsor = {},
            onDismissSponsorNotice = { dismissCount++ },
        )

        assertTrue(handled)
        assertEquals(1, dismissCount)
    }
}
