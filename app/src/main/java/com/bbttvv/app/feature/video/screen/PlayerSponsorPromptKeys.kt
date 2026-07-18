package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent

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
    if (!showSponsorSkipNotice || action != KeyEvent.ACTION_DOWN) return false
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            if (repeatCount == 0) onSkipSponsor()
            true
        }

        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE -> {
            onDismissSponsorNotice()
            true
        }

        else -> false
    }
}
