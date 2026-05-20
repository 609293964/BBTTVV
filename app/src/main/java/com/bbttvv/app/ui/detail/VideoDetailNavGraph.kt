package com.bbttvv.app.ui.detail

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.navigation.PublisherPreviewFaceSavedStateKey
import com.bbttvv.app.navigation.PublisherPreviewNameSavedStateKey
import com.bbttvv.app.navigation.ScreenRoutes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DetailRootReplySavedStateKey = "video_detail_root_reply_json"
private val videoDetailNavJson = Json { ignoreUnknownKeys = true }

internal data class DetailOpenMode(
    val restoreCommentFocusRpidFor: (String) -> Long?,
    val onCommentFocusRestored: (String, Long) -> Unit,
    val setCommentFocusRestore: (String, Long) -> Unit,
    val clearCommentFocusRestore: () -> Unit,
)

internal fun NavGraphBuilder.videoDetailRoutes(
    navController: NavHostController,
    openMode: DetailOpenMode,
) {
    composable(
        route = ScreenRoutes.VideoDetail.route,
        arguments = listOf(
            navArgument("bvid") { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        VideoDetailDestination(
            navController = navController,
            bvid = backStackEntry.arguments?.getString("bvid").orEmpty(),
            openMode = openMode,
            onBack = { navController.navigateDetailBackOrHome() },
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
            },
        ),
    ) { backStackEntry ->
        val bvid = backStackEntry.arguments?.getString("bvid").orEmpty()
        val aid = backStackEntry.arguments?.getLong("aid") ?: 0L
        val rootRpid = backStackEntry.arguments?.getLong("rootRpid") ?: 0L
        val rootReplyJson = navController.previousBackStackEntry
            ?.savedStateHandle
            ?.get<String>(DetailRootReplySavedStateKey)
        val rootReply = runCatching {
            rootReplyJson?.let { videoDetailNavJson.decodeFromString(ReplyItem.serializer(), it) }
        }.getOrNull()

        CommentRepliesScreen(
            bvid = bvid,
            aid = aid,
            rootRpid = rootRpid,
            rootReply = rootReply,
            onBack = { navController.navigateBackOrHome() },
        )
    }
}

@Composable
private fun VideoDetailDestination(
    navController: NavHostController,
    bvid: String,
    openMode: DetailOpenMode,
    onBack: () -> Unit,
) {
    DetailScreen(
        bvid = bvid,
        onBack = onBack,
        onPlay = { playBvid, aid, cid ->
            navController.navigate(
                ScreenRoutes.VideoPlayer.createRoute(
                    bvid = playBvid,
                    cid = cid,
                    aid = aid,
                ),
            )
        },
        restoreCommentFocusRpid = restoreCommentFocusRpidFor(openMode, bvid),
        onCommentFocusRestored = { restoredRpid ->
            markCommentFocusRestored(openMode, bvid, restoredRpid)
        },
        onOpenCommentReplies = { rootReply ->
            setCommentFocusRestore(openMode, bvid, rootReply.rpid)
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(
                    DetailRootReplySavedStateKey,
                    videoDetailNavJson.encodeToString(ReplyItem.serializer(), rootReply),
                )
            navController.navigate(
                ScreenRoutes.CommentReplies.createRoute(
                    bvid = bvid,
                    aid = rootReply.oid.takeIf { it > 0L } ?: 0L,
                    rootRpid = rootReply.rpid,
                ),
            )
        },
        onRelatedVideoClick = { relatedBvid ->
            clearCommentFocusRestore(openMode)
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
        },
    )
}

private fun restoreCommentFocusRpidFor(openMode: DetailOpenMode, bvid: String): Long? {
    return openMode.restoreCommentFocusRpidFor(bvid)
}

private fun markCommentFocusRestored(
    openMode: DetailOpenMode,
    bvid: String,
    restoredRpid: Long,
) {
    openMode.onCommentFocusRestored(bvid, restoredRpid)
}

private fun setCommentFocusRestore(
    openMode: DetailOpenMode,
    bvid: String,
    rpid: Long,
) {
    openMode.setCommentFocusRestore(bvid, rpid)
}

private fun clearCommentFocusRestore(openMode: DetailOpenMode) {
    openMode.clearCommentFocusRestore()
}

private fun NavHostController.navigateBackOrHome() {
    if (!popBackStack()) {
        navigate(ScreenRoutes.Home.route) {
            launchSingleTop = true
        }
    }
}

private fun NavHostController.navigateDetailBackOrHome() {
    val singleBack = com.bbttvv.app.core.store.SettingsManager.getSingleBackToHomeEnabledSync(context)
    if (singleBack) {
        if (!popBackStack(ScreenRoutes.Home.route, inclusive = false)) {
            if (!popBackStack()) {
                navigate(ScreenRoutes.Home.route) {
                    launchSingleTop = true
                }
            }
        }
    } else {
        if (!popBackStack()) {
            navigate(ScreenRoutes.Home.route) {
                launchSingleTop = true
            }
        }
    }
}
