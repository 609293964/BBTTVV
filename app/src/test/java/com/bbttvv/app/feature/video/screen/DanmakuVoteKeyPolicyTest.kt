package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuVoteKeyPolicyTest {
    @Test
    fun `horizontal grade uses left right clamps edges and holds vertical boundaries`() {
        fun move(key: Int, index: Int, action: Int = KeyEvent.ACTION_DOWN) = resolveDanmakuVoteKeyDecision(
            action, key, 0, DanmakuVoteKeyState.Showing, index, 4, horizontal = true,
        )
        assertEquals(1, move(KeyEvent.KEYCODE_DPAD_RIGHT, 0).nextIndex)
        assertEquals(0, move(KeyEvent.KEYCODE_DPAD_LEFT, 1).nextIndex)
        assertEquals(0, move(KeyEvent.KEYCODE_DPAD_LEFT, 0).nextIndex)
        assertEquals(4, move(KeyEvent.KEYCODE_DPAD_RIGHT, 4).nextIndex)
        assertEquals(1, move(KeyEvent.KEYCODE_DPAD_RIGHT, 1, KeyEvent.ACTION_UP).nextIndex)
        listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN).forEach { key ->
            assertTrue(move(key, 2).consumed)
            assertEquals(2, move(key, 2).nextIndex)
        }
        // Existing vertical vote navigation must not change.
        assertEquals(2, resolveDanmakuVoteKeyDecision(
            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, DanmakuVoteKeyState.Showing, 2, 4,
        ).nextIndex)
    }

    @Test
    fun `grade loading result and retry retain star while confirm repeats do not submit`() {
        listOf(DanmakuVoteKeyState.Submitting, DanmakuVoteKeyState.Submitted, DanmakuVoteKeyState.RetryableError).forEach { state ->
            val move = resolveDanmakuVoteKeyDecision(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1, state, 3, 4, horizontal = true,
            )
            assertTrue(move.consumed)
            assertEquals(3, move.nextIndex)
        }
        assertEquals(DanmakuVoteKeyCommand.None, resolveDanmakuVoteKeyDecision(
            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1, DanmakuVoteKeyState.Showing, 3, 4, horizontal = true,
        ).command)
    }
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
