package com.bbttvv.app.ui.home

import android.content.Context
import com.bbttvv.app.core.plugin.*
import com.bbttvv.app.feature.plugin.TodayWatchPlugin
import kotlinx.coroutines.CancellationException
import android.os.SystemClock
import com.bbttvv.app.core.store.CreatorSignalSnapshot
import com.bbttvv.app.core.store.TodayWatchDislikedVideoSnapshot
import com.bbttvv.app.core.store.TodayWatchFeedbackSnapshot
import com.bbttvv.app.core.store.TodayWatchFeedbackStore
import com.bbttvv.app.core.store.TodayWatchProfileStore
import com.bbttvv.app.core.store.withDislikedVideoFeedback
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.repository.HistoryRepository
import com.bbttvv.app.feature.plugin.TodayWatchPluginConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TodayWatchHistoryPageSize = 40
private const val TodayWatchHistoryPageLimit = 2
private const val TodayWatchHistoryFailureCooldownMs = 60_000L
private val TodayWatchTitleKeywordPattern = Regex("""[\p{L}\p{N}\u4E00-\u9FFF]{2,12}""")

internal data class TodayWatchBuildResult(
    val plan: TodayWatchPlan,
    val errorMessage: String?
)

/** Coordinates Today Watch feedback, history sampling, and queue planning. */
internal class TodayWatchCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val historyRepository: HistoryRepository = HistoryRepository,
    private val feedbackStore: TodayWatchFeedbackStore = TodayWatchFeedbackStore,
    private val profileStore: TodayWatchProfileStore = TodayWatchProfileStore
) {
    private val sessionConsumedBvids = linkedSetOf<String>()
    private var cachedHistorySample: List<VideoItem> = emptyList()
    private var lastHistoryLoadFailureMs = 0L
    private var feedbackSnapshot: TodayWatchFeedbackSnapshot = feedbackStore.getSnapshot(context)
    private var latestRefreshToken: Long = 0L

    fun hasCachedHistorySample(): Boolean = cachedHistorySample.isNotEmpty()

    fun resetAccountScope() {
        sessionConsumedBvids.clear()
        cachedHistorySample = emptyList()
        lastHistoryLoadFailureMs = 0L
        feedbackSnapshot = feedbackStore.getSnapshot(context)
    }

    fun applyRefreshToken(config: TodayWatchPluginConfig) {
        if (config.refreshTriggerToken == latestRefreshToken) return
        latestRefreshToken = config.refreshTriggerToken
        feedbackSnapshot = feedbackStore.getSnapshot(context)
        sessionConsumedBvids.clear()
    }

    fun collectManualRefreshConsumed(
        plan: TodayWatchPlan,
        previewLimit: Int
    ) {
        val consumed = collectTodayWatchConsumedForManualRefresh(
            plan = plan,
            previewLimit = previewLimit
        )
        if (consumed.isNotEmpty()) {
            sessionConsumedBvids.addAll(consumed)
        }
    }

    fun markVideoOpened(video: VideoItem) {
        val consumedBvid = video.bvid.takeIf { it.isNotBlank() } ?: return
        sessionConsumedBvids.add(consumedBvid)
    }

    fun consumeFromPlan(
        plan: TodayWatchPlan,
        consumedBvid: String,
        queuePreviewLimit: Int
    ): TodayWatchQueueConsumeUpdate {
        return consumeVideoFromTodayWatchPlan(
            plan = plan,
            consumedBvid = consumedBvid,
            queuePreviewLimit = queuePreviewLimit
        )
    }

    fun markNotInterested(video: VideoItem) {
        persistFeedback(
            feedbackSnapshot.withDislikedVideoFeedback(
                video = TodayWatchDislikedVideoSnapshot(
                    bvid = video.bvid,
                    title = video.title,
                    creatorName = video.owner.name,
                    creatorMid = video.owner.mid,
                    dislikedAtMillis = System.currentTimeMillis()
                ),
                keywords = extractFeedbackKeywords(video.title)
            )
        )

        video.bvid.takeIf { it.isNotBlank() }?.let(sessionConsumedBvids::add)
    }

    suspend fun buildPlan(
        candidates: List<VideoItem>,
        config: TodayWatchPluginConfig,
        mode: TodayWatchMode,
        forceReloadHistory: Boolean,
        isFeedLoading: Boolean
    ): TodayWatchBuildResult {
        val accountEpoch = com.bbttvv.app.core.store.AccountSessionEpoch.current()
        val recommendationRevision = com.bbttvv.app.core.store.RecommendationAccountStore.changes.value
        if (candidates.isEmpty()) {
            return TodayWatchBuildResult(
                plan = TodayWatchPlan(mode = mode),
                errorMessage = if (isFeedLoading) "正在加载推荐内容..." else "暂无可用于今日观看的推荐内容"
            )
        }

        val plugin = PluginManager.getEnabledPlugins(RecommendationPluginApi::class)
            .firstOrNull { it.id == TodayWatchPlugin.PLUGIN_ID }
            ?: return TodayWatchBuildResult(TodayWatchPlan(mode = mode), "推荐插件尚未就绪或已关闭")
        val required = setOf(PluginCapability.RECOMMENDATION_CANDIDATES,
            PluginCapability.LOCAL_HISTORY_READ, PluginCapability.LOCAL_FEEDBACK_READ)
        if (plugin.capabilityManifest.pluginId != plugin.id ||
            plugin.capabilityManifest.apiVersion != 1 || !plugin.capabilityManifest.capabilities.containsAll(required)) {
            return TodayWatchBuildResult(TodayWatchPlan(mode = mode), "推荐插件缺少必要能力声明")
        }
        val historySample = resolveHistorySample(
            forceReload = forceReloadHistory,
            limit = config.historySampleLimit
        )
        val creatorSignals = profileStore.getCreatorSignals(
            context = context,
            limit = 5
        ).map(::toCreatorSignal)
        val penaltySignals = TodayWatchPenaltySignals(
            consumedBvids = sessionConsumedBvids.toSet(),
            dislikedBvids = feedbackSnapshot.dislikedBvids,
            dislikedCreatorMids = feedbackSnapshot.dislikedCreatorMids,
            dislikedKeywords = feedbackSnapshot.dislikedKeywords
        )

        val plan = try {
            withContext(Dispatchers.Default) {
                val result = plugin.buildRecommendations(RecommendationRequest(
                    candidateVideos = candidates, historyVideos = historySample,
                    creatorSignals = creatorSignals.map { RecommendationCreatorSignal(it.mid, it.name, it.score, it.watchCount) },
                    feedbackSignals = RecommendationFeedbackSignals(penaltySignals.consumedBvids,
                        penaltySignals.dislikedBvids, penaltySignals.dislikedCreatorMids, penaltySignals.dislikedKeywords),
                    sceneSignals = RecommendationSceneSignals(eyeCareNightActive = false),
                    mode = RecommendationMode.valueOf(mode.name), queueLimit = config.queueBuildLimit,
                    groupLimit = 5, strategy = config.strategy,
                ))
                check(result.sourcePluginId == plugin.id) { "推荐插件返回来源不匹配" }
                TodayWatchPlan(mode = mode,
                    upRanks = result.groups.firstOrNull { it.id == "preferred_creators" }?.items.orEmpty()
                        .mapNotNull { item -> item.id.toLongOrNull()?.let { TodayUpRank(it, item.title, item.score ?: 0.0, item.watchCount) } },
                    videoQueue = result.items.map { it.video },
                    explanationByBvid = result.items.associate { it.video.bvid to it.explanation },
                    scoreByBvid = result.items.associate { it.video.bvid to it.score },
                    confidenceByBvid = result.items.associate { it.video.bvid to it.confidence },
                    historySampleCount = result.historySampleCount, generatedAt = result.generatedAt,
                )
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return TodayWatchBuildResult(TodayWatchPlan(mode = mode), "推荐插件执行失败，请重试") }
        if (PluginManager.getEnabledPlugins(RecommendationPluginApi::class).none { it === plugin }) {
            return TodayWatchBuildResult(TodayWatchPlan(mode = mode), "推荐插件已关闭")
        }
        if (!com.bbttvv.app.core.store.AccountSessionEpoch.isCurrent(accountEpoch) ||
            com.bbttvv.app.core.store.RecommendationAccountStore.changes.value != recommendationRevision) {
            throw CancellationException("推荐请求身份已变化")
        }
        return TodayWatchBuildResult(
            plan = plan,
            errorMessage = if (plan.videoQueue.isEmpty()) {
                "今日观看暂未生成可播放队列，请刷新推荐内容后重试"
            } else {
                null
            }
        )
    }

    private suspend fun resolveHistorySample(
        forceReload: Boolean,
        limit: Int
    ): List<VideoItem> {
        if (!forceReload && cachedHistorySample.isNotEmpty()) {
            return cachedHistorySample.take(limit)
        }
        if (!forceReload &&
            cachedHistorySample.isEmpty() &&
            lastHistoryLoadFailureMs > 0L &&
            SystemClock.elapsedRealtime() - lastHistoryLoadFailureMs < TodayWatchHistoryFailureCooldownMs
        ) {
            return emptyList()
        }

        val loaded = mutableListOf<VideoItem>()
        var cursorMax = 0L
        var cursorViewAt = 0L
        var cursorBusiness: String? = null

        for (pageIndex in 0 until TodayWatchHistoryPageLimit) {
            val result = historyRepository.getHistoryList(
                ps = TodayWatchHistoryPageSize,
                max = cursorMax,
                viewAt = cursorViewAt,
                business = cursorBusiness
            )
            val page = result.getOrNull() ?: run {
                val error = result.exceptionOrNull()
                Logger.w("TodayWatchCoordinator", "Failed to load history sample for today watch", error)
                lastHistoryLoadFailureMs = SystemClock.elapsedRealtime()
                break
            }
            loaded += page.list
                .map { it.toVideoItem() }
                .filter { it.bvid.isNotBlank() && it.owner.mid > 0L }
            if (loaded.size >= limit) {
                break
            }
            val cursor = page.cursor ?: break
            val nextBusiness = cursor.business.takeIf { it.isNotBlank() }
            val hasNextCursor = cursor.max > 0L || cursor.view_at > 0L || nextBusiness != null
            if (!hasNextCursor) {
                break
            }
            cursorMax = cursor.max
            cursorViewAt = cursor.view_at
            cursorBusiness = nextBusiness
        }

        val normalized = loaded
            .distinctBy { it.bvid }
            .sortedByDescending { it.view_at }
            .take(limit)
        if (normalized.isNotEmpty()) {
            cachedHistorySample = normalized
            lastHistoryLoadFailureMs = 0L
        }
        return (if (normalized.isNotEmpty()) normalized else cachedHistorySample).take(limit)
    }

    private fun persistFeedback(snapshot: TodayWatchFeedbackSnapshot) {
        feedbackSnapshot = snapshot
        scope.launch(Dispatchers.IO) {
            feedbackStore.saveSnapshot(context, snapshot)
        }
    }

    private fun extractFeedbackKeywords(title: String): Set<String> {
        if (title.isBlank()) return emptySet()
        return TodayWatchTitleKeywordPattern.findAll(title.lowercase())
            .map { it.value.trim() }
            .filter { keyword -> keyword.length >= 2 }
            .take(6)
            .toSet()
    }

    private fun toCreatorSignal(snapshot: CreatorSignalSnapshot): TodayWatchCreatorSignal {
        return TodayWatchCreatorSignal(
            mid = snapshot.mid,
            name = snapshot.name,
            score = snapshot.score,
            watchCount = snapshot.watchCount
        )
    }
}

