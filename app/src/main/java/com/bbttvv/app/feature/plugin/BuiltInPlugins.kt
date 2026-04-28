package com.bbttvv.app.feature.plugin

import com.bbttvv.app.core.database.entity.BlockedUp
import com.bbttvv.app.core.plugin.DanmakuItem
import com.bbttvv.app.core.plugin.DanmakuPlugin
import com.bbttvv.app.core.plugin.DanmakuStyle
import com.bbttvv.app.core.plugin.FeedPlugin
import com.bbttvv.app.core.plugin.Plugin
import com.bbttvv.app.core.plugin.PluginInfo
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.PluginStore
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.SponsorBlockMarkerMode
import com.bbttvv.app.data.model.response.SponsorCategory
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.resolveSponsorBlockMarkerMode
import com.bbttvv.app.data.repository.BlockedUpRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val SPONSOR_BLOCK_PLUGIN_ID = "sponsor_block"
const val AD_FILTER_PLUGIN_ID = "ad_filter"
const val DANMAKU_ENHANCE_PLUGIN_ID = "danmaku_enhance"

private val pluginJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class SponsorBlockConfig(
    val autoSkip: Boolean = true,
    val markerModeRaw: String = SponsorBlockMarkerMode.SPONSOR_ONLY.name,
    val showSkipPrompt: Boolean = true,
    val skipSponsor: Boolean = true,
    val skipSelfPromo: Boolean = true,
    val skipIntro: Boolean = true,
    val skipOutro: Boolean = true,
    val skipInteraction: Boolean = true,
    val skipPreview: Boolean = false,
    val skipFiller: Boolean = false
) {
    val markerMode: SponsorBlockMarkerMode
        get() = resolveSponsorBlockMarkerMode(markerModeRaw)

    fun normalized(): SponsorBlockConfig = copy(markerModeRaw = markerMode.name)

    fun isCategoryEnabled(category: String): Boolean = when (category) {
        SponsorCategory.SPONSOR -> skipSponsor
        SponsorCategory.SELFPROMO -> skipSelfPromo
        SponsorCategory.INTRO -> skipIntro
        SponsorCategory.OUTRO -> skipOutro
        SponsorCategory.INTERACTION -> skipInteraction
        SponsorCategory.PREVIEW -> skipPreview
        SponsorCategory.FILLER -> skipFiller
        else -> true
    }
}

fun List<PluginInfo>.findSponsorBlockPluginInfo(): PluginInfo? {
    return firstOrNull { it.plugin.id == SPONSOR_BLOCK_PLUGIN_ID }
}

fun List<PluginInfo>.findSponsorBlockPlugin(): SponsorBlockPlugin? {
    return findSponsorBlockPluginInfo()?.plugin as? SponsorBlockPlugin
}

class SponsorBlockPlugin : Plugin {
    override val id: String = SPONSOR_BLOCK_PLUGIN_ID
    override val name: String = "空降助手"
    override val description: String = "基于 SponsorBlock 自动跳过片头片尾、恰饭和其他可跳过片段。"
    override val version: String = "1.0.0"
    override val author: String = "BBTTVV"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _configState = MutableStateFlow(SponsorBlockConfig())
    val configState: StateFlow<SponsorBlockConfig> = _configState.asStateFlow()

    init {
        scope.launch {
            loadConfigFromStore()
        }
    }

    override suspend fun onEnable() {
        loadConfigFromStore()
        Logger.d("SponsorBlockPlugin", "SponsorBlock plugin enabled")
    }

    override suspend fun onDisable() {
        Logger.d("SponsorBlockPlugin", "SponsorBlock plugin disabled")
    }

    fun setAutoSkip(enabled: Boolean) {
        persistConfig(_configState.value.copy(autoSkip = enabled))
    }

    fun setMarkerMode(mode: SponsorBlockMarkerMode) {
        persistConfig(_configState.value.copy(markerModeRaw = mode.name))
    }

    fun setShowSkipPrompt(enabled: Boolean) {
        persistConfig(_configState.value.copy(showSkipPrompt = enabled))
    }

    fun setSkipSponsor(enabled: Boolean) {
        persistConfig(_configState.value.copy(skipSponsor = enabled))
    }

    fun setSkipSelfPromo(enabled: Boolean) {
        persistConfig(_configState.value.copy(skipSelfPromo = enabled))
    }

    fun setSkipIntro(enabled: Boolean) {
        persistConfig(_configState.value.copy(skipIntro = enabled))
    }

    fun setSkipOutro(enabled: Boolean) {
        persistConfig(_configState.value.copy(skipOutro = enabled))
    }

    fun setSkipInteraction(enabled: Boolean) {
        persistConfig(_configState.value.copy(skipInteraction = enabled))
    }

    fun setSkipPreview(enabled: Boolean) {
        persistConfig(_configState.value.copy(skipPreview = enabled))
    }

    fun setSkipFiller(enabled: Boolean) {
        persistConfig(_configState.value.copy(skipFiller = enabled))
    }

    private suspend fun loadConfigFromStore() {
        val context = PluginManager.getContext()
        val raw = PluginStore.getConfigJson(context, id)
        val config = raw?.let {
            runCatching { pluginJson.decodeFromString<SponsorBlockConfig>(it).normalized() }
                .onFailure { error -> Logger.e("SponsorBlockPlugin", "Failed to decode config", error) }
                .getOrNull()
        } ?: SponsorBlockConfig(
            autoSkip = SettingsManager.consumeLegacySponsorBlockAutoSkip(
                context = context,
                defaultValue = false
            )
        )
        _configState.value = config
        if (raw == null) {
            persistConfig(config)
        }
    }

    private fun persistConfig(config: SponsorBlockConfig) {
        val normalizedConfig = config.normalized()
        _configState.value = normalizedConfig
        scope.launch {
            runCatching {
                PluginStore.setConfigJson(
                    context = PluginManager.getContext(),
                    pluginId = id,
                    configJson = pluginJson.encodeToString(normalizedConfig)
                )
            }.onFailure { error ->
                Logger.e("SponsorBlockPlugin", "Failed to persist config", error)
            }
        }
    }
}

@Serializable
data class AdFilterConfig(
    val filterSponsored: Boolean = true,
    val filterClickbait: Boolean = true,
    val filterLowQuality: Boolean = false,
    val minViewCount: Int = 1000,
    val blockedUpNames: List<String> = emptyList(),
    val blockedUpMids: List<Long> = emptyList(),
    val blockedKeywords: List<String> = emptyList()
)

class AdFilterPlugin : FeedPlugin {
    override val id: String = AD_FILTER_PLUGIN_ID
    override val name: String = "去广告增强"
    override val description: String = "过滤营销推广、夸张标题和已拉黑的 UP 主。"
    override val version: String = "1.0.0"
    override val author: String = "BBTTVV"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val blockedUpRepository by lazy { BlockedUpRepository(PluginManager.getContext()) }

    private val _configState = MutableStateFlow(AdFilterConfig())
    val configState: StateFlow<AdFilterConfig> = _configState.asStateFlow()

    private val _blockedUps = MutableStateFlow<List<BlockedUp>>(emptyList())
    val blockedUps: StateFlow<List<BlockedUp>> = _blockedUps.asStateFlow()

    @Volatile
    private var blockedMidSet: Set<Long> = emptySet()
    private var blockedObserverJob: Job? = null

    private val adKeywords = listOf(
        "商业合作", "恰饭", "推广", "广告", "赞助", "植入",
        "品牌合作", "合作推广", "本期合作", "本视频由",
        "官方活动", "官方推荐", "种草", "优惠券", "领券"
    )
    private val clickbaitKeywords = listOf(
        "震惊", "太离谱", "绝了", "一定要看", "必看", "看哭了",
        "99%的人不知道", "你一定不知道", "曝光", "揭秘", "封神"
    )

    init {
        scope.launch {
            loadConfigFromStore()
        }
        startBlockedObserver()
    }

    override suspend fun onEnable() {
        loadConfigFromStore()
        PluginManager.notifyFeedPluginsUpdated()
        Logger.d("AdFilterPlugin", "Ad filter plugin enabled")
    }

    override suspend fun onDisable() {
        PluginManager.notifyFeedPluginsUpdated()
        Logger.d("AdFilterPlugin", "Ad filter plugin disabled")
    }

    override fun shouldShowItem(item: VideoItem): Boolean {
        val config = _configState.value
        if (item.owner.mid in blockedMidSet || item.owner.mid in config.blockedUpMids) return false
        if (config.blockedUpNames.any { blockedName ->
                val normalizedBlocked = blockedName.trim()
                normalizedBlocked.isNotEmpty() && matchesBlockedName(item.owner.name, normalizedBlocked)
            }
        ) {
            return false
        }

        val title = item.title.trim()
        if (config.blockedKeywords.any { keyword ->
                val normalizedKeyword = keyword.trim()
                normalizedKeyword.isNotEmpty() && title.contains(normalizedKeyword, ignoreCase = true)
            }
        ) {
            return false
        }
        if (config.filterSponsored && adKeywords.any { keyword -> title.contains(keyword, ignoreCase = true) }) {
            return false
        }
        if (config.filterClickbait && clickbaitKeywords.any { keyword -> title.contains(keyword, ignoreCase = true) }) {
            return false
        }
        if (config.filterLowQuality && item.stat.view in 1 until config.minViewCount) {
            return false
        }
        return true
    }

    fun setFilterSponsored(enabled: Boolean) {
        persistConfig(_configState.value.copy(filterSponsored = enabled))
    }

    fun setFilterClickbait(enabled: Boolean) {
        persistConfig(_configState.value.copy(filterClickbait = enabled))
    }

    fun setFilterLowQuality(enabled: Boolean) {
        persistConfig(_configState.value.copy(filterLowQuality = enabled))
    }

    fun setMinViewCount(minViewCount: Int) {
        persistConfig(_configState.value.copy(minViewCount = minViewCount.coerceAtLeast(1)))
    }

    fun addBlockedUpName(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        if (_configState.value.blockedUpNames.any { it.equals(normalized, ignoreCase = true) }) return
        persistConfig(_configState.value.copy(blockedUpNames = _configState.value.blockedUpNames + normalized))
    }

    fun removeBlockedUpName(name: String) {
        persistConfig(
            _configState.value.copy(
                blockedUpNames = _configState.value.blockedUpNames.filterNot { it.equals(name, ignoreCase = true) }
            )
        )
    }

    fun addBlockedUpMid(mid: Long) {
        if (mid <= 0L || _configState.value.blockedUpMids.contains(mid)) return
        persistConfig(_configState.value.copy(blockedUpMids = _configState.value.blockedUpMids + mid))
    }

    fun removeBlockedUpMid(mid: Long) {
        persistConfig(_configState.value.copy(blockedUpMids = _configState.value.blockedUpMids - mid))
    }

    fun addBlockedKeyword(keyword: String) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return
        if (_configState.value.blockedKeywords.any { it.equals(normalized, ignoreCase = true) }) return
        persistConfig(_configState.value.copy(blockedKeywords = _configState.value.blockedKeywords + normalized))
    }

    fun removeBlockedKeyword(keyword: String) {
        persistConfig(
            _configState.value.copy(
                blockedKeywords = _configState.value.blockedKeywords.filterNot { it.equals(keyword, ignoreCase = true) }
            )
        )
    }

    suspend fun unblockUploader(mid: Long) {
        blockedUpRepository.unblockUp(mid)
    }

    private fun startBlockedObserver() {
        if (blockedObserverJob != null) return
        blockedObserverJob = scope.launch {
            blockedUpRepository.getAllBlockedUps().collectLatest { blockedUps ->
                _blockedUps.value = blockedUps
                blockedMidSet = blockedUps.map { it.mid }.toSet()
                PluginManager.notifyFeedPluginsUpdated()
            }
        }
    }

    private suspend fun loadConfigFromStore() {
        val context = PluginManager.getContext()
        val raw = PluginStore.getConfigJson(context, id)
        val config = raw?.let {
            runCatching { pluginJson.decodeFromString<AdFilterConfig>(it) }
                .onFailure { error -> Logger.e("AdFilterPlugin", "Failed to decode config", error) }
                .getOrNull()
        } ?: AdFilterConfig()
        _configState.value = config
    }

    private fun persistConfig(config: AdFilterConfig) {
        _configState.value = config
        scope.launch {
            runCatching {
                PluginStore.setConfigJson(
                    context = PluginManager.getContext(),
                    pluginId = id,
                    configJson = pluginJson.encodeToString(config)
                )
            }.onFailure { error ->
                Logger.e("AdFilterPlugin", "Failed to persist config", error)
            }
            PluginManager.notifyFeedPluginsUpdated()
        }
    }

    private fun matchesBlockedName(ownerName: String, blockedName: String): Boolean {
        val normalizedOwner = ownerName.trim().lowercase()
        val normalizedBlocked = blockedName.trim().lowercase()
        if (normalizedOwner.isEmpty() || normalizedBlocked.isEmpty()) return false
        return normalizedOwner == normalizedBlocked ||
            normalizedOwner.contains(normalizedBlocked) ||
            normalizedBlocked.contains(normalizedOwner)
    }
}

@Serializable
data class DanmakuEnhanceConfig(
    val enableFilter: Boolean = true,
    val enableHighlight: Boolean = true,
    val blockedKeywords: String = "剧透,前方高能",
    val blockedUserIds: String = "",
    val highlightKeywords: String = "同传,中文字幕,翻译"
)

class DanmakuEnhancePlugin : DanmakuPlugin {
    override val id: String = DANMAKU_ENHANCE_PLUGIN_ID
    override val name: String = "弹幕增强"
    override val description: String = "提供关键词屏蔽、用户屏蔽和同传高亮。"
    override val version: String = "1.0.0"
    override val author: String = "BBTTVV"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _configState = MutableStateFlow(DanmakuEnhanceConfig())
    val configState: StateFlow<DanmakuEnhanceConfig> = _configState.asStateFlow()

    @Volatile
    private var blockedKeywordsCache: List<String> = splitKeywords(_configState.value.blockedKeywords)
    @Volatile
    private var blockedUsersCache: List<String> = splitKeywords(_configState.value.blockedUserIds)
    @Volatile
    private var highlightKeywordsCache: List<String> = splitKeywords(_configState.value.highlightKeywords)

    init {
        scope.launch {
            loadConfigFromStore()
        }
    }

    override suspend fun onEnable() {
        loadConfigFromStore()
        PluginManager.notifyDanmakuPluginsUpdated()
        Logger.d("DanmakuEnhancePlugin", "Danmaku enhance plugin enabled")
    }

    override suspend fun onDisable() {
        PluginManager.notifyDanmakuPluginsUpdated()
        Logger.d("DanmakuEnhancePlugin", "Danmaku enhance plugin disabled")
    }

    override fun filterDanmaku(danmaku: DanmakuItem): DanmakuItem? {
        val config = _configState.value
        if (!config.enableFilter) return danmaku
        if (blockedKeywordsCache.any { keyword -> danmaku.content.contains(keyword, ignoreCase = true) }) {
            return null
        }
        if (blockedUsersCache.any { userId ->
                val normalized = userId.trim().lowercase()
                normalized.isNotEmpty() && danmaku.userId.lowercase().contains(normalized)
            }
        ) {
            return null
        }
        return danmaku
    }

    override fun styleDanmaku(danmaku: DanmakuItem): DanmakuStyle? {
        val config = _configState.value
        if (!config.enableHighlight) return null
        if (highlightKeywordsCache.any { keyword -> danmaku.content.contains(keyword, ignoreCase = true) }) {
            return DanmakuStyle(
                textColor = androidx.compose.ui.graphics.Color(0xFFFFD54F),
                backgroundColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f),
                bold = true,
                scale = 1.08f
            )
        }
        return null
    }

    fun setEnableFilter(enabled: Boolean) {
        persistConfig(_configState.value.copy(enableFilter = enabled))
    }

    fun setEnableHighlight(enabled: Boolean) {
        persistConfig(_configState.value.copy(enableHighlight = enabled))
    }

    fun setBlockedKeywords(rawValue: String) {
        persistConfig(_configState.value.copy(blockedKeywords = rawValue))
    }

    fun setBlockedUserIds(rawValue: String) {
        persistConfig(_configState.value.copy(blockedUserIds = rawValue))
    }

    fun setHighlightKeywords(rawValue: String) {
        persistConfig(_configState.value.copy(highlightKeywords = rawValue))
    }

    private suspend fun loadConfigFromStore() {
        val context = PluginManager.getContext()
        val raw = PluginStore.getConfigJson(context, id)
        val config = raw?.let {
            runCatching { pluginJson.decodeFromString<DanmakuEnhanceConfig>(it) }
                .onFailure { error -> Logger.e("DanmakuEnhancePlugin", "Failed to decode config", error) }
                .getOrNull()
        } ?: DanmakuEnhanceConfig()
        _configState.value = config
        refreshCaches(config)
    }

    private fun persistConfig(config: DanmakuEnhanceConfig) {
        _configState.value = config
        refreshCaches(config)
        scope.launch {
            runCatching {
                PluginStore.setConfigJson(
                    context = PluginManager.getContext(),
                    pluginId = id,
                    configJson = pluginJson.encodeToString(config)
                )
            }.onFailure { error ->
                Logger.e("DanmakuEnhancePlugin", "Failed to persist config", error)
            }
            PluginManager.notifyDanmakuPluginsUpdated()
        }
    }

    private fun refreshCaches(config: DanmakuEnhanceConfig) {
        blockedKeywordsCache = splitKeywords(config.blockedKeywords)
        blockedUsersCache = splitKeywords(config.blockedUserIds)
        highlightKeywordsCache = splitKeywords(config.highlightKeywords)
    }

    private fun splitKeywords(value: String): List<String> {
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
