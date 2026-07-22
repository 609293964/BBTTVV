package com.bbttvv.app.ui.input

import android.view.KeyEvent

internal fun isTvBackKey(
    keyCode: Int,
    includeGamepadBack: Boolean = true,
): Boolean {
    return keyCode == KeyEvent.KEYCODE_BACK ||
        keyCode == KeyEvent.KEYCODE_ESCAPE ||
        (includeGamepadBack && keyCode == KeyEvent.KEYCODE_BUTTON_B)
}

internal fun isTvConfirmKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
}

internal fun isTvDirectionalKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_DPAD_UP ||
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
}

/**
 * Alternate dismiss keys handled by a TV modal itself. Android Back remains owned by BackHandler.
 */
internal fun isTvModalDismissKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_ESCAPE ||
        keyCode == KeyEvent.KEYCODE_MENU ||
        keyCode == KeyEvent.KEYCODE_BUTTON_B
}

internal data class TvSinglePressDecision(
    val isConsumed: Boolean,
    val shouldTrigger: Boolean,
)

/**
 * Consumes both halves of a mapped key press while firing its command once on the first key down.
 */
internal fun resolveTvSinglePress(
    action: Int,
    repeatCount: Int,
    isHandledKey: Boolean,
): TvSinglePressDecision {
    if (!isHandledKey) return TvSinglePressDecision(isConsumed = false, shouldTrigger = false)
    return when (action) {
        KeyEvent.ACTION_DOWN -> TvSinglePressDecision(
            isConsumed = true,
            shouldTrigger = repeatCount == 0,
        )

        KeyEvent.ACTION_UP -> TvSinglePressDecision(isConsumed = true, shouldTrigger = false)
        else -> TvSinglePressDecision(isConsumed = false, shouldTrigger = false)
    }
}
