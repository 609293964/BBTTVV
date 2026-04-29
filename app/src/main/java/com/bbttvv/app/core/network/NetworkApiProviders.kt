package com.bbttvv.app.core.network

import okhttp3.OkHttpClient

private const val BILIBILI_API_BASE_URL = "https://api.bilibili.com/"
private const val BILIBILI_APP_BASE_URL = "https://app.bilibili.com/"
private const val BILIBILI_PASSPORT_BASE_URL = "https://passport.bilibili.com/"
private const val BILIBILI_MESSAGE_BASE_URL = "https://api.vc.bilibili.com/"
private const val BILIBILI_WEB_BASE_URL = "https://www.bilibili.com/"

internal object ApiClientProvider {
    private val appSessionCookieJar = AppSessionCookieJar()

    fun clearRuntimeCookies() {
        appSessionCookieJar.clear()
    }

    val okHttpClient: OkHttpClient by lazy {
        NetworkHttpClients.buildApiOkHttpClient(
            appContext = { NetworkModule.appContext },
            appSessionCookieJar = appSessionCookieJar,
        )
    }

    val playbackOkHttpClient: OkHttpClient by lazy {
        NetworkHttpClients.buildPlaybackOkHttpClient(okHttpClient)
    }

    val guestOkHttpClient: OkHttpClient by lazy {
        NetworkHttpClients.buildGuestOkHttpClient(appContext = { NetworkModule.appContext })
    }
}

internal object AuthApiProvider {
    val passportApi: PassportApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_PASSPORT_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = PassportApi::class.java,
        )
    }
}

internal object CoreBilibiliApiProvider {
    val guestApi: BilibiliApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_API_BASE_URL,
            client = ApiClientProvider.guestOkHttpClient,
            serviceClass = BilibiliApi::class.java,
        )
    }

    val api: BilibiliApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_API_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = BilibiliApi::class.java,
        )
    }

    val searchApi: SearchApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_API_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = SearchApi::class.java,
        )
    }

    val articleApi: ArticleApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_API_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = ArticleApi::class.java,
        )
    }

    val dynamicApi: DynamicApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_API_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = DynamicApi::class.java,
        )
    }

    val buvidApi: BuvidApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_API_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = BuvidApi::class.java,
        )
    }

    val spaceApi: SpaceApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_API_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = SpaceApi::class.java,
        )
    }

    val bangumiApi: BangumiApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_API_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = BangumiApi::class.java,
        )
    }
}

internal object AppApiProvider {
    val storyApi: StoryApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_APP_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = StoryApi::class.java,
        )
    }

    val splashApi: SplashApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_APP_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = SplashApi::class.java,
        )
    }
}

internal object MessagingMediaApiProvider {
    val messageApi: MessageApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_MESSAGE_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = MessageApi::class.java,
        )
    }

    val audioApi: AudioApi by lazy {
        createNetworkApi(
            baseUrl = BILIBILI_WEB_BASE_URL,
            client = ApiClientProvider.okHttpClient,
            serviceClass = AudioApi::class.java,
        )
    }
}

private fun <T> createNetworkApi(
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
