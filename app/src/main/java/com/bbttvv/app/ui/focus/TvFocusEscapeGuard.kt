package com.bbttvv.app.ui.focus

import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.LinkedHashMap

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

internal class TvFocusEscapeGuard {
    private val targets = LinkedHashMap<String, TvFocusEscapeTarget>()
    private var lastFocusedTargetKey: String? = null

    fun registerTarget(
        key: String,
        target: TvFocusEscapeTarget,
    ): TvFocusEscapeRegistration {
        targets[key] = target
        return TvFocusEscapeRegistration {
            if (targets[key] === target) {
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
        val target = selectTarget(focusedView) ?: return false
        val hasExpectedFocus = focusedView?.let(target::acceptsFocus) == true
        if (hasExpectedFocus) {
            rememberTarget(target)
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
        rememberTarget(target)
        return recovered || reason == TvFocusEscapeReason.MissingFocus
    }

    private fun selectTarget(focusedView: View?): TvFocusEscapeTarget? {
        if (focusedView != null) {
            targets.entries.lastOrNull { (_, target) -> target.acceptsFocus(focusedView) }?.let { entry ->
                lastFocusedTargetKey = entry.key
                return entry.value
            }
        }
        lastFocusedTargetKey?.let { key ->
            targets[key]?.let { return it }
        }
        return targets.entries.lastOrNull()?.value
    }

    private fun rememberTarget(target: TvFocusEscapeTarget) {
        targets.entries.lastOrNull { it.value === target }?.let { entry ->
            lastFocusedTargetKey = entry.key
        }
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

