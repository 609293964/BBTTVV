package com.bbttvv.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.core.store.StoredAccountSession
import com.bbttvv.app.ui.components.AppTopLevelTab
import com.bbttvv.app.ui.components.rememberSizedImageModel
import com.bbttvv.app.ui.home.HomeFocusCoordinator

@Composable
internal fun ProfileSettingsPanel(
    onOpenSettings: () -> Unit,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    onRequestSidebarFocus: () -> Boolean = { false },
) {
    val contentFocusTarget = rememberProfileContentFocusTargetState(
        focusCoordinator = focusCoordinator,
        focusTab = focusTab,
    )
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "设置", color = if (isLightTheme) Color(0xFF18191C) else Color.White, style = MaterialTheme.typography.headlineMedium)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .profileContentFocusTarget(
                    state = contentFocusTarget,
                    focusCoordinator = focusCoordinator,
                    focusTab = focusTab,
                    onDpadLeft = onRequestSidebarFocus,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileInfoCard(
                title = "统一设置中心",
                value = "播放、弹幕、音频、界面、内容、网络与系统选项集中在完整设置页中。",
            )
            ProfilePrimaryAction(
                text = "打开完整设置",
                onClick = onOpenSettings,
                modifier = Modifier.focusRequester(contentFocusTarget.initialFocusRequester),
            )
        }
    }
}

@Composable
internal fun ProfilePlaceholderPanel(
    title: String,
    subtitle: String,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    onRequestSidebarFocus: () -> Boolean = { false },
) {
    val contentFocusTarget = rememberProfileContentFocusTargetState(
        focusCoordinator = focusCoordinator,
        focusTab = focusTab,
    )
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .profileContentFocusTarget(
                state = contentFocusTarget,
                focusCoordinator = focusCoordinator,
                focusTab = focusTab,
                onDpadLeft = onRequestSidebarFocus,
            )
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = title, color = if (isLightTheme) Color(0xFF18191C) else Color.White, style = MaterialTheme.typography.headlineMedium)
        Box(
            modifier = Modifier
                .focusRequester(contentFocusTarget.initialFocusRequester)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .fillMaxWidth()
                .background(
                    color = if (focused) {
                        if (isLightTheme) Color(0xFFFB7299) else Color(0xE9E6EEF4)
                    } else {
                        if (isLightTheme) Color(0x0C000000) else Color(0x12000000)
                    },
                    shape = cardShape
                )
                .border(
                    width = if (focused) 1.dp else 0.dp,
                    color = if (focused) {
                        if (isLightTheme) Color(0xFFFB7299).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.92f)
                    } else {
                        Color.Transparent
                    },
                    shape = cardShape
                )
                .padding(24.dp)
        ) {
            Text(
                text = subtitle,
                color = if (focused) {
                    Color.White
                } else {
                    if (isLightTheme) Color(0xFF61666D) else Color(0xD9FFFFFF)
                },
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
internal fun SwitchAccountPanel(
    accounts: List<StoredAccountSession>,
    activeAccountMid: Long?,
    onSwitchAccount: (Long) -> Unit,
    onRemoveStoredAccount: (Long) -> Unit,
    onPrepareRelogin: () -> Unit,
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
) {
    val contentFocusTarget = rememberProfileContentFocusTargetState(
        focusCoordinator = focusCoordinator,
        focusTab = focusTab,
    )
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .profileContentFocusTarget(
                state = contentFocusTarget,
                focusCoordinator = focusCoordinator,
                focusTab = focusTab,
            )
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(text = "切换账号", color = if (isLightTheme) Color(0xFF18191C) else Color.White, style = MaterialTheme.typography.headlineMedium)
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
                val isFirstAccount = account.mid == accounts.firstOrNull()?.mid
                AccountRowCard(
                    account = account,
                    active = activeAccountMid == account.mid,
                    onSwitchAccount = onSwitchAccount,
                    onRemoveStoredAccount = onRemoveStoredAccount,
                    initialFocusModifier = if (isFirstAccount) {
                        Modifier.focusRequester(contentFocusTarget.initialFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
        item {
            ProfilePrimaryAction(
                text = "重新扫码登录",
                onClick = onPrepareRelogin,
                modifier = if (accounts.isEmpty()) {
                    Modifier.focusRequester(contentFocusTarget.initialFocusRequester)
                } else {
                    Modifier
                },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountRowCard(
    account: StoredAccountSession,
    active: Boolean,
    onSwitchAccount: (Long) -> Unit,
    onRemoveStoredAccount: (Long) -> Unit,
    initialFocusModifier: Modifier = Modifier,
) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (active) {
                    if (isLightTheme) Color(0x14FB7299) else Color(0x1FFFF0F5)
                } else {
                    if (isLightTheme) Color(0x0C000000) else Color(0x12000000)
                },
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
            Text(text = account.name.ifBlank { "UID ${account.mid}" }, color = if (isLightTheme) Color(0xFF18191C) else Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "UID ${account.mid}", color = if (isLightTheme) Color(0xFF61666D) else Color(0xB3FFFFFF), fontSize = 13.sp)
        }
        if (active) {
            Box(
                modifier = Modifier.widthIn(min = 82.dp).padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "当前账号", color = if (isLightTheme) Color(0xFFFB7299) else Color(0xFFFFE7EF), fontWeight = FontWeight.Medium)
            }
        } else {
            AccountActionButton(
                text = "切换",
                contentColor = if (isLightTheme) Color(0xFFFB7299) else Color.White,
                onClick = { onSwitchAccount(account.mid) },
                modifier = initialFocusModifier,
            )
        }
        AccountActionButton(
            text = "移除",
            contentColor = if (isLightTheme) Color(0xFFE03E5F) else Color(0xFFFFBCC8),
            onClick = { onRemoveStoredAccount(account.mid) },
            modifier = if (active) initialFocusModifier else Modifier,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AccountActionButton(
    text: String,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isLightTheme) Color(0x0A000000) else Color(0x14000000),
            focusedContainerColor = if (isLightTheme) Color(0xFFFB7299) else Color(0xF2EEF6FB)
        )
    ) {
        Box(
            modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (focused) Color.White else contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun ChangeIconPanel(
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    onRequestSidebarFocus: () -> Boolean = { false },
) {
    val contentFocusTarget = rememberProfileContentFocusTargetState(
        focusCoordinator = focusCoordinator,
        focusTab = focusTab,
    )
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .profileContentFocusTarget(
                state = contentFocusTarget,
                focusCoordinator = focusCoordinator,
                focusTab = focusTab,
                onDpadLeft = onRequestSidebarFocus,
            )
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "更换图标", color = if (isLightTheme) Color(0xFF18191C) else Color.White, style = MaterialTheme.typography.headlineMedium)
        ProfileInfoCard(
            title = "图标入口已预留",
            value = "后续可在这里选择 TV 启动图标；当前版本暂不提供可切换图标。",
            modifier = Modifier.focusRequester(contentFocusTarget.initialFocusRequester),
            focusable = true,
        )
    }
}

@Composable
internal fun ProfileGuidePanel(
    focusCoordinator: HomeFocusCoordinator? = null,
    focusTab: AppTopLevelTab? = null,
    onRequestSidebarFocus: () -> Boolean = { false },
) {
    val contentFocusTarget = rememberProfileContentFocusTargetState(
        focusCoordinator = focusCoordinator,
        focusTab = focusTab,
    )
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .profileContentFocusTarget(
                state = contentFocusTarget,
                focusCoordinator = focusCoordinator,
                focusTab = focusTab,
                onDpadLeft = onRequestSidebarFocus,
            )
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(text = "操作说明", color = if (isLightTheme) Color(0xFF18191C) else Color.White, style = MaterialTheme.typography.headlineMedium)
        }

        item { GuideSectionTitle("D-Pad 方向键操作") }
        item {
            ProfileInfoCard(
                "上下左右 — 基础导航",
                "在首页、搜索页、个人页等所有界面中，使用方向键移动焦点。焦点高亮会跟随方向键实时移动，" +
                    "无需按确认键即可看到当前选中项。在视频网格中，上下键在行间切换，左右键在同行卡片间移动。",
                modifier = Modifier.focusRequester(contentFocusTarget.initialFocusRequester),
                focusable = true,
            )
        }
        item {
            ProfileInfoCard(
                "确认键 — 选择与进入",
                "按确认键（D-Pad 中心键 / Enter）可进入当前焦点所在的内容，如打开视频详情、进入播放器、" +
                    "切换设置项等。在顶部 Tab 栏移动焦点时会直接切页，无需额外按确认键确认。"
            )
        }
        item {
            ProfileInfoCard(
                "长按确认键 — 长按操作",
                "在视频卡片上长按确认键可触发上下文菜单（如加入稍后再看、不感兴趣等）。" +
                    "在详情页操作按钮上长按确认键约 1.5 秒可触发一键三连（点赞、投币、收藏）。"
            )
        }
        item {
            ProfileInfoCard(
                "长按方向键 — 快速滚动",
                "在视频网格中长按上下方向键可快速连续滚动浏览更多内容。" +
                    "在播放器中长按左/右方向键可持续快进或快退，松开后定位到目标位置。"
            )
        }
        item {
            ProfileInfoCard(
                "边界行为 — 焦点不会跑丢",
                "在列表或 Tab 的边缘继续按方向键，焦点会停留在当前项，不会跑到不可控的区域。" +
                    "即使焦点意外离开当前界面，也会自动恢复到合理位置。"
            )
        }

        item { GuideSectionTitle("快捷功能") }
        item {
            ProfileInfoCard(
                "播放/暂停",
                "在播放器中按确认键或媒体播放键可切换播放与暂停状态。当控制栏隐藏时，按确认键会先显示控制栏，再按切换播放。"
            )
        }
        item {
            ProfileInfoCard(
                "快进 / 快退",
                "在播放器中按左/右方向键可步进快退 10 秒。长按左/右方向键或快退/快进媒体键可持续快进快退，" +
                    "松开后自动定位。短视频快进速度约 4 秒/秒，长视频约 10 秒遍历全片。"
            )
        }
        item {
            ProfileInfoCard(
                "MENU 键 — 刷新与菜单",
                "在首页视频网格中按 MENU 键可触发刷新当前推荐内容。在直播播放器中按 MENU 键可显示控制面板。"
            )
        }
        item {
            ProfileInfoCard(
                "返回键 — 层级返回",
                "按返回键可逐层返回上一级界面：播放器 → 详情页 → 首页。在弹窗或菜单中按返回键可关闭当前弹窗。" +
                    "在播放器中，返回键会依次关闭面板 → 关闭调试信息 → 隐藏控制栏 → 退出播放器。"
            )
        }
        item {
            ProfileInfoCard(
                "赞助片段跳过",
                "当播放器检测到赞助商片段时，屏幕上会显示跳过提示。按确认键可立即跳过该片段，按返回键可关闭提示继续观看。"
            )
        }
        item {
            ProfileInfoCard(
                "扫码登录",
                "未登录时，\"我的\"页面右侧会显示登录二维码。使用手机 APP 扫码即可登录，登录成功后页面状态自动刷新，无需手动操作。"
            )
        }

        item { GuideSectionTitle("核心功能逻辑") }
        item {
            ProfileInfoCard(
                "首页导航流程",
                "首页由顶部 Tab 栏、二级分类 Tab 和视频网格三层组成。在顶部 Tab 左右移动可直接切换主分类（推荐、热门、直播等），" +
                    "按方向键下进入二级分类或视频网格。从网格按方向键上可逐层返回顶部 Tab。"
            )
        }
        item {
            ProfileInfoCard(
                "播放器控制栏",
                "播放器控制栏有三种显示状态：隐藏（仅显示画面）、可见（显示进度条和操作按钮）、面板打开（显示清晰度/倍速等设置选项）。" +
                    "按方向键上/下在进度条和操作按钮间切换，按确认键打开设置面板。控制栏会在一段时间无操作后自动隐藏。"
            )
        }
        item {
            ProfileInfoCard(
                "返回时自动恢复位置",
                "从详情页或播放器返回首页时，会自动恢复到之前浏览的视频卡片位置，无需重新查找。" +
                    "切换 Tab 后再切回，焦点也会恢复到离开时的位置。弹窗关闭后焦点会回到触发弹窗的按钮上。"
            )
        }
        item {
            ProfileInfoCard(
                "自动加载更多",
                "视频网格向下滚动到接近底部时会自动加载下一页内容。" +
                    "首页刷新数量可在设置中调整（10~40 条），推荐页数据源也可在设置中切换。"
            )
        }
        item {
            ProfileInfoCard(
                "播放结束行为",
                "视频播放结束后的行为可在设置中配置：停留等待手动操作 / 自动播放下一分P / 单集循环 / 自动返回上一页。" +
                    "默认为停留模式，适合连续手动选择。"
            )
        }

        item { GuideSectionTitle("常见问题与建议") }
        item {
            ProfileInfoCard(
                "焦点似乎消失了怎么办",
                "偶尔焦点可能因界面刷新而短暂不可见，稍等片刻会自动恢复。如果长时间无响应，" +
                    "可尝试按返回键回到上一级再重新进入，或按方向键任意方向重新获取焦点。"
            )
        }
        item {
            ProfileInfoCard(
                "视频加载缓慢或失败",
                "可尝试在设置中切换推荐页数据源（网页端/移动端）。如果使用双栈网络遇到异常，" +
                    "可开启\"仅允许使用 IPv4\"选项。清除缓存也可能解决部分加载问题。"
            )
        }
        item {
            ProfileInfoCard(
                "弹幕显示异常",
                "弹幕设置可在\"我的\"→\"弹幕设置\"中调整，包括字体大小、透明度、显示区域等。" +
                    "播放进度变化时弹幕可能短暂延迟出现，属于正常现象。"
            )
        }
        item {
            ProfileInfoCard(
                "音量忽大忽小",
                "可在设置中开启\"音量均衡\"功能，系统会自动调整不同视频的音量差异。" +
                    "如果整体音量偏大或偏小，可使用\"应用音量校准\"单独调节应用输出音量，不影响电视系统音量。"
            )
        }
        item {
            ProfileInfoCard(
                "想从上次位置继续观看",
                "确保设置中\"自动跳到上次播放位置\"已开启。打开视频时会自动从上次中断处继续播放。" +
                    "此功能依赖本地观看记录，开启隐私无痕模式后不会记录播放进度。"
            )
        }
    }
}

@Composable
private fun GuideSectionTitle(title: String) {
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Text(
        text = title,
        color = if (isLightTheme) Color(0xFF61666D) else Color(0x8FFFFFFF),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 6.dp)
    )
}

@Composable
internal fun LogoutPanel() {
    CenterStatus("已退出登录")
}

@Composable
internal fun ProfileInfoCard(
    title: String,
    value: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    focusable: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    val shape = RoundedCornerShape(if (compact) 24.dp else 28.dp)
    val focusModifier = if (focusable) {
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(focusModifier)
            .fillMaxWidth()
            .background(
                color = if (focused) {
                    if (isLightTheme) Color(0xFFFB7299) else Color(0xE9E6EEF4)
                } else {
                    if (isLightTheme) Color(0x0C000000) else Color(0x12000000)
                },
                shape = shape
            )
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) {
                    if (isLightTheme) Color(0xFFFB7299).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.92f)
                } else {
                    Color.Transparent
                },
                shape = shape
            )
            .padding(
                horizontal = if (compact) 18.dp else 22.dp,
                vertical = if (compact) 14.dp else 20.dp
            )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
            Text(
                text = title,
                color = if (focused) {
                    Color.White
                } else {
                    if (isLightTheme) Color(0xFF18191C) else Color.White
                },
                fontSize = if (compact) 15.sp else 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = if (focused) {
                    Color.White.copy(alpha = 0.85f)
                } else {
                    if (isLightTheme) Color(0xFF61666D) else Color(0xD9FFFFFF)
                },
                fontSize = if (compact) 11.sp else 14.sp,
                lineHeight = if (compact) 16.sp else 21.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ProfilePrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val isLightTheme = com.bbttvv.app.ui.theme.LocalIsLightTheme.current
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(24.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isLightTheme) Color(0x0C000000) else Color(0x12000000),
            focusedContainerColor = if (isLightTheme) Color(0xFFFB7299) else Color(0xE9E6EEF4)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (focused) {
                    Color.White
                } else {
                    if (isLightTheme) Color(0xFFFB7299) else Color.White
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
