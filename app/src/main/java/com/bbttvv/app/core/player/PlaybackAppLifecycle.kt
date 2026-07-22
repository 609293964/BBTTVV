package com.bbttvv.app.core.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal fun resolvePlayWhenReadyForAppVisibility(
    requestedPlayWhenReady: Boolean,
    isPlaybackSuppressed: Boolean,
): Boolean = requestedPlayWhenReady && !isPlaybackSuppressed

@Composable
internal fun PausePlaybackOnAppBackgroundEffect(
    onEnterBackground: () -> Unit,
    onEnterForeground: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnEnterBackground = rememberUpdatedState(onEnterBackground)
    val latestOnEnterForeground = rememberUpdatedState(onEnterForeground)

    DisposableEffect(lifecycleOwner) {
        var backgroundHandled = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!backgroundHandled) {
                        backgroundHandled = true
                        latestOnEnterBackground.value()
                    }
                }

                Lifecycle.Event.ON_RESUME -> {
                    if (backgroundHandled) {
                        backgroundHandled = false
                        latestOnEnterForeground.value()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
