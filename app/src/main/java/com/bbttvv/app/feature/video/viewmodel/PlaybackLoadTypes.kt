package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.feature.video.usecase.PlaybackSource

internal data class PlaybackLoadRequest(
    val bvid: String,
    val aid: Long = 0L,
    val cid: Long = 0L,
    val startPositionMs: Long = 0L,
    val force: Boolean = false,
    val resumeFromPrompt: Boolean = false,
)

internal data class PlaybackRuntimeState(
    val bvid: String = "",
    val aid: Long = 0L,
    val cid: Long = 0L,
    val source: PlaybackSource? = null,
) {
    val sessionKey: String
        get() = "$bvid:$cid"
}
