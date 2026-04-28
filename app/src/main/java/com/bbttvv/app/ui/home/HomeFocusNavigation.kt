package com.bbttvv.app.ui.home

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

internal fun Modifier.requestTopBarOnDpadUp(
    enabled: Boolean,
    requestTopBarFocus: () -> Unit
): Modifier {
    if (!enabled) return this
    return onPreviewKeyEvent { keyEvent ->
        val event = keyEvent.nativeKeyEvent
        if (
            event.action == AndroidKeyEvent.ACTION_DOWN &&
            event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
        ) {
            requestTopBarFocus()
            true
        } else {
            false
        }
    }
}
