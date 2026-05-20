package com.bbttvv.app.ui.focus

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent

internal class TvFocusAnchorState internal constructor() {
    val focusRequester: FocusRequester = FocusRequester()

    var hasFocus by mutableStateOf(false)
        private set

    fun requestFocus(): Boolean {
        return runCatching { focusRequester.requestFocus() }.getOrDefault(false)
    }

    internal fun updateFocus(hasFocus: Boolean) {
        this.hasFocus = hasFocus
    }
}

@Composable
internal fun rememberTvFocusAnchorState(): TvFocusAnchorState {
    return remember { TvFocusAnchorState() }
}

@Composable
internal fun TvFocusSandboxAnchor(
    state: TvFocusAnchorState,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
    onDpadUp: () -> Boolean = { false },
    onDpadDown: () -> Boolean = { false },
    onDpadLeft: () -> Boolean = { false },
    onDpadRight: () -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) {
    LaunchedEffect(requestInitialFocus, state) {
        if (requestInitialFocus) {
            withFrameNanos { }
            state.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .focusRequester(state.focusRequester)
            .onFocusChanged { state.updateFocus(it.hasFocus) }
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                if (event.action != AndroidKeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }
                when (event.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> onDpadUp()
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> onDpadDown()
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> onDpadLeft()
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> onDpadRight()
                    else -> false
                }
            },
        content = content,
    )
}
