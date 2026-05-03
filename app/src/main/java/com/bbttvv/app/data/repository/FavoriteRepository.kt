package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.store.TokenManager
import com.bbttvv.app.core.util.safeApiCall
import com.bbttvv.app.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FavoriteRepository {
    private const val TAG = "FavoriteRepo"
    private val api = NetworkModule.api

    data class CollectedFavFoldersPage(
        val folders: List<FavFolder>,
        val totalCount: Int
    )

    suspend fun getFavFolders(mid: Long): Result<List<FavFolder>> {
        return withContext(Dispatchers.IO) {
            safeApiCall(
                tag = TAG,
                errorMessage = { "getFavFolders failed: mid=$mid" }
            ) {
                val response = api.getFavFolders(mid)
                if (response.code == 0) {
                    response.data?.list
                        ?.map { it.copy(source = FavFolderSource.OWNED) }
                        ?: emptyList()
                } else {
                    throw Exception("获取收藏夹失败: ${response.code}")
                }
            }
        }
    }

    suspend fun getCollectedFavFolders(
        mid: Long,
        pn: Int = 1,
        ps: Int = 20,
        platform: String = "web"
    ): Result<CollectedFavFoldersPage> {
        return withContext(Dispatchers.IO) {
            safeApiCall(
                tag = TAG,
                errorMessage = { "getCollectedFavFolders failed: mid=$mid, pn=$pn" }
            ) {
                val response = api.getCollectedFavFolders(mid = mid, pn = pn, ps = ps, platform = platform)
                if (response.code == 0) {
                    CollectedFavFoldersPage(
                        folders = response.data?.list
                            ?.map { it.copy(source = FavFolderSource.SUBSCRIBED) }
                            ?: emptyList(),
                        totalCount = response.data?.count ?: 0
                    )
                } else {
                    throw Exception("获取收藏合集失败: ${response.code}")
                }
            }
        }
    }

    suspend fun getFavoriteList(
        mediaId: Long,
        pn: Int,
        ps: Int = 20,
        keyword: String? = null,
        platform: String = "web"
    ): Result<FavoriteResourceData> {
        return withContext(Dispatchers.IO) {
            safeApiCall(
                tag = TAG,
                errorMessage = { "getFavoriteList failed: mediaId=$mediaId, pn=$pn" }
            ) {
                // pn defaults to 1 if not passed, but here we pass it
                val response = api.getFavoriteList(
                    mediaId = mediaId,
                    pn = pn,
                    ps = ps,
                    keyword = keyword,
                    platform = platform
                )
                if (response.code == 0 && response.data != null) {
                    response.data
                } else {
                    throw Exception(response.message)
                }
            }
        }
    }

    suspend fun getWatchLaterList(): Result<WatchLaterData> {
        return withContext(Dispatchers.IO) {
            safeApiCall(
                tag = TAG,
                errorMessage = { "getWatchLaterList failed" }
            ) {
                val response = api.getWatchLaterList()
                if (response.code == 0 && response.data != null) {
                    response.data
                } else {
                    throw Exception(response.message.ifEmpty { "获取稍后再看失败: ${response.code}" })
                }
            }
        }
    }

    suspend fun removeResource(mediaId: Long, resourceId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            safeApiCall(
                tag = TAG,
                errorMessage = { "removeResource failed: mediaId=$mediaId, resourceId=$resourceId" }
            ) {
                val csrf = TokenManager.csrfCache ?: ""
                // type=2 代表视频
                val resourceStr = "$resourceId:2"
                val response = api.batchDelFavResource(mediaId, resourceStr, csrf)
                
                if (response.code == 0) {
                    true
                } else {
                    throw Exception(response.message)
                }
            }
        }
    }
}

