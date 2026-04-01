// 文件路径: feature/settings/AnimationSettingsScreen.kt
package com.android.purebilibili.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable // [Fix] Missing import
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.R
import com.android.purebilibili.core.theme.*
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.store.LiquidGlassMode
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.resolveEffectiveLiquidGlassEnabled
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.resolveDeviceUiProfile
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.resolveLiquidGlassTuning
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.ui.animation.staggeredEntrance
import kotlinx.coroutines.delay
import android.os.Build

/**
 *  动画与效果设置二级页面
 * 管理卡片动画、过渡效果、磨砂效果等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val screenTitle = stringResource(R.string.animation_effects_title)
    val backLabel = stringResource(R.string.common_back)
    val scope = rememberCoroutineScope()
    val blurLevel = when (state.blurIntensity) {
        BlurIntensity.THIN -> 0.5f
        BlurIntensity.THICK -> 0.8f
        BlurIntensity.APPLE_DOCK -> 1.0f  //  玻璃拟态风格
    }
    val animationInteractionLevel = (
        0.2f +
            if (state.cardAnimationEnabled) 0.25f else 0f +
            if (state.cardTransitionEnabled) 0.25f else 0f +
            if (state.bottomBarBlurEnabled) 0.2f else 0f +
            blurLevel * 0.2f
        ).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(rememberAppBackIcon(), contentDescription = backLabel)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        AnimationSettingsContent(
            modifier = Modifier.padding(padding),
            state = state,
            viewModel = viewModel
        )
    }
}

@Composable
fun AnimationSettingsContent(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val windowSizeClass = LocalWindowSizeClass.current
    val warningTint = rememberAdaptiveSemanticIconTint(iOSOrange)
    val deviceUiProfile = remember(windowSizeClass.widthSizeClass) {
        resolveDeviceUiProfile(
            widthSizeClass = windowSizeClass.widthSizeClass
        )
    }
    val settingsEntranceMotionTier = remember(deviceUiProfile.motionTier) {
        resolveSettingsEntranceMotionTier(deviceUiProfile.motionTier)
    }
    val cardMotionTier = resolveAnimationSettingsCardMotionTier(
        baseTier = deviceUiProfile.motionTier,
        cardAnimationEnabled = state.cardAnimationEnabled
    )
    val motionTierLabel = remember(cardMotionTier) {
        when (cardMotionTier) {
            MotionTier.Reduced -> "低动效"
            MotionTier.Normal -> "标准"
            MotionTier.Enhanced -> "增强"
        }
    }
    val motionTierHint = remember(cardMotionTier) {
        when (cardMotionTier) {
            MotionTier.Reduced -> "更短延迟与更弱位移，优先稳定和性能"
            MotionTier.Normal -> "平衡性能与动效，适合大多数设备"
            MotionTier.Enhanced -> "更明显的层级与动势，适合大屏展示"
        }
    }
    val predictiveBackToggleState = remember(
        state.cardTransitionEnabled,
        state.predictiveBackAnimationEnabled
    ) {
        resolvePredictiveBackToggleUiState(
            cardTransitionEnabled = state.cardTransitionEnabled,
            predictiveBackAnimationEnabled = state.predictiveBackAnimationEnabled
        )
    }
    var previewLiquidGlassProgress by remember { mutableFloatStateOf(state.liquidGlassProgress) }
    LaunchedEffect(state.liquidGlassProgress) {
        previewLiquidGlassProgress = state.liquidGlassProgress
    }
    val liquidGlassPreviewState = remember(previewLiquidGlassProgress) {
        resolveLiquidGlassPreviewUiState(progress = previewLiquidGlassProgress)
    }
    val liquidGlassTuning = remember(previewLiquidGlassProgress) {
        resolveLiquidGlassTuning(progress = previewLiquidGlassProgress)
    }
    val isLiquidGlassAvailable = remember(state.uiPreset) {
        state.uiPreset != UiPreset.MD3
    }
    val effectiveLiquidGlassEnabled = remember(state.isLiquidGlassEnabled, state.uiPreset) {
        resolveEffectiveLiquidGlassEnabled(
            requestedEnabled = state.isLiquidGlassEnabled,
            uiPreset = state.uiPreset
        )
    }
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = WindowInsets.navigationBars.asPaddingValues()
    ) {
            
            //  卡片动画
            //  卡片动画
            item {
                Box(modifier = Modifier.staggeredEntrance(0, isVisible, motionTier = settingsEntranceMotionTier)) {
                    IOSSectionTitle("卡片动画")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(1, isVisible, motionTier = settingsEntranceMotionTier)) {
                    IOSGroup {
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.WandAndStars,
                            title = "进场动画",
                            subtitle = "首页视频卡片的入场动画效果",
                            checked = state.cardAnimationEnabled,
                            onCheckedChange = { viewModel.toggleCardAnimation(it) },
                            iconTint = iOSPink
                        )
                        IOSDivider()
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.ArrowLeftArrowRight,
                            title = "过渡动画",
                            subtitle = "点击卡片时的共享元素过渡效果",
                            checked = state.cardTransitionEnabled,
                            onCheckedChange = { viewModel.toggleCardTransition(it) },
                            iconTint = iOSTeal
                        )
                        IOSDivider()
                        IOSSwitchItem(
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            title = predictiveBackToggleState.title,
                            subtitle = predictiveBackToggleState.subtitle,
                            checked = predictiveBackToggleState.checked,
                            onCheckedChange = {
                                if (predictiveBackToggleState.enabled) {
                                    viewModel.togglePredictiveBackAnimation(it)
                                }
                            },
                            enabled = predictiveBackToggleState.enabled,
                            iconTint = if (predictiveBackToggleState.enabled) iOSBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IOSDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "首页卡片动画档位",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = motionTierLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = motionTierHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "设置页使用独立轻量入场动效，不跟随此开关关闭。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // ✨ 视觉效果
            item {
                Box(modifier = Modifier.staggeredEntrance(2, isVisible, motionTier = settingsEntranceMotionTier)) {
                    IOSSectionTitle("视觉效果")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(3, isVisible, motionTier = settingsEntranceMotionTier)) {
                    IOSGroup {
                        // Android 13+ 显示液态玻璃
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                             IOSSwitchItem(
                                icon = CupertinoIcons.Default.Drop, 
                                title = "液态玻璃", 
                                subtitle = if (isLiquidGlassAvailable) {
                                    "底栏指示器的实时折射效果"
                                } else {
                                    "安卓原生风格下固定关闭，不参与渲染"
                                },
                                checked = effectiveLiquidGlassEnabled,
                                onCheckedChange = {
                                    if (isLiquidGlassAvailable) {
                                        viewModel.toggleLiquidGlass(it)
                                    }
                                },
                                enabled = isLiquidGlassAvailable,
                                iconTint = iOSBlue
                            )
                            // Style Selector (Only visible when enabled)
                            androidx.compose.animation.AnimatedVisibility(
                                visible = isLiquidGlassAvailable && effectiveLiquidGlassEnabled,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "通透到磨砂",
                                        style = MaterialTheme.typography.labelSmall, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    LiquidGlassLivePreview(
                                        previewState = liquidGlassPreviewState,
                                        tuning = liquidGlassTuning
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "玻璃进度",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = liquidGlassPreviewState.modeLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = liquidGlassPreviewState.strengthLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "通透",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "磨砂",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Slider(
                                        value = previewLiquidGlassProgress,
                                        onValueChange = { previewLiquidGlassProgress = it },
                                        valueRange = 0f..1f,
                                        onValueChangeFinished = {
                                            viewModel.setLiquidGlassProgress(previewLiquidGlassProgress)
                                        }
                                    )
                                    Text(
                                        text = liquidGlassPreviewState.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IOSDivider()
                        }

                        // 磨砂效果 (始终显示)
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.SquareStack3dUp,
                            title = "顶部栏磨砂",
                            subtitle = "顶部导航栏的毛玻璃模糊效果",
                            checked = state.headerBlurEnabled,
                            onCheckedChange = { viewModel.toggleHeaderBlur(it) },
                            iconTint = iOSBlue
                        )
                        IOSDivider()
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.Sparkles,
                            title = "底栏磨砂",
                            subtitle = "底部导航栏的毛玻璃模糊效果",
                            checked = state.bottomBarBlurEnabled,
                            onCheckedChange = { viewModel.toggleBottomBarBlur(it) },
                            iconTint = iOSBlue
                        )
                        
                        // 模糊强度（仅在任意模糊开启时显示）
                        if (state.headerBlurEnabled || state.bottomBarBlurEnabled) {
                            IOSDivider()
                            BlurIntensitySelector(
                                selectedIntensity = state.blurIntensity,
                                onIntensityChange = { viewModel.setBlurIntensity(it) }
                            )
                        }
                    }
                }
            }
            
            // 📐 底栏样式
            // 📐 底栏样式
            item {
                Box(modifier = Modifier.staggeredEntrance(4, isVisible, motionTier = settingsEntranceMotionTier)) {
                    IOSSectionTitle("底栏样式")
                }
            }
            item {
                Box(modifier = Modifier.staggeredEntrance(5, isVisible, motionTier = settingsEntranceMotionTier)) {
                    IOSGroup {
                        IOSSwitchItem(
                            icon = CupertinoIcons.Default.RectangleStack,
                            title = "悬浮底栏",
                            subtitle = "关闭后底栏将沉浸式贴底显示",
                            checked = state.isBottomBarFloating,
                            onCheckedChange = { viewModel.toggleBottomBarFloating(it) },
                            iconTint = iOSPurple
                        )
                    }
                }
            }
            
            //  提示
            //  提示
            item {
                Box(modifier = Modifier.staggeredEntrance(6, isVisible, motionTier = settingsEntranceMotionTier)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                CupertinoIcons.Default.Lightbulb,
                                contentDescription = null,
                                tint = warningTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "关闭动画可以减少电量消耗，提升流畅度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }


@Composable
private fun LiquidGlassLivePreview(
    previewState: LiquidGlassPreviewUiState,
    tuning: LiquidGlassTuning,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val surfaceBase = if (isDark) Color(0xFF1A1D24) else Color(0xFFF1F4FB)
    val accent = if (tuning.useNeutralIndicatorTint) {
        if (isDark) Color.White else Color(0xFFEEF2F8)
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.12f else 0.08f),
                            surfaceBase
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "实时预览",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${previewState.modeLabel} · ${previewState.strengthLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 42.dp)
                    .fillMaxWidth(0.82f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(Color.White.copy(alpha = tuning.surfaceAlpha))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = tuning.whiteOverlayAlpha))
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(accent.copy(alpha = tuning.indicatorTintAlpha))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "搜索视频、UP主",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Black.copy(alpha = 0.78f)
                        )
                        Text(
                            text = previewState.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black.copy(alpha = 0.50f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.86f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = tuning.surfaceAlpha + 0.04f))
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = tuning.whiteOverlayAlpha))
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val selected = index == 1
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 18.dp else 16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selected) {
                                            Color.Black.copy(alpha = 0.72f)
                                        } else {
                                            Color.Black.copy(alpha = 0.28f)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.Black.copy(alpha = if (selected) 0.34f else 0.14f))
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (-46).dp)
                        .width(74.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(accent.copy(alpha = tuning.indicatorTintAlpha))
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = tuning.whiteOverlayAlpha))
                    )
                }
            }
        }
    }
}
