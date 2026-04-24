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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val listState = rememberSaveable(saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }

    LaunchedEffect(bvid, aid, rootRpid, rootReply?.rpid) {
        viewModel.loadThread(
            aid = aid,
            rootRpid = rootRpid,
            rootReply = rootReply
        )
    }
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
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
                CommentThreadRootCard(
                    comment = uiState.rootComment ?: ReplyItem(rpid = rootRpid, oid = aid)
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
                        DetailMessageCard(text = "鏆傛棤鍥炲")
                    }
                }

                else -> {
                    items(
                        items = uiState.items,
                        key = { reply -> reply.rpid }
                    ) { reply ->
                        CommentReplyCard(reply = reply)
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
    }
}

@Composable
private fun CommentRepliesHeader(
    replyCount: Int,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DetailPillButton(
            label = "返回",
            onClick = onBack
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "评论回复",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "评论回复",
                color = DetailMutedTextColor,
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CommentThreadRootCard(comment: ReplyItem) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = DetailCardColor,
            focusedContainerColor = DetailCardColor
        ),
        modifier = Modifier.fillMaxWidth()
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
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "点赞 ${formatNumber(comment.like)}",
                    color = DetailMutedTextColor,
                    fontSize = 12.sp
                )
                Text(
                    text = "回复 ${formatNumber(comment.rcount)}",
                    color = DetailMutedTextColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CommentReplyCard(reply: ReplyItem) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
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
        modifier = Modifier.fillMaxWidth()
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
                color = Color.White,
                fontSize = 15.sp,
                lineHeight = 24.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "点赞 ${formatNumber(reply.like)}",
                    color = DetailMutedTextColor,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CommentAuthorRow(comment: ReplyItem) {
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
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (dateText.isNotEmpty()) {
                Text(
                    text = dateText,
                    color = DetailMutedTextColor,
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
            color = DetailMutedTextColor
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

