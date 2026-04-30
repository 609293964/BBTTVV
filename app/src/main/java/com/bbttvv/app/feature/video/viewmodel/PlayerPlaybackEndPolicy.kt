package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.response.Page
import com.bbttvv.app.data.model.response.RelatedVideo

internal sealed interface PlayerPlaybackEndTarget {
    data object None : PlayerPlaybackEndTarget
    data object LoopOne : PlayerPlaybackEndTarget
    data object Return : PlayerPlaybackEndTarget
    data class PageTarget(
        val page: Page,
        val index: Int,
    ) : PlayerPlaybackEndTarget
    data class RelatedTarget(
        val video: RelatedVideo,
    ) : PlayerPlaybackEndTarget
}

internal fun resolvePlayerPlaybackEndTarget(
    action: SettingsManager.PlayerPlaybackEndAction,
    currentBvid: String,
    pages: List<Page>,
    currentPageIndex: Int,
    relatedVideos: List<RelatedVideo>,
): PlayerPlaybackEndTarget {
    return when (action) {
        SettingsManager.PlayerPlaybackEndAction.NONE -> PlayerPlaybackEndTarget.None
        SettingsManager.PlayerPlaybackEndAction.LOOP_ONE -> PlayerPlaybackEndTarget.LoopOne
        SettingsManager.PlayerPlaybackEndAction.RETURN -> PlayerPlaybackEndTarget.Return
        SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT -> {
            resolveNextPageTarget(
                pages = pages,
                currentPageIndex = currentPageIndex,
            )
                ?: resolveNextRelatedTarget(
                    currentBvid = currentBvid,
                    relatedVideos = relatedVideos,
                )
                ?: PlayerPlaybackEndTarget.Return
        }
    }
}

internal fun buildPlayerAutoNextPrompt(
    promptId: Long,
    target: PlayerPlaybackEndTarget,
): PlayerAutoNextPrompt? {
    return when (target) {
        is PlayerPlaybackEndTarget.PageTarget -> {
            PlayerAutoNextPrompt(
                id = promptId,
                title = target.page.part.ifBlank { "第${target.index + 1}P" },
                subtitle = "即将播放下一分P",
            )
        }

        is PlayerPlaybackEndTarget.RelatedTarget -> {
            PlayerAutoNextPrompt(
                id = promptId,
                title = target.video.title.ifBlank { target.video.bvid },
                subtitle = "即将播放相关推荐",
            )
        }

        PlayerPlaybackEndTarget.LoopOne,
        PlayerPlaybackEndTarget.None,
        PlayerPlaybackEndTarget.Return -> null
    }
}

private fun resolveNextPageTarget(
    pages: List<Page>,
    currentPageIndex: Int,
): PlayerPlaybackEndTarget.PageTarget? {
    if (pages.size <= 1) return null
    val nextIndex = currentPageIndex + 1
    val nextPage = pages.getOrNull(nextIndex) ?: return null
    if (nextPage.cid <= 0L) return null
    return PlayerPlaybackEndTarget.PageTarget(
        page = nextPage,
        index = nextIndex,
    )
}

private fun resolveNextRelatedTarget(
    currentBvid: String,
    relatedVideos: List<RelatedVideo>,
): PlayerPlaybackEndTarget.RelatedTarget? {
    val normalizedCurrentBvid = currentBvid.trim()
    val video = relatedVideos.firstOrNull { related ->
        val relatedBvid = related.bvid.trim()
        relatedBvid.isNotBlank() &&
            !relatedBvid.equals(normalizedCurrentBvid, ignoreCase = true)
    } ?: return null
    return PlayerPlaybackEndTarget.RelatedTarget(video)
}
