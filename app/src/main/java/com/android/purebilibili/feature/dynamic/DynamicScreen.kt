// 文件路径: feature/dynamic/DynamicScreen.kt
package com.android.purebilibili.feature.dynamic

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import kotlinx.coroutines.flow.distinctUntilChanged // [Fix] Missing import
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import com.android.purebilibili.core.ui.BiliGradientButton
import com.android.purebilibili.core.ui.ComfortablePullToRefreshBox
import com.android.purebilibili.core.ui.EmptyState
import com.android.purebilibili.core.ui.LoadingAnimation
import com.android.purebilibili.core.util.responsiveContentWidth

import com.android.purebilibili.feature.dynamic.components.DynamicCardV2
import com.android.purebilibili.feature.dynamic.components.DynamicSidebar
import com.android.purebilibili.feature.dynamic.components.DynamicTopBarWithTabs
import com.android.purebilibili.feature.dynamic.components.DynamicDisplayMode
import com.android.purebilibili.feature.dynamic.components.DynamicCommentSheet
import com.android.purebilibili.feature.dynamic.components.RepostDialog
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.util.resolveScrollToTopPlan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

val LocalDynamicScrollChannel = compositionLocalOf<Channel<Unit>?> { null }

/**
 *  动态页面 - 支持两种布局模式
 * 
 * 1. SIDEBAR 模式：UP 主列表在左侧边栏
 * 2. HORIZONTAL 模式：UP 主列表在顶部横向滚动
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicScreen(
    viewModel: DynamicViewModel = viewModel(),
    onVideoClick: (String) -> Unit,
    onDynamicDetailClick: (String) -> Unit = {},
    onUserClick: (Long) -> Unit = {},
    onLiveClick: (roomId: Long, title: String, uname: String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    onLoginClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    globalHazeState: dev.chrisbanes.haze.HazeState? = null  // [新增] 全局底栏模糊状态
) {
    val state by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val listState = rememberLazyListState()
    val sidebarUserListState = rememberLazyListState()
    val horizontalUserListState = rememberLazyListState()
    val dynamicScrollChannel = LocalDynamicScrollChannel.current
    
    // 侧边栏状态
    val followedUsers by viewModel.followedUsers.collectAsState()
    val selectedUserId by viewModel.selectedUserId.collectAsState()
    val isSidebarExpanded by viewModel.isSidebarExpanded.collectAsState()
    val showHiddenUsers by viewModel.showHiddenUsers.collectAsState()
    val hiddenUserIds by viewModel.hiddenUserIds.collectAsState()
    
    //  [新增] 评论/点赞/转发状态
    val selectedDynamicId by viewModel.selectedDynamicId.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val commentsLoading by viewModel.commentsLoading.collectAsState()
    val likedDynamics by viewModel.likedDynamics.collectAsState()
    var showRepostDialog by remember { mutableStateOf<String?>(null) }  // 存储要转发的动态ID
    
    // Tab 选择
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("全部", "视频")
    
    //  布局模式状态（侧边栏/横向）
    val displayMode by viewModel.displayMode.collectAsState()
    
    //  [Haze] 模糊状态
    val hazeState = rememberRecoverableHazeState()
    
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density).let { with(density) { it.toDp() } }
    val pullRefreshState = rememberPullToRefreshState()
    
    // GIF 图片加载器
    val context = LocalContext.current
    val gifImageLoader = context.imageLoader
    
    //  [修改] 过滤动态 - 选中用户时使用 userItems
    val filteredItems = remember(state.items, state.userItems, selectedTab, selectedUserId) {
        val baseItems = if (selectedUserId != null) {
            // 使用专门加载的用户动态
            state.userItems
        } else {
            state.items
        }
        var items = baseItems
        if (selectedTab == 1) {
            items = items.filter(::shouldIncludeDynamicItemInVideoTab)
        }
        items.distinctBy { it.id_str }
    }
    val oldContentDividerLabel = remember(selectedTab) {
        if (selectedTab == 1) "以下是之前的视频" else "以下是之前的动态"
    }
    val oldContentDividerIndex = remember(
        filteredItems,
        selectedUserId,
        state.incrementalRefreshBoundaryKey,
        state.incrementalPrependedCount
    ) {
        if (selectedUserId != null) {
            -1
        } else {
            resolveOldContentDividerIndex(
                displayKeys = filteredItems.map(::dynamicFeedItemKey),
                boundaryKey = state.incrementalRefreshBoundaryKey,
                showDivider = state.incrementalPrependedCount > 0
            )
        }
    }
    
    //  [修改] 判断是否加载更多（区分全部动态和用户动态）
    val currentHasMore = if (selectedUserId != null) state.hasUserMore else state.hasMore

    var handledUserListRefreshBoundary by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        state.incrementalRefreshBoundaryKey,
        state.incrementalPrependedCount,
        selectedUserId
    ) {
        val boundaryKey = state.incrementalRefreshBoundaryKey
        if (!shouldResetFollowedUserListToTopOnRefresh(
                boundaryKey = boundaryKey,
                prependedCount = state.incrementalPrependedCount,
                selectedUserId = selectedUserId,
                handledBoundaryKey = handledUserListRefreshBoundary
            )
        ) {
            return@LaunchedEffect
        }
        handledUserListRefreshBoundary = boundaryKey
        sidebarUserListState.scrollToItem(0)
        horizontalUserListState.scrollToItem(0)
    }
    
    // 加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 3 && !state.isLoading && currentHasMore
        }
    }
    //  [埋点] 页面浏览追踪
    LaunchedEffect(Unit) {
        com.android.purebilibili.core.util.AnalyticsHelper.logScreenView("DynamicScreen")
    }
    
    //  [修改] 加载更多 - 区分全部动态和用户动态
    LaunchedEffect(shouldLoadMore, selectedUserId) {
        if (shouldLoadMore) {
            if (selectedUserId != null) {
                viewModel.loadMoreUserDynamics()
            } else {
                viewModel.loadMore()
            }
        }
    }
    
    // [Feature] BottomBar Scroll Hiding for Dynamic Screen
    val setBottomBarVisible = com.android.purebilibili.core.ui.LocalSetBottomBarVisible.current

    LaunchedEffect(dynamicScrollChannel) {
        dynamicScrollChannel?.receiveAsFlow()?.collect {
            val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 50
            if (isAtTop) {
                viewModel.refresh()
            } else {
                val currentIndex = listState.firstVisibleItemIndex
                val plan = resolveScrollToTopPlan(currentIndex)
                plan.preJumpIndex?.let { preJump ->
                    if (currentIndex > preJump) {
                        listState.scrollToItem(preJump)
                    }
                }
                listState.animateScrollToItem(plan.animateTargetIndex)
            }
        }
    }
    
    // 监听列表滚动实现底栏自动隐藏/显示
    var lastFirstVisibleItem by remember { mutableIntStateOf(0) }
    var lastScrollOffset by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(listState) {
        snapshotFlow { 
            Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) 
        }
        .distinctUntilChanged()
        .collect { (firstVisibleItem, scrollOffset) ->
             // 顶部始终显示
             if (firstVisibleItem == 0 && scrollOffset < 100) {
                 setBottomBarVisible(true)
             } else {
                 val isScrollingDown = when {
                     firstVisibleItem > lastFirstVisibleItem -> true
                     firstVisibleItem < lastFirstVisibleItem -> false
                     else -> scrollOffset > lastScrollOffset + 50 // 较小的阈值
                 }
                 val isScrollingUp = when {
                     firstVisibleItem < lastFirstVisibleItem -> true
                     firstVisibleItem > lastFirstVisibleItem -> false
                     else -> scrollOffset < lastScrollOffset - 50
                 }
                 
                 if (isScrollingDown) setBottomBarVisible(false)
                 if (isScrollingUp) setBottomBarVisible(true)
             }
             lastFirstVisibleItem = firstVisibleItem
             lastScrollOffset = scrollOffset
        }
    }
    
    // 离开页面时恢复底栏显示 (特别是进入详情页或其他 Tab)
    DisposableEffect(Unit) {
        onDispose {
            setBottomBarVisible(true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent // 透明背景以显示渐变
    ) { padding ->
        // 背景层 - 自适应 MaterialTheme
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
             // 移除光晕 Canvas，保持纯净背景
        }

        //  [新增] 模式切换动画
        AnimatedContent(
            targetState = displayMode,
            transitionSpec = {
                //  根据切换方向使用不同动画
                val slideDirection = if (targetState == DynamicDisplayMode.HORIZONTAL) {
                    // 从侧边栏切换到横向：向左滑出+淡出，向左滑入+淡入
                    (slideInHorizontally { -it / 4 } + fadeIn(animationSpec = tween(300))) togetherWith
                    (slideOutHorizontally { it / 4 } + fadeOut(animationSpec = tween(200)))
                } else {
                    // 从横向切换到侧边栏：向右滑出+淡出，向右滑入+淡入
                    (slideInHorizontally { it / 4 } + fadeIn(animationSpec = tween(300))) togetherWith
                    (slideOutHorizontally { -it / 4 } + fadeOut(animationSpec = tween(200)))
                }
                slideDirection.using(SizeTransform(clip = false))
            },
            label = "displayModeTransition"
        ) { targetMode ->
            //  根据布局模式选择不同布局
            when (targetMode) {
                DynamicDisplayMode.SIDEBAR -> {
                    // 侧边栏模式
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        // 左侧边栏
                        DynamicSidebar(
                            users = followedUsers,
                            selectedUserId = selectedUserId,
                            isExpanded = isSidebarExpanded,
                            userListState = sidebarUserListState,
                            onUserClick = { clickedUserId ->
                                viewModel.selectUser(
                                    resolveDynamicSelectedUserIdAfterClick(
                                        selectedUserId = selectedUserId,
                                        clickedUserId = clickedUserId
                                    )
                                )
                            },
                            showHiddenUsers = showHiddenUsers,
                            hiddenCount = hiddenUserIds.size,
                            onToggleShowHidden = { viewModel.toggleShowHiddenUsers() },
                            onTogglePin = { viewModel.togglePinUser(it) },
                            onToggleHidden = { viewModel.toggleHiddenUser(it) },
                            onToggleExpand = { viewModel.toggleSidebar() },
                            topPadding = statusBarHeight, // 传入顶部间距
                            onBackClick = onBack // 传入返回事件
                        )
                        
                        // 右侧内容区
                        ComfortablePullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            state = pullRefreshState,
                            modifier = Modifier.fillMaxSize().weight(1f)
                        ) {
                            // 使用 Box 包裹，以便 hazeSource 可以应用于列表
                            Box(modifier = Modifier.fillMaxSize()) {
                                DynamicList(
                                    state = state,
                                    hasMore = currentHasMore,
                                    filteredItems = filteredItems,
                                    listState = listState,
                                    statusBarHeight = statusBarHeight,
                                    topPaddingExtra = resolveDynamicListTopPaddingExtraDp(
                                        isHorizontalMode = false
                                    ).dp,
                                    oldContentDividerIndex = oldContentDividerIndex,
                                    oldContentDividerLabel = oldContentDividerLabel,
                                    onVideoClick = onVideoClick,
                                    onDynamicDetailClick = onDynamicDetailClick,
                                    onUserClick = onUserClick,
                                    onLiveClick = onLiveClick,
                                    onLoginClick = onLoginClick,
                                    gifImageLoader = gifImageLoader,
                                    onCommentClick = { viewModel.openCommentSheet(it) },
                                    onRepostClick = { showRepostDialog = it },
                                    onLikeClick = { dynamicId ->
                                        viewModel.likeDynamic(dynamicId) { _, msg ->
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                 },
                                 likedDynamics = likedDynamics,
                                 modifier = Modifier
                                        .hazeSource(hazeState) // 本地 hazeSource - 顶栏使用（全局源由根层提供）
                                )
                                
                                // 顶栏
                                DynamicTopBarWithTabs(
                                    selectedTab = selectedTab,
                                    tabs = tabs,
                                    onTabSelected = { selectedTab = it },
                                    displayMode = displayMode,
                                    onDisplayModeChange = { viewModel.setDisplayMode(it) },
                                    hazeState = hazeState, // 传入 hazeState
                                    modifier = Modifier.align(Alignment.TopCenter)
                                )
                            }
                            
                            // 错误提示
                            ErrorOverlay(
                                error = state.error,
                                activeItemsCount = filteredItems.size,
                                onLoginClick = onLoginClick,
                                onRetry = { viewModel.refresh() },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                
                DynamicDisplayMode.HORIZONTAL -> {
                    // 横向模式（UP 主列表在顶部）
                    ComfortablePullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize().padding(padding)
                    ) {
                         // 使用 Box 包裹
                        Box {
                             DynamicList(
                                 state = state,
                                 hasMore = currentHasMore,
                                 filteredItems = filteredItems,
                                 listState = listState,
                                 statusBarHeight = statusBarHeight,
                                 topPaddingExtra = resolveDynamicListTopPaddingExtraDp(
                                     isHorizontalMode = true
                                 ).dp,
                                 oldContentDividerIndex = oldContentDividerIndex,
                                 oldContentDividerLabel = oldContentDividerLabel,
                                 onVideoClick = onVideoClick,
                                 onDynamicDetailClick = onDynamicDetailClick,
                                 onUserClick = onUserClick,
                                 onLiveClick = onLiveClick,
                                 onLoginClick = onLoginClick,
                                 gifImageLoader = gifImageLoader,
                                 onCommentClick = { viewModel.openCommentSheet(it) },
                                 onRepostClick = { showRepostDialog = it },
                                 onLikeClick = { dynamicId ->
                                     viewModel.likeDynamic(dynamicId) { _, msg ->
                                         android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                     }
                                 },
                                 likedDynamics = likedDynamics,
                                 modifier = Modifier
                                     .hazeSource(hazeState) // 本地 hazeSource - 顶栏使用（全局源由根层提供）
                             )
                             
                             // 顶部区域：顶栏 + 横向用户列表
                             Column(modifier = Modifier.align(Alignment.TopCenter)) {
                                 // 获取模糊设置
                                 val blurIntensity = currentUnifiedBlurIntensity()
                                 val backgroundAlpha = BlurStyles.getBackgroundAlpha(blurIntensity)
                                 val headerColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha)

                                 // 应用模糊效果到顶部整体区域
                                 Column(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .unifiedBlur(hazeState)
                                         .background(headerColor)
                                 ) {
                                     // 顶栏 - 移除其自带的模糊，使用透明背景
                                     DynamicTopBarWithTabs(
                                         selectedTab = selectedTab,
                                         tabs = tabs,
                                         onTabSelected = { selectedTab = it },
                                         displayMode = displayMode,
                                         onDisplayModeChange = { viewModel.setDisplayMode(it) },
                                         hazeState = null // 禁用内部模糊，由外层统一处理
                                     )
                                     
                                     //  横向 UP 主列表
                                     HorizontalUserList(
                                         users = followedUsers,
                                         selectedUserId = selectedUserId,
                                         listState = horizontalUserListState,
                                         showHiddenUsers = showHiddenUsers,
                                         hiddenCount = hiddenUserIds.size,
                                         onUserClick = { clickedUserId ->
                                             viewModel.selectUser(
                                                 resolveDynamicSelectedUserIdAfterClick(
                                                     selectedUserId = selectedUserId,
                                                     clickedUserId = clickedUserId
                                                 )
                                             )
                                         },
                                         onToggleShowHidden = { viewModel.toggleShowHiddenUsers() },
                                         onTogglePin = { viewModel.togglePinUser(it) },
                                         onToggleHidden = { viewModel.toggleHiddenUser(it) },
                                         modifier = Modifier.fillMaxWidth()
                                     )
                                 }
                             }
                        }
                        
                        ErrorOverlay(
                            error = state.error,
                            activeItemsCount = filteredItems.size,
                            onLoginClick = onLoginClick,
                            onRetry = { viewModel.refresh() },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
    
    //  [新增] 评论弹窗
    selectedDynamicId?.let { dynamicId ->
        //  [修复] 获取动态的总评论数 (使用 ViewModel 中的实时数据)
        val viewModelCount by viewModel.commentTotalCount.collectAsState()
        //  作为 fallback (如果 viewModelCount 为 0)，先显示 item 里的旧数据
        val dynamicItem = remember(dynamicId, state.items, state.userItems) {
            state.items.find { it.id_str == dynamicId } ?: state.userItems.find { it.id_str == dynamicId }
        }
        val staticCount = dynamicItem?.modules?.module_stat?.comment?.count ?: 0
        val totalCount = if (viewModelCount > 0) viewModelCount else staticCount
        
        DynamicCommentSheet(
            comments = comments,
            totalCount = totalCount, 
            isLoading = commentsLoading,
            onDismiss = { viewModel.closeCommentSheet() },
            onPostComment = { message ->
                viewModel.postComment(dynamicId, message) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    //  [新增] 转发弹窗
    showRepostDialog?.let { dynamicId ->
        RepostDialog(
            onDismiss = { showRepostDialog = null },
            onRepost = { content ->
                viewModel.repostDynamic(dynamicId, content) { success, msg ->
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    if (success) showRepostDialog = null
                }
            }
        )
    }
}

/**
 *  动态列表内容
 */
@Composable
private fun DynamicList(
    state: DynamicUiState,
    hasMore: Boolean,
    filteredItems: List<com.android.purebilibili.data.model.response.DynamicItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    statusBarHeight: androidx.compose.ui.unit.Dp,
    topPaddingExtra: androidx.compose.ui.unit.Dp,
    oldContentDividerIndex: Int,
    oldContentDividerLabel: String,
    onVideoClick: (String) -> Unit,
    onDynamicDetailClick: (String) -> Unit,
    onUserClick: (Long) -> Unit,
    onLiveClick: (Long, String, String) -> Unit,
    onLoginClick: () -> Unit,
    gifImageLoader: ImageLoader,
    //  [新增] 动态操作回调
    onCommentClick: (String) -> Unit = {},
    onRepostClick: (String) -> Unit = {},
    onLikeClick: (String) -> Unit = {},
    likedDynamics: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = statusBarHeight + topPaddingExtra,
            bottom = 120.dp // [Modified] Increased to avoid occlusion by persistent BottomBar
        ),
        modifier = modifier.fillMaxSize().responsiveContentWidth()
    ) {
        // 空状态
        if (filteredItems.isEmpty() && !state.isLoading && state.error == null) {
            item {
                EmptyState(
                    message = "暂无动态",
                    actionText = "登录后查看关注 UP主 的动态",
                    modifier = Modifier.height(300.dp)
                )
            }
        }
        
        // 动态卡片列表
        itemsIndexed(filteredItems, key = { _, item -> "dynamic_${dynamicFeedItemKey(item)}" }) { index, item ->
            if (oldContentDividerIndex == index) {
                OldContentDivider(label = oldContentDividerLabel)
            }
            DynamicCardV2(
                item = item,
                onVideoClick = onVideoClick,
                onDynamicDetailClick = onDynamicDetailClick,
                onUserClick = onUserClick,
                onLiveClick = onLiveClick,
                gifImageLoader = gifImageLoader,
                onCommentClick = onCommentClick,
                onRepostClick = onRepostClick,
                onLikeClick = onLikeClick,
                isLiked = likedDynamics.contains(item.id_str)
            )
            //  [优化] 移除分隔线，卡片式设计使用留白分隔
            Spacer(modifier = Modifier.height(2.dp))
        }
        
        // 加载中
        if (shouldShowDynamicLoadingFooter(isLoading = state.isLoading, activeItemsCount = filteredItems.size)) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingAnimation(size = 40.dp)
                }
            }
        }
        
        // 没有更多
        if (shouldShowDynamicNoMoreFooter(hasMore = hasMore, activeItemsCount = filteredItems.size)) {
            item {
                Text(
                    "没有更多了",
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun OldContentDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontSize = 12.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 *  横向 UP 主列表（Telegram 风格）
 */
@Composable
private fun HorizontalUserList(
    users: List<SidebarUser>,
    selectedUserId: Long?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showHiddenUsers: Boolean,
    hiddenCount: Int,
    onUserClick: (Long?) -> Unit,
    onToggleShowHidden: () -> Unit,
    onTogglePin: (Long) -> Unit,
    onToggleHidden: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // 移除 Surface，直接使用 LazyRow 配合传入的 modifier，实现背景透明
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(
            horizontal = 12.dp,
            vertical = resolveHorizontalUserListVerticalPaddingDp().dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
            if (hiddenCount > 0 || showHiddenUsers) {
                item(key = "hidden_toggle") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(4.dp)
                            .combinedClickable(
                                onClick = onToggleShowHidden,
                                onLongClick = onToggleShowHidden
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showHiddenUsers) {
                                    CupertinoIcons.Default.Eye
                                } else {
                                    CupertinoIcons.Default.EyeSlash
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (showHiddenUsers) "隐藏中" else "显示隐藏",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            // UP 主头像列表
            items(users, key = { it.uid }) { user ->
                val isSelected = selectedUserId == user.uid
                var showMenu by remember { mutableStateOf(false) }
                val displayName = if (user.isHidden) {
                    "${user.name.take(4)}(隐)"
                } else {
                    user.name.take(4)
                }

                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { onUserClick(user.uid) },
                                onLongClick = { showMenu = true }
                            )
                            .padding(4.dp)
                            .alpha(if (user.isHidden) 0.5f else 1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected)
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else
                                        Modifier
                                )
                        ) {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(user.face.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it })
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )

                            //  在线状态
                            if (user.isLive) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(12.dp)
                                        .background(androidx.compose.ui.graphics.Color.White, CircleShape)
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(androidx.compose.ui.graphics.Color.Red, CircleShape)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            displayName,  // 最多显示4个字符
                            fontSize = 11.sp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (user.isPinned) "取消置顶" else "置顶") },
                            onClick = {
                                showMenu = false
                                onTogglePin(user.uid)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (user.isHidden) "取消隐藏" else "隐藏") },
                            onClick = {
                                showMenu = false
                                onToggleHidden(user.uid)
                            }
                        )
                    }
                }
            }
        }
    }

/**
 * 错误提示覆盖层
 */
@Composable
private fun ErrorOverlay(
    error: String?,
    activeItemsCount: Int,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (shouldShowDynamicErrorOverlay(error = error, activeItemsCount = activeItemsCount)) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            if (error?.contains("未登录") == true) {
                BiliGradientButton(text = "去登录", onClick = onLoginClick)
            } else {
                BiliGradientButton(text = "重试", onClick = onRetry)
            }
        }
    }
}
