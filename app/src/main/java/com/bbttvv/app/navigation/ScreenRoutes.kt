package com.bbttvv.app.navigation

import android.net.Uri

sealed class ScreenRoutes(val route: String) {
    data object Home : ScreenRoutes("home")
    data object Settings : ScreenRoutes("settings")

    // Search and Profile are Home top-level tabs, not independent navigation routes.

    data object VideoDetail : ScreenRoutes("video/{bvid}") {
        fun createRoute(bvid: String): String {
            return "video/${Uri.encode(bvid)}"
        }
    }

    data object CommentReplies :
        ScreenRoutes("video/{bvid}/comment_replies?aid={aid}&rootRpid={rootRpid}") {
        fun createRoute(
            bvid: String,
            aid: Long,
            rootRpid: Long
        ): String {
            return "video/${Uri.encode(bvid)}/comment_replies?aid=$aid&rootRpid=$rootRpid"
        }
    }

    data object VideoPlayer : ScreenRoutes("video_player/{bvid}?cid={cid}&aid={aid}&startPositionMs={startPositionMs}") {
        fun createRoute(
            bvid: String,
            cid: Long = 0L,
            aid: Long = 0L,
            startPositionMs: Long = 0L
        ): String {
            return "video_player/${Uri.encode(bvid)}?cid=$cid&aid=$aid&startPositionMs=$startPositionMs"
        }
    }

    data object Publisher : ScreenRoutes("publisher/{mid}") {
        fun createRoute(mid: Long): String {
            return "publisher/$mid"
        }
    }

    data object LivePlayer : ScreenRoutes("live_player/{roomId}") {
        fun createRoute(roomId: Long): String {
            return "live_player/$roomId"
        }
    }
}
