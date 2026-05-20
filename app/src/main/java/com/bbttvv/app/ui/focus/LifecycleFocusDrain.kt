package com.bbttvv.app.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Replays a page-level pending focus intent once the Activity window can accept physical focus again.
 */
@Composable
internal fun RegisterLifecycleFocusDrain(
    key: Any?,
    drainPendingFocus: () -> Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current
    val latestDrainPendingFocus = rememberUpdatedState(drainPendingFocus)

    DisposableEffect(lifecycleOwner, hostView, key) {
        fun drainWhenWindowIsReady() {
            hostView.post {
                if (
                    lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    hostView.isAttachedToWindow &&
                    hostView.hasWindowFocus()
                ) {
                    latestDrainPendingFocus.value()
                }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                drainWhenWindowIsReady()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        drainWhenWindowIsReady()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
