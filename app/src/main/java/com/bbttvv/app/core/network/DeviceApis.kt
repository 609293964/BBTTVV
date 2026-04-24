package com.bbttvv.app.core.network

import com.bbttvv.app.data.model.response.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

@kotlinx.serialization.Serializable
data class BuvidSpiData(
    val b_3: String = "",  // buvid3
    val b_4: String = ""   // buvid4
)

@kotlinx.serialization.Serializable
data class BuvidSpiResponse(
    val code: Int = 0,
    val data: BuvidSpiData? = null
)

//  [新增] Buvid API
interface BuvidApi {
    @GET("x/frontend/finger/spi")
    suspend fun getSpi(): BuvidSpiResponse
    
    //  Buvid 激活 (PiliPala 中关键的一步)
    @retrofit2.http.FormUrlEncoded
    @POST("x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activateBuvid(
        @retrofit2.http.Field("payload") payload: String
    ): SimpleApiResponse
}

//  [新增] 开屏/壁纸 API
interface SplashApi {
    @GET("https://app.bilibili.com/x/v2/splash/list")
    suspend fun getSplashList(
        @QueryMap params: Map<String, String> // 包含 appkey, ts, sign 等
    ): com.bbttvv.app.data.model.response.SplashResponse
    
    // [新增] 品牌开屏壁纸列表 (无广告，高质量)
    @GET("https://app.bilibili.com/x/v2/splash/brand/list")
    suspend fun getSplashBrandList(
        @QueryMap params: Map<String, String>
    ): com.bbttvv.app.data.model.response.SplashBrandResponse
}
