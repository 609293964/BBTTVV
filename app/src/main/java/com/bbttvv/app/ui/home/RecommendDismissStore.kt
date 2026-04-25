package com.bbttvv.app.ui.home

import com.bbttvv.app.data.model.response.VideoItem

/** Keeps recommendation dismissal state off the Composable hot path. */
internal class RecommendDismissStore {
    private val lock = Any()
    private val dismissedBvids = linkedSetOf<String>()
    private val dismissedAids = linkedSetOf<Long>()

    fun markDismissed(video: VideoItem): Boolean {
        val bvid = video.bvid.trim()
        return synchronized(lock) {
            var changed = false
            if (bvid.isNotEmpty()) {
                changed = dismissedBvids.add(bvid) || changed
            }
            if (video.aid > 0L) {
                changed = dismissedAids.add(video.aid) || changed
            }
            changed
        }
    }

    fun isDismissed(video: VideoItem): Boolean {
        val bvid = video.bvid.trim()
        return synchronized(lock) {
            (bvid.isNotEmpty() && bvid in dismissedBvids) ||
                (video.aid > 0L && video.aid in dismissedAids)
        }
    }

    fun hasDismissed(): Boolean {
        return synchronized(lock) {
            dismissedBvids.isNotEmpty() || dismissedAids.isNotEmpty()
        }
    }
}
