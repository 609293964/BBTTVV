package com.android.purebilibili.feature.article

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleSharedTransitionPolicyTest {

    @Test
    fun `history article cover uses wide card aspect ratio`() {
        assertEquals(16f / 10f, resolveHistoryArticleCoverAspectRatio())
    }

    @Test
    fun `article shared return only stays enabled when header banner is visible`() {
        assertTrue(shouldEnableArticleSharedReturn(firstVisibleItemIndex = 0))
        assertFalse(shouldEnableArticleSharedReturn(firstVisibleItemIndex = 1))
        assertFalse(shouldEnableArticleSharedReturn(firstVisibleItemIndex = 4))
    }

    @Test
    fun `article shared transition keys stay stable per slot`() {
        assertEquals(
            "article_card_6233590",
            resolveArticleSharedTransitionKey(6233590L, ArticleSharedElementSlot.CARD)
        )
        assertEquals(
            "article_cover_6233590",
            resolveArticleSharedTransitionKey(6233590L, ArticleSharedElementSlot.COVER)
        )
        assertEquals(
            "article_title_6233590",
            resolveArticleSharedTransitionKey(6233590L, ArticleSharedElementSlot.TITLE)
        )
    }

    @Test
    fun `article route uses no-op transition only when shared animation is ready`() {
        assertTrue(
            shouldUseArticleNoOpRouteTransition(
                cardTransitionEnabled = true,
                predictiveBackAnimationEnabled = false,
                sharedTransitionReady = true
            )
        )
        assertFalse(
            shouldUseArticleNoOpRouteTransition(
                cardTransitionEnabled = true,
                predictiveBackAnimationEnabled = true,
                sharedTransitionReady = true
            )
        )
        assertFalse(
            shouldUseArticleNoOpRouteTransition(
                cardTransitionEnabled = true,
                predictiveBackAnimationEnabled = false,
                sharedTransitionReady = false
            )
        )
    }
}
