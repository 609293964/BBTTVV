package com.bbttvv.app.ui.input

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

internal fun Modifier.onTvDpadKeyDown(
    enabled: Boolean = true,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
): Modifier {
    if (!enabled) return this
    return onPreviewKeyEvent { keyEvent ->
        val event = keyEvent.nativeKeyEvent
        if (event.action != AndroidKeyEvent.ACTION_DOWN) {
            return@onPreviewKeyEvent false
        }
        when (event.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> onUp?.invoke() == true
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> onDown?.invoke() == true
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> onLeft?.invoke() == true
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> onRight?.invoke() == true
            else -> false
        }
    }
}
