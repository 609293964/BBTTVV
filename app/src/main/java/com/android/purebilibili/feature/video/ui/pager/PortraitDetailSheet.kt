package com.android.purebilibili.feature.video.ui.pager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.core.util.FormatUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.feature.video.ui.section.resolvePublishTimeRowText
import com.android.purebilibili.feature.video.ui.section.shouldEmphasizePrecisePublishTime
import kotlinx.coroutines.launch

/**
 * 竖屏视频详情页 (简介)
 * 使用自定义 Box 叠加实现，避免 ModalBottomSheet 的 WindowInsets 问题
 */
@Composable
fun PortraitDetailSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    info: ViewInfo?,
    recommendationTitle: String = "推荐视频",
    recommendations: List<RelatedVideo> = emptyList(),
    onRecommendationClick: (String) -> Unit = {},
    onAuthorClick: (Long) -> Unit = {},
    danmakuEnabled: Boolean = true,
    onDanmakuToggle: () -> Unit = {}
) {
    if (!visible && info == null) return

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    // 拦截返回键
    BackHandler(enabled = visible) {
        onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 1. 遮罩层 (Scrim)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        // 2. 内容层 (Sheet Content)
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300)
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.75f) // max height 75%
                    .clickable(enabled = false) {}, // 拦截点击防止穿透
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "简介",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDanmakuToggle) {
                                Text(
                                    text = if (danmakuEnabled) "弹幕开" else "弹幕关",
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Content
                    if (info == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // 标题
                            val context = LocalContext.current
                            val blockedUpRepository = remember { com.android.purebilibili.data.repository.BlockedUpRepository(context) }
                            val isBlocked by blockedUpRepository.isBlocked(info.owner.mid).collectAsState(initial = false)
                            val scope = rememberCoroutineScope()
                            var showBlockConfirmDialog by remember { mutableStateOf(false) }
                            
                            if (showBlockConfirmDialog) {
                                com.android.purebilibili.core.ui.IOSAlertDialog(
                                    onDismissRequest = { showBlockConfirmDialog = false },
                                    title = { Text(if (isBlocked) "解除屏蔽" else "屏蔽 UP 主") },
                                    text = { Text(if (isBlocked) "确定要解除对 ${info.owner.name} 的屏蔽吗？" else "屏蔽后，将不再推荐该 UP 主的视频。\n确定要屏蔽 ${info.owner.name} 吗？") },
                                    confirmButton = {
                                        com.android.purebilibili.core.ui.IOSDialogAction(
                                            onClick = {
                                                scope.launch {
                                                    if (isBlocked) {
                                                        blockedUpRepository.unblockUp(info.owner.mid)
                                                        android.widget.Toast.makeText(context, "已解除屏蔽", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        blockedUpRepository.blockUp(info.owner.mid, info.owner.name, info.owner.face)
                                                        android.widget.Toast.makeText(context, "已屏蔽该 UP 主", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    showBlockConfirmDialog = false
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = if (isBlocked) "解除屏蔽" else "屏蔽",
                                                color = if (!isBlocked) Color.Red else com.android.purebilibili.core.theme.iOSBlue
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        com.android.purebilibili.core.ui.IOSDialogAction(onClick = { showBlockConfirmDialog = false }) { Text("取消") }
                                    }
                                )
                            }

                            Text(
                                text = info.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            val publishTimeRowText = remember(info.pubdate, info.tname, info.title) {
                                resolvePublishTimeRowText(
                                    pubdate = info.pubdate,
                                    partitionName = info.tname,
                                    title = info.title
                                )
                            }
                            val emphasizePublishTime = remember(info.tname, info.title) {
                                shouldEmphasizePrecisePublishTime(
                                    partitionName = info.tname,
                                    title = info.title
                                )
                            }

                            if (publishTimeRowText.isNotBlank()) {
                                if (emphasizePublishTime) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    ) {
                                        Text(
                                            text = publishTimeRowText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = publishTimeRowText,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.74f),
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )
                                }
                            }
                            
                            // 基础信息 (UP主 / 时间 / 播放量)
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = info.owner.face,
                                    contentDescription = "${info.owner.name} 头像",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray.copy(alpha = 0.2f))
                                        .clickable {
                                            if (info.owner.mid > 0L) {
                                                onAuthorClick(info.owner.mid)
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = info.owner.name,
                                        fontSize = 13.sp,
                                        color = if (isBlocked) Color.Red else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable { showBlockConfirmDialog = true }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${FormatUtils.formatStat(info.stat.view.toLong())}观看 · ${FormatUtils.formatStat(info.stat.danmaku.toLong())}弹幕",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // VID Info
                            Text(
                                text = info.bvid,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // 简介正文
                            Text(
                                text = info.desc.ifEmpty { "暂无简介" },
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            if (recommendations.isNotEmpty()) {
                                Text(
                                    text = recommendationTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )

                                recommendations.take(12).forEach { video ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onRecommendationClick(video.bvid) }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = FormatUtils.fixImageUrl(video.pic),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(width = 96.dp, height = 54.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Gray.copy(alpha = 0.2f)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = video.title,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${video.owner.name} · ${FormatUtils.formatStat(video.stat.view.toLong())}播放",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // 标签 (Flow Layout usually, simplified here for now)
                            // TODO: If tags available in ViewInfo, display them.
                            // Currently ViewInfo usually has minimal info, might need separate tags fetch or check ViewInfo structure.
                        }
                    }
                }
            }
        }
    }
}
