// 文件路径: core/plugin/json/JsonPluginManager.kt
package com.bbttvv.app.core.plugin.json

import android.content.Context
import android.net.Uri
import com.bbttvv.app.core.plugin.DanmakuItem
import com.bbttvv.app.core.plugin.DanmakuStyle
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val TAG = "JsonPluginManager"
private const val STATS_PREFS = "json_plugin_stats"
private const val ENABLED_PREFS = "json_plugins"
private const val ENABLED_PREFIX = "enabled_"
private val PLUGIN_ID_REGEX = Regex("^[a-zA-Z0-9_.-]{1,64}$")

/**
 *  JSON 规则插件管理器
 * 
 * 管理通过 URL 导入的 JSON 规则插件
 */
object JsonPluginManager {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
    private lateinit var appContext: Context
    
    /** 已加载的插件列表 */
    private val _plugins = MutableStateFlow<List<LoadedJsonPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedJsonPlugin>> = _plugins.asStateFlow()
    
    /**  过滤统计 (插件ID -> 过滤数量) */
    private val _filterStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val filterStats: StateFlow<Map<String, Int>> = _filterStats.asStateFlow()
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null
    
    private var isInitialized = false
    
    /**
     * 初始化
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
        
        // 加载已保存的插件
        loadSavedPlugins()
        //  加载持久化统计
        loadFilterStats()
        Logger.d(TAG, " JsonPluginManager initialized")
    }
    
    /**
     * 从 URL 导入插件
     */
    suspend fun importFromUrl(url: String): Result<JsonRulePlugin> {
        return withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = url.trim()
                val plugin = fetchPluginFromUrl(normalizedUrl).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

                // 保存到本地
                savePlugin(plugin)

                // 添加到列表
                var enabled = true
                _plugins.update { current ->
                    enabled = current.find { it.plugin.id == plugin.id }?.enabled ?: true
                    current.filter { it.plugin.id != plugin.id } +
                        LoadedJsonPlugin(plugin, enabled = enabled, sourceUrl = normalizedUrl)
                }
                persistEnabledState(plugin.id, enabled)
                if (plugin.type == "feed") {
                    PluginManager.notifyFeedPluginsUpdated()
                }
                if (plugin.type == "danmaku") {
                    PluginManager.notifyDanmakuPluginsUpdated()
                }

                Logger.d(TAG, " 插件导入成功: ${plugin.name}")
                Result.success(plugin)
            } catch (e: java.net.SocketTimeoutException) {
                Logger.e(TAG, " 连接超时", e)
                Result.failure(Exception("连接超时，请检查网络或 URL 是否正确"))
            } catch (e: java.net.UnknownHostException) {
                Logger.e(TAG, " 无法解析主机", e)
                Result.failure(Exception("无法连接服务器，请检查 URL"))
            } catch (e: java.io.IOException) {
                Logger.e(TAG, " 网络错误", e)
                Result.failure(Exception("网络错误: ${e.message}"))
            } catch (e: Exception) {
                Logger.e(TAG, " 导入失败", e)
                Result.failure(Exception("导入失败: ${e.message?.take(100)}"))
            }
        }
    }

    /**
     * 从 URL 预览插件（不落盘）
     */
    suspend fun previewFromUrl(url: String): Result<JsonRulePlugin> {
        return withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = url.trim()
                fetchPluginFromUrl(normalizedUrl)
            } catch (e: java.net.SocketTimeoutException) {
                Logger.e(TAG, " 连接超时", e)
                Result.failure(Exception("连接超时，请检查网络或 URL 是否正确"))
            } catch (e: java.net.UnknownHostException) {
                Logger.e(TAG, " 无法解析主机", e)
                Result.failure(Exception("无法连接服务器，请检查 URL"))
            } catch (e: java.io.IOException) {
                Logger.e(TAG, " 网络错误", e)
                Result.failure(Exception("网络错误: ${e.message}"))
            } catch (e: Exception) {
                Logger.e(TAG, " 预览失败", e)
                Result.failure(Exception("预览失败: ${e.message?.take(100)}"))
            }
        }
    }
    
    /**
     * 删除插件
     */
    fun removePlugin(pluginId: String) {
        val file = File(getPluginDir(), "$pluginId.json")
        if (file.exists()) file.delete()

        var removedType: String? = null
        _plugins.update { current ->
            removedType = current.find { it.plugin.id == pluginId }?.plugin?.type
            current.filter { it.plugin.id != pluginId }
        }
        _filterStats.update { it - pluginId }
        clearEnabledState(pluginId)
        schedulePersistStats()
        if (removedType == "feed") {
            PluginManager.notifyFeedPluginsUpdated()
        }
        if (removedType == "danmaku") {
            PluginManager.notifyDanmakuPluginsUpdated()
        }
        Logger.d(TAG, " 删除插件: $pluginId")
    }
    
    /**
     * 启用/禁用插件
     */
    fun setEnabled(pluginId: String, enabled: Boolean) {
        var targetType: String? = null
        var changed = false
        _plugins.update { current ->
            targetType = null
            changed = false
            current.map { loaded ->
                if (loaded.plugin.id == pluginId) {
                    targetType = loaded.plugin.type
                    if (loaded.enabled != enabled) {
                        changed = true
                        loaded.copy(enabled = enabled)
                    } else {
                        loaded
                    }
                } else {
                    loaded
                }
            }
        }
        if (targetType == null) {
            Logger.w(TAG, " 插件不存在: $pluginId")
            return
        }

        if (changed) {
            persistEnabledState(pluginId, enabled)
            if (targetType == "feed") {
                PluginManager.notifyFeedPluginsUpdated()
            }
            if (targetType == "danmaku") {
                PluginManager.notifyDanmakuPluginsUpdated()
            }
        }
    }
    
    // ============ 过滤方法 ============
    
    /**  最近一次过滤掉的视频数量（用于 UI 提示） */
    private val _lastFilteredCount = MutableStateFlow(0)
    val lastFilteredCount: StateFlow<Int> = _lastFilteredCount.asStateFlow()
    
    /**
     * 过滤单个视频（增量分页场景）
     */
    fun shouldShowVideo(video: VideoItem, recordStats: Boolean = true): Boolean {
        val feedPlugins = _plugins.value.filter { it.enabled && it.plugin.type == "feed" }
        if (feedPlugins.isEmpty()) {
            if (recordStats) {
                _lastFilteredCount.value = 0
            }
            return true
        }

        val hiddenBy = findFirstMatchingFeedPlugin(video, feedPlugins)
        if (hiddenBy == null) {
            if (recordStats) {
                _lastFilteredCount.value = 0
            }
            return true
        }

        if (recordStats) {
            mergeStatsDelta(mapOf(hiddenBy.plugin.id to 1))
            _lastFilteredCount.value = 1
        }
        return false
    }

    /**
     * 过滤视频列表（带统计和计数）
     * @return 过滤后的视频列表
     */
    fun filterVideos(videos: List<VideoItem>, recordStats: Boolean = true): List<VideoItem> {
        val feedPlugins = _plugins.value.filter { it.enabled && it.plugin.type == "feed" }
        if (feedPlugins.isEmpty()) {
            if (recordStats) {
                _lastFilteredCount.value = 0
            }
            return videos
        }

        var filteredCount = 0
        val statsDelta = mutableMapOf<String, Int>()
        val result = ArrayList<VideoItem>(videos.size)

        videos.forEach { video ->
            val hiddenBy = findFirstMatchingFeedPlugin(video, feedPlugins)

            if (hiddenBy == null) {
                result.add(video)
            } else {
                filteredCount++
                val pluginId = hiddenBy.plugin.id
                statsDelta[pluginId] = statsDelta.getOrDefault(pluginId, 0) + 1
            }
        }

        if (recordStats && statsDelta.isNotEmpty()) {
            mergeStatsDelta(statsDelta)
        }

        if (recordStats) {
            //  更新最近过滤数量
            _lastFilteredCount.value = filteredCount
        }
        if (recordStats && filteredCount > 0) {
            Logger.d(TAG, " 本次过滤了 $filteredCount 个视频")
        }

        return result
    }

    private fun findFirstMatchingFeedPlugin(
        video: VideoItem,
        feedPlugins: List<LoadedJsonPlugin>
    ): LoadedJsonPlugin? {
        for (loaded in feedPlugins) {
            if (!RuleEngine.shouldShowVideo(video, loaded.plugin.rules)) {
                return loaded
            }
        }
        return null
    }
    
    /**
     *  更新插件规则
     */
    fun updatePlugin(plugin: JsonRulePlugin) {
        validatePlugin(plugin)?.let { error ->
            Logger.w(TAG, " 更新插件失败: $error")
            return
        }
        // 保存到本地
        savePlugin(plugin)
        
        // 更新列表（保留 enabled 状态）
        _plugins.update { current ->
            current.map { loaded ->
                if (loaded.plugin.id == plugin.id) {
                    loaded.copy(plugin = plugin)
                } else {
                    loaded
                }
            }
        }
        
        // 重置该插件的统计
        _filterStats.update { it - plugin.id }
        schedulePersistStats()
        if (plugin.type == "feed") {
            PluginManager.notifyFeedPluginsUpdated()
        }
        if (plugin.type == "danmaku") {
            PluginManager.notifyDanmakuPluginsUpdated()
        }
        
        Logger.d(TAG, " 插件已更新: ${plugin.name}")
    }
    
    /**
     *  重置统计（同时清除持久化数据）
     */
    fun resetStats(pluginId: String? = null) {
        if (pluginId != null) {
            _filterStats.update { it - pluginId }
        } else {
            _filterStats.update { emptyMap() }
        }
        schedulePersistStats()
        Logger.d(TAG, " 统计已重置: ${pluginId ?: "全部"}")
    }
    
    /**
     *  测试插件规则（用于验证插件是否生效）
     * 
     * @param pluginId 要测试的插件 ID
     * @param sampleVideos 测试用的视频列表（来自首页）
     * @return Pair(原始数量, 过滤后数量)
     */
    fun testPluginRules(pluginId: String, sampleVideos: List<VideoItem>): Pair<Int, Int> {
        val loaded = _plugins.value.find { it.plugin.id == pluginId }
            ?: return Pair(sampleVideos.size, sampleVideos.size)
        
        val filtered = sampleVideos.filter { video ->
            RuleEngine.shouldShowVideo(video, loaded.plugin.rules)
        }
        
        return Pair(sampleVideos.size, filtered.size)
    }
    
    /**
     *  获取被测试过滤的视频列表（用于展示哪些视频会被过滤）
     */
    fun getFilteredVideosByPlugin(pluginId: String, sampleVideos: List<VideoItem>): List<VideoItem> {
        val loaded = _plugins.value.find { it.plugin.id == pluginId }
            ?: return emptyList()
        
        return sampleVideos.filter { video ->
            !RuleEngine.shouldShowVideo(video, loaded.plugin.rules)
        }
    }
    
    /**
     * 过滤单个弹幕
     */
    fun shouldShowDanmaku(danmaku: DanmakuItem): Boolean {
        val danmakuPlugins = _plugins.value.filter { it.enabled && it.plugin.type == "danmaku" }
        return danmakuPlugins.all { loaded ->
            RuleEngine.shouldShowDanmaku(danmaku, loaded.plugin.rules)
        }
    }
    
    /**
     * 获取弹幕高亮样式
     */
    fun getDanmakuStyle(danmaku: DanmakuItem): DanmakuStyle? {
        val danmakuPlugins = _plugins.value.filter { it.enabled && it.plugin.type == "danmaku" }
        for (loaded in danmakuPlugins) {
            val style = RuleEngine.getDanmakuHighlightStyle(danmaku, loaded.plugin.rules)
            if (style != null) return style
        }
        return null
    }
    
    // ============ 私有方法 ============

    private fun fetchPluginFromUrl(normalizedUrl: String): Result<JsonRulePlugin> {
        validateImportUrl(normalizedUrl).onFailure { return Result.failure(it) }
        Logger.d(TAG, " 下载插件: $normalizedUrl")

        val request = Request.Builder()
            .url(normalizedUrl)
            .build()

        val content = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return Result.failure(
                    Exception("下载失败: HTTP ${response.code} ${response.message}")
                )
            }
            response.body?.string()
                ?: return Result.failure(Exception("服务器返回空内容"))
        }

        Logger.d(TAG, "📄 下载内容长度: ${content.length}")

        val plugin = try {
            json.decodeFromString<JsonRulePlugin>(content)
        } catch (e: Exception) {
            Logger.e(TAG, " JSON 解析失败", e)
            return Result.failure(
                Exception("JSON 解析失败: ${e.message?.take(100)}")
            )
        }

        validatePlugin(plugin)?.let { error ->
            return Result.failure(Exception(error))
        }

        return Result.success(plugin)
    }

    private fun validateImportUrl(url: String): Result<Unit> {
        if (url.isBlank()) return Result.failure(Exception("请输入插件链接"))
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme !in listOf("http", "https")) {
            return Result.failure(Exception("仅支持 http/https 链接"))
        }
        if (uri.host.isNullOrBlank()) {
            return Result.failure(Exception("链接格式不正确"))
        }
        return Result.success(Unit)
    }

    private fun validatePlugin(plugin: JsonRulePlugin): String? {
        if (plugin.id.isBlank()) return "插件 ID 不能为空"
        if (!PLUGIN_ID_REGEX.matches(plugin.id)) {
            return "插件 ID 格式无效，仅支持字母数字/._-"
        }
        if (plugin.name.isBlank()) return "插件名称不能为空"
        if (plugin.type !in setOf("feed", "danmaku")) {
            return "不支持的插件类型: ${plugin.type}"
        }
        if (plugin.rules.isEmpty()) return "规则不能为空"
        if (plugin.rules.any { it.toCondition() == null }) {
            return "存在无效规则（缺少 condition 或 field/op/value）"
        }
        return null
    }

    private fun persistEnabledState(pluginId: String, enabled: Boolean) {
        val prefs = appContext.getSharedPreferences(ENABLED_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("$ENABLED_PREFIX$pluginId", enabled).apply()
    }

    private fun clearEnabledState(pluginId: String) {
        val prefs = appContext.getSharedPreferences(ENABLED_PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove("$ENABLED_PREFIX$pluginId").apply()
    }
    
    private fun getPluginDir(): File {
        val dir = File(appContext.filesDir, "json_plugins")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    private fun savePlugin(plugin: JsonRulePlugin) {
        val file = File(getPluginDir(), "${plugin.id}.json")
        file.writeText(json.encodeToString(JsonRulePlugin.serializer(), plugin))
    }
    
    private fun loadSavedPlugins() {
        val dir = getPluginDir()
        if (!dir.exists()) return
        
        val prefs = appContext.getSharedPreferences(ENABLED_PREFS, Context.MODE_PRIVATE)
        
        val loaded = dir.listFiles()
            ?.sortedBy { it.name }
            ?.mapNotNull { file ->
            try {
                if (file.extension != "json") return@mapNotNull null
                val plugin = json.decodeFromString<JsonRulePlugin>(file.readText())
                validatePlugin(plugin)?.let {
                    Logger.w(TAG, " 插件文件无效，已忽略: ${file.name} ($it)")
                    return@mapNotNull null
                }
                val enabled = prefs.getBoolean("$ENABLED_PREFIX${plugin.id}", true)
                LoadedJsonPlugin(plugin, enabled, sourceUrl = null)
            } catch (e: Exception) {
                Logger.w(TAG, " 加载插件失败: ${file.name}")
                null
            }
        } ?: emptyList()
        
        _plugins.value = loaded
        Logger.d(TAG, " 加载了 ${loaded.size} 个 JSON 插件")
    }
    
    /**
     *  加载持久化过滤统计
     */
    private fun loadFilterStats() {
        val prefs = appContext.getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
        val statsMap = mutableMapOf<String, Int>()
        
        prefs.all.forEach { (key, value) ->
            if (value is Int) {
                statsMap[key] = value
            }
        }
        
        _filterStats.value = statsMap
        Logger.d(TAG, " 加载了 ${statsMap.size} 个插件的过滤统计")
    }

    private fun mergeStatsDelta(statsDelta: Map<String, Int>) {
        if (statsDelta.isEmpty()) return
        _filterStats.update { current ->
            val merged = current.toMutableMap()
            statsDelta.forEach { (pluginId, delta) ->
                merged[pluginId] = merged.getOrDefault(pluginId, 0) + delta
            }
            merged
        }
        schedulePersistStats()
    }

    private fun schedulePersistStats() {
        if (!isInitialized) return
        persistJob?.cancel()
        persistJob = statsScope.launch {
            delay(3000)
            saveFilterStats()
        }
    }
    
    /**
     *  保存过滤统计到持久化存储
     */
    private fun saveFilterStats() {
        val prefs = appContext.getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        // 清空旧数据
        editor.clear()
        
        // 写入新数据
        _filterStats.value.forEach { (pluginId, count) ->
            editor.putInt(pluginId, count)
        }
        
        editor.apply()
    }
}

/**
 * 已加载的 JSON 插件
 */
data class LoadedJsonPlugin(
    val plugin: JsonRulePlugin,
    val enabled: Boolean,
    val sourceUrl: String?
)

