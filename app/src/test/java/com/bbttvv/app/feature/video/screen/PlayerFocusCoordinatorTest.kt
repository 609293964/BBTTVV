package com.bbttvv.app.feature.video.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFocusCoordinatorTest {
    @Test
    fun `overlay focus intent stays pending until target really focuses`() {
        val coordinator = PlayerFocusCoordinator()
        val target = FakePlayerFocusTarget(canFocus = false)

        coordinator.requestFocus(PlayerFocusIntent.FocusProgress)
        assertFalse(coordinator.drainPendingFocus())

        coordinator.registerProgressTarget(target)
        assertEquals(1, target.focusRequests)

        target.canFocus = true

        assertTrue(coordinator.drainPendingFocus())
        assertEquals(2, target.focusRequests)
        assertFalse(coordinator.drainPendingFocus())
    }

    @Test
    fun `indexed action focus uses selected target only`() {
        val coordinator = PlayerFocusCoordinator()
        val firstTarget = FakePlayerFocusTarget(canFocus = true)
        val secondTarget = FakePlayerFocusTarget(canFocus = true)

        coordinator.registerActionTarget(0, firstTarget)
        coordinator.registerActionTarget(1, secondTarget)

        coordinator.requestFocus(PlayerFocusIntent.FocusAction(1))

        assertEquals(0, firstTarget.focusRequests)
        assertEquals(1, secondTarget.focusRequests)
    }

    @Test
    fun `comment focus key remains pending until key target succeeds`() {
        val coordinator = PlayerCommentFocusCoordinator()
        val target = FakePlayerFocusTarget(canFocus = false)

        coordinator.requestFocusKey("comment:1")
        coordinator.registerCommentTarget("comment:1", target)

        assertEquals(1, target.focusRequests)

        target.canFocus = true

        assertTrue(coordinator.drainPendingFocus())
        assertEquals(2, target.focusRequests)
    }

    private class FakePlayerFocusTarget(
        var canFocus: Boolean,
    ) : PlayerFocusTarget {
        var focusRequests: Int = 0
            private set

        override fun tryRequestFocus(): Boolean {
            focusRequests++
            return canFocus
        }
    }
}
