package com.bbttvv.app.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.core.performance.AppPerformanceTracker
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.response.ReplyItem
import kotlin.math.abs
import kotlinx.coroutines.delay

private const val DetailBackgroundCoverWidthPx = 320
private const val DetailBackgroundCoverHeightPx = 180
private const val DetailBackdropDelayMs = 320L
private const val DetailDeferredSectionsDelayMs = 320L
private val DetailBottomPaddingWithoutComments = 320.dp

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
@Composable
fun DetailScreen(
    bvid: String,
    onBack: () -> Unit,
    onPlay: (String, Long, Long) -> Unit,
    restoreCommentFocusRpid: Long? = null,
    onCommentFocusRestored: (Long) -> Unit = {},
    onOpenCommentReplies: (ReplyItem) -> Unit = {},
    onRelatedVideoClick: (String) -> Unit = {},
    onOpenPublisher: (Long, String, String) -> Unit = { _, _, _ -> }
) {
    val viewModel: DetailViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val videoDetailCommentsEnabled by SettingsManager.getVideoDetailCommentsEnabled(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getVideoDetailCommentsEnabledSync(context)
        )
    val playButtonFocusRequester = remember { FocusRequester() }
    val handlePlayRequest: (String, Long, Long) -> Unit = { playBvid, playAid, playCid ->
        viewModel.prefetchPlaybackDanmaku(playCid)
        onPlay(playBvid, playAid, playCid)
    }

    LaunchedEffect(bvid, videoDetailCommentsEnabled) {
        AppPerformanceTracker.beginSpanOnce("first_detail_open")
        viewModel.loadDetail(
            bvid = bvid,
            loadCommentsEnabled = videoDetailCommentsEnabled
        )
    }
    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        when {
            uiState.isLoading && uiState.viewInfo == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "正在加载详情...", color = Color.White)
                }
            }

            uiState.isError -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMsg ?: "加载详情失败",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            else -> uiState.viewInfo?.let { viewInfo ->
                LaunchedEffect(viewInfo.bvid, uiState.isLoading) {
                    if (uiState.isLoading) return@LaunchedEffect
                    withFrameNanos { }
                    AppPerformanceTracker.endSpanOnce(
                        key = "first_detail_open",
                        milestone = "first_detail_interactive",
                        extras = "bvid=${viewInfo.bvid} related=${uiState.relatedVideos.size} comments=${uiState.comments.items.size}"
                    )
                }
                val detailListState = rememberSaveable(viewInfo.bvid, saver = LazyListState.Saver) {
                    LazyListState()
                }
                val detailFocusCoordinator = remember(viewInfo.bvid) {
                    DetailFocusCoordinator()
                }
                DisposableEffect(detailFocusCoordinator, playButtonFocusRequester) {
                    val registration = detailFocusCoordinator.registerPlayButtonTarget(
                        object : DetailFocusTarget {
                            override fun tryRequestFocus(): Boolean {
                                return runCatching {
                                    playButtonFocusRequester.requestFocus()
                                }.getOrDefault(false)
                            }
                        }
                    )
                    onDispose {
                        registration.unregister()
                    }
                }
                var hasRequestedInitialPlayFocus by rememberSaveable(viewInfo.bvid) {
                    mutableStateOf(false)
                }
                var suppressInitialFocusBringIntoView by rememberSaveable(viewInfo.bvid) {
                    mutableStateOf(true)
                }
                var heroActionRowHasFocus by remember(viewInfo.bvid) {
                    mutableStateOf(false)
                }
                var activeHorizontalFocusRailWidth by remember(viewInfo.bvid) {
                    mutableStateOf<Dp?>(null)
                }
                var relatedVideosRailHasFocus by remember(viewInfo.bvid) {
                    mutableStateOf(false)
                }
                var showDeferredSections by rememberSaveable(viewInfo.bvid) {
                    mutableStateOf(false)
                }
                var showBackdrop by rememberSaveable(viewInfo.bvid) {
                    mutableStateOf(false)
                }
                val hasRestoreCommentFocusTarget = restoreCommentFocusRpid?.let { targetRpid ->
                    uiState.comments.items.any { comment -> comment.rpid == targetRpid }
                } ?: false

                LaunchedEffect(viewInfo.bvid, restoreCommentFocusRpid) {
                    if (restoreCommentFocusRpid != null || hasRequestedInitialPlayFocus) {
                        suppressInitialFocusBringIntoView = false
                        return@LaunchedEffect
                    }
                    detailFocusCoordinator.requestInitialPlayFocus {
                        hasRequestedInitialPlayFocus = true
                        suppressInitialFocusBringIntoView = false
                    }
                    repeat(15) {
                        withFrameNanos { }
                        if (detailFocusCoordinator.drainPendingFocus()) {
                            // Keep the bring-into-view override for two extra frames so
                            // the initial focus hand-off finishes before TV pivot scrolling
                            // becomes active again.
                            withFrameNanos { }
                            withFrameNanos { }
                            return@LaunchedEffect
                        }
                    }
                    suppressInitialFocusBringIntoView = false
                }

                LaunchedEffect(restoreCommentFocusRpid, hasRestoreCommentFocusTarget) {
                    val targetRpid = restoreCommentFocusRpid ?: return@LaunchedEffect
                    if (!hasRestoreCommentFocusTarget) return@LaunchedEffect
                    detailFocusCoordinator.requestRestoreComment(
                        rpid = targetRpid,
                        onRestored = onCommentFocusRestored,
                    )
                    repeat(10) { attempt ->
                        withFrameNanos { }
                        if (detailFocusCoordinator.drainPendingFocus()) {
                            return@LaunchedEffect
                        }
                        if (attempt < 9) {
                            delay(50)
                        }
                    }
                }

                LaunchedEffect(viewInfo.bvid) {
                    showDeferredSections = false
                    delay(DetailDeferredSectionsDelayMs)
                    showDeferredSections = true
                }

                LaunchedEffect(viewInfo.bvid) {
                    showBackdrop = false
                    delay(DetailBackdropDelayMs)
                    showBackdrop = true
                }

                LaunchedEffect(heroActionRowHasFocus) {
                    if (heroActionRowHasFocus) {
                        activeHorizontalFocusRailWidth = null
                        if (detailListState.firstVisibleItemIndex > 0 ||
                            detailListState.firstVisibleItemScrollOffset > 0
                        ) {
                            detailListState.scrollToItem(0)
                        }
                    }
                }

                LaunchedEffect(
                    relatedVideosRailHasFocus,
                    videoDetailCommentsEnabled,
                    showDeferredSections,
                    uiState.relatedVideos.size
                ) {
                    if (
                        relatedVideosRailHasFocus &&
                        !videoDetailCommentsEnabled &&
                        showDeferredSections &&
                        uiState.relatedVideos.isNotEmpty()
                    ) {
                        withFrameNanos { }
                        val relatedItemInfo = detailListState.layoutInfo.visibleItemsInfo
                            .firstOrNull { item -> item.index == 1 }
                            ?: return@LaunchedEffect
                        val bottomMarginPx = with(density) { 40.dp.roundToPx() }
                        val minTopMarginPx = with(density) { 96.dp.roundToPx() }
                        val desiredTopOffset = (
                            detailListState.layoutInfo.viewportEndOffset -
                                relatedItemInfo.size -
                                bottomMarginPx
                            ).coerceAtLeast(minTopMarginPx)
                        val scrollDelta = relatedItemInfo.offset - desiredTopOffset
                        if (abs(scrollDelta) > DetailContainerSizeTolerancePx) {
                            // Keep the related rail fully visible, but pin it as low as possible
                            // so the detail page does not open a large empty gap beneath it.
                            detailListState.scrollBy(scrollDelta.toFloat())
                        }
                    }
                }

                if (showBackdrop) {
                    val backgroundCoverModel = remember(context, viewInfo.pic) {
                        buildSizedImageRequest(
                            context,
                            viewInfo.pic,
                            DetailBackgroundCoverWidthPx,
                            DetailBackgroundCoverHeightPx
                        )
                    }
                    DetailCoverBackdrop(model = backgroundCoverModel)
                }

                DetailInitialFocusScrollScope(
                    disableTvFocusPivot = (
                        suppressInitialFocusBringIntoView &&
                            restoreCommentFocusRpid == null
                        ) || heroActionRowHasFocus,
                    horizontalFocusContainerWidth = activeHorizontalFocusRailWidth
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusProperties {
                                enter = {
                                    if (restoreCommentFocusRpid == null) {
                                        playButtonFocusRequester
                                    } else {
                                        FocusRequester.Default
                                    }
                                }
                            },
                        state = detailListState,
                        contentPadding = PaddingValues(
                            start = 56.dp,
                            top = 48.dp,
                            end = 48.dp,
                            // When comments are hidden, related videos become the last section.
                            // Extra trailing space lets TV focus pivot scroll that final rail upward.
                            bottom = if (videoDetailCommentsEnabled) {
                                40.dp
                            } else {
                                DetailBottomPaddingWithoutComments
                            }
                        ),
                        verticalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        item(key = "hero") {
                            val ownerAvatarModel = remember(context, viewInfo.owner.face) {
                                buildSizedImageRequest(context, viewInfo.owner.face, 96, 96)
                            }
                            val coverModel = remember(context, viewInfo.pic) {
                                buildSizedImageRequest(context, viewInfo.pic, 800, 450)
                            }
                            DetailHeroSection(
                                viewInfo = viewInfo,
                                ownerAvatarModel = ownerAvatarModel,
                                coverModel = coverModel,
                                followerCount = uiState.creatorFollowerCount,
                                accountCoinBalance = uiState.accountCoinBalance,
                                isFollowing = uiState.isFollowing,
                                isFollowActionLoading = uiState.isFollowActionLoading,
                                isLiked = uiState.isLiked,
                                isFavoured = uiState.isFavoured,
                                playButtonFocusRequester = playButtonFocusRequester,
                                onActionRowFocusChanged = { hasFocus ->
                                    heroActionRowHasFocus = hasFocus
                                },
                                onHorizontalRailFocusChanged = { width ->
                                    activeHorizontalFocusRailWidth = width
                                },
                                onPlay = handlePlayRequest,
                                onOpenPublisher = onOpenPublisher,
                                onToggleFollow = viewModel::toggleFollow,
                                onToggleLike = viewModel::toggleLike,
                                onToggleFavourite = viewModel::toggleFavourite
                            )
                        }

                        if (showDeferredSections) {
                            item(key = "related") {
                                RelatedVideosSection(
                                    videos = uiState.relatedVideos,
                                    isLoading = uiState.isRelatedLoading,
                                    onRailFocusChanged = { hasFocus ->
                                        relatedVideosRailHasFocus = hasFocus
                                    },
                                    onHorizontalRailFocusChanged = { width ->
                                        activeHorizontalFocusRailWidth = width
                                    },
                                    onVideoFocus = { },
                                    onVideoClick = { related ->
                                        if (related.bvid.isNotBlank()) {
                                            viewModel.prefetchDetail(related)
                                            onRelatedVideoClick(related.bvid)
                                        }
                                    }
                                )
                            }

                            if (videoDetailCommentsEnabled) {
                                detailCommentsSection(
                                    commentsState = uiState.comments,
                                    focusCoordinator = detailFocusCoordinator,
                                    onSortSelected = viewModel::changeCommentSort,
                                    onRetry = {
                                        viewModel.goToCommentPage(uiState.comments.currentPage)
                                    },
                                    onOpenReplies = onOpenCommentReplies,
                                    onPreviousPage = {
                                        viewModel.goToCommentPage(uiState.comments.currentPage - 1)
                                    },
                                    onNextPage = {
                                        viewModel.goToCommentPage(uiState.comments.currentPage + 1)
                                    }
                                )
                            }
                        } else {
                            item(key = "deferred_sections_loading") {
                                DetailMessageCard(text = "正在准备更多内容...")
                            }
                        }
                    }
                }
            }
        }
    }
}
