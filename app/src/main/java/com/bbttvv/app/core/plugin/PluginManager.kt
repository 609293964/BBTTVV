// 文件路径: core/plugin/PluginManager.kt
package com.bbttvv.app.core.plugin

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.bbttvv.app.core.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

internal data class PluginRegistrationActivation(
    val publishedEnabled: Boolean,
    val persistDisabled: Boolean
)

internal fun resolvePluginRegistrationActivation(
    requestedEnabled: Boolean,
    enableSucceeded: Boolean
): PluginRegistrationActivation {
    val publishedEnabled = requestedEnabled && enableSucceeded
    return PluginRegistrationActivation(
        publishedEnabled = publishedEnabled,
        persistDisabled = requestedEnabled && !enableSucceeded
    )
}

/**
 * 插件管理器。
 *
 * 生命周期状态只有在对应 hook 成功后才提交，避免 UI/执行路径看到与真实生命周期
 * 不一致的 enabled 状态。
 */
object PluginManager {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val registry = PluginRegistry(
        readEnabled = { id -> withContext(Dispatchers.IO) { PluginStore.isEnabled(appContext, id) } },
        writeEnabled = { id, enabled -> withContext(Dispatchers.IO) { PluginStore.setEnabled(appContext, id, enabled) } },
        onFailure = { id, error -> Logger.e(TAG, "Plugin lifecycle failed: $id", error) },
        onEnabledChanged = { plugin, _ ->
            if (plugin is DanmakuPlugin) notifyDanmakuPluginsUpdated()
            if (plugin is FeedPlugin) notifyFeedPluginsUpdated()
        },
    )


    private val _plugins = mutableStateListOf<PluginInfo>()
    val plugins: List<PluginInfo> get() = _plugins.toList()

    private val _pluginsFlow = registry.pluginsFlow
    val pluginsFlow: StateFlow<List<PluginInfo>> = _pluginsFlow

    private val _danmakuPluginUpdateToken = MutableStateFlow(0L)
    val danmakuPluginUpdateToken: StateFlow<Long> = _danmakuPluginUpdateToken.asStateFlow()

    private val _feedPluginUpdateToken = MutableStateFlow(0L)
    val feedPluginUpdateToken: StateFlow<Long> = _feedPluginUpdateToken.asStateFlow()

    private var isInitialized = false
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
        applicationScope.launch {
            registry.pluginsFlow.collect { snapshot ->
                _plugins.clear()
                _plugins.addAll(snapshot)
            }
        }
        Logger.d(TAG, " PluginManager initialized")
    }

    fun getContext(): Context = appContext

    fun register(plugin: Plugin) {
        check(isInitialized) { "Initialize PluginManager before registering plugins" }
        applicationScope.launch { registry.register(plugin) }
    }

    suspend fun setEnabled(pluginId: String, enabled: Boolean) {
        check(isInitialized) { "Initialize PluginManager before toggling plugins" }
        withContext(Dispatchers.Main.immediate) { registry.setEnabled(pluginId, enabled) }
    }

    suspend fun awaitPluginReady(pluginId: String) = registry.awaitPluginReady(pluginId)
    @Suppress("UNCHECKED_CAST")
    fun <T : Plugin> getEnabledPlugins(type: KClass<T>): List<T> {
        return _pluginsFlow.value
            .filter { it.enabled && type.isInstance(it.plugin) }
            .map { it.plugin as T }
    }

    fun getEnabledDanmakuPlugins(): List<DanmakuPlugin> = getEnabledPlugins(DanmakuPlugin::class)

    fun getEnabledFeedPlugins(): List<FeedPlugin> = getEnabledPlugins(FeedPlugin::class)

    fun shouldShowFeedItem(
        item: com.bbttvv.app.data.model.response.VideoItem,
        feedKind: FeedKind = FeedKind.GENERIC
    ): Boolean {
        val feedPlugins = enabledFeedPluginsSnapshot()
        if (feedPlugins.isEmpty()) return true

        return feedPlugins.all { plugin ->
            try {
                plugin.shouldShowItem(item, feedKind)
            } catch (error: Exception) {
                Logger.e(TAG, " Feed plugin failed: ${plugin.name}", error)
                true
            }
        }
    }

    fun filterFeedItems(
        items: List<com.bbttvv.app.data.model.response.VideoItem>,
        feedKind: FeedKind = FeedKind.GENERIC
    ): List<com.bbttvv.app.data.model.response.VideoItem> {
        val feedPlugins = enabledFeedPluginsSnapshot()
        if (feedPlugins.isEmpty()) return items

        return filterFeedBatch(items, feedPlugins,
            shouldShow = { plugin, item -> plugin.shouldShowItem(item, feedKind) },
            onFailure = { plugin, error -> Logger.e(TAG, "Feed plugin failed: ${plugin.id}", error) },
        )
    }
    private fun enabledFeedPluginsSnapshot(): List<FeedPlugin> {
        return _pluginsFlow.value.mapNotNull { info ->
            if (info.enabled) info.plugin as? FeedPlugin else null
        }
    }

    fun getEnabledCount(): Int = _plugins.count { it.enabled }

    fun notifyDanmakuPluginsUpdated() {
        _danmakuPluginUpdateToken.update { it + 1L }
    }

    fun notifyFeedPluginsUpdated() {
        _feedPluginUpdateToken.update { it + 1L }
    }
}

data class PluginInfo(
    val plugin: Plugin,
    val enabled: Boolean
)
