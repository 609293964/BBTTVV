package com.bbttvv.app.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.data.model.response.ReplyItem
import com.bbttvv.app.ui.components.CommentImageViewer
import com.bbttvv.app.ui.components.CommentImageViewerState
import com.bbttvv.app.ui.components.CommentCardShortConfirmAction
import com.bbttvv.app.ui.components.CommentPictureThumbnailRow
import com.bbttvv.app.ui.components.CommentPictureUiModel
import com.bbttvv.app.ui.components.commentPictureConfirmModifier
import com.bbttvv.app.ui.components.createCommentImageViewerState
import com.bbttvv.app.ui.components.resolveCommentCardShortConfirmAction
import com.bbttvv.app.ui.components.toCommentPictureUiModels
import com.bbttvv.app.ui.theme.LocalIsLightTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommentRepliesScreen(
    bvid: String,
    aid: Long,
    rootRpid: Long,
    rootReply: ReplyItem?,
    onBack: () -> Unit
) {
    val viewModel: CommentRepliesViewModel = viewModel()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val isLightTheme = LocalIsLightTheme.current
    val pageBackgroundColor = if (isLightTheme) Color(0xFFF4F6F8) else Color(0xFF141414)
    val listState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val commentFocusRequesters = remember(bvid, rootRpid) {
        LinkedHashMap<String, FocusRequester>()
    }
    var commentImageViewerState by remember(bvid, rootRpid) {
        mutableStateOf<CommentImageViewerState?>(null)
    }
    var pendingPictureReturnKey by remember(bvid, rootRpid) {
        mutableStateOf<String?>(null)
    }

    fun openPictures(
        sourceKey: String,
        pictures: List<CommentPictureUiModel>,
        suppressInitialConfirmKeyUp: Boolean,
    ) {
        commentImageViewerState = createCommentImageViewerState(
            pictures = pictures,
            sourceKey = sourceKey,
            suppressInitialConfirmKeyUp = suppressInitialConfirmKeyUp,
        )
    }

    LaunchedEffect(bvid, aid, rootRpid, rootReply?.rpid) {
        viewModel.loadThread(
            aid = aid,
            rootRpid = rootRpid,
            rootReply = rootReply
        )
    }
    LaunchedEffect(rootRpid, uiState.currentPage, uiState.items) {
        commentImageViewerState = null
        pendingPictureReturnKey = null
    }
    LaunchedEffect(commentImageViewerState, pendingPictureReturnKey) {
        if (commentImageViewerState != null) return@LaunchedEffect
        val returnKey = pendingPictureReturnKey ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { commentFocusRequesters[returnKey]?.requestFocus() }
        pendingPictureReturnKey = null
    }
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 56.dp,
                top = 48.dp,
                end = 48.dp,
                bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(key = "header") {
                CommentRepliesHeader(
                    replyCount = uiState.totalCount,
                    onBack = onBack
                )
            }

            item(key = "root_comment") {
                val comment = uiState.rootComment ?: ReplyItem(rpid = rootRpid, oid = aid)
                val sourceKey = "root:${comment.rpid}"
                val focusRequester = remember(sourceKey) { FocusRequester() }
                DisposableEffect(sourceKey, focusRequester) {
                    commentFocusRequesters[sourceKey] = focusRequester
                    onDispose {
                        if (commentFocusRequesters[sourceKey] === focusRequester) {
                            commentFocusRequesters.remove(sourceKey)
                        }
                    }
                }
                CommentThreadRootCard(
                    comment = comment,
                    focusRequester = focusRequester,
                    onOpenPictures = { pictures, suppressInitialKeyUp ->
                        openPictures(sourceKey, pictures, suppressInitialKeyUp)
                    },
                )
            }

            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    item(key = "loading") {
                        DetailMessageCard(text = "正在加载回复...")
                    }
                }

                uiState.errorMessage != null && uiState.items.isEmpty() -> {
                    item(key = "error") {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            DetailMessageCard(text = uiState.errorMessage)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                DetailPillButton(label = "重试", onClick = {
                                    viewModel.goToPage(uiState.currentPage)
                                })
                            }
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    item(key = "empty") {
                        DetailMessageCard(text = "暂无回复")
                    }
                }

                else -> {
                    items(
                        items = uiState.items,
                        key = { reply -> reply.rpid }
                    ) { reply ->
                        val sourceKey = "reply:${reply.rpid}"
                        val focusRequester = remember(sourceKey) { FocusRequester() }
                        DisposableEffect(sourceKey, focusRequester) {
                            commentFocusRequesters[sourceKey] = focusRequester
                            onDispose {
                                if (commentFocusRequesters[sourceKey] === focusRequester) {
                                    commentFocusRequesters.remove(sourceKey)
                                }
                            }
                        }
                        CommentReplyCard(
                            reply = reply,
                            focusRequester = focusRequester,
                            onOpenPictures = { pictures, suppressInitialKeyUp ->
                                openPictures(sourceKey, pictures, suppressInitialKeyUp)
                            },
                        )
                    }
                }
            }

            item(key = "footer") {
                CommentRepliesPagination(
                    currentPage = uiState.currentPage,
                    totalPages = uiState.totalPages,
                    onPrevious = { viewModel.goToPage(uiState.currentPage - 1) },
                    onNext = { viewModel.goToPage(uiState.currentPage + 1) }
                )
            }
        }
        commentImageViewerState?.let { viewerState ->
            CommentImageViewer(
                state = viewerState,
                onStateChanged = { commentImageViewerState = it },
                onDismissRequest = {
                    pendingPictureReturnKey = viewerState.sourceKey
                    commentImageViewerState = null
                },
            )
        }
    }
}

@Composable
private fun CommentRepliesHeader(
    replyCount: Int,
    onBack: () -> Unit
) {
    val primaryTextColor = commentRepliesPrimaryTextColor()
    val mutedTextColor = commentRepliesMutedTextColor()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DetailPillButton(
            label = "返回",
            onClick = onBack
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "评论回复",
                color = primaryTextColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "评论回复",
                color = mutedTextColor,
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CommentThreadRootCard(
    comment: ReplyItem,
    focusRequester: FocusRequester,
    onOpenPictures: (List<CommentPictureUiModel>, Boolean) -> Unit,
) {
    val cardColor = commentRepliesCardColor()
    val primaryTextColor = commentRepliesPrimaryTextColor()
    val mutedTextColor = commentRepliesMutedTextColor()
    val pictures = remember(comment.content.pictures) {
        comment.content.pictures.toCommentPictureUiModels()
    }

    Surface(
        onClick = {
            when (resolveCommentCardShortConfirmAction(
                hasReplies = false,
                hasPictures = pictures.isNotEmpty(),
                replyNavigationEnabled = false,
            )) {
                CommentCardShortConfirmAction.OpenPictures -> onOpenPictures(pictures, false)
                CommentCardShortConfirmAction.OpenReplies,
                CommentCardShortConfirmAction.None,
                -> Unit
            }
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = cardColor,
            focusedContainerColor = cardColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .then(
                commentPictureConfirmModifier(
                    hasPictures = pictures.isNotEmpty(),
                    onOpenPicturesFromLongPress = {
                        onOpenPictures(pictures, true)
                    },
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CommentAuthorRow(comment = comment)
            Text(
                text = comment.content.message.ifBlank { "评论内容加载中" },
                color = primaryTextColor,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
            CommentPictureThumbnailRow(pictures = pictures)
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "点赞 ${formatNumber(comment.like)}",
                    color = mutedTextColor,
                    fontSize = 12.sp
                )
                Text(
                    text = "回复 ${formatNumber(comment.rcount)}",
                    color = mutedTextColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CommentReplyCard(
    reply: ReplyItem,
    focusRequester: FocusRequester,
    onOpenPictures: (List<CommentPictureUiModel>, Boolean) -> Unit,
) {
    val cardColor = commentRepliesCardColor()
    val focusedCardColor = commentRepliesFocusedCardColor()
    val primaryTextColor = commentRepliesPrimaryTextColor()
    val mutedTextColor = commentRepliesMutedTextColor()
    val focusedBorderColor = commentRepliesFocusedBorderColor()
    val pictures = remember(reply.content.pictures) {
        reply.content.pictures.toCommentPictureUiModels()
    }

    Surface(
        onClick = {
            when (resolveCommentCardShortConfirmAction(
                hasReplies = false,
                hasPictures = pictures.isNotEmpty(),
                replyNavigationEnabled = false,
            )) {
                CommentCardShortConfirmAction.OpenPictures -> onOpenPictures(pictures, false)
                CommentCardShortConfirmAction.OpenReplies,
                CommentCardShortConfirmAction.None,
                -> Unit
            }
        },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = cardColor,
            focusedContainerColor = focusedCardColor
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, focusedBorderColor),
                shape = RoundedCornerShape(18.dp)
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .then(
                commentPictureConfirmModifier(
                    hasPictures = pictures.isNotEmpty(),
                    onOpenPicturesFromLongPress = {
                        onOpenPictures(pictures, true)
                    },
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CommentAuthorRow(comment = reply)
            Text(
                text = reply.content.message.ifBlank { "此条回复暂时没有正文内容" },
                color = primaryTextColor,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            CommentPictureThumbnailRow(pictures = pictures)
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "点赞 ${formatNumber(reply.like)}",
                    color = mutedTextColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CommentAuthorRow(comment: ReplyItem) {
    val primaryTextColor = commentRepliesPrimaryTextColor()
    val mutedTextColor = commentRepliesMutedTextColor()
    val dateText = remember(comment.ctime) {
        if (comment.ctime <= 0L) {
            ""
        } else {
            SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA)
                .format(Date(comment.ctime * 1000L))
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = comment.member.uname.ifBlank { "认证用户" },
                color = primaryTextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (dateText.isNotEmpty()) {
                Text(
                    text = dateText,
                    color = mutedTextColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CommentRepliesPagination(
    currentPage: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val mutedTextColor = commentRepliesMutedTextColor()
    val safeTotalPages = totalPages.coerceAtLeast(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "第 $currentPage / $safeTotalPages 页",
            fontSize = 13.sp,
            color = mutedTextColor
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 1) {
                DetailPillButton(label = "上一页", onClick = onPrevious)
            } else {
                DetailDisabledPill(label = "上一页")
            }

            if (currentPage < safeTotalPages) {
                DetailPillButton(label = "下一页", onClick = onNext)
            } else {
                DetailDisabledPill(label = "上一页")
            }
        }
    }
}

@Composable
private fun commentRepliesCardColor(): Color {
    return if (LocalIsLightTheme.current) Color.White else DetailCardColor
}

@Composable
private fun commentRepliesFocusedCardColor(): Color {
    return if (LocalIsLightTheme.current) Color.White else Color(0x33FFFFFF)
}

@Composable
private fun commentRepliesPrimaryTextColor(): Color {
    return if (LocalIsLightTheme.current) Color(0xFF18191C) else Color.White
}

@Composable
private fun commentRepliesMutedTextColor(): Color {
    return if (LocalIsLightTheme.current) Color(0xFF61666D) else DetailMutedTextColor
}

@Composable
private fun commentRepliesFocusedBorderColor(): Color {
    return if (LocalIsLightTheme.current) DetailAccentColor else Color.White
}
