package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.SimpleApiResponse
import retrofit2.http.GET
import retrofit2.http.POST

@kotlinx.serialization.Serializable
data class BuvidSpiData(
    val b_3: String = "",
    val b_4: String = ""
)

@kotlinx.serialization.Serializable
data class BuvidSpiResponse(
    val code: Int = 0,
    val data: BuvidSpiData? = null
)

interface BuvidApi {
    @GET("x/frontend/finger/spi")
    suspend fun getSpi(): BuvidSpiResponse

    @retrofit2.http.FormUrlEncoded
    @POST("x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activateBuvid(
        @retrofit2.http.Field("payload") payload: String
    ): SimpleApiResponse
}
