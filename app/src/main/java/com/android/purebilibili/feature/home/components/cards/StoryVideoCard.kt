// 文件路径: feature/home/components/cards/StoryVideoCard.kt
package com.android.purebilibili.feature.home.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.iOSCardTapEffect
import com.android.purebilibili.core.util.animateEnter
import com.android.purebilibili.core.util.CardPositionManager
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.spring

import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.theme.LocalCornerRadiusScale
import com.android.purebilibili.core.theme.iOSCornerRadius
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.components.UpBadgeName
import com.android.purebilibili.core.ui.components.resolveUpStatsText
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_COVER_ASPECT_RATIO
import com.android.purebilibili.core.ui.transition.shouldEnableVideoCoverSharedTransition
import com.android.purebilibili.core.ui.transition.shouldEnableVideoMetadataSharedTransition
import com.android.purebilibili.feature.home.resolveHomeCardEnterAnimationEnabledAtMount
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*

/**
 *  故事卡片 - 影院海报风格
 * 
 * 特点：
 * - 16:10 宽屏比例
 * - 大圆角 (24dp)
 * - 标题叠加在封面底部
 * - 沉浸电影感
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StoryVideoCard(
    video: VideoItem,
    index: Int = 0,  //  [新增] 索引用于动画延迟
    animationEnabled: Boolean = true,  //  卡片动画开关
    motionTier: MotionTier = MotionTier.Normal,
    transitionEnabled: Boolean = false, //  卡片过渡动画开关
    scrollLiteModeEnabled: Boolean = false,
    showCoverGlassBadges: Boolean = true,
    showInfoGlassBadges: Boolean = true,
    upFollowerCount: Int? = null,
    upVideoCount: Int? = null,
    onDismiss: (() -> Unit)? = null,    //  [新增] 删除/过滤回调（长按触发）
    onLongClick: ((VideoItem) -> Unit)? = null, // [修复] 长按预览回调
    onClick: (String, Long) -> Unit
) {
    val haptic = rememberHapticFeedback()
    
    // [新增] 获取圆角缩放比例
    val cornerRadiusScale = LocalCornerRadiusScale.current
    val cardCornerRadius = iOSCornerRadius.ExtraLarge * cornerRadiusScale  // 20.dp * scale
    val smallCornerRadius = iOSCornerRadius.Small * cornerRadiusScale - 2.dp  // 8.dp * scale
    val durationBadgeStyle = remember { resolveVideoCardDurationBadgeVisualStyle() }
    val scrollLitePolicy = remember(scrollLiteModeEnabled) {
        resolveStoryVideoCardScrollLiteVisualPolicy(
            scrollLiteModeEnabled = scrollLiteModeEnabled
        )
    }
    val badgeStylePolicy = remember(showCoverGlassBadges, showInfoGlassBadges) {
        resolveHomeVideoGlassBadgeStylePolicy(
            showCoverGlassBadges = showCoverGlassBadges,
            showInfoGlassBadges = showInfoGlassBadges
        )
    }
    
    //  [新增] 长按删除菜单状态
    var showDismissMenu by remember { mutableStateOf(false) }
    
    val coverUrl = remember(video.bvid) {
        FormatUtils.fixImageUrl(if (video.pic.startsWith("//")) "https:${video.pic}" else video.pic)
    }
    
    //  获取屏幕尺寸用于计算归一化坐标
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    //  记录卡片位置
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val triggerCardClick = {
        cardBounds?.let { bounds ->
            CardPositionManager.recordCardPosition(
                bounds,
                screenWidthPx,
                screenHeightPx,
                isSingleColumn = !transitionEnabled
            )
        }
        onClick(video.bvid, 0)
    }
    
    //  尝试获取共享元素作用域
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
    
    val cardModifier = if (coverSharedEnabled) {
        with(requireNotNull(sharedTransitionScope)) {
            Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "video_cover_${video.bvid}"),
                    animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                    boundsTransform = { _, _ -> com.android.purebilibili.core.theme.AnimationSpecs.BiliPaiSpringSpec },
                    clipInOverlayDuringTransition = OverlayClip(
                        RoundedCornerShape(cardCornerRadius)
                    )
                )
        }
    } else {
        Modifier
    }
    val enterAnimationEnabledAtMount = remember(video.bvid) {
        resolveHomeCardEnterAnimationEnabledAtMount(
            baseAnimationEnabled = animationEnabled,
            isReturningFromDetail = CardPositionManager.isReturningFromDetail,
            isSwitchingCategory = CardPositionManager.isSwitchingCategory
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            //  [修复] 进场动画 - 使用 Unit 作为 key，避免分类切换时重新动画
            .animateEnter(
                index = index, 
                key = Unit, 
                animationEnabled = enterAnimationEnabledAtMount,
                motionTier = motionTier
            )
            //  [新增] 记录卡片位置
            .onGloballyPositioned { coordinates ->
                cardBounds = coordinates.boundsInRoot()
            }
            .pointerInput(onDismiss, onLongClick) {
                 val hasLongPressAction = onDismiss != null || onLongClick != null
                 if (hasLongPressAction) {
                     detectTapGestures(
                         onLongPress = {
                             if (onLongClick != null) {
                                 haptic(HapticType.HEAVY)
                                 onLongClick(video)
                             } else if (onDismiss != null) {
                                 haptic(HapticType.HEAVY)
                                 showDismissMenu = true
                             }
                         },
                         onTap = {
                             triggerCardClick()
                         }
                     )
                 }
            }
            .then(
                 if (onDismiss == null && onLongClick == null) {
                     Modifier.iOSCardTapEffect(
                         pressScale = 1f,
                         pressTranslationY = 0f,
                         hapticEnabled = true
                     ) {
                         triggerCardClick()
                     }
                 } else Modifier
            )
    ) {
        // 卡片容器 (封面)
        Box(
            modifier = cardModifier
                .fillMaxWidth()
                .shadow(
                    elevation = scrollLitePolicy.coverShadowElevationDp.dp,
                    shape = RoundedCornerShape(cardCornerRadius),
                    ambientColor = Color.Black.copy(alpha = 0.1f),
                    spotColor = Color.Black.copy(alpha = 0.15f),
                    clip = true // [Optimization] Combine shadow and clip
                )
                .background(MaterialTheme.colorScheme.surfaceVariant) // 封面占位色
        ) {
            //  封面 - 16:10 统一共享比例
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(150)
                    .memoryCacheKey("story_${video.bvid}")
                    .diskCacheKey("story_${video.bvid}")
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(VIDEO_SHARED_COVER_ASPECT_RATIO)
                    .clip(RoundedCornerShape(cardCornerRadius)),
                contentScale = ContentScale.Crop
            )
            
            //  时长标签 (保留在封面上)
            if (badgeStylePolicy.coverStyle == HomeVideoBadgeStyle.GLASS) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = durationBadgeStyle.backgroundAlpha),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = durationBadgeStyle.textShadowAlpha),
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                                blurRadius = durationBadgeStyle.textShadowBlurRadiusPx
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    text = FormatUtils.formatDuration(video.duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = durationBadgeStyle.textShadowAlpha),
                            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                            blurRadius = durationBadgeStyle.textShadowBlurRadiusPx
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        //  标题
        // 🔗 [共享元素] 标题
        var titleModifier = Modifier.fillMaxWidth()
        if (metadataSharedEnabled) {
            with(requireNotNull(sharedTransitionScope)) {
                titleModifier = titleModifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "video_title_${video.bvid}"),
                    animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                    boundsTransform = { _, _ ->
                        spring(dampingRatio = 0.8f, stiffness = 200f)
                    }
                )
            }
        }
        
        Text(
            text = video.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp, // 比双列略大
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 23.sp,
            modifier = titleModifier
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // UP主信息 + 数据
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // UP主名称
            // 🔗 [共享元素] UP主名称
            var upNameModifier = Modifier.wrapContentSize()
            if (metadataSharedEnabled) {
                with(requireNotNull(sharedTransitionScope)) {
                    upNameModifier = upNameModifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "video_up_${video.bvid}"),
                        animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                        boundsTransform = { _, _ ->
                            spring(dampingRatio = 0.8f, stiffness = 200f)
                        }
                    )
                }
            }
            
            UpBadgeName(
                name = video.owner.name,
                metaText = resolveUpStatsText(
                    followerCount = upFollowerCount,
                    videoCount = upVideoCount
                ),
                leadingContent = if (video.owner.face.isNotEmpty()) {
                    {
                        var avatarModifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)

                        if (metadataSharedEnabled) {
                            with(requireNotNull(sharedTransitionScope)) {
                                avatarModifier = avatarModifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "video_avatar_${video.bvid}"),
                                    animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                                    boundsTransform = { _, _ ->
                                        spring(dampingRatio = 0.8f, stiffness = 200f)
                                    },
                                    clipInOverlayDuringTransition = OverlayClip(CircleShape)
                                )
                            }
                        }

                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(FormatUtils.fixImageUrl(video.owner.face))
                                .crossfade(100)
                                .build(),
                            contentDescription = null,
                            modifier = avatarModifier,
                            contentScale = ContentScale.Crop
                        )
                    }
                } else null,
                nameStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                nameColor = MaterialTheme.colorScheme.onSurfaceVariant,
                metaColor = MaterialTheme.colorScheme.primary,
                badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                badgeBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = upNameModifier
            )
            
            // 数据行 (Play & Danmaku)
             //  [重设计] 播放数据行 - 独立展示，精致风格
            if (scrollLitePolicy.showSecondaryStatsRow) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(start = 16.dp) // 与 UP 主信息分开
                ) {
                    // 播放量
                    if (video.stat.view > 0) {
                         // 🔗 [共享元素] 播放量
                        var viewsModifier = Modifier.wrapContentSize()
                        if (metadataSharedEnabled) {
                            with(requireNotNull(sharedTransitionScope)) {
                                viewsModifier = viewsModifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "video_views_${video.bvid}"),
                                    animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                                    boundsTransform = { _, _ ->
                                        spring(dampingRatio = 0.8f, stiffness = 200f)
                                    }
                                )
                            }
                        }
                        
                        Box(modifier = viewsModifier) {
                             Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = CupertinoIcons.Outlined.PlayCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = FormatUtils.formatStat(video.stat.view.toLong()),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 弹幕数 (仅当有播放量时显示，保持逻辑一致)
                    if (video.stat.view > 0 && video.stat.danmaku > 0) {
                         // 🔗 [共享元素] 弹幕数
                         var danmakuModifier = Modifier.wrapContentSize()
                         if (metadataSharedEnabled) {
                             with(requireNotNull(sharedTransitionScope)) {
                                 danmakuModifier = danmakuModifier.sharedBounds(
                                     sharedContentState = rememberSharedContentState(key = "video_danmaku_${video.bvid}"),
                                     animatedVisibilityScope = requireNotNull(animatedVisibilityScope),
                                     boundsTransform = { _, _ ->
                                         spring(dampingRatio = 0.8f, stiffness = 200f)
                                     }
                                 )
                             }
                         }

                         Box(modifier = danmakuModifier) {
                             Row(
                                 verticalAlignment = Alignment.CenterVertically,
                                 horizontalArrangement = Arrangement.spacedBy(2.dp)
                             ) {
                                 Icon(
                                     imageVector = CupertinoIcons.Outlined.BubbleLeft,
                                     contentDescription = null,
                                     modifier = Modifier.size(12.dp),
                                     tint = MaterialTheme.colorScheme.onSurfaceVariant
                                 )
                                 Text(
                                     text = FormatUtils.formatStat(video.stat.danmaku.toLong()),
                                     color = MaterialTheme.colorScheme.onSurfaceVariant,
                                     fontSize = 11.sp,
                                     fontWeight = FontWeight.Medium
                                 )
                             }
                         }
                    }
                }
            }
        }
    }
    
    //  [新增] 长按删除菜单
    DropdownMenu(
        expanded = showDismissMenu,
        onDismissRequest = { showDismissMenu = false }
    ) {
        DropdownMenuItem(
            text = { 
                Text(
                    "🚫 不感兴趣",
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            onClick = {
                showDismissMenu = false
                onDismiss?.invoke()
            }
        )
    }
}
