package com.bbttvv.app.feature.profile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

internal class ProfileFocusCoordinator(
    val menuListState: LazyListState,
    val menuFocusRequesters: Map<ProfileMenu, FocusRequester>,
    private val requestSidebarFocusAction: () -> Unit
) {
    fun requestSidebarFocus(): Boolean {
        requestSidebarFocusAction()
        return true
    }
}

@Composable
internal fun rememberProfileFocusCoordinator(
    selectedMenu: ProfileMenu,
    profileMenus: List<ProfileMenu>
): ProfileFocusCoordinator {
    val menuFocusRequesters = remember { ProfileMenu.values().associateWith { FocusRequester() } }
    val menuListState = rememberLazyListState()
    val sidebarFocusRequestToken = remember { mutableIntStateOf(0) }
    val coordinator = remember(menuListState, menuFocusRequesters) {
        ProfileFocusCoordinator(
            menuListState = menuListState,
            menuFocusRequesters = menuFocusRequesters,
            requestSidebarFocusAction = { sidebarFocusRequestToken.intValue += 1 }
        )
    }

    LaunchedEffect(sidebarFocusRequestToken.intValue, selectedMenu, profileMenus) {
        if (sidebarFocusRequestToken.intValue == 0) return@LaunchedEffect
        val selectedMenuIndex = profileMenus.indexOf(selectedMenu)
        if (selectedMenuIndex >= 0) {
            val isSelectedMenuVisible = menuListState.layoutInfo.visibleItemsInfo
                .any { item -> item.index == selectedMenuIndex }
            if (!isSelectedMenuVisible) {
                menuListState.scrollToItem(selectedMenuIndex)
                withFrameNanos { }
            }
        }
        menuFocusRequesters[selectedMenu]?.requestFocus()
    }

    return coordinator
}
