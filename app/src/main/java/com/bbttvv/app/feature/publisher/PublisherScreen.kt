package com.bbttvv.app.feature.publisher

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.data.repository.PublisherRepository
import com.bbttvv.app.ui.components.rememberSizedImageModel
import com.bbttvv.app.ui.home.HomeRecommendGridFocusState
import com.bbttvv.app.ui.home.VideoCardRecyclerGrid

private const val PublisherGridColumns = 4
private const val PublisherLoadMorePrefetchItems = 4

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PublisherScreen(
    mid: Long,
    initialName: String? = null,
    initialFace: String? = null,
    onOpenVideo: (String) -> Unit,
    viewModel: PublisherViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timeSortFocusRequester = remember { FocusRequester() }
    val hotSortFocusRequester = remember { FocusRequester() }
    val videoGridFocusState = remember { HomeRecommendGridFocusState() }
    var headerFocusToken by remember { mutableIntStateOf(0) }
    val initialHeader = remember(mid, initialName, initialFace) {
        if (mid > 0L && !initialName.isNullOrBlank()) {
            PublisherRepository.PublisherHeader(
                mid = mid,
                name = initialName,
                face = initialFace.orEmpty()
            )
        } else {
            null
        }
    }

    LaunchedEffect(mid, initialHeader) {
        viewModel.load(mid, initialHeader)
    }

    val requestVisibleVideoFocus = remember(videoGridFocusState) {
        { videoGridFocusState.tryFocusVisibleItem() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111315))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PublisherHeaderSection(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 32.dp, end = 32.dp, bottom = 12.dp),
                header = uiState.header,
                errorMessage = uiState.headerError,
                selectedSort = uiState.selectedSort,
                timeSortFocusRequester = timeSortFocusRequester,
                hotSortFocusRequester = hotSortFocusRequester,
                requestFocusToken = headerFocusToken,
                onNavigateDown = requestVisibleVideoFocus,
                onSortSelected = viewModel::changeSort
            )

            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "正在加载发布者视频...",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                uiState.videoError != null && uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.videoError ?: "发布者视频加载失败",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 48.dp)
                        )
                    }
                }

                uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "该发布者暂时没有可显示的视频",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        VideoCardRecyclerGrid(
                            videos = uiState.items,
                            contentPadding = PaddingValues(
                                start = 32.dp,
                                end = 32.dp,
                                top = 8.dp,
                                bottom = 48.dp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            gridColumnCount = PublisherGridColumns,
                            focusState = videoGridFocusState,
                            scrollResetKey = uiState.selectedSort,
                            loadMorePrefetchItems = PublisherLoadMorePrefetchItems,
                            canLoadMore = {
                                uiState.mid > 0L &&
                                    uiState.hasMore &&
                                    !uiState.isLoadingMore
                            },
                            onLoadMore = viewModel::loadMore,
                            onTopRowDpadUp = {
                                headerFocusToken += 1
                                true
                            },
                            onVideoClick = { video, _ ->
                                viewModel.primeVideoDetail(video)
                                onOpenVideo(video.bvid)
                            }
                        )

                        if (uiState.isLoadingMore) {
                            PublisherFooterMessage(text = "正在加载更多视频...")
                        }

                        if (uiState.videoError != null && uiState.items.isNotEmpty()) {
                            PublisherFooterMessage(
                                text = uiState.videoError ?: "发布者视频加载失败",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PublisherHeaderSection(
    modifier: Modifier = Modifier,
    header: PublisherRepository.PublisherHeader?,
    errorMessage: String?,
    selectedSort: PublisherRepository.PublisherSortOrder,
    timeSortFocusRequester: FocusRequester,
    hotSortFocusRequester: FocusRequester,
    requestFocusToken: Int,
    onNavigateDown: () -> Boolean,
    onSortSelected: (PublisherRepository.PublisherSortOrder) -> Unit
) {
    LaunchedEffect(requestFocusToken, selectedSort) {
        if (requestFocusToken > 0) {
            when (selectedSort) {
                PublisherRepository.PublisherSortOrder.TIME -> timeSortFocusRequester.requestFocus()
                PublisherRepository.PublisherSortOrder.HOT -> hotSortFocusRequester.requestFocus()
            }
        }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PublisherSortButton(
                label = PublisherRepository.PublisherSortOrder.TIME.label,
                selected = selectedSort == PublisherRepository.PublisherSortOrder.TIME,
                focusRequester = timeSortFocusRequester,
                onClick = { onSortSelected(PublisherRepository.PublisherSortOrder.TIME) },
                onNavigateDown = onNavigateDown
            )
            PublisherSortButton(
                label = PublisherRepository.PublisherSortOrder.HOT.label,
                selected = selectedSort == PublisherRepository.PublisherSortOrder.HOT,
                focusRequester = hotSortFocusRequester,
                onClick = { onSortSelected(PublisherRepository.PublisherSortOrder.HOT) },
                onNavigateDown = onNavigateDown
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.6f),
        ) {
            when {
                header != null -> {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = rememberSizedImageModel(
                                url = header.face,
                                widthPx = 88,
                                heightPx = 88
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.DarkGray)
                        )
                        Text(
                            text = header.name.ifBlank { "未知发布者" },
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                !errorMessage.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(modifier = Modifier.height(72.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Spacer(modifier = Modifier.height(96.dp))
                        Text(
                            text = "正在加载发布者信息...",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PublisherSortButton(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onNavigateDown: () -> Boolean
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.hasFocus
            }
            .onPreviewKeyEvent { keyEvent ->
                if (
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                ) {
                    onNavigateDown()
                } else {
                    false
                }
            },
        shape = ClickableSurfaceDefaults.shape(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isFocused -> Color.White
                selected -> Color.White.copy(alpha = 0.22f)
                else -> Color(0x22000000)
            },
            focusedContainerColor = Color.White,
            pressedContainerColor = if (selected) {
                Color.White.copy(alpha = 0.78f)
            } else {
                Color(0x33FFFFFF)
            }
        ),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None, Glow.None)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp, vertical = 1.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
                .background(Color.Transparent)
                .padding(horizontal = 17.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isFocused) Color(0xFF121212) else Color.White,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PublisherFooterMessage(
    text: String,
    color: Color = MaterialTheme.colorScheme.onBackground
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
