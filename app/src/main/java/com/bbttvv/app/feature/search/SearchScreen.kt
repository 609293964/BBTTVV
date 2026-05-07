package com.bbttvv.app.feature.search

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.RecyclerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.data.model.response.SearchType
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvTextInput
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusEntryHint
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.HomeFocusTarget
import com.bbttvv.app.ui.home.HomeRecommendGridFocusState
import com.bbttvv.app.ui.home.LocalHomeTabActive
import com.bbttvv.app.ui.home.VideoCardRecyclerGrid

@Composable
internal fun SearchScreen(
    onBack: () -> Unit,
    onRequestTopBarFocus: () -> Unit = {},
    onOpenVideo: (String, VideoItem) -> Unit,
    onOpenUp: (Long) -> Unit = {},
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isHomeTabActive = LocalHomeTabActive.current
    val searchBarFocusRequester = remember { FocusRequester() }
    val visibleSearchTypes = remember { listOf(SearchType.VIDEO, SearchType.UP) }
    val categoryFocusRequesters = remember {
        List(visibleSearchTypes.size) { FocusRequester() }
    }
    val videoGridFocusState = remember { HomeRecommendGridFocusState() }
    val searchResultsMode = uiState.hasSubmittedQuery || uiState.query.isNotBlank()
    var searchInputHasFocus by remember { mutableStateOf(false) }
    var searchCategoryHasFocus by remember { mutableStateOf(false) }
    var videoGridHasFocus by remember { mutableStateOf(false) }
    var pendingSearchHomeFocus by remember { mutableStateOf(false) }
    val latestSearchInputHasFocus by rememberUpdatedState(searchInputHasFocus)
    val latestSearchCategoryHasFocus by rememberUpdatedState(searchCategoryHasFocus)
    val hotColumnCount = 2

    LaunchedEffect(isHomeTabActive) {
        if (isHomeTabActive) {
            viewModel.onEnter()
        }
    }

    fun noteSearchRegionFocused(region: HomeFocusRegion) {
        if (!isHomeTabActive) return
        val tab = focusTab ?: return
        focusCoordinator?.onContentRegionFocused(tab, region)
        focusCoordinator?.onContentRowFocused(0)
    }

    fun returnToSearchHomeFromResultGrid(): Boolean {
        if (!isHomeTabActive || !searchResultsMode) return false
        viewModel.returnToSearchHome()
        videoGridHasFocus = false
        pendingSearchHomeFocus = true
        return true
    }

    fun requestSearchInputFocus(): Boolean {
        if (!isHomeTabActive) return false
        return runCatching {
            val focused = searchBarFocusRequester.requestFocus()
            if (focused) {
                noteSearchRegionFocused(HomeFocusRegion.SearchInput)
            }
            focused
        }.getOrDefault(false)
    }

    fun requestSearchCategoryFocus(): Boolean {
        if (!isHomeTabActive) return false
        if (!searchResultsMode) return false
        val selectedIndex = visibleSearchTypes.indexOf(uiState.searchType)
            .takeIf { index -> index >= 0 }
            ?: 0
        val requester = categoryFocusRequesters.getOrNull(selectedIndex) ?: return false
        return runCatching {
            val focused = requester.requestFocus()
            if (focused) {
                noteSearchRegionFocused(HomeFocusRegion.SearchCategory)
            }
            focused
        }.getOrDefault(false)
    }

    LaunchedEffect(pendingSearchHomeFocus, searchResultsMode, isHomeTabActive) {
        if (pendingSearchHomeFocus && isHomeTabActive && !searchResultsMode) {
            withFrameNanos { }
            requestSearchInputFocus()
            pendingSearchHomeFocus = false
        }
    }

    LaunchedEffect(searchResultsMode) {
        if (!searchResultsMode) {
            videoGridHasFocus = false
        }
    }

    BackHandler(enabled = isHomeTabActive && searchResultsMode && videoGridHasFocus) {
        returnToSearchHomeFromResultGrid()
    }

    DisposableEffect(focusCoordinator, focusTab, searchBarFocusRequester, isHomeTabActive) {
        val tab = focusTab
        val registration = if (isHomeTabActive && focusCoordinator != null && tab != null) {
            focusCoordinator.registerContentTarget(
                tab = tab,
                region = HomeFocusRegion.SearchInput,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return requestSearchInputFocus()
                    }

                    override fun hasFocus(): Boolean {
                        return latestSearchInputHasFocus
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

    DisposableEffect(focusCoordinator, focusTab, searchResultsMode, uiState.searchType, isHomeTabActive) {
        val tab = focusTab
        val registration = if (isHomeTabActive && focusCoordinator != null && tab != null && searchResultsMode) {
            focusCoordinator.registerContentTarget(
                tab = tab,
                region = HomeFocusRegion.SearchCategory,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return requestSearchCategoryFocus()
                    }

                    override fun hasFocus(): Boolean {
                        return latestSearchCategoryHasFocus
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Centered Capsule Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            CapsuleSearchBar(
                value = uiState.query,
                placeholder = uiState.defaultSearchHint,
                onValueChange = viewModel::onQueryChange,
                onSubmit = viewModel::search,
                onRequestTopBarFocus = onRequestTopBarFocus,
                modifier = Modifier
                    .focusRequester(searchBarFocusRequester)
                    .onFocusChanged { focusState ->
                        searchInputHasFocus = focusState.isFocused
                        if (focusState.isFocused) {
                            videoGridHasFocus = false
                            noteSearchRegionFocused(HomeFocusRegion.SearchInput)
                        }
                    }
                    .width(400.dp)
            )
        }

        if (!uiState.hasSubmittedQuery && uiState.query.isBlank()) {
            // Hot Search Mode
            if (uiState.hotList.isNotEmpty()) {
                Text(
                    text = "当前热搜",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = AppTopBarDefaults.HeaderContentHorizontalPadding, vertical = 12.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(hotColumnCount),
                    contentPadding = PaddingValues(
                        start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        bottom = AppTopBarDefaults.HeaderContentBottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(uiState.hotList, key = { _, hot -> hot.keyword }) { index, hot ->
                        HotSearchKeywordPill(
                            keyword = hot.show_name.ifBlank { hot.keyword },
                            rank = index + 1,
                            onClick = { viewModel.applyKeyword(hot.show_name.ifBlank { hot.keyword }) },
                            onFocusUp = {
                                if (index < hotColumnCount) {
                                    requestSearchInputFocus()
                                } else {
                                    false
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Search Results Mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                SearchCategoryTabs(
                    selected = uiState.searchType,
                    onSelect = viewModel::setSearchType,
                    visibleTypes = visibleSearchTypes,
                    focusRequesters = categoryFocusRequesters,
                    onFocusUp = ::requestSearchInputFocus,
                    onFocusDown = { index ->
                        val tab = focusTab
                        val coordinator = focusCoordinator
                        if (isHomeTabActive && tab != null && coordinator != null) {
                            coordinator.requestRegionFocus(
                                tab = tab,
                                region = HomeFocusRegion.Grid,
                                entryHint = HomeFocusEntryHint(preferredIndex = index),
                            )
                            true
                        } else {
                            false
                        }
                    },
                    onFocusChanged = { focused ->
                        searchCategoryHasFocus = focused
                        if (focused) {
                            videoGridHasFocus = false
                            noteSearchRegionFocused(HomeFocusRegion.SearchCategory)
                        }
                    }
                )
            }

            if (uiState.isSearching && uiState.currentPage == 1) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "搜索中...", color = Color.White)
                }
            } else if (!uiState.errorMessage.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.errorMessage ?: "Search failed.", color = Color(0xFFFB7299))
                }
            } else if (uiState.searchType == SearchType.VIDEO) {
                VideoCardRecyclerGrid(
                    videos = uiState.videoResults,
                    contentPadding = PaddingValues(
                        start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        bottom = AppTopBarDefaults.HeaderContentBottomPadding
                    ),
                    modifier = Modifier.fillMaxSize(),
                    gridColumnCount = 4,
                    scrollResetKey = uiState.searchType,
                    scrollResetOnFirstComposition = false,
                    focusState = videoGridFocusState,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    videoCardRecycledViewPool = videoCardRecycledViewPool,
                    canLoadMore = {
                        uiState.hasMoreResults &&
                            !uiState.isSearching &&
                            !uiState.isLoadingMore
                    },
                    onLoadMore = viewModel::loadMore,
                    onTopRowDpadUp = {
                        requestSearchCategoryFocus()
                    },
                    onBackToTopBar = {
                        returnToSearchHomeFromResultGrid()
                    },
                    onVideoFocused = { _, _ ->
                        videoGridHasFocus = true
                    },
                    onVideoClick = { video, key ->
                        viewModel.primeVideoDetail(video)
                        onOpenVideo(key, video)
                    }
                )
            } else if (uiState.searchType == SearchType.UP) {
                Box(modifier = Modifier.fillMaxSize()) {
                    UpSearchRecyclerGrid(
                        items = uiState.upResults,
                        contentPadding = PaddingValues(
                            start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                            end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                            bottom = AppTopBarDefaults.HeaderContentBottomPadding
                        ),
                        modifier = Modifier.fillMaxSize(),
                        columns = 4,
                        focusCoordinator = focusCoordinator,
                        focusTab = focusTab,
                        canLoadMore = {
                            uiState.hasMoreResults &&
                                !uiState.isSearching &&
                                !uiState.isLoadingMore
                        },
                        onLoadMore = viewModel::loadMore,
                        onTopRowDpadUp = {
                            requestSearchCategoryFocus()
                        },
                        onOpenUp = onOpenUp
                    )
                    if (uiState.isLoadingMore) {
                        Text(
                            text = "加载中...",
                            color = Color.Gray,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(
                        start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                        bottom = AppTopBarDefaults.HeaderContentBottomPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Fallback for non-video/non-UP types, typically would have specific UI designs.
                    // Currently placing a simple fallback generic item here.
                    item {
                        Text(text = "目前仅支持视频卡片呈现", color = Color.Gray, modifier = Modifier.padding(32.dp))
                    }

                    if (uiState.isLoadingMore) {
                        item {
                            Text(
                                text = "加载中...",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(vertical = 16.dp)
                                    .fillMaxWidth()
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
private fun CapsuleSearchBar(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRequestTopBarFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvTextInput(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        singleLine = true,
        onSubmit = onSubmit,
        onMoveFocusUp = {
            onRequestTopBarFocus()
            true
        },
        shape = RoundedCornerShape(999.dp),
        containerColor = Color(0xFF222733),
        focusedContainerColor = Color.White,
        contentColor = Color.White,
        focusedContentColor = Color.Black,
        focusedScale = 1.05f,
        horizontalPadding = 24.dp,
        verticalPadding = 12.dp,
        modifier = modifier
    ) { contentColor ->
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = "Search",
            tint = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

@Composable
private fun SearchCategoryTabs(
    selected: SearchType,
    onSelect: (SearchType) -> Unit,
    visibleTypes: List<SearchType>,
    focusRequesters: List<FocusRequester>,
    onFocusUp: () -> Boolean,
    onFocusDown: (Int) -> Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleTypes.forEachIndexed { index, type ->
            CategoryPill(
                label = type.displayName,
                selected = type == selected,
                onClick = { onSelect(type) },
                onFocusUp = onFocusUp,
                onFocusDown = { onFocusDown(index) },
                onFocusChanged = onFocusChanged,
                modifier = focusRequesters
                    .getOrNull(index)
                    ?.let { requester -> Modifier.focusRequester(requester) }
                    ?: Modifier
            )
            if (index < visibleTypes.lastIndex) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocusUp: () -> Boolean,
    onFocusDown: () -> Boolean,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "backgroundColor"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color(0xFF111418)
            selected -> Color.White
            else -> Color.White.copy(alpha = 0.6f)
        },
        animationSpec = tween(durationMillis = 150),
        label = "textColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else if (selected) 1.03f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "scale"
    )

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(backgroundColor, RoundedCornerShape(999.dp))
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> return@onPreviewKeyEvent onFocusUp()
                        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> return@onPreviewKeyEvent onFocusDown()
                    }
                }
                false
            }
            .padding(
                horizontal = AppTopBarDefaults.PillHorizontalPadding,
                vertical = AppTopBarDefaults.PillVerticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun HotSearchKeywordPill(
    keyword: String,
    rank: Int,
    onClick: () -> Unit,
    onFocusUp: () -> Boolean
) {
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color(0xFF222733),
        animationSpec = tween(150),
        label = "bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black else Color.White,
        animationSpec = tween(150),
        label = "content"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    val rankColor = when (rank) {
        1 -> Color(0xFFFB7299)
        2 -> Color(0xFFFF9800)
        3 -> Color(0xFFFFC107)
        else -> contentColor.copy(alpha = 0.5f)
    }
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                ) {
                    return@onPreviewKeyEvent onFocusUp()
                }
                false
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = rank.toString(),
                color = rankColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = keyword,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
