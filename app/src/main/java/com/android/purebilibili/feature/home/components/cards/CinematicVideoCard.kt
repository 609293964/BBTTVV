package com.android.purebilibili.feature.home.components.cards

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.theme.LocalCornerRadiusScale
import com.android.purebilibili.core.theme.iOSCornerRadius
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_COVER_ASPECT_RATIO
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.shouldEnableVideoMetadataSharedTransition
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.data.model.response.VideoItem
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.draw.blur
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import io.github.alexzhirkevich.cupertino.icons.filled.PlayCircle
import io.github.alexzhirkevich.cupertino.icons.filled.BubbleLeft
import com.android.purebilibili.feature.home.resolveHomeCardEnterAnimationEnabledAtMount

/**
 * 沉浸式视频卡片 (Cinematic Mode)
 * 全屏大图 + 底部文字遮罩，提供类似电影海报的沉浸体验
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CinematicVideoCard(
    video: VideoItem,
    index: Int,
    isFollowing: Boolean = false,
    animationEnabled: Boolean = true,
    motionTier: MotionTier = MotionTier.Normal,
    transitionEnabled: Boolean = false,
    isDataSaverActive: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    onWatchLater: (() -> Unit)? = null,
    onClick: (String, Long) -> Unit
) {
    val haptic = rememberHapticFeedback()
    
    // 动态圆角 - 略大一点的圆角以适配大图卡片
    val cornerRadiusScale = LocalCornerRadiusScale.current
    val cardCornerRadius = 16.dp * cornerRadiusScale 

    var showDismissMenu by remember { mutableStateOf(false) }

    val coverUrl = remember(video.bvid) {
        FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic)
    }

    // 记录位置
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val densityValue = density.density
    // 记录卡片位置（非 Compose State，避免滚动时触发高频重组）
    val cardBoundsRef = remember { object { var value: androidx.compose.ui.geometry.Rect? = null } }
    val triggerCardClick = {
        cardBoundsRef.value?.let { bounds ->
            CardPositionManager.recordCardPosition(
                bounds,
                screenWidthPx,
                screenHeightPx,
                density = densityValue
            )
        }
        onClick(video.bvid, 0)
    }
    
    // 按压效果
    var isPressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f, // 微妙的缩放
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cardScale"
    )

    // 共享元素
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
    val coverSharedEnabled = shouldEnableVideoCoverSharedTransition(
        transitionEnabled = transitionEnabled,
        hasSharedTransitionScope = sharedTransitionScope != null,
        hasAnimatedVisibilityScope = animatedVisibilityScope != null
    )
    val metadataSharedEnabled = shouldEnableVideoMetadataSharedTransition(
        coverSharedEnabled = coverSharedEnabled,
        isQuickReturnLimited = CardPositionManager.shouldLimitSharedElementsForQuickReturn()
    )
    val enterAnimationEnabledAtMount = remember(video.bvid) {
        resolveHomeCardEnterAnimationEnabledAtMount(
            baseAnimationEnabled = animationEnabled,
            isReturningFromDetail = CardPositionManager.isReturningFromDetail,
            isSwitchingCategory = CardPositionManager.isSwitchingCategory
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp) // 增加间距
            .scale(scale)
            .animateEnter(
                index = index,
                key = Unit,
                animationEnabled = enterAnimationEnabledAtMount,
                motionTier = motionTier
            )
            .onGloballyPositioned { coordinates ->
                cardBoundsRef.value = coordinates.boundsInRoot()
            }
    ) {
        // 卡片主体容器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 8.dp, // 标准阴影
                    shape = RoundedCornerShape(cardCornerRadius),
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                )
                .clip(RoundedCornerShape(cardCornerRadius))
                .background(Color.Black) // 纯黑底色
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onLongPress = {
                             if (onDismiss != null || onWatchLater != null) {
                                haptic(HapticType.HEAVY)
                                showDismissMenu = true
                             }
                        },
                        onTap = {
                            triggerCardClick()
                        }
                    )
                }
        ) {
            val coverModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_SHARED_COVER_ASPECT_RATIO) // 统一共享比例
            
            // 共享元素: 封面
            val finalCoverModifier = if (coverSharedEnabled) {
                with(requireNotNull(sharedTransitionScope)) {
                    coverModifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "video_cover_${video.bvid}"),
                        animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                        boundsTransform = { _, _ -> com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec },
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cardCornerRadius))
                    )
                }
            } else coverModifier

            Box(modifier = Modifier.clip(RoundedCornerShape(cardCornerRadius))) {
                 AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .size(if (isDataSaverActive) 480 else 720) 
                        .crossfade(200)
                        .memoryCacheKey("cover_${video.bvid}_cis")
                        .build(),
                    contentDescription = null,
                    modifier = finalCoverModifier, // 无视差
                    contentScale = ContentScale.Crop
                )
            }

            // 2. 渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.8f) 
                            )
                        )
                    )
            )

            // 3. 内容层
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp) 
            ) {
                // 标题
                 var titleModifier = Modifier.fillMaxWidth().semantics { contentDescription = "视频标题: ${video.title}" }
                if (metadataSharedEnabled) {
                    with(requireNotNull(sharedTransitionScope)) {
                        titleModifier = titleModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "video_title_${video.bvid}"),
                            animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                            boundsTransform = { _, _ -> spring(dampingRatio = 0.8f, stiffness = 200f) }
                        )
                    }
                }

                Text(
                    text = video.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            blurRadius = 8f
                        )
                    ),
                    modifier = titleModifier
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 数据层 (一直显示)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                     var upNameModifier = Modifier.wrapContentSize()
                     if (metadataSharedEnabled) {
                         with(requireNotNull(sharedTransitionScope)) {
                             upNameModifier = upNameModifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "video_up_${video.bvid}"),
                                animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                                boundsTransform = { _, _ -> spring(dampingRatio = 0.8f, stiffness = 200f) }
                             )
                         }
                     }
                     UpBadgeName(
                         name = video.owner.name,
                         leadingContent = if (video.owner.face.isNotEmpty()) {
                             {
                                 var avatarModifier = Modifier
                                     .size(20.dp)
                                     .clip(CircleShape)
                                     .background(Color.White.copy(alpha = 0.2f))

                                 if (metadataSharedEnabled) {
                                     with(requireNotNull(sharedTransitionScope)) {
                                         avatarModifier = avatarModifier.sharedBounds(
                                             sharedContentState = rememberSharedContentState(key = "video_avatar_${video.bvid}"),
                                             animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                                             boundsTransform = { _, _ -> spring(dampingRatio = 0.8f, stiffness = 200f) },
                                             clipInOverlayDuringTransition = OverlayClip(CircleShape)
                                         )
                                     }
                                 }

                                 AsyncImage(
                                     model = ImageRequest.Builder(LocalContext.current)
                                         .data(FormatUtils.fixImageUrl(video.owner.face))
                                         .size(64)
                                         .crossfade(true)
                                         .build(),
                                     contentDescription = null,
                                     modifier = avatarModifier,
                                     contentScale = ContentScale.Crop
                                 )
                             }
                         } else null,
                         nameStyle = MaterialTheme.typography.bodySmall.copy(
                             fontWeight = FontWeight.Medium,
                             shadow = androidx.compose.ui.graphics.Shadow(
                                 color = Color.Black.copy(alpha = 0.5f),
                                 blurRadius = 4f
                             )
                         ),
                         nameColor = Color.White.copy(alpha = 0.9f),
                         badgeTextColor = Color.White.copy(alpha = 0.92f),
                         badgeBorderColor = Color.White.copy(alpha = 0.45f),
                         modifier = upNameModifier
                     )
                     
                     // 播放量
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = CupertinoIcons.Filled.PlayCircle, 
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = FormatUtils.formatStat(video.stat.view.toLong()),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // 时长
                    Text(
                        text = FormatUtils.formatDuration(video.duration),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    )
                }
            }
        }
        
        // 更多操作按钮 (右上角)
         val hasMenu = onDismiss != null || onWatchLater != null
         if (hasMenu) {
             Box(
                 modifier = Modifier
                     .align(Alignment.TopEnd)
                     .padding(12.dp)
                     .size(24.dp)
                     .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                     .clickable { 
                         haptic(HapticType.LIGHT)
                         showDismissMenu = true 
                     },
                 contentAlignment = Alignment.Center
             ) {
                 Text(
                     text = "⋮",
                     color = Color.White,
                     fontSize = 16.sp,
                     fontWeight = FontWeight.Bold,
                     modifier = Modifier.padding(bottom = 2.dp)
                 )
             }
         }
    }


    // 长按菜单
    DropdownMenu(
        expanded = showDismissMenu,
        onDismissRequest = { showDismissMenu = false }
    ) {
        if (onWatchLater != null) {
            DropdownMenuItem(
                text = { Text("🕐 稍后再看") },
                onClick = {
                    showDismissMenu = false
                    onWatchLater.invoke()
                }
            )
        }
        if (onDismiss != null) {
            DropdownMenuItem(
                text = { Text("🚫 不感兴趣") },
                onClick = {
                    showDismissMenu = false
                    onDismiss.invoke()
                }
            )
        }
    }
}
