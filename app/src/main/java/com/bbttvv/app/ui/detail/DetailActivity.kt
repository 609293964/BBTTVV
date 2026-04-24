package com.bbttvv.app.ui.detail

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.MaterialTheme
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.feature.publisher.PublisherScreen
import com.bbttvv.app.feature.video.screen.PlayerScreen
import com.bbttvv.app.navigation.ScreenRoutes
import com.bbttvv.app.ui.theme.AppTheme
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DetailActivityExtraBvid = "com.bbttvv.app.extra.BVID"
private const val DetailActivityRootReplySavedStateKey = "detail_activity_root_reply_json"
private const val DetailActivityPublisherPreviewNameSavedStateKey = "detail_activity_publisher_preview_name"
private const val DetailActivityPublisherPreviewFaceSavedStateKey = "detail_activity_publisher_preview_face"

private val detailActivityJson = Json { ignoreUnknownKeys = true }

class DetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isDebugInspectorInfoEnabled = false

        val initialBvid = intent.getStringExtra(DetailActivityExtraBvid).orEmpty().trim()
        if (initialBvid.isBlank()) {
            finish()
            return
        }

        setContent {
            AppTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    DetailActivityNavHost(
                        initialBvid = initialBvid,
                        onFinish = { finish() }
                    )
                }
            }
        }
        suppressDetailActivityTransition()
    }

    override fun finish() {
        super.finish()
        suppressDetailActivityTransition()
    }

    companion object {
        fun createIntent(context: Context, bvid: String): Intent {
            return Intent(context, DetailActivity::class.java)
                .putExtra(DetailActivityExtraBvid, bvid.trim())
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
    }
}

fun Context.startVideoDetailActivity(bvid: String) {
    val safeBvid = bvid.trim()
    if (safeBvid.isBlank()) return

    val intent = DetailActivity.createIntent(this, safeBvid).apply {
        if (this@startVideoDetailActivity !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    startActivity(intent)
    (this as? Activity)?.suppressDetailActivityTransition()
}

@Composable
private fun DetailActivityNavHost(
    initialBvid: String,
    onFinish: () -> Unit
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
        startDestination = DetailActivityEntryRoute
    ) {
        composable(DetailActivityEntryRoute) {
            DetailActivityDetailDestination(
                navController = navController,
                bvid = initialBvid,
                onBack = { navigateBackOrFinish() }
            )
        }

        composable(
            route = ScreenRoutes.VideoDetail.route,
            arguments = listOf(
                navArgument("bvid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            DetailActivityDetailDestination(
                navController = navController,
                bvid = backStackEntry.arguments?.getString("bvid").orEmpty(),
                onBack = { navigateBackOrFinish() }
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
                ?.get<String>(DetailActivityRootReplySavedStateKey)
            val rootReply = runCatching {
                rootReplyJson?.let { detailActivityJson.decodeFromString(ReplyItem.serializer(), it) }
            }.getOrNull()

            CommentRepliesScreen(
                bvid = bvid,
                aid = aid,
                rootRpid = rootRpid,
                rootReply = rootReply,
                onBack = { navigateBackOrFinish() }
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
                initialName = previousEntryState?.remove<String>(DetailActivityPublisherPreviewNameSavedStateKey),
                initialFace = previousEntryState?.remove<String>(DetailActivityPublisherPreviewFaceSavedStateKey),
                onOpenVideo = { publisherBvid ->
                    navController.navigate(ScreenRoutes.VideoDetail.createRoute(publisherBvid))
                }
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
                onBack = { navigateBackOrFinish() },
                onOpenDetail = {
                    navController.navigate(ScreenRoutes.VideoDetail.createRoute(bvid))
                }
            )
        }
    }
}

@Composable
private fun DetailActivityDetailDestination(
    navController: NavHostController,
    bvid: String,
    onBack: () -> Unit
) {
    DetailScreen(
        bvid = bvid,
        onBack = onBack,
        onPlay = { playBvid, aid, cid ->
            navController.navigate(
                ScreenRoutes.VideoPlayer.createRoute(
                    bvid = playBvid,
                    cid = cid,
                    aid = aid
                )
            )
        },
        onOpenCommentReplies = { rootReply ->
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(
                    DetailActivityRootReplySavedStateKey,
                    detailActivityJson.encodeToString(ReplyItem.serializer(), rootReply)
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
            navController.navigate(ScreenRoutes.VideoDetail.createRoute(relatedBvid))
        },
        onOpenPublisher = { mid, name, face ->
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(DetailActivityPublisherPreviewNameSavedStateKey, name)
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.set(DetailActivityPublisherPreviewFaceSavedStateKey, face)
            navController.navigate(ScreenRoutes.Publisher.createRoute(mid))
        }
    )
}

private const val DetailActivityEntryRoute = "detail_activity_entry"

private fun Activity.suppressDetailActivityTransition() {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
