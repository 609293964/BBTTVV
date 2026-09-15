package com.bbttvv.app.feature.live

internal enum class LiveBehindWindowRecoveryAction {
    SEEK_DEFAULT,
    REBUILD_SOURCE,
    IGNORE_DUPLICATE,
    NONE,
}

internal fun resolveLiveBehindWindowRecoveryAction(
    previousAttemptsMs: List<Long>,
    nowMs: Long,
    windowMs: Long = 15_000L,
    minimumIntervalMs: Long = 1_200L,
): LiveBehindWindowRecoveryAction {
    val recentAttempts = previousAttemptsMs.filter { nowMs - it in 0L..windowMs }
    if (recentAttempts.lastOrNull()?.let { nowMs - it < minimumIntervalMs } == true) {
        return LiveBehindWindowRecoveryAction.IGNORE_DUPLICATE
    }
    return when (recentAttempts.size) {
        0 -> LiveBehindWindowRecoveryAction.SEEK_DEFAULT
        1 -> LiveBehindWindowRecoveryAction.REBUILD_SOURCE
        else -> LiveBehindWindowRecoveryAction.NONE
    }
}
