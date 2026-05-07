package com.bbttvv.app.ui.detail

internal const val DetailTripleTargetCoinCount = 2

internal data class DetailTripleActionVisualState(
    val isLiked: Boolean,
    val coinCount: Int,
    val isFavoured: Boolean,
) {
    val isSatisfied: Boolean
        get() = isLiked && coinCount >= DetailTripleTargetCoinCount && isFavoured
}

internal fun shouldStartDetailTriplePress(longPressConfirmed: Boolean): Boolean {
    return longPressConfirmed
}

internal fun shouldCancelDetailTriplePressOnRelease(
    isTriplePressing: Boolean,
    tripleCompleted: Boolean,
): Boolean {
    return isTriplePressing && !tripleCompleted
}

internal fun shouldTreatDetailTripleCoinFailureAsAlreadyCoined(coinFailureMessage: String?): Boolean {
    return coinFailureMessage?.contains("已投满2个硬币") == true
}

internal fun resolveDetailTripleCoinRequestCount(currentCoinCount: Int): Int {
    val safeCurrentCoinCount = currentCoinCount.coerceIn(0, DetailTripleTargetCoinCount)
    return (DetailTripleTargetCoinCount - safeCurrentCoinCount).coerceIn(0, DetailTripleTargetCoinCount)
}

internal fun resolveDetailCoinActionRequestCount(
    currentCoinCount: Int,
    requestedCount: Int,
): Int {
    if (requestedCount <= 0) return 0
    val remainingCount = resolveDetailTripleCoinRequestCount(currentCoinCount)
    if (remainingCount <= 0) return 0
    return requestedCount.coerceAtMost(remainingCount).coerceIn(1, DetailTripleTargetCoinCount)
}

internal fun resolveDetailCoinCountAfterCoinAction(
    currentCoinCount: Int,
    coinsAdded: Int,
    coinFailureMessage: String? = null,
): Int {
    return when {
        coinsAdded > 0 -> (currentCoinCount + coinsAdded).coerceIn(0, DetailTripleTargetCoinCount)
        shouldTreatDetailTripleCoinFailureAsAlreadyCoined(coinFailureMessage) -> DetailTripleTargetCoinCount
        else -> currentCoinCount.coerceIn(0, DetailTripleTargetCoinCount)
    }
}

internal fun resolveDetailCoinStatIncrement(
    currentCoinCount: Int,
    coinsAdded: Int,
): Int {
    val remainingCount = resolveDetailTripleCoinRequestCount(currentCoinCount)
    return coinsAdded.coerceAtLeast(0).coerceAtMost(remainingCount)
}

internal fun resolveDetailTripleActionVisualState(
    currentLiked: Boolean,
    currentCoinCount: Int,
    currentFavoured: Boolean,
    likeSuccess: Boolean,
    coinSuccess: Boolean,
    coinFailureMessage: String?,
    favouriteSuccess: Boolean,
): DetailTripleActionVisualState {
    return DetailTripleActionVisualState(
        isLiked = currentLiked || likeSuccess || coinSuccess,
        coinCount = when {
            coinSuccess -> maxOf(currentCoinCount, DetailTripleTargetCoinCount)
            shouldTreatDetailTripleCoinFailureAsAlreadyCoined(coinFailureMessage) -> DetailTripleTargetCoinCount
            else -> currentCoinCount
        },
        isFavoured = currentFavoured || favouriteSuccess,
    )
}

internal fun resolveDetailTripleActionFeedbackMessage(
    visualState: DetailTripleActionVisualState,
    likeSuccess: Boolean,
    coinSuccess: Boolean,
    coinFailureMessage: String?,
    favouriteSuccess: Boolean,
): String {
    if (visualState.isSatisfied) return "一键三连成功"

    val parts = mutableListOf<String>()
    if (likeSuccess) parts += "已点赞"
    if (coinSuccess) {
        parts += "投币成功"
    } else if (shouldTreatDetailTripleCoinFailureAsAlreadyCoined(coinFailureMessage)) {
        parts += "已投满硬币"
    }
    if (favouriteSuccess) parts += "已收藏"

    return parts.takeIf { it.isNotEmpty() }?.joinToString("，")
        ?: coinFailureMessage?.takeIf { it.isNotBlank() }
        ?: "三连失败"
}
