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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.feature.plugin.TodayWatchPlugin
import com.bbttvv.app.feature.profile.ProfileScreen
import com.bbttvv.app.feature.search.SearchScreen
import com.bbttvv.app.feature.settings.SettingsScreen
import com.bbttvv.app.feature.live.LivePlayerScreen
import com.bbttvv.app.feature.publisher.PublisherScreen
import com.bbttvv.app.feature.video.screen.PlayerScreen

import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.detail.CommentRepliesScreen
import com.bbttvv.app.ui.detail.DetailScreen
import com.bbttvv.app.ui.detail.startVideoDetailActivity
import com.bbttvv.app.ui.home.HomeScreen
import com.bbttvv.app.ui.home.HomeViewModel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DetailRootReplySavedStateKey = "detail_root_reply_json"
private const val PublisherPreviewNameSavedStateKey = "publisher_preview_name"
private const val PublisherPreviewFaceSavedStateKey = "publisher_preview_face"
private val detailReplyJson = Json { ignoreUnknownKeys = true }

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val navigationState = rememberAppNavigationState()
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

    LaunchedEffect(currentRoute) {
        navigationState.onRouteChanged(currentRoute)
    }

    LaunchedEffect(visibleTopLevelTabsKey) {
        navigationState.normalizeHomeTabIfNeeded(visibleTopLevelTabs)
    }

    DisposableEffect(lifecycleOwner, navigationState) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> navigationState.onHostActivityPaused()
                Lifecycle.Event.ON_RESUME -> navigationState.onHostActivityResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                visibleTabs = visibleTopLevelTabs,
                selectedTabIndex = safeHomeTab.index,
                updateContentOnTabFocusEnabled = updateContentOnTabFocusEnabled,
                restoreVideoFocusKey = navigationState.restoreVideoFocusKey(currentRoute),
                restoreVideoFocusTab = navigationState.restoreVideoFocusTab(currentRoute),
                hasPendingVideoFocusRestore = navigationState.hasReadyHomeVideoFocusRestore(currentRoute),
                onVideoFocusRestored = { restoredKey ->
                    navigationState.markHomeVideoFocusRestored(restoredKey)
                },
                onTabSelected = { targetTab ->
                    navigationState.switchHomeTab(targetTab.index)
                },
                onVideoClick = { video ->
                    navigationState.prepareForDirectDetailOpen()
                    context.startVideoDetailActivity(video)
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
                    navigationState.prepareForRecommendDetailOpen(focusKey)
                    context.startVideoDetailActivity(video)
                },
                onOpenSettings = {
                    navController.navigate(ScreenRoutes.Settings.route)
                },
                onProfileVideoClick = { tab, focusKey, video ->
                    if (video.view_at > 0L) {
                        if (tab == AppTopLevelTab.WATCH_LATER) {
                            navigationState.prepareForInternalHomeTabPlayerOpen(tab, focusKey)
                        } else {
                            navigationState.clearDetailCommentFocusRestore()
                        }
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
                        if (tab == AppTopLevelTab.WATCH_LATER) {
                            navigationState.prepareForHomeTabDetailOpen(tab, focusKey)
                        } else {
                            navigationState.clearDetailCommentFocusRestore()
                        }
                        context.startVideoDetailActivity(video)
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
            route = ScreenRoutes.VideoDetail.route,
            arguments = listOf(
                navArgument("bvid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bvid = backStackEntry.arguments?.getString("bvid").orEmpty()
            DetailScreen(
                bvid = bvid,
                onBack = { navController.popBackStack() },
                onPlay = { playBvid, aid, cid ->
                    navController.navigate(
                        ScreenRoutes.VideoPlayer.createRoute(
                            bvid = playBvid,
                            cid = cid,
                            aid = aid
                        )
                    )
                },
                restoreCommentFocusRpid = navigationState.restoreCommentFocusRpidFor(bvid),
                onCommentFocusRestored = { restoredRpid ->
                    navigationState.markCommentFocusRestored(bvid, restoredRpid)
                },
                onOpenCommentReplies = { rootReply ->
                    navigationState.setDetailCommentFocusRestore(
                        bvid = bvid,
                        rpid = rootReply.rpid,
                    )
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(
                            DetailRootReplySavedStateKey,
                            detailReplyJson.encodeToString(ReplyItem.serializer(), rootReply)
                        )
                    navController.navigate(
                        ScreenRoutes.CommentReplies.createRoute(
                            bvid = bvid,
                            aid = rootReply.oid.takeIf { it > 0L } ?: 0L,
                            rootRpid = rootReply.rpid
                        )
                    )
                },
                onRelatedVideoClick = { relatedBvid ->
                    navigationState.clearDetailCommentFocusRestore()
                    navController.navigate(ScreenRoutes.VideoDetail.createRoute(relatedBvid))
                },
                onOpenPublisher = { mid, name, face ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(PublisherPreviewNameSavedStateKey, name)
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set(PublisherPreviewFaceSavedStateKey, face)
                    navController.navigate(ScreenRoutes.Publisher.createRoute(mid))
                }
            )
        }

        composable(
            route = ScreenRoutes.CommentReplies.route,
            arguments = listOf(
                navArgument("bvid") { type = NavType.StringType },
                navArgument("aid") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("rootRpid") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val bvid = backStackEntry.arguments?.getString("bvid").orEmpty()
            val aid = backStackEntry.arguments?.getLong("aid") ?: 0L
            val rootRpid = backStackEntry.arguments?.getLong("rootRpid") ?: 0L
            val rootReplyJson = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>(DetailRootReplySavedStateKey)
            val rootReply = runCatching {
                rootReplyJson?.let { detailReplyJson.decodeFromString(ReplyItem.serializer(), it) }
            }.getOrNull()

            CommentRepliesScreen(
                bvid = bvid,
                aid = aid,
                rootRpid = rootRpid,
                rootReply = rootReply,
                onBack = { navController.popBackStack() }
            )
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
                    navigationState.clearDetailCommentFocusRestore()
                    context.startVideoDetailActivity(publisherVideo)
                }
            )
        }

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
                onOpenDetail = {
                    context.startVideoDetailActivity(bvid)
                }
            )
        }
    }
}
