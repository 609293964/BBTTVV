// 文件路径: core/util/RetryStrategy.kt
package com.bbttvv.app.core.util

import com.bbttvv.app.data.model.VideoLoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 重试策略工具类。
 *
 * CancellationException 永远属于协程控制流，不会被映射成业务失败或继续重试。
 */
object RetryStrategy {
    private const val TAG = "RetryStrategy"

    data class RetryConfig(
        val maxAttempts: Int = 4,
        val initialDelayMs: Long = 500,
        val maxDelayMs: Long = 5000,
        val multiplier: Double = 2.0
    ) {
        init {
            require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
            require(initialDelayMs >= 0L) { "initialDelayMs must be >= 0" }
            require(maxDelayMs >= initialDelayMs) {
                "maxDelayMs must be >= initialDelayMs"
            }
            require(multiplier.isFinite() && multiplier >= 1.0) {
                "multiplier must be finite and >= 1.0"
            }
        }
    }

    sealed class RetryResult<out T> {
        data class Success<T>(val data: T) : RetryResult<T>()
        data class Failure<T>(
            val error: VideoLoadError,
            val attemptsMade: Int,
            val cause: Throwable? = null
        ) : RetryResult<T>()
    }

    suspend fun <T> executeWithRetry(
        config: RetryConfig = RetryConfig(),
        onAttempt: (attempt: Int, maxAttempts: Int) -> Unit = { _, _ -> },
        shouldRetry: (Throwable) -> Boolean = { true },
        block: suspend () -> T?
    ): RetryResult<T> {
        var lastThrowable: Throwable? = null
        var lastError: VideoLoadError = VideoLoadError.UnknownError(Exception("No attempts made"))
        var currentDelay = config.initialDelayMs

        repeat(config.maxAttempts) { attempt ->
            onAttempt(attempt + 1, config.maxAttempts)
            Logger.d(TAG, " Attempt ${attempt + 1}/${config.maxAttempts}")

            try {
                val result = block()
                if (result != null) {
                    Logger.d(TAG, " Success on attempt ${attempt + 1}")
                    return RetryResult.Success(result)
                }
                val nullResultError = IllegalStateException("Result was null")
                lastThrowable = nullResultError
                lastError = VideoLoadError.UnknownError(nullResultError)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(TAG, " Attempt ${attempt + 1} failed: ${error.message}")
                lastThrowable = error
                lastError = VideoLoadError.fromException(error)
                if (!shouldRetry(error)) {
                    Logger.d(TAG, " Error is not retryable, stopping")
                    return RetryResult.Failure(lastError, attempt + 1, error)
                }
            }

            if (attempt < config.maxAttempts - 1) {
                Logger.d(TAG, " Waiting ${currentDelay}ms before next attempt")
                delay(currentDelay)
                currentDelay = nextDelayMs(
                    currentDelay = currentDelay,
                    multiplier = config.multiplier,
                    maxDelayMs = config.maxDelayMs
                )
            }
        }

        android.util.Log.e(TAG, " All ${config.maxAttempts} attempts failed")
        return RetryResult.Failure(lastError, config.maxAttempts, lastThrowable)
    }

    suspend fun <T> retryOrThrow(
        config: RetryConfig = RetryConfig(),
        onAttempt: (attempt: Int, maxAttempts: Int) -> Unit = { _, _ -> },
        block: suspend () -> T?
    ): T {
        return when (val result = executeWithRetry(config, onAttempt, block = block)) {
            is RetryResult.Success -> result.data
            is RetryResult.Failure -> throw Exception(
                result.error.toUserMessage(),
                result.cause
            )
        }
    }

    internal fun nextDelayMs(
        currentDelay: Long,
        multiplier: Double,
        maxDelayMs: Long
    ): Long {
        if (currentDelay >= maxDelayMs) return maxDelayMs
        val scaled = currentDelay.toDouble() * multiplier
        if (!scaled.isFinite() || scaled >= maxDelayMs.toDouble()) return maxDelayMs
        return scaled.toLong().coerceAtLeast(currentDelay).coerceAtMost(maxDelayMs)
    }
}
