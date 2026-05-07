package com.bbttvv.app.feature.video.viewmodel

import androidx.annotation.MainThread
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.PlaybackResumeCandidate
import com.bbttvv.app.core.store.PlaybackResumeStore
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.store.resolveStoredResumeCandidate
import com.bbttvv.app.core.util.MediaUtils
import com.bbttvv.app.core.util.NetworkUtils
import com.bbttvv.app.core.util.resolvePlaybackDefaultQualityId
import com.bbttvv.app.data.model.response.Page
import com.bbttvv.app.data.repository.PlaybackRepository
import com.bbttvv.app.feature.video.usecase.PlaybackLoadResult
import com.bbttvv.app.feature.video.usecase.PlaybackSource
import com.bbttvv.app.feature.video.usecase.ResumePlaybackCandidate as RemoteResumePlaybackCandidate
import com.bbttvv.app.feature.video.usecase.VideoPlaybackUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val PLAYER_RESUME_MIN_POSITION_MS = 5_000L
private const val PLAYER_RESUME_END_GUARD_MS = 10_000L

internal fun resolvePlayerCdnPreference(): SettingsManager.PlayerCdnPreference {
    return SettingsManager.PlayerCdnPreference.BILIVIDEO
}

internal class PlaybackLoadCoordinator(
    private val scope: CoroutineScope,
    private val playbackUseCase: VideoPlaybackUseCase,
    private val ensureMainThread: (String) -> Unit,
    private val getRuntime: () -> PlaybackRuntimeState,
    private val setRuntime: (PlaybackRuntimeState) -> Unit,
    private val currentUiState: () -> PlayerUiState,
    private val updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
    private val updatePlaybackState: (PlayerPlaybackState) -> Unit,
    private val playbackHistoryReporter: PlaybackHistoryReporter,
    private val playbackQualityController: PlaybackQualityController,
    private val playbackEndController: PlaybackEndController,
    private val commentController: PlayerCommentController,
    private val videoShotController: VideoShotController,
    private val progressHeatmapController: ProgressHeatmapController,
    private val resetDanmakuRequestState: () -> Unit,
    private val clearDanmaku: () -> Unit,
    private val requestDanmakuLoad: (cid: Long, aid: Long, startPositionMs: Long, force: Boolean) -> Unit,
    private val isDanmakuEnabled: () -> Boolean,
    private val resetSponsorState: () -> Unit,
    private val loadSponsorSegments: (bvid: String) -> Unit,
    private val applyPlaybackSource: (
        source: PlaybackSource,
        seekToMs: Long,
        resetPlayer: Boolean,
        playWhenReady: Boolean,
    ) -> Unit,
    private val updatePlaybackDuration: (Long) -> Unit,
    private val refreshPlayerSnapshot: () -> Unit,
    private val loadOnlineCount: (bvid: String, cid: Long) -> Unit,
) {
    private var loadJob: Job? = null
    private var generation: Long = 0L

    @MainThread
    fun load(request: PlaybackLoadRequest) {
        ensureMainThread("PlaybackLoadCoordinator.load")
        val requestBvid = request.bvid.trim()
        val requestedCid = request.cid
        if (requestBvid.isBlank()) return
        if (
            !request.force &&
            getRuntime().bvid == requestBvid &&
            getRuntime().cid == requestedCid &&
            currentUiState().info != null
        ) {
            return
        }

        if (playbackHistoryReporter.hasSession) {
            playbackHistoryReporter.finishSession(reason = "switch_video", closeSession = true)
        }

        loadJob?.cancel()
        playbackQualityController.cancelPending()
        val loadGeneration = ++generation
        playbackEndController.resetForNewVideo()
        progressHeatmapController.clear()
        commentController.reset()

        setRuntime(
            PlaybackRuntimeState(
                bvid = requestBvid,
                aid = request.aid,
                cid = requestedCid,
            )
        )
        resetDanmakuRequestState()
        clearDanmaku()
        resetSponsorState()
        videoShotController.clear()
        updatePlaybackState(PlayerPlaybackState())

        loadJob = scope.launch {
            val appContext = NetworkModule.appContext
            val hdrSupported = MediaUtils.isHdrSupported(appContext)
            val dolbyVisionSupported = MediaUtils.isDolbyVisionSupported(appContext)
            val hevcSupported = MediaUtils.isHevcSupported()
            val av1Supported = MediaUtils.isAv1Supported()
            val preferredQuality = resolveInitialPreferredQuality()
            val cdnPreference = resolvePlayerCdnPreference()

            if (!isCurrentPlaybackLoad(loadGeneration, requestBvid, requestedCid)) {
                return@launch
            }

            updateUiState {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = null,
                    resumePrompt = null,
                    autoNextPrompt = null,
                )
            }

            when (
                val result = playbackUseCase.loadVideo(
                    bvid = requestBvid,
                    aid = request.aid,
                    cid = requestedCid,
                    preferredQuality = preferredQuality,
                    cdnPreference = cdnPreference,
                    isHevcSupported = hevcSupported,
                    isAv1Supported = av1Supported,
                    isHdrSupported = hdrSupported,
                    isDolbyVisionSupported = dolbyVisionSupported,
                )
            ) {
                is PlaybackLoadResult.Error -> {
                    if (!isCurrentPlaybackLoad(loadGeneration, requestBvid, requestedCid)) {
                        return@launch
                    }
                    updateUiState {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message,
                        )
                    }
                }

                is PlaybackLoadResult.Success -> {
                    handleSuccess(
                        loadGeneration = loadGeneration,
                        request = request.copy(bvid = requestBvid, cid = requestedCid),
                        result = result,
                    )
                }
            }
        }
    }

    @MainThread
    fun cancel() {
        ensureMainThread("PlaybackLoadCoordinator.cancel")
        loadJob?.cancel()
        loadJob = null
        generation += 1
    }

    private fun handleSuccess(
        loadGeneration: Long,
        request: PlaybackLoadRequest,
        result: PlaybackLoadResult.Success,
    ) {
        if (!isCurrentPlaybackLoad(loadGeneration, request.bvid, request.cid)) {
            return
        }
        setRuntime(
            getRuntime().copy(
                aid = result.info.aid,
                cid = result.info.cid,
            )
        )
        if (!isCurrentPlaybackSession(loadGeneration, result.info.bvid, result.info.cid)) {
            return
        }
        val localResumeCandidate = resolveLocalResumeCandidate(result)
        if (
            shouldAutoSwitchToResumeCandidate(
                explicitStartPositionMs = request.startPositionMs,
                localResumeCandidate = localResumeCandidate,
                remoteResumeCandidate = result.resumeCandidate,
                resumeFromPrompt = request.resumeFromPrompt,
            )
        ) {
            load(
                PlaybackLoadRequest(
                    bvid = result.info.bvid,
                    aid = result.info.aid,
                    cid = localResumeCandidate!!.cid,
                    startPositionMs = localResumeCandidate.positionMs,
                    force = true,
                    resumeFromPrompt = true,
                )
            )
            return
        }
        val resumePrompt = resolveResumePlaybackPrompt(
            explicitStartPositionMs = request.startPositionMs,
            result = result,
            resumeFromPrompt = request.resumeFromPrompt,
            localResumeCandidate = localResumeCandidate,
        )
        val initialSeekPositionMs = resolveInitialPlaybackStartPosition(
            explicitStartPositionMs = request.startPositionMs,
            sourceResumePositionMs = result.source.resumePositionMs,
            resumeFromPrompt = request.resumeFromPrompt,
            localResumePositionMs = localResumeCandidate
                ?.takeIf { !it.isCrossPage && it.cid == result.info.cid }
                ?.positionMs
                ?: 0L,
        )
        applyPlaybackSource(
            result.source,
            initialSeekPositionMs,
            true,
            resumePrompt == null,
        )
        if (!isCurrentPlaybackSession(loadGeneration, result.info.bvid, result.info.cid)) {
            return
        }
        updatePlaybackDuration(result.source.durationMs)
        playbackHistoryReporter.beginSession(
            aid = result.info.aid,
            bvid = result.info.bvid,
            cid = result.info.cid,
            creatorMid = result.info.owner.mid,
            creatorName = result.info.owner.name,
        )
        if (isDanmakuEnabled()) {
            requestDanmakuLoad(
                result.info.cid,
                result.info.aid,
                initialSeekPositionMs,
                true,
            )
        }
        loadSponsorSegments(result.info.bvid)
        videoShotController.load(result.info.bvid, result.info.cid)
        refreshPlayerSnapshot()

        updateUiState { state ->
            state.withPlaybackSource(
                source = result.source,
                isLoading = false,
                statusMessage = null,
            ).copy(
                errorMessage = null,
                info = result.info,
                relatedVideos = result.relatedVideos,
                pages = result.pages,
                currentPageIndex = result.currentPageIndex,
                playbackSpeed = state.playbackSpeed,
                onlineCountText = "",
                resumePrompt = resumePrompt,
            )
        }
        progressHeatmapController.load(
            bvid = result.info.bvid,
            aid = result.info.aid,
            cid = result.info.cid,
            durationMs = result.source.durationMs,
        )
        loadOnlineCount(result.info.bvid, result.info.cid)
        playbackEndController.prefetchRelatedVideosForAutoNextIfNeeded(result.info.bvid)
    }

    private fun isCurrentPlaybackLoad(
        loadGeneration: Long,
        bvid: String,
        requestedCid: Long,
    ): Boolean {
        val runtime = getRuntime()
        return loadGeneration == generation &&
            runtime.bvid == bvid &&
            (requestedCid <= 0L || runtime.cid == requestedCid)
    }

    private fun isCurrentPlaybackSession(
        loadGeneration: Long,
        bvid: String,
        cid: Long,
    ): Boolean {
        val runtime = getRuntime()
        return loadGeneration == generation &&
            runtime.bvid == bvid &&
            runtime.cid == cid
    }

    private suspend fun resolveInitialPreferredQuality(): Int {
        val appContext = NetworkModule.appContext ?: return 64
        val storedQuality = NetworkUtils.getDefaultQualityId(appContext)
        val autoHighestEnabled = SettingsManager.getAutoHighestQualitySync(appContext)
        val isLoggedIn = !TokenManager.sessDataCache.isNullOrBlank() ||
            !TokenManager.accessTokenCache.isNullOrBlank()
        val effectiveVip = PlaybackRepository.refreshVipStatusForPreferredQualityIfNeeded(
            isLoggedIn = isLoggedIn,
            cachedIsVip = TokenManager.isVipCache,
            storedQuality = storedQuality,
            autoHighestEnabled = autoHighestEnabled,
        )
        return resolvePlaybackDefaultQualityId(
            storedQuality = storedQuality,
            autoHighestEnabled = autoHighestEnabled,
            isLoggedIn = isLoggedIn,
            isVip = effectiveVip,
        )
    }

    private fun resolveInitialPlaybackStartPosition(
        explicitStartPositionMs: Long,
        sourceResumePositionMs: Long,
        resumeFromPrompt: Boolean,
        localResumePositionMs: Long,
    ): Long {
        if (resumeFromPrompt) {
            return explicitStartPositionMs.takeIf { it >= PLAYER_RESUME_MIN_POSITION_MS } ?: 0L
        }
        val appContext = NetworkModule.appContext ?: return 0L
        if (!SettingsManager.getPlayerAutoResumeEnabledSync(appContext)) return 0L
        return explicitStartPositionMs
            .takeIf { it >= PLAYER_RESUME_MIN_POSITION_MS }
            ?: sourceResumePositionMs.takeIf { it >= PLAYER_RESUME_MIN_POSITION_MS }
            ?: localResumePositionMs.takeIf { it >= PLAYER_RESUME_MIN_POSITION_MS }
            ?: 0L
    }

    private fun resolveResumePlaybackPrompt(
        explicitStartPositionMs: Long,
        result: PlaybackLoadResult.Success,
        resumeFromPrompt: Boolean,
        localResumeCandidate: PlaybackResumeCandidate?,
    ): ResumePlaybackPrompt? {
        if (resumeFromPrompt) return null
        val appContext = NetworkModule.appContext ?: return null
        if (SettingsManager.getPlayerAutoResumeEnabledSync(appContext)) return null
        val explicitResumePositionMs = normalizeResumePromptPosition(
            positionMs = explicitStartPositionMs,
            durationMs = result.source.durationMs,
        )
        if (explicitResumePositionMs > 0L) {
            return buildResumePlaybackPrompt(
                targetCid = result.info.cid,
                currentCid = result.info.cid,
                positionMs = explicitResumePositionMs,
                pages = result.pages,
            )
        }
        val sourceCandidate = result.resumeCandidate
            ?: localResumeCandidate?.let {
                RemoteResumePlaybackCandidate(
                    cid = it.cid,
                    positionMs = it.positionMs,
                )
            }
            ?: return null
        return buildResumePlaybackPrompt(
            targetCid = sourceCandidate.cid,
            currentCid = result.info.cid,
            positionMs = sourceCandidate.positionMs,
            pages = result.pages,
        )
    }

    private fun buildResumePlaybackPrompt(
        targetCid: Long,
        currentCid: Long,
        positionMs: Long,
        pages: List<Page>,
    ): ResumePlaybackPrompt {
        val targetPage = pages.firstOrNull { page -> page.cid == targetCid }
        return ResumePlaybackPrompt(
            targetCid = targetCid,
            positionMs = positionMs,
            pageLabel = targetPage
                ?.page
                ?.takeIf { it > 0 && pages.size > 1 }
                ?.let { "第${it}P" },
            isCrossPage = targetCid != currentCid,
        )
    }

    private fun normalizeResumePromptPosition(
        positionMs: Long,
        durationMs: Long,
    ): Long {
        if (positionMs < PLAYER_RESUME_MIN_POSITION_MS) return 0L
        if (durationMs <= 0L) return positionMs
        val maxAllowedPositionMs = (durationMs - PLAYER_RESUME_END_GUARD_MS).coerceAtLeast(0L)
        if (positionMs >= maxAllowedPositionMs) return 0L
        return positionMs.coerceAtMost(maxAllowedPositionMs)
    }

    private fun resolveLocalResumeCandidate(
        result: PlaybackLoadResult.Success,
    ): PlaybackResumeCandidate? {
        val appContext = NetworkModule.appContext ?: return null
        val record = PlaybackResumeStore.load(
            context = appContext,
            bvid = result.info.bvid,
        ) ?: return null
        return resolveStoredResumeCandidate(
            currentCid = result.info.cid,
            pages = result.pages,
            record = record,
        )
    }

    private fun shouldAutoSwitchToResumeCandidate(
        explicitStartPositionMs: Long,
        localResumeCandidate: PlaybackResumeCandidate?,
        remoteResumeCandidate: RemoteResumePlaybackCandidate?,
        resumeFromPrompt: Boolean,
    ): Boolean {
        if (resumeFromPrompt) return false
        if (explicitStartPositionMs >= PLAYER_RESUME_MIN_POSITION_MS) return false
        val appContext = NetworkModule.appContext ?: return false
        if (!SettingsManager.getPlayerAutoResumeEnabledSync(appContext)) return false
        if (remoteResumeCandidate != null) return false
        return localResumeCandidate?.isCrossPage == true
    }
}
