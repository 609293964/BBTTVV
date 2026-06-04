package com.bbttvv.app.ui.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bbttvv.app.BuildConfig
import com.bbttvv.app.ui.components.AppTopLevelTab

/**
 * 首页焦点区域枚举
 *
 * 定义首页所有可聚焦区域，由 [HomeFocusCoordinator] 统一调度。
 * 新增首页区域时必须注册为对应的 HomeFocusRegion，不要在页面内部直接抢焦点。
 */
internal enum class HomeFocusRegion {
    TopBar,
    ContentTabs,
    Grid,
    DynamicLiveUsers,
    DynamicFollowUpdates,
    SearchInput,
    SearchCategory,
    ProfileSidebar,
    ProfileContent,
}

/**
 * 首页焦点意图密封类
 *
 * 表达首页各区域间的焦点切换意图，由 [HomeFocusCoordinator] 消费。
 * - [FocusTopBar]: 聚焦顶部导航栏
 * - [FocusTopBarTab]: 聚焦顶部导航栏指定 Tab
 * - [FocusSelectedContent]: 聚焦当前选中 Tab 的内容区域
 * - [FocusRegion]: 聚焦指定 Tab 的指定区域
 * - [RestoreVideoKey]: 恢复到指定视频 key 的焦点（用于从详情页/播放页返回）
 */
internal sealed class HomeFocusIntent {
    data object FocusTopBar : HomeFocusIntent()
    data class FocusTopBarTab(
        val tab: AppTopLevelTab,
    ) : HomeFocusIntent()

    data object FocusSelectedContent : HomeFocusIntent()
    data class FocusRegion(
        val tab: AppTopLevelTab,
        val region: HomeFocusRegion,
        val entryHint: HomeFocusEntryHint = HomeFocusEntryHint(),
    ) : HomeFocusIntent()

    data class RestoreVideoKey(
        val tab: AppTopLevelTab,
        val key: String,
    ) : HomeFocusIntent()
}

internal data class HomeFocusEntryHint(
    val preferredIndex: Int? = null,
)

internal enum class HomeFocusRequestResult {
    Focused,
    Pending,
    Unavailable;

    val isAccepted: Boolean
        get() = this != Unavailable

    val isFocused: Boolean
        get() = this == Focused

    companion object {
        fun fromFocused(focused: Boolean): HomeFocusRequestResult {
            return if (focused) Focused else Unavailable
        }
    }
}

internal enum class HomeBackReturnRestoreResult {
    ExactFocused,
    PendingShort,
    FallbackFocused,
    Unavailable;

    val isAccepted: Boolean
        get() = this != Unavailable
}

internal sealed class HomeFocusMode {
    data object TopBar : HomeFocusMode()

    data class Content(
        val tab: AppTopLevelTab,
        val region: HomeFocusRegion?,
        val scene: HomeFocusScene,
        val keepTopBarVisible: Boolean,
    ) : HomeFocusMode()
}

internal interface HomeFocusTarget {
    fun tryRequestFocus(): Boolean

    fun tryRequestFocusTab(tab: AppTopLevelTab): Boolean = tryRequestFocus()

    fun tryRequestFocusKey(key: String): Boolean = false

    fun tryRequestFocusKeyOrFallback(key: String): Boolean = tryRequestFocusKey(key)

    fun tryRequestFocusForEntry(entryHint: HomeFocusEntryHint): Boolean = tryRequestFocus()

    fun requestFocusResult(): HomeFocusRequestResult =
        HomeFocusRequestResult.fromFocused(tryRequestFocus())

    fun requestFocusTabResult(tab: AppTopLevelTab): HomeFocusRequestResult =
        HomeFocusRequestResult.fromFocused(tryRequestFocusTab(tab))

    fun requestFocusKeyResult(key: String): HomeFocusRequestResult =
        HomeFocusRequestResult.fromFocused(tryRequestFocusKey(key))

    fun requestFocusKeyOrFallbackResult(key: String): HomeFocusRequestResult =
        HomeFocusRequestResult.fromFocused(tryRequestFocusKeyOrFallback(key))

    fun requestBackReturnFocusKeyResult(key: String): HomeBackReturnRestoreResult {
        return when (requestFocusKeyOrFallbackResult(key)) {
            HomeFocusRequestResult.Focused -> HomeBackReturnRestoreResult.ExactFocused
            HomeFocusRequestResult.Pending -> HomeBackReturnRestoreResult.PendingShort
            HomeFocusRequestResult.Unavailable -> HomeBackReturnRestoreResult.Unavailable
        }
    }

    fun requestFocusForEntryResult(entryHint: HomeFocusEntryHint): HomeFocusRequestResult =
        HomeFocusRequestResult.fromFocused(tryRequestFocusForEntry(entryHint))

    fun hasFocus(): Boolean = false

    fun hasFocusOnRequestedTarget(): Boolean = hasFocus()

    fun hasFocusOnTab(tab: AppTopLevelTab): Boolean = hasFocusOnRequestedTarget()

    fun hasRememberedFocus(): Boolean = false

    fun clearFocusVisualState(): Boolean = false
}

internal fun interface HomeFocusTargetRegistration {
    fun unregister()
}

private data class HomeFocusRule(
    val entryPriority: List<HomeFocusRegion>,
    val gridRememberedEntryPriority: List<HomeFocusRegion> = entryPriority,
    val topEdgePriority: List<HomeFocusRegion> = emptyList(),
    val restorePriority: List<HomeFocusRegion> = HomeFocusRules.DefaultRestorePriority,
    val rememberLastRegionOnEntry: Boolean = true,
    val keepTopBarVisibleWhileEnteringContent: Boolean = false,
) {
    fun entryPriorityFor(targets: Map<HomeFocusRegion, HomeFocusTarget>): List<HomeFocusRegion> {
        return if (targets[HomeFocusRegion.Grid]?.hasRememberedFocus() == true) {
            gridRememberedEntryPriority
        } else {
            entryPriority
        }
    }
}

private object HomeFocusRules {
    val DefaultRestorePriority = listOf(
        HomeFocusRegion.Grid,
        HomeFocusRegion.ProfileContent,
        HomeFocusRegion.DynamicFollowUpdates,
        HomeFocusRegion.DynamicLiveUsers,
        HomeFocusRegion.ContentTabs,
    )

    private val contentTabsThenGrid = listOf(HomeFocusRegion.ContentTabs, HomeFocusRegion.Grid)
    private val gridThenContentTabs = listOf(HomeFocusRegion.Grid, HomeFocusRegion.ContentTabs)

    private val rules = mapOf(
        AppTopLevelTab.RECOMMEND to HomeFocusRule(
            entryPriority = listOf(HomeFocusRegion.Grid),
        ),
        AppTopLevelTab.SEARCH to HomeFocusRule(
            entryPriority = listOf(
                HomeFocusRegion.SearchInput,
                HomeFocusRegion.SearchCategory,
                HomeFocusRegion.Grid,
            ),
        ),
        AppTopLevelTab.TODAY_WATCH to contentTabsRule(),
        AppTopLevelTab.POPULAR to contentTabsRule(),
        AppTopLevelTab.LIVE to contentTabsRule(),
        AppTopLevelTab.DYNAMIC to HomeFocusRule(
            entryPriority = listOf(
                HomeFocusRegion.DynamicLiveUsers,
                HomeFocusRegion.DynamicFollowUpdates,
                HomeFocusRegion.Grid,
            ),
            topEdgePriority = listOf(
                HomeFocusRegion.DynamicFollowUpdates,
                HomeFocusRegion.DynamicLiveUsers,
            ),
            rememberLastRegionOnEntry = false,
        ),
        AppTopLevelTab.WATCH_LATER to HomeFocusRule(
            entryPriority = listOf(HomeFocusRegion.Grid),
        ),
        AppTopLevelTab.PROFILE to HomeFocusRule(
            entryPriority = listOf(
                HomeFocusRegion.ProfileSidebar,
                HomeFocusRegion.ProfileContent,
            ),
            keepTopBarVisibleWhileEnteringContent = true,
        ),
    )

    fun ruleFor(tab: AppTopLevelTab): HomeFocusRule? = rules[tab]

    fun canCoordinate(tab: AppTopLevelTab): Boolean = rules.containsKey(tab)

    private fun contentTabsRule(): HomeFocusRule {
        return HomeFocusRule(
            entryPriority = contentTabsThenGrid,
            gridRememberedEntryPriority = gridThenContentTabs,
            topEdgePriority = listOf(HomeFocusRegion.ContentTabs),
        )
    }
}

/**
 * 首页焦点协调器
 *
 * 统一调度首页所有区域的焦点切换，包括 TopBar、内容 Tab、Grid、动态用户栏、搜索等。
 * 通过 [HomeFocusIntent] 表达焦点意图，通过 [HomeFocusTarget] 注册焦点目标。
 * 每个 Tab 有独立的 [HomeFocusRule]，定义进入优先级、顶部边界行为和恢复优先级。
 * 记住每个 Tab 最后聚焦的 [HomeFocusRegion]，下次进入时优先恢复。
 *
 * 三层焦点架构中的页面层组件，与全局层的 TvFocusEscapeGuard/TvFocusReturn
 * 和组件层的 HomeRecommendGridFocusState/DpadGridController 协同工作。
 */
internal class HomeFocusCoordinator(
    initialSelectedHomeTab: AppTopLevelTab = AppTopLevelTab.RECOMMEND,
) {
    var selectedHomeTab by mutableStateOf(initialSelectedHomeTab)
        private set

    var scene by mutableStateOf(HomeFocusScene.InitialEnter)
        private set

    private var focusMode by mutableStateOf<HomeFocusMode>(HomeFocusMode.TopBar)

    val isTopBarVisible: Boolean
        get() = when (val mode = focusMode) {
            HomeFocusMode.TopBar -> true
            is HomeFocusMode.Content -> mode.keepTopBarVisible || contentTopBarVisible
        }

    val isTopBarFocusable: Boolean
        get() = focusMode is HomeFocusMode.TopBar

    val isContentFocused: Boolean
        get() = focusMode is HomeFocusMode.Content

    val currentFocusMode: HomeFocusMode
        get() = focusMode

    val hasPendingContentFocus: Boolean
        get() = when (pendingIntent) {
            HomeFocusIntent.FocusSelectedContent,
            is HomeFocusIntent.FocusRegion,
            is HomeFocusIntent.RestoreVideoKey -> true
            else -> false
        }

    private var pendingIntent: HomeFocusIntent? = HomeFocusIntent.FocusTopBar
    private var pendingRestoreCallback: ((String) -> Unit)? = null
    private var pendingRestoreCancelCallback: (() -> Unit)? = null
    private var pendingBackToTopBarReset: Boolean = false
    private var contentTopBarVisible by mutableStateOf(false)
    private var topBarTarget: HomeFocusTarget? = null
    private val lastContentRegionByTab = mutableMapOf<AppTopLevelTab, HomeFocusRegion>()
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
        if (scene != HomeFocusScene.BackToTopBar) {
            pendingBackToTopBarReset = false
        }
    }

    fun prepareForContentFocus(
        scene: HomeFocusScene = this.scene,
        keepTopBarVisible: Boolean = false,
    ) {
        this.scene = scene
        if (scene != HomeFocusScene.BackToTopBar) {
            pendingBackToTopBarReset = false
        }
        enterContentMode(
            tab = selectedHomeTab,
            region = lastContentRegionByTab[selectedHomeTab],
            scene = scene,
            keepTopBarVisible = keepTopBarVisible,
        )
        contentTopBarVisible = keepTopBarVisible
    }

    fun onTopBarFocused() {
        clearSelectedContentVisualState()
        logHomeFocus { "onTopBarFocused: mode $focusMode -> TopBar" }
        contentTopBarVisible = false
        focusMode = HomeFocusMode.TopBar
        when (val intent = pendingIntent) {
            HomeFocusIntent.FocusTopBar -> {
                if (topBarTarget?.hasFocusOnRequestedTarget() == true) {
                    pendingIntent = null
                } else {
                    drainPendingFocus()
                }
            }
            is HomeFocusIntent.FocusTopBarTab -> {
                if (topBarTarget?.hasFocusOnTab(intent.tab) == true) {
                    pendingIntent = null
                } else {
                    drainPendingFocus()
                }
            }
            else -> Unit
        }
    }

    fun onContentFocused() {
        enterContentMode(
            tab = selectedHomeTab,
            region = lastContentRegionByTab[selectedHomeTab],
            scene = scene,
            keepTopBarVisible = shouldKeepTopBarVisibleWhileEnteringContent(selectedHomeTab),
        )
    }

    fun onContentRowFocused(rowIndex: Int) {
        logHomeFocus { "onContentRowFocused: row=$rowIndex mode=$focusMode" }
        onContentFocused()
        contentTopBarVisible = rowIndex <= 0
    }

    fun onContentRegionFocused(
        tab: AppTopLevelTab,
        region: HomeFocusRegion,
    ) {
        if (tab != selectedHomeTab) return
        lastContentRegionByTab[tab] = region
        enterContentMode(
            tab = tab,
            region = region,
            scene = scene,
            keepTopBarVisible = shouldKeepTopBarVisibleWhileEnteringContent(tab),
        )
    }

    fun requestTopBarFocus(scene: HomeFocusScene = HomeFocusScene.BackToTopBar) {
        this.scene = scene
        pendingBackToTopBarReset = scene == HomeFocusScene.BackToTopBar
        clearSelectedContentVisualState()
        contentTopBarVisible = false
        focusMode = HomeFocusMode.TopBar
        enqueueFocusIntent(HomeFocusIntent.FocusTopBar)
    }

    fun requestTopBarTabFocusAfterSwitch(
        tab: AppTopLevelTab,
        scene: HomeFocusScene = HomeFocusScene.TabSwitch,
    ) {
        this.scene = scene
        pendingBackToTopBarReset = false
        clearSelectedContentVisualState()
        contentTopBarVisible = false
        focusMode = HomeFocusMode.TopBar
        pendingIntent = HomeFocusIntent.FocusTopBarTab(tab)
    }

    fun consumeBackToTopBarResetIntent(): Boolean {
        if (scene != HomeFocusScene.BackToTopBar || !pendingBackToTopBarReset) {
            return false
        }
        pendingBackToTopBarReset = false
        scene = HomeFocusScene.TopBarFocused
        return true
    }

    fun requestSelectedContentFocus() {
        if (!canCoordinateContent(selectedHomeTab)) return
        enqueueFocusIntent(HomeFocusIntent.FocusSelectedContent)
    }

    fun requestRegionFocus(
        tab: AppTopLevelTab,
        region: HomeFocusRegion,
        entryHint: HomeFocusEntryHint = HomeFocusEntryHint(),
    ) {
        prepareForContentFocus(
            keepTopBarVisible = shouldKeepTopBarVisibleWhileEnteringContent(tab)
        )
        enqueueFocusIntent(
            HomeFocusIntent.FocusRegion(
                tab = tab,
                region = region,
                entryHint = entryHint,
            )
        )
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

    fun setPendingRestoreCancelCallback(callback: (() -> Unit)?) {
        pendingRestoreCancelCallback = callback
    }

    fun cancelPendingRestoreVideoKeyForUserNavigation(tab: AppTopLevelTab?): Boolean {
        val intent = pendingIntent as? HomeFocusIntent.RestoreVideoKey ?: return false
        if (tab != null && intent.tab != tab) return false
        logHomeFocus { "cancelPendingRestoreVideoKeyForUserNavigation: tab=$tab key=${intent.key}" }
        pendingIntent = null
        pendingRestoreCallback = null
        pendingRestoreCancelCallback?.invoke()
        return true
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

    fun handleContentTabsDpadDown(
        tab: AppTopLevelTab,
        preferredIndex: Int? = null,
    ): Boolean {
        requestRegionFocus(
            tab = tab,
            region = HomeFocusRegion.Grid,
            entryHint = HomeFocusEntryHint(preferredIndex = preferredIndex),
        )
        return true
    }

    fun handleDynamicLiveUsersDpadDown(preferredIndex: Int? = null): Boolean {
        val entryHint = HomeFocusEntryHint(preferredIndex = preferredIndex)
        if (
            tryRequestRegionFocus(
                tab = AppTopLevelTab.DYNAMIC,
                region = HomeFocusRegion.DynamicFollowUpdates,
                entryHint = entryHint,
            ).isAccepted
        ) {
            return true
        }
        requestRegionFocus(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.Grid,
            entryHint = entryHint,
        )
        return true
    }

    fun handleDynamicFollowUpdatesDpadUp(): Boolean {
        if (tryRequestRegionFocus(AppTopLevelTab.DYNAMIC, HomeFocusRegion.DynamicLiveUsers).isAccepted) {
            return true
        }
        return handleContentWantsTopBar()
    }

    fun handleDynamicFollowUpdatesDpadDown(preferredIndex: Int? = null): Boolean {
        requestRegionFocus(
            tab = AppTopLevelTab.DYNAMIC,
            region = HomeFocusRegion.Grid,
            entryHint = HomeFocusEntryHint(preferredIndex = preferredIndex),
        )
        return true
    }

    fun handleGridTopEdge(tab: AppTopLevelTab): Boolean {
        val rule = HomeFocusRules.ruleFor(tab)
        rule?.topEdgePriority.orEmpty().forEach { region ->
            if (tryRequestRegionFocus(tab, region).isAccepted) {
                return true
            }
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
        lastContentRegionByTab.keys.removeAll { tab -> tab !in visibleTabs }
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
        val result = when (intent) {
            HomeFocusIntent.FocusTopBar -> tryRequestTopBarFocus()
            is HomeFocusIntent.FocusTopBarTab -> tryRequestTopBarFocus(intent.tab)
            HomeFocusIntent.FocusSelectedContent -> tryRequestSelectedContentFocus(selectedHomeTab)
            is HomeFocusIntent.FocusRegion -> tryRequestRegionFocus(
                tab = intent.tab,
                region = intent.region,
                entryHint = intent.entryHint,
            )
            is HomeFocusIntent.RestoreVideoKey -> tryRestoreVideoKey(intent)
        }
        if (result.isFocused) {
            pendingIntent = null
        }
        return result.isAccepted
    }

    fun recoverFocusAfterEscape(): Boolean {
        if (drainPendingFocus()) return true
        return when (focusMode) {
            is HomeFocusMode.Content -> {
                val contentResult = tryRequestSelectedContentFocus(selectedHomeTab)
                if (contentResult.isAccepted) {
                    true
                } else if (hasPendingContentFocus) {
                    false
                } else {
                    tryRequestTopBarFocus().isAccepted
                }
            }
            HomeFocusMode.TopBar -> {
                val topBarResult = tryRequestTopBarFocus()
                if (topBarResult.isAccepted) {
                    true
                } else {
                    tryRequestSelectedContentFocus(selectedHomeTab).isAccepted
                }
            }
        }
    }

    fun clearSelectedContentVisualState(): Boolean {
        val targets = contentTargets[selectedHomeTab] ?: return false
        return targets.values.fold(false) { cleared, target ->
            target.clearFocusVisualState() || cleared
        }
    }

    private fun tryRequestTopBarFocus(tab: AppTopLevelTab? = null): HomeFocusRequestResult {
        val target = topBarTarget ?: return HomeFocusRequestResult.Unavailable
        val result = if (tab == null) {
            target.requestFocusResult()
        } else {
            target.requestFocusTabResult(tab)
        }
        if (result.isFocused) {
            focusMode = HomeFocusMode.TopBar
        }
        return result
    }

    private fun tryRequestSelectedContentFocus(tab: AppTopLevelTab): HomeFocusRequestResult {
        val regions = contentFocusPriority(tab)
        if (regions.isEmpty()) return HomeFocusRequestResult.Unavailable
        return tryRequestFirstTarget(tab, regions)
    }

    private fun tryRequestRegionFocus(
        tab: AppTopLevelTab,
        region: HomeFocusRegion,
        entryHint: HomeFocusEntryHint = HomeFocusEntryHint(),
    ): HomeFocusRequestResult {
        val target = contentTargets[tab]?.get(region) ?: return HomeFocusRequestResult.Unavailable
        val result = target.requestFocusForEntryResult(entryHint)
        if (result.isFocused) {
            markContentFocusRequested(tab, region)
        }
        return result
    }

    private fun tryRestoreVideoKey(intent: HomeFocusIntent.RestoreVideoKey): HomeFocusRequestResult {
        if (intent.tab != selectedHomeTab) return HomeFocusRequestResult.Unavailable
        val targets = contentTargets[intent.tab] ?: return HomeFocusRequestResult.Unavailable
        val restorePriority = HomeFocusRules.ruleFor(intent.tab)?.restorePriority
            ?: HomeFocusRules.DefaultRestorePriority
        val orderedTargets = restorePriority.mapNotNull { region ->
            targets[region]?.let { target -> region to target }
        }
        var restoredRegion: HomeFocusRegion? = null
        for ((region, target) in orderedTargets) {
            val result = target.requestBackReturnFocusKeyResult(intent.key)
            if (result.isAccepted) {
                restoredRegion = region
                markContentFocusRequested(intent.tab, restoredRegion)
            }
            when (result) {
                HomeBackReturnRestoreResult.ExactFocused -> {
                    pendingRestoreCallback?.invoke(intent.key)
                    pendingRestoreCallback = null
                    return HomeFocusRequestResult.Focused
                }

                HomeBackReturnRestoreResult.FallbackFocused -> {
                    pendingRestoreCallback = null
                    pendingRestoreCancelCallback?.invoke()
                    return HomeFocusRequestResult.Focused
                }

                HomeBackReturnRestoreResult.PendingShort -> {
                    return HomeFocusRequestResult.Pending
                }

                HomeBackReturnRestoreResult.Unavailable -> Unit
            }
        }
        return HomeFocusRequestResult.Unavailable
    }

    private fun tryRequestFirstTarget(
        tab: AppTopLevelTab,
        regions: List<HomeFocusRegion>,
    ): HomeFocusRequestResult {
        val targets = contentTargets[tab] ?: return HomeFocusRequestResult.Unavailable
        for (region in regions) {
            val target = targets[region] ?: continue
            val result = target.requestFocusResult()
            if (result.isFocused) {
                markContentFocusRequested(tab, region)
            }
            if (result.isAccepted) {
                return result
            }
        }
        return HomeFocusRequestResult.Unavailable
    }

    private fun markContentFocusRequested(
        tab: AppTopLevelTab,
        region: HomeFocusRegion?,
    ) {
        if (tab == selectedHomeTab && region != null) {
            lastContentRegionByTab[tab] = region
        }
        enterContentMode(
            tab = tab,
            region = region,
            scene = scene,
            keepTopBarVisible = shouldKeepTopBarVisibleWhileEnteringContent(tab),
        )
    }

    private fun enterContentMode(
        tab: AppTopLevelTab,
        region: HomeFocusRegion?,
        scene: HomeFocusScene,
        keepTopBarVisible: Boolean,
    ) {
        val nextMode = HomeFocusMode.Content(
            tab = tab,
            region = region,
            scene = scene,
            keepTopBarVisible = keepTopBarVisible,
        )
        if (focusMode != nextMode) {
            logHomeFocus { "enterContentMode: $focusMode -> $nextMode" }
            focusMode = nextMode
        }
    }

    private fun contentFocusPriority(tab: AppTopLevelTab): List<HomeFocusRegion> {
        val targets = contentTargets[tab] ?: return emptyList()
        val rule = HomeFocusRules.ruleFor(tab) ?: return emptyList()
        val fallback = rule.entryPriorityFor(targets)
        val remembered = lastContentRegionByTab[tab]
            ?.takeIf { rule.rememberLastRegionOnEntry }
            ?.takeIf { region -> targets.containsKey(region) }
            ?: return fallback
        return listOf(remembered) + fallback.filterNot { region -> region == remembered }
    }

    private fun canCoordinateContent(tab: AppTopLevelTab): Boolean {
        return HomeFocusRules.canCoordinate(tab)
    }

    private fun shouldKeepTopBarVisibleWhileEnteringContent(tab: AppTopLevelTab): Boolean {
        return HomeFocusRules.ruleFor(tab)?.keepTopBarVisibleWhileEnteringContent == true
    }
}

private inline fun logHomeFocus(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d("HomeFocus", message())
    }
}
