package com.bbttvv.app.core.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Protocol

object NetworkModule {
    internal var appContext: Context? = null
    private val appSessionCookieJar = AppSessionCookieJar()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun clearRuntimeCookies() {
        appSessionCookieJar.clear()
    }

    internal fun resolveSharedNetworkProtocols(): List<Protocol> {
        return NetworkHttpClients.resolveSharedNetworkProtocols()
    }

    internal fun resolveApiHttpCacheBudgetBytes(): Long {
        return NetworkHttpClients.resolveApiHttpCacheBudgetBytes()
    }

    internal fun buildPlaybackOkHttpClient(sharedClient: OkHttpClient): OkHttpClient {
        return NetworkHttpClients.buildPlaybackOkHttpClient(sharedClient)
    }

    val okHttpClient: OkHttpClient by lazy {
        NetworkHttpClients.buildApiOkHttpClient(
            appContext = { appContext },
            appSessionCookieJar = appSessionCookieJar,
        )
    }

    val playbackOkHttpClient: OkHttpClient by lazy {
        buildPlaybackOkHttpClient(okHttpClient)
    }

    val guestOkHttpClient: OkHttpClient by lazy {
        NetworkHttpClients.buildGuestOkHttpClient(appContext = { appContext })
    }

    val guestApi: BilibiliApi by lazy {
        createApi("https://api.bilibili.com/", guestOkHttpClient, BilibiliApi::class.java)
    }

    val api: BilibiliApi by lazy {
        createApi("https://api.bilibili.com/", okHttpClient, BilibiliApi::class.java)
    }

    val passportApi: PassportApi by lazy {
        createApi("https://passport.bilibili.com/", okHttpClient, PassportApi::class.java)
    }

    val searchApi: SearchApi by lazy {
        createApi("https://api.bilibili.com/", okHttpClient, SearchApi::class.java)
    }

    val articleApi: ArticleApi by lazy {
        createApi("https://api.bilibili.com/", okHttpClient, ArticleApi::class.java)
    }

    val dynamicApi: DynamicApi by lazy {
        createApi("https://api.bilibili.com/", okHttpClient, DynamicApi::class.java)
    }

    val buvidApi: BuvidApi by lazy {
        createApi("https://api.bilibili.com/", okHttpClient, BuvidApi::class.java)
    }

    val spaceApi: SpaceApi by lazy {
        createApi("https://api.bilibili.com/", okHttpClient, SpaceApi::class.java)
    }

    val bangumiApi: BangumiApi by lazy {
        createApi("https://api.bilibili.com/", okHttpClient, BangumiApi::class.java)
    }

    val storyApi: StoryApi by lazy {
        createApi("https://app.bilibili.com/", okHttpClient, StoryApi::class.java)
    }

    val splashApi: SplashApi by lazy {
        createApi("https://app.bilibili.com/", okHttpClient, SplashApi::class.java)
    }

    val messageApi: MessageApi by lazy {
        createApi("https://api.vc.bilibili.com/", okHttpClient, MessageApi::class.java)
    }

    val audioApi: AudioApi by lazy {
        createApi("https://www.bilibili.com/", okHttpClient, AudioApi::class.java)
    }

    private fun <T> createApi(
        baseUrl: String,
        client: OkHttpClient,
        serviceClass: Class<T>,
    ): T {
        return NetworkRetrofitFactory.create(
            baseUrl = baseUrl,
            client = client,
            serviceClass = serviceClass,
        )
    }
}
