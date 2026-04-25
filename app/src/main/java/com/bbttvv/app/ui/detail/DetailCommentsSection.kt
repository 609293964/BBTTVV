package com.bbttvv.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.ReplyItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DetailCommentSortPillMetrics = DetailPillMetrics(
    height = 28.dp,
    minWidth = 56.dp,
    horizontalPadding = 12.dp,
    textSize = 6.sp,
    iconSpacing = 4.dp,
    focusedScale = 1.0f
)

private val DetailCommentActionPillMetrics = DetailPillMetrics(
    height = DetailDefaultPillMetrics.height,
    minWidth = DetailDefaultPillMetrics.minWidth,
    horizontalPadding = DetailDefaultPillMetrics.horizontalPadding,
    textSize = 7.sp,
    iconSpacing = DetailDefaultPillMetrics.iconSpacing,
    focusedScale = DetailDefaultPillMetrics.focusedScale
)

private val DetailCommentHeaderTitleFontSize = 10.sp
private val DetailCommentHeaderMetaFontSize = 6.5.sp
private val DetailCommentHintFontSize = 6.sp
private val DetailCommentAuthorFontSize = 14.sp
private val DetailCommentBodyFontSize = 14.sp
private val DetailCommentBodyLineHeight = 20.sp
private val DetailCommentMetaFontSize = 12.sp

internal fun LazyListScope.detailCommentsSection(
    commentsState: DetailCommentsState,
    focusCoordinator: DetailFocusCoordinator? = null,
    onSortSelected: (DetailCommentSortMode) -> Unit,
    onRetry: () -> Unit,
    onOpenReplies: (ReplyItem) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    item(key = "comments_header") {
        DetailCommentsHeader(
            commentsState = commentsState,
            onSortSelected = onSortSelected
        )
    }

    when {
        commentsState.isLoading && commentsState.items.isEmpty() -> {
            item(key = "comments_loading") {
                DetailCommentsSkeleton()
            }
        }

        commentsState.errorMessage != null && commentsState.items.isEmpty() -> {
            item(key = "comments_error") {
                DetailCommentErrorCard(
                    message = commentsState.errorMessage,
                    onRetry = onRetry
                )
            }
        }

        commentsState.items.isEmpty() -> {
            item(key = "comments_empty") {
                DetailMessageCard(text = "暂无评论")
            }
        }

        else -> {
            items(
                items = commentsState.items,
                key = { comment -> comment.rpid }
            ) { comment ->
                val commentFocusRequester = remember(comment.rpid) { FocusRequester() }
                DisposableEffect(comment.rpid, commentFocusRequester, focusCoordinator) {
                    val registration = focusCoordinator?.registerCommentTarget(
                        rpid = comment.rpid,
                        target = object : DetailFocusTarget {
                            override fun tryRequestFocus(): Boolean {
                                return runCatching {
                                    commentFocusRequester.requestFocus()
                                }.getOrDefault(false)
                            }
                        },
                    )
                    onDispose {
                        registration?.unregister()
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailCommentCard(
                        comment = comment,
                        focusRequester = commentFocusRequester,
                        onOpenReplies = onOpenReplies
                    )
                    if (comment.rcount > 0 || comment.replies.orEmpty().isNotEmpty()) {
                        Text(
                            text = "查看 ${formatNumber(comment.rcount)} 条回复",
                            color = Color.White,
                            fontSize = DetailCommentMetaFontSize,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    item(key = "comments_footer") {
        DetailCommentPagination(
            commentsState = commentsState,
            onPrevious = onPreviousPage,
            onNext = onNextPage
        )
    }
}

@Composable
private fun DetailCommentsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DetailCardColor)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        repeat(2) { index ->
            DetailCommentSkeletonItem(isCompact = index == 1)
        }
    }
}

@Composable
private fun DetailCommentSkeletonItem(isCompact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isCompact) 0.22f else 0.28f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (isCompact) 0.16f else 0.20f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isCompact) 0.70f else 0.86f)
                .height(13.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.09f))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isCompact) 0.48f else 0.62f)
                .height(13.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.07f))
        )
    }
}

@Composable
private fun DetailCommentsHeader(
    commentsState: DetailCommentsState,
    onSortSelected: (DetailCommentSortMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "评论",
                fontSize = DetailCommentHeaderTitleFontSize,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "共 ${formatNumber(commentsState.totalCount)} 条评论",
                fontSize = DetailCommentHeaderMetaFontSize,
                color = DetailMutedTextColor
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CommentSortSwitcher(
                selectedSort = commentsState.sortMode,
                onSortSelected = onSortSelected
            )
            DetailCommentSortHint(
                selectedSort = commentsState.sortMode,
                isLoading = commentsState.isLoading && commentsState.items.isNotEmpty()
            )
        }
    }
}

@Composable
private fun CommentSortSwitcher(
    selectedSort: DetailCommentSortMode,
    onSortSelected: (DetailCommentSortMode) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 3.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailCommentSortMode.entries.forEach { sortMode ->
            DetailPillButton(
                label = sortMode.label,
                selected = selectedSort == sortMode,
                metrics = DetailCommentSortPillMetrics,
                onClick = {
                    if (selectedSort != sortMode) {
                        onSortSelected(sortMode)
                    }
                }
            )
        }
    }
}

@Composable
private fun DetailCommentSortHint(
    selectedSort: DetailCommentSortMode,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (isLoading) {
            "正在按 ${selectedSort.label} 排序"
        } else {
            "当前按 ${selectedSort.label} 排序"
        },
        fontSize = DetailCommentHintFontSize,
        color = DetailMutedTextColor,
        modifier = modifier
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailCommentCard(
    comment: ReplyItem,
    focusRequester: FocusRequester? = null,
    onOpenReplies: (ReplyItem) -> Unit = {}
) {
    val hasReplies = comment.rcount > 0 || comment.replies.orEmpty().isNotEmpty()
    val dateText = remember(comment.ctime) {
        if (comment.ctime <= 0L) "" else {
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(comment.ctime * 1000L))
        }
    }

    val cardModifier = if (focusRequester != null) {
        Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    } else {
        Modifier.fillMaxWidth()
    }

    Surface(
        onClick = {
            if (hasReplies) {
                onOpenReplies(comment)
            }
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = DetailCardColor,
            focusedContainerColor = Color(0x33FFFFFF)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(18.dp)
            )
        ),
        modifier = cardModifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = comment.member.uname,
                    color = Color.White,
                    fontSize = DetailCommentAuthorFontSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (dateText.isNotEmpty()) {
                    Text(
                        text = dateText,
                        color = DetailMutedTextColor,
                        fontSize = DetailCommentMetaFontSize
                    )
                }
            }

            Text(
                text = comment.content.message.ifBlank { "此条评论暂时没有正文内容" },
                color = Color(0xFFF0F0F0),
                fontSize = DetailCommentBodyFontSize,
                lineHeight = DetailCommentBodyLineHeight,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "点赞 ${formatNumber(comment.like)}",
                    color = DetailMutedTextColor,
                    fontSize = DetailCommentMetaFontSize
                )
                Text(
                    text = "回复 ${formatNumber(comment.rcount)}",
                    color = DetailMutedTextColor,
                    fontSize = DetailCommentMetaFontSize
                )
            }
        }
    }
}

@Composable
private fun DetailCommentPagination(
    commentsState: DetailCommentsState,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val totalPages = commentsState.totalPages.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "第 ${commentsState.currentPage} / $totalPages 页",
            fontSize = DetailCommentHeaderMetaFontSize,
            color = DetailMutedTextColor
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (commentsState.currentPage > 1) {
                DetailPillButton(
                    label = "上一页",
                    metrics = DetailCommentActionPillMetrics,
                    onClick = onPrevious
                )
            } else {
                DetailDisabledPill(
                    label = "上一页",
                    metrics = DetailCommentActionPillMetrics
                )
            }

            if (commentsState.currentPage < totalPages) {
                DetailPillButton(
                    label = "下一页",
                    metrics = DetailCommentActionPillMetrics,
                    onClick = onNext
                )
            } else {
                DetailDisabledPill(
                    label = "下一页",
                    metrics = DetailCommentActionPillMetrics
                )
            }
        }
    }
}

@Composable
private fun DetailCommentErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DetailMessageCard(text = message)
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            DetailPillButton(
                label = "重试",
                metrics = DetailCommentActionPillMetrics,
                onClick = onRetry
            )
        }
    }
}
