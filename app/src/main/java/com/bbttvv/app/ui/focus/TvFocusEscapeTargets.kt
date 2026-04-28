package com.bbttvv.app.ui.focus

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState

internal fun View.isSameOrDescendantOf(ancestor: View): Boolean {
    var current: View? = this
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent as? View
    }
    return false
}

@Composable
internal fun RegisterTvFocusEscapeTarget(
    key: String,
    priority: Int = 0,
    acceptsFocus: (View) -> Boolean,
    shouldRecoverEscapedFocus: (View) -> Boolean = { false },
    recoverFocus: (TvFocusEscapeReason) -> Boolean,
) {
    val guard = LocalTvFocusEscapeGuard.current ?: return
    val latestAcceptsFocus = rememberUpdatedState(acceptsFocus)
    val latestShouldRecoverEscapedFocus = rememberUpdatedState(shouldRecoverEscapedFocus)
    val latestRecoverFocus = rememberUpdatedState(recoverFocus)

    DisposableEffect(guard, key, priority) {
        val registration = guard.registerTarget(
            key = key,
            priority = priority,
            target = object : TvFocusEscapeTarget {
                override fun acceptsFocus(focusedView: View): Boolean {
                    return latestAcceptsFocus.value(focusedView)
                }

                override fun shouldRecoverEscapedFocus(focusedView: View): Boolean {
                    return latestShouldRecoverEscapedFocus.value(focusedView)
                }

                override fun recoverFocus(reason: TvFocusEscapeReason): Boolean {
                    return latestRecoverFocus.value(reason)
                }
            },
        )
        onDispose {
            registration.unregister()
        }
    }
}
