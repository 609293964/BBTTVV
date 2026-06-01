package com.bbttvv.app.core.network

import com.bbttvv.app.core.network.policy.HomeFeedAnonymizerRuntime
import com.bbttvv.app.core.network.policy.resolveHomeFeedCookieAnonymizerDecision
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.Logger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class AppSessionCookieJar : CookieJar {
    private val cookieLock = Any()
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        synchronized(cookieLock) {
            pruneExpiredCookiesLocked(nowMs = System.currentTimeMillis())
            val existingCookies = cookieStore.getOrPut(host) { mutableListOf() }
            cookies.forEach { newCookie ->
                removeCookieLocked(newCookie)
                if (newCookie.expiresAt > System.currentTimeMillis()) {
                    existingCookies.add(newCookie)
                    Logger.d("CookieJar", " Saved cookie: ${newCookie.name} for $host")
                } else {
                    Logger.d("CookieJar", " Removed expired cookie: ${newCookie.name} for $host")
                }
            }
            removeEmptyCookieBucketsLocked()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (resolveHomeFeedCookieAnonymizerDecision(
                pluginEnabled = HomeFeedAnonymizerRuntime.enabled,
                host = url.host,
                encodedPath = url.encodedPath
            )
        ) {
            Logger.d(
                "CookieJar",
                " 初见推荐匿名化首页推荐请求: ${url.encodedPath}, accountCookiesCleared=true"
            )
            return anonymousHomeFeedCookies(url)
        }

        TokenManager.awaitWarmupBlocking()
        val cookies = mutableListOf<Cookie>()

        synchronized(cookieLock) {
            pruneExpiredCookiesLocked(nowMs = System.currentTimeMillis())
            cookieStore.values.forEach { storedCookies ->
                storedCookies
                    .filter { it.matches(url) }
                    .forEach(cookies::add)
            }
        }

        val buvid3 = TokenManager.getOrCreateBuvid3()
        if (cookies.none { it.name == "buvid3" }) {
            cookies.add(
                Cookie.Builder()
                    .domain(url.host)
                    .name("buvid3")
                    .value(buvid3)
                    .build()
            )
        }

        val biliBiliDomain = if (url.host.endsWith("bilibili.com")) "bilibili.com" else url.host
        val sessData = TokenManager.sessDataCache
        if (!sessData.isNullOrEmpty()) {
            cookies.removeAll { it.name == "SESSDATA" }
            cookies.add(
                Cookie.Builder()
                    .domain(biliBiliDomain)
                    .name("SESSDATA")
                    .value(sessData)
                    .build()
            )
        }

        val biliJct = TokenManager.csrfCache
        if (!biliJct.isNullOrEmpty()) {
            cookies.removeAll { it.name == "bili_jct" }
            cookies.add(
                Cookie.Builder()
                    .domain(biliBiliDomain)
                    .name("bili_jct")
                    .value(biliJct)
                    .build()
            )
        }

        if (url.encodedPath.contains("playurl") || url.encodedPath.contains("pgc/view")) {
            Logger.d(
                "CookieJar",
                " ${url.encodedPath} request: domain=$biliBiliDomain, " +
                    "hasSess=${!sessData.isNullOrEmpty()}, hasCsrf=${!biliJct.isNullOrEmpty()}"
            )
        }

        return cookies
    }

    private fun anonymousHomeFeedCookies(url: HttpUrl): List<Cookie> {
        return listOf(
            Cookie.Builder()
                .domain(url.host)
                .name("buvid3")
                .value(HomeFeedAnonymizerRuntime.anonymousBuvid3)
                .build()
        )
    }

    fun clear() {
        synchronized(cookieLock) {
            cookieStore.clear()
        }
    }

    private fun pruneExpiredCookiesLocked(nowMs: Long) {
        cookieStore.values.forEach { cookies ->
            cookies.removeAll { it.expiresAt <= nowMs }
        }
        removeEmptyCookieBucketsLocked()
    }

    private fun removeCookieLocked(cookie: Cookie) {
        cookieStore.values.forEach { cookies ->
            cookies.removeAll { existing ->
                existing.name == cookie.name &&
                    existing.domain == cookie.domain &&
                    existing.path == cookie.path
            }
        }
    }

    private fun removeEmptyCookieBucketsLocked() {
        val emptyHosts = cookieStore
            .filterValues { it.isEmpty() }
            .keys
            .toList()
        emptyHosts.forEach(cookieStore::remove)
    }
}
