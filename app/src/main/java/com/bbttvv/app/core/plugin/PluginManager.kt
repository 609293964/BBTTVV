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
    private val pendingEnabledOverridesLock = Any()
    private val pendingEnabledOverrides = mutableMapOf<String, Boolean>()

    private val _plugins = mutableStateListOf<PluginInfo>()
    val plugins: List<PluginInfo> get() = _plugins.toList()

    private val _pluginsFlow = MutableStateFlow<List<PluginInfo>>(emptyList())
    val pluginsFlow: StateFlow<List<PluginInfo>> = _pluginsFlow.asStateFlow()

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
        Logger.d(TAG, " PluginManager initialized")
    }

    fun getContext(): Context = appContext

    fun register(plugin: Plugin) {
        applicationScope.launch {
            if (_plugins.any { it.plugin.id == plugin.id }) {
                Logger.w(TAG, " Plugin already registered: ${plugin.id}")
                return@launch
            }

            val storedEnabled = withContext(Dispatchers.IO) {
                PluginStore.isEnabled(appContext, plugin.id)
            }
            if (_plugins.any { it.plugin.id == plugin.id }) {
                Logger.w(TAG, " Plugin already registered: ${plugin.id}")
                return@launch
            }

            val requestedEnabled = consumePendingPluginEnabledState(
                pluginId = plugin.id,
                storedEnabled = storedEnabled,
                pendingEnabledOverrides = pendingEnabledOverrides,
                lock = pendingEnabledOverridesLock
            )

            val enableSucceeded = if (!requestedEnabled) {
                true
            } else {
                try {
                    plugin.onEnable()
                    Logger.d(TAG, " Plugin enabled on start: ${plugin.name}")
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Logger.e(TAG, " Failed to enable plugin: ${plugin.name}", error)
                    false
                }
            }

            val activation = resolvePluginRegistrationActivation(
                requestedEnabled = requestedEnabled,
                enableSucceeded = enableSucceeded
            )
            val info = PluginInfo(plugin, activation.publishedEnabled)
            _plugins.add(info)
            _pluginsFlow.value = _plugins.toList()

            if (activation.persistDisabled) {
                try {
                    withContext(Dispatchers.IO) {
                        PluginStore.setEnabled(appContext, plugin.id, false)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Logger.e(
                        TAG,
                        " Failed to persist disabled state after startup activation failure: ${plugin.name}",
                        error
                    )
                }
            }

            Logger.d(
                TAG,
                " Plugin registered: ${plugin.name} (enabled=${activation.publishedEnabled})"
            )
        }
    }

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
                if (enabled) {
                    plugin.onEnable()
                    Logger.d(TAG, " Plugin enabled: ${plugin.name}")
                } else {
                    plugin.onDisable()
                    Logger.d(TAG, " Plugin disabled: ${plugin.name}")
                }

                _plugins[index] = info.copy(enabled = enabled)
                _pluginsFlow.value = _plugins.toList()

                if (plugin is DanmakuPlugin) notifyDanmakuPluginsUpdated()
                if (plugin is FeedPlugin) notifyFeedPluginsUpdated()
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.e(TAG, " Failed to toggle plugin: ${plugin.name}", error)
                false
            }
        }

        if (shouldPersist) {
            withContext(Dispatchers.IO) {
                PluginStore.setEnabled(appContext, pluginId, enabled)
            }
        }
    }

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

        val visibleItems = ArrayList<com.bbttvv.app.data.model.response.VideoItem>(items.size)
        for (item in items) {
            var shouldShow = true
            for (plugin in feedPlugins) {
                shouldShow = try {
                    plugin.shouldShowItem(item, feedKind)
                } catch (error: Exception) {
                    Logger.e(TAG, " Feed plugin failed: ${plugin.name}", error)
                    true
                }
                if (!shouldShow) break
            }
            if (shouldShow) visibleItems.add(item)
        }
        return visibleItems
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
