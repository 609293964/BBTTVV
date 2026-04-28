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
    private var lastFocusedCommentRpid: Long? = null

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
                if (lastFocusedCommentRpid == rpid) {
                    lastFocusedCommentRpid = null
                }
            }
        }
    }

    fun rememberPlayButtonFocus() {
        lastFocusedCommentRpid = null
    }

    fun rememberCommentFocus(rpid: Long) {
        if (commentTargets.containsKey(rpid)) {
            lastFocusedCommentRpid = rpid
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
        lastFocusedCommentRpid = rpid
        pendingIntent = DetailFocusIntent.RestoreComment(
            rpid = rpid,
            onRestored = onRestored,
        )
        drainPendingFocus()
    }

    fun recoverFocusAfterEscape(): Boolean {
        val rememberedCommentRpid = lastFocusedCommentRpid
        if (rememberedCommentRpid != null && tryRequestCommentFocus(rememberedCommentRpid)) {
            return true
        }
        if (rememberedCommentRpid != null) {
            lastFocusedCommentRpid = null
        }
        return tryRequestPlayButtonFocus()
    }

    fun tryRequestPlayButtonFocus(): Boolean {
        return playButtonTarget?.tryRequestFocus() == true
    }

    fun tryRequestCommentFocus(rpid: Long): Boolean {
        return commentTargets[rpid]?.tryRequestFocus() == true
    }

    fun drainPendingFocus(): Boolean {
        val intent = pendingIntent ?: return false
        val focused = when (intent) {
            is DetailFocusIntent.FocusPlayButton -> {
                if (!tryRequestPlayButtonFocus()) return false
                rememberPlayButtonFocus()
                intent.onFocused()
                true
            }

            is DetailFocusIntent.RestoreComment -> {
                if (!tryRequestCommentFocus(intent.rpid)) return false
                lastFocusedCommentRpid = intent.rpid
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
