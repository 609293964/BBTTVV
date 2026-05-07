package com.bbttvv.app.feature.video.viewmodel

import androidx.annotation.MainThread
import androidx.media3.common.Player
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.response.RelatedVideo
import com.bbttvv.app.data.repository.VideoDetailRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class PlaybackEndSessionSnapshot(
    val bvid: String,
    val cid: Long,
) {
    val sessionKey: String
        get() = "$bvid:$cid"
}

internal class PlaybackEndController(
    private val scope: CoroutineScope,
    private val ensureMainThread: (String) -> Unit,
    private val currentSession: () -> PlaybackEndSessionSnapshot,
    private val currentUiState: () -> PlayerUiState,
    private val currentPlaybackState: () -> PlayerPlaybackState,
    private val updateUiState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val finishPlaybackSession: (String) -> Unit,
    private val restartCurrentVideoFromBeginning: () -> Unit,
    private val loadVideo: (bvid: String, aid: Long, cid: Long, force: Boolean) -> Unit,
    private val requestExitPlayer: (String) -> Unit,
) {
    private var relatedVideosJob: Job? = null
    private var handledSessionKey: String? = null
    private var autoNextPromptSequence: Long = 0L
    private var pendingAutoNextTarget: PlayerPlaybackEndTarget? = null

    @MainThread
    fun handlePlaybackEnded() {
        ensureMainThread("PlaybackEndController.handlePlaybackEnded")
        val session = currentSession()
        if (session.bvid.isBlank() || session.cid <= 0L) return
        val sessionKey = session.sessionKey
        if (handledSessionKey == sessionKey) return
        handledSessionKey = sessionKey
        pendingAutoNextTarget = null
        updateUiState { it.copy(autoNextPrompt = null) }

        finishPlaybackSession("ended")

        val action = resolvePlaybackEndAction()
        val state = currentUiState()
        if (
            action == SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT &&
            !hasNextPageTarget(state) &&
            state.relatedVideos.isEmpty()
        ) {
            resolveAutoNextAfterRelatedVideosLoad(
                sessionKey = sessionKey,
                bvid = session.bvid,
            )
            return
        }

        val target = resolvePlayerPlaybackEndTarget(
            action = action,
            currentBvid = session.bvid,
            pages = state.pages,
            currentPageIndex = state.currentPageIndex,
            relatedVideos = state.relatedVideos,
        )
        handlePlaybackEndTarget(target)
    }

    @MainThread
    fun markPlaybackActive() {
        ensureMainThread("PlaybackEndController.markPlaybackActive")
        handledSessionKey = null
    }

    @MainThread
    fun cancelAutoNextPrompt() {
        ensureMainThread("PlaybackEndController.cancelAutoNextPrompt")
        pendingAutoNextTarget = null
        updateUiState { state ->
            if (state.autoNextPrompt == null) state else state.copy(autoNextPrompt = null)
        }
    }

    @MainThread
    fun confirmAutoNextPrompt(promptId: Long) {
        ensureMainThread("PlaybackEndController.confirmAutoNextPrompt")
        val prompt = currentUiState().autoNextPrompt ?: return
        if (prompt.id != promptId) return
        val target = pendingAutoNextTarget ?: return
        pendingAutoNextTarget = null
        updateUiState { it.copy(autoNextPrompt = null) }
        playPlaybackEndTarget(target)
    }

    @MainThread
    fun prefetchRelatedVideosForAutoNextIfNeeded(bvid: String) {
        ensureMainThread("PlaybackEndController.prefetchRelatedVideosForAutoNextIfNeeded")
        val appContext = NetworkModule.appContext ?: return
        if (SettingsManager.getPlayerPlaybackEndActionSync(appContext) != SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT) {
            return
        }
        if (bvid.isBlank() || hasNextPageTarget(currentUiState())) return
        relatedVideosJob?.cancel()
        relatedVideosJob = scope.launch {
            loadRelatedVideosForAutoNext(bvid)
        }
    }

    @MainThread
    fun resetForNewVideo() {
        ensureMainThread("PlaybackEndController.resetForNewVideo")
        relatedVideosJob?.cancel()
        relatedVideosJob = null
        handledSessionKey = null
        pendingAutoNextTarget = null
    }

    @MainThread
    fun clear() {
        ensureMainThread("PlaybackEndController.clear")
        resetForNewVideo()
    }

    @MainThread
    private fun handlePlaybackEndTarget(target: PlayerPlaybackEndTarget) {
        ensureMainThread("PlaybackEndController.handlePlaybackEndTarget")
        when (target) {
            PlayerPlaybackEndTarget.None -> Unit
            PlayerPlaybackEndTarget.LoopOne -> restartCurrentVideoFromBeginning()
            PlayerPlaybackEndTarget.Return -> requestExitPlayer("playback_ended")
            is PlayerPlaybackEndTarget.PageTarget,
            is PlayerPlaybackEndTarget.RelatedTarget -> showAutoNextPrompt(target)
        }
    }

    @MainThread
    private fun showAutoNextPrompt(target: PlayerPlaybackEndTarget) {
        ensureMainThread("PlaybackEndController.showAutoNextPrompt")
        val prompt = buildPlayerAutoNextPrompt(
            promptId = ++autoNextPromptSequence,
            target = target,
        ) ?: return
        pendingAutoNextTarget = target
        updateUiState {
            it.copy(
                autoNextPrompt = prompt,
                statusMessage = null,
            )
        }
    }

    @MainThread
    private fun playPlaybackEndTarget(target: PlayerPlaybackEndTarget) {
        ensureMainThread("PlaybackEndController.playPlaybackEndTarget")
        when (target) {
            PlayerPlaybackEndTarget.None -> Unit
            PlayerPlaybackEndTarget.LoopOne -> restartCurrentVideoFromBeginning()
            PlayerPlaybackEndTarget.Return -> requestExitPlayer("playback_ended")
            is PlayerPlaybackEndTarget.PageTarget -> {
                val info = currentUiState().info ?: return requestExitPlayer("auto_next_missing_info")
                loadVideo(info.bvid, info.aid, target.page.cid, true)
            }

            is PlayerPlaybackEndTarget.RelatedTarget -> {
                val video = target.video
                loadVideo(video.bvid, video.aid, video.cid, true)
            }
        }
    }

    private fun resolveAutoNextAfterRelatedVideosLoad(
        sessionKey: String,
        bvid: String,
    ) {
        relatedVideosJob?.cancel()
        relatedVideosJob = scope.launch {
            val relatedVideos = loadRelatedVideosForAutoNext(bvid)
            if (
                currentSession().sessionKey != sessionKey ||
                currentPlaybackState().playerState != Player.STATE_ENDED ||
                handledSessionKey != sessionKey
            ) {
                return@launch
            }
            val state = currentUiState()
            val target = resolvePlayerPlaybackEndTarget(
                action = SettingsManager.PlayerPlaybackEndAction.AUTO_NEXT,
                currentBvid = bvid,
                pages = state.pages,
                currentPageIndex = state.currentPageIndex,
                relatedVideos = relatedVideos,
            )
            handlePlaybackEndTarget(target)
        }
    }

    private suspend fun loadRelatedVideosForAutoNext(bvid: String): List<RelatedVideo> {
        val cacheKey = bvid.trim()
        if (cacheKey.isBlank()) return emptyList()
        val cached = VideoDetailRepository.getCachedRelatedVideos(cacheKey).orEmpty()
        if (cached.isNotEmpty()) {
            updateRelatedVideosForCurrentSession(cacheKey, cached)
            return cached
        }
        val relatedVideos = VideoDetailRepository.getRelatedVideos(cacheKey)
        updateRelatedVideosForCurrentSession(cacheKey, relatedVideos)
        return relatedVideos
    }

    private fun updateRelatedVideosForCurrentSession(
        bvid: String,
        relatedVideos: List<RelatedVideo>,
    ) {
        if (currentSession().bvid != bvid || relatedVideos.isEmpty()) return
        updateUiState { it.copy(relatedVideos = relatedVideos) }
    }

    private fun hasNextPageTarget(state: PlayerUiState): Boolean {
        return state.pages.getOrNull(state.currentPageIndex + 1)?.cid?.let { it > 0L } == true
    }

    private fun resolvePlaybackEndAction(): SettingsManager.PlayerPlaybackEndAction {
        val appContext = NetworkModule.appContext ?: return SettingsManager.PlayerPlaybackEndAction.NONE
        return SettingsManager.getPlayerPlaybackEndActionSync(appContext)
    }
}
