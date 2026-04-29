package com.bbttvv.app.ui.detail

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.feature.publisher.PublisherScreen
import com.bbttvv.app.feature.video.screen.PlayerScreen
import com.bbttvv.app.navigation.ScreenRoutes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val VideoDetailEntryRoute = "video_detail_entry"
private const val DetailRootReplySavedStateKey = "video_detail_root_reply_json"
private const val PublisherPreviewNameSavedStateKey = "video_detail_publisher_preview_name"
private const val PublisherPreviewFaceSavedStateKey = "video_detail_publisher_preview_face"
private val videoDetailNavJson = Json { ignoreUnknownKeys = true }

internal sealed interface DetailOpenMode {
    data object StandaloneActivity : DetailOpenMode

    data class MainHost(
        val restoreCommentFocusRpidFor: (String) -> Long?,
        val onCommentFocusRestored: (String, Long) -> Unit,
        val setCommentFocusRestore: (String, Long) -> Unit,
        val clearCommentFocusRestore: () -> Unit,
    ) : DetailOpenMode
}

@Composable
internal fun VideoDetailNavGraph(
    initialBvid: String,
    openMode: DetailOpenMode,
    onFinish: () -> Unit,
) {
    val navController = rememberNavController()

    fun navigateBackOrFinish() {
        if (navController.previousBackStackEntry == null) {
            onFinish()
        } else {
            navController.popBackStack()
        }
    }

    NavHost(
        navController = navController,
        startDestination = VideoDetailEntryRoute,
    ) {
        composable(VideoDetailEntryRoute) {
            VideoDetailDestination(
                navController = navController,
                bvid = initialBvid,
                openMode = openMode,
                onBack = { navigateBackOrFinish() },
            )
        }

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
                onBack = { navigateBackOrFinish() },
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
                onBack = { navigateBackOrFinish() },
            )
        }

        composable(
            route = ScreenRoutes.Publisher.route,
            arguments = listOf(
                navArgument("mid") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val mid = backStackEntry.arguments?.getLong("mid") ?: 0L
            val previousEntryState = navController.previousBackStackEntry?.savedStateHandle
            PublisherScreen(
                mid = mid,
                initialName = previousEntryState?.remove<String>(PublisherPreviewNameSavedStateKey),
                initialFace = previousEntryState?.remove<String>(PublisherPreviewFaceSavedStateKey),
                onOpenVideo = { publisherVideo ->
                    clearCommentFocusRestore(openMode)
                    navController.navigate(ScreenRoutes.VideoDetail.createRoute(publisherVideo.bvid))
                },
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
                },
            ),
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
                onBack = { navigateBackOrFinish() },
            )
        }
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
    return when (openMode) {
        is DetailOpenMode.MainHost -> openMode.restoreCommentFocusRpidFor(bvid)
        DetailOpenMode.StandaloneActivity -> null
    }
}

private fun markCommentFocusRestored(
    openMode: DetailOpenMode,
    bvid: String,
    restoredRpid: Long,
) {
    if (openMode is DetailOpenMode.MainHost) {
        openMode.onCommentFocusRestored(bvid, restoredRpid)
    }
}

private fun setCommentFocusRestore(
    openMode: DetailOpenMode,
    bvid: String,
    rpid: Long,
) {
    if (openMode is DetailOpenMode.MainHost) {
        openMode.setCommentFocusRestore(bvid, rpid)
    }
}

private fun clearCommentFocusRestore(openMode: DetailOpenMode) {
    if (openMode is DetailOpenMode.MainHost) {
        openMode.clearCommentFocusRestore()
    }
}
