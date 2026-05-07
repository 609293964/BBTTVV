// 文件路径: core/util/RetryStrategy.kt
package com.bbttvv.app.core.util

import kotlinx.coroutines.delay
import com.bbttvv.app.data.model.VideoLoadError

/**
 * 重试策略工具类
 * 
 * 提供指数退避重试机制，用于处理网络请求失败等临时性错误。
 */
object RetryStrategy {
    
    private const val TAG = "RetryStrategy"
    
    /**
     * 重试配置
     * 
     * @param maxAttempts 最大尝试次数（包括首次）
     * @param initialDelayMs 首次重试前的延迟（毫秒）
     * @param maxDelayMs 最大延迟时间（毫秒）
     * @param multiplier 延迟时间的倍增因子
     */
    data class RetryConfig(
        val maxAttempts: Int = 4,
        val initialDelayMs: Long = 500,
        val maxDelayMs: Long = 5000,
        val multiplier: Double = 2.0
    )
    
    /**
     * 重试结果
     */
    sealed class RetryResult<out T> {
        data class Success<T>(val data: T) : RetryResult<T>()
        data class Failure<T>(val error: VideoLoadError, val attemptsMade: Int) : RetryResult<T>()
    }
    
    /**
     * 执行带重试的操作
     * 
     * @param config 重试配置
     * @param onAttempt 每次尝试时的回调，用于更新 UI 进度
     * @param shouldRetry 判断异常是否应该重试的函数
     * @param block 要执行的挂起函数
     * @return 操作结果
     */
    suspend fun <T> executeWithRetry(
        config: RetryConfig = RetryConfig(),
        onAttempt: (attempt: Int, maxAttempts: Int) -> Unit = { _, _ -> },
        shouldRetry: (Throwable) -> Boolean = { true },
        block: suspend () -> T?
    ): RetryResult<T> {
        var lastError: VideoLoadError = VideoLoadError.UnknownError(Exception("No attempts made"))
        var currentDelay = config.initialDelayMs
        
        repeat(config.maxAttempts) { attempt ->
            // 通知 UI 当前尝试次数
            onAttempt(attempt + 1, config.maxAttempts)
            com.bbttvv.app.core.util.Logger.d(TAG, " Attempt ${attempt + 1}/${config.maxAttempts}")
            
            try {
                val result = block()
                if (result != null) {
                    com.bbttvv.app.core.util.Logger.d(TAG, " Success on attempt ${attempt + 1}")
                    return RetryResult.Success(result)
                }
                // 结果为 null，视为失败
                lastError = VideoLoadError.UnknownError(Exception("Result was null"))
                
            } catch (e: Exception) {
                android.util.Log.w(TAG, " Attempt ${attempt + 1} failed: ${e.message}")
                lastError = VideoLoadError.fromException(e)
                
                // 检查是否应该重试
                if (!shouldRetry(e)) {
                    com.bbttvv.app.core.util.Logger.d(TAG, "🛑 Error is not retryable, stopping")
                    return RetryResult.Failure(lastError, attempt + 1)
                }
            }
            
            // 最后一次尝试不需要等待
            if (attempt < config.maxAttempts - 1) {
                com.bbttvv.app.core.util.Logger.d(TAG, "⏳ Waiting ${currentDelay}ms before next attempt")
                delay(currentDelay)
                // 计算下一次延迟（指数退避）
                currentDelay = (currentDelay * config.multiplier).toLong()
                    .coerceAtMost(config.maxDelayMs)
            }
        }
        
        android.util.Log.e(TAG, " All ${config.maxAttempts} attempts failed")
        return RetryResult.Failure(lastError, config.maxAttempts)
    }
    
    /**
     * 简化版重试，直接返回结果或抛出异常
     */
    suspend fun <T> retryOrThrow(
        config: RetryConfig = RetryConfig(),
        onAttempt: (attempt: Int, maxAttempts: Int) -> Unit = { _, _ -> },
        block: suspend () -> T?
    ): T {
        return when (val result = executeWithRetry(config, onAttempt, block = block)) {
            is RetryResult.Success -> result.data
            is RetryResult.Failure -> throw Exception(result.error.toUserMessage())
        }
    }
}

