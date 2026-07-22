package com.bbttvv.app.feature.live

import android.view.KeyEvent

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
    if (action != KeyEvent.ACTION_DOWN) return LivePlayerKeyDecision(isConsumed = false)

    val repeatableCommand = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> LivePlayerKeyCommand.MoveLeft
        KeyEvent.KEYCODE_DPAD_RIGHT -> LivePlayerKeyCommand.MoveRight
        KeyEvent.KEYCODE_DPAD_UP -> LivePlayerKeyCommand.MoveUp
        KeyEvent.KEYCODE_DPAD_DOWN -> LivePlayerKeyCommand.MoveDown
        else -> null
    }
    if (repeatableCommand != null) {
        return LivePlayerKeyDecision(isConsumed = true, command = repeatableCommand)
    }

    val singleShotCommand = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE -> LivePlayerKeyCommand.Back
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> LivePlayerKeyCommand.Confirm
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> LivePlayerKeyCommand.TogglePlayback
        KeyEvent.KEYCODE_MEDIA_PLAY -> LivePlayerKeyCommand.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> LivePlayerKeyCommand.Pause
        KeyEvent.KEYCODE_MENU -> LivePlayerKeyCommand.ShowMenu
        else -> null
    } ?: return LivePlayerKeyDecision(isConsumed = false)

    return LivePlayerKeyDecision(
        isConsumed = true,
        command = singleShotCommand.takeIf { repeatCount == 0 },
    )
}
