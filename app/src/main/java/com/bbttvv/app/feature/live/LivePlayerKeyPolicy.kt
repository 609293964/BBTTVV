package com.bbttvv.app.feature.live

import android.view.KeyEvent
import com.bbttvv.app.ui.input.isTvBackKey
import com.bbttvv.app.ui.input.isTvConfirmKey
import com.bbttvv.app.ui.input.resolveTvSinglePress

internal enum class LivePlayerKeyCommand {
    Back,
    MoveLeft,
    MoveRight,
    MoveUp,
    MoveDown,
    Confirm,
    TogglePlayback,
    Play,
    Pause,
    ShowMenu,
}

internal data class LivePlayerKeyDecision(
    val isConsumed: Boolean,
    val command: LivePlayerKeyCommand? = null,
)

internal fun resolveLivePlayerKeyDecision(event: KeyEvent): LivePlayerKeyDecision {
    return resolveLivePlayerKeyDecision(
        action = event.action,
        keyCode = event.keyCode,
        repeatCount = event.repeatCount,
    )
}

internal fun resolveLivePlayerKeyDecision(
    action: Int,
    keyCode: Int,
    repeatCount: Int,
): LivePlayerKeyDecision {
    val repeatableCommand = if (action == KeyEvent.ACTION_DOWN) {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> LivePlayerKeyCommand.MoveLeft
            KeyEvent.KEYCODE_DPAD_RIGHT -> LivePlayerKeyCommand.MoveRight
            KeyEvent.KEYCODE_DPAD_UP -> LivePlayerKeyCommand.MoveUp
            KeyEvent.KEYCODE_DPAD_DOWN -> LivePlayerKeyCommand.MoveDown
            else -> null
        }
    } else {
        null
    }
    if (repeatableCommand != null) {
        return LivePlayerKeyDecision(isConsumed = true, command = repeatableCommand)
    }

    val singleShotCommand = when {
        isTvBackKey(keyCode) -> LivePlayerKeyCommand.Back
        isTvConfirmKey(keyCode) -> LivePlayerKeyCommand.Confirm
        else -> when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> LivePlayerKeyCommand.TogglePlayback
            KeyEvent.KEYCODE_MEDIA_PLAY -> LivePlayerKeyCommand.Play
            KeyEvent.KEYCODE_MEDIA_PAUSE -> LivePlayerKeyCommand.Pause
            KeyEvent.KEYCODE_MENU -> LivePlayerKeyCommand.ShowMenu
            else -> null
        }
    }
    val pressDecision = resolveTvSinglePress(
        action = action,
        repeatCount = repeatCount,
        isHandledKey = singleShotCommand != null,
    )

    return LivePlayerKeyDecision(
        isConsumed = pressDecision.isConsumed,
        command = singleShotCommand.takeIf { pressDecision.shouldTrigger },
    )
}
