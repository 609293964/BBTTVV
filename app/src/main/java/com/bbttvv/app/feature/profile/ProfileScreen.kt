package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.core.plugin.json.JsonPluginManager
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.StoredAccountSession
import com.bbttvv.app.data.model.response.HistoryData
import com.bbttvv.app.data.model.response.SponsorBlockMarkerMode
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.displayLabel
import com.bbttvv.app.feature.login.LoginQrPanel
import com.bbttvv.app.feature.settings.TvDanmakuSettingsList
import com.bbttvv.app.feature.settings.TvSettingsList
import com.bbttvv.app.ui.components.AppTopBar

import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.TvConfirmDialog
import com.bbttvv.app.ui.components.TvDialog
import com.bbttvv.app.ui.components.TvDialogActionButton
import com.bbttvv.app.ui.components.TvTextInput
import com.bbttvv.app.ui.components.rememberSizedImageModel
import com.bbttvv.app.ui.home.VideoCardRecyclerGrid
import kotlinx.coroutines.launch

private enum class ProfileMenu(val label: String) {
    HISTORY("历史记录"),
    FAVORITE("我的收藏"),
    BANGUMI("我的追番"),
    WATCH_LATER("稍后再看"),
    SWITCH_ACCOUNT("切换账号"),
    CHANGE_ICON("更换图标"),
    SETTINGS("设置"),
    DANMAKU_SETTINGS("弹幕设置"),
    PLUGINS("插件中心"),
    GUIDE("操作说明"),
    LOGOUT("登出")
}

private val profileMenuDisplayOrder = listOf(
    ProfileMenu.HISTORY,
    ProfileMenu.FAVORITE,
    ProfileMenu.BANGUMI,
    ProfileMenu.WATCH_LATER,
    ProfileMenu.SWITCH_ACCOUNT,
    ProfileMenu.CHANGE_ICON,
    ProfileMenu.SETTINGS,
    ProfileMenu.DANMAKU_SETTINGS,
    ProfileMenu.PLUGINS,
    ProfileMenu.GUIDE,
    ProfileMenu.LOGOUT
)

private const val PROFILE_VIDEO_GRID_COLUMNS = 3
private const val WATCH_LATER_VIDEO_GRID_COLUMNS = 4

private data class ProfileMetric(val label: String, val value: String)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onRequestTopBarFocus: () -> Boolean = { false },
    viewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navData = uiState.navData
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateContentOnTabFocusEnabled by SettingsManager.getUpdateContentOnTabFocusEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val watchLaterInTopTabsEnabled by SettingsManager.getWatchLaterInTopTabsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    var selectedMenu by remember { mutableStateOf(ProfileMenu.HISTORY) }
    val profileMenus = remember(watchLaterInTopTabsEnabled) {
        if (watchLaterInTopTabsEnabled) {
            profileMenuDisplayOrder.filterNot { menu -> menu == ProfileMenu.WATCH_LATER }
        } else {
            profileMenuDisplayOrder
        }
    }

    LaunchedEffect(profileMenus, selectedMenu) {
        if (selectedMenu !in profileMenus) {
            selectedMenu = ProfileMenu.HISTORY
        }
    }

    LaunchedEffect(selectedMenu, navData?.mid, navData?.isLogin) {
        if (navData?.isLogin != true) return@LaunchedEffect
        when (selectedMenu) {
            ProfileMenu.HISTORY -> viewModel.ensureHistoryLoaded(force = true)
            ProfileMenu.FAVORITE -> viewModel.ensureFavoriteLoaded()
            ProfileMenu.WATCH_LATER -> viewModel.ensureWatchLaterLoaded(force = true)
            else -> Unit
        }
    }

    DisposableEffect(lifecycleOwner, navData?.mid, navData?.isLogin, selectedMenu) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME || navData?.isLogin != true) {
                return@LifecycleEventObserver
            }
            when (selectedMenu) {
                ProfileMenu.HISTORY -> viewModel.ensureHistoryLoaded(force = true)
                ProfileMenu.WATCH_LATER -> viewModel.ensureWatchLaterLoaded(force = true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        DecorativeBackdrop()
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 46.dp)
            ) {
                when {
                    uiState.isLoading -> CenterStatus("正在加载我的页面...")
                    !uiState.errorMessage.isNullOrBlank() -> CenterStatus(uiState.errorMessage ?: "加载失败")
                    navData == null || !navData.isLogin -> GuestProfileLayout(uiState = uiState, onLoginSuccess = {
                        selectedMenu = ProfileMenu.HISTORY
                        viewModel.refresh()
                    }, onRequestTopBarFocus = onRequestTopBarFocus)
                    else -> LoggedInProfileLayout(
                        uiState = uiState,
                        selectedMenu = selectedMenu,
                        profileMenus = profileMenus,
                        updateContentOnTabFocusEnabled = updateContentOnTabFocusEnabled,
                        onRequestTopBarFocus = onRequestTopBarFocus,
                        onSelectMenu = { menu ->
                            if (menu == ProfileMenu.LOGOUT) {
                                viewModel.logout(removeStoredAccount = false)
                                selectedMenu = ProfileMenu.HISTORY
                            } else {
                                selectedMenu = menu
                            }
                        },
                        onOpenSettings = onOpenSettings,
                        onOpenVideo = { video ->
                            viewModel.primeVideoDetail(video)
                            onOpenVideo(video)
                        },
                        onLoadMoreHistory = viewModel::loadMoreHistory,
                        onSelectFavoriteFolder = viewModel::selectFavoriteFolder,
                        onLoadMoreFavorites = viewModel::loadMoreFavorites,
                        onRemoveWatchLater = viewModel::removeWatchLater,
                        onSwitchAccount = { mid -> viewModel.switchAccount(mid) },
                        onRemoveStoredAccount = { mid -> viewModel.removeStoredAccount(mid) },
                        onPrepareRelogin = {
                            selectedMenu = ProfileMenu.HISTORY
                            viewModel.logout(removeStoredAccount = false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun WatchLaterVideosScreen(
    onOpenVideo: (String, VideoItem) -> Unit,
    onRequestTopBarFocus: () -> Boolean = { false },
    focusCoordinator: com.bbttvv.app.ui.home.HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(uiState.navData?.mid, uiState.navData?.isLogin) {
        if (uiState.navData?.isLogin == true) {
            viewModel.ensureWatchLaterLoaded(force = true)
        }
    }

    DisposableEffect(lifecycleOwner, uiState.navData?.mid, uiState.navData?.isLogin) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.navData?.isLogin == true) {
                viewModel.ensureWatchLaterLoaded(force = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DecorativeBackdrop()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                event.action == AndroidKeyEvent.ACTION_DOWN &&
                    event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP &&
                    onRequestTopBarFocus()
            }
    ) {
        val navData = uiState.navData
        when {
            uiState.isLoading -> CenterStatus("正在加载稍后再看...")
            !uiState.errorMessage.isNullOrBlank() -> CenterStatus(uiState.errorMessage ?: "加载失败")
            navData == null || !navData.isLogin -> CenterStatus("登录后可查看稍后再看")
            else -> ProfileWatchLaterPanel(
                items = uiState.watchLaterItems,
                totalCount = uiState.watchLaterTotalCount,
                isLoading = uiState.isWatchLaterLoading,
                errorMessage = uiState.watchLaterErrorMessage,
                onOpenVideo = { video ->
                    viewModel.primeVideoDetail(video)
                    onOpenVideo(video.bvid.ifBlank { video.aid.toString() }, video)
                },
                onRemoveVideo = viewModel::removeWatchLater,
                showHeader = false,
                gridColumnCount = WATCH_LATER_VIDEO_GRID_COLUMNS,
                contentPadding = PaddingValues(
                    start = AppTopBarDefaults.HeaderContentHorizontalPadding,
                    end = AppTopBarDefaults.HeaderContentHorizontalPadding,
                    top = AppTopBarDefaults.HeaderContentTopPadding,
                    bottom = AppTopBarDefaults.HomeVideoGridBottomPadding
                ),
                focusCoordinator = focusCoordinator,
                focusTab = focusTab,
                resetGridToTop = true,
                onBackToTopBar = onRequestTopBarFocus,
                onRequestSidebarFocus = onRequestTopBarFocus
            )
        }
    }
}

@Composable
private fun GuestProfileLayout(
    uiState: ProfileUiState,
    onLoginSuccess: () -> Unit,
    onRequestTopBarFocus: () -> Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                event.action == AndroidKeyEvent.ACTION_DOWN &&
                    event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP &&
                    onRequestTopBarFocus()
            },
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        GuestProfileSidebar(storedAccountCount = uiState.storedAccountCount, modifier = Modifier.width(378.dp))
        Box(
            modifier = Modifier.fillMaxHeight().padding(start = 22.dp, top = 18.dp).weight(1f)
        ) {
            LoginQrPanel(
                modifier = Modifier.width(760.dp).fillMaxHeight(),
                onLoginSuccess = onLoginSuccess
            )
        }
    }
}

@Composable
private fun LoggedInProfileLayout(
    uiState: ProfileUiState,
    selectedMenu: ProfileMenu,
    profileMenus: List<ProfileMenu>,
    updateContentOnTabFocusEnabled: Boolean,
    onRequestTopBarFocus: () -> Boolean,
    onSelectMenu: (ProfileMenu) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onSelectFavoriteFolder: (String) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onRemoveWatchLater: (VideoItem) -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRemoveStoredAccount: (Long) -> Unit,
    onPrepareRelogin: () -> Unit
) {
    val menuFocusRequesters = remember { ProfileMenu.values().associateWith { FocusRequester() } }
    val menuListState = rememberLazyListState()
    var sidebarFocusRequestToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(sidebarFocusRequestToken) {
        if (sidebarFocusRequestToken == 0) return@LaunchedEffect
        val selectedMenuIndex = profileMenus.indexOf(selectedMenu)
        if (selectedMenuIndex >= 0) {
            val isSelectedMenuVisible = menuListState.layoutInfo.visibleItemsInfo
                .any { item -> item.index == selectedMenuIndex }
            if (!isSelectedMenuVisible) {
                menuListState.scrollToItem(selectedMenuIndex)
                withFrameNanos { }
            }
        }
        menuFocusRequesters[selectedMenu]?.requestFocus()
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        LoggedInSidebar(
            uiState = uiState,
            selectedMenu = selectedMenu,
            profileMenus = profileMenus,
            updateContentOnTabFocusEnabled = updateContentOnTabFocusEnabled,
            onSelectMenu = onSelectMenu,
            onRequestTopBarFocus = onRequestTopBarFocus,
            menuListState = menuListState,
            menuFocusRequesters = menuFocusRequesters,
            modifier = Modifier.width(324.dp)
        )
        ProfileContentPanel(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, top = 2.dp, end = 14.dp),
            uiState = uiState,
            selectedMenu = selectedMenu,
            onOpenSettings = onOpenSettings,
            onOpenVideo = onOpenVideo,
            onLoadMoreHistory = onLoadMoreHistory,
            onSelectFavoriteFolder = onSelectFavoriteFolder,
            onLoadMoreFavorites = onLoadMoreFavorites,
            onRemoveWatchLater = onRemoveWatchLater,
            onSwitchAccount = onSwitchAccount,
            onRemoveStoredAccount = onRemoveStoredAccount,
            onPrepareRelogin = onPrepareRelogin,
            onRequestSidebarFocus = {
                sidebarFocusRequestToken += 1
                true
            }
        )
    }
}

@Composable
private fun DecorativeBackdrop() {
    Box(
        modifier = Modifier.padding(start = 720.dp, top = 198.dp).size(width = 820.dp, height = 520.dp).background(
            Brush.radialGradient(colors = listOf(Color(0x10FFFFFF), Color.Transparent)),
            RoundedCornerShape(200.dp)
        )
    )
}

@Composable
private fun CenterStatus(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}
@Composable
private fun GuestProfileSidebar(storedAccountCount: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight().padding(top = 24.dp, start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(66.dp).background(Color(0x2AFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Text(text = "未", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = "未登录", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Text(text = "右侧会显示 TV 扫码登录二维码，登录后会自动刷新账户信息与历史记录！", color = Color(0xD9FFFFFF), lineHeight = 22.sp)
        ProfileMetricRow(
            items = listOf(
                ProfileMetric("硬币", "0"),
                ProfileMetric("动态", "0"),
                ProfileMetric("账号", storedAccountCount.toString()),
                ProfileMetric("粉丝", "0")
            )
        )
        ProfileGhostHint("打开 TV 扫二维码登录")
        ProfileGhostHint("登录后保留历史、收藏和账号会话")
        ProfileGhostHint("登入成功后自动回到我的页")
    }
}

@Composable
private fun ProfileGhostHint(text: String) {
    Box(modifier = Modifier.fillMaxWidth().background(Color(0x14000000), RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text(text = text, color = Color(0xE6FFFFFF), lineHeight = 21.sp)
    }
}

@Composable
private fun LoggedInSidebar(
    uiState: ProfileUiState,
    selectedMenu: ProfileMenu,
    profileMenus: List<ProfileMenu>,
    updateContentOnTabFocusEnabled: Boolean,
    onRequestTopBarFocus: () -> Boolean,
    onSelectMenu: (ProfileMenu) -> Unit,
    menuListState: androidx.compose.foundation.lazy.LazyListState,
    menuFocusRequesters: Map<ProfileMenu, FocusRequester>,
    modifier: Modifier = Modifier
) {
    val navData = uiState.navData ?: return
    Column(modifier = modifier.fillMaxHeight().padding(top = 14.dp, start = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(
                model = rememberSizedImageModel(
                    url = navData.face,
                    widthPx = 96,
                    heightPx = 96
                ),
                contentDescription = navData.uname,
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = navData.uname, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MiniBadge(text = "LV${navData.level_info.current_level}", backgroundColor = Color(0xFFEF8D39))
                    if (navData.vip.status == 1) {
                        MiniBadge(text = navData.vip.label.text.ifBlank { "大会员" }, backgroundColor = Color(0xFFB86884))
                    }
                }
                ProfileMetricRow(
                    items = listOf(
                        ProfileMetric("硬币", navData.money.toInt().toString()),
                        ProfileMetric("动态", (uiState.navStatData?.dynamic_count ?: 0).toString()),
                        ProfileMetric("关注", (uiState.navStatData?.following ?: 0).toString()),
                        ProfileMetric("粉丝", (uiState.navStatData?.follower ?: uiState.creatorStats?.followerCount ?: 0).toString())
                    )
                )
            }
        }
        LazyColumn(
            state = menuListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp)
        ) {
            items(profileMenus) { menu ->
                ProfileMenuItemRow(
                    label = menu.label,
                    selected = menu == selectedMenu,
                    isDanger = menu == ProfileMenu.LOGOUT,
                    updateContentOnTabFocusEnabled = updateContentOnTabFocusEnabled,
                    focusRequester = menuFocusRequesters[menu],
                    onDpadUp = {
                        if (menu == profileMenus.firstOrNull()) {
                            onRequestTopBarFocus()
                        } else {
                            false
                        }
                    },
                    onClick = { onSelectMenu(menu) }
                )
            }
        }
    }
}

@Composable
private fun MiniBadge(text: String, backgroundColor: Color) {
    Box(modifier = Modifier.background(backgroundColor, RoundedCornerShape(7.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text = text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProfileMetricRow(items: List<ProfileMetric>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = item.value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(text = item.label, color = Color(0xE6FFFFFF), fontSize = 10.sp)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfileMenuItemRow(
    label: String,
    selected: Boolean,
    isDanger: Boolean,
    updateContentOnTabFocusEnabled: Boolean,
    focusRequester: FocusRequester?,
    onDpadUp: () -> Boolean = { false },
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val itemShape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onPreviewKeyEvent { keyEvent ->
                val event = keyEvent.nativeKeyEvent
                event.action == AndroidKeyEvent.ACTION_DOWN &&
                    event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP &&
                    onDpadUp()
            }
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
                if (focusState.isFocused && updateContentOnTabFocusEnabled && !selected && !isDanger) {
                    onClick()
                }
            },
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(itemShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xE8E5EEF4) else Color(0x12000000),
            focusedContainerColor = if (isDanger) Color(0x33F0B4BF) else Color(0xFFF6FAFD)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(
                    width = if (isFocused) 1.dp else if (selected) 1.dp else 0.dp,
                    color = when {
                        isFocused -> Color.White.copy(alpha = 0.92f)
                        selected -> Color.White.copy(alpha = 0.28f)
                        else -> Color.Transparent
                    },
                    shape = itemShape,
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                color = when {
                    isDanger && isFocused -> Color(0xFF5A1020)
                    selected || isFocused -> Color(0xFF111111)
                    isDanger -> Color(0xFFF0B4BF)
                    else -> Color.White
                },
                fontSize = 14.sp,
                fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ProfileContentPanel(
    modifier: Modifier,
    uiState: ProfileUiState,
    selectedMenu: ProfileMenu,
    onOpenSettings: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onSelectFavoriteFolder: (String) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onRemoveWatchLater: (VideoItem) -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRemoveStoredAccount: (Long) -> Unit,
    onPrepareRelogin: () -> Unit,
    onRequestSidebarFocus: () -> Boolean
) {
    Box(modifier = modifier) {
        AnimatedContent(
            targetState = selectedMenu,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 30)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 120))
            },
            label = "ProfileContentPanel",
        ) { menu ->
            when (menu) {
                ProfileMenu.HISTORY -> ProfileHistoryPanel(
                    historyItems = uiState.historyItems,
                    isLoading = uiState.isHistoryLoading,
                    isLoadingMore = uiState.isHistoryLoadingMore,
                    hasMore = uiState.historyHasMore,
                    errorMessage = uiState.historyErrorMessage,
                    onOpenVideo = onOpenVideo,
                    onLoadMore = onLoadMoreHistory,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
                ProfileMenu.SETTINGS -> ProfileSettingsPanel()
                ProfileMenu.DANMAKU_SETTINGS -> ProfileDanmakuSettingsPanel()
                ProfileMenu.FAVORITE -> ProfileFavoritePanel(
                    folders = uiState.favoriteFolders,
                    selectedFolderKey = uiState.selectedFavoriteFolderKey,
                    items = uiState.favoriteItems,
                    isLoading = uiState.isFavoriteLoading,
                    isLoadingMore = uiState.isFavoriteLoadingMore,
                    hasMore = uiState.favoriteHasMore,
                    errorMessage = uiState.favoriteErrorMessage,
                    onSelectFolder = onSelectFavoriteFolder,
                    onOpenVideo = onOpenVideo,
                    onLoadMore = onLoadMoreFavorites,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
                ProfileMenu.BANGUMI -> ProfilePlaceholderPanel("我的追番", "追番/追剧区域入口已经预留。")
                ProfileMenu.WATCH_LATER -> ProfileWatchLaterPanel(
                    items = uiState.watchLaterItems,
                    totalCount = uiState.watchLaterTotalCount,
                    isLoading = uiState.isWatchLaterLoading,
                    errorMessage = uiState.watchLaterErrorMessage,
                    onOpenVideo = onOpenVideo,
                    onRemoveVideo = onRemoveWatchLater,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
                ProfileMenu.SWITCH_ACCOUNT -> SwitchAccountPanel(uiState.storedAccounts, uiState.activeAccountMid, onSwitchAccount, onRemoveStoredAccount, onPrepareRelogin)
                ProfileMenu.CHANGE_ICON -> ChangeIconPanel(onOpenSettings)
                ProfileMenu.PLUGINS -> ProfilePluginCenterPanel()
                ProfileMenu.GUIDE -> ProfileGuidePanel()
                ProfileMenu.LOGOUT -> LogoutPanel()
            }
        }
    }
}

@Composable
private fun ProfileHistoryPanel(
    historyItems: List<HistoryData>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    onOpenVideo: (VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onRequestSidebarFocus: () -> Boolean
) {
    val videoItems = remember(historyItems) { historyItems.map { item -> item.toVideoItem() } }
    val historyScrollResetKey = remember(historyItems) {
        historyItems.firstOrNull()?.let { item ->
            buildString {
                append(item.history?.business.orEmpty().ifBlank { "archive" })
                append(':')
                append(item.history?.bvid.orEmpty().ifBlank { item.history?.oid?.toString().orEmpty() })
                append(':')
                append(item.history?.cid ?: 0L)
                append(':')
                append(item.view_at)
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "历史记录", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        ProfileVideoGrid(
            items = videoItems,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            errorMessage = errorMessage,
            emptyText = "还没有历史记录",
            onOpenVideo = onOpenVideo,
            onLoadMore = onLoadMore,
            showHistoryProgressOnly = true,
            scrollResetKey = historyScrollResetKey,
            onRequestSidebarFocus = onRequestSidebarFocus,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProfileFavoritePanel(
    folders: List<com.bbttvv.app.data.model.response.FavFolder>,
    selectedFolderKey: String?,
    items: List<VideoItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    onSelectFolder: (String) -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onRequestSidebarFocus: () -> Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "我的收藏", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        if (folders.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(
                    items = folders,
                    key = { folder -> resolveProfileFavoriteFolderKey(folder) }
                ) { folder ->
                    val folderKey = resolveProfileFavoriteFolderKey(folder)
                    FavoriteFolderChip(
                        title = resolveProfileFavoriteFolderLabel(folder),
                        count = folder.media_count,
                        selected = folderKey == selectedFolderKey,
                        onClick = { onSelectFolder(folderKey) }
                    )
                }
            }
        }
        ProfileVideoGrid(
            items = items,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            errorMessage = errorMessage,
            emptyText = if (folders.isEmpty()) "还没有收藏夹" else "当前收藏夹还没有内容",
            onOpenVideo = onOpenVideo,
            onLoadMore = onLoadMore,
            scrollResetKey = selectedFolderKey,
            onRequestSidebarFocus = onRequestSidebarFocus,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FavoriteFolderChip(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color(0xE8E5EEF4) else Color(0x12000000),
            focusedContainerColor = Color(0xF2EEF6FB)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = if (selected) Color(0xFF111111) else Color.White,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    color = if (selected) Color(0x99000000) else Color(0xB3FFFFFF),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ProfileWatchLaterPanel(
    items: List<VideoItem>,
    totalCount: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenVideo: (VideoItem) -> Unit,
    onRemoveVideo: (VideoItem) -> Unit,
    focusCoordinator: com.bbttvv.app.ui.home.HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    showHeader: Boolean = true,
    gridColumnCount: Int = PROFILE_VIDEO_GRID_COLUMNS,
    contentPadding: PaddingValues = PaddingValues(top = 4.dp, bottom = 24.dp, end = 8.dp),
    resetGridToTop: Boolean = false,
    onBackToTopBar: (() -> Boolean)? = null,
    onRequestSidebarFocus: () -> Boolean
) {
    var pendingRemoveVideo by remember { mutableStateOf<VideoItem?>(null) }
    var suppressRemoveDialogConfirmKey by remember { mutableStateOf(false) }
    LaunchedEffect(items) {
        val pending = pendingRemoveVideo ?: return@LaunchedEffect
        val pendingKey = pending.bvid.ifBlank { pending.aid.toString() }
        if (items.none { item -> item.bvid.ifBlank { item.aid.toString() } == pendingKey }) {
            pendingRemoveVideo = null
        }
    }

    val watchLaterScrollResetKey = remember(items) {
        items.firstOrNull()?.let { item -> item.bvid.ifBlank { item.aid.toString() } }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showHeader) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "稍后再看", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                if (totalCount > 0) {
                    Text(text = "共 $totalCount 个", color = Color(0xB3FFFFFF), fontSize = 13.sp)
                }
            }
        }
        ProfileVideoGrid(
            items = items,
            isLoading = isLoading,
            isLoadingMore = false,
            hasMore = false,
            errorMessage = errorMessage,
            emptyText = "稍后再看里还没有视频",
            onOpenVideo = onOpenVideo,
            onLoadMore = {},
            onVideoLongClick = { video ->
                suppressRemoveDialogConfirmKey = true
                pendingRemoveVideo = video
            },
            scrollResetKey = watchLaterScrollResetKey,
            focusCoordinator = focusCoordinator,
            focusTab = focusTab,
            gridColumnCount = gridColumnCount,
            contentPadding = contentPadding,
            resetToTop = resetGridToTop,
            onBackToTopBar = onBackToTopBar,
            onRequestSidebarFocus = onRequestSidebarFocus,
            modifier = Modifier.weight(1f)
        )
    }

    pendingRemoveVideo?.let { video ->
        TvConfirmDialog(
            title = "移除稍后再看",
            message = video.title
                .takeIf { it.isNotBlank() }
                ?.let { title -> "确认从稍后再看移除「$title」吗？" }
                ?: "确认从稍后再看移除这个视频吗？",
            onDismissRequest = { pendingRemoveVideo = null },
            suppressConfirmKey = suppressRemoveDialogConfirmKey,
            onSuppressConfirmKeyConsumed = { suppressRemoveDialogConfirmKey = false },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = { pendingRemoveVideo = null }
                )
                TvDialogActionButton(
                    text = "移除",
                    contentColor = Color(0xFFFFD0D8),
                    onClick = {
                        onRemoveVideo(video)
                        pendingRemoveVideo = null
                    }
                )
            }
        )
    }
}

@Composable
private fun ProfileVideoGrid(
    items: List<VideoItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    emptyText: String,
    onOpenVideo: (VideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onVideoLongClick: ((VideoItem) -> Unit)? = null,
    showHistoryProgressOnly: Boolean = false,
    modifier: Modifier = Modifier,
    scrollResetKey: Any? = null,
    focusCoordinator: com.bbttvv.app.ui.home.HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    gridColumnCount: Int = PROFILE_VIDEO_GRID_COLUMNS,
    contentPadding: PaddingValues = PaddingValues(top = 4.dp, bottom = 24.dp, end = 8.dp),
    resetToTop: Boolean = false,
    onBackToTopBar: (() -> Boolean)? = null,
    onRequestSidebarFocus: () -> Boolean = { false }
) {
    val gridFocusState = remember { com.bbttvv.app.ui.home.HomeRecommendGridFocusState() }
    LaunchedEffect(resetToTop, scrollResetKey) {
        if (resetToTop && scrollResetKey != null) {
            gridFocusState.resetRememberedFocusToTop()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            isLoading && items.isEmpty() -> {
                Text("正在加载内容...", color = Color(0xD9FFFFFF))
            }

            !errorMessage.isNullOrBlank() && items.isEmpty() -> {
                Text(errorMessage, color = Color(0xFFFFC4CF))
            }

            items.isEmpty() -> {
                Text(emptyText, color = Color(0xD9FFFFFF))
            }

            else -> {
                VideoCardRecyclerGrid(
                    videos = items,
                    contentPadding = contentPadding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    gridColumnCount = gridColumnCount,
                    focusState = gridFocusState,
                    scrollResetKey = scrollResetKey,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    showHistoryProgressOnly = showHistoryProgressOnly,
                    showDanmakuCount = false,
                    loadMorePrefetchItems = gridColumnCount + 1,
                    canLoadMore = { hasMore && !isLoading && !isLoadingMore },
                    onLoadMore = onLoadMore,
                    onTopRowDpadUp = onRequestSidebarFocus,
                    consumeTopRowDpadUp = false,
                    onBackToTopBar = onBackToTopBar,
                    onLeftEdgeDpadLeft = onRequestSidebarFocus,
                    onVideoLongClick = onVideoLongClick,
                    onVideoClick = { video, _ ->
                        val bvid = video.bvid.trim()
                        if (bvid.isNotEmpty()) {
                            onOpenVideo(video)
                        }
                    }
                )

                if (isLoadingMore) {
                    Text("正在加载更多...", color = Color(0xD9FFFFFF))
                } else if (!errorMessage.isNullOrBlank()) {
                    Text(errorMessage, color = Color(0xFFFFC4CF))
                }
            }
        }
    }
}

@Composable
private fun ProfileSettingsPanel() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "设置", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        TvSettingsList(
            modifier = Modifier.fillMaxSize(),
            compact = true,
            showBuildInfo = false
        )
    }
}

@Composable
private fun ProfileDanmakuSettingsPanel() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "弹幕设置", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        TvDanmakuSettingsList(
            modifier = Modifier.fillMaxSize(),
            compact = true
        )
    }
}

@Composable
private fun ProfilePlaceholderPanel(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Box(modifier = Modifier.fillMaxWidth().background(Color(0x12000000), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Text(text = subtitle, color = Color(0xD9FFFFFF), lineHeight = 22.sp)
        }
    }
}

@Composable
private fun SwitchAccountPanel(
    accounts: List<StoredAccountSession>,
    activeAccountMid: Long?,
    onSwitchAccount: (Long) -> Unit,
    onRemoveStoredAccount: (Long) -> Unit,
    onPrepareRelogin: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(text = "切换账号", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
        if (accounts.isEmpty()) {
            item {
                ProfileInfoCard("当前没有可切换账号", "退出后可以重新扫码登录")
            }
        } else {
            items(
                items = accounts,
                key = { account -> account.mid }
            ) { account ->
                AccountRowCard(
                    account = account,
                    active = activeAccountMid == account.mid,
                    onSwitchAccount = onSwitchAccount,
                    onRemoveStoredAccount = onRemoveStoredAccount
                )
            }
        }
        item {
            ProfilePrimaryAction(text = "重新扫码登录", onClick = onPrepareRelogin)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountRowCard(account: StoredAccountSession, active: Boolean, onSwitchAccount: (Long) -> Unit, onRemoveStoredAccount: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (active) Color(0x1FFFF0F5) else Color(0x12000000),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AsyncImage(
            model = rememberSizedImageModel(
                url = account.face,
                widthPx = 104,
                heightPx = 104
            ),
            contentDescription = account.name,
            modifier = Modifier.size(52.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = account.name.ifBlank { "UID ${account.mid}" }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "UID ${account.mid}", color = Color(0xB3FFFFFF), fontSize = 13.sp)
        }
        if (active) {
            Box(
                modifier = Modifier.widthIn(min = 82.dp).padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "当前账号", color = Color(0xFFFFE7EF), fontWeight = FontWeight.Medium)
            }
        } else {
            AccountActionButton(
                text = "切换",
                contentColor = Color.White,
                onClick = { onSwitchAccount(account.mid) }
            )
        }
        AccountActionButton(
            text = "移除",
            contentColor = Color(0xFFFFBCC8),
            onClick = { onRemoveStoredAccount(account.mid) }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountActionButton(
    text: String,
    contentColor: Color,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x14000000),
            focusedContainerColor = Color(0xF2EEF6FB)
        )
    ) {
        Box(
            modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (focused) Color(0xFF111111) else contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfilePluginCenterPanel() {
    val scope = rememberCoroutineScope()
    val plugins by com.bbttvv.app.core.plugin.PluginManager.pluginsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val jsonPlugins by JsonPluginManager.plugins.collectAsStateWithLifecycle(initialValue = emptyList())
    val filterStats by JsonPluginManager.filterStats.collectAsStateWithLifecycle(initialValue = emptyMap())
    val lastFilteredCount by JsonPluginManager.lastFilteredCount.collectAsStateWithLifecycle(initialValue = 0)
    var expandedPluginId by remember { mutableStateOf<String?>(null) }

    val builtInPlugins = remember(plugins) {
        listOf(
            com.bbttvv.app.feature.plugin.SPONSOR_BLOCK_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.AD_FILTER_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.DANMAKU_ENHANCE_PLUGIN_ID,
            com.bbttvv.app.feature.plugin.TodayWatchPlugin.PLUGIN_ID
        ).mapNotNull { id ->
            plugins.firstOrNull { it.plugin.id == id }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(text = "插件中心", color = Color.White, style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PluginCenterSummaryCard("内置插件", builtInPlugins.size.toString(), Modifier.weight(1f))
                PluginCenterSummaryCard("已启用", (builtInPlugins.count { it.enabled } + jsonPlugins.count { it.enabled }).toString(), Modifier.weight(1f))
                PluginCenterSummaryCard("最近过期", lastFilteredCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text(text = "内置插件", color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        items(builtInPlugins, key = { it.plugin.id }) { pluginInfo ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PluginCenterRowCard(
                    title = pluginInfo.plugin.name,
                    subtitle = pluginInfo.plugin.description,
                    value = buildString {
                        append(if (pluginInfo.enabled) "已启用" else "已关闭")
                        append(" · 配置")
                    },
                    onClick = {
                        expandedPluginId = if (expandedPluginId == pluginInfo.plugin.id) {
                            null
                        } else {
                            pluginInfo.plugin.id
                        }
                    }
                )
                if (expandedPluginId == pluginInfo.plugin.id) {
                    when (val plugin = pluginInfo.plugin) {
                        is com.bbttvv.app.feature.plugin.SponsorBlockPlugin -> {
                            SponsorBlockPluginPanel(
                                plugin = plugin,
                                enabled = pluginInfo.enabled,
                                onToggleEnabled = {
                                    scope.launch {
                                        com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                    }
                                }
                            )
                        }
                        is com.bbttvv.app.feature.plugin.AdFilterPlugin -> {
                            AdFilterPluginPanel(
                                plugin = plugin,
                                enabled = pluginInfo.enabled,
                                onToggleEnabled = {
                                    scope.launch {
                                        com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                    }
                                }
                            )
                        }
                        is com.bbttvv.app.feature.plugin.DanmakuEnhancePlugin -> {
                            DanmakuEnhancePluginPanel(
                                plugin = plugin,
                                enabled = pluginInfo.enabled,
                                onToggleEnabled = {
                                    scope.launch {
                                        com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                    }
                                }
                            )
                        }
                        is com.bbttvv.app.feature.plugin.TodayWatchPlugin -> {
                            TodayWatchPluginPanel(
                                plugin = plugin,
                                enabled = pluginInfo.enabled,
                                onToggleEnabled = {
                                    scope.launch {
                                        com.bbttvv.app.core.plugin.PluginManager.setEnabled(plugin.id, !pluginInfo.enabled)
                                    }
                                }
                            )
                        }
                        else -> {
                            ProfileInfoCard("暂不支持的插件类型", "这个插件已经注册进插件系统，但当前插件中心还没有给它单独的 TV 配置面板。", compact = true)
                        }
                    }
                }
            }
        }
        item {
            Text(text = "外部规则插件", color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        if (jsonPlugins.isEmpty()) {
            item { ProfileInfoCard("当前还没有导入外部插件", "内置规则插件已经就位，后续如果还有外部 JSON 规则插件，可以继续在这里向下扩展。", compact = true) }
        } else {
            items(jsonPlugins, key = { it.plugin.id }) { loaded ->
                PluginCenterRowCard(
                    title = loaded.plugin.name,
                    subtitle = buildString {
                        append(resolveJsonPluginTypeLabel(loaded.plugin.type))
                        append(" · ")
                        append(loaded.plugin.version)
                        filterStats[loaded.plugin.id]?.takeIf { it > 0 }?.let { count ->
                            append(" · 过滤 ")
                            append(count)
                            append("已")
                        }
                    },
                    value = if (loaded.enabled) "已启用" else "已关闭",
                    onClick = { JsonPluginManager.setEnabled(loaded.plugin.id, !loaded.enabled) }
                )
            }
        }
    }
}

@Composable
private fun SponsorBlockPluginPanel(
    plugin: com.bbttvv.app.feature.plugin.SponsorBlockPlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val config by plugin.configState.collectAsStateWithLifecycle(initialValue = com.bbttvv.app.feature.plugin.SponsorBlockConfig())
    val nextMarkerMode = remember(config.markerMode) {
        val modes = SponsorBlockMarkerMode.entries
        modes[(modes.indexOf(config.markerMode) + 1).mod(modes.size)]
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PluginCenterRowCard(
            title = "插件状态",
            subtitle = "控制空降助手是否参与视频详情页的 SponsorBlock 跳过逻辑。",
            value = if (enabled) "点击关闭" else "点击切换",
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "手动跳过",
            subtitle = "命中片头、片尾或恰饭片段时直接跳过；关闭后会显示手动跳过提示。",
            value = if (config.autoSkip) "已开启" else "已关闭",
            onClick = { plugin.setAutoSkip(!config.autoSkip) }
        )
        PluginCenterRowCard(
            title = "进度条提示",
            subtitle = "切换 SponsorBlock 片段在进度条上的提示策略，便于后续预览标记。",
            value = "${config.markerMode.displayLabel()} -> ${nextMarkerMode.displayLabel()}",
            onClick = { plugin.setMarkerMode(nextMarkerMode) }
        )
        PluginCenterRowCard(
            title = "手动跳过提示",
            subtitle = "关闭后不再显示右下角“按上键跳过”提示，但不会影响进度条标记和自动跳过。",
            value = if (config.showSkipPrompt) "已开启" else "已关闭",
            onClick = { plugin.setShowSkipPrompt(!config.showSkipPrompt) }
        )
        PluginCenterRowCard(
            title = "广告 / 恰饭",
            subtitle = "命中 SponsorBlock 的赞助片段时参与跳过。",
            value = if (config.skipSponsor) "已跳过" else "已保留",
            onClick = { plugin.setSkipSponsor(!config.skipSponsor) }
        )
        PluginCenterRowCard(
            title = "片头动画",
            subtitle = "跳过视频Logo、开场口播和长片头。",
            value = if (config.skipIntro) "已跳过" else "已保留",
            onClick = { plugin.setSkipIntro(!config.skipIntro) }
        )
        PluginCenterRowCard(
            title = "片尾动画",
            subtitle = "跳过结尾彩蛋前的常规片尾片段。",
            value = if (config.skipOutro) "已跳过" else "已保留",
            onClick = { plugin.setSkipOutro(!config.skipOutro) }
        )
        PluginCenterRowCard(
            title = "互动提示",
            subtitle = "跳过无意义的连播投币点赞和下一期提示等互动片段。",
            value = if (config.skipInteraction) "已跳过" else "已保留",
            onClick = { plugin.setSkipInteraction(!config.skipInteraction) }
        )
        PluginCenterRowCard(
            title = "互动推广",
            subtitle = "跳过关注、群号、店铺和其他互动推广口播。",
            value = if (config.skipSelfPromo) "已跳过" else "已保留",
            onClick = { plugin.setSkipSelfPromo(!config.skipSelfPromo) }
        )
        PluginCenterRowCard(
            title = "预告 / 回顾",
            subtitle = "默认跳过片尾广告和重复回顾，默认关闭以免误伤剧情内容。",
            value = if (config.skipPreview) "已跳过" else "已保留",
            onClick = { plugin.setSkipPreview(!config.skipPreview) }
        )
        PluginCenterRowCard(
            title = "无关片段",
            subtitle = "默认跳过跑题片段，默认关闭，适合你想更激进一点的时候。",
            value = if (config.skipFiller) "已跳过" else "已保留",
            onClick = { plugin.setSkipFiller(!config.skipFiller) }
        )
        ProfileInfoCard("已接入播放链路", "现在插件管理器里的空降助手和实际播放器走的是同一套开关，打开后会直接作用到视频播放。", compact = true)
    }
}

@Composable
private fun AdFilterPluginPanel(
    plugin: com.bbttvv.app.feature.plugin.AdFilterPlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val config by plugin.configState.collectAsStateWithLifecycle(initialValue = com.bbttvv.app.feature.plugin.AdFilterConfig())
    val blockedUps by plugin.blockedUps.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddNameDialog by remember { mutableStateOf(false) }
    var inputName by remember { mutableStateOf("") }
    var showAddMidDialog by remember { mutableStateOf(false) }
    var inputMid by remember { mutableStateOf("") }
    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var inputKeyword by remember { mutableStateOf("") }
    var showMinViewCountDialog by remember { mutableStateOf(false) }
    var inputMinViewCount by remember { mutableStateOf("") }
    val blockedUpMidSet = remember(blockedUps) { blockedUps.map { it.mid }.toSet() }
    val manualBlockedMids = remember(config.blockedUpMids, blockedUpMidSet) {
        config.blockedUpMids.filterNot { it in blockedUpMidSet }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PluginCenterRowCard(
            title = "插件状态",
            subtitle = "首页推荐、热门分区会先经过去广告增强再展示。",
            value = if (enabled) "点击关闭" else "点击切换",
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "营销推广过滤",
            subtitle = "过滤商业合作、恰饭推广、官方活动等营销内容。",
            value = if (config.filterSponsored) "已开启" else "已关闭",
            onClick = { plugin.setFilterSponsored(!config.filterSponsored) }
        )
        PluginCenterRowCard(
            title = "标题党过滤",
            subtitle = "过滤夸张标题、震惊体和常见钓鱼式标题。",
            value = if (config.filterClickbait) "已开启" else "已关闭",
            onClick = { plugin.setFilterClickbait(!config.filterClickbait) }
        )
        PluginCenterRowCard(
            title = "低播放量过滤",
            subtitle = "默认过滤播放量低于 1000 的内容，适合你想让瀑布流更干净时开启。",
            value = if (config.filterLowQuality) "已开启" else "已关闭",
            onClick = { plugin.setFilterLowQuality(!config.filterLowQuality) }
        )
        PluginCenterRowCard(
            title = "低播放量阈值",
            subtitle = "低播放量过滤的实际生效，当前小于这个值的视频会被隐藏。",
            value = "${config.minViewCount}",
            onClick = {
                inputMinViewCount = config.minViewCount.toString()
                showMinViewCountDialog = true
            }
        )
        PluginCenterRowCard(
            title = "添加名称黑名单",
            subtitle = "按 UP 名称做模糊匹配拉黑，适合先用遥控器快速录入关键字。",
            value = if (config.blockedUpNames.isEmpty()) "去添加" else "个",
            onClick = { showAddNameDialog = true }
        )
        PluginCenterRowCard(
            title = "添加 MID 黑名单",
            subtitle = "按 UID/MID 精确拉黑，适合你已经知道该 UP 主数字 ID 的情况。",
            value = if (manualBlockedMids.isEmpty()) "去添加" else "个",
            onClick = { showAddMidDialog = true }
        )
        PluginCenterRowCard(
            title = "标题屏蔽词",
            subtitle = "按关键字直接过滤标题，适合屏蔽某类长期不想看的内容。",
            value = if (config.blockedKeywords.isEmpty()) "去添加" else "个",
            onClick = { showAddKeywordDialog = true }
        )
        if (config.blockedUpNames.isNotEmpty()) {
            Text(text = "名称黑名单", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            config.blockedUpNames.forEach { blockedName ->
                PluginCenterRowCard(
                    title = blockedName,
                    subtitle = "按名称匹配的黑名单规则，点按后移除。",
                    value = "移除",
                    onClick = { plugin.removeBlockedUpName(blockedName) }
                )
            }
        }
        if (manualBlockedMids.isNotEmpty()) {
            Text(text = "MID 黑名单", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            manualBlockedMids.forEach { blockedMid ->
                PluginCenterRowCard(
                    title = "MID $blockedMid",
                    subtitle = "按数字 MID 精确匹配，命中后首页和热门都会直接隐藏。",
                    value = "移除",
                    onClick = { plugin.removeBlockedUpMid(blockedMid) }
                )
            }
        }
        if (blockedUps.isNotEmpty()) {
            Text(text = "已拉黑该 UP", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            blockedUps.forEach { blocked ->
                PluginCenterRowCard(
                    title = blocked.name,
                    subtitle = "· 这类拉黑会直接命中 UID",
                    value = "移除",
                    onClick = {
                        scope.launch {
                            plugin.unblockUploader(blocked.mid)
                        }
                    }
                )
            }
        }
        if (config.blockedKeywords.isNotEmpty()) {
            Text(text = "标题屏蔽词", color = Color(0xE6FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            config.blockedKeywords.forEach { blockedKeyword ->
                PluginCenterRowCard(
                    title = blockedKeyword,
                    subtitle = "命中这个关键词的标题会被直接过滤，点按后移除。",
                    value = "移除",
                    onClick = { plugin.removeBlockedKeyword(blockedKeyword) }
                )
            }
        }
        if (
            config.blockedUpNames.isEmpty() &&
            manualBlockedMids.isEmpty() &&
            blockedUps.isEmpty() &&
            config.blockedKeywords.isEmpty()
        ) {
            ProfileInfoCard("规则列表还是空的", "你可以在这里维护名称黑名单、MID 黑名单和标题屏蔽词；后续接到视频详情页的“拉黑该 UP 主”动作后，这里也会直接同步显示。", compact = true)
        }
    }

    if (showAddNameDialog) {
        TvDialog(
            title = "添加名称黑名单",
            onDismissRequest = {
                showAddNameDialog = false
                inputName = ""
            },
            content = {
                TvTextInput(
                    value = inputName,
                    onValueChange = { inputName = it },
                    singleLine = true,
                    label = "UP 名称"
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showAddNameDialog = false
                        inputName = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        plugin.addBlockedUpName(inputName)
                        showAddNameDialog = false
                        inputName = ""
                    }
                )
            }
        )
    }

    if (showAddMidDialog) {
        TvDialog(
            title = "添加 MID 黑名单",
            onDismissRequest = {
                showAddMidDialog = false
                inputMid = ""
            },
            content = {
                TvTextInput(
                    value = inputMid,
                    onValueChange = { inputMid = it.filter { char -> char.isDigit() } },
                    singleLine = true,
                    label = "UP MID",
                    keyboardType = KeyboardType.Number
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showAddMidDialog = false
                        inputMid = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        inputMid.toLongOrNull()?.let(plugin::addBlockedUpMid)
                        showAddMidDialog = false
                        inputMid = ""
                    }
                )
            }
        )
    }

    if (showAddKeywordDialog) {
        TvDialog(
            title = "添加标题屏蔽词",
            onDismissRequest = {
                showAddKeywordDialog = false
                inputKeyword = ""
            },
            content = {
                TvTextInput(
                    value = inputKeyword,
                    onValueChange = { inputKeyword = it },
                    singleLine = true,
                    label = "关键字"
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showAddKeywordDialog = false
                        inputKeyword = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        plugin.addBlockedKeyword(inputKeyword)
                        showAddKeywordDialog = false
                        inputKeyword = ""
                    }
                )
            }
        )
    }

    if (showMinViewCountDialog) {
        TvDialog(
            title = "设置低播放量阈值",
            onDismissRequest = {
                showMinViewCountDialog = false
                inputMinViewCount = ""
            },
            content = {
                TvTextInput(
                    value = inputMinViewCount,
                    onValueChange = { inputMinViewCount = it.filter { char -> char.isDigit() } },
                    singleLine = true,
                    label = "最低播放量",
                    keyboardType = KeyboardType.Number
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        showMinViewCountDialog = false
                        inputMinViewCount = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        inputMinViewCount.toIntOrNull()?.let(plugin::setMinViewCount)
                        showMinViewCountDialog = false
                        inputMinViewCount = ""
                    }
                )
            }
        )
    }
}

@Composable
private fun DanmakuEnhancePluginPanel(
    plugin: com.bbttvv.app.feature.plugin.DanmakuEnhancePlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val config by plugin.configState.collectAsStateWithLifecycle(initialValue = com.bbttvv.app.feature.plugin.DanmakuEnhanceConfig())
    var editingField by remember { mutableStateOf<String?>(null) }
    var editingValue by remember { mutableStateOf("") }

    fun openEditor(field: String, value: String) {
        editingField = field
        editingValue = value
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PluginCenterRowCard(
            title = "插件状态",
            subtitle = "会作用到当前播放器的弹幕载入链路，打开后支持热刷新。",
            value = if (enabled) "点击关闭" else "点击切换",
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "关键词屏蔽",
            subtitle = "按关键字隐藏剧透、前方高能等你不想看到的弹幕。",
            value = if (config.enableFilter) "已开启" else "已关闭",
            onClick = { plugin.setEnableFilter(!config.enableFilter) }
        )
        PluginCenterRowCard(
            title = "同传高亮",
            subtitle = "把同传、翻译类弹幕高亮出来，方便电视端远距离阅读。",
            value = if (config.enableHighlight) "已开启" else "已关闭",
            onClick = { plugin.setEnableHighlight(!config.enableHighlight) }
        )
        PluginCenterRowCard(
            title = "屏蔽关键字",
            subtitle = config.blockedKeywords.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
            onClick = { openEditor("blocked_keywords", config.blockedKeywords) }
        )
        PluginCenterRowCard(
            title = "屏蔽用户 ID",
            subtitle = config.blockedUserIds.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
            onClick = { openEditor("blocked_users", config.blockedUserIds) }
        )
        PluginCenterRowCard(
            title = "高亮关键字",
            subtitle = config.highlightKeywords.ifBlank { "当前生效词，点按后编辑。" },
            value = "编辑",
            onClick = { openEditor("highlight_keywords", config.highlightKeywords) }
        )
    }

    if (editingField != null) {
        TvDialog(
            title = when (editingField) {
                "blocked_keywords" -> "编辑屏蔽关键字"
                "blocked_users" -> "编辑屏蔽用户 ID"
                else -> "编辑高亮关键字"
            },
            onDismissRequest = {
                editingField = null
                editingValue = ""
            },
            content = {
                TvTextInput(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    label = "使用英文逗号分隔",
                    singleLine = false,
                    minLines = 3
                )
            },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = {
                        editingField = null
                        editingValue = ""
                    }
                )
                TvDialogActionButton(
                    text = "保存",
                    onClick = {
                        when (editingField) {
                            "blocked_keywords" -> plugin.setBlockedKeywords(editingValue)
                            "blocked_users" -> plugin.setBlockedUserIds(editingValue)
                            "highlight_keywords" -> plugin.setHighlightKeywords(editingValue)
                        }
                        editingField = null
                        editingValue = ""
                    }
                )
            }
        )
    }
}

@Composable
private fun TodayWatchPluginPanel(
    plugin: com.bbttvv.app.feature.plugin.TodayWatchPlugin,
    enabled: Boolean,
    onToggleEnabled: () -> Unit
) {
    val config by plugin.configState.collectAsStateWithLifecycle(
        initialValue = com.bbttvv.app.feature.plugin.TodayWatchPluginConfig()
    )
    var showResetDialog by remember { mutableStateOf(false) }

    val nextMode = remember(config.currentMode) {
        when (config.currentMode) {
            com.bbttvv.app.ui.home.TodayWatchMode.RELAX -> com.bbttvv.app.ui.home.TodayWatchMode.LEARN
            com.bbttvv.app.ui.home.TodayWatchMode.LEARN -> com.bbttvv.app.ui.home.TodayWatchMode.RELAX
        }
    }
    val nextUpRankLimit = remember(config.upRankLimit) {
        nextCycledOption(config.upRankLimit, listOf(3, 5, 8, 10))
    }
    val nextQueueBuildLimit = remember(config.queueBuildLimit) {
        nextCycledOption(config.queueBuildLimit, listOf(12, 20, 30, 40))
    }
    val nextQueuePreviewLimit = remember(config.queuePreviewLimit, config.queueBuildLimit) {
        nextCycledOption(
            config.queuePreviewLimit,
            listOf(4, 6, 8, 10).filter { it <= config.queueBuildLimit }
        )
    }
    val nextHistorySampleLimit = remember(config.historySampleLimit) {
        nextCycledOption(config.historySampleLimit, listOf(40, 80, 120))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PluginCenterRowCard(
            title = "插件状态",
            subtitle = "启用后，会在首页导航栏最外挂载「推荐单」版块。",
            value = if (enabled) "点击关闭" else "点击切换",
            onClick = onToggleEnabled
        )
        PluginCenterRowCard(
            title = "默认模式",
            subtitle = "进入推荐单页时默认选中的二级标签。",
            value = "${config.currentMode.label} -> ${nextMode.label}",
            onClick = { plugin.setCurrentMode(nextMode) }
        )
        PluginCenterRowCard(
            title = "偏好 UP 榜数量",
            subtitle = "控制头部摘要里显示多少位近期偏好创作者。",
            value = "${config.upRankLimit} -> $nextUpRankLimit",
            onClick = { plugin.setUpRankLimit(nextUpRankLimit) }
        )
        PluginCenterRowCard(
            title = "队列生成长度",
            subtitle = "用于算法内部排序的种子队列规模，越大越容易补齐多样性。",
            value = "${config.queueBuildLimit} -> $nextQueueBuildLimit",
            onClick = { plugin.setQueueBuildLimit(nextQueueBuildLimit) }
        )
        PluginCenterRowCard(
            title = "预览展示条数",
            subtitle = "推荐单页当前行展示前几条视频卡片。",
            value = "${config.queuePreviewLimit} -> $nextQueuePreviewLimit",
            onClick = { plugin.setQueuePreviewLimit(nextQueuePreviewLimit) }
        )
        PluginCenterRowCard(
            title = "历史样本数",
            subtitle = "冷启动生成推荐单时最多抽取最近多少条历史记录。",
            value = "${config.historySampleLimit} -> $nextHistorySampleLimit",
            onClick = { plugin.setHistorySampleLimit(nextHistorySampleLimit) }
        )
        PluginCenterRowCard(
            title = "显示偏好 UP 榜",
            subtitle = "在推荐单头部展示你近期更偏好的创作者摘要。",
            value = if (config.showUpRank) "已开启" else "已关闭",
            onClick = { plugin.setShowUpRank(!config.showUpRank) }
        )
        PluginCenterRowCard(
            title = "显示推荐理由",
            subtitle = "在视频卡片下方显示轻松向 / 学习向等推荐理由。",
            value = if (config.showReasonHint) "已开启" else "已关闭",
            onClick = { plugin.setShowReasonHint(!config.showReasonHint) }
        )
        PluginCenterRowCard(
            title = "清空画像与反馈",
            subtitle = "清空后台学到的创作者偏好和不感兴趣反馈，推荐会重新学习。",
            value = "立即清空",
            onClick = { showResetDialog = true }
        )
        ProfileInfoCard("已接入独立推荐页", "当前插件不是推荐单页顶部卡片，而是独立的外置 Tab，支持模式切换、手动刷新和 MENU 不感兴趣。", compact = true)
    }

    if (showResetDialog) {
        TvConfirmDialog(
            title = "清空推荐画像",
            message = "确认清空当前推荐画像与不感兴趣反馈吗？",
            onDismissRequest = { showResetDialog = false },
            actions = {
                TvDialogActionButton(
                    text = "取消",
                    onClick = { showResetDialog = false }
                )
                TvDialogActionButton(
                    text = "确认",
                    contentColor = Color(0xFFFFD0D8),
                    onClick = {
                        plugin.clearPersonalizationData()
                        showResetDialog = false
                    }
                )
            }
        )
    }
}

@Composable
private fun PluginCenterSummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0x12000000), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, color = Color(0xB3FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PluginCenterRowCard(title: String, subtitle: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x12000000), focusedContainerColor = Color(0xE9E6EEF4))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, color = Color(0xB3FFFFFF), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = value, color = Color(0xE8FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChangeIconPanel(onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "更换图标", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        ProfileInfoCard("图标入口已预留", "后续可以在完整设置页中继续接你的 TV 图标方案！")
        ProfilePrimaryAction(text = "打开设置页", onClick = onOpenSettings)
    }
}

@Composable
private fun ProfileGuidePanel() {
    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "操作说明", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        ProfileInfoCard("方向键换焦点", "顶部把焦点移到推荐或动态时会直接切页，无需回车确认")
        ProfileInfoCard("扫码登录", "未登录时右侧显示二维码，登入成功后自动刷新我的页面状态")
    }
}

@Composable
private fun LogoutPanel() {
    CenterStatus("已退出登录")
}

@Composable
private fun ProfileInfoCard(title: String, value: String, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x12000000), RoundedCornerShape(if (compact) 24.dp else 28.dp))
            .padding(
                horizontal = if (compact) 18.dp else 22.dp,
                vertical = if (compact) 14.dp else 20.dp
            )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = if (compact) 15.sp else 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = Color(0xD9FFFFFF),
                fontSize = if (compact) 11.sp else 14.sp,
                lineHeight = if (compact) 16.sp else 21.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProfilePrimaryAction(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x12000000), focusedContainerColor = Color(0xE9E6EEF4))
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(text = text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun resolveJsonPluginTypeLabel(type: String): String {
    return when (type.lowercase()) {
        "feed" -> "信息流控"
        "danmaku" -> "弹幕规则"
        else -> "插件"
    }
}

private fun <T> nextCycledOption(current: T, options: List<T>): T {
    if (options.isEmpty()) return current
    val currentIndex = options.indexOf(current)
    return if (currentIndex < 0) {
        options.first()
    } else {
        options[(currentIndex + 1) % options.size]
    }
}

