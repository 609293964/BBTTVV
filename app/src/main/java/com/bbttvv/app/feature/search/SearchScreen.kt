package com.bbttvv.app.feature.search

import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.data.model.response.SearchType
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvTextInput
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.HomeFocusTarget
import com.bbttvv.app.ui.home.VideoCardRecyclerGrid

@Composable
internal fun SearchScreen(
    onBack: () -> Unit,
    onRequestTopBarFocus: () -> Unit = {},
    onOpenVideo: (VideoItem) -> Unit,
    onOpenUp: (Long) -> Unit = {},
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchBarFocusRequester = remember { FocusRequester() }
    val visibleSearchTypes = remember { listOf(SearchType.VIDEO, SearchType.UP) }
    val categoryFocusRequesters = remember {
        List(visibleSearchTypes.size) { FocusRequester() }
    }
    val searchResultsMode = uiState.hasSubmittedQuery || uiState.query.isNotBlank()
    var searchInputHasFocus by remember { mutableStateOf(false) }
    var searchCategoryHasFocus by remember { mutableStateOf(false) }
    val latestSearchInputHasFocus by rememberUpdatedState(searchInputHasFocus)
    val latestSearchCategoryHasFocus by rememberUpdatedState(searchCategoryHasFocus)
    val hotColumnCount = 2

    fun noteSearchRegionFocused(region: HomeFocusRegion) {
        val tab = focusTab ?: return
        focusCoordinator?.onContentRegionFocused(tab, region)
        focusCoordinator?.onContentRowFocused(0)
    }

    fun requestSearchInputFocus(): Boolean {
        return runCatching {
            val focused = searchBarFocusRequester.requestFocus()
            if (focused) {
                noteSearchRegionFocused(HomeFocusRegion.SearchInput)
            }
            focused
        }.getOrDefault(false)
    }

    fun requestSearchCategoryFocus(): Boolean {
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

    DisposableEffect(focusCoordinator, focusTab, searchBarFocusRequester) {
        val tab = focusTab
        val registration = if (focusCoordinator != null && tab != null) {
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

    DisposableEffect(focusCoordinator, focusTab, searchResultsMode, uiState.searchType) {
        val tab = focusTab
        val registration = if (focusCoordinator != null && tab != null && searchResultsMode) {
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
                    onFocusChanged = { focused ->
                        searchCategoryHasFocus = focused
                        if (focused) {
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
                    onVideoClick = { video, _ ->
                        viewModel.primeVideoDetail(video)
                        onOpenVideo(video)
                    }
                )
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
                    if (uiState.searchType == SearchType.UP) {
                        itemsIndexed(uiState.upResults, key = { _, up -> up.mid }) { index, up ->
                            UpSearchCard(
                                upId = up.mid,
                                uname = up.uname,
                                fans = up.fans,
                                upic = up.upic,
                                onClick = { onOpenUp(up.mid) },
                                modifier = Modifier.onKeyEvent { keyEvent ->
                                    if (index < 4 &&
                                        keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                        keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                                    ) {
                                        requestSearchCategoryFocus()
                                    } else {
                                        false
                                    }
                                }
                            )
                        }
                    } else {
                        // Fallback for non-video types, typically would have specific UI designs.
                        // Currently placing a simple fallback generic item here.
                        item {
                            Text(text = "目前仅支持视频卡片呈现", color = Color.Gray, modifier = Modifier.padding(32.dp))
                        }
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
                if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                ) {
                    return@onPreviewKeyEvent onFocusUp()
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

@Composable
private fun UpSearchCard(
    upId: Long,
    uname: String,
    fans: Int,
    upic: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else MaterialTheme.colorScheme.surface,
        animationSpec = tween(durationMillis = 150),
        label = "bgColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black else Color.White,
        animationSpec = tween(durationMillis = 150),
        label = "textColor"
    )
    val subtitleColor by animateColorAsState(
        targetValue = if (isFocused) Color.DarkGray else Color.Gray,
        animationSpec = tween(durationMillis = 150),
        label = "subtitleColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "scale"
    )
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = if (upic.startsWith("//")) "https:$upic" else upic,
                contentDescription = uname,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = uname,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                val fansText = if (fans >= 10000) {
                    val w = fans / 10000
                    val r = (fans % 10000) / 1000
                    if (r == 0) "${w}万" else "$w.${r}万"
                } else {
                    fans.toString()
                }
                Text(
                    text = "粉丝: $fansText",
                    color = subtitleColor,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}
