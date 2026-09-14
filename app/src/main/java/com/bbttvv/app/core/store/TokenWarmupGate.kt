package com.bbttvv.app.core.store

internal enum class TokenWarmupState {
    NOT_STARTED,
    RUNNING,
    SUCCEEDED,
    FAILED
}

/** Small synchronized state machine so failed one-time initialization remains retryable. */
internal class TokenWarmupGate {
    private var state: TokenWarmupState = TokenWarmupState.NOT_STARTED

    @Synchronized
    fun state(): TokenWarmupState = state

    @Synchronized
    fun tryStart(): Boolean {
        return when (state) {
            TokenWarmupState.NOT_STARTED,
            TokenWarmupState.FAILED -> {
                state = TokenWarmupState.RUNNING
                true
            }
            TokenWarmupState.RUNNING,
            TokenWarmupState.SUCCEEDED -> false
        }
    }

    @Synchronized
    fun markSucceeded() {
        check(state == TokenWarmupState.RUNNING) { "Warmup is not running" }
        state = TokenWarmupState.SUCCEEDED
    }

    @Synchronized
    fun markFailed() {
        check(state == TokenWarmupState.RUNNING) { "Warmup is not running" }
        state = TokenWarmupState.FAILED
    }
}
