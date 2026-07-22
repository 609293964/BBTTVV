package com.bbttvv.app.feature.live

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlayerKeyPolicyTest {
    @Test
    fun `directional repeat remains navigable`() {
        val decision = resolveLivePlayerKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
            repeatCount = 3,
        )

        assertTrue(decision.isConsumed)
        assertEquals(LivePlayerKeyCommand.MoveRight, decision.command)
    }

    @Test
    fun `confirm and back repeats are consumed without executing again`() {
        listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK).forEach { keyCode ->
            val decision = resolveLivePlayerKeyDecision(
                action = KeyEvent.ACTION_DOWN,
                keyCode = keyCode,
                repeatCount = 1,
            )
            assertTrue(decision.isConsumed)
            assertNull(decision.command)
        }
    }

    @Test
    fun `play pause and toggle keys keep distinct semantics`() {
        assertEquals(
            LivePlayerKeyCommand.Play,
            decisionFor(KeyEvent.KEYCODE_MEDIA_PLAY).command,
        )
        assertEquals(
            LivePlayerKeyCommand.Pause,
            decisionFor(KeyEvent.KEYCODE_MEDIA_PAUSE).command,
        )
        assertEquals(
            LivePlayerKeyCommand.TogglePlayback,
            decisionFor(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE).command,
        )
    }

    @Test
    fun `single shot key up is consumed to prevent one press reaching a parent`() {
        listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MENU,
        ).forEach { keyCode ->
            val decision = resolveLivePlayerKeyDecision(
                action = KeyEvent.ACTION_UP,
                keyCode = keyCode,
                repeatCount = 0,
            )
            assertTrue(decision.isConsumed)
            assertNull(decision.command)
        }
    }

    @Test
    fun `directional key up and unknown keys are not consumed`() {
        assertFalse(
            resolveLivePlayerKeyDecision(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                repeatCount = 0,
            ).isConsumed
        )
        assertFalse(decisionFor(KeyEvent.KEYCODE_VOLUME_UP).isConsumed)
    }

    private fun decisionFor(keyCode: Int): LivePlayerKeyDecision {
        return resolveLivePlayerKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = keyCode,
            repeatCount = 0,
        )
    }
}
