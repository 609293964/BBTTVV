package com.bbttvv.app.core.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Protocol

object NetworkModule {
    internal var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun clearRuntimeCookies() {
        ApiClientProvider.clearRuntimeCookies()
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
        ApiClientProvider.okHttpClient
    }

    val playbackOkHttpClient: OkHttpClient by lazy {
        ApiClientProvider.playbackOkHttpClient
    }

    val guestOkHttpClient: OkHttpClient by lazy {
        ApiClientProvider.guestOkHttpClient
    }

    val guestApi: BilibiliApi by lazy {
        CoreBilibiliApiProvider.guestApi
    }

    val api: BilibiliApi by lazy {
        CoreBilibiliApiProvider.api
    }

    val passportApi: PassportApi by lazy {
        AuthApiProvider.passportApi
    }

    val searchApi: SearchApi by lazy {
        CoreBilibiliApiProvider.searchApi
    }

    val articleApi: ArticleApi by lazy {
        CoreBilibiliApiProvider.articleApi
    }

    val dynamicApi: DynamicApi by lazy {
        CoreBilibiliApiProvider.dynamicApi
    }

    val buvidApi: BuvidApi by lazy {
        CoreBilibiliApiProvider.buvidApi
    }

    val spaceApi: SpaceApi by lazy {
        CoreBilibiliApiProvider.spaceApi
    }

    val bangumiApi: BangumiApi by lazy {
        CoreBilibiliApiProvider.bangumiApi
    }

    val storyApi: StoryApi by lazy {
        AppApiProvider.storyApi
    }

    val splashApi: SplashApi by lazy {
        AppApiProvider.splashApi
    }

    val messageApi: MessageApi by lazy {
        MessagingMediaApiProvider.messageApi
    }

    val audioApi: AudioApi by lazy {
        MessagingMediaApiProvider.audioApi
    }
}
