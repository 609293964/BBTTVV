// 文件路径: feature/dynamic/components/DynamicSidebar.kt
package com.android.purebilibili.feature.dynamic.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.feature.dynamic.SidebarUser

/**
 *  动态侧边栏 - 显示关注的UP主（支持展开/收起、在线状态）
 */
@Composable
fun DynamicSidebar(
    users: List<SidebarUser>,
    selectedUserId: Long?,
    isExpanded: Boolean,
    userListState: androidx.compose.foundation.lazy.LazyListState,
    onUserClick: (Long?) -> Unit,
    showHiddenUsers: Boolean,
    hiddenCount: Int,
    onToggleShowHidden: () -> Unit,
    onTogglePin: (Long) -> Unit,
    onToggleHidden: (Long) -> Unit,
    onToggleExpand: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp, // 新增：内部处理顶部间距
    onBackClick: () -> Unit, // 新增：返回按钮回调
    modifier: Modifier = Modifier
) {
    val expandedWidth = 72.dp
    val collapsedWidth = 64.dp //稍微加宽一点，让头像不拥挤
    val animatedWidth by animateFloatAsState(
        targetValue = if (isExpanded) expandedWidth.value else collapsedWidth.value,
        label = "sidebarWidth"
    )
    
    // 模糊状态
    val sidebarHazeState = rememberRecoverableHazeState()
    
    // 读取模糊强度设置
    val blurIntensity = currentUnifiedBlurIntensity()
    val backgroundAlpha = BlurStyles.getBackgroundAlpha(blurIntensity)
    
    // 侧边栏容器 - Glassmorphism 升级版
    Box(
        modifier = modifier
            .width(animatedWidth.dp)
            .fillMaxHeight()
            .clip(androidx.compose.ui.graphics.RectangleShape) // [修复] 直角
            .background(
                MaterialTheme.colorScheme.surface // 纯白背景，减少割裂感
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                shape = androidx.compose.ui.graphics.RectangleShape // [修复] 直角
            )
    ) {
        // 内容层 - 使用 Box 重新组织布局以支持模糊
        Box(modifier = Modifier.fillMaxSize()) {
            // 可滚动内容 - 作为模糊源
            LazyColumn(
                state = userListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    top = topPadding + 52.dp, // 为顶部返回按钮留出空间
                    bottom = 16.dp
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(sidebarHazeState) // 设置模糊源
            ) {
                // 隐藏用户切换按钮 (胶囊样式)
                if (hiddenCount > 0 || showHiddenUsers) {
                    item(key = "hidden_toggle") {
                        Box(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showHiddenUsers) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                )
                                .clickable { onToggleShowHidden() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showHiddenUsers) CupertinoIcons.Default.Eye else CupertinoIcons.Default.EyeSlash,
                                contentDescription = null,
                                tint = if (showHiddenUsers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 关注的UP主列表 - 带瀑布入场动画
                itemsIndexed(users, key = { _, u -> "sidebar_${u.uid}" }) { index, user ->
                    CascadeSidebarItem(
                        index = index,
                        content = {
                            SidebarUserItem(
                                user = user,
                                isSelected = selectedUserId == user.uid,
                                showLabel = isExpanded,
                                onClick = { onUserClick(user.uid) },
                                onTogglePin = { onTogglePin(user.uid) },
                                onToggleHidden = { onToggleHidden(user.uid) }
                            )
                        }
                    )
                }
            }
            
            // 顶部返回按钮区域 - 应用模糊效果
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topPadding + 52.dp)
                    .unifiedBlur(sidebarHazeState) // 应用模糊
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = backgroundAlpha))
                    .align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .align(Alignment.BottomCenter)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CupertinoIcons.Default.ChevronBackward,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        // 右侧边框线 - 极细
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(0.5.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        )
    }
}

/**
 *  [新增] 瀑布入场动画包装器
 * 每个项目有递增的延迟，形成瀑布展开效果
 */
@Composable
private fun CascadeSidebarItem(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val delay = 30 * index  // 每个项目延迟 30ms
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        visible = true
    }
    
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 20f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.7f,
            stiffness = 400f
        ),
        label = "cascadeOffsetY"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "cascadeAlpha"
    )
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationY = offsetY
                this.alpha = alpha
            }
    ) {
        content()
    }
}

/**
 *  侧边栏项目（文字图标）
 */
@Composable
fun SidebarItem(
    icon: String,
    label: String?,
    isSelected: Boolean,
    isLive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (label != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 *  侧边栏用户项（头像 + 在线状态）
 */
@Composable
fun SidebarUserItem(
    user: SidebarUser,
    isSelected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayName = if (user.isHidden) "${user.name}(隐)" else user.name

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp) // 增加水平间距以适应选中背景
                .clip(RoundedCornerShape(12.dp)) // 选中态圆角背景
                .then(
                    if (isSelected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    else Modifier
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(vertical = 8.dp) // 内部间距
                .alpha(if (user.isHidden) 0.5f else 1f)
        ) {
            Box {
                // 头像
                val faceUrl = remember(user.face) {
                    val raw = user.face.trim()
                    when {
                        raw.isEmpty() -> ""
                        raw.startsWith("https://") -> raw
                        raw.startsWith("http://") -> raw.replace("http://", "https://")
                        raw.startsWith("//") -> "https:$raw"
                        else -> "https://$raw"
                    }
                }

                Box(
                    modifier = Modifier
                        .size(44.dp) // 稍大一点的头像
                        // 选中态边框
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape) // 自适应描边
                        )
                        .padding(2.dp) // 边框与头像间距
                ) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(faceUrl.ifEmpty { null })
                            .crossfade(true)
                            .build(),
                        contentDescription = user.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }

                //  在线状态指示器（带自适应描边）
                if (user.isLive) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .background(MaterialTheme.colorScheme.surface, CircleShape) // 自适应“挖孔”颜色
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFF4081), CircleShape) // 鲜艳的粉红色
                        )
                    }
                }
            }

            if (showLabel) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, // 自适应文字
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer) // 自适应菜单背景
        ) {
            DropdownMenuItem(
                text = { Text(if (user.isPinned) "取消置顶" else "置顶", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    showMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (user.isHidden) "取消隐藏" else "隐藏", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    showMenu = false
                    onToggleHidden()
                }
            )
        }
    }
}
