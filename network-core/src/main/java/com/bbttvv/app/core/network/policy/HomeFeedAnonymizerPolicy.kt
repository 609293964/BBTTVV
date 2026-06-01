package com.bbttvv.app.core.network.policy

import java.util.UUID

private const val BILIBILI_API_HOST = "api.bilibili.com"
private const val WEB_HOME_FEED_PATH = "/x/web-interface/wbi/index/top/feed/rcmd"
private val ANONYMOUS_HOME_FEED_COOKIE_NAMES = setOf("buvid3")

data class HomeFeedAnonymizerStatsSnapshot(
    val totalHits: Long = 0L,
    val lastHitAtMs: Long? = null,
    val lastHitHost: String? = null,
    val lastHitEncodedPath: String? = null
)

object HomeFeedAnonymizerRuntime {
    private val statsLock = Any()
    private val anonymousSessionLock = Any()

    @Volatile
    private var enabledValue: Boolean = false

    private var statsValue: HomeFeedAnonymizerStatsSnapshot = HomeFeedAnonymizerStatsSnapshot()
    private var anonymousBuvid3Value: String? = null

    val enabled: Boolean
        get() = enabledValue

    val statsSnapshot: HomeFeedAnonymizerStatsSnapshot
        get() = synchronized(statsLock) { statsValue }

    val anonymousBuvid3: String
        get() = synchronized(anonymousSessionLock) {
            anonymousBuvid3Value ?: createAnonymousBuvid3().also { anonymousBuvid3Value = it }
        }

    fun setEnabled(enabled: Boolean) {
        enabledValue = enabled
        if (!enabled) {
            clearAnonymousSession()
        }
    }

    fun rotateAnonymousSession() {
        synchronized(anonymousSessionLock) {
            anonymousBuvid3Value = createAnonymousBuvid3()
        }
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

    private fun clearAnonymousSession() {
        synchronized(anonymousSessionLock) {
            anonymousBuvid3Value = null
        }
    }

    private fun createAnonymousBuvid3(): String {
        return UUID.randomUUID().toString().replace("-", "") + "infoc"
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
        cookieNames.filter(::isAnonymousHomeFeedCookieName)
    } else {
        cookieNames
    }
}

fun isAnonymousHomeFeedCookieName(name: String): Boolean {
    return name in ANONYMOUS_HOME_FEED_COOKIE_NAMES
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
