// 文件路径: feature/video/danmaku/DanmakuManager.kt
package com.android.purebilibili.feature.video.danmaku

import android.content.Context
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.plugin.DanmakuItem
import com.android.purebilibili.core.plugin.DanmakuPlugin
import com.android.purebilibili.core.plugin.DanmakuStyle
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.plugin.json.JsonPluginManager
import com.bytedance.danmaku.render.engine.DanmakuView
import com.bytedance.danmaku.render.engine.control.DanmakuController
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import com.bytedance.danmaku.render.engine.touch.IItemClickListener
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.math.abs

/**
 * 弹幕管理器（单例模式）
 * 
 * 使用 ByteDance DanmakuRenderEngine 重构
 * 
 * 负责：
 * 1. 加载和解析弹幕数据
 * 2. 与 ExoPlayer 同步弹幕播放
 * 3. 管理弹幕视图生命周期
 * 
 * 使用单例模式确保横竖屏切换时保持弹幕状态
 */
class DanmakuManager private constructor(
    private val context: Context,
    initialScope: CoroutineScope
) {
    companion object {
        private const val TAG = "DanmakuManager"
        
        @Volatile
        private var instance: DanmakuManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(context: Context, scope: CoroutineScope): DanmakuManager {
            return instance ?: synchronized(this) {
                instance ?: DanmakuManager(context.applicationContext, scope).also { 
                    instance = it 
                    Log.d(TAG, " DanmakuManager instance created")
                }
            }
        }
        
        /**
         * 更新 CoroutineScope（用于配置变化时）
         */
        fun updateScope(scope: CoroutineScope) {
            instance?.updateScopeInternal(scope)
        }
        
        /**
         * 释放单例实例
         */
        fun clearInstance() {
            instance?.release()
            instance = null
            Log.d(TAG, " DanmakuManager instance cleared")
        }

        fun trimCachesForBackgroundIfPresent() {
            instance?.trimCachesForBackground()
        }
    }

    private var scope: CoroutineScope = createDanmakuManagerScope(initialScope)
    
    // 视图和控制器
    private var danmakuView: DanmakuView? = null
    private var controller: DanmakuController? = null
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var loadJob: Job? = null
    private var syncJob: Job? = null  // ⚙️ [漂移修复] 定期检测漂移
    
    // 弹幕状态
    private var isPlaying = false
    private var isLoading = false
    private var danmakuClickListener: ((String, Long, Long, Boolean) -> Unit)? = null
    
    // 缓存解析后的弹幕数据（横竖屏切换时复用）
    private var cachedDanmakuList: List<DanmakuData>? = null
    private var sourceDanmakuList: List<DanmakuData>? = null
    private var sourceAdvancedDanmakuList: List<AdvancedDanmakuData>? = null
    private var rawDanmakuList: List<DanmakuData>? = null
    // [新增] 高级弹幕数据流
    private val _advancedDanmakuFlow = kotlinx.coroutines.flow.MutableStateFlow<List<AdvancedDanmakuData>>(emptyList())
    val advancedDanmakuFlow: kotlinx.coroutines.flow.StateFlow<List<AdvancedDanmakuData>> = _advancedDanmakuFlow.asStateFlow()
    private var cachedCid: Long = 0L
    private var lastExplicitSeekPositionMs: Long? = null
    private var lastExplicitSeekElapsedRealtimeMs: Long? = null
    
    //  [新增] 记录原始弹幕滚动时间（用于倍速同步）
    private var originalMoveTime: Long = 8000L  // 默认 8 秒
    private var currentVideoSpeed: Float = 1.0f
    private var pluginObserverJob: Job? = null
    private var lastDanmakuPluginUpdateToken: Long = 0L
    private var currentFaceAwareBand: DanmakuDisplayBand? = null
    private val faceBandStabilizer = FaceOcclusionBandStabilizer()
    private var wasBufferingWhilePlaying = false
    
    // 配置
    val config = DanmakuConfig()
    private var blockedRuleMatchers: List<DanmakuBlockRuleMatcher> = emptyList()

    init {
        startDanmakuPluginObserver()
    }
    
    // 便捷属性访问器
    var isEnabled: Boolean
        get() = config.isEnabled
        set(value) {
            config.isEnabled = value
            if (value) show() else hide()
        }
    
    var opacity: Float
        get() = config.opacity
        set(value) {
            config.opacity = value
            applyConfigToController("opacity")
        }
    
    var fontScale: Float
        get() = config.fontScale
        set(value) {
            config.fontScale = value
            applyConfigToController("fontScale")
        }
    
    var speedFactor: Float
        get() = config.speedFactor
        set(value) {
            config.speedFactor = value
            applyConfigToController("speedFactor")
        }
    
    var displayArea: Float
        get() = config.displayAreaRatio
        set(value) {
            config.displayAreaRatio = value
            applyConfigToController("displayArea")
        }

    var allowScrollDanmaku: Boolean
        get() = config.allowScroll
        set(value) {
            config.allowScroll = value
            applyConfigToController("filter_changed")
        }

    var allowTopDanmaku: Boolean
        get() = config.allowTop
        set(value) {
            config.allowTop = value
            applyConfigToController("filter_changed")
        }

    var allowBottomDanmaku: Boolean
        get() = config.allowBottom
        set(value) {
            config.allowBottom = value
            applyConfigToController("filter_changed")
        }

    var allowColorfulDanmaku: Boolean
        get() = config.allowColorful
        set(value) {
            config.allowColorful = value
            applyConfigToController("filter_changed")
        }

    var allowSpecialDanmaku: Boolean
        get() = config.allowSpecial
        set(value) {
            config.allowSpecial = value
            applyConfigToController("filter_changed")
        }

    internal fun updateFaceOcclusion(faceRegions: List<FaceOcclusionRegion>) {
        if (!config.smartOcclusionEnabled) return

        val targetBand = resolveFaceAwareDisplayBand(
            faceRegions = faceRegions,
            defaultBand = DanmakuDisplayBand(0f, config.displayAreaRatio)
        )
        val nextBand = faceBandStabilizer.step(
            detectedBand = targetBand,
            hasFace = faceRegions.isNotEmpty(),
            nowRealtimeMs = SystemClock.elapsedRealtime()
        ) ?: return

        currentFaceAwareBand = nextBand
        config.safeBandTopRatio = nextBand.topRatio
        config.safeBandBottomRatio = nextBand.bottomRatio
        applyConfigToController("face_occlusion")
    }

    private fun updateScopeInternal(newScope: CoroutineScope) {
        val currentDispatcher = scope.coroutineContext[ContinuationInterceptor]
        val nextDispatcher = newScope.coroutineContext[ContinuationInterceptor]
        if (currentDispatcher == nextDispatcher) return
        scope = createDanmakuManagerScope(newScope)
        startDanmakuPluginObserver()
    }

    private fun startDanmakuPluginObserver() {
        pluginObserverJob?.cancel()
        pluginObserverJob = scope.launch {
            PluginManager.danmakuPluginUpdateToken.collect { token ->
                if (token <= 0L || token == lastDanmakuPluginUpdateToken) return@collect
                lastDanmakuPluginUpdateToken = token

                if (isLoading || sourceDanmakuList == null) return@collect

                val rebuilt = withContext(Dispatchers.Default) {
                    rebuildDanmakuCacheFromSource("plugin_update")
                }
                if (!rebuilt) return@collect

                withContext(Dispatchers.Main) {
                    applyCachedDanmakuToController("plugin_update")
                }
            }
        }
    }

    private fun rebuildDanmakuCacheFromSource(reason: String): Boolean {
        val sourceStandard = sourceDanmakuList ?: return false
        val sourceAdvanced = sourceAdvancedDanmakuList ?: emptyList()

        val (pluginFilteredStandardList, pluginFilteredAdvancedList) =
            applyDanmakuPluginPipeline(sourceStandard, sourceAdvanced)
        val (filteredStandardList, filteredAdvancedList) =
            applyDanmakuTypeFilters(pluginFilteredStandardList, pluginFilteredAdvancedList)

        if (filteredStandardList.isEmpty() && filteredAdvancedList.isEmpty()) {
            cachedDanmakuList = emptyList()
            rawDanmakuList = emptyList()
            _advancedDanmakuFlow.value = emptyList()
            Log.w(TAG, " Danmaku cache rebuilt ($reason): no visible items after filtering")
            return false
        }

        rawDanmakuList = filteredStandardList

        if (config.mergeDuplicates) {
            val (mergedStandard, mergedAdvanced) = DanmakuMerger.merge(filteredStandardList)
            cachedDanmakuList = mergedStandard
            val settings = currentTypeFilterSettings()
            val visibleMergedAdvanced = mergedAdvanced.filter { merged ->
                shouldDisplayMergedAdvancedDanmaku(
                    content = merged.content,
                    color = merged.color,
                    settings = settings,
                    blockedMatchers = blockedRuleMatchers
                )
            }
            _advancedDanmakuFlow.value = filteredAdvancedList + visibleMergedAdvanced
        } else {
            cachedDanmakuList = filteredStandardList
            _advancedDanmakuFlow.value = filteredAdvancedList
        }

        Log.w(
            TAG,
            " Danmaku cache rebuilt ($reason): standard=${cachedDanmakuList?.size ?: 0}, advanced=${_advancedDanmakuFlow.value.size}"
        )
        return true
    }

    private fun applyCachedDanmakuToController(reason: String) {
        val currentPos = player?.currentPosition ?: 0L
        val list = cachedDanmakuList ?: emptyList()
        if (list.isEmpty()) {
            controller?.clear()
            isPlaying = false
            Log.w(TAG, " applyCachedDanmakuToController($reason): cleared (empty list)")
            return
        }

        resyncDanmakuTimeline(
            list = list,
            positionMs = currentPos,
            shouldPlay = player?.isPlaying == true,
            invalidateView = true,
            reason = "applyCached:$reason"
        )
        Log.w(TAG, " applyCachedDanmakuToController($reason): size=${list.size}, pos=${currentPos}ms")
    }

    private fun resyncDanmakuTimeline(
        list: List<DanmakuData>,
        positionMs: Long,
        shouldPlay: Boolean,
        invalidateView: Boolean = false,
        reason: String
    ) {
        val ctrl = controller ?: return
        executeExplicitDanmakuResync(
            pause = { ctrl.pause() },
            setData = { ctrl.setData(list, 0) },
            start = { ctrl.start(positionMs) }
        )
        if (invalidateView) {
            ctrl.invalidateView()
        }
        if (shouldPlay && config.isEnabled) {
            isPlaying = true
        } else {
            ctrl.pause()
            isPlaying = false
        }
        Log.w(TAG, " Resynced danmaku timeline ($reason) at ${positionMs}ms, play=$shouldPlay")
    }

    private fun markExplicitSeekResync(positionMs: Long) {
        lastExplicitSeekPositionMs = positionMs
        lastExplicitSeekElapsedRealtimeMs = SystemClock.elapsedRealtime()
    }

    private fun clearExplicitSeekResyncMarker() {
        lastExplicitSeekPositionMs = null
        lastExplicitSeekElapsedRealtimeMs = null
    }

    private fun shouldSuppressFollowupHardResync(positionMs: Long): Boolean {
        return shouldSuppressFollowupDanmakuHardResync(
            positionMs = positionMs,
            explicitSeekPositionMs = lastExplicitSeekPositionMs,
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            explicitSeekElapsedRealtimeMs = lastExplicitSeekElapsedRealtimeMs
        )
    }

    private fun TextData.copyForPluginPipeline(): TextData {
        val copied = if (this is WeightedTextData) {
            WeightedTextData().also {
                it.danmakuId = this.danmakuId
                it.userHash = this.userHash
                it.weight = this.weight
                it.pool = this.pool
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

    private fun applyDanmakuPluginPipeline(
        standardDanmakuList: List<DanmakuData>,
        advancedDanmakuList: List<AdvancedDanmakuData>
    ): Pair<List<DanmakuData>, List<AdvancedDanmakuData>> {
        val nativePlugins = PluginManager.getEnabledDanmakuPlugins()
        val useJsonRules = JsonPluginManager.plugins.value.any { it.enabled && it.plugin.type == "danmaku" }
        if (nativePlugins.isEmpty() && !useJsonRules) {
            return Pair(standardDanmakuList, advancedDanmakuList)
        }

        var filteredStandardCount = 0
        val filteredStandard = ArrayList<DanmakuData>(standardDanmakuList.size)
        standardDanmakuList.forEach { data ->
            val sourceTextData = data as? TextData
            if (sourceTextData == null) {
                filteredStandard.add(data)
                return@forEach
            }
            val textData = sourceTextData.copyForPluginPipeline()

            val sourceItem = textData.toPluginItem()
            val filteredItem = runDanmakuFilters(sourceItem, nativePlugins, useJsonRules)
            if (filteredItem == null) {
                filteredStandardCount++
                return@forEach
            }

            val style = collectDanmakuStyle(filteredItem, nativePlugins, useJsonRules)
            textData.applyPluginResult(filteredItem, style)
            filteredStandard.add(textData)
        }

        var filteredAdvancedCount = 0
        val filteredAdvanced = ArrayList<AdvancedDanmakuData>(advancedDanmakuList.size)
        advancedDanmakuList.forEach { data ->
            val sourceItem = DanmakuItem(
                id = parseAdvancedDanmakuId(data.id),
                content = data.content,
                timeMs = data.startTimeMs,
                type = 7,
                color = data.color and 0x00FFFFFF,
                userId = ""
            )

            val filteredItem = runDanmakuFilters(sourceItem, nativePlugins, useJsonRules)
            if (filteredItem == null) {
                filteredAdvancedCount++
                return@forEach
            }

            val style = collectDanmakuStyle(filteredItem, nativePlugins, useJsonRules)
            var updated = data.copy(
                content = filteredItem.content,
                startTimeMs = filteredItem.timeMs,
                color = filteredItem.color and 0x00FFFFFF
            )
            style?.textColor?.let { color ->
                updated = updated.copy(color = color.toArgb() and 0x00FFFFFF)
            }
            if (style != null && abs(style.scale - 1.0f) > 0.01f) {
                updated = updated.copy(
                    fontSize = (updated.fontSize * style.scale).coerceIn(8f, 120f)
                )
            }
            filteredAdvanced.add(updated)
        }

        if (filteredStandardCount > 0 || filteredAdvancedCount > 0) {
            Log.w(
                TAG,
                " Danmaku plugin filter applied: standard -$filteredStandardCount, advanced -$filteredAdvancedCount"
            )
        }

        return Pair(filteredStandard, filteredAdvanced)
    }

    private fun currentTypeFilterSettings(): DanmakuTypeFilterSettings {
        return DanmakuTypeFilterSettings(
            allowScroll = config.allowScroll,
            allowTop = config.allowTop,
            allowBottom = config.allowBottom,
            allowColorful = config.allowColorful,
            allowSpecial = config.allowSpecial
        )
    }

    private fun applyDanmakuTypeFilters(
        standardDanmakuList: List<DanmakuData>,
        advancedDanmakuList: List<AdvancedDanmakuData>
    ): Pair<List<DanmakuData>, List<AdvancedDanmakuData>> {
        val settings = currentTypeFilterSettings()
        if (
            settings.allowScroll &&
            settings.allowTop &&
            settings.allowBottom &&
            settings.allowColorful &&
            settings.allowSpecial &&
            blockedRuleMatchers.isEmpty()
        ) {
            return Pair(standardDanmakuList, advancedDanmakuList)
        }

        var filteredStandardCount = 0
        var blockedByKeywordStandardCount = 0
        val filteredStandard = standardDanmakuList.filter { data ->
            val textData = data as? TextData ?: return@filter true
            val danmakuType = mapLayerTypeToDanmakuType(textData.layerType)
            val color = textData.textColor ?: 0x00FFFFFF
            val typeVisible = shouldDisplayStandardDanmaku(
                danmakuType = danmakuType,
                color = color,
                settings = settings
            )
            if (!typeVisible) {
                filteredStandardCount++
                return@filter false
            }
            val content = textData.text.orEmpty()
            val blockedByKeyword = shouldBlockDanmakuByMatchers(
                content = content,
                matchers = blockedRuleMatchers
            )
            if (blockedByKeyword) {
                blockedByKeywordStandardCount++
            }
            !blockedByKeyword
        }

        var filteredAdvancedCount = 0
        var blockedByKeywordAdvancedCount = 0
        val filteredAdvanced = advancedDanmakuList.filter { data ->
            val typeVisible = shouldDisplayAdvancedDanmaku(
                color = data.color,
                settings = settings
            )
            if (!typeVisible) {
                filteredAdvancedCount++
                return@filter false
            }
            val blockedByKeyword = shouldBlockDanmakuByMatchers(
                content = data.content,
                matchers = blockedRuleMatchers
            )
            if (blockedByKeyword) {
                blockedByKeywordAdvancedCount++
            }
            !blockedByKeyword
        }

        if (
            filteredStandardCount > 0 ||
            filteredAdvancedCount > 0 ||
            blockedByKeywordStandardCount > 0 ||
            blockedByKeywordAdvancedCount > 0
        ) {
            Log.w(
                TAG,
                " Danmaku filter applied: type standard -$filteredStandardCount, " +
                    "type advanced -$filteredAdvancedCount, " +
                    "keyword standard -$blockedByKeywordStandardCount, " +
                    "keyword advanced -$blockedByKeywordAdvancedCount"
            )
        }
        return Pair(filteredStandard, filteredAdvanced)
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
            userId = weighted?.userHash.orEmpty()
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
            val baseSize = if (currentSize > 0f) currentSize else 25f
            textSize = (baseSize * style.scale).coerceIn(12f, 96f)
        }
        typeface = if (style?.bold == true) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun runDanmakuFilters(
        item: DanmakuItem,
        nativePlugins: List<DanmakuPlugin>,
        useJsonRules: Boolean
    ): DanmakuItem? {
        var current = item
        nativePlugins.forEach { plugin ->
            val filtered = try {
                plugin.filterDanmaku(current)
            } catch (e: Exception) {
                Log.e(TAG, " Danmaku plugin filter failed: ${plugin.name}", e)
                current
            }
            if (filtered == null) return null
            current = filtered
        }

        if (useJsonRules) {
            val shouldShow = try {
                JsonPluginManager.shouldShowDanmaku(current)
            } catch (e: Exception) {
                Log.e(TAG, " JSON danmaku rule filter failed", e)
                true
            }
            if (!shouldShow) return null
        }

        return current
    }

    private fun collectDanmakuStyle(
        item: DanmakuItem,
        nativePlugins: List<DanmakuPlugin>,
        useJsonRules: Boolean
    ): DanmakuStyle? {
        var style: DanmakuStyle? = null
        nativePlugins.forEach { plugin ->
            val next = try {
                plugin.styleDanmaku(item)
            } catch (e: Exception) {
                Log.e(TAG, " Danmaku plugin style failed: ${plugin.name}", e)
                null
            }
            style = mergeDanmakuStyle(style, next)
        }

        if (useJsonRules) {
            val next = try {
                JsonPluginManager.getDanmakuStyle(item)
            } catch (e: Exception) {
                Log.e(TAG, " JSON danmaku rule style failed", e)
                null
            }
            style = mergeDanmakuStyle(style, next)
        }

        return style
    }

    private fun mergeDanmakuStyle(base: DanmakuStyle?, incoming: DanmakuStyle?): DanmakuStyle? {
        if (base == null) return incoming
        if (incoming == null) return base
        return DanmakuStyle(
            textColor = incoming.textColor ?: base.textColor,
            borderColor = incoming.borderColor ?: base.borderColor,
            backgroundColor = incoming.backgroundColor ?: base.backgroundColor,
            bold = base.bold || incoming.bold,
            scale = if (abs(incoming.scale - 1.0f) > 0.01f) incoming.scale else base.scale
        )
    }

    private fun mapLayerTypeToDanmakuType(layerType: Int): Int = when (layerType) {
        LAYER_TYPE_BOTTOM_CENTER -> 4
        LAYER_TYPE_TOP_CENTER -> 5
        else -> 1
    }

    private fun mapDanmakuTypeToLayerType(type: Int): Int = when (type) {
        4 -> LAYER_TYPE_BOTTOM_CENTER
        5 -> LAYER_TYPE_TOP_CENTER
        else -> LAYER_TYPE_SCROLL
    }

    private fun parseAdvancedDanmakuId(rawId: String): Long {
        return rawId.toLongOrNull()
            ?: rawId.filter { it.isDigit() }.toLongOrNull()
            ?: 0L
    }


    /**
     *  批量更新弹幕设置（实时生效）
     */
    fun updateSettings(
        opacity: Float = this.opacity,
        fontScale: Float = this.fontScale,
        speed: Float = this.speedFactor,
        displayArea: Float = this.displayArea,
        mergeDuplicates: Boolean = config.mergeDuplicates,
        allowScroll: Boolean = config.allowScroll,
        allowTop: Boolean = config.allowTop,
        allowBottom: Boolean = config.allowBottom,
        allowColorful: Boolean = config.allowColorful,
        allowSpecial: Boolean = config.allowSpecial,
        blockedRules: List<String> = config.blockedRules,
        smartOcclusion: Boolean = config.smartOcclusionEnabled
    ) {
        val mergeChanged = config.mergeDuplicates != mergeDuplicates
        val blockedRulesChanged = config.blockedRules != blockedRules
        val filterChanged =
            config.allowScroll != allowScroll ||
                config.allowTop != allowTop ||
                config.allowBottom != allowBottom ||
                config.allowColorful != allowColorful ||
                config.allowSpecial != allowSpecial ||
                blockedRulesChanged
        val occlusionChanged = config.smartOcclusionEnabled != smartOcclusion
        
        config.opacity = opacity
        config.fontScale = fontScale
        config.speedFactor = speed
        config.displayAreaRatio = displayArea
        config.mergeDuplicates = mergeDuplicates
        config.allowScroll = allowScroll
        config.allowTop = allowTop
        config.allowBottom = allowBottom
        config.allowColorful = allowColorful
        config.allowSpecial = allowSpecial
        config.blockedRules = blockedRules
        config.smartOcclusionEnabled = smartOcclusion
        if (blockedRulesChanged) {
            blockedRuleMatchers = compileDanmakuBlockRules(blockedRules)
        }

        if (occlusionChanged) {
            if (smartOcclusion) {
                currentFaceAwareBand = DanmakuDisplayBand(0f, config.displayAreaRatio)
                config.safeBandTopRatio = currentFaceAwareBand?.topRatio ?: 0f
                config.safeBandBottomRatio = currentFaceAwareBand?.bottomRatio ?: config.displayAreaRatio
                faceBandStabilizer.reset(
                    defaultBand = currentFaceAwareBand,
                    nowRealtimeMs = SystemClock.elapsedRealtime()
                )
            } else {
                currentFaceAwareBand = null
                config.safeBandTopRatio = 0f
                config.safeBandBottomRatio = 1f
                faceBandStabilizer.reset()
            }
        }
        
        if (mergeChanged || filterChanged || occlusionChanged) {
            val reason = if (mergeChanged) "merge_changed" else "filter_changed"
            val resolvedReason = if (occlusionChanged) "smart_occlusion_toggle" else reason
            applyConfigToController(resolvedReason)
        } else {
            applyConfigToController("batch")
        }
    }

    /**
     * 应用弹幕配置到 Controller，并同步倍速基准
     *  [修复] fontScale/displayArea 改变时重新设置数据，让新配置生效
     */
    private fun applyConfigToController(reason: String) {
        controller?.let { ctrl ->
            val viewHeight = danmakuView?.height ?: 0
            config.applyTo(ctrl.config, viewHeight)

            // 记录设置后的基准滚动时间，供倍速同步使用
            originalMoveTime = ctrl.config.scroll.moveTime

            // 若视频非 1.0x，则按倍速调整弹幕滚动时间
            if (currentVideoSpeed != 1.0f) {
                ctrl.config.scroll.moveTime = (originalMoveTime / currentVideoSpeed).toLong()
            }

            //  [关键修复] fontScale/displayArea/viewHeight 改变时，需要重新设置弹幕数据
            // 因为引擎的 config.text.size 只对新弹幕生效，已显示的弹幕不会更新
            if (reason == "fontScale" || reason == "displayArea" || reason == "batch" || reason == "resize" || reason == "merge_changed" || reason == "filter_changed" || reason == "smart_occlusion_toggle") {
                // 如果是合并状态改变，需要重新计算 cachedList
                if (reason == "merge_changed" || reason == "filter_changed") {
                    rebuildDanmakuCacheFromSource(reason)
                }
            
                cachedDanmakuList?.let { list ->
                    val currentPos = player?.currentPosition ?: 0L
                    Log.w(TAG, " Re-applying danmaku data after $reason change at ${currentPos}ms")
                    resyncDanmakuTimeline(
                        list = list,
                        positionMs = currentPos,
                        shouldPlay = player?.isPlaying == true,
                        reason = "config:$reason"
                    )
                }
            } else {
                ctrl.invalidateView()
            }
            
            Log.w(
                TAG,
                " Config applied ($reason): opacity=${config.opacity}, fontScale=${config.fontScale}, " +
                    "speed=${config.speedFactor}, area=${config.displayAreaRatio}, " +
                    "smartOcclusion=${config.smartOcclusionEnabled}, band=${config.safeBandTopRatio}-${config.safeBandBottomRatio}, " +
                    "allowScroll=${config.allowScroll}, allowTop=${config.allowTop}, allowBottom=${config.allowBottom}, " +
                    "allowColorful=${config.allowColorful}, allowSpecial=${config.allowSpecial}, " +
                    "baseMoveTime=$originalMoveTime, videoSpeed=$currentVideoSpeed, " +
                    "moveTime=${ctrl.config.scroll.moveTime}"
            )
        }
    }
    
    //  [新增] 记录上次应用的视图尺寸，用于检测横竖屏切换
    private var lastAppliedWidth: Int = 0
    private var lastAppliedHeight: Int = 0
    
    /**
     * 绑定 DanmakuView
     * 
     *  [修复] 支持横竖屏切换时重新应用弹幕数据
     * 当同一个视图的尺寸发生变化时，也会重新设置弹幕数据
     */
    fun attachView(view: DanmakuView) {
        // 使用 Log.w (warning) 确保日志可见
        Log.w(TAG, "========== attachView CALLED ==========")
        Log.w(TAG, "📎 View size: width=${view.width}, height=${view.height}, lastApplied=${lastAppliedWidth}x${lastAppliedHeight}")
        
        //  [关键修复] 如果是同一个视图但尺寸发生变化（横竖屏切换），也需要重新应用弹幕数据
        val isSameView = danmakuView === view
        val sizeChanged = view.width != lastAppliedWidth || view.height != lastAppliedHeight
        val hasValidSize = view.width > 0 && view.height > 0
        
        if (isSameView && !sizeChanged && hasValidSize) {
            Log.w(TAG, "📎 attachView: Same view, same size, skipping")
            return
        }
        
        if (isSameView && sizeChanged && hasValidSize) {
            Log.w(TAG, "📎 attachView: Same view but size changed (rotation?), re-applying danmaku data")
            lastAppliedWidth = view.width
            lastAppliedHeight = view.height
            // [修复] 尺寸变化时，重新应用配置（计算行数）和数据
            applyConfigToController("resize")
            return
        }
        
        Log.w(TAG, "📎 attachView: new view, old=${danmakuView != null}, hashCode=${view.hashCode()}")
        
        danmakuView = view
        controller = view.controller
        applyDanmakuClickListener()
        
        Log.w(TAG, "📎 controller obtained: ${controller != null}")
        
        // 内置渲染层（ScrollLayer, TopCenterLayer, BottomCenterLayer）由 DanmakuRenderEngine 自动注册
        // 不需要手动添加，手动添加会报错 "The custom LayerType must not be less than 2000"
        
        // 应用配置并同步倍速基准
        applyConfigToController("attachView")
        
        //  [关键修复] 等待 View 布局完成后再设置弹幕数据
        // DanmakuRenderEngine 需要有效的 View 尺寸来计算弹幕轨道位置
        if (hasValidSize) {
            // View 已经有有效尺寸，直接设置数据
            Log.w(TAG, "📎 View has valid size, setting data immediately")
            lastAppliedWidth = view.width
            lastAppliedHeight = view.height
            // [修复] 立即应用正确的配置（含高度）和数据
            applyConfigToController("resize")
        } else {
            // View 尺寸为 0，等待布局完成
            Log.w(TAG, "📎 View size is 0, waiting for layout...")
            view.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // 移除监听器，避免重复回调
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    
                    Log.w(TAG, "📎 Layout callback! width=${view.width}, height=${view.height}")
                    
                    // 确保 View 仍然是当前绑定的 View
                    if (danmakuView === view && view.width > 0 && view.height > 0) {
                        lastAppliedWidth = view.width
                        lastAppliedHeight = view.height
                        // [修复] 布局完成后，重新应用配置（含高度）和数据
                        applyConfigToController("resize")
                    } else if (danmakuView === view) {
                        //  [修复] 如果布局回调时尺寸仍为 0，延迟 100ms 再试一次
                        Log.w(TAG, " View still zero size, scheduling delayed retry...")
                        view.postDelayed({
                            if (danmakuView === view && view.width > 0 && view.height > 0) {
                                Log.w(TAG, "📎 Delayed retry: width=${view.width}, height=${view.height}")
                                lastAppliedWidth = view.width
                                lastAppliedHeight = view.height
                                applyConfigToController("resize")
                            } else {
                                Log.w(TAG, " View still invalid after delay, skipping")
                            }
                        }, 100)
                    } else {
                        Log.w(TAG, " View changed, skipping setData")
                    }
                }
            })
        }
        
        Log.w(TAG, "========== attachView COMPLETED ==========")
    }
    
    /**
     * 将缓存的弹幕数据应用到 controller（内部方法）
     */
    private fun applyDanmakuDataToController() {
        Log.w(TAG, "📎 cachedDanmakuList is null? ${cachedDanmakuList == null}, size=${cachedDanmakuList?.size ?: 0}")
        cachedDanmakuList?.let { list ->
            //  [修复] 始终用 playTime=0 设置数据，因为弹幕的 showAtTime 是相对于视频开头的
            Log.w(TAG, "📎 Calling setData with ${list.size} items, playTime=0 (base reference)")
            player?.let { p ->
                val position = p.currentPosition
                Log.w(TAG, "📎 Player state: isPlaying=${p.isPlaying}, isEnabled=${config.isEnabled}, position=${position}ms")
                resyncDanmakuTimeline(
                    list = list,
                    positionMs = position,
                    shouldPlay = p.isPlaying,
                    invalidateView = true,
                    reason = "applyDanmakuData"
                )
            } ?: Log.w(TAG, "📎 Player is null, not syncing")
        } ?: Log.w(TAG, "📎 No cached danmaku list to apply")
    }
    
    /**
     * 解绑 DanmakuView（不释放弹幕数据）
     */
    fun detachView() {
        Log.d(TAG, "📎 detachView: Pausing and clearing controller")
        controller?.pause()
        controller = null
        danmakuView = null
    }
    
    /**
     * ⚙️ [漂移修复] 启动定期漂移检测
     * 根据倍速动态调整检测频率；非 1.0x 周期性强制重建时间轴
     */
    private fun startDriftSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                delay(resolveDanmakuDriftSyncIntervalMs(currentVideoSpeed))
                player?.let { p ->
                    if (p.isPlaying && config.isEnabled && isPlaying) {
                        val playerPos = p.currentPosition
                        tickCount++
                        when (
                            resolveDanmakuGuardAction(
                                videoSpeed = currentVideoSpeed,
                                tickCount = tickCount,
                                danmakuEnabled = config.isEnabled,
                                isPlaying = isPlaying,
                                hasData = cachedDanmakuList != null
                            )
                        ) {
                            DanmakuSyncAction.HardResync -> {
                                cachedDanmakuList?.let { list ->
                                    resyncDanmakuTimeline(
                                        list = list,
                                        positionMs = playerPos,
                                        shouldPlay = true,
                                        reason = "drift_sync"
                                    )
                                }
                            }
                            DanmakuSyncAction.None,
                            DanmakuSyncAction.PauseOnly -> Unit
                        }
                        Log.d(
                            TAG,
                            "⚙️ Drift sync at ${playerPos}ms speed=$currentVideoSpeed tick=$tickCount"
                        )
                    }
                }
            }
        }
        Log.d(TAG, "⚙️ Drift sync started")
    }
    
    /**
     * ⚙️ [漂移修复] 停止定期漂移检测
     */
    private fun stopDriftSync() {
        syncJob?.cancel()
        syncJob = null
        Log.d(TAG, "⚙️ Drift sync stopped")
    }
    
    /**
     * 绑定 ExoPlayer
     * 
     * [修复] 添加同一播放器实例检查，避免重复绑定
     * 当从其他视频返回时，需要重新绑定当前播放器
     */
    fun attachPlayer(exoPlayer: ExoPlayer) {
        Log.d(TAG, " attachPlayer: new=${exoPlayer.hashCode()}, old=${player?.hashCode()}")
        
        // [修复] 移除"同一播放器跳过"的逻辑
        // 原因：在 Navigation 切换视频后返回时，虽然 player 实例相同，
        // 但 DanmakuManager 的 playerListener 可能已被其他页面的 player 替换。
        // 必须重新绑定以确保当前 player 的事件能被正确处理。
        
        // 移除旧监听器（无论是同一播放器还是不同播放器）
        playerListener?.let { 
            player?.removeListener(it)
            Log.d(TAG, " Removed old listener from player ${player?.hashCode()}")
        }
        
        player = exoPlayer
        currentVideoSpeed = exoPlayer.playbackParameters.speed.coerceAtLeast(0.1f)
        controller?.let { ctrl ->
            ctrl.config.scroll.moveTime = (originalMoveTime / currentVideoSpeed).toLong()
        }
        
        // 🎬 [根本修复] 不在这里启动帧同步，而是在 onIsPlayingChanged 中启动
        
        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayerPlaying: Boolean) {
                Log.w(TAG, " onIsPlayingChanged: isPlaying=$isPlayerPlaying, isEnabled=${config.isEnabled}, hasData=${cachedDanmakuList != null}")

                when (
                    resolveDanmakuActionForIsPlayingChange(
                        isPlayerPlaying = isPlayerPlaying,
                        danmakuEnabled = config.isEnabled,
                        hasData = cachedDanmakuList != null
                    )
                ) {
                    DanmakuSyncAction.HardResync -> {
                        val position = exoPlayer.currentPosition
                        val shouldSuppressResync = shouldSuppressFollowupHardResync(position)
                        cachedDanmakuList?.let { list ->
                            if (shouldSuppressResync) {
                                Log.w(TAG, " Skip duplicate danmaku hard resync after explicit seek at ${position}ms (is_playing_changed)")
                            } else {
                                resyncDanmakuTimeline(
                                    list = list,
                                    positionMs = position,
                                    shouldPlay = true,
                                    reason = "is_playing_changed"
                                )
                            }
                        }
                        isPlaying = true
                        wasBufferingWhilePlaying = false
                        startDriftSync()
                        if (shouldSuppressResync) {
                            clearExplicitSeekResyncMarker()
                        }
                        Log.w(TAG, " Danmaku HARD RESYNC at ${position}ms with frame sync")
                    }
                    DanmakuSyncAction.PauseOnly -> {
                        controller?.pause()
                        isPlaying = false
                        stopDriftSync()
                        Log.w(TAG, " Danmaku PAUSED (danmakus stay in place)")
                    }
                    DanmakuSyncAction.None -> {
                        if (isPlayerPlaying) {
                            Log.w(TAG, " Player playing but danmaku data not loaded/enabled yet, will sync after load")
                        }
                    }
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG, " onPlaybackStateChanged: state=$playbackState")
                when (
                    resolveDanmakuActionForPlaybackState(
                        playbackState = playbackState,
                        isPlayerPlaying = exoPlayer.isPlaying,
                        danmakuEnabled = config.isEnabled,
                        hasData = cachedDanmakuList != null,
                        resumedFromBuffering = wasBufferingWhilePlaying
                    )
                ) {
                    DanmakuSyncAction.HardResync -> {
                        val position = exoPlayer.currentPosition
                        val shouldSuppressResync = shouldSuppressFollowupHardResync(position)
                        cachedDanmakuList?.let { list ->
                            if (shouldSuppressResync) {
                                Log.w(TAG, " Skip duplicate danmaku hard resync after explicit seek at ${position}ms (state_ready_resume)")
                            } else {
                                resyncDanmakuTimeline(
                                    list = list,
                                    positionMs = position,
                                    shouldPlay = true,
                                    reason = "state_ready_resume"
                                )
                            }
                        }
                        isPlaying = true
                        wasBufferingWhilePlaying = false
                        startDriftSync()
                        if (shouldSuppressResync) {
                            clearExplicitSeekResyncMarker()
                        }
                    }
                    DanmakuSyncAction.PauseOnly -> {
                        if (playbackState == Player.STATE_BUFFERING) {
                            wasBufferingWhilePlaying = isPlaying
                        } else {
                            wasBufferingWhilePlaying = false
                        }
                        controller?.pause()
                        if (playbackState == Player.STATE_ENDED) {
                            isPlaying = false
                            stopDriftSync()
                        }
                        if (playbackState == Player.STATE_BUFFERING) {
                            Log.d(TAG, " Buffering, danmaku paused")
                        }
                    }
                    DanmakuSyncAction.None -> {
                        if (playbackState != Player.STATE_BUFFERING) {
                            wasBufferingWhilePlaying = false
                        }
                    }
                }
            }
            
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (
                    resolveDanmakuActionForPositionDiscontinuity(
                        reason = reason,
                        hasData = cachedDanmakuList != null
                    ) == DanmakuSyncAction.HardResync
                ) {
                    Log.w(TAG, " Seek detected: ${oldPosition.positionMs}ms -> ${newPosition.positionMs}ms")
                    if (shouldSuppressFollowupHardResync(newPosition.positionMs)) {
                        Log.w(TAG, " Skip duplicate danmaku hard resync after explicit seek at ${newPosition.positionMs}ms (seek_discontinuity)")
                    } else {
                        //  关键修复：Seek 时重新调用 setData(list, 0) + start(newPosition)
                        cachedDanmakuList?.let { list ->
                            Log.w(TAG, " Re-setting data with playTime=0, then start at ${newPosition.positionMs}ms")
                            resyncDanmakuTimeline(
                                list = list,
                                positionMs = newPosition.positionMs,
                                shouldPlay = exoPlayer.isPlaying,
                                reason = "seek_discontinuity"
                            )
                            Log.w(TAG, " Danmaku resynced at ${newPosition.positionMs}ms")
                        } ?: run {
                            controller?.clear()
                            Log.w(TAG, " No cached danmaku, just cleared screen")
                        }
                    }
                }
            }
            
            //  [新增] 视频倍速变化时同步弹幕速度
            //  [问题10修复] 优化长按加速视频时的弹幕同步
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                val videoSpeed = playbackParameters.speed.coerceAtLeast(0.1f)
                Log.w(TAG, "⏩ onPlaybackParametersChanged: videoSpeed=$videoSpeed, previous=$currentVideoSpeed")
                
                //  同步弹幕速度：视频 2x 时，弹幕也需要 2 倍速滚动
                // 通过减少 moveTime 来加快弹幕滚动
                if (videoSpeed != currentVideoSpeed) {
                    val previousSpeed = currentVideoSpeed
                    currentVideoSpeed = videoSpeed
                    
                    controller?.let { ctrl ->
                        // 根据视频倍速调整弹幕滚动时间
                        // 视频 2x 倍速 = 弹幕滚动时间减半
                        val adjustedMoveTime = (originalMoveTime / videoSpeed).toLong()
                        ctrl.config.scroll.moveTime = adjustedMoveTime
                        
                        if (
                            resolveDanmakuActionForPlaybackSpeedChange(
                                previousSpeed = previousSpeed,
                                newSpeed = videoSpeed,
                                isPlayerPlaying = exoPlayer.isPlaying,
                                hasData = cachedDanmakuList != null
                            ) == DanmakuSyncAction.HardResync
                        ) {
                            val currentPos = exoPlayer.currentPosition
                            Log.w(TAG, "⏩ Speed changed, resyncing danmaku at ${currentPos}ms")
                            cachedDanmakuList?.let { list ->
                                resyncDanmakuTimeline(
                                    list = list,
                                    positionMs = currentPos,
                                    shouldPlay = exoPlayer.isPlaying,
                                    reason = "speed_change"
                                )
                            }
                        }
                        
                        ctrl.invalidateView()
                        Log.w(TAG, "⏩ Danmaku moveTime: original=$originalMoveTime, adjusted=$adjustedMoveTime (video=${videoSpeed}x)")
                    }
                }
            }
        }
        
        exoPlayer.addListener(playerListener!!)
    }
    
    /**
     * 加载弹幕数据
     * 
     * @param cid 视频 cid
     * @param aid 视频 aid (用于获取弹幕高级元数据)
     * @param durationMs 视频时长 (毫秒)，用于计算 Protobuf 分段数。如果为 0，则回退到 XML API
     */
    fun loadDanmaku(cid: Long, aid: Long, durationMs: Long = 0L) {
        Log.w(TAG, "========== loadDanmaku CALLED cid=$cid, aid=$aid, duration=${durationMs}ms ==========")
        Log.w(TAG, " loadDanmaku: cid=$cid, cached=$cachedCid, isLoading=$isLoading, controller=${controller != null}")
        
        // 如果正在加载，优先处理新 cid
        if (isLoading) {
            if (cid != cachedCid) {
                Log.w(TAG, " Loading in progress for cid=$cachedCid, canceling to load cid=$cid")
                loadJob?.cancel()
                isLoading = false
            } else {
                Log.w(TAG, " Already loading same cid=$cid, skipping")
                return
            }
        }
        
        // 如果是同一个 cid 且已有缓存数据，直接使用（横竖屏切换场景）
        if (cid == cachedCid && cachedDanmakuList != null) {
            val currentPos = player?.currentPosition ?: 0L
            Log.w(TAG, " Using cached danmaku list (${cachedDanmakuList!!.size} items) for cid=$cid, position=${currentPos}ms")

            //  [修复] 显式重同步要先 pause 再 start，避免引擎在播放中忽略 start()
            resyncDanmakuTimeline(
                list = cachedDanmakuList!!,
                positionMs = currentPos,
                shouldPlay = player?.isPlaying == true,
                reason = "load_cached"
            )
            Log.w(TAG, " Cached data: setData(0) + start(${currentPos}ms)")
            return
        }
        
        // 需要从网络加载新 cid 的弹幕
        Log.w(TAG, " loadDanmaku: New cid=$cid, loading from network")
        isLoading = true
        cachedCid = cid
        clearExplicitSeekResyncMarker()
        cachedDanmakuList = null
        sourceDanmakuList = null
        sourceAdvancedDanmakuList = null
        _advancedDanmakuFlow.value = emptyList()
        
        // 清除现有弹幕
        controller?.stop()
        
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                // 1. 获取弹幕元数据 (High-Energy, Command Dms)
                var commandDmList: List<AdvancedDanmakuData> = emptyList()
                val viewReply = if (aid > 0) {
                     com.android.purebilibili.data.repository.DanmakuRepository.getDanmakuView(cid, aid)
                } else null
                
                if (viewReply != null) {
                    Log.w(TAG, " Got Danmaku Metadata: count=${viewReply.count}, segments=${viewReply.dmSge?.total ?: "N/A"}")
                    
                    // 处理 Command Dms (如高能进度条提示, 互动弹幕)
                    if (viewReply.commandDms.isNotEmpty()) {
                        commandDmList = viewReply.commandDms.mapNotNull { cmd ->
                            buildCommandDanmaku(cmd)
                        }
                        Log.w(
                            TAG,
                            " Converted ${commandDmList.size}/${viewReply.commandDms.size} Command Dms to AdvancedDanmakuData"
                        )
                    }
                    
                    // TODO: specialDms 通常是 URL 列表，需要额外下载解析，暂跳过
                }
                
                val (segments, rawData) = withContext(Dispatchers.IO) {
                    var segmentList: List<ByteArray>? = null
                    var xmlData: ByteArray? = null
                    
                    //  [新增] 优先使用 Protobuf API (seg.so)
                    if (durationMs > 0 || viewReply != null) {
                        Log.w(TAG, " Trying Protobuf API (seg.so)...")
                        try {
                            val fetched = com.android.purebilibili.data.repository.DanmakuRepository.getDanmakuSegments(
                                cid = cid,
                                durationMs = durationMs,
                                metadataSegmentCount = viewReply?.dmSge?.total?.toInt()
                            )
                            if (fetched.isNotEmpty()) {
                                segmentList = fetched
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, " Protobuf API failed: ${e.message}, falling back to XML")
                        }
                    }
                    
                    //  [后备] 如果 Protobuf 失败或未提供 duration，使用 XML API
                    if (segmentList.isNullOrEmpty()) {
                        Log.w(TAG, " Trying XML API (fallback)...")
                        xmlData = com.android.purebilibili.data.repository.DanmakuRepository.getDanmakuRawData(cid)
                    }
                    
                    Pair(segmentList, xmlData)
                }
                
                val parsedResult = withContext(Dispatchers.Default) {
                    when {
                        !segments.isNullOrEmpty() -> {
                            val parsed = DanmakuParser.parseProtobuf(segments)
                            Log.w(TAG, " Protobuf parsed: Standard=${parsed.standardList.size}, Advanced=${parsed.advancedList.size}")
                            parsed
                        }
                        rawData != null && rawData.isNotEmpty() -> {
                            val parsed = DanmakuParser.parse(rawData)
                            Log.w(TAG, " XML parsed: Standard=${parsed.standardList.size}, Advanced=${parsed.advancedList.size}")
                            parsed
                        }
                        else -> ParsedDanmaku(emptyList(), emptyList())
                    }
                }
                
                sourceDanmakuList = parsedResult.standardList
                sourceAdvancedDanmakuList = parsedResult.advancedList + commandDmList

                val rebuilt = withContext(Dispatchers.Default) {
                    rebuildDanmakuCacheFromSource("load")
                }

                if (!rebuilt) {
                    Log.w(TAG, " No danmaku data available for cid=$cid")
                    withContext(Dispatchers.Main) {
                        isLoading = false
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    isLoading = false
                    
                    //  [核心修复] 仿照 Seek 处理器的模式
                    val currentPlayTime = player?.currentPosition ?: 0L
                    Log.w(TAG, "📎 View size: width=${danmakuView?.width}, height=${danmakuView?.height}")
                    
                    //  [核心修复] 先用 0 作为基准设置数据，再用实际位置启动
                    // 这与 Seek 处理器的模式一致，确保引擎知道完整的时间线
                    // 注意：这里必须使用缓存后的最终列表（可能已经去重合并）
                    val finalList = cachedDanmakuList ?: emptyList()
                    if (finalList.isEmpty()) {
                        controller?.clear()
                        isPlaying = false
                        Log.w(TAG, "📎 Final danmaku list empty after rebuild, cleared controller")
                        return@withContext
                    }
                    Log.w(TAG, "📎 Calling setData with ${finalList.size} items, playTime=0 (base)")
                    resyncDanmakuTimeline(
                        list = finalList,
                        positionMs = currentPlayTime,
                        shouldPlay = player?.isPlaying == true,
                        invalidateView = true,
                        reason = "load_new"
                    )
                    Log.w(TAG, " controller synced to $currentPlayTime ms")
                }
            } catch (e: Exception) {
                Log.e(TAG, " Failed to load danmaku for cid=$cid: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }
    
    fun show() {
        Log.d(TAG, "👁️ show()")
        danmakuView?.visibility = android.view.View.VISIBLE
        
        if (player?.isPlaying == true) {
            cachedDanmakuList?.let { list ->
                resyncDanmakuTimeline(
                    list = list,
                    positionMs = player?.currentPosition ?: 0L,
                    shouldPlay = true,
                    invalidateView = true,
                    reason = "show"
                )
            }
        }
    }
    
    fun hide() {
        Log.d(TAG, "🙈 hide()")
        controller?.pause()
        danmakuView?.visibility = android.view.View.GONE
        isPlaying = false
    }
    
    /**
     *  清除当前显示的弹幕（拖动进度条时调用）
     */
    fun clear() {
        Log.d(TAG, "🧹 clear() - clearing displayed danmakus")
        controller?.clear()
    }
    
    /**
     *  跳转到指定时间（拖动进度条完成时调用）
     * 会清除当前弹幕并从新位置开始显示
     * 
     * @param positionMs 目标位置（毫秒）
     */
    fun seekTo(positionMs: Long) {
        Log.w(TAG, "⏭️ seekTo($positionMs) - refreshing danmaku")
        markExplicitSeekResync(positionMs)
        cachedDanmakuList?.let { list ->
            resyncDanmakuTimeline(
                list = list,
                positionMs = positionMs,
                shouldPlay = player?.isPlaying == true,
                reason = "manual_seek"
            )
            Log.w(TAG, "⏭️ Danmaku restarted at ${positionMs}ms")
        } ?: run {
            controller?.clear()
            Log.w(TAG, "⏭️ No cached danmaku, just cleared")
        }
    }
    
    /**
     * [新增] 添加本地弹幕（发送成功后立即显示）
     * 
     * 此方法用于在用户发送弹幕后立即将其显示在屏幕上，
     * 无需等待服务器刷新弹幕列表。
     * 
     * @param text 弹幕内容
     * @param color 弹幕颜色 (十进制 RGB，默认白色 16777215)
     * @param mode 弹幕模式: 1=滚动(默认), 4=底部, 5=顶部
     * @param fontSize 字号: 18=小, 25=中(默认), 36=大
     */
    fun addLocalDanmaku(
        text: String,
        color: Int = 16777215,
        mode: Int = 1,
        fontSize: Int = 25
    ) {
        val currentPosition = player?.currentPosition ?: run {
            Log.w(TAG, "📝 addLocalDanmaku: player is null, cannot add danmaku")
            return
        }
        
        Log.d(TAG, "📝 addLocalDanmaku: text=$text, color=$color, mode=$mode, fontSize=$fontSize, position=${currentPosition}ms")
        
        // 使用 TextData (DanmakuData 的具体实现)
        val danmakuData = com.bytedance.danmaku.render.engine.render.draw.text.TextData().apply {
            //  [修复] 设置显示时间为当前播放位置 + 100ms 偏移
            // 这确保弹幕不会因为"已经过去"而被跳过
            showAtTime = currentPosition + 100L
            
            // 设置弹幕内容 - [修改] 使用『』包裹作为标记，更美观
            this.text = "『 $text 』"
            
            // 设置颜色 (ARGB 格式)
            textColor = color or 0xFF000000.toInt()
            
            // 尝试设置边框/背景
            try {
                val greenBorder = 0xFF4CAF50.toInt()
                val clazz = this::class.java
                
                // 尝试多个可能的字段名 - 希望能命中一个
                // 1. borderColor (边框颜色)
                // 2. strokeColor (可能是文字描边，也可能是框) -> 先前尝试未生效或被覆盖
                // 3. backgroundColor (背景色)
                val fieldNames = listOf("borderColor", "backgroundColor", "backColor", "padding")
                
                for (name in fieldNames) {
                    try {
                        val field = clazz.getDeclaredField(name)
                        field.isAccessible = true
                        
                        if (name == "padding") {
                             field.setFloat(this, 10f)
                        } else {
                             field.setInt(this, greenBorder)
                        }
                        Log.d(TAG, "📝 Reflex set $name to Green/Value")
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            } catch (e: Exception) {
                // 忽略
            }
            
            // 设置弹幕类型 - 使用库的常量
            layerType = when (mode) {
                4 -> com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER  // 底部
                5 -> com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER     // 顶部
                else -> com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL      // 滚动 (默认)
            }
        }
        
        // 添加到缓存列表并排序
        // [核心修复] 必须按时间排序！渲染引擎依赖顺序数据，乱序会导致弹幕无法显示
        cachedDanmakuList = (cachedDanmakuList ?: emptyList()).plus(danmakuData).sortedBy { it.showAtTime }
        sourceDanmakuList = (sourceDanmakuList ?: emptyList()).plus(danmakuData).sortedBy { it.showAtTime }
        Log.d(TAG, "📝 Added to cache and sorted, total: ${cachedDanmakuList?.size} danmakus")
        
        // 立即显示（通过重新设置数据并跳到当前位置）
        cachedDanmakuList?.let { list ->
            Log.d(TAG, "📝 Calling setData with ${list.size} items")
            resyncDanmakuTimeline(
                list = list,
                positionMs = currentPosition,
                shouldPlay = player?.isPlaying == true,
                invalidateView = true,
                reason = "add_local"
            )
        }
        
        Log.d(TAG, "📝 Local danmaku added and displayed")
    }
    
    /**
     * 清除视图引用（防止内存泄漏）
     */
    fun clearViewReference() {
        Log.d(TAG, " clearViewReference: Clearing all references")
        
        // 移除播放器监听器
        playerListener?.let { listener ->
            player?.removeListener(listener)
        }
        playerListener = null
        player = null
        
        // 停止弹幕
        controller?.stop()
        controller = null
        danmakuView = null
        
        //  [修复] 重置尺寸记录
        lastAppliedWidth = 0
        lastAppliedHeight = 0
        
        // 取消加载任务
        loadJob?.cancel()
        loadJob = null
        
        // 🎬 [根本修复] 停止帧级同步
        stopDriftSync()
        
        isPlaying = false
        isLoading = false
        clearExplicitSeekResyncMarker()
        
        Log.d(TAG, " All references cleared")
    }

    fun trimCachesForBackground() {
        Log.d(TAG, " trimCachesForBackground: dropping parsed danmaku caches")
        cachedDanmakuList = null
        sourceDanmakuList = null
        sourceAdvancedDanmakuList = null
        rawDanmakuList = null
        _advancedDanmakuFlow.value = emptyList()
        controller?.clear()
    }

    /**
     * 设置弹幕点击监听器
     *
     * @param listener 回调函数，参数为 (text, dmid, uid, isSelf)
     */
    fun setOnDanmakuClickListener(listener: (String, Long, Long, Boolean) -> Unit) {
        danmakuClickListener = listener
        applyDanmakuClickListener()
    }

    private fun applyDanmakuClickListener() {
        val callback = danmakuClickListener ?: return
        controller?.let { ctrl ->
            try {
                ctrl.itemClickListener = object : IItemClickListener {
                    override fun onDanmakuClick(
                        danmaku: DanmakuData,
                        rect: android.graphics.RectF,
                        point: android.graphics.PointF
                    ) {
                        val textData = danmaku as? TextData
                        val weighted = textData as? WeightedTextData
                        val text = textData?.text.orEmpty()
                        val dmid = weighted?.danmakuId ?: 0L
                        val uid = weighted?.userHash?.toLongOrNull() ?: 0L
                        val currentMid = com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
                        val isSelf = uid != 0L && uid == currentMid
                        callback(text, dmid, uid, isSelf)
                    }
                }
                Log.d(TAG, "setOnDanmakuClickListener set (DanmakuRenderEngine)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set listener", e)
            }
        }
    }



    /**
     * 释放所有资源
     */
    fun release() {
        Log.d(TAG, " release")
        clearViewReference()
        pluginObserverJob?.cancel()
        pluginObserverJob = null
        
        // 清除缓存
        cachedDanmakuList = null
        sourceDanmakuList = null
        sourceAdvancedDanmakuList = null
        rawDanmakuList = null
        _advancedDanmakuFlow.value = emptyList()
        cachedCid = 0L
        clearExplicitSeekResyncMarker()
        
        Log.d(TAG, " DanmakuManager fully released")
    }
}

internal fun createDanmakuManagerScope(sourceScope: CoroutineScope): CoroutineScope {
    val dispatcher = sourceScope.coroutineContext[ContinuationInterceptor] ?: Dispatchers.Main.immediate
    return CoroutineScope(dispatcher + Job())
}

/**
 * Composable 辅助函数：获取弹幕管理器实例
 */
@Composable
fun rememberDanmakuManager(): DanmakuManager {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val manager = remember { 
        DanmakuManager.getInstance(context, scope) 
    }
    
    // 确保 scope 是最新的
    DisposableEffect(scope) {
        DanmakuManager.updateScope(scope)
        onDispose { }
    }
    
    return manager
}
