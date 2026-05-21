@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
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
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentSortMode
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import com.bbttvv.app.ui.theme.LocalIsLightTheme

@Composable
internal fun PlayerCommentsPanel(
    uiState: PlayerCommentsUiState,
    totalCommentCount: Int,
    primaryFocusRequester: FocusRequester,
    onToggleSort: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenThread: (ReplyItem) -> Unit,
    onBackFromThread: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isViewingThread = uiState.isViewingThread
    val listItems = if (isViewingThread) uiState.threadItems else uiState.items
    val isLoading = if (isViewingThread) uiState.isThreadLoading else uiState.isLoading
    val isAppending = if (isViewingThread) uiState.isThreadAppending else uiState.isAppending
    val errorMessage = if (isViewingThread) uiState.threadErrorMessage else uiState.errorMessage
    val totalCount = if (isViewingThread) uiState.threadTotalCount else totalCommentCount
    val hasMore = if (isViewingThread) uiState.threadHasMore else uiState.hasMore
    val listState = rememberLazyListState()
    val commentFocusCoordinator = remember(isViewingThread, uiState.sortMode) {
        PlayerCommentFocusCoordinator()
    }
    var lastFocusedCommentKey by remember(isViewingThread, uiState.sortMode) { mutableStateOf<String?>(null) }
    var pendingAppendFocusKey by remember(isViewingThread, uiState.sortMode) { mutableStateOf<String?>(null) }
    val currentItemKeys = remember(isViewingThread, listItems) {
        listItems.mapIndexed { index, reply ->
            playerCommentItemKey(isViewingThread = isViewingThread, reply = reply, index = index)
        }.toSet()
    }

    LaunchedEffect(isViewingThread, uiState.sortMode) {
        runCatching { listState.scrollToItem(0) }
    }

    LaunchedEffect(isAppending, currentItemKeys, isViewingThread, uiState.sortMode) {
        if (isAppending) {
            pendingAppendFocusKey = lastFocusedCommentKey?.takeIf { it in currentItemKeys }
            return@LaunchedEffect
        }

        val focusKey = pendingAppendFocusKey?.takeIf { it in currentItemKeys }
        pendingAppendFocusKey = null
        if (focusKey == null) return@LaunchedEffect

        commentFocusCoordinator.requestFocusKey(focusKey)
        repeat(5) { attempt ->
            withFrameNanos { }
            if (commentFocusCoordinator.drainPendingFocus()) return@LaunchedEffect
            if (attempt < 4) {
                delay(40L)
            }
        }
    }

    LaunchedEffect(listState, isViewingThread, uiState.sortMode, hasMore, isAppending) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex to totalItems
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalItems) ->
                if (!hasMore || isAppending || totalItems <= 0) return@collect
                if (lastVisibleIndex < totalItems - 2) return@collect
                onLoadMore()
            }
    }

    val isLightTheme = LocalIsLightTheme.current
    val panelBgColor = if (isLightTheme) Color.White.copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.65f)
    val panelBorderColor = if (isLightTheme) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)
    val mainTextColor = if (isLightTheme) Color(0xFF18191C) else Color.White
    val subTextColor = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.72f)

    Column(
        modifier = modifier
            .fillMaxWidth(0.4f)
            .widthIn(min = 460.dp, max = 560.dp)
            .focusGroup()
            .focusProperties {
                onExit = { cancelFocusChange() }
            }
            .clip(RoundedCornerShape(28.dp))
            .background(panelBgColor)
            .border(1.dp, panelBorderColor, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isViewingThread) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "认证回复",
                        color = mainTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "共 ${formatCount(totalCount)} 条回复",
                        color = subTextColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                PlayerCommentPillButton(
                    label = "返回评论",
                    onClick = onBackFromThread,
                    focusRequester = primaryFocusRequester,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "视频评论",
                        color = mainTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "共 ${formatCount(totalCount)} 条评论",
                        color = subTextColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                PlayerCommentPillButton(
                    label = "切换到${nextCommentSortMode(uiState.sortMode).label}",
                    onClick = onToggleSort,
                    focusRequester = primaryFocusRequester,
                )
            }
        }

        uiState.activeThreadRoot?.takeIf { isViewingThread }?.let { rootReply ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "主评论",
                    color = subTextColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
                PlayerCommentCard(
                    reply = rootReply,
                    onOpenThread = {},
                    showReplyAction = false,
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 2.dp, bottom = 18.dp),
        ) {
            when {
                isLoading && listItems.isEmpty() -> {
                    item(key = "loading") {
                        PlayerCommentMessageCard(
                            text = if (isViewingThread) "正在加载回复..." else "正在加载评论...",
                        )
                    }
                }

                !errorMessage.isNullOrBlank() && listItems.isEmpty() -> {
                    item(key = "error") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PlayerCommentMessageCard(text = errorMessage)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                PlayerCommentPillButton(label = "重试", onClick = onRetry)
                            }
                        }
                    }
                }

                listItems.isEmpty() -> {
                    item(key = "empty") {
                        PlayerCommentMessageCard(
                            text = if (isViewingThread) "暂无回复" else "暂无评论",
                        )
                    }
                }

                else -> {
                    itemsIndexed(
                        items = listItems,
                        key = { index, reply ->
                            playerCommentItemKey(isViewingThread = isViewingThread, reply = reply, index = index)
                        },
                    ) { index, reply ->
                        val commentKey = playerCommentItemKey(
                            isViewingThread = isViewingThread,
                            reply = reply,
                            index = index,
                        )
                        val commentFocusRequester = remember(commentKey) { FocusRequester() }
                        DisposableEffect(commentKey, commentFocusRequester, commentFocusCoordinator) {
                            val registration = commentFocusCoordinator.registerCommentTarget(
                                key = commentKey,
                                target = object : PlayerFocusTarget {
                                    override fun tryRequestFocus(): Boolean {
                                        return runCatching {
                                            commentFocusRequester.requestFocus()
                                        }.getOrDefault(false)
                                    }
                                },
                            )
                            onDispose {
                                registration.unregister()
                            }
                        }
                        PlayerCommentCard(
                            reply = reply,
                            onOpenThread = onOpenThread,
                            showReplyAction = !isViewingThread,
                            focusRequester = commentFocusRequester,
                            onFocused = { lastFocusedCommentKey = commentKey },
                        )
                    }
                }
            }

            if (listItems.isNotEmpty() && hasMore && !isAppending) {
                item(key = "load_more_button") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        PlayerCommentPillButton(
                            label = if (isViewingThread) "加载更多回复" else "加载更多评论",
                            onClick = onLoadMore,
                        )
                    }
                }
            }

            if (listItems.isNotEmpty() && isAppending) {
                item(key = "append_loading") {
                    PlayerCommentFooterHint(text = "正在加载更多...")
                }
            } else if (listItems.isNotEmpty() && !hasMore) {
                item(key = "end_of_list") {
                    PlayerCommentFooterHint(text = "已经到底了")
                }
            }
        }
    }
}

private fun playerCommentItemKey(
    isViewingThread: Boolean,
    reply: ReplyItem,
    index: Int,
): String {
    val scope = if (isViewingThread) "thread" else "main"
    if (reply.rpid > 0L) return "$scope:${reply.rpid}"
    return "$scope:fallback:$index:${reply.ctime}:${reply.member.mid}:${reply.content.message.hashCode()}"
}

private fun nextCommentSortMode(sortMode: PlayerCommentSortMode): PlayerCommentSortMode {
    return when (sortMode) {
        PlayerCommentSortMode.Hot -> PlayerCommentSortMode.Time
        PlayerCommentSortMode.Time -> PlayerCommentSortMode.Hot
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerCommentPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val isLightTheme = LocalIsLightTheme.current
    var isFocused by remember { mutableStateOf(false) }
    val composedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }
    
    val containerColor = when {
        isFocused -> if (isLightTheme) Color(0xFFFB7299) else Color.White.copy(alpha = 0.95f)
        selected -> if (isLightTheme) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.24f)
        else -> if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f)
    }
    
    val borderStrokeColor = when {
        isFocused -> if (isLightTheme) Color(0xFFFB7299) else Color.White
        selected -> if (isLightTheme) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.62f)
        else -> if (isLightTheme) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.24f)
    }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = if (isLightTheme) Color(0xFFFB7299) else Color.White.copy(alpha = 0.95f),
            pressedContainerColor = if (isLightTheme) Color(0xFFE25E83) else Color.White.copy(alpha = 0.82f),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(
                    if (isFocused) 2.dp else 1.dp,
                    borderStrokeColor,
                ),
                shape = RoundedCornerShape(999.dp),
            ),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, if (isLightTheme) Color(0xFFFB7299) else Color.White),
                shape = RoundedCornerShape(999.dp),
            ),
        ),
        modifier = composedModifier
            .onFocusChanged { focusState -> isFocused = focusState.hasFocus },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val textColor = when {
                isFocused -> Color.White
                selected -> if (isLightTheme) Color(0xFFFB7299) else Color.White
                else -> if (isLightTheme) Color(0xFF18191C) else Color.White
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerCommentCard(
    reply: ReplyItem,
    onOpenThread: (ReplyItem) -> Unit,
    showReplyAction: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    val isLightTheme = LocalIsLightTheme.current
    val hasReplies = reply.rcount > 0 || reply.replies.orEmpty().isNotEmpty()
    val dateText = remember(reply.ctime) { formatCommentDate(reply.ctime) }
    var isFocused by remember { mutableStateOf(false) }
    val composedModifier = if (focusRequester != null) {
        modifier.focusRequester(focusRequester)
    } else {
        modifier
    }
    
    val containerColor = when {
        isFocused -> if (isLightTheme) Color.White else Color.White.copy(alpha = 0.24f)
        else -> if (isLightTheme) Color.Black.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.14f)
    }
    
    val borderStroke = if (isFocused) {
        androidx.compose.foundation.BorderStroke(2.dp, if (isLightTheme) Color(0xFFFB7299) else Color.White)
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.18f))
    }

    Surface(
        onClick = {
            if (showReplyAction && hasReplies) {
                onOpenThread(reply)
            }
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = containerColor,
            focusedContainerColor = if (isLightTheme) Color.White else Color.White.copy(alpha = 0.24f),
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = borderStroke,
                shape = RoundedCornerShape(20.dp),
            ),
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, if (isLightTheme) Color(0xFFFB7299) else Color.White),
                shape = RoundedCornerShape(20.dp),
            ),
        ),
        modifier = composedModifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                isFocused = focusState.hasFocus
                if (focusState.hasFocus) {
                    onFocused()
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = reply.member.uname.ifBlank { "评论用户" },
                    color = if (isLightTheme) {
                        if (isFocused) Color(0xFFFB7299) else Color(0xFF18191C)
                    } else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (dateText.isNotEmpty()) {
                    Text(
                        text = dateText,
                        color = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.62f),
                        fontSize = 10.sp,
                    )
                }
            }
            Text(
                text = reply.content.message.ifBlank { "此条评论暂时没有正文内容" },
                color = if (isLightTheme) Color(0xFF18191C) else Color.White.copy(alpha = 0.92f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = if (showReplyAction) 6 else 8,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "点赞 ${formatCount(reply.like)}",
                        color = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.62f),
                        fontSize = 10.sp,
                    )
                    if (showReplyAction) {
                        Text(
                            text = "回复 ${formatCount(reply.rcount)}",
                            color = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.62f),
                            fontSize = 10.sp,
                        )
                    }
                }
                if (showReplyAction && hasReplies) {
                    Text(
                        text = "查看回复",
                        color = if (isLightTheme) {
                            if (isFocused) Color(0xFFFB7299) else Color(0xFF1D5AA5)
                        } else {
                            if (isFocused) Color.White else Color(0xFFDDEBFF)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCommentMessageCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = LocalIsLightTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isLightTheme) Color.Black.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.12f))
            .border(1.dp, if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            color = if (isLightTheme) Color(0xFF18191C) else Color.White.copy(alpha = 0.82f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun PlayerCommentFooterHint(text: String) {
    val isLightTheme = LocalIsLightTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.72f),
            fontSize = 11.sp,
        )
    }
}

private fun formatCommentDate(timestampSec: Long): String {
    if (timestampSec <= 0L) return ""
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(timestampSec * 1000L))
}

