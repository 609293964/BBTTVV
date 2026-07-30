package com.bbttvv.app.feature.video.screen

internal enum class PlayerExclusiveOverlayOwner {
    CommentImageViewer,
    InteractiveVideo,
    DanmakuVote,
}

internal fun resolvePlayerExclusiveOverlayOwner(
    commentImageViewerOpen: Boolean,
    interactiveVideoOpen: Boolean,
    danmakuVoteOpen: Boolean,
): PlayerExclusiveOverlayOwner? {
    return when {
        commentImageViewerOpen -> PlayerExclusiveOverlayOwner.CommentImageViewer
        interactiveVideoOpen -> PlayerExclusiveOverlayOwner.InteractiveVideo
        danmakuVoteOpen -> PlayerExclusiveOverlayOwner.DanmakuVote
        else -> null
    }
}
