package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.RecyclerView
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
import com.bbttvv.app.ui.home.LocalHomeTabActive

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
    ProfileMenu.SETTINGS,
    ProfileMenu.DANMAKU_SETTINGS,
    ProfileMenu.PLUGINS,
    ProfileMenu.SWITCH_ACCOUNT,
    ProfileMenu.CHANGE_ICON,
    ProfileMenu.GUIDE,
    ProfileMenu.LOGOUT
)

internal const val PROFILE_VIDEO_GRID_COLUMNS = 3
internal const val WATCH_LATER_VIDEO_GRID_COLUMNS = 4


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenVideo: (String, VideoItem) -> Unit,
    onRequestTopBarFocus: () -> Boolean = { false },
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    viewModel: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isHomeTabActive = LocalHomeTabActive.current
    val navData = uiState.navData
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateContentOnTabFocusEnabled by SettingsManager.getUpdateContentOnTabFocusEnabled(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val watchLaterInTopTabsEnabled by SettingsManager.getWatchLaterInTopTabsEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    var selectedMenu by rememberSaveable { mutableStateOf(ProfileMenu.HISTORY) }
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

    LaunchedEffect(isHomeTabActive) {
        if (isHomeTabActive) {
            viewModel.onEnter()
        }
    }

    LaunchedEffect(selectedMenu, navData?.mid, navData?.isLogin, isHomeTabActive) {
        if (!isHomeTabActive) return@LaunchedEffect
        if (navData?.isLogin != true) return@LaunchedEffect
        when (selectedMenu) {
            ProfileMenu.HISTORY -> viewModel.ensureHistoryLoaded(force = true)
            ProfileMenu.FAVORITE -> viewModel.ensureFavoriteLoaded()
            ProfileMenu.BANGUMI -> viewModel.ensureBangumiLoaded()
            ProfileMenu.WATCH_LATER -> viewModel.ensureWatchLaterLoaded(force = true)
            else -> Unit
        }
    }

    DisposableEffect(lifecycleOwner, navData?.mid, navData?.isLogin, selectedMenu, isHomeTabActive) {
        if (!isHomeTabActive) {
            onDispose { }
        } else {
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
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 46.dp, end = 24.dp)
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
                        videoCardRecycledViewPool = videoCardRecycledViewPool,
                        onSelectMenu = { menu ->
                            if (menu == ProfileMenu.LOGOUT) {
                                viewModel.logout(removeStoredAccount = false)
                                selectedMenu = ProfileMenu.HISTORY
                            } else {
                                selectedMenu = menu
                            }
                        },
                        onOpenSettings = onOpenSettings,
                        onOpenVideo = { focusKey, video ->
                            viewModel.primeVideoDetail(video)
                            onOpenVideo(focusKey, video)
                        },
                        onLoadMoreHistory = viewModel::loadMoreHistory,
                        onSelectFavoriteFolder = viewModel::selectFavoriteFolder,
                        onLoadMoreFavorites = viewModel::loadMoreFavorites,
                        onLoadMoreBangumi = viewModel::loadMoreBangumi,
                        onUnfollowBangumi = viewModel::unfollowBangumiInProfile,
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
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    viewModel: ProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isHomeTabActive = LocalHomeTabActive.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(isHomeTabActive) {
        if (isHomeTabActive) {
            viewModel.onEnter()
        }
    }

    LaunchedEffect(uiState.navData?.mid, uiState.navData?.isLogin, isHomeTabActive) {
        if (!isHomeTabActive) return@LaunchedEffect
        if (uiState.navData?.isLogin == true) {
            viewModel.ensureWatchLaterLoaded(force = true)
        }
    }

    DisposableEffect(lifecycleOwner, uiState.navData?.mid, uiState.navData?.isLogin, isHomeTabActive) {
        if (!isHomeTabActive) {
            onDispose { }
        } else {
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
    }

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
                onOpenVideo = { focusKey, video ->
                    viewModel.primeVideoDetail(video)
                    onOpenVideo(focusKey, video)
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
                videoCardRecycledViewPool = videoCardRecycledViewPool,
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
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    onSelectMenu: (ProfileMenu) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String, VideoItem) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onSelectFavoriteFolder: (String) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onLoadMoreBangumi: () -> Unit,
    onUnfollowBangumi: (Long) -> Unit,
    onRemoveWatchLater: (VideoItem) -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRemoveStoredAccount: (Long) -> Unit,
    onPrepareRelogin: () -> Unit
) {
    val profileFocusCoordinator = rememberProfileFocusCoordinator(
        selectedMenu = selectedMenu,
        profileMenus = profileMenus
    )
    val isHomeTabActive = LocalHomeTabActive.current
    var sidebarHasFocus by remember { mutableStateOf(false) }
    val latestSidebarHasFocus by rememberUpdatedState(sidebarHasFocus)

    fun requestProfileContentFocus(): Boolean {
        val tab = focusTab ?: return false
        val coordinator = focusCoordinator ?: return false
        if (!isHomeTabActive) return false
        coordinator.requestRegionFocus(tab, HomeFocusRegion.ProfileContent)
        return true
    }

    DisposableEffect(focusCoordinator, focusTab, profileFocusCoordinator, isHomeTabActive) {
        val tab = focusTab
        val registration = if (isHomeTabActive && focusCoordinator != null && tab != null) {
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
                if (isHomeTabActive && focused && focusCoordinator != null && focusTab != null) {
                    focusCoordinator.onContentRegionFocused(focusTab, HomeFocusRegion.ProfileSidebar)
                    focusCoordinator.onContentRowFocused(0)
                }
            },
            menuListState = profileFocusCoordinator.menuListState,
            menuFocusRequesters = profileFocusCoordinator.menuFocusRequesters,
            onRequestContentFocus = ::requestProfileContentFocus,
            modifier = Modifier.width(324.dp)
        )
        ProfileContentPanel(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, top = 2.dp),
            uiState = uiState,
            selectedMenu = selectedMenu,
            onOpenSettings = onOpenSettings,
            onOpenVideo = onOpenVideo,
            onLoadMoreHistory = onLoadMoreHistory,
            onSelectFavoriteFolder = onSelectFavoriteFolder,
            onLoadMoreFavorites = onLoadMoreFavorites,
            onLoadMoreBangumi = onLoadMoreBangumi,
            onUnfollowBangumi = onUnfollowBangumi,
            onRemoveWatchLater = onRemoveWatchLater,
            onSwitchAccount = onSwitchAccount,
            onRemoveStoredAccount = onRemoveStoredAccount,
            onPrepareRelogin = onPrepareRelogin,
            focusCoordinator = focusCoordinator,
            focusTab = focusTab,
            videoCardRecycledViewPool = videoCardRecycledViewPool,
            onRequestSidebarFocus = profileFocusCoordinator::requestSidebarFocus,
            onRequestTopBarFocus = onRequestTopBarFocus
        )
    }
}

@Composable
internal fun CenterStatus(text: String) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = if (isLightTheme) Color(0xFF18191C) else Color.White, style = MaterialTheme.typography.titleLarge)
    }
}
@Composable
private fun ProfileContentPanel(
    modifier: Modifier,
    uiState: ProfileUiState,
    selectedMenu: ProfileMenu,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String, VideoItem) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onSelectFavoriteFolder: (String) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onLoadMoreBangumi: () -> Unit,
    onUnfollowBangumi: (Long) -> Unit,
    onRemoveWatchLater: (VideoItem) -> Unit,
    onSwitchAccount: (Long) -> Unit,
    onRemoveStoredAccount: (Long) -> Unit,
    onPrepareRelogin: () -> Unit,
    focusCoordinator: HomeFocusCoordinator?,
    focusTab: AppTopLevelTab?,
    videoCardRecycledViewPool: RecyclerView.RecycledViewPool?,
    onRequestSidebarFocus: () -> Boolean,
    onRequestTopBarFocus: () -> Boolean
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
                    onBackToTopBar = onRequestTopBarFocus,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    videoCardRecycledViewPool = videoCardRecycledViewPool
                )
                ProfileMenu.SETTINGS -> ProfileSettingsPanel(
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
                ProfileMenu.DANMAKU_SETTINGS -> ProfileDanmakuSettingsPanel(
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
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
                    onBackToTopBar = onRequestTopBarFocus,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    videoCardRecycledViewPool = videoCardRecycledViewPool
                )
                ProfileMenu.BANGUMI -> ProfileBangumiPanel(
                    items = uiState.bangumiItems,
                    isLoading = uiState.isBangumiLoading,
                    isLoadingMore = uiState.isBangumiLoadingMore,
                    hasMore = uiState.bangumiHasMore,
                    errorMessage = uiState.bangumiErrorMessage,
                    onOpenVideo = onOpenVideo,
                    onLoadMore = onLoadMoreBangumi,
                    onUnfollowBangumi = onUnfollowBangumi,
                    onRequestSidebarFocus = onRequestSidebarFocus,
                    onBackToTopBar = onRequestTopBarFocus,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    videoCardRecycledViewPool = videoCardRecycledViewPool
                )
                ProfileMenu.WATCH_LATER -> ProfileWatchLaterPanel(
                    items = uiState.watchLaterItems,
                    totalCount = uiState.watchLaterTotalCount,
                    isLoading = uiState.isWatchLaterLoading,
                    errorMessage = uiState.watchLaterErrorMessage,
                    onOpenVideo = onOpenVideo,
                    onRemoveVideo = onRemoveWatchLater,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    videoCardRecycledViewPool = videoCardRecycledViewPool,
                    focusRegion = HomeFocusRegion.ProfileContent,
                    onBackToTopBar = onRequestTopBarFocus,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
                ProfileMenu.SWITCH_ACCOUNT -> SwitchAccountPanel(
                    accounts = uiState.storedAccounts,
                    activeAccountMid = uiState.activeAccountMid,
                    onSwitchAccount = onSwitchAccount,
                    onRemoveStoredAccount = onRemoveStoredAccount,
                    onPrepareRelogin = onPrepareRelogin,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab
                )
                ProfileMenu.CHANGE_ICON -> ChangeIconPanel(
                    onOpenSettings = onOpenSettings,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
                ProfileMenu.PLUGINS -> ProfilePluginCenterPanel(
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
                ProfileMenu.GUIDE -> ProfileGuidePanel(
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    onRequestSidebarFocus = onRequestSidebarFocus
                )
                ProfileMenu.LOGOUT -> LogoutPanel()
            }
        }
    }
}
