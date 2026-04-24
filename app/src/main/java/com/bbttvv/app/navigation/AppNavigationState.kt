package com.bbttvv.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.bbttvv.app.ui.components.AppTopLevelTab

enum class HomeBackPressResult {
    Consumed,
    ShowExitHint,
    Exit,
}

@Stable
class AppNavigationState internal constructor(
    private val homeTabIndexState: MutableIntState,
    private val homeVideoFocusRestoreKeyState: MutableState<String?>,
    private val homeVideoFocusRestoreTabIndexState: MutableState<Int?>,
    private val homeVideoFocusRestoreReadyState: MutableState<Boolean>,
    private val homeVideoFocusRestoreSawPauseState: MutableState<Boolean>,

    private val detailCommentFocusRestoreRpidState: MutableState<Long?>,
    private val detailCommentFocusRestoreBvidState: MutableState<String?>,
    private val lastBackPressedAtState: MutableLongState,
    private val previousRouteState: MutableState<String?>,
) {
    var homeTabIndex: Int
        get() = homeTabIndexState.intValue
        set(value) {
            homeTabIndexState.intValue = AppTopLevelTab.homeContentFromIndex(value).index
        }

    var homeVideoFocusRestoreKey: String?
        get() = homeVideoFocusRestoreKeyState.value
        private set(value) {
            homeVideoFocusRestoreKeyState.value = value
        }

    private var homeVideoFocusRestoreTabIndex: Int?
        get() = homeVideoFocusRestoreTabIndexState.value
        set(value) {
            homeVideoFocusRestoreTabIndexState.value = value
        }

    private var homeVideoFocusRestoreReady: Boolean
        get() = homeVideoFocusRestoreReadyState.value
        set(value) {
            homeVideoFocusRestoreReadyState.value = value
        }

    private var homeVideoFocusRestoreSawPause: Boolean
        get() = homeVideoFocusRestoreSawPauseState.value
        set(value) {
            homeVideoFocusRestoreSawPauseState.value = value
        }

    private var detailCommentFocusRestoreRpid: Long?
        get() = detailCommentFocusRestoreRpidState.value
        set(value) {
            detailCommentFocusRestoreRpidState.value = value
        }

    private var detailCommentFocusRestoreBvid: String?
        get() = detailCommentFocusRestoreBvidState.value
        set(value) {
            detailCommentFocusRestoreBvidState.value = value
        }

    private var lastBackPressedAt: Long
        get() = lastBackPressedAtState.longValue
        set(value) {
            lastBackPressedAtState.longValue = value
        }

    private var previousRoute: String?
        get() = previousRouteState.value
        set(value) {
            previousRouteState.value = value
        }

    fun safeHomeTab(visibleTabs: List<AppTopLevelTab>): AppTopLevelTab {
        return AppTopLevelTab.resolveVisibleHomeTab(
            index = homeTabIndex,
            visibleTabs = visibleTabs
        )
    }

    fun normalizeHomeTabIfNeeded(visibleTabs: List<AppTopLevelTab>) {
        val safeTab = safeHomeTab(visibleTabs)
        if (safeTab.index != homeTabIndex) {
            homeTabIndex = safeTab.index
        }
    }

    fun isOnHome(currentRoute: String?): Boolean {
        return currentRoute == ScreenRoutes.Home.route
    }

    fun restoreVideoFocusKey(currentRoute: String?): String? {
        return homeVideoFocusRestoreKey.takeIf {
            isOnHome(currentRoute) && homeVideoFocusRestoreReady && it != null
        }
    }

    fun restoreVideoFocusTab(currentRoute: String?): AppTopLevelTab? {
        if (restoreVideoFocusKey(currentRoute) == null) return null
        val tabIndex = homeVideoFocusRestoreTabIndex ?: AppTopLevelTab.RECOMMEND.index
        return AppTopLevelTab.homeContentFromIndex(tabIndex)
    }

    fun hasReadyHomeVideoFocusRestore(currentRoute: String?): Boolean {
        return restoreVideoFocusKey(currentRoute) != null
    }

    fun onRouteChanged(currentRoute: String?) {
        if (!isOnHome(currentRoute)) {
            lastBackPressedAt = 0L
        }
        previousRoute = currentRoute
    }

    fun onHostActivityPaused() {
        if (homeVideoFocusRestoreKey != null && !homeVideoFocusRestoreReady) {
            homeVideoFocusRestoreSawPause = true
        }
    }

    fun onHostActivityResumed() {
        if (homeVideoFocusRestoreKey != null && homeVideoFocusRestoreSawPause) {
            homeVideoFocusRestoreReady = true
            homeVideoFocusRestoreSawPause = false
        }
    }

    fun markHomeVideoFocusRestored(restoredKey: String) {
        if (restoredKey == homeVideoFocusRestoreKey) {
            clearHomeVideoFocusRestore()
        }
    }

    fun switchHomeTab(targetTabIndex: Int) {
        homeTabIndex = targetTabIndex
    }

    fun prepareForDirectDetailOpen() {
        clearHomeVideoFocusRestore()
        clearDetailCommentFocusRestore()
    }

    fun prepareForRecommendDetailOpen(focusKey: String) {
        prepareHomeVideoFocusRestore(
            tab = AppTopLevelTab.RECOMMEND,
            focusKey = focusKey,
            waitForHostActivityReturn = true,
        )
        clearDetailCommentFocusRestore()
    }

    fun prepareForHomeTabDetailOpen(tab: AppTopLevelTab, focusKey: String) {
        prepareHomeVideoFocusRestore(
            tab = tab,
            focusKey = focusKey,
            waitForHostActivityReturn = true,
        )
        clearDetailCommentFocusRestore()
    }

    fun prepareForLivePlayerOpen(focusKey: String) {
        prepareHomeVideoFocusRestore(
            tab = AppTopLevelTab.LIVE,
            focusKey = focusKey,
            waitForHostActivityReturn = false,
        )
        clearDetailCommentFocusRestore()
    }

    private fun prepareHomeVideoFocusRestore(
        tab: AppTopLevelTab,
        focusKey: String,
        waitForHostActivityReturn: Boolean,
    ) {
        homeVideoFocusRestoreKey = focusKey
        homeVideoFocusRestoreTabIndex = tab.index
        homeVideoFocusRestoreReady = !waitForHostActivityReturn
        homeVideoFocusRestoreSawPause = false
    }

    private fun clearHomeVideoFocusRestore() {
        homeVideoFocusRestoreKey = null
        homeVideoFocusRestoreTabIndex = null
        homeVideoFocusRestoreReady = false
        homeVideoFocusRestoreSawPause = false
    }

    fun clearDetailCommentFocusRestore() {
        detailCommentFocusRestoreRpid = null
        detailCommentFocusRestoreBvid = null
    }

    fun setDetailCommentFocusRestore(bvid: String, rpid: Long) {
        detailCommentFocusRestoreBvid = bvid
        detailCommentFocusRestoreRpid = rpid
    }

    fun restoreCommentFocusRpidFor(bvid: String): Long? {
        return detailCommentFocusRestoreRpid.takeIf { detailCommentFocusRestoreBvid == bvid }
    }

    fun markCommentFocusRestored(bvid: String, restoredRpid: Long) {
        if (detailCommentFocusRestoreBvid == bvid && detailCommentFocusRestoreRpid == restoredRpid) {
            clearDetailCommentFocusRestore()
        }
    }

    fun handleHomeBackPressed(
        now: Long,
        visibleTabs: List<AppTopLevelTab>
    ): HomeBackPressResult {
        val safeTab = safeHomeTab(visibleTabs)
        if (safeTab.index != homeTabIndex) {
            homeTabIndex = safeTab.index
        }

        if (safeTab.index != AppTopLevelTab.RECOMMEND.index) {
            homeTabIndex = AppTopLevelTab.RECOMMEND.index
            return HomeBackPressResult.Consumed
        }
        return if (now - lastBackPressedAt <= 2000L) {
            HomeBackPressResult.Exit
        } else {
            lastBackPressedAt = now
            HomeBackPressResult.ShowExitHint
        }
    }
}

@Composable
fun rememberAppNavigationState(): AppNavigationState {
    val homeTabIndexState = remember { androidx.compose.runtime.mutableIntStateOf(AppTopLevelTab.RECOMMEND.index) }
    val homeVideoFocusRestoreKeyState = rememberSaveable { mutableStateOf<String?>(null) }
    val homeVideoFocusRestoreTabIndexState = rememberSaveable { mutableStateOf<Int?>(null) }
    val homeVideoFocusRestoreReadyState = rememberSaveable { mutableStateOf(false) }
    val homeVideoFocusRestoreSawPauseState = rememberSaveable { mutableStateOf(false) }
    val detailCommentFocusRestoreRpidState = rememberSaveable { mutableStateOf<Long?>(null) }
    val detailCommentFocusRestoreBvidState = rememberSaveable { mutableStateOf<String?>(null) }
    val lastBackPressedAtState = remember { mutableLongStateOf(0L) }
    val previousRouteState = remember { mutableStateOf<String?>(null) }
    return remember(
        homeTabIndexState,
        homeVideoFocusRestoreKeyState,
        homeVideoFocusRestoreTabIndexState,
        homeVideoFocusRestoreReadyState,
        homeVideoFocusRestoreSawPauseState,
        detailCommentFocusRestoreRpidState,
        detailCommentFocusRestoreBvidState,
        lastBackPressedAtState,
        previousRouteState,
    ) {
        AppNavigationState(
            homeTabIndexState = homeTabIndexState,
            homeVideoFocusRestoreKeyState = homeVideoFocusRestoreKeyState,
            homeVideoFocusRestoreTabIndexState = homeVideoFocusRestoreTabIndexState,
            homeVideoFocusRestoreReadyState = homeVideoFocusRestoreReadyState,
            homeVideoFocusRestoreSawPauseState = homeVideoFocusRestoreSawPauseState,
            detailCommentFocusRestoreRpidState = detailCommentFocusRestoreRpidState,
            detailCommentFocusRestoreBvidState = detailCommentFocusRestoreBvidState,
            lastBackPressedAtState = lastBackPressedAtState,
            previousRouteState = previousRouteState,
        )
    }
}
