package com.bbttvv.app.core.util

/**
 * 统一平板导航模式判定，避免不同页面断点不一致导致底栏/侧栏显示冲突。
 */
internal fun shouldUseSidebarNavigationForLayout(
    windowSizeClass: WindowSizeClass,
    tabletUseSidebar: Boolean
): Boolean {
    return tabletUseSidebar && windowSizeClass.shouldUseSideNavigation
}

/**
 * 首页侧边抽屉仅在底栏导航模式下启用，避免与平板侧栏模式叠层冲突。
 */
internal fun shouldEnableHomeDrawer(useSideNavigation: Boolean): Boolean {
    return !useSideNavigation
}

