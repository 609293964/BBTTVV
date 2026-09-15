package com.bbttvv.app.feature.video.screen

import android.view.KeyEvent
import com.bbttvv.app.ui.input.isTvBackKey
import com.bbttvv.app.ui.input.isTvConfirmKey

internal enum class DanmakuVoteKeyState {
    Showing,
    Submitting,
    RetryableError,
    Submitted,
}

internal enum class DanmakuVoteKeyCommand {
    None,
    Select,
    Retry,
    Dismiss,
}

internal data class DanmakuVoteKeyDecision(
    val consumed: Boolean,
    val nextIndex: Int,
    val command: DanmakuVoteKeyCommand = DanmakuVoteKeyCommand.None,
)

/**
 * Keeps all remote navigation inside the vote modal and fires confirm/back once on key down.
 */
internal fun resolveDanmakuVoteKeyDecision(
    action: Int,
    keyCode: Int,
    repeatCount: Int,
    state: DanmakuVoteKeyState,
    currentIndex: Int,
    lastIndex: Int,
    horizontal: Boolean = false,
): DanmakuVoteKeyDecision {
    val safeLastIndex = lastIndex.coerceAtLeast(0)
    val safeIndex = currentIndex.coerceIn(0, safeLastIndex)
    if (isTvBackKey(keyCode)) {
        return DanmakuVoteKeyDecision(
            consumed = action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP,
            nextIndex = safeIndex,
            command = if (action == KeyEvent.ACTION_DOWN && repeatCount == 0) {
                DanmakuVoteKeyCommand.Dismiss
            } else {
                DanmakuVoteKeyCommand.None
            },
        )
    }
    if (isTvConfirmKey(keyCode)) {
        val command = when {
            action != KeyEvent.ACTION_DOWN || repeatCount != 0 -> DanmakuVoteKeyCommand.None
            state == DanmakuVoteKeyState.Showing -> DanmakuVoteKeyCommand.Select
            state == DanmakuVoteKeyState.RetryableError -> DanmakuVoteKeyCommand.Retry
            else -> DanmakuVoteKeyCommand.None
        }
        return DanmakuVoteKeyDecision(
            consumed = action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP,
            nextIndex = safeIndex,
            command = command,
        )
    }

    if (
        keyCode != KeyEvent.KEYCODE_DPAD_UP &&
        keyCode != KeyEvent.KEYCODE_DPAD_DOWN &&
        keyCode != KeyEvent.KEYCODE_DPAD_LEFT &&
        keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
    ) {
        return DanmakuVoteKeyDecision(consumed = false, nextIndex = safeIndex)
    }

    val nextIndex = if (action == KeyEvent.ACTION_DOWN && state == DanmakuVoteKeyState.Showing) {
        val previousKey = if (horizontal) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_UP
        val nextKey = if (horizontal) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_DOWN
        when (keyCode) {
            previousKey -> (safeIndex - 1).coerceAtLeast(0)
            nextKey -> (safeIndex + 1).coerceAtMost(safeLastIndex)
            else -> safeIndex
        }
    } else {
        safeIndex
    }
    return DanmakuVoteKeyDecision(
        consumed = action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP,
        nextIndex = nextIndex,
    )
}
