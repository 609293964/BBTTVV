package com.bbttvv.app.core.network

import com.bbttvv.app.core.util.Logger
import java.util.UUID
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class GuestCookieJar : CookieJar {
    private val guestBuvid3: String by lazy {
        UUID.randomUUID().toString().replace("-", "") + "infoc"
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Guest mode is intentionally stateless.
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        Logger.d(
            "GuestCookieJar",
            " ${url.encodedPath} request: guest mode with fresh buvid3=${guestBuvid3.take(15)}..."
        )
        return listOf(
            Cookie.Builder()
                .domain(url.host)
                .name("buvid3")
                .value(guestBuvid3)
                .build()
        )
    }
}
