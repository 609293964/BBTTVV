package com.bbttvv.app.feature.plugin

import com.bbttvv.app.core.plugin.Plugin
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.PluginStore
import com.bbttvv.app.core.store.TodayWatchFeedbackStore
import com.bbttvv.app.core.store.TodayWatchProfileStore
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.ui.home.TodayWatchMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TodayWatchPluginTag = "TodayWatchPlugin"

private val todayWatchPluginJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class TodayWatchPluginConfig(
    val currentMode: TodayWatchMode = TodayWatchMode.RELAX,
    val upRankLimit: Int = 5,
    val queueBuildLimit: Int = 40,
    val queuePreviewLimit: Int = 8,
    val historySampleLimit: Int = 120,
    val showUpRank: Boolean = true,
    val showReasonHint: Boolean = true,
    val refreshTriggerToken: Long = 0L
)

class TodayWatchPlugin : Plugin {

    override val id: String = PLUGIN_ID
    override val name: String = "今日推荐单"
    override val description: String = "基于本地历史和偏好画像生成今晚轻松看 / 深度学习看推荐单。"
    override val version: String = "1.0.0"
    override val author: String = "BBTTVV"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _configState = MutableStateFlow(TodayWatchPluginConfig())
    val configState: StateFlow<TodayWatchPluginConfig> = _configState.asStateFlow()

    override suspend fun onEnable() {
        loadConfigFromStore()
    }

    override suspend fun onDisable() {
        Logger.d(TodayWatchPluginTag, "Today watch plugin disabled")
    }

    fun setCurrentMode(mode: TodayWatchMode) {
        persistConfig(_configState.value.copy(currentMode = mode))
    }

    fun setUpRankLimit(limit: Int) {
        persistConfig(_configState.value.copy(upRankLimit = limit))
    }

    fun setQueueBuildLimit(limit: Int) {
        persistConfig(_configState.value.copy(queueBuildLimit = limit))
    }

    fun setQueuePreviewLimit(limit: Int) {
        persistConfig(_configState.value.copy(queuePreviewLimit = limit))
    }

    fun setHistorySampleLimit(limit: Int) {
        persistConfig(_configState.value.copy(historySampleLimit = limit))
    }

    fun setShowUpRank(enabled: Boolean) {
        persistConfig(_configState.value.copy(showUpRank = enabled))
    }

    fun setShowReasonHint(enabled: Boolean) {
        persistConfig(_configState.value.copy(showReasonHint = enabled))
    }

    fun clearPersonalizationData() {
        scope.launch {
            runCatching {
                val context = PluginManager.getContext()
                TodayWatchProfileStore.clear(context)
                TodayWatchFeedbackStore.clear(context)
                persistConfig(
                    _configState.value.copy(
                        refreshTriggerToken = System.currentTimeMillis()
                    )
                )
            }.onFailure { error ->
                Logger.e(TodayWatchPluginTag, "Failed to clear today watch personalization", error)
            }
        }
    }

    private suspend fun loadConfigFromStore() {
        val context = PluginManager.getContext()
        val raw = PluginStore.getConfigJson(context, id)
        val config = raw?.let {
            runCatching {
                todayWatchPluginJson.decodeFromString<TodayWatchPluginConfig>(it)
            }.onFailure { error ->
                Logger.e(TodayWatchPluginTag, "Failed to decode today watch config", error)
            }.getOrNull()
        } ?: TodayWatchPluginConfig()
        _configState.value = normalizeConfig(config)
    }

    private fun persistConfig(config: TodayWatchPluginConfig) {
        val normalized = normalizeConfig(config)
        _configState.value = normalized
        scope.launch {
            runCatching {
                PluginStore.setConfigJson(
                    context = PluginManager.getContext(),
                    pluginId = id,
                    configJson = todayWatchPluginJson.encodeToString(normalized)
                )
            }.onFailure { error ->
                Logger.e(TodayWatchPluginTag, "Failed to persist today watch config", error)
            }
        }
    }

    private fun normalizeConfig(config: TodayWatchPluginConfig): TodayWatchPluginConfig {
        val normalizedQueueBuildLimit = config.queueBuildLimit.coerceIn(6, 40)
        val normalizedPreviewLimit = config.queuePreviewLimit
            .coerceIn(3, 12)
            .coerceAtMost(normalizedQueueBuildLimit)
        return config.copy(
            upRankLimit = config.upRankLimit.coerceIn(1, 12),
            queueBuildLimit = normalizedQueueBuildLimit,
            queuePreviewLimit = normalizedPreviewLimit,
            historySampleLimit = config.historySampleLimit.coerceIn(20, 120)
        )
    }

    companion object {
        const val PLUGIN_ID: String = "today_watch"

        fun getInstance(): TodayWatchPlugin? {
            return PluginManager.plugins
                .firstOrNull { it.plugin.id == PLUGIN_ID }
                ?.plugin as? TodayWatchPlugin
        }
    }
}
