package com.bbttvv.app.core.network.policy

private const val BILIBILI_API_HOST = "api.bilibili.com"
private const val WEB_HOME_FEED_PATH = "/x/web-interface/wbi/index/top/feed/rcmd"

data class HomeFeedAnonymizerStatsSnapshot(
    val totalHits: Long = 0L,
    val lastHitAtMs: Long? = null,
    val lastHitHost: String? = null,
    val lastHitEncodedPath: String? = null
)

object HomeFeedAnonymizerRuntime {
    private val statsLock = Any()

    @Volatile
    private var enabledValue: Boolean = false

    private var statsValue: HomeFeedAnonymizerStatsSnapshot = HomeFeedAnonymizerStatsSnapshot()

    val enabled: Boolean
        get() = enabledValue

    val statsSnapshot: HomeFeedAnonymizerStatsSnapshot
        get() = synchronized(statsLock) { statsValue }

    fun setEnabled(enabled: Boolean) {
        enabledValue = enabled
    }

    fun recordHit(
        host: String,
        encodedPath: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        synchronized(statsLock) {
            statsValue = statsValue.copy(
                totalHits = statsValue.totalHits + 1L,
                lastHitAtMs = nowMs,
                lastHitHost = host,
                lastHitEncodedPath = encodedPath
            )
        }
    }

    fun resetStats() {
        synchronized(statsLock) {
            statsValue = HomeFeedAnonymizerStatsSnapshot()
        }
    }
}

fun shouldClearHomeFeedCookies(
    pluginEnabled: Boolean,
    host: String,
    encodedPath: String
): Boolean {
    return pluginEnabled &&
        host == BILIBILI_API_HOST &&
        encodedPath == WEB_HOME_FEED_PATH
}

fun filterHomeFeedCookieHeaderNames(
    pluginEnabled: Boolean,
    host: String,
    encodedPath: String,
    cookieNames: List<String>
): List<String> {
    return if (shouldClearHomeFeedCookies(
            pluginEnabled = pluginEnabled,
            host = host,
            encodedPath = encodedPath
        )
    ) {
        emptyList()
    } else {
        cookieNames
    }
}

fun resolveHomeFeedCookieAnonymizerDecision(
    pluginEnabled: Boolean,
    host: String,
    encodedPath: String,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
    val shouldClear = shouldClearHomeFeedCookies(
        pluginEnabled = pluginEnabled,
        host = host,
        encodedPath = encodedPath
    )
    if (shouldClear) {
        HomeFeedAnonymizerRuntime.recordHit(
            host = host,
            encodedPath = encodedPath,
            nowMs = nowMs
        )
    }
    return shouldClear
}
