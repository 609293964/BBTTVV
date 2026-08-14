package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuVoteKeyPolicyTest {
    @Test
    fun `down and up move selection once while staying in vote domain`() {
        val down = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 0,
            lastIndex = 1,
        )
        val up = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = down.nextIndex,
            lastIndex = 1,
        )

        assertTrue(down.consumed)
        assertEquals(1, down.nextIndex)
        assertTrue(up.consumed)
        assertEquals(1, up.nextIndex)
    }

    @Test
    fun `selection stops at option boundaries`() {
        val aboveFirst = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_UP,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 0,
            lastIndex = 1,
        )
        val belowLast = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 1,
            lastIndex = 1,
        )

        assertEquals(0, aboveFirst.nextIndex)
        assertEquals(1, belowLast.nextIndex)
    }

    @Test
    fun `confirm selects exactly once and retry state retries`() {
        val selectDown = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 1,
            lastIndex = 1,
        )
        val selectUp = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 1,
            lastIndex = 1,
        )
        val retry = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_ENTER,
            repeatCount = 0,
            state = DanmakuVoteKeyState.RetryableError,
            currentIndex = 1,
            lastIndex = 1,
        )

        assertEquals(DanmakuVoteKeyCommand.Select, selectDown.command)
        assertEquals(DanmakuVoteKeyCommand.None, selectUp.command)
        assertEquals(DanmakuVoteKeyCommand.Retry, retry.command)
    }

    @Test
    fun `back is consumed and dismisses only on initial down`() {
        val down = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_BACK,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 0,
            lastIndex = 1,
        )
        val repeat = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_BACK,
            repeatCount = 1,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 0,
            lastIndex = 1,
        )
        val up = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_UP,
            keyCode = KeyEvent.KEYCODE_BACK,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 0,
            lastIndex = 1,
        )

        assertTrue(down.consumed)
        assertEquals(DanmakuVoteKeyCommand.Dismiss, down.command)
        assertEquals(DanmakuVoteKeyCommand.None, repeat.command)
        assertEquals(DanmakuVoteKeyCommand.None, up.command)
    }

    @Test
    fun `unrelated key remains available to upper layers`() {
        val decision = resolveDanmakuVoteKeyDecision(
            action = KeyEvent.ACTION_DOWN,
            keyCode = KeyEvent.KEYCODE_VOLUME_UP,
            repeatCount = 0,
            state = DanmakuVoteKeyState.Showing,
            currentIndex = 0,
            lastIndex = 1,
        )

        assertFalse(decision.consumed)
    }
}
