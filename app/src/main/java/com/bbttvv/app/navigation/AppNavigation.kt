package com.bbttvv.app.navigation

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.VideoDetailRepository
import com.bbttvv.app.feature.plugin.TodayWatchPlugin
import com.bbttvv.app.feature.settings.SettingsScreen
import com.bbttvv.app.feature.live.LivePlayerScreen
import com.bbttvv.app.feature.publisher.PublisherScreen
import com.bbttvv.app.feature.bangumi.BangumiDetailScreen
import com.bbttvv.app.feature.video.screen.PlayerScreen

import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.detail.DetailOpenMode
import com.bbttvv.app.ui.detail.videoDetailRoutes
import com.bbttvv.app.ui.home.HomeRecyclerPools
import com.bbttvv.app.ui.home.HomeScreen
import com.bbttvv.app.ui.home.HomeViewModel

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navigationState = rememberAppNavigationState()
    val homeRecyclerPools = remember { HomeRecyclerPools() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnHome = navigationState.isOnHome(currentRoute)
    val updateContentOnTabFocusEnabled by SettingsManager.getUpdateContentOnTabFocusEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val watchLaterInTopTabsEnabled by SettingsManager.getWatchLaterInTopTabsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val plugins by PluginManager.pluginsFlow.collectAsStateWithLifecycle(initialValue = PluginManager.plugins)
    val todayWatchEnabled = plugins.any { it.plugin.id == TodayWatchPlugin.PLUGIN_ID && it.enabled }
    val visibleTopLevelTabs = remember(todayWatchEnabled, watchLaterInTopTabsEnabled) {
        AppTopLevelTab.resolveVisibleTabs(
            todayWatchEnabled = todayWatchEnabled,
            watchLaterInTopTabsEnabled = watchLaterInTopTabsEnabled,
        )
    }
    val visibleTopLevelTabsKey = remember(visibleTopLevelTabs) {
        visibleTopLevelTabs.joinToString(separator = "|") { tab -> tab.name }
    }
    val safeHomeTab = navigationState.safeHomeTab(visibleTopLevelTabs)

    DisposableEffect(homeRecyclerPools) {
        onDispose {
            homeRecyclerPools.clear()
        }
    }

    LaunchedEffect(currentRoute) {
        navigationState.onRouteChanged(currentRoute)
    }

    LaunchedEffect(visibleTopLevelTabsKey) {
        navigationState.normalizeHomeTabIfNeeded(visibleTopLevelTabs)
    }

    val detailOpenMode = remember(navigationState) {
        DetailOpenMode(
            restoreCommentFocusRpidFor = navigationState::restoreCommentFocusRpidFor,
            onCommentFocusRestored = navigationState::markCommentFocusRestored,
            setCommentFocusRestore = navigationState::setDetailCommentFocusRestore,
            clearCommentFocusRestore = navigationState::clearDetailCommentFocusRestore,
        )
    }

    fun handleHomeExitRequest() {
        when (navigationState.handleHomeBackPressed(System.currentTimeMillis(), visibleTopLevelTabs)) {
            HomeBackPressResult.Consumed -> Unit
            HomeBackPressResult.ShowExitHint -> {
                Toast.makeText(context, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show()
            }

            HomeBackPressResult.Exit -> {
                (context as? Activity)?.finish()
            }
        }
    }

    fun openVideoDetail(
        video: VideoItem,
        prepareNavigation: () -> Unit,
    ) {
        val safeBvid = video.bvid.trim()
        if (safeBvid.isBlank()) return
        if (safeBvid.startsWith("ss") || safeBvid.startsWith("ep")) {
            prepareNavigation()
            val id = safeBvid.substring(2).toLongOrNull() ?: 0L
            if (safeBvid.startsWith("ss")) {
                navController.navigate(ScreenRoutes.BangumiDetail.createRoute(seasonId = id, epId = 0L))
            } else {
                navController.navigate(ScreenRoutes.BangumiDetail.createRoute(seasonId = 0L, epId = id))
            }
            return
        }
        prepareNavigation()
        VideoDetailRepository.cacheVideoPreview(video.copy(bvid = safeBvid))
        navController.navigate(ScreenRoutes.VideoDetail.createRoute(safeBvid))
    }

    BackHandler(enabled = isOnHome) {
        handleHomeExitRequest()
    }

    NavHost(
        navController = navController,
        startDestination = ScreenRoutes.Home.route
    ) {
        composable(ScreenRoutes.Home.route) {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                recyclerPools = homeRecyclerPools,
                visibleTabs = visibleTopLevelTabs,
                selectedTabIndex = safeHomeTab.index,
                updateContentOnTabFocusEnabled = updateContentOnTabFocusEnabled,
                restoreVideoFocusKey = navigationState.restoreVideoFocusKey(currentRoute),
                restoreVideoFocusTab = navigationState.restoreVideoFocusTab(currentRoute),
                hasPendingVideoFocusRestore = navigationState.hasReadyHomeVideoFocusRestore(currentRoute),
                onVideoFocusRestored = { restoredKey ->
                    navigationState.markHomeVideoFocusRestored(restoredKey)
                },
                onCancelVideoFocusRestore = {
                    navigationState.cancelHomeVideoFocusRestore()
                },
                onTabSelected = { targetTab ->
                    navigationState.switchHomeTab(targetTab.index)
                },
                onVideoClick = { video ->
                    openVideoDetail(video) {
                        navigationState.prepareForDirectDetailOpen()
                    }
                },
                onLiveClick = { roomId, focusKey ->
                    if (focusKey == null) {
                        navigationState.prepareForDirectDetailOpen()
                    } else {
                        navigationState.prepareForLivePlayerOpen(focusKey)
                    }
                    navController.navigate(ScreenRoutes.LivePlayer.createRoute(roomId))
                },
                onRecommendVideoClick = { focusKey, video ->
                    openVideoDetail(video) {
                        navigationState.prepareForRecommendDetailOpen(focusKey)
                    }
                },
                onSearchVideoClick = { focusKey, video ->
                    openVideoDetail(video) {
                        navigationState.prepareForHomeTabDetailOpen(AppTopLevelTab.SEARCH, focusKey)
                    }
                },
                onDynamicVideoClick = { focusKey, video ->
                    openVideoDetail(video) {
                        navigationState.prepareForHomeTabDetailOpen(AppTopLevelTab.DYNAMIC, focusKey)
                    }
                },
                onOpenSettings = {
                    navController.navigate(ScreenRoutes.Settings.route)
                },
                onProfileVideoClick = { tab, focusKey, video ->
                    if (video.view_at > 0L) {
                        navigationState.prepareForInternalHomeTabPlayerOpen(tab, focusKey)
                        navController.navigate(
                            ScreenRoutes.VideoPlayer.createRoute(
                                bvid = video.bvid,
                                cid = video.cid,
                                aid = video.aid,
                                startPositionMs = video.progress
                                    .takeIf { it >= 5 }
                                    ?.times(1000L)
                                    ?: 0L
                            )
                        )
                    } else {
                        openVideoDetail(video) {
                            navigationState.prepareForHomeTabDetailOpen(tab, focusKey)
                        }
                    }
                },
                onOpenUp = { mid ->
                    navController.navigate(ScreenRoutes.Publisher.createRoute(mid))
                }
            )
        }
        composable(ScreenRoutes.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = ScreenRoutes.Publisher.route,
            arguments = listOf(
                navArgument("mid") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val mid = backStackEntry.arguments?.getLong("mid") ?: 0L
            val previousEntryState = navController.previousBackStackEntry?.savedStateHandle
            PublisherScreen(
                mid = mid,
                initialName = previousEntryState?.remove<String>(PublisherPreviewNameSavedStateKey),
                initialFace = previousEntryState?.remove<String>(PublisherPreviewFaceSavedStateKey),
                onOpenVideo = { publisherVideo ->
                    openVideoDetail(publisherVideo) {
                        navigationState.clearDetailCommentFocusRestore()
                    }
                }
            )
        }

        videoDetailRoutes(
            navController = navController,
            openMode = detailOpenMode,
        )

        composable(
            route = ScreenRoutes.LivePlayer.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getLong("roomId") ?: 0L
            LivePlayerScreen(
                roomId = roomId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = ScreenRoutes.VideoPlayer.route,
            arguments = listOf(
                navArgument("bvid") { type = NavType.StringType },
                navArgument("cid") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("aid") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("startPositionMs") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val bvid = backStackEntry.arguments?.getString("bvid").orEmpty()
            val cid = backStackEntry.arguments?.getLong("cid") ?: 0L
            val aid = backStackEntry.arguments?.getLong("aid") ?: 0L
            val startPositionMs = backStackEntry.arguments?.getLong("startPositionMs") ?: 0L
            PlayerScreen(
                bvid = bvid,
                cid = cid,
                aid = aid,
                startPositionMs = startPositionMs,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = ScreenRoutes.BangumiDetail.route,
            arguments = listOf(
                navArgument("seasonId") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("epId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val seasonId = backStackEntry.arguments?.getLong("seasonId") ?: 0L
            val epId = backStackEntry.arguments?.getLong("epId") ?: 0L
            BangumiDetailScreen(
                seasonId = seasonId,
                epId = epId,
                onBack = { navController.popBackStack() },
                onPlayEpisode = { targetEpId, cid, aid ->
                    navController.navigate(
                        ScreenRoutes.VideoPlayer.createRoute(
                            bvid = "ep$targetEpId",
                            cid = cid,
                            aid = aid,
                            startPositionMs = 0L
                        )
                    )
                }
            )
        }
    }
}
