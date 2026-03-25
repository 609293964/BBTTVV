package com.android.purebilibili.feature.article

internal enum class ArticleSharedElementSlot {
    CARD,
    COVER,
    TITLE
}

internal fun resolveHistoryArticleCoverAspectRatio(): Float = 16f / 10f

internal fun shouldEnableArticleSharedReturn(
    firstVisibleItemIndex: Int
): Boolean = firstVisibleItemIndex == 0

internal fun resolveArticleSharedTransitionKey(
    articleId: Long,
    slot: ArticleSharedElementSlot
): String {
    val normalizedId = articleId.coerceAtLeast(0L)
    return when (slot) {
        ArticleSharedElementSlot.CARD -> "article_card_$normalizedId"
        ArticleSharedElementSlot.COVER -> "article_cover_$normalizedId"
        ArticleSharedElementSlot.TITLE -> "article_title_$normalizedId"
    }
}

internal fun shouldUseArticleNoOpRouteTransition(
    cardTransitionEnabled: Boolean,
    predictiveBackAnimationEnabled: Boolean,
    sharedTransitionReady: Boolean
): Boolean {
    return cardTransitionEnabled &&
        sharedTransitionReady &&
        !predictiveBackAnimationEnabled
}
