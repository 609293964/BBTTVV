// 文件路径: feature/dynamic/components/DynamicCard.kt
package com.android.purebilibili.feature.dynamic.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.common.CopySelectionDialog
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOffIcon
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.feature.dynamic.resolveDynamicCardContentPadding
import com.android.purebilibili.feature.dynamic.resolveDynamicCardOuterPadding
import com.android.purebilibili.data.model.response.DynamicStatModule
import com.android.purebilibili.data.model.response.DynamicType

/**
 *  动态卡片V2 - 官方风格
 */
@Composable
fun DynamicCardV2(
    item: DynamicItem,
    onVideoClick: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    onLiveClick: (roomId: Long, title: String, uname: String) -> Unit = { _, _, _ -> },
    onDynamicDetailClick: ((dynamicId: String) -> Unit)? = null,
    gifImageLoader: ImageLoader,
    //  [新增] 评论/转发/点赞回调
    onCommentClick: (dynamicId: String) -> Unit = {},
    onRepostClick: (dynamicId: String) -> Unit = {},
    onLikeClick: (dynamicId: String) -> Unit = {},
    isLiked: Boolean = false
) {
    val author = item.modules.module_author
    val content = item.modules.module_dynamic
    val stat = item.modules.module_stat
    val type = DynamicType.fromApiValue(item.type)
    val cardClickAction = remember(item) { resolveDynamicCardPrimaryAction(item) }
    val isPrimaryClickEnabled = remember(cardClickAction, onDynamicDetailClick) {
        when (cardClickAction) {
            is DynamicCardPrimaryAction.OpenDynamicDetail -> onDynamicDetailClick != null
            DynamicCardPrimaryAction.None -> false
            else -> true
        }
    }

    //  [优化] 卡片式设计：圆角 + 微阴影 + 更好的间距
    //  [优化] 使用 GlassCard 替换 Surface
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = resolveDynamicCardOuterPadding(), vertical = 6.dp)
            .clickable(enabled = isPrimaryClickEnabled) {
                dispatchDynamicCardPrimaryAction(
                    action = cardClickAction,
                    onVideoClick = onVideoClick,
                    onDynamicDetailClick = onDynamicDetailClick,
                    onUserClick = onUserClick,
                    onLiveClick = onLiveClick
                )
            },
        backgroundColor = MaterialTheme.colorScheme.surface, // 纯白背景，减少割裂感
        shape = RoundedCornerShape(20.dp) // 更大的圆角
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(resolveDynamicCardContentPadding())  // 卡片内部间距
    ) {
        //  [新增] 更多菜单状态
        var showMoreMenu by remember { mutableStateOf(false) }
        val context = LocalContext.current
        
        //  用户头部（头像 + 名称 + 时间 + 更多）
        if (author != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 头像
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(author.face.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it })
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(enabled = author.mid > 0) {
                            dispatchDynamicCardPrimaryAction(
                                action = DynamicCardPrimaryAction.OpenUser(author.mid),
                                onVideoClick = onVideoClick,
                                onDynamicDetailClick = onDynamicDetailClick,
                                onUserClick = onUserClick,
                                onLiveClick = onLiveClick
                            )
                        },
                    contentScale = ContentScale.Crop
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        author.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = if (author.vip?.status == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        author.pub_time,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f)
                    )
                }
                
                //  [修复] 更多按钮 + 下拉菜单
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            rememberAppMoreIcon(),
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                        )
                    }
                    
                    // 下拉菜单 - 自适应背景
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer) // 使用 surfaceContainer 获得微略不同的背景
                    ) {
                        // 复制链接
                        DropdownMenuItem(
                            text = { Text("复制链接", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { 
                                Icon(
                                    CupertinoIcons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            onClick = {
                                showMoreMenu = false
                                // 复制动态链接到剪贴板
                                val dynamicUrl = "https://t.bilibili.com/${item.id_str}"
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("动态链接", dynamicUrl))
                                android.widget.Toast.makeText(context, "已复制链接", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        
                        // 不感兴趣
                        DropdownMenuItem(
                            text = { Text("不感兴趣", color = MaterialTheme.colorScheme.onSurface) },
                            leadingIcon = { 
                                Icon(
                                    rememberAppVisibilityOffIcon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                ) 
                            },
                            onClick = {
                                showMoreMenu = false
                                android.widget.Toast.makeText(context, "已标记为不感兴趣", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        //  动态内容文字（支持@高亮）
        content?.desc?.let { desc ->
            if (desc.text.isNotEmpty()) {
                RichTextContent(
                    desc = desc,
                    onUserClick = onUserClick
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
        
        //  视频类型动态 - 大图预览
        content?.major?.archive?.let { archive ->
            val playableBvid = resolveArchivePlayableBvid(archive)
            VideoCardLarge(
                archive = archive,
                publishTs = author?.pub_ts ?: 0L,
                onClick = {
                    playableBvid?.let(onVideoClick)
                        ?: onDynamicDetailClick?.invoke(item.id_str)
                },
                transitionName = "video-${archive.bvid}" // [新增] 共享元素过渡名称
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        //  图片类型动态（支持GIF + 点击预览）
        content?.major?.draw?.let { draw ->
            var selectedImageIndex by remember { mutableIntStateOf(-1) }
            var sourceRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
            val drawPreviewText = remember(author?.name, content.desc?.text) {
                ImagePreviewTextContent(
                    headline = author?.name.orEmpty(),
                    body = content.desc?.text.orEmpty()
                )
            }
            
            DrawGridV2(
                items = draw.items,
                gifImageLoader = gifImageLoader,
                onImageClick = { index, rect ->
                    val action = resolveDynamicCardMediaAction(item, index)
                    if (action is DynamicCardMediaAction.PreviewImages) {
                        selectedImageIndex = action.initialIndex
                        sourceRect = rect
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            // 全屏图片预览
            if (selectedImageIndex >= 0) {
                ImagePreviewDialog(
                    images = draw.items.map { it.src },
                    initialIndex = selectedImageIndex,
                    sourceRect = sourceRect,  //  [新增] 传递源位置用于展开动画
                    textContent = drawPreviewText,
                    onDismiss = { selectedImageIndex = -1 }
                )
            }
        }
        
        //  [新增] Opus 图文动态 (新版格式)
        content?.major?.opus?.let { opus ->
            var selectedImageIndex by remember { mutableIntStateOf(-1) }
            var sourceRect by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
            val opusPreviewText = remember(author?.name, content.desc?.text, opus.summary?.text) {
                val body = content.desc?.text.takeUnless { it.isNullOrBlank() }
                    ?: opus.summary?.text.orEmpty()
                ImagePreviewTextContent(
                    headline = author?.name.orEmpty(),
                    body = body
                )
            }
            
            // 显示标题 (如果有)
            opus.title?.let { title ->
                if (title.isNotEmpty()) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            
            // 显示文字摘要 (如果有且 desc 为空)
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
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            
            // 显示图片 (转换为 DrawItem 格式复用现有组件)
            if (opus.pics.isNotEmpty()) {
                val drawItems = opus.pics.map { pic ->
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
                        val action = resolveDynamicCardMediaAction(item, index)
                        if (action is DynamicCardMediaAction.PreviewImages) {
                            selectedImageIndex = action.initialIndex
                            sourceRect = rect
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // 全屏图片预览
                if (selectedImageIndex >= 0) {
                    ImagePreviewDialog(
                        images = opus.pics.map { it.url },
                        initialIndex = selectedImageIndex,
                        sourceRect = sourceRect,  //  [新增] 传递源位置用于展开动画
                        textContent = opusPreviewText,
                        onDismiss = { selectedImageIndex = -1 }
                    )
                }
            }
        }
        
        //  [新增] 合集/剧集动态
        content?.major?.ugc_season?.let { season ->
            val seasonArchive = resolveUgcSeasonArchiveFallback(season)
            val playableBvid = resolveUgcSeasonPlayableBvid(season)
            if (seasonArchive != null) {
                VideoCardLarge(
                    archive = seasonArchive,
                    publishTs = author?.pub_ts ?: 0L,
                    onClick = {
                        playableBvid?.let(onVideoClick)
                            ?: onDynamicDetailClick?.invoke(item.id_str)
                    },
                    isCollection = true,
                    collectionTitle = season.title,
                    transitionName = "video-${seasonArchive.bvid}" // [新增] 共享元素过渡名称
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Text(
                     "合集：${season.title}", 
                     fontWeight = FontWeight.Bold,
                     color = MaterialTheme.colorScheme.primary
                )
                 Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        //  直播推荐动态
        content?.major?.live_rcmd?.let { liveRcmd ->
            LiveCard(
                liveRcmd = liveRcmd,
                onLiveClick = { roomId, title, uname ->
                    dispatchDynamicCardPrimaryAction(
                        action = DynamicCardPrimaryAction.OpenLive(roomId, title, uname),
                        onVideoClick = onVideoClick,
                        onDynamicDetailClick = onDynamicDetailClick,
                        onUserClick = onUserClick,
                        onLiveClick = onLiveClick
                    )
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        //  转发动态 - 嵌套显示原始内容
        if (type == DynamicType.FORWARD && item.orig != null) {
            ForwardedContent(
                orig = item.orig,
                onVideoClick = onVideoClick,
                onUserClick = onUserClick,
                gifImageLoader = gifImageLoader
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        //  [修复] 底部操作栏：转发、评论、点赞 - 始终显示
        val statModule = stat ?: DynamicStatModule()  // 使用默认值避免按钮消失
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 转发按钮
            ActionButton(
                icon = io.github.alexzhirkevich.cupertino.icons.CupertinoIcons.Default.ArrowTurnUpRight,
                count = statModule.forward.count,
                label = "转发",
                onClick = { onRepostClick(item.id_str) }
            )
            
            // 评论按钮
            ActionButton(
                icon = io.github.alexzhirkevich.cupertino.icons.CupertinoIcons.Default.Message,
                count = statModule.comment.count,
                label = "评论",
                onClick = { onCommentClick(item.id_str) }
            )
            
            // 点赞按钮
            ActionButton(
                icon = if (isLiked) io.github.alexzhirkevich.cupertino.icons.CupertinoIcons.Filled.HandThumbsup
                       else io.github.alexzhirkevich.cupertino.icons.CupertinoIcons.Default.HandThumbsup,
                count = statModule.like.count,
                label = "点赞",
                isActive = isLiked,
                onClick = { onLikeClick(item.id_str) }
            )
        }
    }
    }  //  关闭 Surface
}

/**
 *  富文本内容（支持表情、@提及、话题高亮）
 *  解析 API 返回的 rich_text_nodes 来正确渲染表情图片
 */
@Composable
fun RichTextContent(
    desc: DynamicDesc,
    onUserClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    // 收集所有表情节点以创建 InlineContent
    // 支持两种类型格式: RICH_TEXT_NODE_TYPE_EMOJI 和 EMOJI
    val emojiNodes = remember(desc.rich_text_nodes) {
        desc.rich_text_nodes
            .filter { it.type.endsWith("EMOJI") && it.emoji != null }
            .associateBy({ it.text }, { it.emoji!!.icon_url })
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val annotatedText = remember(desc, primaryColor, textColor) {
        buildDynamicRichTextAnnotatedString(
            desc = desc,
            primaryColor = primaryColor,
            textColor = textColor
        )
    }
    
    // 创建表情的 InlineContent 映射
    val inlineContent = emojiNodes.mapValues { (_, iconUrl) ->
        InlineTextContent(
            Placeholder(
                width = 1.4.em,
                height = 1.4.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
            )
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(iconUrl.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it })
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    val copyText = remember(desc.rich_text_nodes, desc.text) {
        val richNodeText = desc.rich_text_nodes.joinToString(separator = "") { it.text }
        richNodeText.ifBlank { desc.text }.trim()
    }
    var showCopySelectionDialog by remember(copyText) { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    
    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = textColor,
        onTextLayout = { textLayoutResult = it },
        modifier = Modifier.pointerInput(copyText, annotatedText) {
            detectTapGestures(
                onLongPress = {
                    if (copyText.isNotEmpty()) {
                        showCopySelectionDialog = true
                    }
                },
                onTap = { offset ->
                    val layoutResult = textLayoutResult ?: return@detectTapGestures
                    val position = layoutResult.getOffsetForPosition(offset)
                    val annotation = annotatedText.getStringAnnotations(
                        tag = DYNAMIC_RICH_TEXT_URL_TAG,
                        start = maxOf(0, position - 1),
                        end = minOf(annotatedText.length, position + 1)
                    ).firstOrNull() ?: return@detectTapGestures

                    scope.launch {
                        when (resolveDynamicRichTextOpenMode(annotation.item)) {
                            DynamicRichTextOpenMode.IN_APP -> {
                                val inAppIntent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(annotation.item)
                                ).setPackage(context.packageName)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                val launchedInApp = runCatching {
                                    context.startActivity(inAppIntent)
                                }.isSuccess
                                if (!launchedInApp) {
                                    openDynamicRichTextLinkExternally(context, annotation.item, uriHandler)
                                }
                            }

                            DynamicRichTextOpenMode.EXTERNAL -> {
                                openDynamicRichTextLinkExternally(context, annotation.item, uriHandler)
                            }

                            null -> Unit
                        }
                    }
                }
            )
        }
    )
    if (showCopySelectionDialog) {
        CopySelectionDialog(
            text = copyText,
            title = "选择动态内容",
            onDismiss = { showCopySelectionDialog = false }
        )
    }
}

private fun openDynamicRichTextLinkExternally(
    context: android.content.Context,
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    val externalIntent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url)
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

    val packageManager = context.packageManager
    val externalPackage = packageManager.queryIntentActivities(
        externalIntent,
        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
    ).firstOrNull { it.activityInfo?.packageName != context.packageName }
        ?.activityInfo
        ?.packageName

    val launchedExternally = if (!externalPackage.isNullOrBlank()) {
        runCatching {
            context.startActivity(externalIntent.setPackage(externalPackage))
        }.isSuccess
    } else {
        false
    }

    if (!launchedExternally) {
        runCatching { uriHandler.openUri(url) }
    }
}

/**
 *  紧凑列表卡片 - 单行显示
 */
@Composable
fun DynamicCardCompact(
    item: DynamicItem,
    onVideoClick: (String) -> Unit,
    onUserClick: (Long) -> Unit
) {
    val author = item.modules.module_author
    val content = item.modules.module_dynamic
    val stat = item.modules.module_stat
    
    // 获取内容预览文本
    val previewText = content?.desc?.text?.take(50) 
        ?: content?.major?.archive?.title 
        ?: "动态"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // 如果有视频则跳转视频
                content?.major?.archive
                    ?.let(::resolveArchivePlayableBvid)
                    ?.let(onVideoClick)
                    ?: author?.let { onUserClick(it.mid) }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),  //  优化间距
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        if (author != null) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(author.face.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it })
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = author.mid > 0) { onUserClick(author.mid) },
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
        }
        
        // 内容区
        Column(modifier = Modifier.weight(1f)) {
            // 用户名 + 时间
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    author?.name ?: "",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    author?.pub_time ?: "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 内容预览
            Text(
                previewText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        
        // 封面缩略图（如果有视频）
        content?.major?.archive?.let { archive ->
            Spacer(modifier = Modifier.width(12.dp))
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(archive.cover.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it })
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 80.dp, height = 50.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
