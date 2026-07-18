@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.bbttvv.app.feature.video.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentSortMode
import com.bbttvv.app.feature.video.viewmodel.PlayerCommentsUiState
import com.bbttvv.app.ui.components.rememberSizedImageModel
import com.bbttvv.app.ui.theme.LocalIsLightTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val PLAYER_COMMENTS_SIDEBAR_WIDTH_FRACTION = 0.30f

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
    val panelBgColor = if (isLightTheme) Color(0xFFF8F9FB) else Color(0xF5141518)
    val dividerColor = if (isLightTheme) Color.Black.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.16f)
    val mainTextColor = if (isLightTheme) Color(0xFF18191C) else Color.White
    val subTextColor = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.72f)

    Row(
        modifier = modifier
            .fillMaxWidth(PLAYER_COMMENTS_SIDEBAR_WIDTH_FRACTION)
            .fillMaxHeight()
            .focusGroup()
            .focusProperties {
                onExit = { cancelFocusChange() }
            }
            .background(panelBgColor),
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = if (isViewingThread) "评论回复" else "视频评论",
                        color = mainTextColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = playerCommentCountLabel(totalCount),
                        color = subTextColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                PlayerCommentPillButton(
                    label = if (isViewingThread) "返回" else playerCommentSortLabel(uiState.sortMode),
                    onClick = if (isViewingThread) onBackFromThread else onToggleSort,
                    focusRequester = primaryFocusRequester,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor),
            )

            uiState.activeThreadRoot?.takeIf { isViewingThread }?.let { rootReply ->
                Text(
                    text = "主评论",
                    color = subTextColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp),
                )
                PlayerCommentListItem(
                    reply = rootReply,
                    onOpenThread = {},
                    showReplyAction = false,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                when {
                    isLoading && listItems.isEmpty() -> {
                        item(key = "loading") {
                            PlayerCommentMessage(
                                text = if (isViewingThread) "正在加载回复..." else "正在加载评论...",
                            )
                        }
                    }

                    !errorMessage.isNullOrBlank() && listItems.isEmpty() -> {
                        item(key = "error") {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PlayerCommentMessage(text = errorMessage, modifier = Modifier.padding(0.dp))
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
                            PlayerCommentMessage(
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
                            PlayerCommentListItem(
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
                                .padding(top = 14.dp),
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

internal fun playerCommentCountLabel(count: Int): String = "${formatCount(count)} 条"

internal fun playerCommentSortLabel(sortMode: PlayerCommentSortMode): String = "按${sortMode.label}"

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
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            val textColor = when {
                isFocused -> if (isLightTheme) Color.White else Color(0xFF111111)
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
private fun PlayerCommentListItem(
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
    val locationText = reply.replyControl?.location.orEmpty()
    val contextText = remember(dateText, locationText) {
        listOf(dateText, locationText).filter(String::isNotBlank).joinToString(" · ")
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusModifier = if (focusRequester != null) {
        Modifier.focusRequester(focusRequester)
    } else {
        Modifier
    }
    val accentColor = Color(0xFFFB7299)
    val dividerColor = if (isLightTheme) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f)
    val flatBorder = Border(
        border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
        shape = RectangleShape,
    )

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            onClick = {
                if (showReplyAction && hasReplies) {
                    onOpenThread(reply)
                }
            },
            shape = ClickableSurfaceDefaults.shape(RectangleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = if (isLightTheme) {
                    Color.Black.copy(alpha = 0.045f)
                } else {
                    Color.White.copy(alpha = 0.09f)
                },
            ),
            border = ClickableSurfaceDefaults.border(
                border = flatBorder,
                focusedBorder = flatBorder,
            ),
            modifier = focusModifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    isFocused = focusState.hasFocus
                    if (focusState.hasFocus) {
                        onFocused()
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                PlayerCommentAvatar(
                    avatarUrl = reply.member.avatar,
                    username = reply.member.uname,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = reply.member.uname.ifBlank { "评论用户" },
                            color = if (isLightTheme) {
                                if (isFocused) accentColor else Color(0xFF18191C)
                            } else Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        reply.member.levelInfo.currentLevel.takeIf { it > 0 }?.let { level ->
                            Text(
                                text = "LV$level",
                                color = if (isLightTheme) Color(0xFF8A5A22) else Color(0xFFF0C98A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (contextText.isNotEmpty()) {
                        Text(
                            text = contextText,
                            color = if (isLightTheme) Color(0xFF7A7F87) else Color.White.copy(alpha = 0.56f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = reply.content.message.ifBlank { "此条评论暂时没有正文内容" },
                        color = if (isLightTheme) Color(0xFF24262A) else Color.White.copy(alpha = 0.90f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        maxLines = if (showReplyAction) 6 else 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "赞 ${formatCount(reply.like)}",
                                color = if (isLightTheme) Color(0xFF686D75) else Color.White.copy(alpha = 0.60f),
                                fontSize = 11.sp,
                            )
                            if (showReplyAction) {
                                Text(
                                    text = "回复 ${formatCount(reply.rcount)}",
                                    color = if (isLightTheme) Color(0xFF686D75) else Color.White.copy(alpha = 0.60f),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        if (showReplyAction && hasReplies) {
                            Text(
                                text = "查看回复 ›",
                                color = if (isFocused) accentColor else if (isLightTheme) Color(0xFF3467A8) else Color(0xFFDDEBFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(start = if (isFocused) 0.dp else 18.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor),
            )
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accentColor),
                )
            }
        }
    }
}

@Composable
private fun PlayerCommentAvatar(
    avatarUrl: String,
    username: String,
) {
    val isLightTheme = LocalIsLightTheme.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isLightTheme) Color(0xFFE7E9EE) else Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = rememberSizedImageModel(
                    url = avatarUrl,
                    widthPx = 72,
                    heightPx = 72,
                ),
                contentDescription = username.ifBlank { "评论用户头像" },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = username.trim().firstOrNull()?.toString().orEmpty().ifBlank { "评" },
                color = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlayerCommentMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    val isLightTheme = LocalIsLightTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Text(
            text = text,
            color = if (isLightTheme) Color(0xFF61666D) else Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
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
            fontSize = 13.sp,
        )
    }
}

private fun formatCommentDate(timestampSec: Long): String {
    if (timestampSec <= 0L) return ""
    return SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA).format(Date(timestampSec * 1000L))
}
