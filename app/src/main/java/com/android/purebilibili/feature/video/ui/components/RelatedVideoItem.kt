package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.core.theme.iosCard
import com.android.purebilibili.core.theme.iOSSystemGray3
import com.android.purebilibili.core.theme.iOSBlue
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_COVER_ASPECT_RATIO

/**
 * Related Video Components
 * 
 * Contains components for displaying related videos:
 * - RelatedVideosHeader: Section header
 * - RelatedVideoItem: Individual video card
 * 
 * Requirement Reference: AC3.3 - Related video components in dedicated file
 */

/**
 * Related Videos Header
 */
@Composable
fun RelatedVideosHeader() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent // 🎨 [修复] 让标题直接显示在背景上，不显示为独立的色块
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\u66f4\u591a\u63a8\u8350",
                style = MaterialTheme.typography.titleMedium, // Should be ~17sp SemiBold "Body/Headline"
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Related Video Item (iOS style optimized)
 * 
 * @param video 相关视频数据
 * @param isFollowed 是否已关注
 * @param transitionEnabled 🔗 是否启用共享元素过渡动画
 * @param onClick 点击回调
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RelatedVideoItem(
    video: RelatedVideo, 
    isFollowed: Boolean = false,
    transitionEnabled: Boolean = false,  // 🔗 [新增] 共享元素过渡开关
    onClick: () -> Unit
) {
    // 🔗 获取共享元素作用域 (用于过渡动画)
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    
    // Top-level container acts as the button/card
    // Using simple Row structure but with IOS touch physics manually or via wrapper
    // Since we want the whole row to be clickable and scale:
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f, // MediumBouncy
            stiffness = 500f // Medium
        ),
        label = "cardScale"
    )
    
    // Haptic feedback
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(isPressed) {
        if (isPressed) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) // Using standard haptic for now
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp) // Spacing between items
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.foundation.LocalIndication.current,
                    onClick = onClick
                )
        ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp) // Internal padding
        ) {
            // 🔗 [共享元素] 为封面添加共享元素标记
            val coverModifier = if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    Modifier
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "video_cover_${video.bvid}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec },
                            clipInOverlayDuringTransition = OverlayClip(
                                RoundedCornerShape(12.dp)
                            )
                        )
                }
            } else {
                Modifier
            }
            val relatedCoverWidth = 130.dp
            val relatedCoverHeight = relatedCoverWidth / VIDEO_SHARED_COVER_ASPECT_RATIO
            
            // Video cover
            Box(
                modifier = coverModifier
                    .width(relatedCoverWidth)
                    .height(relatedCoverHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(FormatUtils.fixImageUrl(video.pic))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Duration label - Plain text with shadow, no background (Apple style)
                Text(
                    text = FormatUtils.formatDuration(video.duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.6f),
                            blurRadius = 4f,
                            offset = Offset(0f, 1f)
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Video info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = relatedCoverHeight),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Title
                // 🔗 [共享元素] 标题 - Wrap in Box to isolate from Text intrinsic measurement issues
                var titleBoxModifier = Modifier.fillMaxWidth()

                if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        titleBoxModifier = titleBoxModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "video_title_${video.bvid}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ ->
                                com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec
                            }
                        )
                    }
                }

                Box(modifier = titleBoxModifier) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium.copy( // 15sp regular/medium
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column {
                    // UP owner info row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // UP Name
                        var upNameBoxModifier = Modifier.weight(1f, fill = false)

                        if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                upNameBoxModifier = upNameBoxModifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "video_up_${video.bvid}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ ->
                                        com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec
                                    }
                                )
                            }
                        }
                        var followActionModifier = Modifier.wrapContentSize()
                        if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                followActionModifier = followActionModifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "video_up_action_${video.bvid}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ ->
                                        com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec
                                    }
                                )
                            }
                        }

                        UpBadgeName(
                            name = video.owner.name,
                            badgeTrailingContent = if (isFollowed) {
                                {
                                    Text(
                                        text = "已关注",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = iOSBlue,
                                        modifier = followActionModifier
                                    )
                                }
                            } else null,
                            leadingContent = if (video.owner.face.isNotEmpty()) {
                                {
                                    var avatarModifier = Modifier
                                        .size(16.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)

                                    if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                                        with(sharedTransitionScope) {
                                            avatarModifier = avatarModifier.sharedBounds(
                                                sharedContentState = rememberSharedContentState(key = "video_avatar_${video.bvid}"),
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                boundsTransform = { _, _ ->
                                                    com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec
                                                },
                                                clipInOverlayDuringTransition = OverlayClip(androidx.compose.foundation.shape.CircleShape)
                                            )
                                        }
                                    }

                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(FormatUtils.fixImageUrl(video.owner.face))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = avatarModifier
                                    )
                                }
                            } else null,
                            nameStyle = MaterialTheme.typography.labelMedium,
                            nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            badgeBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = upNameBoxModifier
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // Stats row
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Views
                        var viewsModifier = Modifier.wrapContentSize()
                        if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                viewsModifier = viewsModifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "video_views_${video.bvid}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ ->
                                        com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec
                                    }
                                )
                            }
                        }
                        Box(modifier = viewsModifier) {
                            StatItem(icon = CupertinoIcons.Filled.Play, text = FormatUtils.formatStat(video.stat.view.toLong()))
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Danmaku
                        var danmakuModifier = Modifier.wrapContentSize()
                        if (transitionEnabled && sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                danmakuModifier = danmakuModifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "video_danmaku_${video.bvid}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ ->
                                        com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec
                                    }
                                )
                            }
                        }
                        Box(modifier = danmakuModifier) {
                            StatItem(icon = CupertinoIcons.Filled.BubbleLeft, text = FormatUtils.formatStat(video.stat.danmaku.toLong()))
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline, // System Gray 3 or similar
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
