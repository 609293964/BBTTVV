package com.bbttvv.app.ui.home

import com.bbttvv.app.ui.components.AppTopLevelTab

internal enum class HomeFocusScene {
    InitialEnter,
    TabSwitch,
    BackToTopBar,
    BackToRecommend,
    BackReturn,
}

internal object HomeFocusStrategy {
    fun shouldResetRecommendScroll(
        scene: HomeFocusScene,
        selectedHomeTab: AppTopLevelTab,
        isRestoringBackReturnFocus: Boolean,
        hasContentFocus: Boolean = false,
        hasRememberedGridFocus: Boolean = false,
    ): Boolean {
        if (selectedHomeTab != AppTopLevelTab.RECOMMEND) return false
        if (isRestoringBackReturnFocus) return false
        if (hasContentFocus || hasRememberedGridFocus) return false
        return scene == HomeFocusScene.InitialEnter
    }

    fun shouldRestoreBackReturnVideoFocus(
        scene: HomeFocusScene,
        selectedHomeTab: AppTopLevelTab,
        restoreVideoFocusTab: AppTopLevelTab = AppTopLevelTab.RECOMMEND,
        restoreVideoFocusKey: String?,
        restoreVideoIndex: Int,
    ): Boolean {
        return scene == HomeFocusScene.BackReturn &&
            selectedHomeTab == restoreVideoFocusTab &&
            restoreVideoFocusKey != null &&
            (restoreVideoFocusTab != AppTopLevelTab.RECOMMEND || restoreVideoIndex >= 0)
    }
}
