// 文件路径: core/plugin/json/JsonPluginManager.kt
package com.bbttvv.app.core.plugin.json

import android.content.Context
import android.net.Uri
import com.bbttvv.app.core.plugin.DanmakuItem
import com.bbttvv.app.core.plugin.DanmakuStyle
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoItem
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

private const val TAG = "JsonPluginManager"
private const val STATS_PREFS = "json_plugin_stats"
private const val ENABLED_PREFS = "json_plugins"
private const val ENABLED_PREFIX = "enabled_"
private val PLUGIN_ID_REGEX = Regex("^[a-zA-Z0-9_.-]{1,64}$")

/**
 * JSON 规则插件管理器。
 *
 * 远程 JSON 被视为不可信输入：下载、解析、规则执行和持久化都有独立边界，单个
 * 插件失败时采用 fail-open，不能中断宿主 feed/danmaku 流程。
 */
object JsonPluginManager {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                if (!chain.request().url.isHttps) {
                    throw IOException("插件下载只允许 HTTPS")
                }
                chain.proceed(chain.request())
            }
            .build()
    }
    private lateinit var appContext: Context

    private val _plugins = MutableStateFlow<List<LoadedJsonPlugin>>(emptyList())
    val plugins: StateFlow<List<LoadedJsonPlugin>> = _plugins.asStateFlow()

    private val _filterStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val filterStats: StateFlow<Map<String, Int>> = _filterStats.asStateFlow()
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null

    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true
        loadSavedPlugins()
        loadFilterStats()
        Logger.d(TAG, " JsonPluginManager initialized")
    }

    suspend fun importFromUrl(url: String): Result<JsonRulePlugin> {
        return withContext(Dispatchers.IO) {
            try {
                val normalizedUrl = url.trim()
                val plugin = fetchPluginFromUrl(normalizedUrl).getOrElse { error ->
                    return@withContext Result.failure(error)
                }

                // Disk is the commit point. Runtime state is changed only after durable save.
                savePlugin(plugin)

                var enabled = true
                _plugins.update { current ->
                    enabled = current.find { it.plugin.id == plugin.id }?.enabled ?: true
                    current.filter { it.plugin.id != plugin.id } +
                        LoadedJsonPlugin(plugin, enabled = enabled, sourceUrl = normalizedUrl)
                }
                persistEnabledState(plugin.id, enabled)
                notifyPluginTypeChanged(plugin.type)

                Logger.d(TAG, " 插件导入成功: ${plugin.name}")
                Result.success(plugin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                Logger.e(TAG, " 连接超时", e)
                Result.failure(Exception("连接超时，请检查网络或 URL 是否正确"))
            } catch (e: java.net.UnknownHostException) {
                Logger.e(TAG, " 无法解析主机", e)
                Result.failure(Exception("无法连接服务器，请检查 URL"))
            } catch (e: IOException) {
                Logger.e(TAG, " 网络或存储错误", e)
                Result.failure(Exception(e.message ?: "网络或存储错误"))
            } catch (e: Exception) {
                Logger.e(TAG, " 导入失败", e)
                Result.failure(Exception("导入失败: ${e.message?.take(100)}"))
            }
        }
    }

    suspend fun previewFromUrl(url: String): Result<JsonRulePlugin> {
        return withContext(Dispatchers.IO) {
            try {
                fetchPluginFromUrl(url.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                Logger.e(TAG, " 连接超时", e)
                Result.failure(Exception("连接超时，请检查网络或 URL 是否正确"))
            } catch (e: java.net.UnknownHostException) {
                Logger.e(TAG, " 无法解析主机", e)
                Result.failure(Exception("无法连接服务器，请检查 URL"))
            } catch (e: IOException) {
                Logger.e(TAG, " 网络错误", e)
                Result.failure(Exception(e.message ?: "网络错误"))
            } catch (e: Exception) {
                Logger.e(TAG, " 预览失败", e)
                Result.failure(Exception("预览失败: ${e.message?.take(100)}"))
            }
        }
    }

    fun removePlugin(pluginId: String) {
        if (!PLUGIN_ID_REGEX.matches(pluginId)) {
            Logger.w(TAG, " 拒绝删除非法插件 ID: $pluginId")
            return
        }
        val file = File(getPluginDir(), "$pluginId.json")
        if (!JsonPluginStorage.deleteConsistently(file)) {
            Logger.e(TAG, " 删除插件文件失败，保留运行时状态: $pluginId")
            return
        }

        var removedType: String? = null
        _plugins.update { current ->
            removedType = current.find { it.plugin.id == pluginId }?.plugin?.type
            current.filter { it.plugin.id != pluginId }
        }
        _filterStats.update { it - pluginId }
        clearEnabledState(pluginId)
        schedulePersistStats()
        removedType?.let(::notifyPluginTypeChanged)
        Logger.d(TAG, " 删除插件: $pluginId")
    }

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
            targetType?.let(::notifyPluginTypeChanged)
        }
    }

    private val _lastFilteredCount = MutableStateFlow(0)
    val lastFilteredCount: StateFlow<Int> = _lastFilteredCount.asStateFlow()

    fun shouldShowVideo(video: VideoItem, recordStats: Boolean = true): Boolean {
        val feedPlugins = _plugins.value.filter { it.enabled && it.plugin.type == "feed" }
        if (feedPlugins.isEmpty()) {
            if (recordStats) _lastFilteredCount.value = 0
            return true
        }

        val hiddenBy = findFirstMatchingFeedPlugin(video, feedPlugins)
        if (hiddenBy == null) {
            if (recordStats) _lastFilteredCount.value = 0
            return true
        }

        if (recordStats) {
            mergeStatsDelta(mapOf(hiddenBy.plugin.id to 1))
            _lastFilteredCount.value = 1
        }
        return false
    }

    fun filterVideos(videos: List<VideoItem>, recordStats: Boolean = true): List<VideoItem> {
        val feedPlugins = _plugins.value.filter { it.enabled && it.plugin.type == "feed" }
        if (feedPlugins.isEmpty()) {
            if (recordStats) _lastFilteredCount.value = 0
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

        if (recordStats && statsDelta.isNotEmpty()) mergeStatsDelta(statsDelta)
        if (recordStats) _lastFilteredCount.value = filteredCount
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
            val shouldShow = runPluginSafely(loaded, fallback = true) {
                RuleEngine.shouldShowVideo(video, loaded.plugin.rules)
            }
            if (!shouldShow) return loaded
        }
        return null
    }

    fun updatePlugin(plugin: JsonRulePlugin) {
        validatePlugin(plugin)?.let { error ->
            Logger.w(TAG, " 更新插件失败: $error")
            return
        }
        try {
            savePlugin(plugin)
        } catch (e: Exception) {
            Logger.e(TAG, " 更新插件写盘失败，保留原状态", e)
            return
        }

        var updated = false
        _plugins.update { current ->
            current.map { loaded ->
                if (loaded.plugin.id == plugin.id) {
                    updated = true
                    loaded.copy(plugin = plugin)
                } else {
                    loaded
                }
            }
        }
        if (!updated) {
            Logger.w(TAG, " 更新插件失败，运行时插件不存在: ${plugin.id}")
            return
        }

        _filterStats.update { it - plugin.id }
        schedulePersistStats()
        notifyPluginTypeChanged(plugin.type)
        Logger.d(TAG, " 插件已更新: ${plugin.name}")
    }

    fun resetStats(pluginId: String? = null) {
        if (pluginId != null) {
            _filterStats.update { it - pluginId }
        } else {
            _filterStats.update { emptyMap() }
        }
        schedulePersistStats()
        Logger.d(TAG, " 统计已重置: ${pluginId ?: "全部"}")
    }

    fun testPluginRules(pluginId: String, sampleVideos: List<VideoItem>): Pair<Int, Int> {
        val loaded = _plugins.value.find { it.plugin.id == pluginId }
            ?: return Pair(sampleVideos.size, sampleVideos.size)
        val filtered = sampleVideos.filter { video ->
            runPluginSafely(loaded, fallback = true) {
                RuleEngine.shouldShowVideo(video, loaded.plugin.rules)
            }
        }
        return Pair(sampleVideos.size, filtered.size)
    }

    fun getFilteredVideosByPlugin(pluginId: String, sampleVideos: List<VideoItem>): List<VideoItem> {
        val loaded = _plugins.value.find { it.plugin.id == pluginId }
            ?: return emptyList()
        return sampleVideos.filter { video ->
            !runPluginSafely(loaded, fallback = true) {
                RuleEngine.shouldShowVideo(video, loaded.plugin.rules)
            }
        }
    }

    fun shouldShowDanmaku(danmaku: DanmakuItem): Boolean {
        val danmakuPlugins = _plugins.value.filter { it.enabled && it.plugin.type == "danmaku" }
        return danmakuPlugins.all { loaded ->
            runPluginSafely(loaded, fallback = true) {
                RuleEngine.shouldShowDanmaku(danmaku, loaded.plugin.rules)
            }
        }
    }

    fun getDanmakuStyle(danmaku: DanmakuItem): DanmakuStyle? {
        val danmakuPlugins = _plugins.value.filter { it.enabled && it.plugin.type == "danmaku" }
        for (loaded in danmakuPlugins) {
            val style = runPluginSafely<DanmakuStyle?>(loaded, fallback = null) {
                RuleEngine.getDanmakuHighlightStyle(danmaku, loaded.plugin.rules)
            }
            if (style != null) return style
        }
        return null
    }

    private suspend fun fetchPluginFromUrl(normalizedUrl: String): Result<JsonRulePlugin> {
        validateImportUrl(normalizedUrl).onFailure { return Result.failure(it) }
        Logger.d(TAG, " 下载插件: $normalizedUrl")

        val request = Request.Builder().url(normalizedUrl).build()
        val response = executeCancellable(request)
        val content = response.use { httpResponse ->
            if (!httpResponse.isSuccessful) {
                return Result.failure(
                    Exception("下载失败: HTTP ${httpResponse.code} ${httpResponse.message}")
                )
            }
            readBodyBounded(httpResponse.body)
        }

        validateJsonPluginDocument(content)?.let { error ->
            return Result.failure(Exception(error))
        }

        val plugin = try {
            json.decodeFromString<JsonRulePlugin>(content)
        } catch (e: Exception) {
            Logger.e(TAG, " JSON 解析失败", e)
            return Result.failure(Exception("JSON 解析失败: ${e.message?.take(100)}"))
        }

        validatePlugin(plugin)?.let { error ->
            return Result.failure(Exception(error))
        }
        return Result.success(plugin)
    }

    private suspend fun executeCancellable(request: Request): Response {
        return suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            })
        }
    }

    private fun readBodyBounded(body: ResponseBody): String {
        val declaredLength = body.contentLength()
        if (declaredLength > JSON_PLUGIN_MAX_BYTES) {
            throw IOException("插件文件过大，最大 ${JSON_PLUGIN_MAX_BYTES / 1024}KB")
        }

        val output = ByteArrayOutputStream(
            declaredLength.takeIf { it in 1..JSON_PLUGIN_MAX_BYTES.toLong() }
                ?.toInt()
                ?: 8192
        )
        var total = 0
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > JSON_PLUGIN_MAX_BYTES) {
                    throw IOException("插件文件过大，最大 ${JSON_PLUGIN_MAX_BYTES / 1024}KB")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun validateImportUrl(url: String): Result<Unit> {
        if (url.isBlank()) return Result.failure(Exception("请输入插件链接"))
        if (url.length > 2048) return Result.failure(Exception("插件链接过长"))
        val uri = Uri.parse(url)
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return Result.failure(Exception("插件链接仅支持 HTTPS"))
        }
        if (uri.host.isNullOrBlank()) return Result.failure(Exception("链接格式不正确"))
        return Result.success(Unit)
    }

    private fun validatePlugin(plugin: JsonRulePlugin): String? = validateJsonRulePlugin(plugin)

    private inline fun <T> runPluginSafely(
        loaded: LoadedJsonPlugin,
        fallback: T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (e: Exception) {
            Logger.e(TAG, " JSON 插件运行失败，已忽略: ${loaded.plugin.id}", e)
            fallback
        }
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
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("无法创建 JSON 插件目录")
        }
        return dir
    }

    private fun savePlugin(plugin: JsonRulePlugin) {
        val content = json.encodeToString(JsonRulePlugin.serializer(), plugin)
        validateJsonPluginDocument(content)?.let { error -> throw IOException(error) }
        val file = File(getPluginDir(), "${plugin.id}.json")
        JsonPluginStorage.writeAtomically(file, content)
    }

    private fun loadSavedPlugins() {
        val dir = try {
            getPluginDir()
        } catch (e: Exception) {
            Logger.e(TAG, " JSON 插件目录不可用", e)
            _plugins.value = emptyList()
            return
        }
        val prefs = appContext.getSharedPreferences(ENABLED_PREFS, Context.MODE_PRIVATE)

        val loadedById = linkedMapOf<String, LoadedJsonPlugin>()
        dir.listFiles()
            ?.sortedBy { it.name }
            ?.forEach { file ->
                if (file.extension != "json") return@forEach
                try {
                    if (file.length() > JSON_PLUGIN_MAX_BYTES) {
                        Logger.w(TAG, " 插件文件过大，已忽略: ${file.name}")
                        return@forEach
                    }
                    val content = file.readText()
                    validateJsonPluginDocument(content)?.let { error ->
                        Logger.w(TAG, " 插件文件无效，已忽略: ${file.name} ($error)")
                        return@forEach
                    }
                    val plugin = json.decodeFromString<JsonRulePlugin>(content)
                    validatePlugin(plugin)?.let { error ->
                        Logger.w(TAG, " 插件文件无效，已忽略: ${file.name} ($error)")
                        return@forEach
                    }
                    if (file.nameWithoutExtension != plugin.id) {
                        Logger.w(TAG, " 插件文件名与 ID 不一致，已忽略: ${file.name}")
                        return@forEach
                    }
                    val enabled = prefs.getBoolean("$ENABLED_PREFIX${plugin.id}", true)
                    loadedById[plugin.id] = LoadedJsonPlugin(plugin, enabled, sourceUrl = null)
                } catch (e: Exception) {
                    Logger.w(TAG, " 加载插件失败: ${file.name} (${e.message})")
                }
            }

        _plugins.value = loadedById.values.toList()
        Logger.d(TAG, " 加载了 ${_plugins.value.size} 个 JSON 插件")
    }

    private fun notifyPluginTypeChanged(type: String) {
        if (type == "feed") PluginManager.notifyFeedPluginsUpdated()
        if (type == "danmaku") PluginManager.notifyDanmakuPluginsUpdated()
    }

    private fun loadFilterStats() {
        val prefs = appContext.getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
        val statsMap = mutableMapOf<String, Int>()
        prefs.all.forEach { (key, value) ->
            if (value is Int) statsMap[key] = value
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

    private fun saveFilterStats() {
        val prefs = appContext.getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()
        _filterStats.value.forEach { (pluginId, count) -> editor.putInt(pluginId, count) }
        editor.apply()
    }
}

data class LoadedJsonPlugin(
    val plugin: JsonRulePlugin,
    val enabled: Boolean,
    val sourceUrl: String?
)
