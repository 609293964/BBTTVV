package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusEntryHint
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.HomeFocusRequestResult
import com.bbttvv.app.ui.home.HomeFocusTarget
import com.bbttvv.app.ui.home.LocalHomeTabActive

internal class ProfileContentFocusTargetState {
    val focusRequester = FocusRequester()
    val initialFocusRequester = FocusRequester()
    private val focusRequestToken = mutableIntStateOf(0)
    private var completedRequestToken = 0
    private val hasFocusState = mutableStateOf(false)

    val requestToken: Int
        get() = focusRequestToken.intValue

    fun requestFocusResult(): HomeFocusRequestResult {
        val focused = tryRequestFocusNow()
        if (!focused) {
            focusRequestToken.intValue += 1
        } else {
            completedRequestToken = focusRequestToken.intValue
        }
        return if (focused) {
            HomeFocusRequestResult.Focused
        } else {
            HomeFocusRequestResult.Pending
        }
    }

    fun requestFocus(): Boolean {
        return requestFocusResult().isAccepted
    }

    fun tryRequestFocusNow(): Boolean {
        return requestFocus(initialFocusRequester) || requestFocus(focusRequester)
    }

    fun drainPendingFocus(): Boolean {
        val requestToken = focusRequestToken.intValue
        if (requestToken <= completedRequestToken) return false
        val focused = tryRequestFocusNow()
        if (focused) {
            completedRequestToken = requestToken
        }
        return focused
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

internal val LocalProfileContentFocusScope =
    staticCompositionLocalOf<ProfileContentFocusScope?> { null }

@Composable
internal fun rememberProfileContentFocusTargetState(
    focusCoordinator: HomeFocusCoordinator?,
    focusTab: AppTopLevelTab?,
): ProfileContentFocusTargetState {
    val state = remember { ProfileContentFocusTargetState() }
    val isHomeTabActive = LocalHomeTabActive.current
    val contentScope = LocalProfileContentFocusScope.current

    LaunchedEffect(state.requestToken, isHomeTabActive, contentScope) {
        if (
            isHomeTabActive &&
            contentScope.isEligible() &&
            state.requestToken > 0
        ) {
            state.drainPendingFocus()
        }
    }

    DisposableEffect(focusCoordinator, focusTab, state, isHomeTabActive, contentScope) {
        val tab = focusTab
        val registration = if (isHomeTabActive && focusCoordinator != null && tab != null) {
            focusCoordinator.registerContentTarget(
                tab = tab,
                region = HomeFocusRegion.ProfileContent,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        if (!contentScope.isEligible()) {
                            return false
                        }
                        return state.requestFocus()
                    }

                    override fun requestFocusResult(): HomeFocusRequestResult {
                        if (!contentScope.isEligible()) {
                            return HomeFocusRequestResult.Pending
                        }
                        return state.requestFocusResult()
                    }

                    override fun requestFocusForEntryResult(
                        entryHint: HomeFocusEntryHint,
                    ): HomeFocusRequestResult {
                        return requestFocusResult()
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
    val contentScope = LocalProfileContentFocusScope.current
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
        .onGloballyPositioned {
            if (
                isHomeTabActive &&
                contentScope.isEligible() &&
                state.requestToken > 0
            ) {
                state.drainPendingFocus()
            }
        }
        .focusRequester(state.focusRequester)
        .focusGroup()
}

private fun ProfileContentFocusScope?.isEligible(): Boolean {
    return this == null || isCurrent()
}
