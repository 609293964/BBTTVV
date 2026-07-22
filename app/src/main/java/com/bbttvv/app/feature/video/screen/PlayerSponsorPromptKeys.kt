package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import com.bbttvv.app.ui.input.isTvBackKey
import com.bbttvv.app.ui.input.isTvConfirmKey
import com.bbttvv.app.ui.input.resolveTvSinglePress

internal fun handleSponsorSkipNoticeKeyEvent(
    event: KeyEvent,
    showSponsorSkipNotice: Boolean,
    onSkipSponsor: () -> Unit,
    onDismissSponsorNotice: () -> Unit,
): Boolean {
    return handleSponsorSkipNoticeKeyEvent(
        action = event.action,
        keyCode = event.keyCode,
        repeatCount = event.repeatCount,
        showSponsorSkipNotice = showSponsorSkipNotice,
        onSkipSponsor = onSkipSponsor,
        onDismissSponsorNotice = onDismissSponsorNotice,
    )
}

internal fun handleSponsorSkipNoticeKeyEvent(
    action: Int,
    keyCode: Int,
    repeatCount: Int = 0,
    showSponsorSkipNotice: Boolean,
    onSkipSponsor: () -> Unit,
    onDismissSponsorNotice: () -> Unit,
): Boolean {
    if (!showSponsorSkipNotice) return false
    val isConfirm = isTvConfirmKey(keyCode)
    val isBack = isTvBackKey(keyCode)
    val decision = resolveTvSinglePress(
        action = action,
        repeatCount = repeatCount,
        isHandledKey = isConfirm || isBack,
    )
    if (decision.shouldTrigger) {
        if (isConfirm) {
            onSkipSponsor()
        } else {
            onDismissSponsorNotice()
        }
    }
    return decision.isConsumed
}
