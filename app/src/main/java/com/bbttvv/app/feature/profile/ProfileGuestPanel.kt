package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.bbttvv.app.feature.login.LoginQrPanel
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.HomeFocusTarget
import com.bbttvv.app.ui.home.LocalHomeTabActive
import com.bbttvv.app.ui.focus.RegisterTvFocusReturnTarget

@Composable
internal fun GuestProfileLayout(
    uiState: ProfileUiState,
    onLoginSuccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestTopBarFocus: () -> Boolean,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null
) {
    val loginActionFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    var settingsHasFocus by remember { mutableStateOf(false) }
    val isHomeTabActive = LocalHomeTabActive.current

    RegisterTvFocusReturnTarget(
        key = ProfileSettingsFocusKeys.GuestAction,
        focusRequester = settingsFocusRequester,
    )

    fun noteGuestContentFocused() {
        if (!isHomeTabActive) return
        val tab = focusTab ?: return
        focusCoordinator?.onContentRegionFocused(tab, HomeFocusRegion.ProfileContent)
        focusCoordinator?.onContentRowFocused(0)
    }

    fun requestGuestContentFocus(): Boolean {
        if (!isHomeTabActive) return false
        return runCatching {
            val focused = loginActionFocusRequester.requestFocus()
            if (focused) {
                noteGuestContentFocused()
            }
            focused
        }.getOrDefault(false)
    }

    DisposableEffect(focusCoordinator, focusTab, loginActionFocusRequester, isHomeTabActive) {
        val tab = focusTab
        val registration = if (isHomeTabActive && focusCoordinator != null && tab != null) {
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
                    !settingsHasFocus &&
                    onRequestTopBarFocus()
            },
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        GuestProfileSidebar(storedAccountCount = uiState.storedAccountCount, modifier = Modifier.width(378.dp))
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 22.dp, top = 18.dp, bottom = 24.dp)
                .weight(1f)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                LoginQrPanel(
                    modifier = Modifier.width(760.dp).fillMaxHeight(),
                    onLoginSuccess = onLoginSuccess,
                    refreshFocusRequester = loginActionFocusRequester,
                    onRefreshFocused = ::noteGuestContentFocused
                )
            }
            ProfilePrimaryAction(
                text = "打开设置",
                onClick = onOpenSettings,
                modifier = Modifier
                    .width(760.dp)
                    .focusRequester(settingsFocusRequester)
                    .focusProperties { up = loginActionFocusRequester }
                    .onFocusChanged {
                        settingsHasFocus = it.isFocused
                        if (it.isFocused) noteGuestContentFocused()
                    },
            )
        }
    }
}
