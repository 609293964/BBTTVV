package com.bbttvv.app.ui.detail

internal sealed class DetailFocusIntent {
    data class FocusPlayButton(
        val onFocused: () -> Unit,
    ) : DetailFocusIntent()

    data class RestoreComment(
        val rpid: Long,
        val onRestored: (Long) -> Unit,
    ) : DetailFocusIntent()
}

internal interface DetailFocusTarget {
    fun tryRequestFocus(): Boolean
}

internal fun interface DetailFocusTargetRegistration {
    fun unregister()
}

internal class DetailFocusCoordinator {
    private var pendingIntent: DetailFocusIntent? = null
    private var playButtonTarget: DetailFocusTarget? = null
    private val commentTargets = LinkedHashMap<Long, DetailFocusTarget>()

    fun registerPlayButtonTarget(target: DetailFocusTarget): DetailFocusTargetRegistration {
        playButtonTarget = target
        drainPendingFocus()
        return DetailFocusTargetRegistration {
            if (playButtonTarget === target) {
                playButtonTarget = null
            }
        }
    }

    fun registerCommentTarget(
        rpid: Long,
        target: DetailFocusTarget,
    ): DetailFocusTargetRegistration {
        commentTargets[rpid] = target
        drainPendingFocus()
        return DetailFocusTargetRegistration {
            if (commentTargets[rpid] === target) {
                commentTargets.remove(rpid)
            }
        }
    }

    fun requestInitialPlayFocus(onFocused: () -> Unit) {
        pendingIntent = DetailFocusIntent.FocusPlayButton(onFocused)
        drainPendingFocus()
    }

    fun requestRestoreComment(
        rpid: Long,
        onRestored: (Long) -> Unit,
    ) {
        pendingIntent = DetailFocusIntent.RestoreComment(
            rpid = rpid,
            onRestored = onRestored,
        )
        drainPendingFocus()
    }

    fun drainPendingFocus(): Boolean {
        val intent = pendingIntent ?: return false
        val focused = when (intent) {
            is DetailFocusIntent.FocusPlayButton -> {
                val target = playButtonTarget ?: return false
                if (!target.tryRequestFocus()) return false
                intent.onFocused()
                true
            }

            is DetailFocusIntent.RestoreComment -> {
                val target = commentTargets[intent.rpid] ?: return false
                if (!target.tryRequestFocus()) return false
                intent.onRestored(intent.rpid)
                true
            }
        }
        if (focused) {
            pendingIntent = null
        }
        return focused
    }
}
