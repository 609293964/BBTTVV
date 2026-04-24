package com.bbttvv.app.core.player

import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.plugin.DanmakuItem
import com.bbttvv.app.core.plugin.DanmakuPlugin
import com.bbttvv.app.core.plugin.DanmakuStyle
import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.plugin.json.JsonPluginManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.store.player.DanmakuSettings
import com.bbttvv.app.core.store.player.DanmakuSettingsStore
import com.bbttvv.app.core.store.player.normalizeDanmakuAiShieldLevel
import com.bbttvv.app.core.store.player.shouldAllowDanmakuWeight
import com.bbttvv.app.core.store.player.shouldRenderDanmakuItem
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.repository.DanmakuRepository
import com.bbttvv.app.data.repository.DanmakuUserFilter
import com.bbttvv.app.data.repository.DanmakuWebSetting
import com.bbttvv.app.feature.video.danmaku.AdvancedDanmakuData
import com.bbttvv.app.feature.video.danmaku.DanmakuParser
import com.bbttvv.app.feature.video.danmaku.DanmakuRenderPayload
import com.bbttvv.app.feature.video.danmaku.ParsedDanmaku
import com.bbttvv.app.feature.video.danmaku.WeightedTextData
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import kotlin.math.abs

internal data class PlayerDanmakuFilterContext(
    val followBiliShield: Boolean = false,
    val serverSetting: DanmakuWebSetting? = null,
    val userFilter: DanmakuUserFilter = DanmakuUserFilter.EMPTY,
)

internal data class PlayerDanmakuLoadResult(
    val parsed: ParsedDanmaku?,
    val rawData: ByteArray?,
    val sourceLabel: String,
    val filterContext: PlayerDanmakuFilterContext,
)

internal object PlayerDanmakuPipeline {
    suspend fun loadSegmentSource(
        cid: Long,
        aid: Long = 0L,
        segmentIndex: Int = 1,
    ): PlayerDanmakuLoadResult {
        val localSettings = resolveDanmakuSettings()
        val shouldFollowBiliShield =
            localSettings.followBiliShield &&
                !TokenManager.sessDataCache.isNullOrBlank() &&
                aid > 0L
        val danmakuViewMetadata = if (shouldFollowBiliShield && segmentIndex == 1) {
            DanmakuRepository.getDanmakuView(cid = cid, aid = aid)
        } else {
            null
        }
        val userFilter = if (shouldFollowBiliShield) {
            DanmakuRepository.getDanmakuUserFilter()
        } else {
            DanmakuUserFilter.EMPTY
        }
        val filterContext = PlayerDanmakuFilterContext(
            followBiliShield = shouldFollowBiliShield,
            serverSetting = danmakuViewMetadata?.setting,
            userFilter = userFilter,
        )
        val segmentBytes = DanmakuRepository.getDanmakuSegment(cid, segmentIndex)

        val segmented = if (segmentBytes != null) {
            DanmakuParser.parseProtobuf(listOf(segmentBytes))
        } else {
            null
        }

        if (segmented != null &&
            (segmented.standardList.isNotEmpty() || segmented.advancedList.isNotEmpty())
        ) {
            return PlayerDanmakuLoadResult(
                parsed = segmented,
                rawData = null,
                sourceLabel = "SEG_$segmentIndex",
                filterContext = filterContext,
            )
        }

        val xmlData = DanmakuRepository.getDanmakuRawData(cid)
        val parsed = xmlData?.let(DanmakuParser::parse)
        return PlayerDanmakuLoadResult(
            parsed = parsed,
            rawData = xmlData,
            sourceLabel = if (segmentIndex == 1) "XML" else "XML_FALLBACK_SEG_$segmentIndex",
            filterContext = filterContext,
        )
    }

    fun buildRenderPayload(
        parsed: ParsedDanmaku,
        sourceLabel: String,
        filterContext: PlayerDanmakuFilterContext,
    ): DanmakuRenderPayload {
        val danmakuSettings = resolveEffectiveDanmakuSettings(
            localSettings = resolveDanmakuSettings(),
            filterContext = filterContext,
        )
        val (baseFilteredStandard, baseFilteredAdvanced) = applyGlobalDanmakuFilters(
            standardDanmakuList = parsed.standardList,
            advancedDanmakuList = parsed.advancedList,
            settings = danmakuSettings,
            userFilter = filterContext.userFilter,
        )
        val (filteredStandard, filteredAdvanced) = applyDanmakuPluginPipeline(
            standardDanmakuList = baseFilteredStandard,
            advancedDanmakuList = baseFilteredAdvanced,
        )
        return DanmakuRenderPayload(
            standardList = filteredStandard,
            advancedList = filteredAdvanced,
            sourceLabel = sourceLabel,
            totalCount = filteredStandard.size + filteredAdvanced.size,
        )
    }

    private fun applyGlobalDanmakuFilters(
        standardDanmakuList: List<DanmakuData>,
        advancedDanmakuList: List<AdvancedDanmakuData>,
        settings: DanmakuSettings,
        userFilter: DanmakuUserFilter,
    ): Pair<List<DanmakuData>, List<AdvancedDanmakuData>> {
        val filteredStandard = standardDanmakuList.filter { data ->
            val textData = data as? TextData ?: return@filter true
            val weightedTextData = textData as? WeightedTextData
            if (!shouldAllowDanmakuWeight(weightedTextData?.weight ?: Int.MAX_VALUE, settings)) {
                return@filter false
            }
            if (!shouldAllowDanmakuByUserFilter(textData.text.orEmpty(), weightedTextData?.userHash, userFilter)) {
                return@filter false
            }
            shouldRenderDanmakuItem(
                type = mapLayerTypeToDanmakuType(textData.layerType),
                color = (textData.textColor ?: 0x00FFFFFF) and 0x00FFFFFF,
                settings = settings,
            )
        }
        val filteredAdvanced = advancedDanmakuList.filter { data ->
            if (!shouldAllowDanmakuByUserFilter(data.content, null, userFilter)) {
                return@filter false
            }
            shouldRenderDanmakuItem(
                type = 7,
                color = data.color and 0x00FFFFFF,
                settings = settings,
            )
        }
        return Pair(filteredStandard, filteredAdvanced)
    }

    private fun applyDanmakuPluginPipeline(
        standardDanmakuList: List<DanmakuData>,
        advancedDanmakuList: List<AdvancedDanmakuData>,
    ): Pair<List<DanmakuData>, List<AdvancedDanmakuData>> {
        val nativePlugins = PluginManager.getEnabledDanmakuPlugins()
        val useJsonRules = JsonPluginManager.plugins.value.any { it.enabled && it.plugin.type == "danmaku" }
        if (nativePlugins.isEmpty() && !useJsonRules) {
            return Pair(standardDanmakuList, advancedDanmakuList)
        }

        val filteredStandard = ArrayList<DanmakuData>(standardDanmakuList.size)
        standardDanmakuList.forEach { data ->
            val sourceTextData = data as? TextData
            if (sourceTextData == null) {
                filteredStandard.add(data)
                return@forEach
            }
            val textData = sourceTextData.copyForPluginPipeline()
            val filteredItem = runDanmakuFilters(
                item = textData.toPluginItem(),
                nativePlugins = nativePlugins,
                useJsonRules = useJsonRules,
            ) ?: return@forEach
            val style = collectDanmakuStyle(filteredItem, nativePlugins, useJsonRules)
            textData.applyPluginResult(filteredItem, style)
            filteredStandard.add(textData)
        }

        val filteredAdvanced = ArrayList<AdvancedDanmakuData>(advancedDanmakuList.size)
        advancedDanmakuList.forEach { data ->
            val filteredItem = runDanmakuFilters(
                item = DanmakuItem(
                    id = data.id.hashCode().toLong(),
                    content = data.content,
                    timeMs = data.startTimeMs,
                    type = 7,
                    color = data.color and 0x00FFFFFF,
                    userId = "",
                ),
                nativePlugins = nativePlugins,
                useJsonRules = useJsonRules,
            ) ?: return@forEach

            val style = collectDanmakuStyle(filteredItem, nativePlugins, useJsonRules)
            var updated = data.copy(
                content = filteredItem.content,
                startTimeMs = filteredItem.timeMs,
                color = filteredItem.color and 0x00FFFFFF,
            )
            style?.textColor?.let { color ->
                updated = updated.copy(color = color.toArgb() and 0x00FFFFFF)
            }
            if (style != null && abs(style.scale - 1.0f) > 0.01f) {
                updated = updated.copy(
                    fontSize = (updated.fontSize * style.scale).coerceIn(8f, 120f),
                )
            }
            filteredAdvanced.add(updated)
        }

        return Pair(filteredStandard, filteredAdvanced)
    }

    private fun TextData.copyForPluginPipeline(): TextData {
        val copied = if (this is WeightedTextData) {
            WeightedTextData().also {
                it.danmakuId = danmakuId
                it.userHash = userHash
                it.weight = weight
                it.pool = pool
            }
        } else {
            TextData()
        }
        copied.text = text
        copied.showAtTime = showAtTime
        copied.layerType = layerType
        copied.textColor = textColor
        copied.textSize = textSize
        copied.typeface = typeface
        return copied
    }

    private fun TextData.toPluginItem(): DanmakuItem {
        val weighted = this as? WeightedTextData
        val currentColor = textColor ?: 0xFFFFFF
        return DanmakuItem(
            id = weighted?.danmakuId ?: 0L,
            content = text.orEmpty(),
            timeMs = showAtTime,
            type = mapLayerTypeToDanmakuType(layerType),
            color = currentColor and 0x00FFFFFF,
            userId = weighted?.userHash.orEmpty(),
        )
    }

    private fun TextData.applyPluginResult(item: DanmakuItem, style: DanmakuStyle?) {
        text = item.content
        showAtTime = item.timeMs
        layerType = mapDanmakuTypeToLayerType(item.type)
        textColor = (item.color and 0x00FFFFFF) or 0xFF000000.toInt()

        style?.textColor?.let { color -> textColor = color.toArgb() }
        if (style != null && abs(style.scale - 1.0f) > 0.01f) {
            val currentSize = textSize ?: 25f
            textSize = (currentSize * style.scale).coerceIn(12f, 96f)
        }
        typeface = if (style?.bold == true) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun runDanmakuFilters(
        item: DanmakuItem,
        nativePlugins: List<DanmakuPlugin>,
        useJsonRules: Boolean,
    ): DanmakuItem? {
        var current = item
        nativePlugins.forEach { plugin ->
            val filtered = runCatching { plugin.filterDanmaku(current) }
                .getOrElse { error ->
                    Logger.e(TAG, "Danmaku plugin filter failed: ${plugin.name}", error)
                    current
                }
            if (filtered == null) return null
            current = filtered
        }

        if (useJsonRules && !JsonPluginManager.shouldShowDanmaku(current)) {
            return null
        }
        return current
    }

    private fun collectDanmakuStyle(
        item: DanmakuItem,
        nativePlugins: List<DanmakuPlugin>,
        useJsonRules: Boolean,
    ): DanmakuStyle? {
        var style: DanmakuStyle? = null
        nativePlugins.forEach { plugin ->
            val nextStyle = runCatching { plugin.styleDanmaku(item) }
                .onFailure { error -> Logger.e(TAG, "Danmaku plugin style failed: ${plugin.name}", error) }
                .getOrNull()
            style = mergeDanmakuStyle(style, nextStyle)
        }
        if (useJsonRules) {
            style = mergeDanmakuStyle(style, JsonPluginManager.getDanmakuStyle(item))
        }
        return style
    }

    private fun mergeDanmakuStyle(base: DanmakuStyle?, next: DanmakuStyle?): DanmakuStyle? {
        if (base == null) return next
        if (next == null) return base
        return DanmakuStyle(
            textColor = next.textColor ?: base.textColor,
            borderColor = next.borderColor ?: base.borderColor,
            backgroundColor = next.backgroundColor ?: base.backgroundColor,
            bold = base.bold || next.bold,
            scale = if (abs(next.scale - 1.0f) > 0.01f) next.scale else base.scale,
        )
    }

    private fun mapLayerTypeToDanmakuType(layerType: Int): Int {
        return when (layerType) {
            com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER -> 4
            com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER -> 5
            else -> 1
        }
    }

    private fun mapDanmakuTypeToLayerType(type: Int): Int {
        return when (type) {
            4 -> com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER
            5 -> com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER
            else -> com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
        }
    }

    private fun resolveEffectiveDanmakuSettings(
        localSettings: DanmakuSettings,
        filterContext: PlayerDanmakuFilterContext,
    ): DanmakuSettings {
        val serverSetting = filterContext.serverSetting.takeIf { filterContext.followBiliShield }
        return localSettings.copy(
            aiShieldEnabled = localSettings.aiShieldEnabled || (serverSetting?.aiEnabled ?: false),
            aiShieldLevel = normalizeDanmakuAiShieldLevel(
                maxOf(localSettings.aiShieldLevel, serverSetting?.aiLevel ?: 0),
            ),
            allowScroll = localSettings.allowScroll && (serverSetting?.allowScroll ?: true),
            allowTop = localSettings.allowTop && (serverSetting?.allowTop ?: true),
            allowBottom = localSettings.allowBottom && (serverSetting?.allowBottom ?: true),
            allowColor = localSettings.allowColor && (serverSetting?.allowColor ?: true),
            allowSpecial = localSettings.allowSpecial && (serverSetting?.allowSpecial ?: true),
        )
    }

    private fun shouldAllowDanmakuByUserFilter(
        content: String,
        userHash: String?,
        userFilter: DanmakuUserFilter,
    ): Boolean {
        if (userFilter.isEmpty()) return true

        if (userFilter.blockedUserMidHashes.isNotEmpty()) {
            val normalizedHash = userHash?.trim()?.lowercase()
            if (!normalizedHash.isNullOrBlank() && userFilter.blockedUserMidHashes.contains(normalizedHash)) {
                return false
            }
        }

        if (userFilter.keywords.any { keyword -> keyword.isNotBlank() && content.contains(keyword) }) {
            return false
        }

        if (userFilter.regexes.any { regex -> regex.containsMatchIn(content) }) {
            return false
        }

        return true
    }

    private const val TAG = "PlayerDanmakuPipeline"
}

internal fun resolveInitialDanmakuEnabled(): Boolean {
    val context = NetworkModule.appContext ?: return true
    return DanmakuSettingsStore.getSettingsSync(context).enabled
}

internal fun resolveDanmakuSettings(): DanmakuSettings {
    val context = NetworkModule.appContext ?: return DanmakuSettings()
    return DanmakuSettingsStore.getSettingsSync(context)
}
