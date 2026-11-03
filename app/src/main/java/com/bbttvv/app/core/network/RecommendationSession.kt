package com.bbttvv.app.core.network

import android.content.Context
import com.bbttvv.app.core.store.AccountSessionStore
import com.bbttvv.app.core.store.RecommendationAccountStore
import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerRuntime
import com.bbttvv.app.core.network.policy.resolveHomeFeedCookieAnonymizerDecision
import okhttp3.CookieJar

/** Immutable request identity: a later active-account switch cannot mix token and Cookie. */
internal class RecommendationSession private constructor(
    val mid: Long,
    val accessToken: String,
    private val sessData: String,
    private val csrf: String,
    private val buvid3: String,
) {
    override fun toString() = "RecommendationSession(redacted)"

    val api: BilibiliApi by lazy {
        val client = NetworkModule.okHttpClient.newBuilder()
            .cache(null)
            .cookieJar(CookieJar.NO_COOKIES)
            .addNetworkInterceptor { chain ->
                val original = chain.request()
                val url = original.url
                val anonymous = resolveHomeFeedCookieAnonymizerDecision(
                    HomeFeedAnonymizerRuntime.enabled, url.host, url.encodedPath)
                val cookie = recommendationCookieHeader(url.host, url.encodedPath, anonymous, sessData, csrf, buvid3)
                val request = original.newBuilder().removeHeader("Cookie")
                    .header("Cache-Control", "no-store")
                if (cookie.isNotEmpty()) request.header("Cookie", cookie)
                // App feed / feedback authenticate with the captured access_key, never the active CookieJar.
                chain.proceed(request.build())
            }.build()
        NetworkRetrofitFactory.create("https://api.bilibili.com/", client, BilibiliApi::class.java)
    }

    companion object {
        /** Explicit origins are used for feedback; a missing origin must never fall back to another account. */
        suspend fun capture(context: Context, originMid: Long? = null): RecommendationSession? {
            val current = AccountSessionStore.captureCurrentSession()
            val currentMid = current.mid
            val selected = originMid ?: RecommendationAccountStore.selectedMid(context)
            if (selected == null || selected == currentMid) {
                return RecommendationSession(currentMid, current.accessToken, current.sessData, current.csrf, current.buvid3)
            }
            val account = AccountSessionStore.getAccountsResult(context).getOrThrow()
                .firstOrNull { it.mid == (selected ?: currentMid) }
            if (account != null) return RecommendationSession(account.mid, account.accessToken,
                account.sessData, account.csrf, account.buvid3.ifBlank { current.buvid3 })
            if (originMid != null) return null
            return RecommendationSession(currentMid, current.accessToken, current.sessData, current.csrf, current.buvid3)
        }
    }
}
