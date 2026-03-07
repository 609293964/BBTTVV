package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.imageLoader
//  已改用 MaterialTheme.colorScheme.primary
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.ReplyFansDetail
import com.android.purebilibili.data.model.response.ReplyCardLabel
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import com.android.purebilibili.data.model.response.ReplyPicture
import com.android.purebilibili.data.model.response.ReplySailingCardBg
import com.android.purebilibili.data.model.response.ReplySailingFan
import com.android.purebilibili.data.model.response.ReplyUpAction
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextPlacement
import androidx.compose.ui.layout.ContentScale
import com.android.purebilibili.core.ui.common.CopySelectionDialog
import com.android.purebilibili.core.ui.common.copyOnLongPress
import androidx.compose.foundation.text.selection.SelectionContainer
import java.text.SimpleDateFormat
import java.util.*

private val EMOTE_TOKEN_PATTERN = """\[(.*?)\]""".toRegex()
internal val COMMENT_TIMESTAMP_PATTERN =
    """(?<!\d)(\d{1,2})\s*[:：]\s*(\d{2})(?:\s*[:：]\s*(\d{2}))?(?!\d)""".toRegex()

internal fun parseCommentTimestampSeconds(match: MatchResult): Long? {
    val first = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val second = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
    val third = match.groupValues.getOrNull(3)?.toIntOrNull()
    return if (third != null) {
        first * 3600L + second * 60L + third
    } else {
        first * 60L + second
    }
}

internal fun collectRenderableEmoteKeys(
    text: String,
    emoteMap: Map<String, String>
): Set<String> {
    if (text.isEmpty() || emoteMap.isEmpty()) return emptySet()
    return EMOTE_TOKEN_PATTERN.findAll(text)
        .map { it.value }
        .filter { emoteMap.containsKey(it) }
        .toSet()
}

internal fun shouldEnableRichCommentSelection(
    hasRenderableEmotes: Boolean,
    hasInteractiveAnnotations: Boolean
): Boolean {
    return !hasRenderableEmotes && !hasInteractiveAnnotations
}

internal fun shouldShowReplyAncillaryDecorations(
    lightweightMode: Boolean
): Boolean = !lightweightMode

internal fun shouldShowReplySubPreview(
    hideSubPreview: Boolean,
    lightweightMode: Boolean
): Boolean = !hideSubPreview && !lightweightMode

internal fun resolveReplySpecialLabelText(
    cardLabels: List<ReplyCardLabel>?,
    showUpFlag: Boolean,
    upAction: ReplyUpAction?
): String? {
    val serverLabel = cardLabels.orEmpty()
        .asSequence()
        .map { it.textContent.trim() }
        .firstOrNull { it.isNotEmpty() }
    if (!serverLabel.isNullOrEmpty()) return serverLabel
    return if (showUpFlag && upAction?.like == true) "UP主觉得很赞" else null
}

internal fun resolveReplyDisplayLikeCount(
    baseLikeCount: Int,
    initialAction: Int,
    isLiked: Boolean
): Int {
    return when {
        isLiked && initialAction != 1 -> baseLikeCount + 1
        !isLiked && initialAction == 1 -> baseLikeCount - 1
        else -> baseLikeCount
    }
}

internal fun resolveReplyLocationText(location: String?): String? {
    if (location.isNullOrBlank()) return null
    val cleanLocation = location
        .removePrefix("IP属地：")
        .removePrefix("IP属地")
        .trim()
    return if (cleanLocation.isNotEmpty()) "IP归属地：$cleanLocation" else null
}

internal fun buildSubReplyPreviewPrefix(
    userName: String,
    isUpComment: Boolean
): List<String> {
    return buildList {
        add(userName)
        if (isUpComment) {
            add(" ")
            add("[UP]")
        }
        add(": ")
    }
}

internal fun resolveReplyItemContentType(item: ReplyItem): String {
    return when {
        !item.cardLabels.isNullOrEmpty() -> "reply_labeled"
        !item.content.pictures.isNullOrEmpty() -> "reply_media"
        !item.replies.isNullOrEmpty() || item.rcount > 0 -> "reply_thread"
        else -> "reply_plain"
    }
}

internal data class FanGroupTagVisual(
    val fanNumber: String,
    val cardBgImageUrl: String?,
    val fanColorHex: String = ""
)

internal fun resolveSailingFan(cardBgs: List<ReplySailingCardBg>): ReplySailingFan? {
    return cardBgs.asSequence()
        .mapNotNull { it.fan }
        .firstOrNull { it.numDesc.isNotBlank() || it.number > 0 }
}

internal fun resolveSailingDecorationImage(cardBgs: List<ReplySailingCardBg>): String? {
    return cardBgs.asSequence()
        .map { it.image }
        .firstOrNull { it.isNotBlank() }
}

internal fun resolveFanGroupTagVisual(
    fan: ReplySailingFan?,
    cardBgImage: String?,
    fanColorHex: String? = null
): FanGroupTagVisual? {
    fan ?: return null
    val fanNumber = fan.numDesc.ifBlank {
        if (fan.number > 0) fan.number.toString().padStart(6, '0') else ""
    }
    if (fanNumber.isBlank()) return null
    return FanGroupTagVisual(
        fanNumber = fanNumber,
        cardBgImageUrl = cardBgImage?.takeIf { it.isNotBlank() },
        fanColorHex = fanColorHex?.takeIf { it.isNotBlank() } ?: fan.color
    )
}

internal fun resolveFanGroupVisualFromMemberAndSailing(
    member: ReplyMember,
    cardBgs: List<ReplySailingCardBg>
): FanGroupTagVisual? {
    val sailingFan = resolveSailingFan(cardBgs)
    val legacyNumber = member.garbCardNumber.trim()
    val fanNumber = when {
        legacyNumber.isNotBlank() -> legacyNumber
        sailingFan != null -> {
            sailingFan.numDesc.ifBlank {
                if (sailingFan.number > 0) sailingFan.number.toString().padStart(6, '0') else ""
            }
        }
        else -> ""
    }
    if (fanNumber.isBlank()) return null

    val image = when {
        member.garbCardImage.isNotBlank() -> member.garbCardImage
        member.garbCardImageWithFocus.isNotBlank() -> member.garbCardImageWithFocus
        else -> resolveSailingDecorationImage(cardBgs).orEmpty()
    }
    val fanColorHex = member.garbCardFanColor
        .takeIf { it.isNotBlank() }
        ?: sailingFan?.color
        ?: ""

    return FanGroupTagVisual(
        fanNumber = fanNumber,
        cardBgImageUrl = image.takeIf { it.isNotBlank() },
        fanColorHex = fanColorHex
    )
}

internal fun resolveReplyPreviewTextContent(item: ReplyItem): ImagePreviewTextContent {
    return ImagePreviewTextContent(
        headline = item.member.uname,
        body = item.content.message,
        placement = ImagePreviewTextPlacement.TOP_BAR
    )
}

//  优化后的颜色常量 (使用 MaterialTheme 替代硬编码)
// private val SubReplyBgColor = Color(0xFFF7F8FA)  // OLD
// private val TextSecondaryColor = Color(0xFF9499A0)  // OLD
// private val TextTertiaryColor = Color(0xFFB2B7BF)   // OLD

@Composable
fun ReplyHeader(count: Int) {
    Row(
        modifier = Modifier
        .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "评论",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = FormatUtils.formatStat(count.toLong()),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ReplyItemView(
    item: ReplyItem,
    upMid: Long = 0,
    showUpFlag: Boolean = false,
    isPinned: Boolean = false,
    emoteMap: Map<String, String> = emptyMap(),
    lightweightMode: Boolean = false,
    onClick: () -> Unit,
    onSubClick: (ReplyItem) -> Unit,
    onTimestampClick: ((Long) -> Unit)? = null,
    onImagePreview: ((List<String>, Int, Rect?, ImagePreviewTextContent?) -> Unit)? = null,
    isLiked: Boolean = item.action == 1,
    onLikeClick: (() -> Unit)? = null,
    onReplyClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    location: String? = item.replyControl?.location,
    onUrlClick: ((String) -> Unit)? = null,
    hideSubPreview: Boolean = false,
    onAvatarClick: (String) -> Unit
) {
    val isUpComment = upMid > 0 && item.mid == upMid
    val showAncillaryDecorations = shouldShowReplyAncillaryDecorations(lightweightMode)
    val showSubPreview = shouldShowReplySubPreview(
        hideSubPreview = hideSubPreview,
        lightweightMode = lightweightMode
    )
    val localEmoteMap = remember(item.content.emote, emoteMap) {
        val inlineEmotes = item.content.emote.orEmpty()
        if (inlineEmotes.isEmpty()) {
            emoteMap
        } else {
            buildMap(emoteMap.size + inlineEmotes.size) {
                putAll(emoteMap)
                inlineEmotes.forEach { (key, value) -> put(key, value.url) }
            }
        }
    }
    val displayLocation = remember(location) {
        resolveReplyLocationText(location)
    }
    val specialLabelText = remember(item.cardLabels, showUpFlag, item.upAction, showAncillaryDecorations) {
        if (!showAncillaryDecorations) return@remember null
        resolveReplySpecialLabelText(
            cardLabels = item.cardLabels,
            showUpFlag = showUpFlag,
            upAction = item.upAction
        )
    }
    val displayLikeCount = remember(item.like, item.action, isLiked) {
        resolveReplyDisplayLikeCount(
            baseLikeCount = item.like,
            initialAction = item.action,
            isLiked = isLiked
        )
    }
    val fansDetail = if (showAncillaryDecorations) {
        item.member.fansDetail?.takeIf { it.medalName.isNotBlank() && it.level > 0 }
    } else {
        null
    }
    val nameplateImage = if (showAncillaryDecorations) {
        item.member.nameplate?.imageSmall?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    val sailingCardBgs = if (showAncillaryDecorations) {
        listOfNotNull(
            item.member.userSailing?.cardBg,
            item.member.userSailing?.cardBgWithFocus,
            item.member.userSailingV2?.cardBg,
            item.member.userSailingV2?.cardBgWithFocus
        )
    } else {
        emptyList()
    }
    val fanGroupVisual = if (showAncillaryDecorations) {
        resolveFanGroupVisualFromMemberAndSailing(
            member = item.member,
            cardBgs = sailingCardBgs
        )
    } else {
        null
    }
    val piliPlusDecoration = fanGroupVisual
        ?.takeIf { !it.cardBgImageUrl.isNullOrBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface) // White background for iOS list feel
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            // Avatar
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(FormatUtils.fixImageUrl(item.member.avatar))
                    .crossfade(!lightweightMode)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onAvatarClick(item.member.mid) }
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // User Info Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = if (piliPlusDecoration != null) 88.dp else 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.member.uname,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.member.vip?.vipStatus == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .copyOnLongPress(item.member.uname, "用户名")
                            )

                            if (isUpComment) {
                                UpTag()
                            }

                            if (isPinned) {
                                PinnedTag()
                            }

                            if (item.member.levelInfo.currentLevel > 0) {
                                LevelTag(level = item.member.levelInfo.currentLevel)
                            }

                            if (fansDetail != null) {
                                FansMedalTag(detail = fansDetail)
                            }

                            if (!nameplateImage.isNullOrBlank()) {
                                NameplateTag(imageUrl = nameplateImage)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Content
                    RichCommentText(
                        text = item.content.message,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        emoteMap = localEmoteMap,
                        onTimestampClick = onTimestampClick,
                        onUrlClick = onUrlClick
                    )

                    // Images
                    if (!item.content.pictures.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CommentPictures(
                            pictures = item.content.pictures,
                            onImageClick = { images, index, rect ->
                                onImagePreview?.invoke(
                                    images,
                                    index,
                                    rect,
                                    resolveReplyPreviewTextContent(item)
                                )
                            }
                        )
                    }

                    if (!specialLabelText.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ReplySpecialLabelChip(text = specialLabelText)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Footer Actions
                    Row(verticalAlignment = Alignment.CenterVertically) {
                    // Time & Location
                    Text(
                        text = buildString {
                            append(formatTime(item.ctime))
                            if (!displayLocation.isNullOrEmpty()) {
                                append(" · $displayLocation")
                            }
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Like
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(enabled = onLikeClick != null) { onLikeClick?.invoke() }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) CupertinoIcons.Filled.HandThumbsup else CupertinoIcons.Default.HandThumbsup,
                            contentDescription = "Like",
                            tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        if (displayLikeCount > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = FormatUtils.formatStat(displayLikeCount.toLong()),
                                fontSize = 12.sp,
                                color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Dislike (Placeholder)
                    Icon(
                        imageVector = CupertinoIcons.Default.MinusCircle, // Fallback Dislike
                        contentDescription = "Dislike",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { /* TODO: Dislike */ }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Reply Icon
                    Icon(
                        imageVector = CupertinoIcons.Default.BubbleLeft, // iOS Bubble icon
                        contentDescription = "Reply",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onReplyClick?.invoke() ?: onSubClick(item) }
                    )

                    // [新增] 删除按钮 (仅显示给本人)
                    if (onDeleteClick != null) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(
                            imageVector = CupertinoIcons.Default.Trash,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDeleteClick() }
                        )
                    }
                }

                // Sub-comments (Threaded view)
                if (showSubPreview && (!item.replies.isNullOrEmpty() || item.rcount > 0)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    // No background container, just cleaner list
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubClick(item) }
                    ) {
                        item.replies?.take(3)?.forEach { subReply ->
                            val subEmoteMap = remember(subReply.content.emote, emoteMap) {
                                val inlineEmotes = subReply.content.emote.orEmpty()
                                if (inlineEmotes.isEmpty()) {
                                    emoteMap
                                } else {
                                    buildMap(emoteMap.size + inlineEmotes.size) {
                                        putAll(emoteMap)
                                        inlineEmotes.forEach { (key, value) -> put(key, value.url) }
                                    }
                                }
                            }

                            val prefixTokens = remember(subReply.member.uname, upMid, subReply.mid) {
                                buildSubReplyPreviewPrefix(
                                    userName = subReply.member.uname,
                                    isUpComment = upMid > 0 && subReply.mid == upMid
                                )
                            }
                            val prefixTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            val prefixSeparatorColor = MaterialTheme.colorScheme.onSurfaceVariant
                            val upTagColor = MaterialTheme.colorScheme.primary
                            val prefix = remember(prefixTokens, prefixTextColor, prefixSeparatorColor, upTagColor) {
                                buildAnnotatedString {
                                    prefixTokens.forEach { token ->
                                        when (token) {
                                            "[UP]" -> withStyle(
                                                SpanStyle(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = upTagColor
                                                )
                                            ) {
                                                append(token)
                                            }
                                            ": " -> withStyle(
                                                SpanStyle(color = prefixSeparatorColor)
                                            ) {
                                                append(token)
                                            }
                                            else -> withStyle(
                                                SpanStyle(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = prefixTextColor
                                                )
                                            ) {
                                                append(token)
                                            }
                                        }
                                    }
                                }
                            }

                            RichCommentText(
                                text = subReply.content.message,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                emoteMap = subEmoteMap,
                                maxLines = 3,  // Increase max lines for inline text
                                onTimestampClick = onTimestampClick,
                                onUrlClick = onUrlClick,
                                prefix = prefix
                            )
                        }
                        
                        if (item.rcount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "查看全部 ${item.rcount} 条回复 >",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clickable { onSubClick(item) }
                                    .padding(vertical = 4.dp) // Increase touch target
                            )
                        }
                    }
                    }
                }

                if (piliPlusDecoration != null) {
                    PiliPlusGarbCardDecoration(
                        visual = piliPlusDecoration,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp)
                    )
                }
            }
        }
        
        // Inset Divider (starts after avatar + spacing = 16 + 40 + 12 = 68dp)
        HorizontalDivider(
            modifier = Modifier.padding(start = 68.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    }
}

/**
 *  [新增] 富文本评论组件
 * 支持：表情渲染、时间戳点击跳转
 */
@Composable
fun RichCommentText(
    text: String,
    fontSize: TextUnit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emoteMap: Map<String, String>,
    maxLines: Int = Int.MAX_VALUE,
    onTimestampClick: ((Long) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null,
    prefix: AnnotatedString? = null
) {
    val context = LocalContext.current
    val timestampColor = MaterialTheme.colorScheme.primary
    val urlColor = MaterialTheme.colorScheme.primary
    
    val renderableEmoteKeys = remember(text, emoteMap) {
        collectRenderableEmoteKeys(text, emoteMap)
    }
    
    val annotatedString = remember(
        text,
        prefix,
        renderableEmoteKeys,
        timestampColor,
        color,
        urlColor
    ) {
        buildAnnotatedString {
            // [新增] 添加前缀 (如用户名)
            if (prefix != null) {
                append(prefix)
            }

            // 高亮 "回复 @某人 :"
            val replyPattern = "^回复 @(.*?) :".toRegex()
            val replyMatch = replyPattern.find(text)
            var startIndex = 0
            if (replyMatch != null) {
                withStyle(SpanStyle(color = color, fontWeight = FontWeight.Medium)) {
                    append(replyMatch.value)
                }
                startIndex = replyMatch.range.last + 1
            }

            val remainingText = text.substring(startIndex)
            
            //  收集所有匹配（表情 + 时间戳）并按位置排序
            data class MatchInfo(val range: IntRange, val type: String, val value: String, val seconds: Long = 0)
            val allMatches = mutableListOf<MatchInfo>()
            
            // 收集表情匹配
            EMOTE_TOKEN_PATTERN.findAll(remainingText).forEach { match ->
                allMatches.add(MatchInfo(match.range, "emote", match.value))
            }
            
            // 收集时间戳匹配
            COMMENT_TIMESTAMP_PATTERN.findAll(remainingText).forEach { match ->
                val totalSeconds = parseCommentTimestampSeconds(match) ?: return@forEach
                allMatches.add(MatchInfo(match.range, "timestamp", match.value, totalSeconds))
            }
            
            // 收集 URL 匹配
            val urlPattern = """((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])""".toRegex()
            urlPattern.findAll(remainingText).forEach { match ->
                allMatches.add(MatchInfo(match.range, "url", match.value))
            }

            // 按位置排序
            allMatches.sortBy { it.range.first }
            
            var lastIndex = 0
            allMatches.forEach { matchInfo ->
                // 添加匹配之前的普通文本
                if (lastIndex < matchInfo.range.first) {
                    append(remainingText.substring(lastIndex, matchInfo.range.first))
                }
                // 避免重叠匹配（例如 URL 中的数字被识别为时间戳）
                if (matchInfo.range.first >= lastIndex) {
                    when (matchInfo.type) {
                        "emote" -> {
                            if (matchInfo.value in renderableEmoteKeys) {
                                appendInlineContent(id = matchInfo.value, alternateText = matchInfo.value)
                            } else {
                                append(matchInfo.value)
                            }
                        }
                        "timestamp" -> {
                            //  时间戳使用特殊样式并添加点击注解
                            pushStringAnnotation(tag = "TIMESTAMP", annotation = matchInfo.seconds.toString())
                            withStyle(SpanStyle(color = timestampColor, fontWeight = FontWeight.Medium)) {
                                append(matchInfo.value)
                            }
                            pop()
                        }
                        "url" -> {
                            // URL 高亮
                            pushStringAnnotation(tag = "URL", annotation = matchInfo.value)
                            withStyle(SpanStyle(color = urlColor, textDecoration = TextDecoration.Underline)) {
                                append(matchInfo.value)
                            }
                            pop()
                        }
                    }
                    lastIndex = matchInfo.range.last + 1
                }
            }
            
            // 添加剩余文本
            if (lastIndex < remainingText.length) {
                append(remainingText.substring(lastIndex))
            }
        }
    }

    val inlineContent = remember(renderableEmoteKeys, emoteMap, context) {
        renderableEmoteKeys.associateWith { key ->
            val url = emoteMap[key].orEmpty()
            InlineTextContent(
                Placeholder(width = 1.4.em, height = 1.4.em, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    val hasInteractiveAnnotations = onTimestampClick != null || onUrlClick != null
    val selectionEnabled = remember(renderableEmoteKeys, hasInteractiveAnnotations) {
        shouldEnableRichCommentSelection(
            hasRenderableEmotes = renderableEmoteKeys.isNotEmpty(),
            hasInteractiveAnnotations = hasInteractiveAnnotations
        )
    }
    val copyText = remember(text) { text.trim() }
    var showCopySelectionDialog by remember(copyText) { mutableStateOf(false) }

    val content: @Composable () -> Unit = {
        //  使用 Text + pointerInput 实现带表情的可点击文本
        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        val textModifier = when {
            hasInteractiveAnnotations -> Modifier.pointerInput(annotatedString, text) {
                detectTapGestures(
                    onLongPress = {
                        if (copyText.isNotEmpty()) {
                            showCopySelectionDialog = true
                        }
                    },
                    onTap = { offset ->
                        textLayoutResult?.let { layoutResult ->
                            val position = layoutResult.getOffsetForPosition(offset)
                            val searchStart = maxOf(0, position - 1)
                            val searchEnd = minOf(annotatedString.length, position + 1)

                            annotatedString.getStringAnnotations(
                                tag = "URL",
                                start = searchStart,
                                end = searchEnd
                            ).firstOrNull()?.let { annotation ->
                                onUrlClick?.invoke(annotation.item)
                                return@detectTapGestures
                            }

                            annotatedString.getStringAnnotations(
                                tag = "TIMESTAMP",
                                start = searchStart,
                                end = searchEnd
                            )
                                .firstOrNull()?.let { annotation ->
                                    val secondsValue = annotation.item.toLongOrNull() ?: 0L
                                    onTimestampClick?.invoke(secondsValue * 1000)
                                }
                        }
                    }
                )
            }
            !selectionEnabled -> Modifier.pointerInput(copyText) {
                detectTapGestures(
                    onLongPress = {
                        if (copyText.isNotEmpty()) {
                            showCopySelectionDialog = true
                        }
                    }
                )
            }
            else -> Modifier
        }

        Text(
            text = annotatedString,
            inlineContent = inlineContent,
            fontSize = fontSize,
            color = color,
            lineHeight = (fontSize.value * 1.5).sp,
            maxLines = maxLines,
            onTextLayout = { textLayoutResult = it },
            modifier = textModifier
        )
    }

    if (selectionEnabled) {
        SelectionContainer {
            content()
        }
    } else {
        content()
    }

    if (showCopySelectionDialog) {
        CopySelectionDialog(
            text = copyText,
            title = "选择评论内容",
            onDismiss = { showCopySelectionDialog = false }
        )
    }
}

/**
 *  [兼容] 旧版 EmojiText (保持向后兼容)
 */
@Composable
fun EmojiText(
    text: String,
    fontSize: TextUnit,
    color: Color = MaterialTheme.colorScheme.onSurface,
    emoteMap: Map<String, String>
) {
    RichCommentText(
        text = text,
        fontSize = fontSize,
        color = color,
        emoteMap = emoteMap,
        onTimestampClick = null
    )
}

//  等级标签（改为截图同款的小矩形 LV 样式）
@Composable
fun LevelTag(level: Int) {
    val badgeColor = resolveLevelBadgeColor(level)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        badgeColor.copy(alpha = 0.92f),
                        badgeColor
                    )
                )
            )
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = "LV$level",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 10.sp
        )
    }
}

@Composable
private fun FansMedalTag(detail: ReplyFansDetail) {
    val accentColor = resolveFansMedalColor(detail.level)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .border(
                width = 0.8.dp,
                color = accentColor.copy(alpha = 0.75f),
                shape = RoundedCornerShape(3.dp)
            )
            .background(accentColor.copy(alpha = 0.14f))
    ) {
        Text(
            text = detail.medalName,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = accentColor.copy(alpha = 0.95f),
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, end = 3.dp, top = 1.dp, bottom = 1.dp)
        )
        Box(
            modifier = Modifier
                .background(accentColor)
                .padding(horizontal = 3.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = detail.level.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun NameplateTag(imageUrl: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(FormatUtils.fixImageUrl(imageUrl))
            .crossfade(true)
            .build(),
        contentDescription = "Nameplate",
        modifier = Modifier
            .size(width = 20.dp, height = 12.dp)
            .clip(RoundedCornerShape(2.dp))
    )
}

@Composable
private fun PiliPlusGarbCardDecoration(
    visual: FanGroupTagVisual,
    modifier: Modifier = Modifier
) {
    val fallbackImageUrl = normalizeHttpImageUrl(visual.cardBgImageUrl)
    val primaryImageUrl = resolveDecorationImageUrl(visual.cardBgImageUrl)
    if (primaryImageUrl.isBlank()) return
    var imageUrl by remember(primaryImageUrl, fallbackImageUrl) {
        mutableStateOf(primaryImageUrl)
    }

    val textColor = parseHexColorOrNull(visual.fanColorHex) ?: Color(0xFFD2D8E2)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUrl)
                .listener(
                    onError = { _, _ ->
                        if (fallbackImageUrl.isNotBlank() && imageUrl != fallbackImageUrl) {
                            imageUrl = fallbackImageUrl
                        }
                    }
                )
                .crossfade(true)
                .build(),
            contentDescription = "Fan group decoration",
            // Garb card assets often contain huge transparent margins.
            // Keep center crop so the main emblem near center stays visible.
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .size(width = 42.dp, height = 22.dp)
                .clip(RoundedCornerShape(2.dp))
        )
        Text(
            text = "co.${visual.fanNumber}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

internal fun normalizeHttpImageUrl(url: String?): String {
    if (url.isNullOrBlank()) return ""
    val text = url.trim()
    val lower = text.lowercase(Locale.ROOT)
    val looksLikeHostPath = !text.startsWith("/") &&
        text.substringBefore('/').contains('.')
    return when {
        text.startsWith("//") -> "https:$text"
        lower.startsWith("http://") -> "https://${text.substringAfter("://")}"
        lower.startsWith("https://") -> text
        looksLikeHostPath -> "https://$text"
        else -> text
    }
}

private val IMAGE_SUFFIX_REGEX =
    Regex("""\.(jpg|jpeg|png|webp|gif|avif)(\?.*)?$""", RegexOption.IGNORE_CASE)
private val THUMBNAIL_SUFFIX_REGEX =
    Regex("""(@(\d+[a-z]_?)*)(\..*)?$""", RegexOption.IGNORE_CASE)

internal fun resolveDecorationImageUrl(url: String?): String {
    val normalized = normalizeHttpImageUrl(url)
    if (normalized.isBlank()) return ""
    if (!IMAGE_SUFFIX_REGEX.containsMatchIn(normalized)) return normalized

    return if (THUMBNAIL_SUFFIX_REGEX.containsMatchIn(normalized)) {
        normalized.replace(THUMBNAIL_SUFFIX_REGEX) { match ->
            val suffix = match.groups[3]?.value ?: ".webp"
            "${match.groups[1]?.value.orEmpty()}_1q$suffix"
        }
    } else {
        "$normalized@1q.webp"
    }
}

private fun resolveLevelBadgeColor(level: Int): Color {
    return when {
        level >= 6 -> Color(0xFFF04444)
        level >= 5 -> Color(0xFFFF7A45)
        level >= 4 -> Color(0xFFFF8B5A)
        level >= 3 -> Color(0xFFFF9C6E)
        level >= 2 -> Color(0xFFFFAE84)
        else -> Color(0xFFA7ADB8)
    }
}

private fun resolveFansMedalColor(level: Int): Color {
    return when {
        level >= 30 -> Color(0xFFE67A2B)
        level >= 20 -> Color(0xFFD9963B)
        level >= 10 -> Color(0xFFCFA657)
        else -> Color(0xFFB6B6B6)
    }
}

private fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
}

fun formatTime(timestamp: Long): String {
    val date = Date(timestamp * 1000)
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(date)
}

@Composable
private fun ReplySpecialLabelChip(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary
    )
}

//  UP 标签 - iOS Pill Style
@Composable
fun UpTag() {
    Surface(
        color = Color(0xFFFF6699),
        shape = CircleShape, // Capsule/Pill shape
        modifier = Modifier
            .height(16.dp)
            .wrapContentWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .fillMaxHeight()
        ) {
            Text(
                text = "UP",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 9.sp
            )
        }
    }
}

//  置顶标签 - iOS Pill Style
@Composable
fun PinnedTag() {
    Surface(
        color = Color(0xFFFFA500),
        shape = CircleShape,
        modifier = Modifier.height(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            Text(
                text = "置顶",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

//  评论图片网格组件 - 支持 GIF 动画
//  [优化] 更新为匹配动态页面的视觉风格
@Composable
fun CommentPictures(
    pictures: List<ReplyPicture>,
    onImageClick: (List<String>, Int, Rect?) -> Unit
) {
    //  获取高质量图片URL（移除分辨率限制参数）
    val imageUrls = remember(pictures) {
        pictures.map { pic ->
            var url = pic.imgSrc
            // 修复协议
            if (url.startsWith("//")) {
                url = "https:$url"
            } else if (url.startsWith("http://")) {
                url = url.replace("http://", "https://")
            }
            //  移除尺寸参数以获取原图（避免模糊）
            if (url.contains("@")) {
                url = url.substringBefore("@")
            }
            url
        }
    }
    val context = LocalContext.current
    val totalCount = pictures.size  //  [优化] 保存总图片数用于角标显示
    
    //  GIF 图片加载器
    val gifImageLoader = context.imageLoader
    
    // 检测是否是 GIF
    fun isGif(url: String) = url.contains(".gif", ignoreCase = true)
    
    // 根据图片数量选择不同的布局
    when (pictures.size) {
        1 -> {
            // 单张图片：保持原始比例，限制最大尺寸
            val pic = pictures[0]
            //  [优化] 更好的比例计算
            val aspectRatio = if (pic.imgHeight > 0 && pic.imgWidth > 0) {
                (pic.imgWidth.toFloat() / pic.imgHeight.toFloat()).coerceIn(0.5f, 2f)
            } else {
                1.33f  // 默认 4:3 比例
            }
            var imageRect by remember { mutableStateOf<Rect?>(null) }
            
            Box(
                modifier = Modifier
                    .widthIn(max = 220.dp)  //  [优化] 增大最大宽度
                    .heightIn(max = 220.dp)
                    .aspectRatio(aspectRatio)
                    .clip(RoundedCornerShape(12.dp))  //  [优化] 更大圆角 8dp → 12dp
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onGloballyPositioned { coordinates ->
                        imageRect = coordinates.boundsInWindow()
                    }
                    .clickable { onImageClick(imageUrls, 0, imageRect) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrls[0])
                        .size(coil.size.Size.ORIGINAL)  //  强制加载原图，避免模糊
                        .addHeader("Referer", "https://www.bilibili.com/")  //  必需
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    imageLoader = gifImageLoader,  //  支持 GIF 和其他格式
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        else -> {
            // 多张图片：网格布局
            val displayItems = pictures.take(9)  //  [优化] 最多显示9张
            val columns = when {
                displayItems.size <= 4 -> 2
                else -> 3
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {  //  [优化] 增加间距 4dp → 6dp
                displayItems.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { colIndex, pic ->
                            val globalIndex = rowIndex * columns + colIndex
                            var imageRect by remember { mutableStateOf<Rect?>(null) }
                            
                            Box(
                                modifier = Modifier
                                    .size(85.dp)  //  [优化] 增大尺寸 80dp → 85dp
                                    .clip(RoundedCornerShape(10.dp))  //  [优化] 更大圆角 6dp → 10dp
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .onGloballyPositioned { coordinates ->
                                        imageRect = coordinates.boundsInWindow()
                                    }
                                    .clickable { onImageClick(imageUrls, globalIndex, imageRect) },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrls[globalIndex])
                                        .size(coil.size.Size.ORIGINAL)  //  强制加载原图，避免模糊
                                        .addHeader("Referer", "https://www.bilibili.com/")  //  必需
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    imageLoader = gifImageLoader,  //  支持 GIF
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                //  [新增] 最后一张图片显示多图角标（如 +3）
                                if (globalIndex == displayItems.size - 1 && totalCount > 9) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "+${totalCount - 9}",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
