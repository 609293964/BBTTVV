// 文件路径: feature/dynamic/components/VideoCards.kt
package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.purebilibili.data.model.response.ArchiveMajor

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.feature.dynamic.DynamicVideoCardLayoutMode
import com.android.purebilibili.feature.dynamic.resolveDynamicVideoCardLayoutMode
import com.android.purebilibili.feature.video.ui.section.resolveDynamicPublishTimeRowText
import com.android.purebilibili.feature.video.ui.section.shouldEmphasizePrecisePublishTime

/**
 *  大尺寸视频卡片
 *  🎨 [优化] 更大圆角、渐变遮罩、更好的信息展示
 */
@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun VideoCardLarge(
    archive: ArchiveMajor,
    onClick: () -> Unit,
    publishTs: Long = 0L,
    // [新增] 合集相关参数
    isCollection: Boolean = false,
    collectionTitle: String = "",
    // [新增] 共享元素过渡动画支持
    transitionName: String? = null
) {
    val context = LocalContext.current
    val coverUrl = remember(archive.cover) {
        val raw = archive.cover.trim()
        when {
            raw.startsWith("https://") -> raw
            raw.startsWith("http://") -> raw.replace("http://", "https://")
            raw.startsWith("//") -> "https:$raw"
            raw.isNotEmpty() -> "https://$raw"
            else -> ""
        }
    }
    
    // 获取共享元素动画的作用域
    var modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onClick)
        
    // [新增] 应用共享元素过渡动画
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    if (transitionName != null && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            modifier = modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = transitionName),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    }
    
    BoxWithConstraints(modifier = modifier) {
        when (resolveDynamicVideoCardLayoutMode(containerWidthDp = maxWidth.value.toInt())) {
            DynamicVideoCardLayoutMode.VERTICAL -> {
                VideoCardLargeVerticalContent(
                    archive = archive,
                    coverUrl = coverUrl,
                    publishTs = publishTs,
                    isCollection = isCollection,
                    collectionTitle = collectionTitle,
                    context = context
                )
            }
            DynamicVideoCardLayoutMode.HORIZONTAL -> {
                VideoCardLargeHorizontalContent(
                    archive = archive,
                    coverUrl = coverUrl,
                    publishTs = publishTs,
                    isCollection = isCollection,
                    collectionTitle = collectionTitle,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun VideoCardLargeVerticalContent(
    archive: ArchiveMajor,
    coverUrl: String,
    publishTs: Long,
    isCollection: Boolean,
    collectionTitle: String,
    context: android.content.Context
) {
    Column {
        VideoCardLargeCover(
            archive = archive,
            coverUrl = coverUrl,
            context = context,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            durationTextSize = 12.sp,
            isCollection = isCollection
        )

        Spacer(modifier = Modifier.height(10.dp))
        VideoCardLargeInfo(
            archive = archive,
            publishTs = publishTs,
            isCollection = isCollection,
            collectionTitle = collectionTitle,
            titleMaxLines = 2
        )
    }
}

@Composable
private fun VideoCardLargeHorizontalContent(
    archive: ArchiveMajor,
    coverUrl: String,
    publishTs: Long,
    isCollection: Boolean,
    collectionTitle: String,
    context: android.content.Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VideoCardLargeCover(
            archive = archive,
            coverUrl = coverUrl,
            context = context,
            modifier = Modifier
                .width(248.dp)
                .fillMaxHeight(),
            durationTextSize = 11.sp,
            isCollection = false
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isCollection) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "合集",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isCollection && collectionTitle.isNotEmpty()) {
                    Text(
                        text = collectionTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "更新：${archive.title}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 19.sp
                    )
                } else {
                    Text(
                        text = archive.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            VideoCardLargeStats(archive = archive)
            val publishTimeRowText = remember(publishTs, archive.title) {
                resolveDynamicPublishTimeRowText(
                    publishTs = publishTs,
                    title = archive.title
                )
            }
            if (publishTimeRowText.isNotBlank()) {
                Text(
                    text = publishTimeRowText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoCardLargeCover(
    archive: ArchiveMajor,
    coverUrl: String,
    context: android.content.Context,
    modifier: Modifier,
    durationTextSize: androidx.compose.ui.unit.TextUnit,
    isCollection: Boolean
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (coverUrl.isNotEmpty()) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(coverUrl)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.BottomCenter)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f)
                        )
                    )
                )
        )

        if (isCollection) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "合集",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = archive.duration_text,
                fontSize = durationTextSize,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun VideoCardLargeInfo(
    archive: ArchiveMajor,
    publishTs: Long,
    isCollection: Boolean,
    collectionTitle: String,
    titleMaxLines: Int
) {
    val publishTimeRowText = remember(publishTs, archive.title) {
        resolveDynamicPublishTimeRowText(
            publishTs = publishTs,
            title = archive.title
        )
    }
    val emphasizePublishTime = remember(archive.title) {
        shouldEmphasizePrecisePublishTime(partitionName = "", title = archive.title)
    }

    if (isCollection && collectionTitle.isNotEmpty()) {
        Text(
            text = collectionTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "更新：${archive.title}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        Text(
            text = archive.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = titleMaxLines,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    if (publishTimeRowText.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        if (emphasizePublishTime) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = publishTimeRowText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        } else {
            Text(
                text = publishTimeRowText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    VideoCardLargeStats(archive = archive)
}

@Composable
private fun VideoCardLargeStats(
    archive: ArchiveMajor
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            CupertinoIcons.Default.Play,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            archive.stat.play,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            CupertinoIcons.Default.Message,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            archive.stat.danmaku,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 *  小尺寸视频卡片（用于转发）
 */
@Composable
fun VideoCardSmall(
    archive: ArchiveMajor,
    publishTs: Long = 0L,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val coverUrl = remember(archive.cover) {
        val raw = archive.cover.trim()
        when {
            raw.startsWith("https://") -> raw
            raw.startsWith("http://") -> raw.replace("http://", "https://")
            raw.startsWith("//") -> "https:$raw"
            raw.isNotEmpty() -> "https://$raw"
            else -> ""
        }
    }
    
    val publishTimeRowText = remember(publishTs, archive.title) {
        resolveDynamicPublishTimeRowText(
            publishTs = publishTs,
            title = archive.title
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面
        Box(
            modifier = Modifier
                .width(110.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
        ) {
            if (coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(coverUrl)
                        .addHeader("Referer", "https://www.bilibili.com/")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(0.7f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(archive.duration_text, fontSize = 10.sp, color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                archive.title,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (publishTimeRowText.isNotBlank()) {
                Text(
                    text = publishTimeRowText,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
            }
        }
    }
}
