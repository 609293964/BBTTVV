package com.bbttvv.app.ui.focus

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.LinkedHashMap

/**
 * TV 焦点逃逸防护
 *
 * 在 MainActivity.dispatchKeyEvent 中拦截焦点逃逸事件，防止焦点跑到不可预期的 View。
 * 通过 CompositionLocalProvider 注入 Compose 树，各页面通过 LocalTvFocusEscapeGuard 访问。
 * 注册 TvFocusEscapeTarget 可声明焦点逃逸恢复策略。
 */
internal val LocalTvFocusEscapeGuard = staticCompositionLocalOf<TvFocusEscapeGuard?> { null }

internal enum class TvFocusEscapeReason {
    MissingFocus,
    EscapedFocus,
}

internal interface TvFocusEscapeTarget {
    fun acceptsFocus(focusedView: View): Boolean

    fun shouldRecoverEscapedFocus(focusedView: View): Boolean = false

    fun recoverFocus(reason: TvFocusEscapeReason): Boolean
}

internal fun interface TvFocusEscapeRegistration {
    fun unregister()
}

internal data class TvFocusEscapeTargetEntry(
    val key: String,
    val priority: Int,
    val order: Long,
    val target: TvFocusEscapeTarget,
)

internal class TvFocusEscapeGuard {
    private val targets = LinkedHashMap<String, TvFocusEscapeTargetEntry>()
    private var lastFocusedTargetKey: String? = null
    private var nextTargetOrder = 0L

    fun registerTarget(
        key: String,
        target: TvFocusEscapeTarget,
        priority: Int = 0,
    ): TvFocusEscapeRegistration {
        val entry = TvFocusEscapeTargetEntry(
            key = key,
            priority = priority,
            order = nextTargetOrder++,
            target = target,
        )
        targets[key] = entry
        return TvFocusEscapeRegistration {
            if (targets[key] === entry) {
                targets.remove(key)
                if (lastFocusedTargetKey == key) {
                    lastFocusedTargetKey = null
                }
            }
        }
    }

    fun handleKeyEvent(
        event: KeyEvent,
        focusedView: View?,
    ): Boolean {
        val entry = selectTarget(focusedView) ?: return false
        val target = entry.target
        val hasExpectedFocus = focusedView?.let(target::acceptsFocus) == true
        if (hasExpectedFocus) {
            rememberTarget(entry)
            return false
        }

        val escapedFocus = focusedView?.let(target::shouldRecoverEscapedFocus) == true
        val shouldRecover = TvFocusEscapePolicy.shouldRecoverOnDirectionalKey(
            action = event.action,
            repeatCount = event.repeatCount,
            keyCode = event.keyCode,
            hasCurrentFocus = focusedView != null,
            focusNeedsRecovery = focusedView == null || escapedFocus,
            hasRecoveryTarget = true,
        )
        if (!shouldRecover) return false

        val reason = if (focusedView == null) {
            TvFocusEscapeReason.MissingFocus
        } else {
            TvFocusEscapeReason.EscapedFocus
        }
        val recovered = target.recoverFocus(reason)
        rememberTarget(entry)
        return recovered || reason == TvFocusEscapeReason.MissingFocus
    }

    private fun selectTarget(focusedView: View?): TvFocusEscapeTargetEntry? {
        if (focusedView != null) {
            TvFocusEscapeTargetSelectionPolicy.selectHighestPriority(
                targets.values.filter { entry ->
                    entry.target.acceptsFocus(focusedView) ||
                        entry.target.shouldRecoverEscapedFocus(focusedView)
                }
            )?.let { entry ->
                lastFocusedTargetKey = entry.key
                return entry
            }
        }
        return TvFocusEscapeTargetSelectionPolicy.selectForMissingFocus(
            entries = targets.values,
            lastFocusedTargetKey = lastFocusedTargetKey,
        )
    }

    private fun rememberTarget(entry: TvFocusEscapeTargetEntry) {
        lastFocusedTargetKey = entry.key
    }
}

internal object TvFocusEscapeTargetSelectionPolicy {
    fun selectForMissingFocus(
        entries: Collection<TvFocusEscapeTargetEntry>,
        lastFocusedTargetKey: String?,
    ): TvFocusEscapeTargetEntry? {
        val remembered = lastFocusedTargetKey?.let { key ->
            entries.firstOrNull { it.key == key }
        }
        val highest = selectHighestPriority(entries)
        return when {
            remembered == null -> highest
            highest == null -> remembered
            highest.priority > remembered.priority -> highest
            else -> remembered
        }
    }

    fun selectHighestPriority(
        entries: Collection<TvFocusEscapeTargetEntry>,
    ): TvFocusEscapeTargetEntry? {
        return entries.maxWithOrNull(
            compareBy<TvFocusEscapeTargetEntry> { it.priority }
                .thenBy { it.order }
        )
    }
}

internal object TvFocusEscapePolicy {
    fun shouldRecoverOnDirectionalKey(
        action: Int,
        repeatCount: Int,
        keyCode: Int,
        hasCurrentFocus: Boolean,
        focusNeedsRecovery: Boolean,
        hasRecoveryTarget: Boolean,
    ): Boolean {
        if (!hasRecoveryTarget) return false
        if (action != KeyEvent.ACTION_DOWN) return false
        if (repeatCount > 0) return false
        if (!isDirectionalDpadKey(keyCode)) return false
        return !hasCurrentFocus || focusNeedsRecovery
    }

    private fun isDirectionalDpadKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
    }
}
