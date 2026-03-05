// 文件路径: feature/video/screen/TabletVideoLayout.kt
package com.android.purebilibili.feature.video.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp // Add this back
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.android.purebilibili.core.ui.AdaptiveSplitLayout
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.dynamic.components.ImagePreviewDialog
import com.android.purebilibili.feature.dynamic.components.ImagePreviewTextContent
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.ui.components.*
import com.android.purebilibili.feature.video.ui.section.ActionButtonsRow
import com.android.purebilibili.feature.video.ui.section.UpInfoSection
import com.android.purebilibili.feature.video.ui.section.VideoPlayerSection
import com.android.purebilibili.feature.video.ui.section.VideoTitleWithDesc
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.PlayerUiState
import com.android.purebilibili.feature.video.viewmodel.PlayerViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import io.github.alexzhirkevich.cupertino.CupertinoActivityIndicator
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import kotlinx.coroutines.launch

//  共享元素过渡
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.android.purebilibili.core.ui.LocalSharedTransitionScope
import com.android.purebilibili.core.ui.LocalAnimatedVisibilityScope
import com.android.purebilibili.core.ui.transition.VIDEO_SHARED_COVER_ASPECT_RATIO

/**
 * 🖥️ 平板端视频详情页布局
 * 
 * 左右分栏布局：
 * - 左侧：视频播放器 + 视频信息
 * - 右侧：评论 / 相关推荐（可切换）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TabletVideoLayout(
    playerState: VideoPlayerState,
    uiState: PlayerUiState,
    commentState: CommentUiState,
    viewModel: PlayerViewModel,
    commentViewModel: VideoCommentViewModel,
    configuration: Configuration,
    isVerticalVideo: Boolean,
    sleepTimerMinutes: Int?,
    viewPoints: List<ViewPoint>,
    bvid: String,
    coverUrl: String = "",
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit,
    onNavigateToAudioMode: () -> Unit,
    onToggleFullscreen: () -> Unit,  // 📺 全屏切换回调
    isInPipMode: Boolean,
    onPipClick: () -> Unit,
    isPortraitFullscreen: Boolean = false,

    // [New] Codec & Audio Params
    currentCodec: String = "hev1", 
    onCodecChange: (String) -> Unit = {},
    currentSecondCodec: String = "avc1",
    onSecondCodecChange: (String) -> Unit = {},
    currentAudioQuality: Int = -1,
    onAudioQualityChange: (Int) -> Unit = {},
    transitionEnabled: Boolean = false, //  卡片过渡动画开关
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    // 🔁 [新增] 播放模式
    currentPlayMode: com.android.purebilibili.feature.video.player.PlayMode = com.android.purebilibili.feature.video.player.PlayMode.SEQUENTIAL,
    onPlayModeClick: () -> Unit = {},
    forceCoverOnlyOnReturn: Boolean = false
) {
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolveTabletVideoLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    var secondaryPaneModeName by rememberSaveable(bvid) {
        mutableStateOf(TabletSecondaryPaneMode.EXPANDED.name)
    }
    val secondaryPaneMode = remember(secondaryPaneModeName) {
        runCatching { TabletSecondaryPaneMode.valueOf(secondaryPaneModeName) }
            .getOrDefault(TabletSecondaryPaneMode.EXPANDED)
    }
    val primaryRatio = resolveTabletPrimaryRatio(
        basePrimaryRatio = layoutPolicy.primaryRatio,
        secondaryPaneMode = secondaryPaneMode
    )
    
    // 🖥️ [修复] 使用 LocalContext 获取 Activity，而非 playerState.context
    val context = LocalContext.current
    val activity = remember(context) {
        (context as? android.app.Activity)
            ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
    }
    
    AdaptiveSplitLayout(
        primaryContent = {
            // 📹 左侧：播放器 + 视频信息（可滚动）
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 视频播放器（固定高度，不参与滚动）
                
                //  尝试获取共享元素作用域
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalAnimatedVisibilityScope.current
                
                //  为播放器容器添加共享元素标记（受开关控制）
                val playerContainerModifier = if (
                    transitionEnabled &&
                    sharedTransitionScope != null &&
                    animatedVisibilityScope != null &&
                    !forceCoverOnlyOnReturn
                ) {
                    with(sharedTransitionScope) {
                        Modifier
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "video_cover_$bvid"),
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

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val playerWidth = minOf(maxWidth, layoutPolicy.playerMaxWidthDp.dp)
                    val videoHeight = if (forceCoverOnlyOnReturn) {
                        playerWidth / VIDEO_SHARED_COVER_ASPECT_RATIO
                    } else {
                        playerWidth * 9f / 16f
                    }
                    Box(
                        modifier = playerContainerModifier
                            .width(playerWidth)
                            .height(videoHeight)
                            .align(Alignment.Center)
                            .background(Color.Black)
                    ) {
                        VideoPlayerSection(
                            playerState = playerState,
                            uiState = uiState,
                            isFullscreen = false,
                            isInPipMode = isInPipMode,
                            onToggleFullscreen = onToggleFullscreen,
                            onQualityChange = { qid, pos -> viewModel.changeQuality(qid, pos) },
                            onBack = onBack,
                            bvid = bvid,
                            coverUrl = coverUrl,
                            onDoubleTapLike = { viewModel.toggleLike() },
                            onReloadVideo = { viewModel.reloadVideo() },
                            cdnCount = (uiState as? PlayerUiState.Success)?.cdnCount ?: 1,
                            onSwitchCdn = { viewModel.switchCdn() },
                            onSwitchCdnTo = { viewModel.switchCdnTo(it) },
                            isAudioOnly = false,
                            onAudioOnlyToggle = {
                                viewModel.setAudioMode(true)
                                onNavigateToAudioMode()
                            },
                            sleepTimerMinutes = sleepTimerMinutes,
                            onSleepTimerChange = { viewModel.setSleepTimer(it) },
                            videoshotData = (uiState as? PlayerUiState.Success)?.videoshotData,
                            viewPoints = viewPoints,
                            isVerticalVideo = isVerticalVideo,
                            onPortraitFullscreen = { playerState.setPortraitFullscreen(true) },
                            isPortraitFullscreen = isPortraitFullscreen,

                            onPipClick = onPipClick,
                            // [New] Codec & Audio
                            currentCodec = currentCodec,
                            onCodecChange = onCodecChange,
                            currentSecondCodec = currentSecondCodec,
                            onSecondCodecChange = onSecondCodecChange,
                            currentAudioQuality = currentAudioQuality,
                            onAudioQualityChange = onAudioQualityChange,
                            // [New Actions]
                            onSaveCover = { viewModel.saveCover(context) },
                            onDownloadAudio = { viewModel.downloadAudio(context) },
                            // 🔁 [新增] 播放模式
                            currentPlayMode = currentPlayMode,
                            onPlayModeClick = onPlayModeClick
                        )
                    }
                }
                
                // 📜 视频信息区域（可滚动）
                if (uiState is PlayerUiState.Success) {
                    val success = uiState as PlayerUiState.Success
                    val currentPageIndex = success.info.pages.indexOfFirst { it.cid == success.info.cid }.coerceAtLeast(0)
                    val downloadProgress by viewModel.downloadProgress.collectAsState()
                    
                    ScrollableVideoInfoSection(
                        info = success.info,
                        isFollowing = success.isFollowing,
                        isFavorited = success.isFavorited,
                        isLiked = success.isLiked,
                        coinCount = success.coinCount,
                        currentPageIndex = currentPageIndex,
                        downloadProgress = downloadProgress,
                        isInWatchLater = success.isInWatchLater,
                        videoTags = success.videoTags,
                        relatedVideos = success.related,
                        ownerFollowerCount = success.ownerFollowerCount,
                        ownerVideoCount = success.ownerVideoCount,
                        onFollowClick = { viewModel.toggleFollow() },
                        onFavoriteClick = { viewModel.toggleFavorite() },
                        onLikeClick = { viewModel.toggleLike() },
                        onCoinClick = { viewModel.openCoinDialog() },
                        onTripleClick = { viewModel.doTripleAction() },
                        onPageSelect = { viewModel.switchPage(it) },
                        onUpClick = onUpClick,
                        onDownloadClick = { viewModel.openDownloadDialog() },
                        onWatchLaterClick = { viewModel.toggleWatchLater() },
                        onRelatedVideoClick = onRelatedVideoClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .widthIn(max = layoutPolicy.infoMaxWidthDp.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        secondaryContent = {
            // 📝 右侧：评论 / 相关推荐
            if (uiState is PlayerUiState.Success) {
                val success = uiState as PlayerUiState.Success
                
                TabletSecondaryContent(
                    success = success,
                    commentState = commentState,
                    commentViewModel = commentViewModel,
                    viewModel = viewModel,
                    playerState = playerState,
                    onUpClick = onUpClick,
                    paneMode = secondaryPaneMode,
                    onPaneModeChange = { secondaryPaneModeName = it.name },
                    onPaneModeCycle = {
                        secondaryPaneModeName = nextTabletSecondaryPaneMode(secondaryPaneMode).name
                    },
                    onRelatedVideoClick = onRelatedVideoClick
                )
            }
        },
        primaryRatio = primaryRatio
    )
}

/**
 * 📝 平板右侧内容区域（评论/推荐切换）
 */
@Composable
private fun TabletSecondaryContent(
    success: PlayerUiState.Success,
    commentState: CommentUiState,
    commentViewModel: VideoCommentViewModel,
    viewModel: PlayerViewModel,
    playerState: VideoPlayerState,
    onUpClick: (Long) -> Unit,
    paneMode: TabletSecondaryPaneMode,
    onPaneModeChange: (TabletSecondaryPaneMode) -> Unit,
    onPaneModeCycle: () -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit
) {
    val defaultTab = if (commentState.replyCount == 0 && success.related.isNotEmpty()) 1 else 0
    var selectedTab by rememberSaveable(success.info.bvid) { mutableIntStateOf(defaultTab) }
    val tabs = listOf("评论 ${if (commentState.replyCount > 0) "(${commentState.replyCount})" else ""}", "相关推荐")
    
    // 评论图片预览状态
    var showImagePreview by remember { mutableStateOf(false) }
    var previewImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var previewInitialIndex by remember { mutableIntStateOf(0) }
    var sourceRect by remember { mutableStateOf<Rect?>(null) }
    var previewTextContent by remember { mutableStateOf<ImagePreviewTextContent?>(null) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val openCommentUrl: (String) -> Unit = openCommentUrl@{ rawUrl ->
        val url = rawUrl.trim()
        if (url.isEmpty()) return@openCommentUrl

        val parsedResult = com.android.purebilibili.core.util.BilibiliUrlParser.parse(url)
        if (parsedResult.bvid != null) {
            onRelatedVideoClick(parsedResult.bvid, null)
            return@openCommentUrl
        }

        if (shouldOpenCommentUrlInApp(url)) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .setPackage(context.packageName)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val launchedInApp = runCatching {
                context.startActivity(intent)
            }.isSuccess
            if (launchedInApp) return@openCommentUrl
        }

        runCatching { uriHandler.openUri(url) }
    }
    
    // 图片预览对话框
    if (showImagePreview && previewImages.isNotEmpty()) {
        ImagePreviewDialog(
            images = previewImages,
            initialIndex = previewInitialIndex,
            sourceRect = sourceRect,
            textContent = previewTextContent,
            onDismiss = {
                showImagePreview = false
                previewTextContent = null
            }
        )
    }

    if (paneMode == TabletSecondaryPaneMode.COLLAPSED) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TextButton(onClick = { onPaneModeChange(TabletSecondaryPaneMode.COMPACT) }) {
                Text("半开")
            }
            TextButton(onClick = { onPaneModeChange(TabletSecondaryPaneMode.EXPANDED) }) {
                Text("展开")
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                selectedTab = 0
                onPaneModeChange(TabletSecondaryPaneMode.COMPACT)
            }) {
                Text("评论")
            }
            TextButton(onClick = {
                selectedTab = 1
                onPaneModeChange(TabletSecondaryPaneMode.COMPACT)
            }) {
                Text("推荐")
            }
        }
        return
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onPaneModeCycle) {
                Text(
                    when (paneMode) {
                        TabletSecondaryPaneMode.EXPANDED -> "半开"
                        TabletSecondaryPaneMode.COMPACT -> "收起"
                        TabletSecondaryPaneMode.COLLAPSED -> "展开"
                    }
                )
            }
        }

        // Tab 栏
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        // 内容区域
        when (selectedTab) {
            0 -> {
                // 评论列表
                val listState = rememberLazyListState()
                
                // 加载更多检测
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val totalItems = layoutInfo.totalItemsCount
                        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleItemIndex >= totalItems - 3 && !commentState.isRepliesLoading
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) commentViewModel.loadComments()
                }
                
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        // 排序/筛选栏
                        item {
                            CommentSortFilterBar(
                                count = commentState.replyCount,
                                sortMode = commentState.sortMode,
                                onSortModeChange = { mode ->
                                    commentViewModel.setSortMode(mode)
                                    scope.launch {
                                        com.android.purebilibili.core.store.SettingsManager
                                            .setCommentDefaultSortMode(context, mode.apiMode)
                                    }
                                }
                            )
                        }
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(14.dp),
                                onClick = {
                                    viewModel.clearReplyingTo()
                                    viewModel.showCommentInputDialog()
                                }
                            ) {
                                Text(
                                    text = "写评论，直接和 UP 主交流",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                        
                        // 评论列表
                        items(
                            items = commentState.replies,
                            key = { "reply_${it.rpid}" }
                        ) { reply ->
                            // [新增] 使用 DissolvableVideoCard 包裹 (平板端也需要)
                            com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard(
                                isDissolving = reply.rpid in commentState.dissolvingIds,
                                onDissolveComplete = { commentViewModel.deleteComment(reply.rpid) },
                                cardId = "comment_${reply.rpid}",
                                modifier = Modifier.padding(bottom = 1.dp)
                            ) {
                                ReplyItemView(
                                    item = reply,
                                    emoteMap = success.emoteMap,
                                    upMid = success.info.owner.mid,
                                    onClick = {},
                                    onSubClick = { commentViewModel.openSubReply(it) },
                                    onTimestampClick = { positionMs ->
                                        playerState.player.seekTo(positionMs)
                                        playerState.player.play()
                                    },
                                    onImagePreview = { images, index, rect, textContent ->
                                        previewImages = images
                                        previewInitialIndex = index
                                        sourceRect = rect
                                        previewTextContent = textContent
                                        showImagePreview = true
                                    },
                                    // [新增] 点赞按钮
                                    onLikeClick = { commentViewModel.likeComment(reply.rpid) },
                                    isLiked = reply.action == 1 || reply.rpid in commentState.likedComments,
                                    onReplyClick = {
                                        viewModel.setReplyingTo(reply)
                                        viewModel.showCommentInputDialog()
                                    },
                                    // [新增] 删除按钮
                                    onDeleteClick = if (commentState.currentMid > 0 && reply.mid == commentState.currentMid) {
                                        { commentViewModel.startDissolve(reply.rpid) }
                                    } else null,
                                    onUrlClick = openCommentUrl,
                                    // [新增] 头像点击
                                    onAvatarClick = { mid -> mid.toLongOrNull()?.let { onUpClick(it) } }
                                )
                            }
                        }
                        
                        // 加载指示器
                        if (commentState.isRepliesLoading) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CupertinoActivityIndicator()
                                }
                            }
                        }
                    }

                    if (commentState.replies.isEmpty() && !commentState.isRepliesLoading) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无评论",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "先看看相关推荐",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { selectedTab = 1 }) {
                                Text("切换到相关推荐")
                            }
                        }
                    }

                    // 🟢 [平板适配] "只看UP主" 悬浮按钮 (FAB)
                    FloatingActionButton(
                        onClick = { commentViewModel.toggleUpOnly() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp), 
                        containerColor = if (commentState.upOnlyFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (commentState.upOnlyFilter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                imageVector = if (commentState.upOnlyFilter) io.github.alexzhirkevich.cupertino.icons.CupertinoIcons.Default.CheckmarkCircle else io.github.alexzhirkevich.cupertino.icons.CupertinoIcons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "只看\nUP",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
            1 -> {
                // 相关推荐列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(
                        items = success.related,
                        key = { "related_${it.bvid}" }
                    ) { video ->
                        // 简单的水平布局推荐卡片
                        RelatedVideoItem(
                            video = video,
                            isFollowed = video.owner.mid in success.followingMids,
                            onClick = { 
                                val activity = (context as? android.app.Activity) ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                                val options = activity?.let { 
                                    android.app.ActivityOptions.makeSceneTransitionAnimation(it).toBundle() 
                                }
                                val navOptions = android.os.Bundle(options ?: android.os.Bundle.EMPTY)
                                if (video.cid > 0L) {
                                    navOptions.putLong(VIDEO_NAV_TARGET_CID_KEY, video.cid)
                                }
                                onRelatedVideoClick(video.bvid, navOptions) 
                            }
                        )
                    }
                }
            }
        }
    }
}


/**
 * 📊 平板视频信息区域（可滚动版）
 * 使用 LazyColumn 确保内容过多时可以滚动，避免布局冲突
 */
@Composable
private fun ScrollableVideoInfoSection(
    info: com.android.purebilibili.data.model.response.ViewInfo,
    isFollowing: Boolean,
    isFavorited: Boolean,
    isLiked: Boolean,
    coinCount: Int,
    currentPageIndex: Int,
    downloadProgress: Float?,
    isInWatchLater: Boolean,
    videoTags: List<com.android.purebilibili.data.model.response.VideoTag>,
    ownerFollowerCount: Int?,
    ownerVideoCount: Int?,
    onFollowClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCoinClick: () -> Unit,
    onTripleClick: () -> Unit,
    onPageSelect: (Int) -> Unit,
    onUpClick: (Long) -> Unit,
    onDownloadClick: () -> Unit,
    onWatchLaterClick: () -> Unit,
    onRelatedVideoClick: (String, android.os.Bundle?) -> Unit,
    relatedVideos: List<com.android.purebilibili.data.model.response.RelatedVideo> = emptyList(),
    modifier: Modifier = Modifier
) {
    // 合集展开状态
    var showCollectionSheet by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 合集底部弹窗
    info.ugc_season?.let { season ->
        if (showCollectionSheet) {
            CollectionSheet(
                ugcSeason = season,
                currentBvid = info.bvid,
                onDismiss = { showCollectionSheet = false },
                onEpisodeClick = { episode ->
                    showCollectionSheet = false
                    val activity = (context as? android.app.Activity) ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                    val options = activity?.let { 
                        android.app.ActivityOptions.makeSceneTransitionAnimation(it).toBundle() 
                    }
                    val navOptions = android.os.Bundle(options ?: android.os.Bundle.EMPTY)
                    if (episode.cid > 0L) {
                        navOptions.putLong(VIDEO_NAV_TARGET_CID_KEY, episode.cid)
                    }
                    onRelatedVideoClick(episode.bvid, navOptions)
                }
            )
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 12.dp)
    ) {
        // 1. 视频标题
        item {
            VideoTitleWithDesc(
                info = info,
                videoTags = videoTags
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 2. UP主信息
        item {
            UpInfoSection(
                info = info,
                isFollowing = isFollowing,
                onFollowClick = onFollowClick,
                onUpClick = onUpClick,
                followerCount = ownerFollowerCount,
                videoCount = ownerVideoCount
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. 互动按钮
        item {
            ActionButtonsRow(
                info = info,
                isLiked = isLiked,
                isFavorited = isFavorited,
                coinCount = coinCount,
                isInWatchLater = isInWatchLater,
                onLikeClick = onLikeClick,
                onCoinClick = onCoinClick,
                onFavoriteClick = onFavoriteClick,
                onTripleClick = onTripleClick,
                onDownloadClick = onDownloadClick,
                onWatchLaterClick = onWatchLaterClick,
                downloadProgress = downloadProgress ?: -1f,
                onCommentClick = { /* 平板模式不需要跳转评论 */ }
            )
        }

        // 4. 合集
        item {
            info.ugc_season?.let { season ->
                Spacer(modifier = Modifier.height(12.dp))
                CollectionRow(
                    ugcSeason = season,
                    currentBvid = info.bvid,
                    onClick = { showCollectionSheet = true }
                )
            }
        }

        // 5. 分P选择器
        item {
            if (info.pages.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                PagesSelector(
                    pages = info.pages,
                    currentPageIndex = currentPageIndex,
                    onPageSelect = onPageSelect
                )
            }
        }

        // 6. 简介（展开式）
        item {
            Spacer(modifier = Modifier.height(24.dp))
            if (info.desc.isNotEmpty()) {
                Text(
                    text = "简介",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                var isExpanded by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), // 🎨 修复粉色背景，使用中性灰
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .clickable { isExpanded = !isExpanded }
                        .padding(12.dp)
                ) {
                    Text(
                        text = info.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                    if (info.desc.length > 50) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isExpanded) "收起" else "展开",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        // 7. 更多推荐 (水平滚动)
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "更多推荐",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (relatedVideos.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    items(relatedVideos.take(10)) { video ->
                        Column(
                            modifier = Modifier
                                .width(160.dp)
                                .clickable {
                                    val activity = (context as? android.app.Activity) ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                                    val options = activity?.let {
                                        android.app.ActivityOptions.makeSceneTransitionAnimation(it).toBundle()
                                    }
                                    val navOptions = android.os.Bundle(options ?: android.os.Bundle.EMPTY)
                                    if (video.cid > 0L) {
                                        navOptions.putLong(VIDEO_NAV_TARGET_CID_KEY, video.cid)
                                    }
                                    onRelatedVideoClick(video.bvid, navOptions)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.6f)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                coil.compose.AsyncImage(
                                    model = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(video.pic),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = com.android.purebilibili.core.util.FormatUtils.formatDuration(video.duration),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = video.owner.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无更多推荐",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            // 底部留白，防止被圆角遮挡
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
