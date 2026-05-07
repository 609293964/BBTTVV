package com.bbttvv.app.feature.video.screen

/**
 * 播放器焦点意图密封类
 *
 * - [FocusPlayerSurface]: 聚焦播放 Surface
 * - [FocusProgress]: 聚焦进度条
 * - [FocusCommentsPanel]: 聚焦评论面板
 * - [FocusAction]: 聚焦指定索引的操作按钮
 * - [FocusPanelOption]: 聚焦指定索引的设置面板项
 */
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

/**
 * 播放器焦点协调器
 *
 * 管理播放器内的焦点，包括播放 Surface、进度条、评论面板、操作按钮和设置面板。
 * 播放器内不应直接散落 focusRequester.requestFocus()，应通过 PlayerFocusIntent 表达明确意图。
 * 同文件包含 PlayerCommentFocusCoordinator，管理播放器评论面板中按 key 索引的评论焦点。
 */
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

    fun requestFocus(intent: PlayerFocusIntent): Boolean {
        pendingIntent = intent
        return drainPendingFocus()
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
