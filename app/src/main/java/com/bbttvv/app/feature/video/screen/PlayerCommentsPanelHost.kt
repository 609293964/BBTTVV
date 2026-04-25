package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentsUiState

@Composable
internal fun BoxScope.PlayerCommentsPanelHost(
    uiState: PlayerCommentsUiState,
    totalCommentCount: Int,
    primaryFocusRequester: FocusRequester,
    onToggleSort: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenThread: (ReplyItem) -> Unit,
    onBackFromThread: () -> Unit,
) {
    PlayerCommentsPanel(
        uiState = uiState,
        totalCommentCount = totalCommentCount,
        primaryFocusRequester = primaryFocusRequester,
        onToggleSort = onToggleSort,
        onRetry = onRetry,
        onLoadMore = onLoadMore,
        onOpenThread = onOpenThread,
        onBackFromThread = onBackFromThread,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(
                top = PlayerLayoutTokens.commentsPanelEdgePadding,
                end = PlayerLayoutTokens.commentsPanelEdgePadding,
                bottom = PlayerLayoutTokens.commentsPanelEdgePadding,
            ),
    )
}
