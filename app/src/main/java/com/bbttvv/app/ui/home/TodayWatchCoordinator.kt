package com.bbttvv.app.ui.home

import android.content.Context
import android.os.SystemClock
import com.bbttvv.app.core.store.CreatorSignalSnapshot
import com.bbttvv.app.core.store.TodayWatchFeedbackSnapshot
import com.bbttvv.app.core.store.TodayWatchFeedbackStore
import com.bbttvv.app.core.store.TodayWatchProfileStore
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
        val dislikedBvids = buildSet {
            addAll(feedbackSnapshot.dislikedBvids)
            video.bvid.takeIf { it.isNotBlank() }?.let(::add)
        }
        val dislikedCreatorMids = buildSet {
            addAll(feedbackSnapshot.dislikedCreatorMids)
            video.owner.mid.takeIf { it > 0L }?.let(::add)
        }
        persistFeedback(
            feedbackSnapshot.copy(
                dislikedBvids = dislikedBvids,
                dislikedCreatorMids = dislikedCreatorMids,
                dislikedKeywords = feedbackSnapshot.dislikedKeywords + extractFeedbackKeywords(video.title)
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
        if (candidates.isEmpty()) {
            return TodayWatchBuildResult(
                plan = TodayWatchPlan(mode = mode),
                errorMessage = if (isFeedLoading) "正在加载推荐内容..." else "暂无可用于今日观看的推荐内容"
            )
        }

        val historySample = resolveHistorySample(
            forceReload = forceReloadHistory,
            limit = config.historySampleLimit
        )
        val creatorSignals = profileStore.getCreatorSignals(
            context = context,
            limit = config.upRankLimit
        ).map(::toCreatorSignal)
        val penaltySignals = TodayWatchPenaltySignals(
            consumedBvids = sessionConsumedBvids.toSet(),
            dislikedBvids = feedbackSnapshot.dislikedBvids,
            dislikedCreatorMids = feedbackSnapshot.dislikedCreatorMids,
            dislikedKeywords = feedbackSnapshot.dislikedKeywords
        )

        val plan = withContext(Dispatchers.Default) {
            buildTodayWatchPlan(
                historyVideos = historySample,
                candidateVideos = candidates,
                mode = mode,
                eyeCareNightActive = false,
                upRankLimit = config.upRankLimit,
                queueLimit = config.queueBuildLimit,
                creatorSignals = creatorSignals,
                penaltySignals = penaltySignals
            )
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

