package com.bbttvv.app.feature.profile

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.bbttvv.app.ui.components.rememberSizedImageModel

private data class ProfileMetric(val label: String, val value: String)

@Composable
internal fun GuestProfileSidebar(storedAccountCount: Int, modifier: Modifier = Modifier) {
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
internal fun LoggedInSidebar(
    uiState: ProfileUiState,
    selectedMenu: ProfileMenu,
    profileMenus: List<ProfileMenu>,
    updateContentOnTabFocusEnabled: Boolean,
    onRequestTopBarFocus: () -> Boolean,
    onSidebarFocusChanged: (Boolean) -> Unit = {},
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
                    onFocusChanged = onSidebarFocusChanged,
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
    onFocusChanged: (Boolean) -> Unit = {},
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
                onFocusChanged(focusState.isFocused)
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
