package com.bbttvv.app.feature.profile

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * 个人页焦点协调器
 *
 * 管理 Profile 页侧边栏菜单焦点，每个 ProfileMenu 对应独立的 FocusRequester。
 * 内容区域通过 ProfileContentFocusTargetState 注册为 HomeFocusRegion.ProfileContent 目标。
 */
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

        // 弹性重试与多级安全兜底逻辑，确保滚动和组件挂载完成，绝对防止焦点在屏幕中消失逃逸
        val requester = menuFocusRequesters[selectedMenu]
        if (requester != null) {
            var success = false
            for (retry in 0..5) {
                val result = runCatching { requester.requestFocus() }
                if (result.isSuccess) {
                    success = true
                    break
                }
                // 若未附着（通常由于滚动/渲染延迟抛出 IllegalStateException），则间隔 16ms 进行非阻塞等待重试
                kotlinx.coroutines.delay(16)
            }

            // 如果多次重试后仍然失败（如由于某种刷新导致该项临时失效），启用兜底机制，寻找其他可用菜单项聚焦，保障焦点在侧边栏内
            if (!success) {
                for (menu in profileMenus) {
                    val fallbackRequester = menuFocusRequesters[menu]
                    if (fallbackRequester != null) {
                        val fallbackResult = runCatching { fallbackRequester.requestFocus() }
                        if (fallbackResult.isSuccess) {
                            success = true
                            break
                        }
                    }
                }
            }

            // 最后的物理安全防线，若兜底也失败，强行做最后一次重试尝试
            if (!success) {
                runCatching { requester.requestFocus() }
            }
        }
    }

    return coordinator
}
