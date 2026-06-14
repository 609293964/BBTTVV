package com.bbttvv.app.ui.focus

import android.view.View
import android.view.ViewTreeObserver
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
        var registeredViewTreeObserver: ViewTreeObserver? = null

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

        val windowFocusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                drainWhenWindowIsReady()
            }
        }

        fun unregisterWindowFocusListener() {
            registeredViewTreeObserver
                ?.takeIf { observer -> observer.isAlive }
                ?.removeOnWindowFocusChangeListener(windowFocusListener)
            registeredViewTreeObserver = null
        }

        fun registerWindowFocusListener() {
            val observer = hostView.viewTreeObserver
            if (!observer.isAlive || registeredViewTreeObserver === observer) return
            unregisterWindowFocusListener()
            observer.addOnWindowFocusChangeListener(windowFocusListener)
            registeredViewTreeObserver = observer
        }

        val attachStateListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                registerWindowFocusListener()
                drainWhenWindowIsReady()
            }

            override fun onViewDetachedFromWindow(view: View) {
                unregisterWindowFocusListener()
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                registerWindowFocusListener()
                drainWhenWindowIsReady()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        hostView.addOnAttachStateChangeListener(attachStateListener)
        if (hostView.isAttachedToWindow) {
            registerWindowFocusListener()
        }
        drainWhenWindowIsReady()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hostView.removeOnAttachStateChangeListener(attachStateListener)
            unregisterWindowFocusListener()
        }
    }
}
