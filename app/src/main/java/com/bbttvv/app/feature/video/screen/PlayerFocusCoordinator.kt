package com.bbttvv.app.feature.video.screen

internal sealed class PlayerFocusIntent {
    data object FocusPlayerSurface : PlayerFocusIntent()
    data object FocusProgress : PlayerFocusIntent()
    data object FocusCommentsPanel : PlayerFocusIntent()
    data class FocusAction(val index: Int) : PlayerFocusIntent()
    data class FocusPanelOption(val index: Int) : PlayerFocusIntent()
}

internal interface PlayerFocusTarget {
    fun tryRequestFocus(): Boolean
}

internal fun interface PlayerFocusTargetRegistration {
    fun unregister()
}

internal class PlayerFocusCoordinator {
    private var pendingIntent: PlayerFocusIntent? = null
    private var playerSurfaceTarget: PlayerFocusTarget? = null
    private var progressTarget: PlayerFocusTarget? = null
    private var commentsPanelTarget: PlayerFocusTarget? = null
    private val actionTargets = LinkedHashMap<Int, PlayerFocusTarget>()
    private val panelOptionTargets = LinkedHashMap<Int, PlayerFocusTarget>()

    fun registerPlayerSurfaceTarget(target: PlayerFocusTarget): PlayerFocusTargetRegistration {
        playerSurfaceTarget = target
        drainPendingFocus()
        return PlayerFocusTargetRegistration {
            if (playerSurfaceTarget === target) {
                playerSurfaceTarget = null
            }
        }
    }

    fun registerProgressTarget(target: PlayerFocusTarget): PlayerFocusTargetRegistration {
        progressTarget = target
        drainPendingFocus()
        return PlayerFocusTargetRegistration {
            if (progressTarget === target) {
                progressTarget = null
            }
        }
    }

    fun registerCommentsPanelTarget(target: PlayerFocusTarget): PlayerFocusTargetRegistration {
        commentsPanelTarget = target
        drainPendingFocus()
        return PlayerFocusTargetRegistration {
            if (commentsPanelTarget === target) {
                commentsPanelTarget = null
            }
        }
    }

    fun registerActionTarget(
        index: Int,
        target: PlayerFocusTarget,
    ): PlayerFocusTargetRegistration {
        actionTargets[index] = target
        drainPendingFocus()
        return PlayerFocusTargetRegistration {
            if (actionTargets[index] === target) {
                actionTargets.remove(index)
            }
        }
    }

    fun registerPanelOptionTarget(
        index: Int,
        target: PlayerFocusTarget,
    ): PlayerFocusTargetRegistration {
        panelOptionTargets[index] = target
        drainPendingFocus()
        return PlayerFocusTargetRegistration {
            if (panelOptionTargets[index] === target) {
                panelOptionTargets.remove(index)
            }
        }
    }

    fun requestFocus(intent: PlayerFocusIntent) {
        pendingIntent = intent
        drainPendingFocus()
    }

    fun drainPendingFocus(): Boolean {
        val intent = pendingIntent ?: return false
        val target = when (intent) {
            PlayerFocusIntent.FocusPlayerSurface -> playerSurfaceTarget
            PlayerFocusIntent.FocusProgress -> progressTarget
            PlayerFocusIntent.FocusCommentsPanel -> commentsPanelTarget
            is PlayerFocusIntent.FocusAction -> actionTargets[intent.index]
            is PlayerFocusIntent.FocusPanelOption -> panelOptionTargets[intent.index]
        } ?: return false
        if (!target.tryRequestFocus()) return false
        pendingIntent = null
        return true
    }
}

internal class PlayerCommentFocusCoordinator {
    private var pendingKey: String? = null
    private val commentTargets = LinkedHashMap<String, PlayerFocusTarget>()

    fun registerCommentTarget(
        key: String,
        target: PlayerFocusTarget,
    ): PlayerFocusTargetRegistration {
        commentTargets[key] = target
        drainPendingFocus()
        return PlayerFocusTargetRegistration {
            if (commentTargets[key] === target) {
                commentTargets.remove(key)
            }
        }
    }

    fun requestFocusKey(key: String) {
        pendingKey = key
        drainPendingFocus()
    }

    fun drainPendingFocus(): Boolean {
        val key = pendingKey ?: return false
        val target = commentTargets[key] ?: return false
        if (!target.tryRequestFocus()) return false
        pendingKey = null
        return true
    }
}
