package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.bbttvv.app.feature.login.LoginQrPanel
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.HomeFocusTarget

@Composable
internal fun GuestProfileLayout(
    uiState: ProfileUiState,
    onLoginSuccess: () -> Unit,
    onRequestTopBarFocus: () -> Boolean,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null
) {
    val loginActionFocusRequester = remember { FocusRequester() }

    fun noteGuestContentFocused() {
        val tab = focusTab ?: return
        focusCoordinator?.onContentRegionFocused(tab, HomeFocusRegion.ProfileContent)
        focusCoordinator?.onContentRowFocused(0)
    }

    fun requestGuestContentFocus(): Boolean {
        return runCatching {
            val focused = loginActionFocusRequester.requestFocus()
            if (focused) {
                noteGuestContentFocused()
            }
            focused
        }.getOrDefault(false)
    }

    DisposableEffect(focusCoordinator, focusTab, loginActionFocusRequester) {
        val tab = focusTab
        val registration = if (focusCoordinator != null && tab != null) {
            focusCoordinator.registerContentTarget(
                tab = tab,
                region = HomeFocusRegion.ProfileContent,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return requestGuestContentFocus()
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                event.action == AndroidKeyEvent.ACTION_DOWN &&
                    event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP &&
                    onRequestTopBarFocus()
            },
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        GuestProfileSidebar(storedAccountCount = uiState.storedAccountCount, modifier = Modifier.width(378.dp))
        Box(
            modifier = Modifier.fillMaxHeight().padding(start = 22.dp, top = 18.dp).weight(1f)
        ) {
            LoginQrPanel(
                modifier = Modifier.width(760.dp).fillMaxHeight(),
                onLoginSuccess = onLoginSuccess,
                refreshFocusRequester = loginActionFocusRequester,
                onRefreshFocused = ::noteGuestContentFocused
            )
        }
    }
}
