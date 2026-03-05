// 文件路径: feature/settings/SettingsViewModel.kt
package com.android.purebilibili.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.allManagedAppIconLauncherAliases
import com.android.purebilibili.core.store.normalizeAppIconKey
import com.android.purebilibili.core.store.resolveAppIconLauncherAlias
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.util.CacheUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val hwDecode: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.FOLLOW_SYSTEM,
    val dynamicColor: Boolean = true,
    val bgPlay: Boolean = false,
    val gestureSensitivity: Float = 1.0f,
    val themeColorIndex: Int = 0,
    val appIcon: String = "icon_3d",
    val isBottomBarFloating: Boolean = true,
    val bottomBarLabelMode: Int = 1,  // 0=图标+文字, 1=仅图标, 2=仅文字
    val headerBlurEnabled: Boolean = true,
    val bottomBarBlurEnabled: Boolean = true,
    val blurIntensity: BlurIntensity = BlurIntensity.THIN,  //  模糊强度
    val displayMode: Int = 0,
    val cardAnimationEnabled: Boolean = false,     //  卡片进场动画（默认关闭）
    val cardTransitionEnabled: Boolean = false,    //  卡片过渡动画（默认关闭）
    val predictiveBackAnimationEnabled: Boolean = true, // [New] 预测性返回联动动画
    val smartVisualGuardEnabled: Boolean = false, // [Retired] 智能流畅优先已下线
    val cacheSize: String = "计算中...",
    val cacheBreakdown: CacheUtils.CacheBreakdown? = null,  //  详细缓存统计
    //  实验性功能
    val auto1080p: Boolean = true,
    val autoSkipOpEd: Boolean = false,
    val prefetchVideo: Boolean = false,
    val doubleTapLike: Boolean = true,

    //  空降助手
    val sponsorBlockEnabled: Boolean = false,
    val sponsorBlockAutoSkip: Boolean = true,
    // [新增] 触感反馈
    val hapticFeedbackEnabled: Boolean = true,
    // [New]
    // [New]
    val isLiquidGlassEnabled: Boolean = true,
    val liquidGlassStyle: com.android.purebilibili.core.store.LiquidGlassStyle = com.android.purebilibili.core.store.LiquidGlassStyle.CLASSIC, // [New]
    // [New] 平板导航模式
    val tabletUseSidebar: Boolean = false,
    val isHeaderCollapseEnabled: Boolean = true, // [New]
    val gridColumnCount: Int = 0 // [New]
)

// 内部数据类，用于分批合并流
private data class CoreSettings(
    val hwDecode: Boolean,
    val themeMode: AppThemeMode,
    val dynamicColor: Boolean,
    val bgPlay: Boolean
)

data class ExtraSettings(
    val gestureSensitivity: Float,
    val themeColorIndex: Int,
    val appIcon: String,
    val isBottomBarFloating: Boolean,
    val bottomBarLabelMode: Int,
    val headerBlurEnabled: Boolean,
    val bottomBarBlurEnabled: Boolean,
    val blurIntensity: BlurIntensity,  //  添加模糊强度
    val displayMode: Int,
    val cardAnimationEnabled: Boolean,
    val cardTransitionEnabled: Boolean,
    val predictiveBackAnimationEnabled: Boolean,
    val smartVisualGuardEnabled: Boolean,
    val hapticFeedbackEnabled: Boolean, // [Restored]
    val isLiquidGlassEnabled: Boolean = true, // [New]
    val liquidGlassStyle: com.android.purebilibili.core.store.LiquidGlassStyle, // [New]
    val tabletUseSidebar: Boolean, // [New]
    val isHeaderCollapseEnabled: Boolean, // [New]
    val gridColumnCount: Int // [New]
)


//  实验性功能设置
data class ExperimentalSettings(
    val auto1080p: Boolean,
    val autoSkipOpEd: Boolean,
    val prefetchVideo: Boolean,
    val doubleTapLike: Boolean,
    //  空降助手
    val sponsorBlockEnabled: Boolean,
    val sponsorBlockAutoSkip: Boolean
)

private data class BaseSettings(
    val hwDecode: Boolean,
    val themeMode: AppThemeMode,
    val dynamicColor: Boolean,
    val bgPlay: Boolean,
    val gestureSensitivity: Float,
    val themeColorIndex: Int,
    val appIcon: String,
    val isBottomBarFloating: Boolean,
    val bottomBarLabelMode: Int,
    val headerBlurEnabled: Boolean,
    val bottomBarBlurEnabled: Boolean,
    val blurIntensity: BlurIntensity,  //  模糊强度
    val displayMode: Int, //  新增
    val cardAnimationEnabled: Boolean, //  卡片进场动画
    val cardTransitionEnabled: Boolean, //  卡片过渡动画
    val predictiveBackAnimationEnabled: Boolean, // [New]
    val smartVisualGuardEnabled: Boolean, // [New]
    val hapticFeedbackEnabled: Boolean, // [新增]
    val isLiquidGlassEnabled: Boolean, // [New]
    val liquidGlassStyle: com.android.purebilibili.core.store.LiquidGlassStyle, // [New]
    val tabletUseSidebar: Boolean, // [New]
    val isHeaderCollapseEnabled: Boolean, // [New]
    val gridColumnCount: Int // [New]
)

private fun <T> Flow<T>.asAnyFlow(): Flow<Any?> = map { it }


class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    // 本地状态流：缓存大小
    private val _cacheSize = MutableStateFlow("计算中...")
    private val _cacheBreakdown = MutableStateFlow<CacheUtils.CacheBreakdown?>(null)

    //  [核心修复] 分步合并，解决 combine 参数限制报错
    // 第 1 步：合并前 4 个设置
    private val coreSettingsFlow = combine(
        SettingsManager.getHwDecode(context),
        SettingsManager.getThemeMode(context),
        SettingsManager.getDynamicColor(context),
        SettingsManager.getBgPlay(context)
    ) { hwDecode, themeMode, dynamicColor, bgPlay ->
        CoreSettings(hwDecode, themeMode, dynamicColor, bgPlay)
    }
    
    // 第 2 步：合并界面设置 (分两组，每组最多5个)
    private val uiSettingsFlow1 = combine(
        SettingsManager.getGestureSensitivity(context),
        SettingsManager.getThemeColorIndex(context),
        SettingsManager.getAppIcon(context)
    ) { gestureSensitivity, themeColorIndex, appIcon ->
        Triple(gestureSensitivity, themeColorIndex, appIcon)
    }
    
    private val uiSettingsFlow2 = combine(
        SettingsManager.getBottomBarFloating(context).asAnyFlow(),
        SettingsManager.getBottomBarLabelMode(context).asAnyFlow(),
        SettingsManager.getDisplayMode(context).asAnyFlow(),
        SettingsManager.getCardAnimationEnabled(context).asAnyFlow(), // [Restored]
        SettingsManager.getCardTransitionEnabled(context).asAnyFlow(),
        SettingsManager.getPredictiveBackAnimationEnabled(context).asAnyFlow(), // [New]
        SettingsManager.getSmartVisualGuardEnabled(context).asAnyFlow(), // [New]
        SettingsManager.getHapticFeedbackEnabled(context).asAnyFlow(), // [新增]
        SettingsManager.getLiquidGlassEnabled(context).asAnyFlow(), // [New]
        SettingsManager.getLiquidGlassStyle(context).asAnyFlow(), // [New]
        SettingsManager.getTabletUseSidebar(context).asAnyFlow(), // [New]
        SettingsManager.getHeaderCollapseEnabled(context).asAnyFlow(), // [New]
        SettingsManager.getGridColumnCount(context).asAnyFlow() // [New]
    ) { values ->
        val isBottomBarFloating = values[0] as Boolean
        val labelMode = values[1] as Int
        val displayMode = values[2] as Int
        val cardAnimation = values[3] as Boolean
        val cardTransition = values[4] as Boolean
        val predictiveBackAnimation = values[5] as Boolean
        val smartVisualGuard = values[6] as Boolean
        val hapticFeedback = values[7] as Boolean
        val liquidGlass = values[8] as Boolean
        val liquidGlassStyle = values[9] as com.android.purebilibili.core.store.LiquidGlassStyle
        val tabletUseSidebar = values[10] as Boolean
        val headerCollapse = values[11] as Boolean
        val gridColumnCount = values[12] as Int
        
        data class Ui2(
            val f: Boolean,
            val l: Int,
            val d: Int,
            val ca: Boolean,
            val ct: Boolean,
            val pba: Boolean,
            val svg: Boolean,
            val h: Boolean,
            val lg: Boolean,
            val lgs: com.android.purebilibili.core.store.LiquidGlassStyle,
            val tus: Boolean,
            val hc: Boolean,
            val gcc: Int
        )
        Ui2(
            isBottomBarFloating,
            labelMode,
            displayMode,
            cardAnimation,
            cardTransition,
            predictiveBackAnimation,
            smartVisualGuard,
            hapticFeedback,
            liquidGlass,
            liquidGlassStyle,
            tabletUseSidebar,
            headerCollapse,
            gridColumnCount
        )
    }

    // 合并所有 UI 设置
    private val uiSettingsFlow = combine(uiSettingsFlow1, uiSettingsFlow2) { ui1, ui2 ->
        // ui1: Triple(gesture, color, icon)
        // ui2: Ui2 class
        ExtraSettings(
            gestureSensitivity = ui1.first,
            themeColorIndex = ui1.second,
            appIcon = ui1.third,
            isBottomBarFloating = ui2.f,
            bottomBarLabelMode = ui2.l,
            displayMode = ui2.d,
            cardAnimationEnabled = ui2.ca,
            cardTransitionEnabled = ui2.ct,
            predictiveBackAnimationEnabled = ui2.pba,
            smartVisualGuardEnabled = ui2.svg,
            hapticFeedbackEnabled = ui2.h, // [新增]
            isLiquidGlassEnabled = ui2.lg, // [New]
            liquidGlassStyle = ui2.lgs, // [New]
            tabletUseSidebar = ui2.tus, // [New]
            isHeaderCollapseEnabled = ui2.hc, // [New]
            gridColumnCount = ui2.gcc, // [New]
            headerBlurEnabled = false, // 暂存，将在下一步合并
            bottomBarBlurEnabled = false, // 暂存
            blurIntensity = BlurIntensity.THIN // 暂存
        )
    }
    
    // 第 3 步：合并模糊设置 (3个)
    private val blurSettingsFlow = combine(
        SettingsManager.getHeaderBlurEnabled(context),
        SettingsManager.getBottomBarBlurEnabled(context),
        SettingsManager.getBlurIntensity(context)  //  添加模糊强度
    ) { headerBlur, bottomBarBlur, blurIntensity ->
        Triple(headerBlur, bottomBarBlur, blurIntensity)
    }
    
    // 第 4 步：合并 UI 和 模糊设置
    private val extraSettingsFlow = combine(uiSettingsFlow, blurSettingsFlow) { uiSettings, blur ->
        uiSettings.copy(
            headerBlurEnabled = blur.first,
            bottomBarBlurEnabled = blur.second,
            blurIntensity = blur.third
        )
    }
    
    //  第 4.5 步：合并实验性功能设置
    private val experimentalSettingsFlow = combine(
        SettingsManager.getAuto1080p(context),
        SettingsManager.getAutoSkipOpEd(context),
        SettingsManager.getPrefetchVideo(context),
        SettingsManager.getDoubleTapLike(context),
        SettingsManager.getSponsorBlockEnabled(context),
        SettingsManager.getSponsorBlockAutoSkip(context)
    ) { values ->
        ExperimentalSettings(
            auto1080p = values[0] as Boolean,
            autoSkipOpEd = values[1] as Boolean,
            prefetchVideo = values[2] as Boolean,
            doubleTapLike = values[3] as Boolean,
            sponsorBlockEnabled = values[4] as Boolean,
            sponsorBlockAutoSkip = values[5] as Boolean
        )
    }
    
    // 第 5 步：合并两组设置
    private val baseSettingsFlow = combine(coreSettingsFlow, extraSettingsFlow) { core, extra ->
        BaseSettings(
            hwDecode = core.hwDecode,
            themeMode = core.themeMode,
            dynamicColor = core.dynamicColor,
            bgPlay = core.bgPlay,
            gestureSensitivity = extra.gestureSensitivity,
            themeColorIndex = extra.themeColorIndex,
            appIcon = extra.appIcon,
            isBottomBarFloating = extra.isBottomBarFloating,
            bottomBarLabelMode = extra.bottomBarLabelMode,
            headerBlurEnabled = extra.headerBlurEnabled,
            bottomBarBlurEnabled = extra.bottomBarBlurEnabled,
            blurIntensity = extra.blurIntensity,  //  模糊强度
            displayMode = extra.displayMode,
            cardAnimationEnabled = extra.cardAnimationEnabled,
            cardTransitionEnabled = extra.cardTransitionEnabled,
            predictiveBackAnimationEnabled = extra.predictiveBackAnimationEnabled,
            smartVisualGuardEnabled = extra.smartVisualGuardEnabled,
            hapticFeedbackEnabled = extra.hapticFeedbackEnabled, // [新增]
            isLiquidGlassEnabled = extra.isLiquidGlassEnabled, // [New]
            liquidGlassStyle = extra.liquidGlassStyle, // [New]
            tabletUseSidebar = extra.tabletUseSidebar, // [New]
            isHeaderCollapseEnabled = extra.isHeaderCollapseEnabled, // [New]
            gridColumnCount = extra.gridColumnCount // [New]
        )

    }

    // 第 6 步：与缓存大小和实验性功能合并
    private val cacheFlow = combine(_cacheSize, _cacheBreakdown) { size, breakdown ->
        Pair(size, breakdown)
    }
    
    val state: StateFlow<SettingsUiState> = combine(
        baseSettingsFlow,
        cacheFlow,
        experimentalSettingsFlow
    ) { settings, cache, experimental ->
        SettingsUiState(
            hwDecode = settings.hwDecode,
            themeMode = settings.themeMode,
            dynamicColor = settings.dynamicColor,
            bgPlay = settings.bgPlay,
            gestureSensitivity = settings.gestureSensitivity,
            themeColorIndex = settings.themeColorIndex,
            appIcon = settings.appIcon,
            isBottomBarFloating = settings.isBottomBarFloating,
            bottomBarLabelMode = settings.bottomBarLabelMode,
            headerBlurEnabled = settings.headerBlurEnabled,
            bottomBarBlurEnabled = settings.bottomBarBlurEnabled,
            blurIntensity = settings.blurIntensity,  //  模糊强度
            displayMode = settings.displayMode,
            cardAnimationEnabled = settings.cardAnimationEnabled,
            cardTransitionEnabled = settings.cardTransitionEnabled,
            predictiveBackAnimationEnabled = settings.predictiveBackAnimationEnabled,
            smartVisualGuardEnabled = settings.smartVisualGuardEnabled,
            hapticFeedbackEnabled = settings.hapticFeedbackEnabled, // [新增]
            isLiquidGlassEnabled = settings.isLiquidGlassEnabled, // [New]
            liquidGlassStyle = settings.liquidGlassStyle, // [New]
            tabletUseSidebar = settings.tabletUseSidebar, // [New]
            isHeaderCollapseEnabled = settings.isHeaderCollapseEnabled, // [New]
            gridColumnCount = settings.gridColumnCount, // [New]

            cacheSize = cache.first,
            cacheBreakdown = cache.second,  //  详细缓存统计
            //  实验性功能
            auto1080p = experimental.auto1080p,
            autoSkipOpEd = experimental.autoSkipOpEd,
            prefetchVideo = experimental.prefetchVideo,
            doubleTapLike = experimental.doubleTapLike,
            //  空降助手
            sponsorBlockEnabled = experimental.sponsorBlockEnabled,
            sponsorBlockAutoSkip = experimental.sponsorBlockAutoSkip
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        refreshCacheSize()
    }

    // --- 功能方法 ---

    //  优化：同时获取缓存大小和详细统计
    fun refreshCacheSize() {
        viewModelScope.launch { 
            val breakdown = CacheUtils.getCacheBreakdown(context)
            _cacheSize.value = breakdown.format()
            _cacheBreakdown.value = breakdown
        }
    }

    suspend fun clearCache(): Result<CacheUtils.CacheBreakdown> {
        return CacheUtils.clearAllCache(context).mapCatching {
            // 清理后立即刷新
            val breakdown = CacheUtils.getCacheBreakdown(context)
            _cacheSize.value = breakdown.format()
            _cacheBreakdown.value = breakdown
            breakdown
        }
    }

    fun toggleHwDecode(value: Boolean) { viewModelScope.launch { SettingsManager.setHwDecode(context, value) } }
    fun setThemeMode(mode: AppThemeMode) { 
        viewModelScope.launch { 
            SettingsManager.setThemeMode(context, mode)
        } 
    }
    fun toggleDynamicColor(value: Boolean) { viewModelScope.launch { SettingsManager.setDynamicColor(context, value) } }
    fun toggleBgPlay(value: Boolean) { viewModelScope.launch { SettingsManager.setBgPlay(context, value) } }
    //  [新增] 手势灵敏度和主题色
    fun setGestureSensitivity(value: Float) { viewModelScope.launch { SettingsManager.setGestureSensitivity(context, value) } }
    fun setThemeColorIndex(index: Int) { 
        viewModelScope.launch { 
            SettingsManager.setThemeColorIndex(context, index)
            //  选择自定义主题色时，自动关闭动态取色
            if (index != 0) {
                SettingsManager.setDynamicColor(context, false)
            }
        }
    }

    //  [新增] 切换应用图标
    fun setAppIcon(iconKey: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val normalizedIconKey = normalizeAppIconKey(iconKey)
            // 1. 保存偏好
            SettingsManager.setAppIcon(context, normalizedIconKey)
            
            // 2. 应用 Alias
            val pm = context.packageManager
            val packageName = context.packageName

            val targetAlias = resolveAppIconLauncherAlias(packageName, normalizedIconKey)
            val allUniqueAliases = allManagedAppIconLauncherAliases(packageName)
            val compatAlias = "${packageName}.MainActivityAlias3D"
            
            android.util.Log.d("SettingsViewModel", "Switching icon to: $iconKey -> $normalizedIconKey -> $targetAlias")
            
            try {
                // 第一步：先启用目标 alias（确保始终有一个活动入口点）
                // ⚠️ [修复] 在尝试杀死进程的操作前，再次延迟，确保 DataStore/SharedPrefs 完全写入磁盘
                kotlinx.coroutines.delay(100)
                
                pm.setComponentEnabledSetting(
                    android.content.ComponentName(packageName, targetAlias),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                // 保留兼容入口（无 Launcher 图标），避免历史 IDE 运行配置启动失败
                pm.setComponentEnabledSetting(
                    android.content.ComponentName(packageName, compatAlias),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
                android.util.Log.d("SettingsViewModel", "Enabled alias: $targetAlias")

                // 第二步：立即禁用其他 alias，避免部分桌面出现“双图标”残留
                allUniqueAliases
                    .filter { it != targetAlias }
                    .forEach { aliasFullName ->
                    try {
                        pm.setComponentEnabledSetting(
                            android.content.ComponentName(packageName, aliasFullName),
                            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP
                        )
                    } catch (e: Exception) {
                        android.util.Log.w("SettingsViewModel", "Failed to disable alias: $aliasFullName", e)
                    }
                }
                android.util.Log.d("SettingsViewModel", "Icon switch completed: $normalizedIconKey")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to switch app icon to $normalizedIconKey", e)
            }
        }
    }

    //  [新增] 切换底栏样式
    fun toggleBottomBarFloating(value: Boolean) { viewModelScope.launch { SettingsManager.setBottomBarFloating(context, value) } }
    
    //  [新增] 底栏显示模式 (0=图标+文字, 1=仅图标, 2=仅文字)
    fun setBottomBarLabelMode(mode: Int) { viewModelScope.launch { SettingsManager.setBottomBarLabelMode(context, mode) } }
    


    fun toggleHeaderBlur(value: Boolean) { viewModelScope.launch { SettingsManager.setHeaderBlurEnabled(context, value) } }
    fun toggleHeaderCollapse(value: Boolean) { viewModelScope.launch { SettingsManager.setHeaderCollapseEnabled(context, value) } }
    fun toggleBottomBarBlur(value: Boolean) { 
        viewModelScope.launch { 
            val resolved = resolveBottomBarBlurToggleState(
                enableBottomBarBlur = value
            )
            SettingsManager.setBottomBarBlurEnabled(context, resolved.bottomBarBlurEnabled)
            SettingsManager.setLiquidGlassEnabled(context, resolved.liquidGlassEnabled)
        } 
    }
    fun setBlurIntensity(intensity: BlurIntensity) { viewModelScope.launch { SettingsManager.setBlurIntensity(context, intensity) } }  //  模糊强度设置
    
    //  [新增] 卡片进场动画开关
    fun toggleCardAnimation(value: Boolean) { viewModelScope.launch { SettingsManager.setCardAnimationEnabled(context, value) } }
    
    //  [新增] 卡片过渡动画开关
    fun toggleCardTransition(value: Boolean) { viewModelScope.launch { SettingsManager.setCardTransitionEnabled(context, value) } }

    // [New] 预测性返回联动动画开关
    fun togglePredictiveBackAnimation(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setPredictiveBackAnimationEnabled(context, value)
        }
    }

    fun toggleSmartVisualGuard(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setSmartVisualGuardEnabled(context, value)
        }
    }
    
    //  [新增] 首页展示模式
    fun setDisplayMode(mode: Int) { 
        viewModelScope.launch { 
            // 兼容旧的 shared preferences
            context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putInt("display_mode", mode).apply()
            // 触发 flow 更新 (如果需要，或者仅仅依赖 prefs 监听? 这里简化处理，假设 ViewModel 只负责写，读在 flow 中)
            // 实际上这里的 flow 是基于 SettingsManager (DataStore) 的。
            // 如果 display_mode 还是 SharedPreferences，我们需要一个 flow 来通过 DataStore 或者手动构建。
            //为了简单统一，建议迁移到 SettingsManager。但为了不破坏 HomeScreen 读取，我们先保持 Prefs，
            // 并在 SettingsManager 中增加对 display_mode 的支持 (或者直接在这里用 MutableStateFlow 桥接?)
            // 鉴于 HomeScreen 可能直接读 Prefs，我们这里只需写 Prefs。
            // 但为了 UI 响应，我们需要通知 UIState。
            // 由于 SettingsManager 目前不管理 display_mode，我们需要添加它。
            // 既然要 refactor，就彻底点。
            SettingsManager.setDisplayMode(context, mode)
        } 
    }
    
    //  [新增] 实验性功能
    fun toggleAuto1080p(value: Boolean) { viewModelScope.launch { SettingsManager.setAuto1080p(context, value) } }
    fun toggleAutoSkipOpEd(value: Boolean) { viewModelScope.launch { SettingsManager.setAutoSkipOpEd(context, value) } }
    fun togglePrefetchVideo(value: Boolean) { viewModelScope.launch { SettingsManager.setPrefetchVideo(context, value) } }
    fun toggleDoubleTapLike(value: Boolean) { viewModelScope.launch { SettingsManager.setDoubleTapLike(context, value) } }
    
    //  [新增] 空降助手
    fun toggleSponsorBlock(value: Boolean) { viewModelScope.launch { SettingsManager.setSponsorBlockEnabled(context, value) } }
    fun toggleSponsorBlockAutoSkip(value: Boolean) { viewModelScope.launch { SettingsManager.setSponsorBlockAutoSkip(context, value) } }
    
    // [New] Splash Screen
    fun toggleSplashEnabled(value: Boolean) { viewModelScope.launch { SettingsManager.setSplashEnabled(context, value) } }
    fun toggleSplashIconAnimationEnabled(value: Boolean) { viewModelScope.launch { SettingsManager.setSplashIconAnimationEnabled(context, value) } }

    // [New] 触感反馈
    fun toggleHapticFeedback(value: Boolean) { viewModelScope.launch { SettingsManager.setHapticFeedbackEnabled(context, value) } }
    
    // [New] Liquid Glass
    fun toggleLiquidGlass(enabled: Boolean) {
        viewModelScope.launch {
            val resolved = resolveLiquidGlassToggleState(enabled)
            SettingsManager.setLiquidGlassEnabled(context, resolved.liquidGlassEnabled)
            SettingsManager.setBottomBarBlurEnabled(context, resolved.bottomBarBlurEnabled)
        }
    }
    
    fun setLiquidGlassStyle(style: com.android.purebilibili.core.store.LiquidGlassStyle) {
        viewModelScope.launch {
            SettingsManager.setLiquidGlassStyle(context, style)
        }
    }

    // [New] 平板导航模式
    fun toggleTabletUseSidebar(value: Boolean) {
        viewModelScope.launch {
            SettingsManager.setTabletUseSidebar(context, value)
        }
    }

    // [New] 网格列数
    fun setGridColumnCount(count: Int) {
        viewModelScope.launch {
            SettingsManager.setGridColumnCount(context, count)
        }
    }
    

}

// Move DisplayMode enum here to be accessible
enum class DisplayMode(val title: String, val description: String, val value: Int) {
    DoubleGrid(title = "双列网格", description = "经典双列瀑布流布局", value = 0),
    SingleColumn(title = "单列视频", description = "类似信息流的单列布局", value = 1)
}
