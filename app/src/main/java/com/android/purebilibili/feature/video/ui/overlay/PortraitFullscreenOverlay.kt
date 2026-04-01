package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.filled.*
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.MoreVert
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.FormatUtils

internal fun shouldShowPortraitViewCount(viewCount: Int, compactMode: Boolean): Boolean {
    return viewCount > 0 && !compactMode
}

internal fun shouldShowPortraitTopMoreAction(): Boolean = false

/**
 * 竖屏全屏覆盖层 (B站官方风格) - 重构版
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortraitFullscreenOverlay(
    title: String,
    authorName: String = "",
    authorFace: String = "",
    isPlaying: Boolean,
    progress: PlayerProgress,
    
    // 互动数据
    statView: Int = 0,
    statLike: Int = 0,
    statDanmaku: Int = 0,
    statReply: Int = 0,
    statFavorite: Int = 0,
    statShare: Int = 0,
    
    // 互动状态
    isLiked: Boolean,
    isCoined: Boolean,
    isFavorited: Boolean,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onCommentClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    
    // 关注状态 (Follow status)
    isFollowing: Boolean = false,
    onFollowClick: () -> Unit = {},
    
    // [新增] 详情点击
    onDetailClick: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onAuthorClick: () -> Unit = {},
    
    // 控制状态
    currentSpeed: Float,
    currentQualityLabel: String,
    currentRatio: VideoAspectRatio,
    danmakuEnabled: Boolean,
    isStatusBarHidden: Boolean,
    
    // 显示状态
    showControls: Boolean = true,
    
    // 回调
    onBack: () -> Unit,
    onHomeClick: () -> Unit = onBack,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},
    seekPositionMs: Long = progress.current,
    isSeekScrubbing: Boolean = false,
    onSeekDragStart: (Long) -> Unit = {},
    onSeekDragUpdate: (Long) -> Unit = {},
    onSeekDragCancel: () -> Unit = {},
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    onRatioClick: () -> Unit,
    onDanmakuToggle: () -> Unit,
    onDanmakuInputClick: () -> Unit,
    onToggleStatusBar: () -> Unit,
    onRotateToLandscape: () -> Unit,
    onSearchClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolvePortraitFullscreenOverlayLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        
        // 控件层动画
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                
                // 1. 顶部栏 (返回 + 观看人数)
                PortraitTopControlBar(
                    layoutPolicy = layoutPolicy,
                    onBack = onBack,
                    onHomeClick = onHomeClick,
                    viewCount = statView,
                    danmakuEnabled = danmakuEnabled,
                    onDanmakuToggle = onDanmakuToggle,
                    isStatusBarHidden = isStatusBarHidden,
                    onToggleStatusBar = onToggleStatusBar,
                    onSearchClick = onSearchClick,
                    onMoreClick = onMoreClick
                )

                // 2. 右侧互动栏 (不再包含头像)
                PortraitInteractionBar(
                    isLiked = isLiked,
                    likeCount = statLike,
                    isFavorited = isFavorited,
                    favoriteCount = statFavorite,
                    commentCount = statReply.takeIf { it > 0 } ?: statDanmaku, // 优先用评论数，没有则用弹幕数代替展示
                    shareCount = statShare,
                    onLikeClick = onLikeClick,
                    onFavoriteClick = onFavoriteClick,
                    onCommentClick = onCommentClick,
                    onShareClick = onShareClick,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
                
                // 3. 底部区域 (信息 + 进度条 + 输入栏占位)
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                ) {
                    // 视频信息 (Video Info)
                    PortraitVideoInfo(
                        layoutPolicy = layoutPolicy,
                        authorName = authorName,
                        authorFace = authorFace,
                        title = title,
                        isFollowing = isFollowing,
                        onFollowClick = onFollowClick,
                        onTitleClick = onTitleClick,
                        onAuthorClick = onAuthorClick,
                        modifier = Modifier
                            .fillMaxWidth(layoutPolicy.infoWidthFraction)
                            .padding(horizontal = layoutPolicy.infoHorizontalPaddingDp.dp)
                            .padding(bottom = layoutPolicy.infoBottomPaddingDp.dp)
                    )
                    
                    // 底部进度条 (Progress Bar)
                    PortraitBottomContainer(
                        progress = if (progress.duration > 0) progress.current.toFloat() / progress.duration else 0f,
                        duration = progress.duration,
                        seekPositionMs = seekPositionMs,
                        isSeekScrubbing = isSeekScrubbing,
                        onSeek = onSeek,
                        onSeekStart = onSeekStart,
                        onSeekDragStart = onSeekDragStart,
                        onSeekDragUpdate = onSeekDragUpdate,
                        onSeekDragCancel = onSeekDragCancel
                    )
                    
                    // 底部输入栏占位 (Input Bar Spacer)
                    // Input Bar height is usually around 50-60dp.
                    // Since Input Bar is an overlay at Alignment.BottomCenter in the outer Box (see below),
                    // we need to add a spacer here so the progress bar sits *above* the input bar, not behind it.
                    // Or, we render the Input Bar *here* in the Column?
                    // "PortraitBottomInputBar" logic:
                    // If we put it here, it will be stacked. 
                    // Let's verify where PortraitBottomInputBar is placed in the original code.
                    // Original: Modifier.align(Alignment.BottomCenter)
                    
                    // Let's add a Spacer. Assuming Input Bar height ~50dp + margins.
                    Spacer(
                        modifier = Modifier.height(
                            (layoutPolicy.bottomInputSpacerHeightDp + layoutPolicy.bottomInputLiftDp).dp
                        )
                    )
                }

                // 4. 底部输入栏 (Input Bar) - Keep strict bottom alignment (Overlay)
                PortraitBottomInputBar(
                    onInputClick = onDanmakuInputClick,
                    onRotateClick = onRotateToLandscape,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = layoutPolicy.bottomInputLiftDp.dp)
                )
            }
        }

        // 控件隐藏时仍显示底部细进度条，方便随时感知播放进度
        if (!showControls) {
            PersistentBottomProgressBar(
                current = progress.current,
                duration = progress.duration,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * 顶部控制区
 */
@Composable
private fun PortraitTopControlBar(
    layoutPolicy: PortraitFullscreenOverlayLayoutPolicy,
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    viewCount: Int,
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit,
    isStatusBarHidden: Boolean,
    onToggleStatusBar: () -> Unit,
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = layoutPolicy.topHorizontalPaddingDp.dp,
                vertical = layoutPolicy.topVerticalPaddingDp.dp
            ),
    ) {
        // 左侧：返回 + 观看人数
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent),
                modifier = Modifier.size(layoutPolicy.topBackButtonSizeDp.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.ChevronBackward,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.topBackIconSizeDp.dp)
                )
            }
            IconButton(
                onClick = onHomeClick,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent),
                modifier = Modifier.size(layoutPolicy.topBackButtonSizeDp.dp)
            ) {
                Icon(
                    imageVector = CupertinoIcons.Default.House,
                    contentDescription = "主界面",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.topBackIconSizeDp.dp)
                )
            }
            Spacer(modifier = Modifier.width(layoutPolicy.topViewCountStartSpacingDp.dp))
            if (shouldShowPortraitViewCount(viewCount = viewCount, compactMode = layoutPolicy.compactMode)) {
                Text(
                    text = "${FormatUtils.formatStat(viewCount.toLong())}播放",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = layoutPolicy.topViewCountFontSp.sp
                )
            }
        }

        // 右上角功能区
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(layoutPolicy.topActionSpacingDp.dp)
        ) {
            val danmakuToggleInteraction = remember { MutableInteractionSource() }
            val danmakuActiveColor = MaterialTheme.colorScheme.primary
            val danmakuInactiveColor = Color.White.copy(alpha = 0.74f)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (danmakuEnabled) {
                            danmakuActiveColor.copy(alpha = 0.2f)
                        } else {
                            danmakuInactiveColor.copy(alpha = 0.14f)
                        }
                    )
                    .clickable(
                        interactionSource = danmakuToggleInteraction,
                        indication = null,
                        onClick = onDanmakuToggle
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (danmakuEnabled) CupertinoIcons.Filled.TextBubble else CupertinoIcons.Outlined.TextBubble,
                    contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                    tint = if (danmakuEnabled) danmakuActiveColor else danmakuInactiveColor,
                    modifier = Modifier.size(layoutPolicy.topActionIconSizeDp.dp)
                )
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "搜索",
                    tint = Color.White,
                    modifier = Modifier.size(layoutPolicy.topActionIconSizeDp.dp)
                )
            }
            if (shouldShowPortraitTopMoreAction()) {
                IconButton(onClick = onMoreClick) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "菜单",
                        tint = Color.White,
                        modifier = Modifier.size(layoutPolicy.topActionIconSizeDp.dp)
                    )
                }
            }
        }
    }
}

/**
 * 底部视频信息 (重构：头像在左下角)
 */
@Composable
private fun PortraitVideoInfo(
    layoutPolicy: PortraitFullscreenOverlayLayoutPolicy,
    authorName: String,
    authorFace: String,
    title: String,
    isFollowing: Boolean,
    onFollowClick: () -> Unit,
    onTitleClick: () -> Unit,
    onAuthorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // 第一行：头像 + 名字 + 关注按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(bottom = layoutPolicy.authorRowBottomPaddingDp.dp)
                .clickable { onAuthorClick() }
        ) {
            // 头像
            if (authorFace.isNotEmpty()) {
                AsyncImage(
                    model = FormatUtils.fixImageUrl(authorFace),
                    contentDescription = authorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(layoutPolicy.avatarSizeDp.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                )
                Spacer(modifier = Modifier.width(layoutPolicy.avatarNameSpacingDp.dp))
            }
            
            // 名字
            Text(
                text = "@$authorName",
                color = Color.White,
                fontSize = layoutPolicy.authorNameFontSp.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.width(layoutPolicy.avatarNameSpacingDp.dp))
            
            // 关注按钮
            val isFollowed = isFollowing
            val buttonColor = if (isFollowed) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary
            val contentColor = if (isFollowed) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onPrimary
            val buttonText = if (isFollowed) "已关注" else "关注"
            val iconVisible = !isFollowed

            Surface(
                shape = RoundedCornerShape(layoutPolicy.followButtonCornerRadiusDp.dp),
                color = buttonColor,
                modifier = Modifier
                    .height(layoutPolicy.followButtonHeightDp.dp)
                    .clickable { onFollowClick() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = layoutPolicy.followButtonHorizontalPaddingDp.dp)
                ) {
                    if (iconVisible) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(layoutPolicy.followIconSizeDp.dp)
                        )
                        Spacer(modifier = Modifier.width(layoutPolicy.followIconSpacingDp.dp))
                    }
                    Text(
                        text = buttonText,
                        color = contentColor,
                        fontSize = layoutPolicy.followTextFontSp.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 第二行：标题
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = layoutPolicy.titleFontSp.sp,
            maxLines = 3,
            lineHeight = layoutPolicy.titleLineHeightSp.sp,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { onTitleClick() }
        )
    }
}
