// 文件路径: feature/home/components/TopBar.kt
package com.android.purebilibili.feature.home.components

import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Tv

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.foundation.ExperimentalFoundationApi // [Added]
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.feature.home.HomeCategory
import com.android.purebilibili.feature.home.resolveHomeTopCategories
import com.android.purebilibili.core.store.LiquidGlassStyle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.animation.rememberDampedDragAnimationState
import com.android.purebilibili.core.ui.animation.horizontalDragGesture
import androidx.compose.foundation.combinedClickable // [Added]
import top.yukonga.miuix.kmp.basic.TabRowDefaults as MiuixTabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour as MiuixTabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun resolveFloatingIndicatorStartPaddingPx(
    baseInsetPx: Float,
    leftBiasPx: Float
): Float = (baseInsetPx - leftBiasPx).coerceAtLeast(0f)

internal fun resolveTopTabRowHorizontalPaddingDp(
    isFloatingStyle: Boolean,
    edgeToEdge: Boolean = false
): Float {
    if (edgeToEdge) return 0f
    return if (isFloatingStyle) 0f else 4f
}

internal fun resolveTopTabVisibleSlots(
    categoryCount: Int,
    longestLabelLength: Int = 0
): Int {
    if (categoryCount <= 4) return 4
    return if (longestLabelLength >= 8) 4 else 5
}

internal fun resolveMd3TopTabVisibleSlots(): Int = 4

internal fun resolveMd3TopTabItemWidthDp(
    containerWidthDp: Float,
    visibleSlots: Int = resolveMd3TopTabVisibleSlots()
): Float {
    if (containerWidthDp <= 0f) return 80f
    return containerWidthDp / visibleSlots.coerceAtLeast(1)
}

internal fun resolveMd3VisibleTabIndices(
    totalCount: Int,
    selectedIndex: Int,
    visibleSlots: Int = resolveMd3TopTabVisibleSlots()
): List<Int> {
    if (totalCount <= 0) return emptyList()
    if (totalCount <= visibleSlots) return List(totalCount) { it }
    val safeSelected = selectedIndex.coerceIn(0, totalCount - 1)
    if (safeSelected < visibleSlots) {
        return List(visibleSlots) { it }
    }
    val pinnedPrefixCount = (visibleSlots - 1).coerceAtLeast(0)
    return buildList {
        repeat(pinnedPrefixCount) { add(it) }
        add(safeSelected)
    }
}

internal fun resolveMd3SelectedVisibleIndex(
    visibleIndices: List<Int>,
    selectedIndex: Int
): Int {
    val resolved = visibleIndices.indexOf(selectedIndex)
    return if (resolved >= 0) resolved else 0
}

internal fun resolveTopTabMinItemWidthDp(isFloatingStyle: Boolean): Float {
    return if (isFloatingStyle) 72f else 64f
}

internal fun shouldRouteTopTabToLivePage(categoryKey: String): Boolean {
    return categoryKey.equals(HomeCategory.LIVE.name, ignoreCase = true)
}

internal fun resolveTopTabItemWidthDp(
    containerWidthDp: Float,
    categoryCount: Int,
    isFloatingStyle: Boolean,
    longestLabelLength: Int = 0
): Float {
    if (containerWidthDp <= 0f) return resolveTopTabMinItemWidthDp(isFloatingStyle)
    val slots = resolveTopTabVisibleSlots(
        categoryCount = categoryCount,
        longestLabelLength = longestLabelLength
    ).coerceAtLeast(1)
    val baseWidth = containerWidthDp / slots
    return baseWidth.coerceAtLeast(resolveTopTabMinItemWidthDp(isFloatingStyle))
}

internal fun normalizeTopTabLabelMode(mode: Int): Int {
    return when (mode) {
        0, 1, 2 -> mode
        else -> 2
    }
}

internal fun shouldShowTopTabIcon(mode: Int): Boolean {
    val normalized = normalizeTopTabLabelMode(mode)
    return normalized == 0 || normalized == 1
}

internal fun shouldShowTopTabText(mode: Int): Boolean {
    val normalized = normalizeTopTabLabelMode(mode)
    return normalized == 0 || normalized == 2
}

internal fun resolveMd3TopTabLabelMode(requestedLabelMode: Int): Int =
    normalizeTopTabLabelMode(requestedLabelMode)

internal fun shouldUseNativeMiuixTopTabRow(
    androidNativeVariant: AndroidNativeVariant,
    labelMode: Int
): Boolean {
    val normalized = normalizeTopTabLabelMode(labelMode)
    // Native Miuix TabRow is text-first. Keep icon-only / icon+text modes on the shared row.
    return androidNativeVariant == AndroidNativeVariant.MIUIX &&
        shouldShowTopTabText(normalized) &&
        !shouldShowTopTabIcon(normalized)
}

private fun resolveTopTabCategoryForIcon(categoryKey: String): HomeCategory? {
    val normalizedKey = categoryKey.trim()
    if (normalizedKey.isEmpty()) return null

    return HomeCategory.entries.firstOrNull { category ->
        category.name.equals(normalizedKey, ignoreCase = true) || category.label == normalizedKey
    }
}

internal fun resolveTopTabCategoryIcon(
    categoryKey: String,
    uiPreset: UiPreset = UiPreset.IOS
): ImageVector {
    val category = resolveTopTabCategoryForIcon(categoryKey)
    return when (uiPreset) {
        UiPreset.MD3 -> when (category) {
            HomeCategory.RECOMMEND -> Icons.Outlined.Home
            HomeCategory.FOLLOW -> Icons.Outlined.Person
            HomeCategory.POPULAR -> Icons.AutoMirrored.Outlined.TrendingUp
            HomeCategory.LIVE -> Icons.Outlined.LiveTv
            HomeCategory.ANIME -> Icons.Outlined.Tv
            HomeCategory.GAME -> Icons.Outlined.SportsEsports
            HomeCategory.KNOWLEDGE -> Icons.Outlined.Lightbulb
            HomeCategory.TECH -> Icons.Outlined.SmartToy
            else -> Icons.AutoMirrored.Outlined.MenuOpen
        }
        UiPreset.IOS -> when (category) {
            HomeCategory.RECOMMEND -> CupertinoIcons.Default.House
            HomeCategory.FOLLOW -> CupertinoIcons.Default.PersonCropCircleBadgePlus
            HomeCategory.POPULAR -> CupertinoIcons.Default.ChartBar
            HomeCategory.LIVE -> CupertinoIcons.Default.Video
            HomeCategory.ANIME -> CupertinoIcons.Default.Tv
            HomeCategory.GAME -> CupertinoIcons.Default.PlayCircle
            HomeCategory.KNOWLEDGE -> CupertinoIcons.Default.Lightbulb
            HomeCategory.TECH -> CupertinoIcons.Default.Cpu
            else -> CupertinoIcons.Default.ListBullet
        }
    }
}

internal fun resolveTopTabPartitionIcon(uiPreset: UiPreset): ImageVector {
    return if (uiPreset == UiPreset.MD3) {
        Icons.AutoMirrored.Outlined.MenuOpen
    } else {
        CupertinoIcons.Default.ListBullet
    }
}

internal enum class Md3TopTabRowVariant {
    UNDERLINE_FIXED
}

internal fun resolveMd3TopTabRowVariant(): Md3TopTabRowVariant =
    Md3TopTabRowVariant.UNDERLINE_FIXED

internal fun resolveMd3TopTabActionButtonCorner(
    isFloatingStyle: Boolean,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
) = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
    if (isFloatingStyle) 18.dp else 14.dp
} else {
    if (isFloatingStyle) 16.dp else 12.dp
}

internal fun resolveMd3TopTabActionButtonSize(
    isFloatingStyle: Boolean,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
) = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
    if (isFloatingStyle) 46.dp else 38.dp
} else {
    if (isFloatingStyle) 44.dp else 36.dp
}

internal fun resolveMd3TopTabActionIconSize(
    isFloatingStyle: Boolean,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
) = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
    if (isFloatingStyle) 21.dp else 18.dp
} else {
    if (isFloatingStyle) 20.dp else 18.dp
}

internal fun resolveMd3TopTabActionContentBottomPadding(): Dp = 4.dp

internal fun performHomeTopBarTap(
    haptic: (HapticType) -> Unit,
    onClick: () -> Unit,
    hapticType: HapticType = HapticType.LIGHT
) {
    haptic(hapticType)
    onClick()
}

/**
 * Q弹点击效果
 */
fun Modifier.premiumClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "scale"
    )
    this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

/**
 *  iOS 风格悬浮顶栏
 * - 不贴边，有水平边距
 * - 圆角 + 毛玻璃效果
 */
@Composable
fun FluidHomeTopBar(
    user: UserState,
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        //  悬浮式导航栏容器 - 增强视觉层次
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,  //  使用主题色，适配深色模式
            shadowElevation = 6.dp,  // 添加阴影增加层次感
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp) // 稍微减小高度
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                //  左侧：头像
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .premiumClickable { onAvatarClick() }
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    if (user.isLogin && user.face.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(FormatUtils.fixImageUrl(user.face))
                                .crossfade(true).build(),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("未", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                //  中间：搜索框
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable { onSearchClick() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            CupertinoIcons.Default.MagnifyingGlass,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "搜索视频、UP主...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                
                //  右侧：设置按钮
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        CupertinoIcons.Default.Gearshape,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 *  [HIG] iOS 风格分类标签栏
 * - 限制可见标签为 4 个主要分类 (HIG 建议 3-5 个)
 * - 其余分类收入"更多"下拉菜单
 * - 圆角胶囊选中指示器
 * - 最小触摸目标 44pt
 */
/**
 *  [HIG] iOS 风格可滑动分类标签栏 (Liquid Glass Style)
 * - 移除"更多"菜单，所有分类水平平铺
 * - 支持水平惯性滚动
 * - 液态玻璃选中指示器 (变长胶囊)
 */
internal fun resolveTopTabUnselectedAlpha(): Float = 0.78f

internal fun resolveTopTabUnselectedColor(isLightMode: Boolean): Color {
    return if (isLightMode) {
        Color.Black.copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTabRow(
    categories: List<String> = resolveHomeTopCategories().map { it.label },
    categoryKeys: List<String> = resolveHomeTopCategories().map { it.name },
    selectedIndex: Int = 0,
    onCategorySelected: (Int) -> Unit = {},
    onPartitionClick: () -> Unit = {},
    onLiveClick: () -> Unit = {},  // [新增] 直播分区点击回调
    pagerState: androidx.compose.foundation.pager.PagerState? = null, // [New] PagerState for sync
    labelMode: Int = 2,
    isLiquidGlassEnabled: Boolean = false,
    liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC,
    liquidGlassTuning: LiquidGlassTuning? = null,
    backdrop: LayerBackdrop? = null,
    isFloatingStyle: Boolean = false,
    edgeToEdge: Boolean = false,
    interactionBudget: HomeInteractionMotionBudget = HomeInteractionMotionBudget.FULL,
    isViewportSyncEnabled: Boolean = true
) {
    val uiPreset = LocalUiPreset.current
    val visualTuning = remember(uiPreset) { resolveTopTabVisualTuning(uiPreset) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val effectiveLiquidGlassEnabled = resolveEffectiveTopTabLiquidGlassEnabled(
        isLiquidGlassEnabled = isLiquidGlassEnabled,
        interactionBudget = interactionBudget
    )
    val resolvedLiquidGlassTuning = remember(liquidGlassStyle, liquidGlassTuning) {
        liquidGlassTuning ?: resolveLiquidGlassTuning(liquidGlassStyle)
    }

    //  [交互优化] 触觉反馈
    val haptic = com.android.purebilibili.core.util.rememberHapticFeedback()
    val scrollChannel = com.android.purebilibili.feature.home.LocalHomeScrollChannel.current
    val coroutineScope = rememberCoroutineScope()
    val tabRowHeight = if (isFloatingStyle) 62.dp else 48.dp
    val actionButtonSize = if (isFloatingStyle) 50.dp else 44.dp
    val actionButtonCorner = if (isFloatingStyle) 22.dp else 22.dp
    val actionIconSize = if (isFloatingStyle) 22.dp else 20.dp
    val topIndicatorHeight = visualTuning.nonFloatingIndicatorHeightDp.dp
    val topIndicatorCorner = visualTuning.nonFloatingIndicatorCornerDp.dp
    val topIndicatorWidthRatio = visualTuning.nonFloatingIndicatorWidthRatio
    val topIndicatorMinWidth = visualTuning.nonFloatingIndicatorMinWidthDp.dp
    val topIndicatorHorizontalInset = visualTuning.nonFloatingIndicatorHorizontalInsetDp.dp
    val floatingLiquidWidthMultiplier = visualTuning.floatingIndicatorWidthMultiplier
    val floatingLiquidMinWidth = visualTuning.floatingIndicatorMinWidthDp.dp
    val floatingLiquidMaxWidth = visualTuning.floatingIndicatorMaxWidthDp.dp
    val floatingLiquidMaxWidthToItemRatio = visualTuning.floatingIndicatorMaxWidthToItemRatio
    val floatingLiquidHeight = visualTuning.floatingIndicatorHeightDp.dp
    val floatingIndicatorEdgeInset = 0.dp
    val floatingIndicatorLeftBias = 0.dp

    if (resolveTopTabIndicatorStyle(uiPreset) == TopTabIndicatorStyle.MATERIAL) {
        Md3CategoryTabRow(
            categories = categories,
            categoryKeys = categoryKeys,
            selectedIndex = selectedIndex,
            onCategorySelected = onCategorySelected,
            onPartitionClick = onPartitionClick,
            onLiveClick = onLiveClick,
            pagerState = pagerState,
            labelMode = labelMode,
            isFloatingStyle = isFloatingStyle
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tabRowHeight)
            .padding(horizontal = resolveTopTabRowHorizontalPaddingDp(
                isFloatingStyle = isFloatingStyle,
                edgeToEdge = edgeToEdge
            ).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [Refactor] 使用 BoxWithConstraints 动态计算宽度
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val tabWidth = resolveTopTabItemWidthDp(
                containerWidthDp = maxWidth.value,
                categoryCount = categories.size,
                isFloatingStyle = isFloatingStyle,
                longestLabelLength = categories.maxOfOrNull { it.length } ?: 0
            ).dp
            val localDensity = LocalDensity.current
            val tabListState = rememberLazyListState()
            
            // [简化] 直接从 PagerState 计算位置，不再使用 DampedDragAnimationState
            // 这是唯一的状态源，消除多状态同步问题
            val currentPosition by remember(pagerState) {
                derivedStateOf {
                    resolveTopTabPagerPosition(
                        selectedIndex = selectedIndex,
                        pagerCurrentPage = pagerState?.currentPage,
                        pagerTargetPage = pagerState?.targetPage,
                        pagerCurrentPageOffsetFraction = pagerState?.currentPageOffsetFraction,
                        pagerIsScrolling = pagerState?.isScrollInProgress == true
                    )
                }
            }
            val viewportAnchorIndex by remember(pagerState, selectedIndex) {
                derivedStateOf {
                    resolveTopTabViewportAnchorIndex(
                        selectedIndex = selectedIndex,
                        pagerCurrentPage = pagerState?.currentPage,
                        pagerTargetPage = pagerState?.targetPage,
                        pagerIsScrolling = pagerState?.isScrollInProgress == true
                    )
                }
            }
            
            // [简化] 是否正在交互（用于指示器缩放效果）
            var isInteracting by remember { mutableStateOf(false) }
            var indicatorVelocityPxPerSecond by remember { mutableFloatStateOf(0f) }
            var lastPosition by remember { mutableFloatStateOf(currentPosition) }
            var lastTimeMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
            var velocityDecayJob by remember { mutableStateOf<Job?>(null) }
            
            // 同步滚动位置：当选中索引变化时，自动滚动到可见区域
            val firstVisibleIndex by remember {
                derivedStateOf {
                    tabListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                }
            }
            val lastVisibleIndex by remember {
                derivedStateOf {
                    tabListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                }
            }

            LaunchedEffect(viewportAnchorIndex, interactionBudget, firstVisibleIndex, lastVisibleIndex) {
                if (!isViewportSyncEnabled) {
                    return@LaunchedEffect
                }
                val targetIndex = viewportAnchorIndex.coerceIn(0, categories.size - 1)
                if (!shouldAnimateTopTabAutoScroll(targetIndex, firstVisibleIndex, lastVisibleIndex, interactionBudget)) {
                    return@LaunchedEffect
                }
                if (interactionBudget == HomeInteractionMotionBudget.REDUCED) {
                    tabListState.scrollToItem(targetIndex)
                } else {
                    tabListState.animateScrollToItem(targetIndex)
                }
            }

            // [修复] 从 layoutInfo 中获取第一个 Tab 的实际物理宽度
            val actualTabWidthPx by remember {
                derivedStateOf {
                    tabListState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.toFloat() 
                        ?: with(localDensity) { tabWidth.toPx() }
                }
            }
            val floatingIndicatorWidthPx by remember(isFloatingStyle) {
                derivedStateOf {
                    if (!isFloatingStyle) 0f
                    else {
                        val minWidthPx = with(localDensity) { floatingLiquidMinWidth.toPx() }
                        val maxWidthPx = with(localDensity) { floatingLiquidMaxWidth.toPx() }
                        (actualTabWidthPx * floatingLiquidWidthMultiplier).coerceIn(minWidthPx, maxWidthPx)
                    }
                }
            }
            val floatingInsetPx by remember(isFloatingStyle) {
                derivedStateOf {
                    if (!isFloatingStyle) 0f
                    else {
                        val edgePx = with(localDensity) { floatingIndicatorEdgeInset.toPx() }
                        ((floatingIndicatorWidthPx - actualTabWidthPx) / 2f).coerceAtLeast(0f) + edgePx
                    }
                }
            }
            val floatingAdjustedInsetDp = with(localDensity) {
                resolveFloatingIndicatorStartPaddingPx(
                    baseInsetPx = floatingInsetPx,
                    leftBiasPx = floatingIndicatorLeftBias.toPx()
                ).toDp()
            }

            LaunchedEffect(effectiveLiquidGlassEnabled) {
                snapshotFlow { Pair(currentPosition, actualTabWidthPx) }
                    .collect { (position, tabWidthPx) ->
                        val now = SystemClock.uptimeMillis()
                        val dt = (now - lastTimeMs).coerceAtLeast(1L)
                        val horizontalDeltaPx = resolveTopTabHorizontalDeltaPx(
                            positionDeltaPages = position - lastPosition,
                            tabWidthPx = tabWidthPx
                        )
                        val rawHorizontalVelocity = (horizontalDeltaPx * 1000f) / dt
                        val rawVelocity = resolveTopTabIndicatorVelocity(
                            horizontalVelocityPxPerSecond = rawHorizontalVelocity
                        )
                        indicatorVelocityPxPerSecond =
                            indicatorVelocityPxPerSecond * 0.32f + rawVelocity * 0.68f
                        isInteracting = shouldTopTabIndicatorBeInteracting(
                            pagerIsScrolling = pagerState?.isScrollInProgress == true,
                            combinedVelocityPxPerSecond = rawVelocity,
                            liquidGlassEnabled = effectiveLiquidGlassEnabled
                        )
                        lastPosition = position
                        lastTimeMs = now

                        velocityDecayJob?.cancel()
                        velocityDecayJob = coroutineScope.launch {
                            delay(90)
                            indicatorVelocityPxPerSecond *= 0.35f
                            isInteracting = shouldTopTabIndicatorBeInteracting(
                                pagerIsScrolling = pagerState?.isScrollInProgress == true,
                                combinedVelocityPxPerSecond = indicatorVelocityPxPerSecond,
                                liquidGlassEnabled = effectiveLiquidGlassEnabled
                            )
                            delay(90)
                            indicatorVelocityPxPerSecond = 0f
                            isInteracting = pagerState?.isScrollInProgress == true
                        }
                    }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val tabContentBackdrop = rememberLayerBackdrop()
                val shouldRefract = shouldTopTabIndicatorUseRefraction(
                    position = currentPosition,
                    interacting = isInteracting,
                    velocityPxPerSecond = indicatorVelocityPxPerSecond
                )
                val indicatorBackdrop = if (shouldRefract) {
                    if (effectiveLiquidGlassEnabled) tabContentBackdrop else backdrop
                } else {
                    null
                }

                // 1. [Layer] Background Liquid Indicator
                // [修复] 使用 layoutInfo 动态计算滚动偏移
                val scrollOffset by remember {
                    derivedStateOf {
                        resolveTopTabIndicatorViewportShiftPx(
                            firstVisibleItemIndex = tabListState.firstVisibleItemIndex,
                            firstVisibleItemScrollOffsetPx = tabListState.firstVisibleItemScrollOffset,
                            tabWidthPx = actualTabWidthPx
                        )
                    }
                }

                Box(modifier = Modifier.graphicsLayer {
                    translationX = -scrollOffset
                }) {
                    if (isFloatingStyle) {
                        LiquidIndicator(
                            position = currentPosition,
                            itemWidth = with(localDensity) { actualTabWidthPx.toDp() },
                            itemCount = categories.size,
                            isDragging = isInteracting,
                            velocity = indicatorVelocityPxPerSecond,
                            startPadding = floatingAdjustedInsetDp,
                            modifier = Modifier.fillMaxSize(),
                            isLiquidGlassEnabled = effectiveLiquidGlassEnabled,
                            clampToBounds = true,
                            edgeInset = floatingIndicatorEdgeInset,
                            viewportShiftPx = scrollOffset,
                            indicatorWidthMultiplier = floatingLiquidWidthMultiplier,
                            indicatorMinWidth = floatingLiquidMinWidth,
                            indicatorMaxWidth = floatingLiquidMaxWidth,
                            maxWidthToItemRatio = floatingLiquidMaxWidthToItemRatio,
                            indicatorHeight = floatingLiquidHeight,
                            lensIntensityBoost = resolvedLiquidGlassTuning.indicatorLensBoost,
                            edgeWarpBoost = resolvedLiquidGlassTuning.indicatorEdgeWarpBoost,
                            chromaticBoost = resolvedLiquidGlassTuning.indicatorChromaticBoost,
                            liquidGlassStyle = liquidGlassStyle,
                            liquidGlassTuning = resolvedLiquidGlassTuning,
                            backdrop = indicatorBackdrop,
                            color = if (resolvedLiquidGlassTuning.useNeutralIndicatorTint) {
                                if (uiPreset == UiPreset.IOS) {
                                    resolveIos26BottomIndicatorGrayColor(isDarkTheme = isSystemInDarkTheme()).copy(
                                        alpha = resolvedLiquidGlassTuning.indicatorTintAlpha
                                    )
                                } else {
                                    Color.White.copy(
                                        alpha = if (isSystemInDarkTheme()) {
                                            resolvedLiquidGlassTuning.indicatorTintAlpha * 0.68f
                                        } else {
                                            resolvedLiquidGlassTuning.indicatorTintAlpha * 0.82f
                                        }
                                    )
                                }
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = resolvedLiquidGlassTuning.indicatorTintAlpha)
                            }
                        )
                    } else {
                        SimpleLiquidIndicator(
                            position = currentPosition,
                            itemWidthPx = actualTabWidthPx,
                            isDragging = isInteracting,
                            velocityPxPerSecond = indicatorVelocityPxPerSecond,
                            isLiquidGlassEnabled = effectiveLiquidGlassEnabled,
                            liquidGlassStyle = liquidGlassStyle,
                            liquidGlassTuning = resolvedLiquidGlassTuning,
                            backdrop = indicatorBackdrop,
                            indicatorColor = if (resolvedLiquidGlassTuning.useNeutralIndicatorTint) {
                                if (uiPreset == UiPreset.IOS) {
                                    resolveIos26BottomIndicatorGrayColor(isDarkTheme = isSystemInDarkTheme()).copy(
                                        alpha = resolvedLiquidGlassTuning.indicatorTintAlpha
                                    )
                                } else {
                                    Color.White.copy(
                                        alpha = if (isSystemInDarkTheme()) {
                                            resolvedLiquidGlassTuning.indicatorTintAlpha * 0.68f
                                        } else {
                                            resolvedLiquidGlassTuning.indicatorTintAlpha * 0.82f
                                        }
                                    )
                                }
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = resolvedLiquidGlassTuning.indicatorTintAlpha)
                            },
                            indicatorHeight = topIndicatorHeight,
                            cornerRadius = topIndicatorCorner,
                            widthRatio = topIndicatorWidthRatio,
                            minWidth = topIndicatorMinWidth,
                            horizontalInset = topIndicatorHorizontalInset,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                }

                // 2. [Layer] Content Tabs
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    LazyRow(
                        state = tabListState,
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        contentPadding = PaddingValues(
                            horizontal = if (isFloatingStyle) floatingAdjustedInsetDp else 0.dp
                        )
                    ) {
                        itemsIndexed(categories) { index, category ->
                            val categoryKey = categoryKeys.getOrNull(index) ?: category
                            Box(
                                modifier = Modifier.width(tabWidth),
                                contentAlignment = Alignment.Center
                            ) {
                                CategoryTabItem(
                                    category = category,
                                    categoryKey = categoryKey,
                                    index = index,
                                    selectedIndex = selectedIndex,
                                    currentPosition = currentPosition,
                                    primaryColor = primaryColor,
                                    unselectedColor = unselectedColor,
                                    labelMode = labelMode,
                                    onClick = {
                                        performHomeTopBarTap(haptic = haptic, onClick = {
                                            // [修复] 直播由分类语义驱动，而不是固定索引，支持自定义排序
                                            if (shouldRouteTopTabToLivePage(categoryKey)) {
                                                onLiveClick()
                                            } else {
                                                onCategorySelected(index)
                                            }
                                        })
                                    },
                                    onDoubleTap = {
                                        if (selectedIndex == index) {
                                            scrollChannel?.trySend(Unit)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        //  分区按钮
        Box(
            modifier = Modifier
                .size(actionButtonSize)
                .clip(RoundedCornerShape(actionButtonCorner))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    performHomeTopBarTap(haptic = haptic, onClick = onPartitionClick)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                resolveTopTabPartitionIcon(uiPreset),
                contentDescription = "浏览全部分区",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(actionIconSize)
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun Md3CategoryTabRow(
    categories: List<String>,
    categoryKeys: List<String>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onPartitionClick: () -> Unit,
    onLiveClick: () -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState?,
    labelMode: Int,
    isFloatingStyle: Boolean
) {
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val haptic = com.android.purebilibili.core.util.rememberHapticFeedback()
    val scrollChannel = com.android.purebilibili.feature.home.LocalHomeScrollChannel.current
    val normalizedLabelMode = resolveMd3TopTabLabelMode(labelMode)

    if (shouldUseNativeMiuixTopTabRow(androidNativeVariant, normalizedLabelMode)) {
        MiuixCategoryTabRow(
            categories = categories,
            categoryKeys = categoryKeys,
            selectedIndex = selectedIndex,
            onCategorySelected = onCategorySelected,
            onPartitionClick = onPartitionClick,
            onLiveClick = onLiveClick,
            pagerState = pagerState,
            haptic = haptic,
            scrollChannel = scrollChannel
        )
        return
    }

    val visualSpec = remember(isFloatingStyle, androidNativeVariant, normalizedLabelMode) {
        resolveMd3TopTabVisualSpec(
            isFloatingStyle = isFloatingStyle,
            androidNativeVariant = androidNativeVariant,
            labelMode = normalizedLabelMode
        )
    }
    val isMiuixChrome = androidNativeVariant == AndroidNativeVariant.MIUIX
    val tabRowHeight = visualSpec.rowHeight
    val actionButtonSize = resolveMd3TopTabActionButtonSize(isFloatingStyle, androidNativeVariant)
    val actionButtonCorner = resolveMd3TopTabActionButtonCorner(isFloatingStyle, androidNativeVariant)
    val actionIconSize = resolveMd3TopTabActionIconSize(isFloatingStyle, androidNativeVariant)
    val actionContentBottomPadding = resolveMd3TopTabActionContentBottomPadding()
    val viewportAnchorIndex by remember(pagerState, selectedIndex) {
        derivedStateOf {
            resolveTopTabViewportAnchorIndex(
                selectedIndex = selectedIndex,
                pagerCurrentPage = pagerState?.currentPage,
                pagerTargetPage = pagerState?.targetPage,
                pagerIsScrolling = pagerState?.isScrollInProgress == true
            )
        }
    }
    val visibleIndices = remember(categories, viewportAnchorIndex) {
        resolveMd3VisibleTabIndices(
            totalCount = categories.size,
            selectedIndex = viewportAnchorIndex
        )
    }
    val selectedVisibleIndex = remember(visibleIndices, selectedIndex) {
        resolveMd3SelectedVisibleIndex(
            visibleIndices = visibleIndices,
            selectedIndex = selectedIndex
        )
    }
    val currentPagerPosition by remember(pagerState, selectedIndex) {
        derivedStateOf {
            resolveTopTabPagerPosition(
                selectedIndex = selectedIndex,
                pagerCurrentPage = pagerState?.currentPage,
                pagerTargetPage = pagerState?.targetPage,
                pagerCurrentPageOffsetFraction = pagerState?.currentPageOffsetFraction,
                pagerIsScrolling = pagerState?.isScrollInProgress == true
            )
        }
    }
    val currentVisiblePosition by remember(visibleIndices, currentPagerPosition) {
        derivedStateOf {
            resolveMd3TopTabViewportPosition(
                visibleIndices = visibleIndices,
                absolutePagerPosition = currentPagerPosition
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(tabRowHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val slotCount = visibleIndices.size.coerceAtLeast(1)
            val slotWidth = maxWidth / slotCount
            val indicatorWidth = if (isMiuixChrome) {
                (slotWidth * 0.56f).coerceIn(50.dp, 74.dp)
            } else {
                (slotWidth * 0.34f).coerceIn(24.dp, 32.dp)
            }
            val animatedIndicatorOffset by animateDpAsState(
                targetValue = slotWidth * currentVisiblePosition + ((slotWidth - indicatorWidth) / 2f),
                label = "md3TopTabIndicatorOffset"
            )

            Box(modifier = Modifier.fillMaxSize()) {
                val selectedContainerColor = resolveMd3TopTabSelectedContainerColor(
                    colorScheme = MaterialTheme.colorScheme,
                    androidNativeVariant = androidNativeVariant
                )
                Surface(
                    modifier = Modifier
                        .offset(x = animatedIndicatorOffset)
                        .width(indicatorWidth)
                        .height(visualSpec.selectedCapsuleHeight)
                        .padding(bottom = if (isMiuixChrome) 0.dp else 2.dp)
                        .align(if (isMiuixChrome) Alignment.CenterStart else Alignment.BottomStart),
                    shape = RoundedCornerShape(visualSpec.selectedCapsuleCornerRadius),
                    color = selectedContainerColor,
                    tonalElevation = visualSpec.selectedCapsuleTonalElevation,
                    shadowElevation = visualSpec.selectedCapsuleShadowElevation
                ) {}

                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleIndices.forEachIndexed { visibleIndex, originalIndex ->
                        val category = categories[originalIndex]
                        val categoryKey = categoryKeys.getOrNull(originalIndex) ?: category
                        val showIcon = shouldShowTopTabIcon(normalizedLabelMode)
                        val showText = shouldShowTopTabText(normalizedLabelMode)
                        val icon = resolveTopTabCategoryIcon(categoryKey, uiPreset)
                        val selectionFraction =
                            (1f - abs(currentVisiblePosition - visibleIndex.toFloat())).coerceIn(0f, 1f)
                        val onTabClick = {
                            performHomeTopBarTap(haptic = haptic, onClick = {
                                if (shouldRouteTopTabToLivePage(categoryKey)) {
                                    onLiveClick()
                                } else {
                                    onCategorySelected(originalIndex)
                                }
                            })
                        }

                        val iconColor = resolveMd3TopTabIconTint(
                            selectionFraction = selectionFraction,
                            colorScheme = MaterialTheme.colorScheme,
                            androidNativeVariant = androidNativeVariant
                        )
                        val labelColor = resolveMd3TopTabLabelTint(
                            selectionFraction = selectionFraction,
                            colorScheme = MaterialTheme.colorScheme,
                            androidNativeVariant = androidNativeVariant
                        )

                        Box(
                            modifier = Modifier
                                .width(slotWidth)
                                .fillMaxHeight()
                                .padding(vertical = 2.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = LocalIndication.current,
                                    onClick = onTabClick,
                                    onDoubleClick = {
                                        if (selectedVisibleIndex == visibleIndex) {
                                            scrollChannel?.trySend(Unit)
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = visualSpec.itemHorizontalPadding,
                                        end = visualSpec.itemHorizontalPadding,
                                        top = if (showIcon && showText) 0.dp else if (isMiuixChrome) 0.dp else 2.dp,
                                        bottom = if (showIcon && showText) 2.dp else if (isMiuixChrome) 0.dp else 8.dp
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (showIcon) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = iconColor,
                                        modifier = Modifier.size(visualSpec.iconSize)
                                    )
                                }
                                if (showIcon && showText) {
                                    Spacer(modifier = Modifier.height(visualSpec.iconLabelSpacing))
                                }
                                if (showText) {
                                    Text(
                                        text = category,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = visualSpec.labelTextSize,
                                        lineHeight = visualSpec.labelLineHeight,
                                        fontWeight = if (selectionFraction > 0.6f) FontWeight.SemiBold else FontWeight.Medium,
                                        color = labelColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .width(actionButtonSize)
                .fillMaxHeight()
                .padding(bottom = if (isMiuixChrome) 0.dp else actionContentBottomPadding)
                .clip(RoundedCornerShape(actionButtonCorner))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current
                ) {
                    performHomeTopBarTap(haptic = haptic, onClick = onPartitionClick)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                resolveTopTabPartitionIcon(uiPreset),
                contentDescription = "浏览全部分区",
                tint = if (isMiuixChrome) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(actionIconSize)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun MiuixCategoryTabRow(
    categories: List<String>,
    categoryKeys: List<String>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onPartitionClick: () -> Unit,
    onLiveClick: () -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState?,
    haptic: (HapticType) -> Unit,
    scrollChannel: kotlinx.coroutines.channels.Channel<Unit>?
) {
    val selectedTabIndex by remember(pagerState, selectedIndex, categories.size) {
        derivedStateOf {
            resolveTopTabViewportAnchorIndex(
                selectedIndex = selectedIndex,
                pagerCurrentPage = pagerState?.currentPage,
                pagerTargetPage = pagerState?.targetPage,
                pagerIsScrolling = pagerState?.isScrollInProgress == true
            ).coerceIn(0, (categories.size - 1).coerceAtLeast(0))
        }
    }
    val topTabSpec = resolveMd3TopTabVisualSpec(
        isFloatingStyle = false,
        androidNativeVariant = AndroidNativeVariant.MIUIX
    )
    val actionButtonSize = resolveMd3TopTabActionButtonSize(
        isFloatingStyle = false,
        androidNativeVariant = AndroidNativeVariant.MIUIX
    )
    val actionButtonCorner = resolveMd3TopTabActionButtonCorner(
        isFloatingStyle = false,
        androidNativeVariant = AndroidNativeVariant.MIUIX
    )
    val actionIconSize = resolveMd3TopTabActionIconSize(
        isFloatingStyle = false,
        androidNativeVariant = AndroidNativeVariant.MIUIX
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(topTabSpec.rowHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            MiuixTabRowWithContour(
                tabs = categories,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { index ->
                    val categoryKey = categoryKeys.getOrNull(index) ?: categories[index]
                    performHomeTopBarTap(haptic = haptic, onClick = {
                        when {
                            index == selectedIndex -> scrollChannel?.trySend(Unit)
                            shouldRouteTopTabToLivePage(categoryKey) -> onLiveClick()
                            else -> onCategorySelected(index)
                        }
                    })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topTabSpec.rowHeight),
                colors = MiuixTabRowDefaults.tabRowColors(
                    backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f),
                    contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    selectedBackgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.18f),
                    selectedContentColor = MiuixTheme.colorScheme.onSecondaryContainer
                ),
                height = topTabSpec.rowHeight,
                cornerRadius = topTabSpec.selectedCapsuleCornerRadius + 4.dp,
                itemSpacing = 6.dp
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        Surface(
            modifier = Modifier
                .size(actionButtonSize)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    performHomeTopBarTap(haptic = haptic, onClick = onPartitionClick)
            },
            shape = RoundedCornerShape(actionButtonCorner),
            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.10f),
            border = BorderStroke(
                width = 0.8.dp,
                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    resolveTopTabPartitionIcon(UiPreset.MD3),
                    contentDescription = "浏览全部分区",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.size(actionIconSize)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
    }
}

internal fun resolveTopTabIndicatorVelocity(
    horizontalVelocityPxPerSecond: Float
): Float {
    // 顶部指示器仅响应横向分页滑动，避免页面纵向滚动触发胶囊形变。
    return horizontalVelocityPxPerSecond.coerceIn(-4200f, 4200f)
}

internal fun shouldTopTabIndicatorBeInteracting(
    pagerIsScrolling: Boolean,
    combinedVelocityPxPerSecond: Float,
    liquidGlassEnabled: Boolean
): Boolean {
    if (pagerIsScrolling) return true
    val combinedThreshold = if (liquidGlassEnabled) 20f else 60f
    return abs(combinedVelocityPxPerSecond) > combinedThreshold
}

internal fun shouldTopTabIndicatorUseRefraction(
    position: Float,
    interacting: Boolean,
    velocityPxPerSecond: Float,
    positionEpsilon: Float = 0.001f,
    velocityEpsilon: Float = 45f
): Boolean {
    if (interacting) return true
    val fractional = abs(position - position.roundToInt().toFloat()) > positionEpsilon
    if (fractional) return true
    return abs(velocityPxPerSecond) > velocityEpsilon
}

internal fun resolveTopTabHorizontalDeltaPx(
    positionDeltaPages: Float,
    tabWidthPx: Float,
    deadZonePages: Float = 0.0012f
): Float {
    if (tabWidthPx <= 0f) return 0f
    if (abs(positionDeltaPages) < deadZonePages) return 0f
    return positionDeltaPages * tabWidthPx
}

internal fun resolveTopTabIndicatorViewportShiftPx(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffsetPx: Int,
    tabWidthPx: Float
): Float {
    if (tabWidthPx <= 0f) return 0f
    if (firstVisibleItemIndex < 0) return 0f
    val clampedScrollOffsetPx = firstVisibleItemScrollOffsetPx.coerceAtLeast(0)
    return firstVisibleItemIndex * tabWidthPx + clampedScrollOffsetPx.toFloat()
}


@Composable
fun CategoryTabItem(
    category: String,
    categoryKey: String = category,
    index: Int,
    selectedIndex: Int,
    currentPosition: Float,
    primaryColor: Color,
    unselectedColor: Color,
    labelMode: Int,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit = {}
) {
     val uiPreset = LocalUiPreset.current
     // [Optimized] Calculate fraction from the position
     val selectionFraction = remember(currentPosition, index) {
         val distance = kotlin.math.abs(currentPosition - index)
         (1f - distance).coerceIn(0f, 1f)
     }

     // 单层文本渲染，避免双层交叉透明带来的发虚/重影。
     val contentColor = androidx.compose.ui.graphics.lerp(
         unselectedColor,
         primaryColor,
         selectionFraction
     )
     val normalizedLabelMode = normalizeTopTabLabelMode(labelMode)
     val showIcon = shouldShowTopTabIcon(normalizedLabelMode)
     val showText = shouldShowTopTabText(normalizedLabelMode)
     val icon = resolveTopTabCategoryIcon(categoryKey, uiPreset)
     val iconSize = if (showText) 16.dp else 18.dp
     val textSize = resolveTopTabLabelTextSizeSp(normalizedLabelMode).sp
     val textLineHeight = resolveTopTabLabelLineHeightSp(normalizedLabelMode).sp
     val contentMinHeight = resolveTopTabContentMinHeightDp().dp
     
     val targetScale = resolveTopTabContentScale(
         selectionFraction = selectionFraction,
         showIcon = showIcon,
         showText = showText,
         uiPreset = uiPreset
     )
     
     // Font weight change still triggers relayout, but it's discrete (only happens at 0.6 threshold)
     // This is acceptable as it doesn't happen every frame.
     val fontWeight = if (selectionFraction > 0.6f) FontWeight.SemiBold else FontWeight.Medium

     val haptic = com.android.purebilibili.core.util.rememberHapticFeedback()

     Box(
         modifier = Modifier
             .clip(RoundedCornerShape(16.dp)) 
             .combinedClickable(
                 interactionSource = remember { MutableInteractionSource() },
                 indication = null,
                 onClick = { onClick() },
                 onDoubleClick = onDoubleTap
             )
             .padding(horizontal = 8.dp, vertical = 4.dp)
             .heightIn(min = contentMinHeight),
         contentAlignment = Alignment.Center
     ) {
         if (showIcon && showText) {
             Column(
                 horizontalAlignment = Alignment.CenterHorizontally,
                 verticalArrangement = Arrangement.Center,
                 modifier = Modifier.graphicsLayer {
                     scaleX = targetScale
                     scaleY = targetScale
                     transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                 }
             ) {
                Icon(
                     imageVector = icon,
                     contentDescription = null,
                     tint = contentColor,
                     modifier = Modifier
                         .size(iconSize)
                         .offset(y = (-0.5).dp)
                 )
                 Spacer(modifier = Modifier.height(2.dp))
                 Text(
                     text = category,
                     color = contentColor,
                     fontSize = textSize,
                     fontWeight = fontWeight,
                     lineHeight = textLineHeight,
                     maxLines = 1,
                     overflow = TextOverflow.Ellipsis
                 )
             }
         } else if (showIcon) {
             Icon(
                 imageVector = icon,
                 contentDescription = null,
                 tint = contentColor,
                 modifier = Modifier
                     .size(iconSize)
                     .graphicsLayer {
                         scaleX = targetScale
                         scaleY = targetScale
                         transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                     }
             )
         } else {
             Text(
                 text = category,
                 color = contentColor,
                 fontSize = textSize,
                 fontWeight = fontWeight,
                 lineHeight = textLineHeight,
                 modifier = Modifier.graphicsLayer {
                     scaleX = targetScale
                     scaleY = targetScale
                     transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                 },
                 maxLines = 1,
                 overflow = TextOverflow.Ellipsis
             )
         }
     }
}
