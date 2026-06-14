package com.bbttvv.app.feature.profile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import com.bbttvv.app.ui.home.HomeFocusRequestResult
import kotlin.math.abs

internal fun interface ProfileMenuFocusRegistration {
    fun unregister()
}

internal data class ProfileContentFocusScope(
    val coordinator: ProfileFocusCoordinator,
    val menu: ProfileMenu,
    val generation: Int,
) {
    fun isCurrent(): Boolean = coordinator.isCurrentContentTarget(menu, generation)
}

private data class PendingProfileMenuFocus(
    val menu: ProfileMenu,
    val generation: Int,
)

/**
 * Coordinates sidebar and content focus with generation-scoped pending requests.
 */
internal class ProfileFocusCoordinator(
    val menuListState: LazyListState,
    val menuFocusRequesters: Map<ProfileMenu, FocusRequester>,
) {
    private val mountedMenuTargets = mutableMapOf<ProfileMenu, () -> Boolean>()
    private var profileMenus: List<ProfileMenu> = emptyList()
    private var selectedMenu: ProfileMenu = ProfileMenu.HISTORY
    private var sidebarGeneration = 0
    private var pendingSidebarFocus by mutableStateOf<PendingProfileMenuFocus?>(null)

    var contentGeneration: Int by mutableIntStateOf(0)
        private set

    private var contentMenu: ProfileMenu by mutableStateOf(ProfileMenu.HISTORY)

    val pendingMenu: ProfileMenu?
        get() = pendingSidebarFocus?.menu

    val pendingGeneration: Int
        get() = pendingSidebarFocus?.generation ?: 0

    fun updateSelection(menu: ProfileMenu, menus: List<ProfileMenu>) {
        val previousMenus = profileMenus
        profileMenus = menus
        val legalMenu = ProfileMenuFocusPolicy.resolve(menu, previousMenus, menus)
        selectedMenu = legalMenu
        prepareContentMenu(legalMenu)

        val pending = pendingSidebarFocus
        if (pending != null && pending.menu !in menus) {
            queueSidebarFocus(legalMenu)
        }
    }

    fun prepareContentMenu(menu: ProfileMenu) {
        if (contentMenu == menu) return
        contentMenu = menu
        contentGeneration += 1
    }

    fun isCurrentContentTarget(menu: ProfileMenu, generation: Int): Boolean {
        return contentMenu == menu && contentGeneration == generation
    }

    fun requestSidebarFocus(): Boolean = requestSidebarFocusResult().isAccepted

    fun requestSidebarFocusResult(): HomeFocusRequestResult {
        val targetMenu = ProfileMenuFocusPolicy.resolve(
            selectedMenu = selectedMenu,
            previousMenus = profileMenus,
            currentMenus = profileMenus,
        )
        if (pendingSidebarFocus?.menu != targetMenu) {
            queueSidebarFocus(targetMenu)
        }
        return drainPendingSidebarFocus()
    }

    fun registerMenuTarget(
        menu: ProfileMenu,
        requestFocus: () -> Boolean,
    ): ProfileMenuFocusRegistration {
        mountedMenuTargets[menu] = requestFocus
        drainPendingSidebarFocus()
        return ProfileMenuFocusRegistration {
            if (mountedMenuTargets[menu] === requestFocus) {
                mountedMenuTargets.remove(menu)
            }
        }
    }

    fun onMenuTargetLaidOut(menu: ProfileMenu) {
        if (pendingSidebarFocus?.menu == menu) {
            drainPendingSidebarFocus()
        }
    }

    fun drainPendingSidebarFocus(expectedGeneration: Int? = null): HomeFocusRequestResult {
        val pending = pendingSidebarFocus ?: return HomeFocusRequestResult.Unavailable
        if (expectedGeneration != null && pending.generation != expectedGeneration) {
            return HomeFocusRequestResult.Pending
        }
        val requestFocus = mountedMenuTargets[pending.menu]
            ?: return HomeFocusRequestResult.Pending
        return if (requestFocus()) {
            pendingSidebarFocus = null
            HomeFocusRequestResult.Focused
        } else {
            HomeFocusRequestResult.Pending
        }
    }

    private fun queueSidebarFocus(menu: ProfileMenu) {
        sidebarGeneration += 1
        pendingSidebarFocus = PendingProfileMenuFocus(
            menu = menu,
            generation = sidebarGeneration,
        )
    }
}

internal object ProfileMenuFocusPolicy {
    fun resolve(
        selectedMenu: ProfileMenu,
        previousMenus: List<ProfileMenu>,
        currentMenus: List<ProfileMenu>,
    ): ProfileMenu {
        if (selectedMenu in currentMenus) return selectedMenu
        if (currentMenus.isEmpty()) return ProfileMenu.HISTORY
        val oldIndex = previousMenus.indexOf(selectedMenu)
        if (oldIndex < 0) return currentMenus.first()
        return currentMenus.minByOrNull { menu ->
            val candidateIndex = previousMenus.indexOf(menu)
            if (candidateIndex < 0) Int.MAX_VALUE else abs(candidateIndex - oldIndex)
        } ?: currentMenus.first()
    }
}

@Composable
internal fun rememberProfileFocusCoordinator(
    selectedMenu: ProfileMenu,
    profileMenus: List<ProfileMenu>,
): ProfileFocusCoordinator {
    val menuFocusRequesters = remember { ProfileMenu.values().associateWith { FocusRequester() } }
    val menuListState = rememberLazyListState()
    val coordinator = remember(menuListState, menuFocusRequesters) {
        ProfileFocusCoordinator(
            menuListState = menuListState,
            menuFocusRequesters = menuFocusRequesters,
        )
    }

    SideEffect {
        coordinator.updateSelection(selectedMenu, profileMenus)
    }

    val pendingMenu = coordinator.pendingMenu
    val pendingGeneration = coordinator.pendingGeneration
    LaunchedEffect(pendingMenu, pendingGeneration, profileMenus) {
        val menu = pendingMenu ?: return@LaunchedEffect
        val menuIndex = profileMenus.indexOf(menu)
        if (menuIndex < 0) return@LaunchedEffect
        val isVisible = menuListState.layoutInfo.visibleItemsInfo.any { it.index == menuIndex }
        if (!isVisible) {
            menuListState.scrollToItem(menuIndex)
        }
        coordinator.drainPendingSidebarFocus(pendingGeneration)
    }

    return coordinator
}
