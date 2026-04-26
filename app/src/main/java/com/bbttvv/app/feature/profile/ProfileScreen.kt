package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.ui.components.AppTopBarDefaults
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.home.HomeFocusCoordinator
import com.bbttvv.app.ui.home.HomeFocusRegion
import com.bbttvv.app.ui.home.HomeFocusTarget

internal enum class ProfileMenu(val label: String) {
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

internal const val PROFILE_VIDEO_GRID_COLUMNS = 3
internal const val WATCH_LATER_VIDEO_GRID_COLUMNS = 4


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenVideo: (VideoItem) -> Unit,
    onRequestTopBarFocus: () -> Boolean = { false },
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
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
                    }, onRequestTopBarFocus = onRequestTopBarFocus, focusCoordinator = focusCoordinator, focusTab = focusTab)
                    else -> LoggedInProfileLayout(
                        uiState = uiState,
                        selectedMenu = selectedMenu,
                        profileMenus = profileMenus,
                        updateContentOnTabFocusEnabled = updateContentOnTabFocusEnabled,
                        onRequestTopBarFocus = onRequestTopBarFocus,
                        focusCoordinator = focusCoordinator,
                        focusTab = focusTab,
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
private fun LoggedInProfileLayout(
    uiState: ProfileUiState,
    selectedMenu: ProfileMenu,
    profileMenus: List<ProfileMenu>,
    updateContentOnTabFocusEnabled: Boolean,
    onRequestTopBarFocus: () -> Boolean,
    focusCoordinator: HomeFocusCoordinator?,
    focusTab: AppTopLevelTab?,
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
    val profileFocusCoordinator = rememberProfileFocusCoordinator(
        selectedMenu = selectedMenu,
        profileMenus = profileMenus
    )
    var sidebarHasFocus by remember { mutableStateOf(false) }
    val latestSidebarHasFocus by rememberUpdatedState(sidebarHasFocus)

    DisposableEffect(focusCoordinator, focusTab, profileFocusCoordinator) {
        val tab = focusTab
        val registration = if (focusCoordinator != null && tab != null) {
            focusCoordinator.registerContentTarget(
                tab = tab,
                region = HomeFocusRegion.ProfileSidebar,
                target = object : HomeFocusTarget {
                    override fun tryRequestFocus(): Boolean {
                        return profileFocusCoordinator.requestSidebarFocus()
                    }

                    override fun hasFocus(): Boolean {
                        return latestSidebarHasFocus
                    }

                    override fun hasRememberedFocus(): Boolean {
                        return true
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

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        LoggedInSidebar(
            uiState = uiState,
            selectedMenu = selectedMenu,
            profileMenus = profileMenus,
            updateContentOnTabFocusEnabled = updateContentOnTabFocusEnabled,
            onSelectMenu = onSelectMenu,
            onRequestTopBarFocus = onRequestTopBarFocus,
            onSidebarFocusChanged = { focused ->
                sidebarHasFocus = focused
                if (focused && focusCoordinator != null && focusTab != null) {
                    focusCoordinator.onContentRegionFocused(focusTab, HomeFocusRegion.ProfileSidebar)
                    focusCoordinator.onContentRowFocused(0)
                }
            },
            menuListState = profileFocusCoordinator.menuListState,
            menuFocusRequesters = profileFocusCoordinator.menuFocusRequesters,
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
            focusCoordinator = focusCoordinator,
            focusTab = focusTab,
            onRequestSidebarFocus = profileFocusCoordinator::requestSidebarFocus
        )
    }
}

@Composable
internal fun DecorativeBackdrop() {
    Box(
        modifier = Modifier.padding(start = 720.dp, top = 198.dp).size(width = 820.dp, height = 520.dp).background(
            Brush.radialGradient(colors = listOf(Color(0x10FFFFFF), Color.Transparent)),
            RoundedCornerShape(200.dp)
        )
    )
}

@Composable
internal fun CenterStatus(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.titleLarge)
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
    focusCoordinator: HomeFocusCoordinator?,
    focusTab: AppTopLevelTab?,
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
                    onRequestSidebarFocus = onRequestSidebarFocus,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab
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
                    onRequestSidebarFocus = onRequestSidebarFocus,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab
                )
                ProfileMenu.BANGUMI -> ProfilePlaceholderPanel("我的追番", "追番/追剧区域入口已经预留。")
                ProfileMenu.WATCH_LATER -> ProfileWatchLaterPanel(
                    items = uiState.watchLaterItems,
                    totalCount = uiState.watchLaterTotalCount,
                    isLoading = uiState.isWatchLaterLoading,
                    errorMessage = uiState.watchLaterErrorMessage,
                    onOpenVideo = onOpenVideo,
                    onRemoveVideo = onRemoveWatchLater,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    focusRegion = HomeFocusRegion.ProfileContent,
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
