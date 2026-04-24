package com.bbttvv.app.app

import android.app.Application
import android.content.ComponentCallbacks2
import com.bbttvv.app.app.startup.AppStartupOrchestrator
import com.bbttvv.app.app.startup.AppStartupTask
import com.bbttvv.app.app.startup.HomeBootstrapper
import com.bbttvv.app.core.performance.AppPerformanceTracker
import com.bbttvv.app.core.lifecycle.BackgroundManager
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.PluginStore
import com.bbttvv.app.core.plugin.json.JsonPluginManager
import com.bbttvv.app.core.store.PlayerSettingsCache
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.CrashReporter
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.feature.plugin.AD_FILTER_PLUGIN_ID
import com.bbttvv.app.feature.plugin.AdFilterPlugin
import com.bbttvv.app.feature.plugin.DanmakuEnhancePlugin
import com.bbttvv.app.feature.plugin.SPONSOR_BLOCK_PLUGIN_ID
import com.bbttvv.app.feature.plugin.SponsorBlockPlugin
import com.bbttvv.app.feature.plugin.TodayWatchPlugin
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BbtvApplication : Application(), ImageLoaderFactory, ComponentCallbacks2 {
    private val startupOrchestrator by lazy { AppStartupOrchestrator() }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var imageLoaderRef: ImageLoader? = null
    @Volatile
    private var deferredStartupScheduled = false

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.12)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(96L * 1024 * 1024)
                    .build()
            }
            .okHttpClient(NetworkModule.okHttpClient)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowRgb565(true)
            .crossfade(false)
            .build()
            .also { imageLoaderRef = it }
    }

    override fun onCreate() {
        super.onCreate()
        AppPerformanceTracker.markApplicationCreated()
        Logger.init(this)
        startupOrchestrator.runImmediate(::runStartupTask)
    }

    fun onFirstFrameRendered() {
        if (deferredStartupScheduled) return
        deferredStartupScheduled = true
        AppPerformanceTracker.markMilestoneOnce("first_frame_rendered")
        startupOrchestrator.scheduleDeferred(::runStartupTask)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        imageLoaderRef?.memoryCache?.clear()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            imageLoaderRef?.memoryCache?.clear()
        }
    }

    private fun runStartupTask(task: AppStartupTask) {
        AppPerformanceTracker.measureStartupTask(task) {
            when (task.id) {
                "network_module_init" -> NetworkModule.init(this)
                "token_manager_init" -> TokenManager.init(this)
                "video_repository_init" -> com.bbttvv.app.data.repository.PlaybackRepository.init(this)
                "background_manager_init" -> BackgroundManager.init()
                "player_settings_cache_init" -> PlayerSettingsCache.init(this)
                "home_feed_preload" -> HomeBootstrapper.preload()
                "crash_reporter_init" -> {
                    CrashReporter.init(this)
                    CrashReporter.installGlobalExceptionHandler()
                }
                "plugin_manager_init" -> {
                    PluginManager.initialize(this)
                    JsonPluginManager.initialize(this)
                    PluginManager.register(SponsorBlockPlugin())
                    PluginManager.register(AdFilterPlugin())
                    PluginManager.register(DanmakuEnhancePlugin())
                    PluginManager.register(TodayWatchPlugin())
                    syncBuiltInPluginState()
                }
            }
        }
    }

    private fun syncBuiltInPluginState() {
        appScope.launch {
            val sponsorStored = PluginStore.getEnabledOrNull(this@BbtvApplication, SPONSOR_BLOCK_PLUGIN_ID)
            if (sponsorStored == null) {
                val migratedEnabled = SettingsManager.consumeLegacySponsorBlockEnabled(
                    context = this@BbtvApplication,
                    defaultValue = true
                )
                PluginManager.setEnabled(SPONSOR_BLOCK_PLUGIN_ID, migratedEnabled)
            }

            val adFilterStored = PluginStore.getEnabledOrNull(this@BbtvApplication, AD_FILTER_PLUGIN_ID)
            if (adFilterStored == null) {
                PluginManager.setEnabled(AD_FILTER_PLUGIN_ID, true)
            }
        }
    }
}
