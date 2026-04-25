package com.bbttvv.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bbttvv.app.ui.components.AppTopLevelTab

internal enum class HomeFocusRegion {
    TopBar,
    ContentTabs,
    Grid,
    DynamicLiveUsers,
}

internal sealed class HomeFocusIntent {
    data object FocusTopBar : HomeFocusIntent()
    data object FocusSelectedContent : HomeFocusIntent()
    data class FocusRegion(
        val tab: AppTopLevelTab,
        val region: HomeFocusRegion,
    ) : HomeFocusIntent()

    data class RestoreVideoKey(
        val tab: AppTopLevelTab,
        val key: String,
    ) : HomeFocusIntent()
}

internal interface HomeFocusTarget {
    fun tryRequestFocus(): Boolean

    fun tryRequestFocusKey(key: String): Boolean = false

    fun hasFocus(): Boolean = false

    fun hasFocusOnRequestedTarget(): Boolean = hasFocus()

    fun hasRememberedFocus(): Boolean = false

    fun clearFocusVisualState(): Boolean = false
}

internal fun interface HomeFocusTargetRegistration {
    fun unregister()
}

internal class HomeFocusCoordinator(
    initialSelectedHomeTab: AppTopLevelTab = AppTopLevelTab.RECOMMEND,
) {
    var selectedHomeTab by mutableStateOf(initialSelectedHomeTab)
        private set

    var scene by mutableStateOf(HomeFocusScene.InitialEnter)
        private set

    var isTopBarVisible by mutableStateOf(true)
        private set

    var isContentFocused by mutableStateOf(false)
        private set

    private var pendingIntent: HomeFocusIntent? = HomeFocusIntent.FocusTopBar
    private var pendingRestoreCallback: ((String) -> Unit)? = null
    private var topBarTarget: HomeFocusTarget? = null
    private val contentTargets =
        LinkedHashMap<AppTopLevelTab, LinkedHashMap<HomeFocusRegion, HomeFocusTarget>>()

    fun updateSelectedHomeTab(tab: AppTopLevelTab) {
        if (selectedHomeTab != tab) {
            selectedHomeTab = tab
        }
        if (!canCoordinateContent(tab) && pendingIntent == HomeFocusIntent.FocusSelectedContent) {
            pendingIntent = null
        }
        drainPendingFocus()
    }

    fun updateScene(scene: HomeFocusScene) {
        this.scene = scene
    }

    fun prepareForContentFocus(scene: HomeFocusScene = this.scene) {
        this.scene = scene
        isTopBarVisible = false
        isContentFocused = true
    }

    fun onTopBarFocused() {
        clearSelectedContentVisualState()
        isTopBarVisible = true
        isContentFocused = false
        if (pendingIntent == HomeFocusIntent.FocusTopBar) {
            if (topBarTarget?.hasFocusOnRequestedTarget() == true) {
                pendingIntent = null
            } else {
                drainPendingFocus()
            }
        }
    }

    fun onContentFocused() {
        isContentFocused = true
    }

    fun onContentRowFocused(rowIndex: Int) {
        isContentFocused = true
        isTopBarVisible = rowIndex <= 0
    }

    fun requestTopBarFocus(scene: HomeFocusScene = HomeFocusScene.BackToTopBar) {
        this.scene = scene
        clearSelectedContentVisualState()
        isTopBarVisible = true
        isContentFocused = false
        enqueueFocusIntent(HomeFocusIntent.FocusTopBar)
    }

    fun requestSelectedContentFocus() {
        if (!canCoordinateContent(selectedHomeTab)) return
        prepareForContentFocus()
        enqueueFocusIntent(HomeFocusIntent.FocusSelectedContent)
    }

    fun requestRegionFocus(
        tab: AppTopLevelTab,
        region: HomeFocusRegion,
    ) {
        prepareForContentFocus()
        enqueueFocusIntent(HomeFocusIntent.FocusRegion(tab = tab, region = region))
    }

    fun requestRestoreVideoKey(
        tab: AppTopLevelTab,
        key: String,
        onRestored: (String) -> Unit,
    ) {
        prepareForContentFocus(HomeFocusScene.BackReturn)
        pendingRestoreCallback = onRestored
        enqueueFocusIntent(HomeFocusIntent.RestoreVideoKey(tab = tab, key = key))
    }

    fun handleTopBarDpadDown(): Boolean {
        if (!canCoordinateContent(selectedHomeTab)) return false
        requestSelectedContentFocus()
        return true
    }

    fun handleContentWantsTopBar(scene: HomeFocusScene = HomeFocusScene.BackToTopBar): Boolean {
        requestTopBarFocus(scene)
        return true
    }

    fun handleContentTabsDpadUp(
        tab: AppTopLevelTab,
        scene: HomeFocusScene = HomeFocusScene.BackToTopBar,
    ): Boolean {
        return handleContentWantsTopBar(scene)
    }

    fun handleContentTabsDpadDown(tab: AppTopLevelTab): Boolean {
        requestRegionFocus(tab = tab, region = HomeFocusRegion.Grid)
        return true
    }

    fun handleDynamicLiveUsersDpadDown(): Boolean {
        requestRegionFocus(tab = AppTopLevelTab.DYNAMIC, region = HomeFocusRegion.Grid)
        return true
    }

    fun handleGridTopEdge(tab: AppTopLevelTab): Boolean {
        val upperRegion = when (tab) {
            AppTopLevelTab.POPULAR,
            AppTopLevelTab.LIVE,
            AppTopLevelTab.TODAY_WATCH -> HomeFocusRegion.ContentTabs
            AppTopLevelTab.DYNAMIC -> HomeFocusRegion.DynamicLiveUsers
            else -> null
        }
        if (upperRegion != null && tryRequestRegionFocus(tab, upperRegion)) {
            return true
        }
        return handleContentWantsTopBar()
    }

    fun registerTopBarTarget(target: HomeFocusTarget): HomeFocusTargetRegistration {
        topBarTarget = target
        drainPendingFocus()
        return HomeFocusTargetRegistration {
            if (topBarTarget === target) {
                topBarTarget = null
            }
        }
    }

    fun registerContentTarget(
        tab: AppTopLevelTab,
        region: HomeFocusRegion,
        target: HomeFocusTarget,
    ): HomeFocusTargetRegistration {
        val tabTargets = contentTargets.getOrPut(tab) { LinkedHashMap() }
        tabTargets[region] = target
        drainPendingFocus()
        return HomeFocusTargetRegistration {
            val currentTargets = contentTargets[tab] ?: return@HomeFocusTargetRegistration
            if (currentTargets[region] === target) {
                currentTargets.remove(region)
                if (currentTargets.isEmpty()) {
                    contentTargets.remove(tab)
                }
            }
        }
    }

    fun retainVisibleTabs(visibleTabs: Set<AppTopLevelTab>) {
        val iterator = contentTargets.keys.iterator()
        while (iterator.hasNext()) {
            val tab = iterator.next()
            if (tab !in visibleTabs) {
                iterator.remove()
            }
        }
    }

    fun enqueueFocusIntent(intent: HomeFocusIntent) {
        if (intent !is HomeFocusIntent.RestoreVideoKey) {
            pendingRestoreCallback = null
        }
        pendingIntent = intent
        drainPendingFocus()
    }

    fun drainPendingFocus(): Boolean {
        val intent = pendingIntent ?: return false
        val focused = when (intent) {
            HomeFocusIntent.FocusTopBar -> tryRequestTopBarFocus()
            HomeFocusIntent.FocusSelectedContent -> tryRequestSelectedContentFocus(selectedHomeTab)
            is HomeFocusIntent.FocusRegion -> tryRequestRegionFocus(intent.tab, intent.region)
            is HomeFocusIntent.RestoreVideoKey -> tryRestoreVideoKey(intent)
        }
        if (focused) {
            pendingIntent = null
        }
        return focused
    }

    fun clearSelectedContentVisualState(): Boolean {
        val targets = contentTargets[selectedHomeTab] ?: return false
        return targets.values.fold(false) { cleared, target ->
            target.clearFocusVisualState() || cleared
        }
    }

    private fun tryRequestTopBarFocus(): Boolean {
        val target = topBarTarget ?: return false
        if (!target.tryRequestFocus()) return false
        isTopBarVisible = true
        isContentFocused = false
        return true
    }

    private fun tryRequestSelectedContentFocus(tab: AppTopLevelTab): Boolean {
        val regions = contentFocusPriority(tab)
        if (regions.isEmpty()) return false
        return tryRequestFirstTarget(tab, regions)
    }

    private fun tryRequestRegionFocus(
        tab: AppTopLevelTab,
        region: HomeFocusRegion,
    ): Boolean {
        val target = contentTargets[tab]?.get(region) ?: return false
        if (!target.tryRequestFocus()) return false
        markContentFocusRequested()
        return true
    }

    private fun tryRestoreVideoKey(intent: HomeFocusIntent.RestoreVideoKey): Boolean {
        if (intent.tab != selectedHomeTab) return false
        val targets = contentTargets[intent.tab] ?: return false
        val orderedTargets = listOfNotNull(
            targets[HomeFocusRegion.Grid],
            targets[HomeFocusRegion.DynamicLiveUsers],
            targets[HomeFocusRegion.ContentTabs],
        )
        val restored = orderedTargets.any { target ->
            target.tryRequestFocusKey(intent.key)
        }
        if (!restored) return false
        markContentFocusRequested()
        pendingRestoreCallback?.invoke(intent.key)
        pendingRestoreCallback = null
        return true
    }

    private fun tryRequestFirstTarget(
        tab: AppTopLevelTab,
        regions: List<HomeFocusRegion>,
    ): Boolean {
        val targets = contentTargets[tab] ?: return false
        for (region in regions) {
            val target = targets[region] ?: continue
            if (target.tryRequestFocus()) {
                markContentFocusRequested()
                return true
            }
        }
        return false
    }

    private fun markContentFocusRequested() {
        isContentFocused = true
    }

    private fun contentFocusPriority(tab: AppTopLevelTab): List<HomeFocusRegion> {
        val targets = contentTargets[tab] ?: return emptyList()
        return when (tab) {
            AppTopLevelTab.RECOMMEND -> listOf(HomeFocusRegion.Grid)
            AppTopLevelTab.POPULAR,
            AppTopLevelTab.LIVE,
            AppTopLevelTab.TODAY_WATCH -> {
                val gridHasRememberedFocus =
                    targets[HomeFocusRegion.Grid]?.hasRememberedFocus() == true
                if (gridHasRememberedFocus) {
                    listOf(HomeFocusRegion.Grid, HomeFocusRegion.ContentTabs)
                } else {
                    listOf(HomeFocusRegion.ContentTabs, HomeFocusRegion.Grid)
                }
            }
            AppTopLevelTab.DYNAMIC -> listOf(HomeFocusRegion.DynamicLiveUsers, HomeFocusRegion.Grid)
            AppTopLevelTab.WATCH_LATER -> listOf(HomeFocusRegion.Grid)
            else -> emptyList()
        }
    }

    private fun canCoordinateContent(tab: AppTopLevelTab): Boolean {
        return tab == AppTopLevelTab.RECOMMEND ||
            tab == AppTopLevelTab.POPULAR ||
            tab == AppTopLevelTab.LIVE ||
            tab == AppTopLevelTab.DYNAMIC ||
            tab == AppTopLevelTab.WATCH_LATER ||
            tab == AppTopLevelTab.TODAY_WATCH
    }
}
