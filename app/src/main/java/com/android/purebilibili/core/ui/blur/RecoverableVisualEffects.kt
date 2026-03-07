package com.android.purebilibili.core.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.purebilibili.core.lifecycle.BackgroundManager
import dev.chrisbanes.haze.HazeState

internal fun shouldEnableRecoverableHeavyVisualEffects(
    userEnabled: Boolean,
    isAppInBackground: Boolean
): Boolean {
    return userEnabled && !isAppInBackground
}

@Composable
fun rememberRecoverableHazeState(
    userEnabled: Boolean = true,
    initialBlurEnabled: Boolean = true
): HazeState {
    val hazeState = remember { HazeState(initialBlurEnabled = initialBlurEnabled) }
    var isAppInBackground by remember { mutableStateOf(BackgroundManager.isInBackground) }

    DisposableEffect(Unit) {
        val listener = object : BackgroundManager.BackgroundStateListener {
            override fun onEnterBackground() {
                isAppInBackground = true
            }

            override fun onEnterForeground() {
                isAppInBackground = false
            }
        }
        BackgroundManager.addListener(listener)
        onDispose {
            BackgroundManager.removeListener(listener)
        }
    }

    SideEffect {
        hazeState.blurEnabled = shouldEnableRecoverableHeavyVisualEffects(
            userEnabled = userEnabled,
            isAppInBackground = isAppInBackground
        )
    }

    return hazeState
}
