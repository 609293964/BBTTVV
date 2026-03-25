// 文件路径: feature/dynamic/components/ForwardedContent.kt
package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.DrawMajor
import com.android.purebilibili.data.model.response.OpusMajor

internal data class ForwardedImagePreviewState(
    val images: List<String>,
    val initialIndex: Int
)

internal fun resolveForwardedDrawPreviewState(
    draw: DrawMajor,
    clickedIndex: Int
): ForwardedImagePreviewState? {
    return resolveForwardedImagePreviewState(
        images = draw.items.map { it.src },
        clickedIndex = clickedIndex
    )
}

internal fun resolveForwardedOpusPreviewState(
    opus: OpusMajor,
    clickedIndex: Int
): ForwardedImagePreviewState? {
    return resolveForwardedImagePreviewState(
        images = opus.pics.map { it.url },
        clickedIndex = clickedIndex
    )
}

private fun resolveForwardedImagePreviewState(
    images: List<String>,
    clickedIndex: Int
): ForwardedImagePreviewState? {
    if (clickedIndex !in images.indices) return null
    if (images.isEmpty()) return null
    return ForwardedImagePreviewState(
        images = images,
        initialIndex = clickedIndex
    )
}

/**
 *  转发的原始内容
 */
@Composable
fun ForwardedContent(
    orig: DynamicItem,
    onVideoClick: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    gifImageLoader: ImageLoader
) {
    val author = orig.modules.module_author
    val content = orig.modules.module_dynamic
    var previewState by remember { mutableStateOf<ForwardedImagePreviewState?>(null) }
    var previewSourceRect by remember { mutableStateOf<Rect?>(null) }
    val previewTextContent = remember(author?.name, content?.desc?.text, content?.major?.opus?.summary?.text) {
        val bodyText = content?.desc?.text.takeUnless { it.isNullOrBlank() }
            ?: content?.major?.opus?.summary?.text.orEmpty()
        ImagePreviewTextContent(
            headline = author?.name.orEmpty(),
            body = bodyText
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) // 主题自适应背景
            .padding(12.dp)
    ) {
        // 原作者
        if (author != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "@${author.name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary, // 主题自适应颜色
                    modifier = Modifier.clickable { onUserClick(author.mid) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    author.pub_time,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // 原文字内容 - 使用 RichTextContent 支持表情
        content?.desc?.let { desc ->
            if (desc.text.isNotEmpty()) {
                RichTextContent(
                    desc = desc,
                    onUserClick = onUserClick
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        // 原视频
        content?.major?.archive?.let { archive ->
            val playableBvid = resolveArchivePlayableBvid(archive)
            VideoCardSmall(
                archive = archive,
                publishTs = author?.pub_ts ?: 0L,
                onClick = { playableBvid?.let(onVideoClick) }
            )
        }
        
        // 原图片
        content?.major?.draw?.let { draw ->
            DrawGridV2(
                items = draw.items.take(4),
                gifImageLoader = gifImageLoader,
                onImageClick = { index, rect ->
                    val state = resolveForwardedDrawPreviewState(draw, index) ?: return@DrawGridV2
                    previewState = state
                    previewSourceRect = rect
                }
            )
        }
        
        //  [新增] 原 Opus 图文动态
        content?.major?.opus?.let { opus ->
            // 显示文字摘要 (如果 desc 为空)
            if (content.desc?.text.isNullOrEmpty()) {
                opus.summary?.let { summary ->
                    if (summary.text.isNotEmpty()) {
                        RichTextContent(
                            desc = DynamicDesc(
                                text = summary.text,
                                rich_text_nodes = summary.rich_text_nodes
                            ),
                            onUserClick = onUserClick
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
            // 显示图片
            if (opus.pics.isNotEmpty()) {
                val drawItems = opus.pics.take(4).map { pic ->
                    com.android.purebilibili.data.model.response.DrawItem(
                        src = pic.url,
                        width = pic.width,
                        height = pic.height
                    )
                }
                DrawGridV2(
                    items = drawItems,
                    gifImageLoader = gifImageLoader,
                    onImageClick = { index, rect ->
                        val state = resolveForwardedOpusPreviewState(opus, index) ?: return@DrawGridV2
                        previewState = state
                        previewSourceRect = rect
                    }
                )
            }
        }
    }

    previewState?.let { state ->
        ImagePreviewDialog(
            images = state.images,
            initialIndex = state.initialIndex,
            sourceRect = previewSourceRect,
            textContent = previewTextContent,
            onDismiss = {
                previewState = null
                previewSourceRect = null
            }
        )
    }
}
