package com.bbttvv.app.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester

/**
 * TV 焦点返回
 *
 * 通过 CompositionLocal 向下传递，支持跨页面的焦点返回语义。
 * 各页面注册 TvFocusReturnTarget 声明可恢复的焦点目标，
 * 调用方通过 capture/restore/clear 管理焦点返回流程。
 */
internal val LocalTvFocusReturn = staticCompositionLocalOf<TvFocusReturn?> { null }

internal interface TvFocusReturnTarget {
    fun tryRequestFocus(): Boolean
}

internal fun interface TvFocusReturnRegistration {
    fun unregister()
}

private data class TvFocusReturnTargetEntry(
    val target: TvFocusReturnTarget,
)

private data class TvFocusReturnCapture(
    val key: String,
    val fallbackKeys: List<String>,
)

internal class TvFocusReturn {
    private val targets = LinkedHashMap<String, TvFocusReturnTargetEntry>()
    private var captured: TvFocusReturnCapture? = null

    fun registerTarget(
        key: String,
        target: TvFocusReturnTarget,
    ): TvFocusReturnRegistration {
        val entry = TvFocusReturnTargetEntry(target = target)
        targets[key] = entry
        return TvFocusReturnRegistration {
            if (targets[key] === entry) {
                targets.remove(key)
            }
        }
    }

    fun capture(
        key: String,
        fallbackKeys: List<String> = emptyList(),
    ) {
        captured = TvFocusReturnCapture(
            key = key,
            fallbackKeys = fallbackKeys.filterNot { it == key },
        )
    }

    fun restore(): Boolean {
        val capture = captured ?: return false
        captured = null
        return restoreKeys(listOf(capture.key) + capture.fallbackKeys)
    }

    fun clear() {
        captured = null
    }

    private fun restoreKeys(keys: List<String>): Boolean {
        keys.distinct().forEach { key ->
            val target = targets[key]?.target ?: return@forEach
            if (target.tryRequestFocus()) return true
        }
        return false
    }
}

@Composable
internal fun RegisterTvFocusReturnTarget(
    key: String,
    focusRequester: FocusRequester,
) {
    RegisterTvFocusReturnTarget(
        key = key,
        requestFocus = {
            runCatching { focusRequester.requestFocus() }.getOrDefault(false)
        },
    )
}

@Composable
internal fun RegisterTvFocusReturnTarget(
    key: String,
    requestFocus: () -> Boolean,
) {
    val focusReturn = LocalTvFocusReturn.current ?: return
    val latestRequestFocus = rememberUpdatedState(requestFocus)

    DisposableEffect(focusReturn, key) {
        val registration = focusReturn.registerTarget(
            key = key,
            target = object : TvFocusReturnTarget {
                override fun tryRequestFocus(): Boolean {
                    return latestRequestFocus.value()
                }
            },
        )
        onDispose {
            registration.unregister()
        }
    }
}
