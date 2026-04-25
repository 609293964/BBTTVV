package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.feature.video.viewmodel.PlaybackBadge
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentsUiState
import com.bbttvv.app.feature.video.viewmodel.PlayerPlaybackState
import com.bbttvv.app.feature.video.viewmodel.PlayerUiState
import com.bbttvv.app.feature.video.videoshot.VideoShotFrame
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.PlayerOverlayHost(
    uiState: PlayerUiState,
    commentsUiState: PlayerCommentsUiState,
    overlayUiState: PlayerOverlayUiState,
    playbackState: State<PlayerPlaybackState>,
    actions: List<PlayerAction>,
    panelOptions: List<PanelOption>,
    sponsorMarkers: List<SponsorProgressMark>,
    topRightBadges: List<PlaybackBadge>,
    seekPreviewFrame: VideoShotFrame?,
    showOnlineCount: Boolean,
    isDanmakuEnabled: Boolean,
    isCommentsPanelVisible: Boolean,
    visualEffectsState: PlayerVisualEffectsState,
    progressFocusRequester: FocusRequester,
    actionFocusRequesters: List<FocusRequester>,
    panelFocusRequesters: List<FocusRequester>,
    commentsPanelPrimaryFocusRequester: FocusRequester,
    onOverlayKey: (android.view.KeyEvent) -> Boolean,
    onToggleCommentSort: () -> Unit,
    onRetryComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onOpenCommentThread: (ReplyItem) -> Unit,
    onBackFromCommentThread: () -> Unit,
) {
    if (overlayUiState.overlayMode == PlayerOverlayMode.FullControls) {
        PlayerControlsScrim()
        PlayerTopStatusBadges(
            uiState = uiState,
            topRightBadges = topRightBadges,
            showOnlineCount = showOnlineCount,
            showDebugOverlay = overlayUiState.showDebugOverlay,
        )
        PlayerControlsLayer(
            uiState = uiState,
            commentsUiState = commentsUiState,
            overlayUiState = overlayUiState,
            playbackState = playbackState,
            actions = actions,
            panelOptions = panelOptions,
            sponsorMarkers = sponsorMarkers,
            seekPreviewFrame = seekPreviewFrame,
            isDanmakuEnabled = isDanmakuEnabled,
            isCommentsPanelVisible = isCommentsPanelVisible,
            visualEffectsState = visualEffectsState,
            progressFocusRequester = progressFocusRequester,
            actionFocusRequesters = actionFocusRequesters,
            panelFocusRequesters = panelFocusRequesters,
            commentsPanelPrimaryFocusRequester = commentsPanelPrimaryFocusRequester,
            onOverlayKey = onOverlayKey,
            onToggleCommentSort = onToggleCommentSort,
            onRetryComments = onRetryComments,
            onLoadMoreComments = onLoadMoreComments,
            onOpenCommentThread = onOpenCommentThread,
            onBackFromCommentThread = onBackFromCommentThread,
        )
    }
}

internal fun buildTopRightBadges(
    uiState: PlayerUiState,
    isDanmakuEnabled: Boolean,
): List<PlaybackBadge> {
    val seen = linkedSetOf<String>()
    val duplicatedCodecLabels = buildSet {
        uiState.selectedVideoCodecLabel.trim().takeIf { it.isNotBlank() }?.let(::add)
        uiState.selectedAudioCodecLabel.trim().takeIf { it.isNotBlank() }?.let(::add)
    }
    return buildList {
        fun addUnique(label: String?, isActive: Boolean = false) {
            val normalized = label?.trim().orEmpty()
            if (normalized.isBlank() || !seen.add(normalized)) return
            add(PlaybackBadge(label = normalized, isActive = isActive))
        }

        addUnique(
            actionSecondaryText(
                action = PlayerAction.Quality,
                uiState = uiState,
                isDanmakuEnabled = isDanmakuEnabled,
            )?.let { "质量 $it" }
        )
        addUnique(if (isDanmakuEnabled) "弹幕 开启" else "弹幕 关闭", isActive = isDanmakuEnabled)
        addUnique(
            actionSecondaryText(
                action = PlayerAction.Audio,
                uiState = uiState,
                isDanmakuEnabled = isDanmakuEnabled,
            )?.let { "音频 $it" }
        )
        addUnique(
            actionSecondaryText(
                action = PlayerAction.Codec,
                uiState = uiState,
                isDanmakuEnabled = isDanmakuEnabled,
            )?.let { "码率 $it" }
        )

        uiState.playbackBadges.forEach { badge ->
            val normalized = badge.label.trim()
            if (normalized.isBlank()) return@forEach
            val isDuplicatedCodecBadge = duplicatedCodecLabels.any { it.equals(normalized, ignoreCase = true) }
            if (!isDuplicatedCodecBadge && seen.add(normalized)) {
                add(badge.copy(label = normalized))
            }
        }
    }
}

@Composable
private fun BoxScope.PlayerControlsScrim() {
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(300.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.28f),
                        Color.Black.copy(alpha = 0.82f),
                    ),
                ),
            ),
    )
}

@Composable
private fun BoxScope.PlayerTopStatusBadges(
    uiState: PlayerUiState,
    topRightBadges: List<PlaybackBadge>,
    showOnlineCount: Boolean,
    showDebugOverlay: Boolean,
) {
    if (showOnlineCount && uiState.onlineCountText.isNotBlank()) {
        OnlineCountPill(
            text = uiState.onlineCountText,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = PlayerLayoutTokens.topOnlinePadding,
                    top = PlayerLayoutTokens.topBadgePadding,
                ),
        )
    }

    if (topRightBadges.isNotEmpty() && !showDebugOverlay) {
        BadgeRow(
            badges = topRightBadges,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = PlayerLayoutTokens.topBadgePadding,
                    end = PlayerLayoutTokens.topOnlinePadding,
                ),
        )
    }
}

@Composable
private fun BoxScope.PlayerControlsLayer(
    uiState: PlayerUiState,
    commentsUiState: PlayerCommentsUiState,
    overlayUiState: PlayerOverlayUiState,
    playbackState: State<PlayerPlaybackState>,
    actions: List<PlayerAction>,
    panelOptions: List<PanelOption>,
    sponsorMarkers: List<SponsorProgressMark>,
    seekPreviewFrame: VideoShotFrame?,
    isDanmakuEnabled: Boolean,
    isCommentsPanelVisible: Boolean,
    visualEffectsState: PlayerVisualEffectsState,
    progressFocusRequester: FocusRequester,
    actionFocusRequesters: List<FocusRequester>,
    panelFocusRequesters: List<FocusRequester>,
    commentsPanelPrimaryFocusRequester: FocusRequester,
    onOverlayKey: (android.view.KeyEvent) -> Boolean,
    onToggleCommentSort: () -> Unit,
    onRetryComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onOpenCommentThread: (ReplyItem) -> Unit,
    onBackFromCommentThread: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                if (isCommentsPanelVisible) {
                    false
                } else {
                    onOverlayKey(keyEvent.nativeKeyEvent)
                }
            },
    ) {
        PlayerInfoOverlay(
            uiState = uiState,
            playbackState = playbackState,
            sponsorMarkers = sponsorMarkers,
            isProgressFocused = overlayUiState.fullControlsFocus == PlayerFullControlsFocus.Progress &&
                overlayUiState.activePanel == null,
            progressPreviewState = overlayUiState.simpleSeekState,
            progressModifier = Modifier
                .focusRequester(progressFocusRequester)
                .focusable(),
            actionBar = {
                PlayerActionBar(
                    actions = actions,
                    selectedIndex = overlayUiState.selectedActionIndex,
                    hasFocus = overlayUiState.fullControlsFocus == PlayerFullControlsFocus.Actions ||
                        overlayUiState.activePanel != null,
                    isDanmakuEnabled = isDanmakuEnabled,
                    actionFocusRequesters = actionFocusRequesters,
                )
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = PlayerLayoutTokens.overlayHorizontalPadding,
                    end = PlayerLayoutTokens.overlayHorizontalPadding,
                    bottom = PlayerLayoutTokens.overlayBottomPadding,
                ),
        )

        overlayUiState.simpleSeekState?.let { seekState ->
            SimpleSeekOverlay(
                state = seekState,
                previewFrame = seekPreviewFrame,
                playbackState = playbackState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = PlayerLayoutTokens.overlayHorizontalPadding,
                        end = PlayerLayoutTokens.overlayHorizontalPadding,
                        bottom = PlayerLayoutTokens.seekPreviewBottomPadding,
                    )
                    .widthIn(max = 1080.dp)
                    .fillMaxWidth(),
            )
        }

        overlayUiState.activePanel?.let { activePanel ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = PlayerLayoutTokens.overlayHorizontalPadding,
                        end = PlayerLayoutTokens.overlayHorizontalPadding,
                        bottom = PlayerLayoutTokens.panelBottomPadding,
                    )
                    .widthIn(max = 1080.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                PlayerOptionsPanel(
                    title = panelTitleFor(activePanel),
                    options = panelOptions,
                    selectedIndex = overlayUiState.selectedPanelIndex,
                    visualEffectsState = visualEffectsState,
                    optionFocusRequesters = panelFocusRequesters,
                )
            }
        }

        if (isCommentsPanelVisible) {
            PlayerCommentsPanelHost(
                uiState = commentsUiState,
                totalCommentCount = maxOf(commentsUiState.totalCount, uiState.info?.stat?.reply ?: 0),
                primaryFocusRequester = commentsPanelPrimaryFocusRequester,
                onToggleSort = onToggleCommentSort,
                onRetry = onRetryComments,
                onLoadMore = onLoadMoreComments,
                onOpenThread = onOpenCommentThread,
                onBackFromThread = onBackFromCommentThread,
            )
        }
    }
}

private fun actionSecondaryText(
    action: PlayerAction,
    uiState: PlayerUiState,
    isDanmakuEnabled: Boolean,
): String? {
    return when (action) {
        PlayerAction.Comments -> null
        PlayerAction.Speed -> formatPlaybackSpeedLabel(uiState.playbackSpeed)
        PlayerAction.Quality -> uiState.selectedQualityLabel
            .ifBlank { null }
            ?.let(::compactActionValue)
        PlayerAction.Audio -> selectedAudioActionValue(uiState)?.let(::compactActionValue)
        PlayerAction.Codec -> buildCodecActionValue(uiState)?.let(::compactActionValue)
        PlayerAction.Danmaku -> if (isDanmakuEnabled) "开启" else "关闭"
        PlayerAction.Detail -> null
    }
}

private fun selectedAudioActionValue(uiState: PlayerUiState): String? {
    return uiState.audioOptions.firstOrNull { it.isSelected }?.label
        ?.takeIf { it.isNotBlank() }
        ?: uiState.selectedAudioCodecLabel.takeIf { it.isNotBlank() }
}

private fun buildCodecActionValue(uiState: PlayerUiState): String? {
    val codecLabel = uiState.selectedVideoCodecLabel.takeIf { it.isNotBlank() }
        ?: uiState.videoCodecOptions.firstOrNull { it.isSelected }?.label?.takeIf { it.isNotBlank() }
    val totalBitrate = uiState.selectedVideoBandwidth.coerceAtLeast(0) +
        uiState.selectedAudioBandwidth.coerceAtLeast(0)
    val bitrateLabel = totalBitrate.takeIf { it > 0 }?.let(::formatCompactBitrate)
    return listOfNotNull(codecLabel, bitrateLabel).joinToString(" ").ifBlank { null }
}

private fun compactActionValue(value: String): String {
    return value
        .replace("高码率", "高码")
        .replace("低码率", "低码")
        .replace("音频", "音频")
        .replace("码率", "码率")
        .replace(" ", "")
}

private fun formatCompactBitrate(bitrate: Int): String {
    if (bitrate <= 0) return "--"
    val kbps = bitrate / 1000f
    return if (kbps >= 1000f) {
        String.format(Locale.US, "%.1f", kbps / 1000f).removeSuffix(".0") + "M"
    } else {
        "${kbps.roundToInt()}K"
    }
}

@Composable
private fun OnlineCountPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Color(0xFFFB7299)),
        )
        Text(
            text = text,
            color = Color(0xEAF7FAFF),
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BadgeRow(
    badges: List<PlaybackBadge>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(badges) { badge ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = if (badge.isActive) 0.28f else 0.15f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = badge.label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}
