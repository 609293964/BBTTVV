package com.android.purebilibili.feature.bangumi.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.feature.bangumi.BangumiOverlayUnsupportedAction
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.feature.bangumi.resolveBangumiOverlayQualityLabel
import com.android.purebilibili.feature.bangumi.resolveBangumiOverlayShareTitle
import com.android.purebilibili.feature.bangumi.resolveBangumiUnsupportedOverlayActionMessage
import com.android.purebilibili.feature.bangumi.shouldShowBangumiOverlayDislikeAction
import com.android.purebilibili.core.util.ShareUtils
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlCallbacks
import com.android.purebilibili.feature.video.ui.overlay.SubtitleControlUiState
import com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlay

@Composable
internal fun BangumiPlayerOverlayHost(
    player: ExoPlayer,
    seasonId: Long,
    epId: Long,
    title: String,
    subtitle: String,
    bvid: String,
    aid: Long,
    cid: Long,
    coverUrl: String,
    currentVideoUrl: String,
    currentAudioUrl: String,
    isVisible: Boolean,
    onToggleVisible: () -> Unit,
    isFullscreen: Boolean,
    isScreenLocked: Boolean,
    onLockToggle: () -> Unit,
    currentQuality: Int,
    acceptQuality: List<Int>,
    acceptDescription: List<String>,
    onQualityChange: (Int) -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    danmakuOpacity: Float,
    danmakuFontScale: Float,
    danmakuSpeed: Float,
    danmakuDisplayArea: Float,
    danmakuMergeDuplicates: Boolean,
    onDanmakuOpacityChange: (Float) -> Unit,
    onDanmakuFontScaleChange: (Float) -> Unit,
    onDanmakuSpeedChange: (Float) -> Unit,
    onDanmakuDisplayAreaChange: (Float) -> Unit,
    onDanmakuMergeDuplicatesChange: (Boolean) -> Unit,
    currentAspectRatio: VideoAspectRatio,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    pages: List<Page>,
    currentPageIndex: Int,
    onPageSelect: (Int) -> Unit,
    isLiked: Boolean,
    coinCount: Int,
    onToggleLike: () -> Unit,
    onCoin: () -> Unit,
    onCaptureScreenshot: () -> Unit,
    onReloadVideo: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val currentQualityLabel = resolveBangumiOverlayQualityLabel(
        currentQuality = currentQuality,
        acceptQuality = acceptQuality,
        acceptDescription = acceptDescription
    )

    VideoPlayerOverlay(
        player = player,
        title = title,
        isVisible = isVisible,
        onToggleVisible = onToggleVisible,
        isFullscreen = isFullscreen,
        currentQualityLabel = currentQualityLabel,
        qualityLabels = acceptDescription,
        qualityIds = acceptQuality,
        onQualitySelected = { index ->
            acceptQuality.getOrNull(index)?.let(onQualityChange)
        },
        onBack = onBack,
        onHomeClick = onBack,
        onToggleFullscreen = onToggleFullscreen,
        bvid = bvid,
        cid = cid,
        videoOwnerName = title,
        videoDuration = player.duration.coerceAtLeast(0L),
        videoTitle = subtitle.ifBlank { title },
        currentAid = aid,
        currentQuality = currentQuality,
        currentVideoUrl = currentVideoUrl,
        currentAudioUrl = currentAudioUrl,
        isLiked = isLiked,
        isCoined = coinCount > 0,
        coinCount = coinCount,
        isScreenLocked = isScreenLocked,
        onLockToggle = onLockToggle,
        danmakuEnabled = danmakuEnabled,
        onDanmakuToggle = onDanmakuToggle,
        onDanmakuInputClick = {},
        danmakuOpacity = danmakuOpacity,
        danmakuFontScale = danmakuFontScale,
        danmakuSpeed = danmakuSpeed,
        danmakuDisplayArea = danmakuDisplayArea,
        danmakuMergeDuplicates = danmakuMergeDuplicates,
        onDanmakuOpacityChange = onDanmakuOpacityChange,
        onDanmakuFontScaleChange = onDanmakuFontScaleChange,
        onDanmakuSpeedChange = onDanmakuSpeedChange,
        onDanmakuDisplayAreaChange = onDanmakuDisplayAreaChange,
        onDanmakuMergeDuplicatesChange = onDanmakuMergeDuplicatesChange,
        subtitleControlState = SubtitleControlUiState(),
        subtitleControlCallbacks = SubtitleControlCallbacks(),
        currentAspectRatio = currentAspectRatio,
        onAspectRatioChange = onAspectRatioChange,
        onShare = {
            ShareUtils.shareBangumi(
                context = context,
                title = resolveBangumiOverlayShareTitle(title = title, subtitle = subtitle),
                seasonId = seasonId,
                epId = epId.takeIf { it > 0L }
            )
        },
        showDislikeAction = shouldShowBangumiOverlayDislikeAction(),
        coverUrl = coverUrl,
        onReloadVideo = onReloadVideo,
        onQualityChange = { qualityId, _ ->
            onQualityChange(qualityId)
        },
        onPipClick = {},
        onCaptureScreenshot = onCaptureScreenshot,
        onAudioOnlyToggle = {
            onShowMessage("番剧暂不支持音频模式")
        },
        onSaveCover = {
            onShowMessage("番剧暂不支持封面保存")
        },
        onDownloadAudio = {
            onShowMessage("番剧暂不支持音频下载")
        },
        pages = pages,
        currentPageIndex = currentPageIndex,
        onPageSelect = onPageSelect,
        onToggleLike = onToggleLike,
        onDislike = {
            onShowMessage(
                resolveBangumiUnsupportedOverlayActionMessage(
                    BangumiOverlayUnsupportedAction.DISLIKE
                )
            )
        },
        onCoin = onCoin
    )
}
