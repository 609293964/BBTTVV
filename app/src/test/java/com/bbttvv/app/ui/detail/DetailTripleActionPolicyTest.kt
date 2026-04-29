package com.bbttvv.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailTripleActionPolicyTest {
    @Test
    fun `triple press starts only after long press confirmation`() {
        assertTrue(shouldStartDetailTriplePress(longPressConfirmed = true))
        assertFalse(shouldStartDetailTriplePress(longPressConfirmed = false))
    }

    @Test
    fun `release cancels unfinished triple press only`() {
        assertTrue(
            shouldCancelDetailTriplePressOnRelease(
                isTriplePressing = true,
                tripleCompleted = false,
            )
        )
        assertFalse(
            shouldCancelDetailTriplePressOnRelease(
                isTriplePressing = true,
                tripleCompleted = true,
            )
        )
        assertFalse(
            shouldCancelDetailTriplePressOnRelease(
                isTriplePressing = false,
                tripleCompleted = false,
            )
        )
    }

    @Test
    fun `successful coin also resolves liked visual state`() {
        val state = resolveDetailTripleActionVisualState(
            currentLiked = false,
            currentCoinCount = 0,
            currentFavoured = true,
            likeSuccess = false,
            coinSuccess = true,
            coinFailureMessage = null,
            favouriteSuccess = false,
        )

        assertTrue(state.isLiked)
        assertEquals(DetailTripleTargetCoinCount, state.coinCount)
        assertTrue(state.isSatisfied)
    }

    @Test
    fun `already coined message resolves full coin count`() {
        val state = resolveDetailTripleActionVisualState(
            currentLiked = true,
            currentCoinCount = 0,
            currentFavoured = true,
            likeSuccess = false,
            coinSuccess = false,
            coinFailureMessage = "已投满2个硬币",
            favouriteSuccess = false,
        )

        assertEquals(DetailTripleTargetCoinCount, state.coinCount)
        assertTrue(state.isSatisfied)
    }

    @Test
    fun `feedback prefers full completion message`() {
        val message = resolveDetailTripleActionFeedbackMessage(
            visualState = DetailTripleActionVisualState(
                isLiked = true,
                coinCount = DetailTripleTargetCoinCount,
                isFavoured = true,
            ),
            likeSuccess = false,
            coinSuccess = false,
            coinFailureMessage = "已投满2个硬币",
            favouriteSuccess = false,
        )

        assertEquals("一键三连成功", message)
    }

    @Test
    fun `triple coin request fills only remaining coins`() {
        assertEquals(2, resolveDetailTripleCoinRequestCount(currentCoinCount = 0))
        assertEquals(1, resolveDetailTripleCoinRequestCount(currentCoinCount = 1))
        assertEquals(0, resolveDetailTripleCoinRequestCount(currentCoinCount = 2))
    }

    @Test
    fun `manual coin request is capped by remaining coins`() {
        assertEquals(2, resolveDetailCoinActionRequestCount(currentCoinCount = 0, requestedCount = 2))
        assertEquals(1, resolveDetailCoinActionRequestCount(currentCoinCount = 1, requestedCount = 2))
        assertEquals(0, resolveDetailCoinActionRequestCount(currentCoinCount = 2, requestedCount = 1))
    }

    @Test
    fun `already coined message updates own count without stat increment`() {
        val nextCoinCount = resolveDetailCoinCountAfterCoinAction(
            currentCoinCount = 1,
            coinsAdded = 0,
            coinFailureMessage = "已投满2个硬币",
        )
        val coinStatIncrement = resolveDetailCoinStatIncrement(
            currentCoinCount = 1,
            coinsAdded = 0,
        )

        assertEquals(DetailTripleTargetCoinCount, nextCoinCount)
        assertEquals(0, coinStatIncrement)
    }
}
