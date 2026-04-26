package com.bbttvv.app.feature.profile

import androidx.compose.foundation.background
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
import com.bbttvv.app.feature.settings.TvDanmakuSettingsList
import com.bbttvv.app.feature.settings.TvSettingsList
import com.bbttvv.app.ui.components.rememberSizedImageModel

@Composable
internal fun ProfileSettingsPanel() {
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
internal fun ProfileDanmakuSettingsPanel() {
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
internal fun ProfilePlaceholderPanel(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
        Box(modifier = Modifier.fillMaxWidth().background(Color(0x12000000), RoundedCornerShape(28.dp)).padding(24.dp)) {
            Text(text = subtitle, color = Color(0xD9FFFFFF), lineHeight = 22.sp)
        }
    }
}

@Composable
internal fun SwitchAccountPanel(
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

@Composable
internal fun ChangeIconPanel(onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "更换图标", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        ProfileInfoCard("图标入口已预留", "后续可以在完整设置页中继续接你的 TV 图标方案！")
        ProfilePrimaryAction(text = "打开设置页", onClick = onOpenSettings)
    }
}

@Composable
internal fun ProfileGuidePanel() {
    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "操作说明", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        ProfileInfoCard("方向键换焦点", "顶部把焦点移到推荐或动态时会直接切页，无需回车确认")
        ProfileInfoCard("扫码登录", "未登录时右侧显示二维码，登入成功后自动刷新我的页面状态")
    }
}

@Composable
internal fun LogoutPanel() {
    CenterStatus("已退出登录")
}

@Composable
internal fun ProfileInfoCard(title: String, value: String, compact: Boolean = false) {
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
internal fun ProfilePrimaryAction(text: String, onClick: () -> Unit) {
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
