package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.HomeFocusTarget
import com.bbttvv.app.ui.home.LocalHomeTabActive

internal class ProfileContentFocusTargetState {
    val focusRequester = FocusRequester()
    val initialFocusRequester = FocusRequester()
    private val focusRequestToken = mutableIntStateOf(0)
    private val hasFocusState = mutableStateOf(false)

    val requestToken: Int
        get() = focusRequestToken.intValue

    fun requestFocus(): Boolean {
        val focused = tryRequestFocusNow()
        if (!focused) {
            focusRequestToken.intValue += 1
        }
        return true
    }

    fun tryRequestFocusNow(): Boolean {
        return requestFocus(initialFocusRequester) || requestFocus(focusRequester)
    }

    fun onFocusChanged(hasFocus: Boolean) {
        hasFocusState.value = hasFocus
    }

    fun hasFocus(): Boolean {
        return hasFocusState.value
    }

    private fun requestFocus(requester: FocusRequester): Boolean {
        return runCatching { requester.requestFocus() }.getOrDefault(false)
    }
}

@Composable
internal fun rememberProfileContentFocusTargetState(
    focusCoordinator: HomeFocusCoordinator?,
    focusTab: AppTopLevelTab?,
): ProfileContentFocusTargetState {
    val state = remember { ProfileContentFocusTargetState() }
    val isHomeTabActive = LocalHomeTabActive.current

    LaunchedEffect(state.requestToken, isHomeTabActive) {
        if (isHomeTabActive && state.requestToken > 0) {
            state.tryRequestFocusNow()
        }
    }

    DisposableEffect(focusCoordinator, focusTab, state, isHomeTabActive) {
        val tab = focusTab
        val registration = if (isHomeTabActive && focusCoordinator != null && tab != null) {
            focusCoordinator.registerContentTarget(
                tab = tab,
                region = HomeFocusRegion.ProfileContent,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return state.requestFocus()
                    }

                    override fun hasFocus(): Boolean {
                        return state.hasFocus()
                    }

                    override fun hasRememberedFocus(): Boolean {
                        return true
                    }
                }
            )
        } else {
            null
        }
        onDispose {
            registration?.unregister()
        }
    }

    return state
}

@Composable
internal fun Modifier.profileContentFocusTarget(
    state: ProfileContentFocusTargetState,
    focusCoordinator: HomeFocusCoordinator?,
    focusTab: AppTopLevelTab?,
    onDpadLeft: (() -> Boolean)? = null,
): Modifier {
    val isHomeTabActive = LocalHomeTabActive.current
    val focusModifier = this.onFocusChanged { focusState ->
        val hasFocus = focusState.hasFocus || focusState.isFocused
        state.onFocusChanged(hasFocus)
        if (isHomeTabActive && hasFocus && focusCoordinator != null && focusTab != null) {
            focusCoordinator.onContentRegionFocused(focusTab, HomeFocusRegion.ProfileContent)
            focusCoordinator.onContentRowFocused(0)
        }
    }

    val directionalModifier = if (onDpadLeft == null) {
        focusModifier
    } else {
        focusModifier.onPreviewKeyEvent { keyEvent ->
            val event = keyEvent.nativeKeyEvent
            event.action == AndroidKeyEvent.ACTION_DOWN &&
                event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT &&
                onDpadLeft()
        }
    }

    return directionalModifier
        .focusRequester(state.focusRequester)
        .focusGroup()
}
