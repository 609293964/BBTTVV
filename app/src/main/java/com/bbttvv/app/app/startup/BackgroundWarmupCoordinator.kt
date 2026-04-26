package com.bbttvv.app.app.startup

import android.content.Context
import android.os.SystemClock
import com.bbttvv.app.core.network.NetworkWarmup
import com.bbttvv.app.core.store.StartupSettingsCache
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.repository.CommentRepository
import com.bbttvv.app.data.repository.DanmakuRepository
import com.bbttvv.app.data.repository.LiveRepository
import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Locale

internal object BackgroundWarmupCoordinator {
    private const val TAG = "BackgroundWarmup"

    @Volatile
    private var started = false

    fun warmup(context: Context) {
        if (!markStarted()) return
        val appContext = context.applicationContext

        runBlocking {
            Logger.i(TAG, "background warmup started")
            runWarmupTask("settings_cache_warmup", timeoutMs = 600L) {
                val snapshot = StartupSettingsCache.warmup(appContext)
                Logger.d(
                    TAG,
                    "settings cache ready feed=${snapshot.feedApiType.value}, " +
                        "homeRefresh=${snapshot.homeRefreshCount}, ipv4Only=${snapshot.ipv4OnlyEnabled}"
                )
            }
            runWarmupTask("session_validation_warmup", timeoutMs = 2_500L) {
                TokenManager.awaitWarmup(appContext)
                val navInfo = SubtitleAndAuxRepository.getNavInfo().getOrNull()
                Logger.d(TAG, "session validation ready isLogin=${navInfo?.isLogin ?: false}")
            }
            runWarmupTask("danmaku_config_warmup", timeoutMs = 2_500L) {
                val filter = DanmakuRepository.getDanmakuUserFilter(forceRefresh = false)
                val emoteCount = CommentRepository.warmupEmoteMap()
                Logger.d(
                    TAG,
                    "danmaku config ready filterEmpty=${filter.isEmpty()}, emoteCount=$emoteCount"
                )
            }
            runWarmupTask("live_metadata_cache_warmup", timeoutMs = 2_500L) {
                val areaCount = LiveRepository.warmupLiveMetadata()
                Logger.d(TAG, "live metadata ready areaCount=$areaCount")
            }
            runWarmupTask("network_preconnect_warmup", timeoutMs = 4_000L) {
                val result = NetworkWarmup.warmupConnections()
                Logger.d(
                    TAG,
                    "network preconnect ready dns=${result.dnsResolvedCount}, " +
                        "http=${result.httpPreconnectCount}, images=${result.imagePreconnectCount}"
                )
            }
            Logger.i(TAG, "background warmup finished")
        }
    }

    private fun markStarted(): Boolean {
        if (started) return false
        synchronized(this) {
            if (started) return false
            started = true
            return true
        }
    }

    private suspend fun runWarmupTask(
        id: String,
        timeoutMs: Long,
        block: suspend () -> Unit
    ) {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val result = try {
            withTimeout(timeoutMs) {
                block()
            }
            WarmupTaskResult.Completed
        } catch (error: TimeoutCancellationException) {
            WarmupTaskResult.TimedOut
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            WarmupTaskResult.Failed(error)
        }
        val durationMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
        when (result) {
            WarmupTaskResult.Completed -> Logger.i(
                TAG,
                "$id completed duration=${String.format(Locale.US, "%.3f", durationMs)}ms"
            )
            WarmupTaskResult.TimedOut -> Logger.w(
                TAG,
                "$id timed out duration=${String.format(Locale.US, "%.3f", durationMs)}ms timeout=${timeoutMs}ms"
            )
            is WarmupTaskResult.Failed -> Logger.w(
                TAG,
                "$id failed duration=${String.format(Locale.US, "%.3f", durationMs)}ms error=${result.error.message}"
            )
        }
    }

    private sealed interface WarmupTaskResult {
        data object Completed : WarmupTaskResult
        data object TimedOut : WarmupTaskResult
        data class Failed(val error: Throwable) : WarmupTaskResult
    }
}
