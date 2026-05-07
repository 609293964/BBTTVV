package com.bbttvv.app.ui.home

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.response.DynamicItem
import com.bbttvv.app.data.model.response.FollowedLiveRoom
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.Owner
import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.repository.DynamicFollowUpdateFixedItem
import com.bbttvv.app.data.repository.DynamicFollowUpdateItem
import com.bbttvv.app.data.repository.DynamicFollowUpdateUpItem
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.rememberSizedImageModel
import com.bbttvv.app.ui.components.AppTopBarDefaults

private val LiveCardShape = RoundedCornerShape(8.dp)
private val LiveTagShape = RoundedCornerShape(4.dp)
private val FollowUpdateCardShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun DynamicScreen(
    onVideoClick: (VideoItem) -> Unit,
    onLiveClick: (Long) -> Unit = {},
    onOpenUp: (Long) -> Unit = {},
    viewModel: DynamicViewModel,
    onContentRowFocused: (Int) -> Unit = {},
    focusCoordinator: HomeFocusCoordinator,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    gridColumnCount: Int = 4,
    focusState: HomeRecommendGridFocusState = remember { HomeRecommendGridFocusState() },
    topBarHeightPx: Int = 0,
    collapseHeaderEnabled: Boolean = true,
    collapsingHeaderState: HomeCollapsingHeaderState = rememberHomeCollapsingHeaderState()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isHomeTabActive = LocalHomeTabActive.current
    val context = LocalContext.current
    val displayMode by SettingsManager.getDynamicPageDisplayMode(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getDynamicPageDisplayModeSync(context)
        )
    val lifecycleOwner = LocalLifecycleOwner.current
    val visibleLiveUsers = if (displayMode.showLive) uiState.liveUsers else emptyList()
    val visibleFollowUpdateItems = if (displayMode.showFollowUpdates) {
        uiState.followUpdateItems
    } else {
        emptyList()
    }
    val visibleUiState = uiState.copy(
        liveUsers = visibleLiveUsers,
        followUpdateItems = visibleFollowUpdateItems,
        liveErrorMsg = if (displayMode.showLive) uiState.liveErrorMsg else null
    )
    val hasAnyContent = uiState.dynamicVideos.isNotEmpty() ||
        visibleLiveUsers.isNotEmpty() ||
        visibleFollowUpdateItems.isNotEmpty()
    val isLoadingVisibleRows = uiState.isLoadingVideos ||
        (displayMode.showLive && uiState.isLoadingLive) ||
        (displayMode.showFollowUpdates && uiState.isLoadingFollowUpdates)
    val emptyStateMessage = remember(
        uiState.dynamicVideos,
        visibleLiveUsers,
        visibleFollowUpdateItems,
        uiState.videoErrorMsg,
        uiState.liveErrorMsg
    ) {
        resolveDynamicEmptyStateMessage(visibleUiState)
    }
    val partialNoticeMessage = remember(
        uiState.dynamicVideos,
        visibleLiveUsers,
        uiState.videoErrorMsg,
        uiState.liveErrorMsg
    ) {
        resolveDynamicPartialNoticeMessage(visibleUiState)
    }

    LaunchedEffect(isHomeTabActive) {
        if (isHomeTabActive) {
            viewModel.onEnter()
        }
    }

    DisposableEffect(lifecycleOwner, viewModel, isHomeTabActive) {
        if (!isHomeTabActive) {
            onDispose { }
        } else {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFollowUpdates(showLoading = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
        }
    }

    if (!hasAnyContent && isLoadingVisibleRows) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "加载动态、直播与关注...", color = MaterialTheme.colorScheme.onBackground)
        }
    } else if (!hasAnyContent && emptyStateMessage != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyStateMessage,
                color = resolveDynamicStateTextColor(
                    videoErrorMsg = uiState.videoErrorMsg,
                    liveErrorMsg = uiState.liveErrorMsg
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    } else {
        val contentPadding = PaddingValues(
            start = 32.dp,
            end = 32.dp,
            top = 8.dp,
            bottom = AppTopBarDefaults.HomeVideoGridBottomPadding
        )
        val dynamicVideoItems = remember(uiState.dynamicVideos) {
            uiState.dynamicVideos.map { it.toVideoItem() }
        }
        val liveUserIds = remember(visibleLiveUsers) {
            visibleLiveUsers.map { live -> live.roomid }
        }
        val liveUserFocusRequesters = remember(liveUserIds) {
            List(liveUserIds.size) { FocusRequester() }
        }
        val followUpdateKeys = remember(visibleFollowUpdateItems) {
            visibleFollowUpdateItems.map { item -> item.key }
        }
        val followUpdateFocusRequesters = remember(followUpdateKeys) {
            List(followUpdateKeys.size) { FocusRequester() }
        }
        var lastFocusedLiveUserIndex by remember { mutableIntStateOf(0) }
        var lastFocusedFollowUpdateIndex by remember { mutableIntStateOf(0) }
        val hasLiveRow = visibleLiveUsers.isNotEmpty()
        val hasFollowUpdateRow = visibleFollowUpdateItems.isNotEmpty()
        val rowsBeforeVideoGrid = (if (hasLiveRow) 1 else 0) + (if (hasFollowUpdateRow) 1 else 0)

        fun requestLiveUserFocusAt(preferredIndex: Int? = null): Boolean {
            if (!isHomeTabActive) {
                return false
            }
            if (liveUserFocusRequesters.isEmpty()) {
                return false
            }
            val targetIndex = (preferredIndex ?: lastFocusedLiveUserIndex)
                .coerceIn(0, liveUserFocusRequesters.lastIndex)
            return runCatching {
                val focused = liveUserFocusRequesters[targetIndex].requestFocus()
                if (focused) {
                    onContentRowFocused(0)
                    focusCoordinator.onContentRegionFocused(
                        AppTopLevelTab.DYNAMIC,
                        HomeFocusRegion.DynamicLiveUsers
                    )
                }
                focused
            }.getOrDefault(false)
        }

        val requestLiveUserFocus = remember(
            liveUserFocusRequesters,
            lastFocusedLiveUserIndex,
            onContentRowFocused,
            focusCoordinator,
            isHomeTabActive,
        ) {
            {
                requestLiveUserFocusAt()
            }
        }

        fun requestFollowUpdateFocusAt(preferredIndex: Int? = null): Boolean {
            if (!isHomeTabActive) {
                return false
            }
            if (followUpdateFocusRequesters.isEmpty()) {
                return false
            }
            val targetIndex = (preferredIndex ?: lastFocusedFollowUpdateIndex)
                .coerceIn(0, followUpdateFocusRequesters.lastIndex)
            return runCatching {
                val focused = followUpdateFocusRequesters[targetIndex].requestFocus()
                if (focused) {
                    onContentRowFocused(if (hasLiveRow) 1 else 0)
                    focusCoordinator.onContentRegionFocused(
                        AppTopLevelTab.DYNAMIC,
                        HomeFocusRegion.DynamicFollowUpdates
                    )
                }
                focused
            }.getOrDefault(false)
        }

        val requestFollowUpdateFocus = remember(
            followUpdateFocusRequesters,
            lastFocusedFollowUpdateIndex,
            hasLiveRow,
            onContentRowFocused,
            focusCoordinator,
            isHomeTabActive,
        ) {
            {
                requestFollowUpdateFocusAt()
            }
        }

        DisposableEffect(focusCoordinator, requestLiveUserFocus, visibleLiveUsers.isNotEmpty(), isHomeTabActive) {
            val registration = if (isHomeTabActive && visibleLiveUsers.isNotEmpty()) {
                focusCoordinator.registerContentTarget(
                    tab = AppTopLevelTab.DYNAMIC,
                    region = HomeFocusRegion.DynamicLiveUsers,
                    target = object : HomeFocusTarget {
                        override fun tryRequestFocus(): Boolean {
                            return requestLiveUserFocus()
                        }

                        override fun tryRequestFocusForEntry(entryHint: HomeFocusEntryHint): Boolean {
                            return requestLiveUserFocusAt(entryHint.preferredIndex)
                        }
                    }
                )
            } else {
                null
            }
            onDispose {
                registration?.unregister()
            }
        }

        DisposableEffect(
            focusCoordinator,
            requestFollowUpdateFocus,
            visibleFollowUpdateItems.isNotEmpty(),
            isHomeTabActive
        ) {
            val registration = if (isHomeTabActive && visibleFollowUpdateItems.isNotEmpty()) {
                focusCoordinator.registerContentTarget(
                    tab = AppTopLevelTab.DYNAMIC,
                    region = HomeFocusRegion.DynamicFollowUpdates,
                    target = object : HomeFocusTarget {
                        override fun tryRequestFocus(): Boolean {
                            return requestFollowUpdateFocus()
                        }

                        override fun tryRequestFocusForEntry(entryHint: HomeFocusEntryHint): Boolean {
                            return requestFollowUpdateFocusAt(entryHint.preferredIndex)
                        }
                    }
                )
            } else {
                null
            }
            onDispose {
                registration?.unregister()
            }
        }

        val hasHeaderContent = partialNoticeMessage != null || hasLiveRow || hasFollowUpdateRow

        LaunchedEffect(hasHeaderContent) {
            if (!hasHeaderContent) {
                collapsingHeaderState.reset()
            }
        }

        HomeCollapsingHeaderGrid(
            topBarHeightPx = topBarHeightPx,
            state = collapsingHeaderState,
            modifier = Modifier.fillMaxSize(),
            collapseEnabled = collapseHeaderEnabled,
            localHeader = if (hasHeaderContent) {
                {
                    DynamicHeaderRows(
                        partialNoticeMessage = partialNoticeMessage,
                        visibleLiveUsers = visibleLiveUsers,
                        visibleFollowUpdateItems = visibleFollowUpdateItems,
                        contentPadding = contentPadding,
                        liveUserFocusRequesters = liveUserFocusRequesters,
                        followUpdateFocusRequesters = followUpdateFocusRequesters,
                        onLiveClick = onLiveClick,
                        onOpenUp = onOpenUp,
                        onLiveUserFocused = { index ->
                            if (isHomeTabActive) {
                                collapsingHeaderState.reset()
                                lastFocusedLiveUserIndex = index
                                onContentRowFocused(0)
                                focusCoordinator.onContentRegionFocused(
                                    AppTopLevelTab.DYNAMIC,
                                    HomeFocusRegion.DynamicLiveUsers
                                )
                            }
                        },
                        onFollowUpdateFocused = { index ->
                            if (isHomeTabActive) {
                                collapsingHeaderState.reset()
                                lastFocusedFollowUpdateIndex = index
                                onContentRowFocused(if (visibleLiveUsers.isNotEmpty()) 1 else 0)
                                focusCoordinator.onContentRegionFocused(
                                    AppTopLevelTab.DYNAMIC,
                                    HomeFocusRegion.DynamicFollowUpdates
                                )
                            }
                        },
                        onLiveUsersDpadDown = { index ->
                            isHomeTabActive && focusCoordinator.handleDynamicLiveUsersDpadDown(index)
                        },
                        onLiveUsersDpadUp = {
                            if (isHomeTabActive) {
                                collapsingHeaderState.reset()
                                focusCoordinator.handleContentWantsTopBar()
                            } else {
                                false
                            }
                        },
                        onFollowUpdatesDpadUp = {
                            if (isHomeTabActive) {
                                collapsingHeaderState.reset()
                                focusCoordinator.handleDynamicFollowUpdatesDpadUp()
                            } else {
                                false
                            }
                        },
                        onFollowUpdatesDpadDown = { index ->
                            isHomeTabActive && focusCoordinator.handleDynamicFollowUpdatesDpadDown(index)
                        },
                        onConsumeFollowUpdatePrompt = viewModel::consumeFollowUpdatePrompt,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                null
            }
        ) { topPadding, onScrollOffset ->
            // -- Dynamic Videos --
            if (dynamicVideoItems.isNotEmpty()) {
                VideoCardRecyclerGrid(
                    videos = dynamicVideoItems,
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                        end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
                        top = topPadding + if (hasHeaderContent) 0.dp else contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding()
                    ),
                    modifier = Modifier.fillMaxSize(),
                    gridColumnCount = gridColumnCount,
                    focusState = focusState,
                    focusCoordinator = focusCoordinator,
                    focusTab = AppTopLevelTab.DYNAMIC,
                    allowChildDrawingOutsideBounds = false,
                    videoCardRecycledViewPool = videoCardRecycledViewPool,
                    onVerticalScrollOffsetChanged = onScrollOffset,
                    canLoadMore = { !uiState.isLoadingVideos },
                    onLoadMore = viewModel::loadMoreVideos,
                    onMenuRefresh = viewModel::refresh,
                    onVideoFocused = { video, _ ->
                        viewModel.prefetchVideoDetail(video)
                    },
                    onFocusedRowChanged = { rowIndex ->
                        onContentRowFocused(rowIndex + rowsBeforeVideoGrid)
                    },
                    consumeTopRowDpadUp = true,
                    onTopRowDpadUp = {
                        collapsingHeaderState.reset()
                        focusState.resetRememberedFocusToTopForTopBarReturn()
                        focusCoordinator.handleGridTopEdge(AppTopLevelTab.DYNAMIC)
                    },
                    onBackToTopBar = {
                        collapsingHeaderState.reset()
                        focusState.resetRememberedFocusToTopForTopBarReturn()
                        focusCoordinator.handleContentWantsTopBar()
                    },
                    onVideoClick = { videoItem, _ ->
                        viewModel.primeVideoDetail(videoItem)
                        onVideoClick(videoItem)
                    }
                )
            }
        }
    }
}

@Composable
private fun DynamicHeaderRows(
    partialNoticeMessage: String?,
    visibleLiveUsers: List<FollowedLiveRoom>,
    visibleFollowUpdateItems: List<DynamicFollowUpdateItem>,
    contentPadding: PaddingValues,
    liveUserFocusRequesters: List<FocusRequester>,
    followUpdateFocusRequesters: List<FocusRequester>,
    onLiveClick: (Long) -> Unit,
    onOpenUp: (Long) -> Unit,
    onLiveUserFocused: (Int) -> Unit,
    onFollowUpdateFocused: (Int) -> Unit,
    onLiveUsersDpadDown: (Int) -> Boolean,
    onLiveUsersDpadUp: () -> Boolean,
    onFollowUpdatesDpadUp: () -> Boolean,
    onFollowUpdatesDpadDown: (Int) -> Boolean,
    onConsumeFollowUpdatePrompt: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        partialNoticeMessage?.let { notice ->
            DynamicNoticeBanner(
                message = notice,
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                    end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
                    top = contentPadding.calculateTopPadding(),
                    bottom = 12.dp
                )
            )
        }

        if (visibleLiveUsers.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                    end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
                    top = if (partialNoticeMessage == null) contentPadding.calculateTopPadding() else 0.dp,
                    bottom = 12.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                itemsIndexed(
                    items = visibleLiveUsers,
                    key = { _, live -> live.roomid },
                    contentType = { _, _ -> "dynamic_live_user" }
                ) { index, live ->
                    LiveAvatarCard(
                        live = live,
                        onClick = { onLiveClick(live.roomid) },
                        focusRequester = liveUserFocusRequesters.getOrNull(index),
                        onFocus = { onLiveUserFocused(index) },
                        onDpadDown = { onLiveUsersDpadDown(index) },
                        modifier = Modifier.requestTopBarOnDpadUp(
                            enabled = true,
                            requestTopBarFocus = {
                                onLiveUsersDpadUp()
                            }
                        )
                    )
                }
            }
        }

        if (visibleFollowUpdateItems.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(LayoutDirection.Ltr),
                    end = contentPadding.calculateEndPadding(LayoutDirection.Ltr),
                    top = if (
                        partialNoticeMessage == null &&
                        visibleLiveUsers.isEmpty()
                    ) {
                        contentPadding.calculateTopPadding()
                    } else {
                        0.dp
                    },
                    bottom = 12.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusGroup()
            ) {
                itemsIndexed(
                    items = visibleFollowUpdateItems,
                    key = { _, item -> item.key },
                    contentType = { _, item ->
                        when (item) {
                            is DynamicFollowUpdateFixedItem -> "dynamic_follow_fixed"
                            is DynamicFollowUpdateUpItem -> "dynamic_follow_up"
                        }
                    }
                ) { index, item ->
                    DynamicFollowUpdateCard(
                        item = item,
                        onClick = {
                            if (item is DynamicFollowUpdateUpItem) {
                                onConsumeFollowUpdatePrompt(item.mid)
                                onOpenUp(item.mid)
                            }
                        },
                        focusRequester = followUpdateFocusRequesters.getOrNull(index),
                        onFocus = { onFollowUpdateFocused(index) },
                        onDpadUp = onFollowUpdatesDpadUp,
                        onDpadDown = { onFollowUpdatesDpadDown(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicNoticeBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DynamicFollowUpdateCard(
    item: DynamicFollowUpdateItem,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onDpadUp: () -> Boolean = { false },
    onDpadDown: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(FollowUpdateCardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None, Glow.None),
        modifier = modifier
            .then(focusRequesterModifier)
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                if (event.action != AndroidKeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (event.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> onDpadUp()
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> onDpadDown()
                        else -> false
                    }
                }
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.hasFocus
                if (focusState.hasFocus) {
                    onFocus()
                }
            }
            .width(88.dp)
            .graphicsLayer {
                clip = true
                shape = FollowUpdateCardShape
            }
    ) {
        Column(
            modifier = Modifier
                .clip(FollowUpdateCardShape)
                .background(if (isFocused) Color(0x1AFFFFFF) else Color.Transparent)
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (item) {
                is DynamicFollowUpdateFixedItem -> DynamicFollowUpdateFixedIcon(
                    item = item,
                    isFocused = isFocused
                )

                is DynamicFollowUpdateUpItem -> DynamicFollowUpdateAvatar(
                    item = item,
                    isFocused = isFocused
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (item) {
                    is DynamicFollowUpdateFixedItem -> item.title
                    is DynamicFollowUpdateUpItem -> item.name.ifBlank { "UP ${item.mid}" }
                },
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DynamicFollowUpdateFixedIcon(
    item: DynamicFollowUpdateFixedItem,
    isFocused: Boolean
) {
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .graphicsLayer {
                    clip = true
                    shape = CircleShape
                }
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color(0x66FFFFFF),
                    shape = CircleShape
                )
                .background(if (isFocused) Color.White else Color(0x26FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.iconText,
                color = if (isFocused) Color(0xFF111315) else Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DynamicFollowUpdateAvatar(
    item: DynamicFollowUpdateUpItem,
    isFocused: Boolean
) {
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .graphicsLayer {
                    clip = true
                    shape = CircleShape
                }
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color.White else Color(0x66FFFFFF),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = rememberSizedImageModel(
                    url = item.face,
                    widthPx = 112,
                    heightPx = 112
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .graphicsLayer {
                        clip = true
                        shape = CircleShape
                    }
                    .background(Color.DarkGray)
            )
        }

        if (item.hasUpdate) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(13.dp)
                    .background(Color(0xFFFB7299), CircleShape)
                    .border(2.dp, Color(0xFF111315), CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveAvatarCard(
    live: FollowedLiveRoom,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onDpadDown: () -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequesterModifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(LiveCardShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None, Glow.None),
        modifier = modifier
            .then(focusRequesterModifier)
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                if (
                    event.action == AndroidKeyEvent.ACTION_DOWN &&
                    event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onDpadDown()
                } else {
                    false
                }
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.hasFocus
                if (focusState.hasFocus) {
                    onFocus()
                }
            }
            .width(72.dp)
            .graphicsLayer {
                clip = true
                shape = LiveCardShape
            }
    ) {
        Column(
            modifier = Modifier
                .clip(LiveCardShape)
                .background(if (isFocused) Color(0x1AFFFFFF) else Color.Transparent)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                // Circle border (Pink padding)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            clip = true
                            shape = CircleShape
                        }
                        .border(2.dp, Color(0xFFFB7299), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = rememberSizedImageModel(
                            url = live.face,
                            widthPx = 112,
                            heightPx = 112
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                clip = true
                                shape = CircleShape
                            }
                            .background(Color.DarkGray)
                    )
                }

                // Tag LIVE
                Box(
                    modifier = Modifier
                        .padding(bottom = 0.dp)
                        .graphicsLayer {
                            clip = true
                            shape = LiveTagShape
                        }
                        .background(Color(0xFFFB7299))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = live.uname,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Mapper from Dynamic to Recommend structure for the Card
private fun DynamicItem.toVideoItem(): VideoItem {
    val archive = this.modules.module_dynamic?.major?.archive
    val author = this.modules.module_author

    return VideoItem(
        id = archive?.aid?.toLongOrNull() ?: 0L,
        aid = archive?.aid?.toLongOrNull() ?: 0L,
        bvid = archive?.bvid ?: this.id_str,
        pic = archive?.cover ?: "",
        title = archive?.title ?: this.modules.module_dynamic?.major?.opus?.title ?: "动态内容",
        owner = Owner(
            mid = author?.mid ?: 0L,
            name = author?.name ?: "",
            face = author?.face ?: ""
        ),
        stat = Stat(
            // Approximate extraction from string stat like "12.4万"
            view = parseChineseCounterString(archive?.stat?.play),
            danmaku = parseChineseCounterString(archive?.stat?.danmaku)
        ),
        pubdate = author?.pub_ts ?: 0L,
        // For video duration, convert "05:20" to seconds
        duration = parseDurationText(archive?.duration_text)
    )
}

private fun parseChineseCounterString(text: String?): Int {
    if (text.isNullOrBlank()) return 0
    val cleaned = text.trim()
    val multiplier = if (cleaned.endsWith("万")) 10000 else 1
    val numStr = cleaned.removeSuffix("万").trim()
    val num = numStr.toDoubleOrNull() ?: 0.0
    return (num * multiplier).toInt()
}

private fun parseDurationText(text: String?): Int {
    if (text.isNullOrBlank()) return 0
    val parts = text.split(":")
    if (parts.size == 2) {
        val m = parts[0].toIntOrNull() ?: 0
        val s = parts[1].toIntOrNull() ?: 0
        return m * 60 + s
    } else if (parts.size == 3) {
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        val s = parts[2].toIntOrNull() ?: 0
        return h * 3600 + m * 60 + s
    }
    return 0
}

private fun resolveDynamicEmptyStateMessage(uiState: DynamicUiState): String? {
    if (uiState.dynamicVideos.isNotEmpty() || uiState.liveUsers.isNotEmpty()) {
        return null
    }

    val videoErrorMsg = uiState.videoErrorMsg
    if (!videoErrorMsg.isNullOrBlank()) {
        return if (isLoginRequiredDynamicMessage(videoErrorMsg)) {
            "未登录账号，请在“我的”中登录"
        } else {
            "动态加载失败：$videoErrorMsg"
        }
    }

    val liveErrorMsg = uiState.liveErrorMsg
    if (!liveErrorMsg.isNullOrBlank()) {
        return if (isLoginRequiredDynamicMessage(liveErrorMsg)) {
            "未登录账号，请在“我的”中登录"
        } else {
            "关注直播加载失败：$liveErrorMsg"
        }
    }

    return "暂时没有可显示的动态"
}

private fun resolveDynamicPartialNoticeMessage(uiState: DynamicUiState): String? {
    return when {
        uiState.dynamicVideos.isEmpty() &&
            uiState.liveUsers.isNotEmpty() &&
            !uiState.videoErrorMsg.isNullOrBlank() -> {
            if (isLoginRequiredDynamicMessage(uiState.videoErrorMsg)) {
                "动态需要登录后查看，当前仅显示直播"
            } else {
                "动态视频加载失败，当前仅显示直播"
            }
        }

        uiState.dynamicVideos.isNotEmpty() &&
            !uiState.liveErrorMsg.isNullOrBlank() -> {
            if (isLoginRequiredDynamicMessage(uiState.liveErrorMsg)) {
                "关注直播需要登录后查看，当前已显示动态内容"
            } else {
                "关注直播刷新失败，当前已显示动态内容"
            }
        }

        else -> null
    }
}

@Composable
private fun resolveDynamicStateTextColor(
    videoErrorMsg: String?,
    liveErrorMsg: String?
): Color {
    return when {
        isLoginRequiredDynamicMessage(videoErrorMsg) -> MaterialTheme.colorScheme.onBackground
        isLoginRequiredDynamicMessage(liveErrorMsg) -> MaterialTheme.colorScheme.onBackground
        videoErrorMsg.isNullOrBlank() && liveErrorMsg.isNullOrBlank() -> MaterialTheme.colorScheme.onBackground
        else -> MaterialTheme.colorScheme.error
    }
}

private fun isLoginRequiredDynamicMessage(message: String?): Boolean {
    return message?.contains("未登录") == true
}

