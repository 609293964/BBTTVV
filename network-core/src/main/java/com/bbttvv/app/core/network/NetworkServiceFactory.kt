package com.bbttvv.app.core.network

interface NetworkServiceFactory<Client : Any> {
    fun <T : Any> create(
        baseUrl: String,
        client: Client,
        serviceClass: Class<T>
    ): T
}
