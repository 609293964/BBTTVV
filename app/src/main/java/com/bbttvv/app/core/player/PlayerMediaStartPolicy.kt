package com.bbttvv.app.core.player

internal data class PlayerMediaStart(
    val initialPositionMs: Long?,
    val resetPosition: Boolean,
)

internal fun resolvePlayerMediaStart(
    requestedPositionMs: Long,
    resetPosition: Boolean,
): PlayerMediaStart {
    return PlayerMediaStart(
        initialPositionMs = requestedPositionMs.takeIf { it > 0L },
        resetPosition = resetPosition,
    )
}
