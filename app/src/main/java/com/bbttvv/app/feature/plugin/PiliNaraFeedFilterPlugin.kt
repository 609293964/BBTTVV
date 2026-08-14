package com.bbttvv.app.feature.plugin

import com.bbttvv.app.core.plugin.FeedKind
import com.bbttvv.app.core.plugin.FeedPlugin
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.PluginStore
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PILINARA_FEED_FILTER_PLUGIN_ID = "pilinara_feed_filter"

/**
 * A TV-oriented port of PiliNara's recommendation filter.
 *
 * It only changes the visible projection of already-loaded feeds; source paging,
 * card keys and focus restoration remain owned by their existing controllers.
 */
class PiliNaraFeedFilterPlugin : FeedPlugin {
    override val id: String = PILINARA_FEED_FILTER_PLUGIN_ID
    override val name: String = "推荐流过滤"
    override val description: String = "按时长、播放量、点赞率、关键词和 UP 主规则筛选推荐内容。"
    override val version: String = "1.0.0"
    override val author: String = "PiliNara / BBTTVV"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val _configState = MutableStateFlow(PiliNaraFeedFilterConfig())
    val configState: StateFlow<PiliNaraFeedFilterConfig> = _configState.asStateFlow()

    @Volatile private var titleRegex: Regex? = null
    @Volatile private var zoneRegex: Regex? = null

    override suspend fun onEnable() {
        loadConfig()
        PluginManager.notifyFeedPluginsUpdated()
    }

    override suspend fun onDisable() {
        PluginManager.notifyFeedPluginsUpdated()
    }

    override fun shouldShowItem(item: VideoItem): Boolean =
        shouldShowItem(item, FeedKind.HOME_RECOMMEND)

    override fun shouldShowItem(item: VideoItem, feedKind: FeedKind): Boolean = shouldShowFeedItem(
        config = _configState.value,
        item = item,
        feedKind = feedKind,
        titleRegex = titleRegex,
        zoneRegex = zoneRegex,
    )

    fun setMinDuration(seconds: Long) = updateConfig { it.copy(minDurationSeconds = seconds.coerceAtLeast(0)) }
    fun setMinPlayCount(count: Long) = updateConfig { it.copy(minPlayCount = count.coerceAtLeast(0)) }
    fun setMinLikeRatio(percent: Int) = updateConfig { it.copy(minLikeRatioPercent = percent.coerceIn(0, 100)) }
    fun setExemptFollowed(enabled: Boolean) = updateConfig { it.copy(exemptFollowed = enabled) }
    fun setApplyToPopular(enabled: Boolean) = updateConfig { it.copy(applyToPopular = enabled) }
    fun setTitleKeywords(value: String) = updateConfig { it.copy(titleKeywords = value) }
    fun setZoneKeywords(value: String) = updateConfig { it.copy(zoneKeywords = value) }
    fun setBlockedMids(value: String) = updateConfig { it.copy(blockedMids = parseMids(value)) }
    fun setWhitelistMids(value: String) = updateConfig { it.copy(whitelistMids = parseMids(value)) }

    private suspend fun loadConfig() {
        val loaded = PluginStore.getConfigJson(PluginManager.getContext(), id)
            ?.let { raw -> runCatching { json.decodeFromString(PiliNaraFeedFilterConfig.serializer(), raw) }.getOrNull() }
            ?: PiliNaraFeedFilterConfig()
        applyConfig(loaded)
    }

    private fun updateConfig(transform: (PiliNaraFeedFilterConfig) -> PiliNaraFeedFilterConfig) {
        val next = transform(_configState.value)
        if (next == _configState.value) return
        applyConfig(next)
        scope.launch {
            runCatching {
                PluginStore.setConfigJson(PluginManager.getContext(), id, json.encodeToString(PiliNaraFeedFilterConfig.serializer(), next))
            }.onFailure { Logger.e(TAG, "Failed to save recommendation filter settings", it) }
        }
        PluginManager.notifyFeedPluginsUpdated()
    }

    private fun applyConfig(config: PiliNaraFeedFilterConfig) {
        _configState.value = config
        titleRegex = compileKeywords(config.titleKeywords)
        zoneRegex = compileKeywords(config.zoneKeywords)
    }

    companion object {
        private const val TAG = "PiliNaraFeedFilter"

        /** One regex per non-empty line. Invalid expressions are ignored, never fatal. */
        fun compileKeywords(value: String): Regex? {
            val expression = value.lineSequence().map(String::trim).filter(String::isNotEmpty)
                .joinToString("|") { "($it)" }
            return expression.takeIf(String::isNotEmpty)?.let { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }
        }

        fun parseMids(value: String): Set<Long> = value.lineSequence()
            .map(String::trim)
            .mapNotNull { Regex("\\d+").find(it)?.value?.toLongOrNull() }
            .filter { it > 0L }
            .toSet()

        fun midsEditorValue(mids: Set<Long>): String = mids.sorted().joinToString("\n")
    }
}

@Serializable
data class PiliNaraFeedFilterConfig(
    val minDurationSeconds: Long = 0,
    val minPlayCount: Long = 0,
    val minLikeRatioPercent: Int = 0,
    val titleKeywords: String = "",
    val zoneKeywords: String = "",
    val blockedMids: Set<Long> = emptySet(),
    val whitelistMids: Set<Long> = emptySet(),
    val exemptFollowed: Boolean = true,
    val applyToPopular: Boolean = false,
)

internal fun shouldShowFeedItem(
    config: PiliNaraFeedFilterConfig,
    item: VideoItem,
    feedKind: FeedKind,
    titleRegex: Regex?,
    zoneRegex: Regex?,
): Boolean {
    if (feedKind == FeedKind.HOME_POPULAR && !config.applyToPopular) return true
    if (feedKind != FeedKind.HOME_RECOMMEND && feedKind != FeedKind.HOME_POPULAR) return true

    val mid = item.owner.mid
    if (mid > 0L && mid in config.whitelistMids) return true
    if (feedKind == FeedKind.HOME_RECOMMEND && config.exemptFollowed && item.isFollowed) return true
    if (item.duration > 0 && item.duration < config.minDurationSeconds) return false

    val views = item.stat.view.toLong()
    val likes = item.stat.like.toLong()
    if (views >= 0L && views < config.minPlayCount) return false
    if (views > 0L && likes >= 0L && likes * 100L < config.minLikeRatioPercent.toLong() * views) return false
    if (titleRegex?.containsMatchIn(item.title) == true) return false
    if (mid > 0L && mid in config.blockedMids) return false
    if (zoneRegex?.containsMatchIn(item.tname) == true) return false
    return true
}
