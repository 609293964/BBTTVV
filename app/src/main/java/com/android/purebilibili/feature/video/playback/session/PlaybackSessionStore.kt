package com.android.purebilibili.feature.video.playback.session

import com.android.purebilibili.feature.video.playback.loader.PlaybackRequest
import com.android.purebilibili.feature.video.policy.ResumePlaybackSuggestion
import com.android.purebilibili.feature.video.viewmodel.normalizeCodecFamilyKey
import com.android.purebilibili.feature.video.viewmodel.PlaybackEndAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class PlaybackLoadRequestContext(
    val request: PlaybackRequest,
    val requestToken: Long,
    val subtitleToken: Long
)

internal data class PlaybackSessionState(
    val currentBvid: String = "",
    val currentCid: Long = 0L,
    val currentLoadRequestToken: Long = 0L,
    val subtitleLoadToken: Long = 0L,
    val currentRequest: PlaybackRequest? = null,
    val blockedVideoCodecs: Set<String> = emptySet(),
    val resumeSuggestion: ResumePlaybackSuggestion? = null,
    val lastCompletionAction: PlaybackEndAction? = null
)

internal class PlaybackSessionStore(
    initialState: PlaybackSessionState = PlaybackSessionState()
) {
    private val _state = MutableStateFlow(initialState)
    val state = _state.asStateFlow()

    fun setResumeSuggestion(suggestion: ResumePlaybackSuggestion?) {
        _state.update { current ->
            current.copy(resumeSuggestion = suggestion)
        }
    }

    fun updateCurrentMedia(
        bvid: String = _state.value.currentBvid,
        cid: Long = _state.value.currentCid
    ) {
        _state.update { current ->
            current.copy(
                currentBvid = bvid,
                currentCid = cid
            )
        }
    }

    fun clearCurrentMedia() {
        _state.update { current ->
            current.copy(
                currentBvid = "",
                currentCid = 0L,
                currentRequest = null
            )
        }
    }

    fun setCurrentRequest(request: PlaybackRequest?) {
        _state.update { current ->
            current.copy(currentRequest = request)
        }
    }

    fun clearCurrentRequest() {
        setCurrentRequest(null)
    }

    fun blockVideoCodec(codec: String) {
        val normalizedCodec = normalizeCodecFamilyKey(codec) ?: return
        _state.update { current ->
            current.copy(
                blockedVideoCodecs = current.blockedVideoCodecs + normalizedCodec
            )
        }
    }

    fun setCurrentLoadRequestToken(token: Long) {
        _state.update { current ->
            current.copy(currentLoadRequestToken = token)
        }
    }

    fun nextLoadRequestToken(): Long {
        val nextToken = _state.value.currentLoadRequestToken + 1L
        setCurrentLoadRequestToken(nextToken)
        return nextToken
    }

    fun setSubtitleLoadToken(token: Long) {
        _state.update { current ->
            current.copy(subtitleLoadToken = token)
        }
    }

    fun nextSubtitleToken(): Long {
        val nextToken = _state.value.subtitleLoadToken + 1L
        setSubtitleLoadToken(nextToken)
        return nextToken
    }

    fun beginLoadRequest(request: PlaybackRequest): PlaybackLoadRequestContext {
        val requestToken = nextLoadRequestToken()
        val subtitleToken = nextSubtitleToken()
        _state.update { current ->
            current.copy(
                currentRequest = request,
                currentBvid = request.bvid
            )
        }
        return PlaybackLoadRequestContext(
            request = request,
            requestToken = requestToken,
            subtitleToken = subtitleToken
        )
    }

    fun clearResumeSuggestion() {
        setResumeSuggestion(null)
    }

    fun consumeResumeSuggestion(): ResumePlaybackSuggestion? {
        val suggestion = _state.value.resumeSuggestion
        _state.update { current ->
            current.copy(resumeSuggestion = null)
        }
        return suggestion
    }

    fun recordCompletionAction(action: PlaybackEndAction) {
        _state.update { current ->
            current.copy(lastCompletionAction = action)
        }
    }
}
