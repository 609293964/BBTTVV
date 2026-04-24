// 文件路径: core/plugin/PluginManager.kt
package com.bbttvv.app.core.plugin

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.bbttvv.app.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

private const val TAG = "PluginManager"

internal fun consumePendingPluginEnabledState(
    pluginId: String,
    storedEnabled: Boolean,
    pendingEnabledOverrides: MutableMap<String, Boolean>,
    lock: Any? = null
): Boolean {
    return if (lock != null) {
        synchronized(lock) {
            pendingEnabledOverrides.remove(pluginId) ?: storedEnabled
        }
    } else {
        pendingEnabledOverrides.remove(pluginId) ?: storedEnabled
    }
}

/**
 *  插件管理器
 * 
 * 负责管理所有插件的注册、启用/禁用、生命周期调用等。
 * 使用单例模式，在 Application 启动时初始化。
 */
object PluginManager {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pendingEnabledOverridesLock = Any()
    private val pendingEnabledOverrides = mutableMapOf<String, Boolean>()
    
    /** 所有已注册插件 */
    private val _plugins = mutableStateListOf<PluginInfo>()
    val plugins: List<PluginInfo> get() = _plugins.toList()
    
    /** 插件列表状态流 (用于 Compose 监听) */
    private val _pluginsFlow = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginsFlow: StateFlow<List<PluginInfo>> = _pluginsFlow.asStateFlow()

    /** 弹幕插件更新信号（用于播放中热刷新当前弹幕） */
    private val _danmakuPluginUpdateToken = MutableStateFlow(0L)
    val danmakuPluginUpdateToken: StateFlow<Long> = _danmakuPluginUpdateToken.asStateFlow()

    /** 信息流插件更新信号（用于首页/热门重算过滤结果） */
    private val _feedPluginUpdateToken = MutableStateFlow(0L)
    val feedPluginUpdateToken: StateFlow<Long> = _feedPluginUpdateToken.asStateFlow()
    
    private var isInitialized = false
    private lateinit var appContext: Context
    
    /**
     * 初始化插件管理器
     * 应在 Application.onCreate() 中调用
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
        Logger.d(TAG, " PluginManager initialized")
    }
    
    /** 获取Application Context供插件使用 */
    fun getContext(): Context = appContext
    
    /**
     * 注册插件
     * 内置插件在 Application 中注册
     */
    fun register(plugin: Plugin) {
        if (_plugins.any { it.plugin.id == plugin.id }) {
            Logger.w(TAG, " Plugin already registered: ${plugin.id}")
            return
        }
        
        scope.launch {
            val storedEnabled = withContext(Dispatchers.IO) {
                PluginStore.isEnabled(appContext, plugin.id)
            }
            val enabled = consumePendingPluginEnabledState(
                pluginId = plugin.id,
                storedEnabled = storedEnabled,
                pendingEnabledOverrides = pendingEnabledOverrides,
                lock = pendingEnabledOverridesLock
            )
            val info = PluginInfo(plugin, enabled)
            _plugins.add(info)
            _pluginsFlow.value = _plugins.toList()
            
            if (enabled) {
                try {
                    plugin.onEnable()
                    Logger.d(TAG, " Plugin enabled on start: ${plugin.name}")
                } catch (e: Exception) {
                    Logger.e(TAG, " Failed to enable plugin: ${plugin.name}", e)
                }
            }
            
            Logger.d(TAG, " Plugin registered: ${plugin.name} (enabled=$enabled)")
        }
    }
    
    /**
     * 启用/禁用插件
     */
    suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        val shouldPersist = withContext(Dispatchers.Main.immediate) {
            val index = _plugins.indexOfFirst { it.plugin.id == pluginId }
            if (index == -1) {
                synchronized(pendingEnabledOverridesLock) {
                    pendingEnabledOverrides[pluginId] = enabled
                }
                Logger.d(TAG, " Deferring plugin enabled change until registration: $pluginId -> $enabled")
                return@withContext true
            }

            val info = _plugins[index]
            val plugin = info.plugin
            if (info.enabled == enabled) return@withContext false

            try {
                if (enabled && !info.enabled) {
                    plugin.onEnable()
                    Logger.d(TAG, " Plugin enabled: ${plugin.name}")
                } else if (!enabled && info.enabled) {
                    plugin.onDisable()
                    Logger.d(TAG, "🔴 Plugin disabled: ${plugin.name}")
                }

                // 更新状态
                _plugins[index] = info.copy(enabled = enabled)
                _pluginsFlow.value = _plugins.toList()

                if (plugin is DanmakuPlugin) {
                    notifyDanmakuPluginsUpdated()
                }
                if (plugin is FeedPlugin) {
                    notifyFeedPluginsUpdated()
                }

                true
            } catch (e: Exception) {
                Logger.e(TAG, " Failed to toggle plugin: ${plugin.name}", e)
                false
            }
        }

        if (shouldPersist) {
            withContext(Dispatchers.IO) {
                PluginStore.setEnabled(appContext, pluginId, enabled)
            }
        }
    }
    
    /**
     * 获取指定类型的所有已启用插件
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Plugin> getEnabledPlugins(type: KClass<T>): List<T> {
        return _plugins.toList()
            .filter { it.enabled && type.isInstance(it.plugin) }
            .map { it.plugin as T }
    }
    
    /**
     * 获取所有 PlayerPlugin
     */
    fun getEnabledPlayerPlugins(): List<PlayerPlugin> = getEnabledPlugins(PlayerPlugin::class)
    
    /**
     * 获取所有 DanmakuPlugin
     */
    fun getEnabledDanmakuPlugins(): List<DanmakuPlugin> = getEnabledPlugins(DanmakuPlugin::class)
    
    /**
     * 获取所有 FeedPlugin
     */
    fun getEnabledFeedPlugins(): List<FeedPlugin> = getEnabledPlugins(FeedPlugin::class)
    
    /**
     * 使用所有启用的 FeedPlugin 判断单个视频是否可见
     */
    fun shouldShowFeedItem(item: com.bbttvv.app.data.model.response.VideoItem): Boolean {
        val feedPlugins = getEnabledFeedPlugins()
        if (feedPlugins.isEmpty()) return true

        return feedPlugins.all { plugin ->
            try {
                plugin.shouldShowItem(item)
            } catch (e: Exception) {
                Logger.e(TAG, " Feed plugin failed: ${plugin.name}", e)
                true
            }
        }
    }

    /**
     *  使用所有启用的 FeedPlugin 过滤视频列表
     * 用于首页推荐和搜索结果
     */
    fun filterFeedItems(items: List<com.bbttvv.app.data.model.response.VideoItem>): List<com.bbttvv.app.data.model.response.VideoItem> {
        return items.filter(::shouldShowFeedItem)
    }
    
    /**
     * 获取已启用插件数量
     */
    fun getEnabledCount(): Int = _plugins.count { it.enabled }

    fun notifyDanmakuPluginsUpdated() {
        _danmakuPluginUpdateToken.update { it + 1L }
    }

    fun notifyFeedPluginsUpdated() {
        _feedPluginUpdateToken.update { it + 1L }
    }
}

/**
 * 插件信息包装类
 */
data class PluginInfo(
    val plugin: Plugin,
    val enabled: Boolean
)

