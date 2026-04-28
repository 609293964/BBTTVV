package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.AppSignUtils
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.data.model.response.SplashItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SplashRepository {
    // 使用 NetworkModule 直接获取 API 实例 (与 VideoRepository 保持一致)
    private val api = NetworkModule.splashApi

    /**
     * 获取官方壁纸列表
     * 优先使用 brand/list API (无广告，高质量)
     * 失败时回退到 splash/list API
     */
    suspend fun getOfficialWallpapers(): Result<List<SplashItem>> = withContext(Dispatchers.IO) {
        try {
            val params = mutableMapOf<String, String>()
            params["appkey"] = AppSignUtils.ANDROID_APP_KEY
            params["ts"] = AppSignUtils.getTimestamp().toString()
            val signedParams = AppSignUtils.signForAndroidApi(params)
            
            // 优先尝试 brand/list API (无广告)
            try {
                val brandResponse = api.getSplashBrandList(signedParams)
                if (brandResponse.code == 0 && brandResponse.data != null && brandResponse.data.list.isNotEmpty()) {
                    val items = brandResponse.data.list
                        .filter { it.thumb.isNotEmpty() } // 过滤掉没有图片的项
                        .map { brand ->
                            SplashItem(
                                id = brand.id,
                                thumb = brand.thumb,
                                logoUrl = brand.logoUrl,
                                title = "官方壁纸 #${brand.id}"
                            )
                        }
                    if (items.isNotEmpty()) {
                        return@withContext Result.success(items)
                    }
                }
            } catch (e: Exception) {
                // brand/list 失败，回退到 splash/list
                e.printStackTrace()
            }
            
            // 回退到原有 splash/list API
            val fullParams = mutableMapOf<String, String>()
            fullParams["mobi_app"] = "android"
            fullParams["device"] = "android"
            fullParams["platform"] = "android"
            fullParams["build"] = "7930010"
            fullParams["channel"] = "master"
            fullParams["width"] = "1080"
            fullParams["height"] = "1920"
            fullParams["ver"] = "1055052953259468962"
            fullParams["appkey"] = AppSignUtils.ANDROID_APP_KEY
            fullParams["ts"] = AppSignUtils.getTimestamp().toString()
            val signedFullParams = AppSignUtils.signForAndroidApi(fullParams)
            
            val response = api.getSplashList(signedFullParams)
            if (response.code == 0 && response.data != null) {
                // 过滤掉广告和没有图片的项
                val rawList = response.data.list
                val filteredList = rawList.filter { 
                    !it.isAd && (it.thumb.isNotEmpty() || it.image.isNotEmpty()) 
                }
                Result.success(if (filteredList.isNotEmpty()) filteredList else rawList.filter { it.thumb.isNotEmpty() || it.image.isNotEmpty() })
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

