package com.bbttvv.app.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

internal object NetworkRetrofitFactory : NetworkServiceFactory<OkHttpClient> {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override fun <T : Any> create(
        baseUrl: String,
        client: OkHttpClient,
        serviceClass: Class<T>,
    ): T {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(serviceClass)
    }
}
