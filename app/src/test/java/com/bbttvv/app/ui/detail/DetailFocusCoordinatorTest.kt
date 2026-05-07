package com.bbttvv.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailFocusCoordinatorTest {
    @Test
    fun `initial play focus callback waits for real focus`() {
        val coordinator = DetailFocusCoordinator()
        val target = FakeDetailFocusTarget(canFocus = false)
        var focused = false

        coordinator.requestInitialPlayFocus {
            focused = true
        }

        assertFalse(focused)
        coordinator.registerPlayButtonTarget(target)
        assertEquals(1, target.focusRequests)
        assertFalse(focused)

        target.canFocus = true

        assertTrue(coordinator.drainPendingFocus())
        assertTrue(focused)
        assertEquals(2, target.focusRequests)
    }

    @Test
    fun `comment restore callback waits for registered target real focus`() {
        val coordinator = DetailFocusCoordinator()
        val target = FakeDetailFocusTarget(canFocus = false)
        var restored: Long? = null

        coordinator.requestRestoreComment(42L) { rpid ->
            restored = rpid
        }

        assertNull(restored)
        coordinator.registerCommentTarget(42L, target)
        assertEquals(1, target.focusRequests)
        assertNull(restored)

        target.canFocus = true

        assertTrue(coordinator.drainPendingFocus())
        assertEquals(42L, restored)
        assertEquals(2, target.focusRequests)
    }

    @Test
    fun `escape recovery falls back to play button`() {
        val coordinator = DetailFocusCoordinator()
        val playTarget = FakeDetailFocusTarget(canFocus = true)

        coordinator.registerPlayButtonTarget(playTarget)

        assertTrue(coordinator.recoverFocusAfterEscape())
        assertEquals(1, playTarget.focusRequests)
    }

    @Test
    fun `escape recovery prefers remembered comment target`() {
        val coordinator = DetailFocusCoordinator()
        val playTarget = FakeDetailFocusTarget(canFocus = true)
        val commentTarget = FakeDetailFocusTarget(canFocus = true)

        coordinator.registerPlayButtonTarget(playTarget)
        coordinator.registerCommentTarget(42L, commentTarget)
        coordinator.rememberCommentFocus(42L)

        assertTrue(coordinator.recoverFocusAfterEscape())
        assertEquals(0, playTarget.focusRequests)
        assertEquals(1, commentTarget.focusRequests)
    }

    @Test
    fun `escape recovery falls back to play button when remembered comment unregisters`() {
        val coordinator = DetailFocusCoordinator()
        val playTarget = FakeDetailFocusTarget(canFocus = true)
        val commentTarget = FakeDetailFocusTarget(canFocus = true)

        coordinator.registerPlayButtonTarget(playTarget)
        val registration = coordinator.registerCommentTarget(42L, commentTarget)
        coordinator.rememberCommentFocus(42L)

        registration.unregister()

        assertTrue(coordinator.recoverFocusAfterEscape())
        assertEquals(1, playTarget.focusRequests)
        assertEquals(0, commentTarget.focusRequests)
    }

    @Test
    fun `escape recovery clears unfocusable remembered comment before falling back`() {
        val coordinator = DetailFocusCoordinator()
        val playTarget = FakeDetailFocusTarget(canFocus = true)
        val commentTarget = FakeDetailFocusTarget(canFocus = false)

        coordinator.registerPlayButtonTarget(playTarget)
        coordinator.registerCommentTarget(42L, commentTarget)
        coordinator.rememberCommentFocus(42L)

        assertTrue(coordinator.recoverFocusAfterEscape())
        assertTrue(coordinator.recoverFocusAfterEscape())
        assertEquals(2, playTarget.focusRequests)
        assertEquals(1, commentTarget.focusRequests)
    }

    private class FakeDetailFocusTarget(
        var canFocus: Boolean,
    ) : DetailFocusTarget {
        var focusRequests: Int = 0
            private set

        override fun tryRequestFocus(): Boolean {
            focusRequests++
            return canFocus
        }
    }
}
