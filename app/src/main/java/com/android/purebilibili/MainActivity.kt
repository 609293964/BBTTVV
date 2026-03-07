// 文件路径: app/src/main/java/com/android/purebilibili/MainActivity.kt
package com.android.purebilibili

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.ContentScale
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.theme.PureBiliBiliTheme
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.SharedTransitionProvider
import com.android.purebilibili.core.ui.wallpaper.SplashWallpaperLayout
import com.android.purebilibili.core.ui.wallpaper.resolveSplashWallpaperLayout
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.plugin.EyeProtectionOverlay
import com.android.purebilibili.feature.settings.AppUpdateAutoCheckGate
import com.android.purebilibili.feature.settings.AppUpdateCheckResult
import com.android.purebilibili.feature.settings.AppUpdateChecker
import com.android.purebilibili.feature.settings.AppThemeMode
import com.android.purebilibili.feature.settings.RELEASE_DISCLAIMER_ACK_KEY
import com.android.purebilibili.feature.settings.resolveAppUpdateDialogTextColors
import com.android.purebilibili.feature.settings.resolveUpdateReleaseNotesText
import com.android.purebilibili.feature.settings.shouldRunAppEntryAutoCheck
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.ui.overlay.FullscreenPlayerOverlay
import com.android.purebilibili.feature.video.ui.overlay.MiniPlayerOverlay
import com.android.purebilibili.navigation.AppNavigation
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.pow

private const val TAG = "MainActivity"
private const val PREFS_NAME = "app_welcome"
private const val KEY_FIRST_LAUNCH = "first_launch_shown"
private val PLUGIN_INSTALL_HTTPS_HOSTS = setOf(
    "bilipai.app",
    "www.bilipai.app",
    "plugins.bilipai.app"
)

internal fun resolveShortcutRoute(host: String): String? {
    return when (host) {
        "search" -> com.android.purebilibili.navigation.ScreenRoutes.Search.route
        "dynamic" -> com.android.purebilibili.navigation.ScreenRoutes.Dynamic.route
        "favorite" -> com.android.purebilibili.navigation.ScreenRoutes.Favorite.route
        "history" -> com.android.purebilibili.navigation.ScreenRoutes.History.route
        "login" -> com.android.purebilibili.navigation.ScreenRoutes.Login.route
        "playback" -> com.android.purebilibili.navigation.ScreenRoutes.PlaybackSettings.route
        "plugins" -> com.android.purebilibili.navigation.ScreenRoutes.PluginsSettings.createRoute()
        else -> null
    }
}

internal data class PluginInstallDeepLinkRequest(
    val pluginUrl: String
)

internal fun resolvePluginInstallDeepLink(rawDeepLink: String): PluginInstallDeepLinkRequest? {
    val uri = runCatching { URI(rawDeepLink) }.getOrNull() ?: return null
    val normalizedScheme = uri.scheme?.lowercase() ?: return null
    val normalizedHost = uri.host?.lowercase() ?: return null
    val normalizedPath = uri.path?.trim()?.trimEnd('/') ?: ""

    val installLinkMatched = when (normalizedScheme) {
        "bilipai" -> normalizedHost == "plugin" && normalizedPath == "/install"
        "https", "http" -> normalizedHost in PLUGIN_INSTALL_HTTPS_HOSTS &&
            normalizedPath in setOf("/plugin/install", "/plugins/install")
        else -> false
    }
    if (!installLinkMatched) return null

    val queryMap = uri.rawQuery
        ?.split("&")
        ?.mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val pair = part.split("=", limit = 2)
            val key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8)
            val value = URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            key to value
        }
        ?.toMap()
        ?: emptyMap()

    val rawUrl = queryMap["url"]?.trim().orEmpty()
    if (rawUrl.isBlank()) return null

    val targetUri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
    val scheme = targetUri.scheme?.lowercase()
    if (scheme !in listOf("http", "https") || targetUri.host.isNullOrBlank()) {
        return null
    }
    return PluginInstallDeepLinkRequest(pluginUrl = rawUrl)
}

internal fun shouldNavigateToVideoFromNotification(
    currentRoute: String?,
    currentBvid: String?,
    targetBvid: String
): Boolean {
    val isInVideoRoute = currentRoute?.substringBefore("/") == com.android.purebilibili.navigation.VideoRoute.base
    return !(isInVideoRoute && currentBvid == targetBvid)
}

internal fun resolveMainActivityVideoRoute(
    bvid: String,
    cid: Long
): String {
    return com.android.purebilibili.navigation.VideoRoute.resolveVideoRoutePath(
        bvid = bvid,
        cid = cid,
        encodedCover = "",
        startAudio = false,
        autoPortrait = true
    )
}

internal fun resolveMainActivityDynamicRoute(dynamicId: String): String {
    val encodedDynamicId = URLEncoder.encode(dynamicId, StandardCharsets.UTF_8.toString())
    return "dynamic_detail/$encodedDynamicId"
}

internal fun shouldForceStopPlaybackOnUserLeaveHint(
    isInVideoDetail: Boolean,
    stopPlaybackOnExit: Boolean,
    shouldTriggerPip: Boolean
): Boolean {
    return isInVideoDetail && stopPlaybackOnExit && !shouldTriggerPip
}

internal enum class CrashLogPromptAction {
    SHARE,
    DISMISS,
    IGNORE
}

internal fun shouldShowPendingCrashLogPrompt(
    hasPendingCrashSnapshot: Boolean,
    hasPromptBeenHandled: Boolean
): Boolean = hasPendingCrashSnapshot && !hasPromptBeenHandled

internal fun shouldClearPendingCrashLogAfterAction(
    action: CrashLogPromptAction
): Boolean = action != CrashLogPromptAction.IGNORE

internal fun shouldUseRealtimeSplashBlur(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

@Suppress("DEPRECATION")
internal fun resolveLaunchIconResId(context: Context, launchIntent: android.content.Intent?): Int {
    val fromLaunchComponent = runCatching {
        launchIntent?.component
            ?.let { context.packageManager.getActivityInfo(it, 0).getIconResource() }
            ?: 0
    }.getOrDefault(0)
    if (fromLaunchComponent != 0) return fromLaunchComponent

    return context.applicationInfo.icon
}

internal fun shouldShowCustomSplashOverlay(
    customSplashEnabled: Boolean,
    splashUri: String
): Boolean {
    // Flyout animation and custom splash wallpaper can coexist:
    // system splash flyout exits first, then custom wallpaper overlay fades out.
    return customSplashEnabled && splashUri.isNotEmpty()
}

internal fun shouldReadCustomSplashPreferences(): Boolean {
    return true
}

internal fun resolveSplashWallpaperAlignmentBias(
    isTabletLayout: Boolean,
    mobileBias: Float,
    tabletBias: Float
): Float {
    return if (isTabletLayout) tabletBias else mobileBias
}

internal fun resolveSplashWallpaperUriForLaunch(
    randomEnabled: Boolean,
    fixedSplashUri: String,
    poolUris: List<String>,
    launchSeed: Long
): String {
    if (!randomEnabled) return fixedSplashUri
    val candidates = poolUris
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (candidates.isEmpty()) return fixedSplashUri
    val index = Math.floorMod(launchSeed, candidates.size.toLong()).toInt()
    return candidates[index]
}

internal fun shouldStartLocalProxyOnAppLaunch(): Boolean = false

internal fun shouldEnableSplashFlyoutAnimation(
    sdkInt: Int,
    hasCompletedOnboarding: Boolean,
    hasAcceptedReleaseDisclaimer: Boolean,
    splashIconAnimationEnabled: Boolean
): Boolean {
    if (!splashIconAnimationEnabled) return false
    if (sdkInt < Build.VERSION_CODES.S) return false
    return hasCompletedOnboarding && hasAcceptedReleaseDisclaimer
}

internal fun shouldApplySplashRealtimeBlur(
    useRealtimeBlur: Boolean,
    progress: Float
): Boolean {
    return useRealtimeBlur && progress > 0f
}

internal fun splashExitDurationMs(): Long = 920L
internal fun splashExitTranslateYDp(): Float = 220f
internal fun splashExitScaleEnd(): Float = 1.12f
internal fun splashExitBlurRadiusEnd(): Float = 32f
internal fun splashMaxKeepOnScreenMs(): Long = 1000L
internal fun customSplashHoldDurationMs(): Long = 1900L
internal fun customSplashFadeDurationMs(): Int = 1450

internal fun customSplashShouldRender(
    showSplash: Boolean,
    overlayAlpha: Float
): Boolean = showSplash || overlayAlpha > 0.01f

internal fun customSplashFadeProgress(overlayAlpha: Float): Float {
    return (1f - overlayAlpha).coerceIn(0f, 1f)
}

internal fun customSplashOverlayScale(fadeProgress: Float): Float {
    val normalized = fadeProgress.coerceIn(0f, 1f)
    return 1f + (0.024f * normalized.pow(1.08f))
}

internal fun customSplashOverlayScrimAlpha(fadeProgress: Float): Float {
    val normalized = fadeProgress.coerceIn(0f, 1f)
    return (0.14f * normalized.pow(1.2f)).coerceIn(0f, 0.16f)
}

internal fun customSplashExtraBlurDp(fadeProgress: Float): Float {
    val normalized = fadeProgress.coerceIn(0f, 1f)
    return (14f * normalized.pow(1.1f)).coerceAtLeast(0f)
}

internal fun splashExitTravelDistancePx(
    splashHeightPx: Int,
    targetSizePx: Int,
    minTravelPx: Float
): Float {
    if (splashHeightPx <= 0) return minTravelPx
    // Center icon needs to pass the top edge and leave some margin to feel like a full fly-out.
    val dynamicTravel = (splashHeightPx / 2f) + targetSizePx + 24f
    return max(minTravelPx, dynamicTravel)
}

internal fun splashExitBlurProgress(progress: Float): Float {
    val normalized = progress.coerceIn(0f, 1f)
    return normalized.pow(1.6f)
}

internal fun splashExitIconAlpha(progress: Float): Float {
    if (progress <= 0.12f) return 1f
    val normalized = ((progress - 0.12f) / 0.88f).coerceIn(0f, 1f)
    return (1f - normalized.pow(1.6f)).coerceIn(0f, 1f)
}

internal fun splashExitBackgroundAlpha(progress: Float): Float {
    if (progress <= 0.18f) return 1f
    val normalized = ((progress - 0.18f) / 0.82f).coerceIn(0f, 1f)
    return (1f - normalized.pow(1.1f)).coerceIn(0f, 1f)
}

internal fun splashTrailPrimaryAlpha(progress: Float): Float {
    val normalized = progress.coerceIn(0f, 1f)
    if (normalized <= 0.08f) return 0f
    val trailProgress = ((normalized - 0.08f) / 0.92f).coerceIn(0f, 1f)
    return (0.34f * (1f - trailProgress).pow(1.15f)).coerceIn(0f, 1f)
}

internal fun splashTrailSecondaryAlpha(progress: Float): Float {
    val normalized = progress.coerceIn(0f, 1f)
    if (normalized <= 0.16f) return 0f
    val trailProgress = ((normalized - 0.16f) / 0.84f).coerceIn(0f, 1f)
    return (0.2f * (1f - trailProgress).pow(1.22f)).coerceIn(0f, 1f)
}

internal enum class SplashFlyoutTargetType {
    SYSTEM_ICON,
    FALLBACK_ICON,
    SPLASH_ROOT
}

internal fun resolveSplashFlyoutTargetType(
    hasSystemIcon: Boolean,
    hasFallbackIcon: Boolean
): SplashFlyoutTargetType {
    return when {
        hasSystemIcon -> SplashFlyoutTargetType.SYSTEM_ICON
        hasFallbackIcon -> SplashFlyoutTargetType.FALLBACK_ICON
        else -> SplashFlyoutTargetType.SPLASH_ROOT
    }
}

internal fun shouldLogWarmResume(
    hasCompletedInitialResume: Boolean,
    isChangingConfigurations: Boolean
): Boolean {
    return hasCompletedInitialResume && !isChangingConfigurations
}

@OptIn(androidx.media3.common.util.UnstableApi::class) // 解决 UnsafeOptInUsageError，因为 AppNavigation 内部使用了不稳定的 API
class MainActivity : ComponentActivity() {
    
    //  PiP 状态
    var isInPipMode by mutableStateOf(false)
        private set
    
    //  是否在视频页面 (用于决定是否进入 PiP)
    var isInVideoDetail by mutableStateOf(false)
    
    //  小窗管理器
    private lateinit var miniPlayerManager: MiniPlayerManager
    private var hasCompletedInitialResume = false
    private var splashFlyoutEnabledAtCreate = false
    private var splashExitCallbackTriggered = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        //  安装 SplashScreen
        val splashScreen = installSplashScreen()
        val welcomePrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val splashFlyoutEnabled = shouldEnableSplashFlyoutAnimation(
            sdkInt = Build.VERSION.SDK_INT,
            hasCompletedOnboarding = welcomePrefs.getBoolean(KEY_FIRST_LAUNCH, false),
            hasAcceptedReleaseDisclaimer = welcomePrefs.getBoolean(RELEASE_DISCLAIMER_ACK_KEY, false),
            splashIconAnimationEnabled = SettingsManager.isSplashIconAnimationEnabledSync(this)
        )
        val splashFlyoutIconResId = resolveLaunchIconResId(this, intent)
        splashFlyoutEnabledAtCreate = splashFlyoutEnabled
        Logger.d(
            TAG,
            "🚀 Splash setup. flyoutEnabled=$splashFlyoutEnabled, firstLaunchShown=${welcomePrefs.getBoolean(KEY_FIRST_LAUNCH, false)}, disclaimerAck=${welcomePrefs.getBoolean(RELEASE_DISCLAIMER_ACK_KEY, false)}, taskRoot=$isTaskRoot, savedState=${savedInstanceState != null}, intentFlags=0x${intent?.flags?.toString(16) ?: "0"}, launchIconResId=$splashFlyoutIconResId"
        )
        
        //  🚀 [启动优化] 立即开始预加载首页数据
        // 这个必须尽早调用，利用开屏动画的时间并行加载数据
        com.android.purebilibili.data.repository.VideoRepository.preloadHomeData()
        
        super.onCreate(savedInstanceState)
        //  初始调用，后续会根据主题动态更新
        enableEdgeToEdge()
        
        // 初始化小窗管理器
        miniPlayerManager = MiniPlayerManager.getInstance(this)
        
        //  🚀 [启动优化] 保持 Splash 直到数据加载完成或超时
        var isDataReady = false
        val startTime = System.currentTimeMillis()
        
        splashScreen.setKeepOnScreenCondition {
            // 检查数据是否就绪
            if (com.android.purebilibili.data.repository.VideoRepository.isHomeDataReady()) {
                isDataReady = true
            }
            
            // 计算耗时
            val elapsed = System.currentTimeMillis() - startTime
            
            // 条件：数据未就绪 且 未超时(1400ms)
            // 如果超时，强制进入（会显示骨架屏），避免用户以为死机
            val shouldKeep = !isDataReady && elapsed < splashMaxKeepOnScreenMs()
            
            if (!shouldKeep) {
                 Logger.d(TAG, "🚀 Splash dismissed. DataReady=$isDataReady, Elapsed=${elapsed}ms")
            }
            
            shouldKeep
        }

        if (splashFlyoutEnabled) {
            Logger.d(TAG, "🚀 Splash flyout exit listener registered")
            splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
                splashExitCallbackTriggered = true
                runCatching {
                    val splashView = splashScreenViewProvider.view
                    val systemIconView = splashScreenViewProvider.iconView
                    if (systemIconView == null) {
                        Logger.w(TAG, "⚠️ Splash system iconView unavailable, attempting fallback icon")
                    }
                    val fallbackIconView = if (systemIconView == null) {
                        (splashView as? android.view.ViewGroup)?.let { container ->
                            val sizePx = (112f * resources.displayMetrics.density).toInt()
                            ImageView(this).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageResource(splashFlyoutIconResId)
                                if (container is android.widget.FrameLayout) {
                                    container.addView(
                                        this,
                                        android.widget.FrameLayout.LayoutParams(
                                            sizePx,
                                            sizePx,
                                            android.view.Gravity.CENTER
                                        )
                                    )
                                } else {
                                    container.addView(
                                        this,
                                        android.view.ViewGroup.LayoutParams(sizePx, sizePx)
                                    )
                                }
                            }
                        }
                    } else {
                        null
                    }
                    val animatedTarget = systemIconView ?: fallbackIconView ?: splashView
                    val targetType = resolveSplashFlyoutTargetType(
                        hasSystemIcon = systemIconView != null,
                        hasFallbackIcon = fallbackIconView != null
                    )
                    Logger.d(
                        TAG,
                        "🚀 Splash exit animation start. targetType=$targetType, hasSystemIcon=${systemIconView != null}, hasFallbackIcon=${fallbackIconView != null}"
                    )
                    if (targetType == SplashFlyoutTargetType.SPLASH_ROOT) {
                        Logger.w(
                            TAG,
                            "⚠️ Splash flyout degraded to splash root animation (icon target unavailable)"
                        )
                    }
                    val frameContainer = splashView as? android.widget.FrameLayout
                    val targetDrawableState = (animatedTarget as? ImageView)
                        ?.drawable
                        ?.constantState
                    val targetSizePx = if (animatedTarget.width > 0 && animatedTarget.height > 0) {
                        minOf(animatedTarget.width, animatedTarget.height)
                    } else {
                        (112f * resources.displayMetrics.density).toInt()
                    }
                    Logger.d(
                        TAG,
                        "🚀 Splash exit target metrics. targetSizePx=$targetSizePx, hasFrameContainer=${frameContainer != null}, useRealtimeBlur=${shouldUseRealtimeSplashBlur(Build.VERSION.SDK_INT)}"
                    )
                    fun createTrailView(): ImageView? {
                        val container = frameContainer ?: return null
                        val drawable = targetDrawableState?.newDrawable(resources)?.mutate()
                        if (drawable == null && splashFlyoutIconResId == 0) return null
                        return ImageView(this).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            alpha = 0f
                            if (drawable != null) {
                                setImageDrawable(drawable)
                            } else {
                                setImageResource(splashFlyoutIconResId)
                            }
                            val anchorIndex = container.indexOfChild(animatedTarget)
                                .let { if (it >= 0) it else container.childCount }
                            container.addView(
                                this,
                                anchorIndex,
                                android.widget.FrameLayout.LayoutParams(
                                    targetSizePx,
                                    targetSizePx,
                                    android.view.Gravity.CENTER
                                )
                            )
                        }
                    }
                    val primaryTrailView = createTrailView()
                    val secondaryTrailView = createTrailView()
                    val minTranslateYPx = splashExitTranslateYDp() * resources.displayMetrics.density
                    val translateYPx = splashExitTravelDistancePx(
                        splashHeightPx = splashView.height,
                        targetSizePx = targetSizePx,
                        minTravelPx = minTranslateYPx
                    )
                    val supportsRealtimeBlur = shouldUseRealtimeSplashBlur(Build.VERSION.SDK_INT)
                    var blurEffectEnabled = supportsRealtimeBlur
                    val animator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = splashExitDurationMs()
                        interpolator = android.view.animation.PathInterpolator(0.12f, 0.98f, 0.2f, 1.0f)
                        addUpdateListener { valueAnimator ->
                            val progress = valueAnimator.animatedValue as Float
                            val trailProgressPrimary = ((progress - 0.08f) / 0.92f).coerceIn(0f, 1f)
                            val trailProgressSecondary = ((progress - 0.16f) / 0.84f).coerceIn(0f, 1f)
                            animatedTarget.translationY = -translateYPx * progress
                            animatedTarget.alpha = splashExitIconAlpha(progress)
                            splashView.alpha = splashExitBackgroundAlpha(progress)

                            val scale = 1f + (splashExitScaleEnd() - 1f) * progress
                            animatedTarget.scaleX = scale
                            animatedTarget.scaleY = scale

                            primaryTrailView?.let { trail ->
                                trail.translationY = -translateYPx * trailProgressPrimary
                                trail.alpha = splashTrailPrimaryAlpha(progress)
                                trail.scaleX = scale * 1.03f
                                trail.scaleY = scale * 1.03f
                            }

                            secondaryTrailView?.let { trail ->
                                trail.translationY = -translateYPx * trailProgressSecondary
                                trail.alpha = splashTrailSecondaryAlpha(progress)
                                trail.scaleX = scale * 1.06f
                                trail.scaleY = scale * 1.06f
                            }

                            if (shouldApplySplashRealtimeBlur(blurEffectEnabled, progress)) {
                                val radius = splashExitBlurRadiusEnd() * splashExitBlurProgress(progress)
                                runCatching {
                                    splashView.setRenderEffect(
                                        android.graphics.RenderEffect.createBlurEffect(
                                            radius * 0.55f,
                                            radius * 0.55f,
                                            android.graphics.Shader.TileMode.CLAMP
                                        )
                                    )
                                    animatedTarget.setRenderEffect(
                                        android.graphics.RenderEffect.createBlurEffect(
                                            radius,
                                            radius,
                                            android.graphics.Shader.TileMode.CLAMP
                                        )
                                    )
                                    primaryTrailView?.setRenderEffect(
                                        android.graphics.RenderEffect.createBlurEffect(
                                            radius * 1.2f,
                                            radius * 1.2f,
                                            android.graphics.Shader.TileMode.CLAMP
                                        )
                                    )
                                    secondaryTrailView?.setRenderEffect(
                                        android.graphics.RenderEffect.createBlurEffect(
                                            radius * 1.45f,
                                            radius * 1.45f,
                                            android.graphics.Shader.TileMode.CLAMP
                                        )
                                    )
                                }.onFailure {
                                    blurEffectEnabled = false
                                    splashView.setRenderEffect(null)
                                    animatedTarget.setRenderEffect(null)
                                    primaryTrailView?.setRenderEffect(null)
                                    secondaryTrailView?.setRenderEffect(null)
                                    Logger.w(TAG, "⚠️ Splash realtime blur failed, fallback to non-blur flyout", it)
                                }
                            }
                        }
                    }
                    animator.doOnEnd {
                        if (supportsRealtimeBlur) {
                            splashView.setRenderEffect(null)
                            animatedTarget.setRenderEffect(null)
                            primaryTrailView?.setRenderEffect(null)
                            secondaryTrailView?.setRenderEffect(null)
                        }
                        primaryTrailView?.let { frameContainer?.removeView(it) }
                        secondaryTrailView?.let { frameContainer?.removeView(it) }
                        splashScreenViewProvider.remove()
                    }
                    animator.start()
                }.onFailure {
                    Logger.e(TAG, "❌ Splash exit animation failed, removing splash immediately", it)
                    splashScreenViewProvider.remove()
                }
            }
        }

        //  [新增] 处理 deep link 或分享意图
        handleIntent(intent)
        
        // --- 📺 DLNA Service Init ---
        // Android 12+ 需要运行时权限
        // requestDlnaPermissionsAndBind()
        
        if (shouldStartLocalProxyOnAppLaunch()) {
            // Optional warmup path; default keeps proxy off cold-start critical path.
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val started = com.android.purebilibili.feature.cast.LocalProxyServer.ensureStarted()
                    if (started) {
                        com.android.purebilibili.core.util.Logger.d(TAG, "📺 Local Proxy Server started on port 8901")
                    } else {
                        com.android.purebilibili.core.util.Logger.d(TAG, "📺 Local Proxy Server already running")
                    }
                } catch (e: Exception) {
                    com.android.purebilibili.core.util.Logger.e(TAG, "❌ Failed to start Local Proxy Server", e)
                }
            }
        }

        setContent {
            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            val navController = androidx.navigation.compose.rememberNavController()
            var startupUpdateCheckResult by remember { mutableStateOf<AppUpdateCheckResult?>(null) }
            var pendingCrashSnapshotPath by remember {
                mutableStateOf(Logger.getPendingCrashSnapshotPath(context))
            }
            var hasHandledCrashPrompt by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val autoCheckUpdateEnabled = SettingsManager.getAutoCheckAppUpdate(context).first()
                val gateAllowsCheck = AppUpdateAutoCheckGate.tryMarkChecked()
                if (shouldRunAppEntryAutoCheck(autoCheckUpdateEnabled, gateAllowsCheck)) {
                    AppUpdateChecker.check(BuildConfig.VERSION_NAME).onSuccess { info ->
                        if (info.isUpdateAvailable) {
                            startupUpdateCheckResult = info
                        }
                    }
                }
            }
            
            //  [新增] 监听 pendingVideoId 并导航到视频详情页
            LaunchedEffect(pendingVideoId) {
                pendingVideoId?.let { videoId ->
                    val currentEntry = navController.currentBackStackEntry
                    val currentRoute = currentEntry?.destination?.route
                    val currentBvid = currentEntry?.arguments?.getString("bvid")
                    val shouldNavigate = shouldNavigateToVideoFromNotification(
                        currentRoute = currentRoute,
                        currentBvid = currentBvid,
                        targetBvid = videoId
                    )

                    if (shouldNavigate) {
                        Logger.d(TAG, "🚀 导航到视频: $videoId")
                        miniPlayerManager.isNavigatingToVideo = true
                        navController.navigate(resolveMainActivityVideoRoute(bvid = videoId, cid = 0L)) {
                            launchSingleTop = true
                        }
                    } else {
                        Logger.d(TAG, "🎯 已在目标视频页，跳过重复导航: $videoId")
                    }
                    pendingVideoId = null
                }
            }
            
            // 🚀 [新增] 监听 pendingRoute 并导航到对应页面 (App Shortcuts)
            LaunchedEffect(pendingRoute) {
                pendingRoute?.let { route ->
                    Logger.d(TAG, "🚀 导航到快捷入口: $route")
                    val targetRoute = resolveShortcutRoute(route)
                    targetRoute?.let { 
                        navController.navigate(it) { launchSingleTop = true }
                    }
                    pendingRoute = null  // 清除，避免重复导航
                }
            }

            LaunchedEffect(pendingNavigationRoute) {
                pendingNavigationRoute?.let { route ->
                    Logger.d(TAG, "🚀 导航到指定页面: $route")
                    navController.navigate(route) { launchSingleTop = true }
                    pendingNavigationRoute = null
                }
            }
            
            //  首次启动检测已移交 AppNavigation 处理
            // val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
            // var showWelcome by remember { mutableStateOf(!prefs.getBoolean(KEY_FIRST_LAUNCH, false)) }

            // 1. 获取存储的模式 (默认为跟随系统)
            val themeMode by SettingsManager.getThemeMode(context).collectAsState(initial = AppThemeMode.FOLLOW_SYSTEM)

            //  检查并请求所有文件访问权限 (Android 11+)
            //  检查并请求所有文件访问权限 (已移除启动时强制检查，改为按需申请)
            // LaunchedEffect(Unit) { ... }

            //  2. [新增] 获取动态取色设置 (默认为 true)
            val dynamicColor by SettingsManager.getDynamicColor(context).collectAsState(initial = true)
            
            //  3. [新增] 获取主题色索引
            val themeColorIndex by SettingsManager.getThemeColorIndex(context).collectAsState(initial = 0)
            
            // 4. 获取系统当前的深色状态
            val systemInDark = isSystemInDarkTheme()

            // 5. 根据枚举值决定是否开启 DarkTheme
            val useDarkTheme = when (themeMode) {
                AppThemeMode.FOLLOW_SYSTEM -> systemInDark // 跟随系统：系统黑则黑，系统白则白
                AppThemeMode.LIGHT -> false                // 强制浅色
                AppThemeMode.DARK -> true                  // 强制深色
                AppThemeMode.AMOLED -> true                // 强制纯黑
            }
            val useAmoledDarkTheme = themeMode == AppThemeMode.AMOLED
            val effectiveDynamicColor = dynamicColor && !useAmoledDarkTheme

            //  [新增] 根据主题动态更新状态栏样式
            LaunchedEffect(useDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkTheme) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }
            
            //  全局 Haze 状态，用于实现毛玻璃效果
            // 强制启用 blur，避免部分设备（如 Android 12）默认降级为仅半透明遮罩
            val mainHazeState = rememberRecoverableHazeState(initialBlurEnabled = true)
            
            //  📐 [平板适配] 计算窗口尺寸类
            val windowSizeClass = com.android.purebilibili.core.util.calculateWindowSizeClass()

            // 6. 传入参数
            PureBiliBiliTheme(
                darkTheme = useDarkTheme,
                dynamicColor = effectiveDynamicColor,
                amoledDarkTheme = useAmoledDarkTheme,
                themeColorIndex = themeColorIndex, //  传入主题色索引

            ) {
                com.android.purebilibili.core.ui.blur.ProvideUnifiedBlurIntensity {
                    //  📐 [平板适配] 提供全局 WindowSizeClass
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.android.purebilibili.core.util.LocalWindowSizeClass provides windowSizeClass
                    ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)  // 📐 [修复] 防止平板端返回后出现黑边
                    ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        //  [修复] 移除 .haze() 以避免与 hazeSource/hazeEffect 冲突
                        // 每个 Screen 自己管理 hazeSource（内容）和 hazeEffect（头部/底栏）
                        Box(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            //  SharedTransitionProvider 包裹导航，启用共享元素过渡
                            SharedTransitionProvider {
                                AppNavigation(
                                    navController = navController,
                                    miniPlayerManager = miniPlayerManager,
                                    isInPipMode = isInPipMode,
                                    onVideoDetailEnter = { 
                                        isInVideoDetail = true
                                        Logger.d(TAG, " 进入视频详情页")
                                    },
                                    onVideoDetailExit = { 
                                        isInVideoDetail = false
                                        Logger.d(TAG, "🔙 退出视频详情页")
                                    },
                                    mainHazeState = mainHazeState //  传递全局 Haze 状态
                                )
                            }
                            
                            //  OnboardingBottomSheet 等其他 overlay 组件

                        }
                    }
                    //  小窗全屏状态
                    var showFullscreen by remember { mutableStateOf(false) }
                    //  小窗播放器覆盖层 (非 PiP 模式下显示)
                    if (!isInPipMode) {
                        MiniPlayerOverlay(
                            miniPlayerManager = miniPlayerManager,
                            onExpandClick = {
                                if (miniPlayerManager.isLiveMode) {
                                    // 📺 直播小窗展开：导航回直播间
                                    val roomId = miniPlayerManager.currentRoomId
                                    val liveTitle = miniPlayerManager.currentTitle
                                    val liveUname = miniPlayerManager.currentLiveUname
                                    miniPlayerManager.exitMiniMode(animate = false)
                                    navController.navigate(
                                        com.android.purebilibili.navigation.ScreenRoutes.Live.createRoute(roomId, liveTitle, liveUname)
                                    ) {
                                        launchSingleTop = true
                                    }
                                } else {
                                    //  [修改] 导航回详情页，而不是只显示全屏播放器
                                    miniPlayerManager.currentBvid?.let { bvid ->
                                        miniPlayerManager.isNavigatingToVideo = true
                                        miniPlayerManager.exitMiniMode(animate = false)
                                        val cid = miniPlayerManager.currentCid
                                        navController.navigate(resolveMainActivityVideoRoute(bvid = bvid, cid = cid)) {
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        )
                    }
                    
                    //  全屏播放器覆盖层（包含亮度、音量、进度调节）
                    if (showFullscreen) {
                        FullscreenPlayerOverlay(
                            miniPlayerManager = miniPlayerManager,
                            onDismiss = { 
                                showFullscreen = false
                                miniPlayerManager.enterMiniMode()
                            },
                            onNavigateToDetail = {
                                //  关闭全屏覆盖层并导航到视频详情页
                                showFullscreen = false
                                miniPlayerManager.currentBvid?.let { bvid ->
                                    miniPlayerManager.isNavigatingToVideo = true
                                    miniPlayerManager.exitMiniMode(animate = false)
                                    //  [修复] 使用正确的 cid，而不是 0
                                    val cid = miniPlayerManager.currentCid
                                    navController.navigate(resolveMainActivityVideoRoute(bvid = bvid, cid = cid)) {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                    
                    //  护眼模式覆盖层（最顶层，应用于所有内容）
                    EyeProtectionOverlay()

                    // PiP 模式专用播放器 (只在 PiP 模式下显示，覆盖所有内容)
                    if (isInPipMode) {
                        PiPVideoPlayer(miniPlayerManager = miniPlayerManager)
                    }
                    
                    // [New] Custom Splash Wallpaper Overlay
                    val readCustomSplashPrefs = remember { shouldReadCustomSplashPreferences() }
                    val splashUri = remember(readCustomSplashPrefs) {
                        val fixedUri = SettingsManager.getSplashWallpaperUriSync(context)
                        val randomEnabled = SettingsManager.isSplashRandomEnabledSync(context)
                        val splashRandomPool = SettingsManager.getSplashRandomPoolUrisSync(context)
                        resolveSplashWallpaperUriForLaunch(
                            randomEnabled = randomEnabled,
                            fixedSplashUri = fixedUri,
                            poolUris = splashRandomPool,
                            launchSeed = System.currentTimeMillis()
                        )
                    }
                    val splashAlignmentBias = remember(readCustomSplashPrefs, windowSizeClass.widthSizeClass) {
                        val mobileBias = SettingsManager.getSplashAlignmentSync(context, isTablet = false)
                        val tabletBias = SettingsManager.getSplashAlignmentSync(context, isTablet = true)
                        resolveSplashWallpaperAlignmentBias(
                            isTabletLayout = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact,
                            mobileBias = mobileBias,
                            tabletBias = tabletBias
                        )
                    }
                    val showCustomSplashInitially = remember(splashUri) {
                        shouldShowCustomSplashOverlay(
                            customSplashEnabled = SettingsManager.isSplashEnabledSync(context),
                            splashUri = splashUri
                        )
                    }
                    var showSplash by remember { mutableStateOf(showCustomSplashInitially) }
                    // [Optimization] If we delayed enough in splash screen, we might want to skip custom splash or show it briefly?
                    // Logic: If user uses custom splash, system splash shows icon, then custom splash shows wallpaper.
                    // If we use setKeepOnScreenCondition, system splash (icon) stays longer.
                    // This is acceptable behavior: Icon -> Wallpaper (if enabled) -> App.
                    // Or if custom wallpaper is enabled, maybe we shouldn't delay system splash?
                    // User request: "当用户看见遮罩的时候，异步加载首页视频". Mask usually means System Splash (Icon) OR Custom Wallpaper.
                    // Implementing delay on System Splash ensures data is likely ready when ANY content shows.

                    LaunchedEffect(showCustomSplashInitially) {
                        if (showCustomSplashInitially) {
                            showSplash = true
                            kotlinx.coroutines.delay(customSplashHoldDurationMs())
                            showSplash = false
                        } else {
                            showSplash = false
                        }
                    }
                    val splashOverlayAlpha by animateFloatAsState(
                        targetValue = if (showSplash) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = customSplashFadeDurationMs(),
                            easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                        ),
                        label = "customSplashOverlayAlpha"
                    )
                    val splashFadeProgress = customSplashFadeProgress(splashOverlayAlpha)
                    val splashOverlayScale = customSplashOverlayScale(splashFadeProgress)
                    val splashExtraBlur = customSplashExtraBlurDp(splashFadeProgress)
                    val splashTailScrimAlpha = customSplashOverlayScrimAlpha(splashFadeProgress)

                    if (customSplashShouldRender(showSplash, splashOverlayAlpha) && splashUri.isNotEmpty()) {
                        val splashWallpaperLayout = remember(windowSizeClass.widthSizeClass) {
                            resolveSplashWallpaperLayout(windowSizeClass.widthSizeClass)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = splashOverlayAlpha)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            when (splashWallpaperLayout) {
                                SplashWallpaperLayout.FULL_CROP -> {
                                    AsyncImage(
                                        model = splashUri,
                                        contentDescription = "Splash Wallpaper",
                                        alignment = androidx.compose.ui.BiasAlignment(0f, splashAlignmentBias),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = splashOverlayScale,
                                                scaleY = splashOverlayScale
                                            )
                                            .blur(splashExtraBlur.dp)
                                    )
                                }

                                SplashWallpaperLayout.POSTER_CARD_BLUR_BG -> {
                                    AsyncImage(
                                        model = splashUri,
                                        contentDescription = null,
                                        alignment = androidx.compose.ui.BiasAlignment(0f, splashAlignmentBias),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer(
                                                scaleX = splashOverlayScale,
                                                scaleY = splashOverlayScale
                                            )
                                            .blur((56f + splashExtraBlur).dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color.Black.copy(
                                                    alpha = (0.16f + splashTailScrimAlpha * 0.5f).coerceAtMost(0.26f)
                                                )
                                            )
                                    )
                                    Card(
                                        shape = RoundedCornerShape(26.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .graphicsLayer(
                                                scaleX = 1f + (splashFadeProgress * 0.015f),
                                                scaleY = 1f + (splashFadeProgress * 0.015f)
                                            )
                                            .fillMaxWidth(
                                                if (windowSizeClass.widthSizeClass == com.android.purebilibili.core.util.WindowWidthSizeClass.Expanded) {
                                                    0.34f
                                                } else {
                                                    0.48f
                                                }
                                            )
                                            .widthIn(min = 190.dp, max = 340.dp)
                                            .aspectRatio(9f / 16f)
                                    ) {
                                        AsyncImage(
                                            model = splashUri,
                                            contentDescription = "Splash Wallpaper Poster",
                                            alignment = androidx.compose.ui.BiasAlignment(0f, splashAlignmentBias),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .graphicsLayer(
                                                    scaleX = splashOverlayScale,
                                                    scaleY = splashOverlayScale
                                                )
                                                .blur((splashExtraBlur * 0.35f).dp)
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = splashTailScrimAlpha))
                            )
                        }
                    }

                    startupUpdateCheckResult?.let { info ->
                        val resolvedReleaseNotes = remember(info.releaseNotes) {
                            resolveUpdateReleaseNotesText(info.releaseNotes)
                        }
                        val isDialogDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        val dialogTextColors = remember(isDialogDarkTheme) {
                            resolveAppUpdateDialogTextColors(
                                isDarkTheme = isDialogDarkTheme
                            )
                        }
                        val releaseNotesScrollState = rememberScrollState()
                        com.android.purebilibili.core.ui.IOSAlertDialog(
                            onDismissRequest = { startupUpdateCheckResult = null },
                            title = {
                                Text(
                                    text = "发现新版本 v${info.latestVersion}",
                                    color = dialogTextColors.titleColor
                                )
                            },
                            text = {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "当前版本 v${info.currentVersion}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = dialogTextColors.currentVersionColor
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = resolvedReleaseNotes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = dialogTextColors.releaseNotesColor,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                            .verticalScroll(releaseNotesScrollState)
                                    )
                                }
                            },
                            confirmButton = {
                                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                                    startupUpdateCheckResult = null
                                    uriHandler.openUri(info.releaseUrl)
                                }) { Text("前往下载") }
                            },
                            dismissButton = {
                                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                                    startupUpdateCheckResult = null
                                }) { Text("稍后") }
                            }
                        )
                    }

                    if (
                        shouldShowPendingCrashLogPrompt(
                            hasPendingCrashSnapshot = pendingCrashSnapshotPath != null,
                            hasPromptBeenHandled = hasHandledCrashPrompt
                        )
                    ) {
                        com.android.purebilibili.core.ui.IOSAlertDialog(
                            onDismissRequest = {
                                hasHandledCrashPrompt = true
                                if (shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.DISMISS)) {
                                    Logger.clearPendingCrashSnapshot(context)
                                    pendingCrashSnapshotPath = null
                                }
                            },
                            title = {
                                Text(text = "检测到上次闪退日志")
                            },
                            text = {
                                Text(
                                    text = "应用已自动保存一份崩溃快照，并同步导出到 Download/BiliPai/logs/last_crash_log.txt。现在可以直接分享给开发者排查，也可以先关闭提示。"
                                )
                            },
                            confirmButton = {
                                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                                    hasHandledCrashPrompt = true
                                    Logger.sharePendingCrashSnapshot(context)
                                    if (shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.SHARE)) {
                                        Logger.clearPendingCrashSnapshot(context)
                                        pendingCrashSnapshotPath = null
                                    }
                                }) { Text("分享") }
                            },
                            dismissButton = {
                                com.android.purebilibili.core.ui.IOSDialogAction(onClick = {
                                    hasHandledCrashPrompt = true
                                    if (shouldClearPendingCrashLogAfterAction(CrashLogPromptAction.DISMISS)) {
                                        Logger.clearPendingCrashSnapshot(context)
                                        pendingCrashSnapshotPath = null
                                    }
                                }) { Text("关闭") }
                            }
                        )
                    }

                    }
                    }  // 📐 CompositionLocalProvider 结束
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (shouldLogWarmResume(hasCompletedInitialResume, isChangingConfigurations)) {
            Logger.d(
                TAG,
                "🔁 Warm resume path. splash flyout is not expected (Activity already created). flyoutEnabledAtCreate=$splashFlyoutEnabledAtCreate, splashExitCallbackTriggered=$splashExitCallbackTriggered"
            )
        }
    }

    override fun onResume() {
        super.onResume()
        miniPlayerManager.clearUserLeaveHint()
        if (!hasCompletedInitialResume) {
            hasCompletedInitialResume = true
        }
    }
    
    //  用户按 Home 键或切换应用时触发
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        
        Logger.d(TAG, "👋 onUserLeaveHint 触发, isInVideoDetail=$isInVideoDetail, isMiniMode=${miniPlayerManager.isMiniMode}")
        miniPlayerManager.markUserLeaveHint()
        miniPlayerManager.refreshMediaSessionBinding()
        
        val stopPlaybackOnExit = SettingsManager.getStopPlaybackOnExitSync(this)
        //  [重构] 使用新的模式判断方法
        val shouldEnterPip = miniPlayerManager.shouldEnterPip()
        val currentMode = miniPlayerManager.getCurrentMode()
        val isActuallyPlaying = miniPlayerManager.isPlaying || (miniPlayerManager.player?.isPlaying == true)
        
        //  🔧 [修复] PiP 只应在视频详情页触发，小窗模式下不应触发系统 PiP
        // 原因：小窗模式意味着用户已离开视频详情页（在首页等其他页面），
        // 此时从其他页面返回桌面不应进入 PiP
        val shouldTriggerPip = isInVideoDetail 
            && shouldEnterPip 
            && isActuallyPlaying

        val shouldForceStopPlayback = shouldForceStopPlaybackOnUserLeaveHint(
            isInVideoDetail = isInVideoDetail,
            stopPlaybackOnExit = stopPlaybackOnExit,
            shouldTriggerPip = shouldTriggerPip
        )
        if (shouldForceStopPlayback) {
            Logger.d(TAG, "🛑 stopPlaybackOnExit=true, leaving by Home, force stop playback immediately")
            miniPlayerManager.markLeavingByNavigation()
        }
        
        Logger.d(TAG, " miniPlayerMode=$currentMode, shouldEnterPip=$shouldEnterPip, isPlaying=$isActuallyPlaying, shouldTriggerPip=$shouldTriggerPip, API=${Build.VERSION.SDK_INT}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shouldTriggerPip) {
            try {
                Logger.d(TAG, " 尝试进入 PiP 模式...")
                
                val pipParams = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                
                // Android 12+: 启用自动进入和无缝调整
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pipParams.setAutoEnterEnabled(true)
                    pipParams.setSeamlessResizeEnabled(true)
                }
                
                enterPictureInPictureMode(pipParams.build())
                Logger.d(TAG, " 成功进入 PiP 模式")
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e(TAG, " 进入 PiP 失败", e)
            }
        } else {
            Logger.d(TAG, "⏳ 未满足 PiP 条件: API>=${Build.VERSION_CODES.O}=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.O}, shouldTriggerPip=$shouldTriggerPip")
        }
    }
    
    //  PiP 模式变化回调
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        Logger.d(TAG, " PiP 模式变化: $isInPictureInPictureMode")
    }
    
    //  [新增] 处理 singleTop 模式下的新 Intent
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
    
    // 📺 [DLNA] 权限请求和服务绑定 - 移除自动请求，改为按需请求
    // private val dlnaPermissionLauncher = ...
    
    // private fun requestDlnaPermissionsAndBind() { ... }
    
    //  待导航的视频 ID（用于在 Compose 中触发导航）
    var pendingVideoId by mutableStateOf<String?>(null)
    var pendingRoute by mutableStateOf<String?>(null)  // 🚀 App Shortcuts: pending route
    var pendingNavigationRoute by mutableStateOf<String?>(null)
        private set
    
    /**
     *  [新增] 处理 Deep Link 和分享意图
     */
    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        
        Logger.d(TAG, "🔗 handleIntent: action=${intent.action}, data=${intent.data}")
        
        when (intent.action) {
            android.content.Intent.ACTION_VIEW -> {
                // 点击链接打开
                val uri = intent.data
                if (uri != null) {
                    val scheme = uri.scheme ?: ""
                    val host = uri.host ?: ""

                    val pluginInstallRequest = resolvePluginInstallDeepLink(uri.toString())
                    if (pluginInstallRequest != null) {
                        pendingNavigationRoute = com.android.purebilibili.navigation.ScreenRoutes.PluginsSettings
                            .createRoute(importUrl = pluginInstallRequest.pluginUrl)
                        Logger.d(TAG, "🚀 Plugin install deep link detected: ${pluginInstallRequest.pluginUrl}")
                        return
                    }
                    
                    // 🚀 App Shortcuts: bilipai:// scheme
                    if (scheme == "bilipai") {
                        pendingRoute = host  // e.g., "search", "dynamic", "favorite", "history"
                        Logger.d(TAG, "🚀 App Shortcut detected: $host")
                    }
                    // b23.tv 短链接需要重定向
                    else if (host.contains("b23.tv")) {
                        resolveShortLinkAndNavigate(uri.toString())
                    } else {
                        // bilibili.com 直接解析
                        val result = when (scheme.lowercase()) {
                            "http", "https" -> com.android.purebilibili.core.util.BilibiliUrlParser.parse(uri.toString())
                            else -> com.android.purebilibili.core.util.BilibiliUrlParser.parseUri(uri)
                        }
                        if (result.isValid) {
                            result.getVideoId()?.let { videoId ->
                                Logger.d(TAG, "📺 从 Deep Link 提取到视频: $videoId")
                                pendingVideoId = videoId
                                return
                            }
                            result.getDynamicTargetId()?.let { dynamicId ->
                                Logger.d(TAG, "📝 从 Deep Link 提取到动态: $dynamicId")
                                pendingNavigationRoute = resolveMainActivityDynamicRoute(dynamicId)
                            }
                        }
                    }
                }
            }
            android.content.Intent.ACTION_SEND -> {
                // 分享文本到 app
                val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                if (text != null) {
                    Logger.d(TAG, "📤 收到分享文本: $text")
                    
                    // 检查是否包含 b23.tv 短链接
                    val urls = com.android.purebilibili.core.util.BilibiliUrlParser.extractUrls(text)
                    val pluginInstallLink = urls.firstOrNull { resolvePluginInstallDeepLink(it) != null }
                    val shortLink = urls.find { it.contains("b23.tv") }

                    if (pluginInstallLink != null) {
                        val pluginInstallRequest = resolvePluginInstallDeepLink(pluginInstallLink)
                        if (pluginInstallRequest != null) {
                            pendingNavigationRoute = com.android.purebilibili.navigation.ScreenRoutes.PluginsSettings
                                .createRoute(importUrl = pluginInstallRequest.pluginUrl)
                            Logger.d(TAG, "🚀 Plugin install shared link detected: ${pluginInstallRequest.pluginUrl}")
                            return
                        }
                    }
                    
                    if (shortLink != null) {
                        resolveShortLinkAndNavigate(shortLink)
                    } else {
                        // 直接解析
                        val result = com.android.purebilibili.core.util.BilibiliUrlParser.parse(text)
                        if (result.isValid) {
                            result.getVideoId()?.let { videoId ->
                                Logger.d(TAG, "📺 从分享文本提取到视频: $videoId")
                                pendingVideoId = videoId
                                return
                            }
                            result.getDynamicTargetId()?.let { dynamicId ->
                                Logger.d(TAG, "📝 从分享文本提取到动态: $dynamicId")
                                pendingNavigationRoute = resolveMainActivityDynamicRoute(dynamicId)
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     *  解析 b23.tv 短链接并导航
     */
    private fun resolveShortLinkAndNavigate(shortUrl: String) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val fullUrl = com.android.purebilibili.core.util.BilibiliUrlParser.resolveShortUrl(shortUrl)
            if (fullUrl != null) {
                val result = com.android.purebilibili.core.util.BilibiliUrlParser.parse(fullUrl)
                if (result.isValid) {
                    result.getVideoId()?.let { videoId ->
                        Logger.d(TAG, "📺 从短链接解析到视频: $videoId")
                        pendingVideoId = videoId
                        return@launch
                    }
                    result.getDynamicTargetId()?.let { dynamicId ->
                        Logger.d(TAG, "📝 从短链接解析到动态: $dynamicId")
                        pendingNavigationRoute = resolveMainActivityDynamicRoute(dynamicId)
                    }
                }
            } else {
                Logger.w(TAG, "⚠️ 无法解析短链接: $shortUrl")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        com.android.purebilibili.feature.cast.DlnaManager.unbindService(this)
    }
}

/**
 * PiP 模式专用播放器 Composable
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PiPVideoPlayer(miniPlayerManager: MiniPlayerManager) {
    val player = miniPlayerManager.player
    
    if (player != null) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                    useController = false // 隐藏控制器，由系统 PiP 窗口接管
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // 确保视频填充窗口
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                // 每次重组确保 player 是最新的
                if (view.player != player) {
                    view.player = player
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    } else {
        // 如果没有播放器，显示黑屏
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }
}

/**
 *  首次启动欢迎弹窗 - 精美设计版
 */
