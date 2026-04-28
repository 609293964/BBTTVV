package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.data.model.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FavoriteRepository {
    private val api = NetworkModule.api

    data class CollectedFavFoldersPage(
        val folders: List<FavFolder>,
        val totalCount: Int
    )

    suspend fun getFavFolders(mid: Long): Result<List<FavFolder>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getFavFolders(mid)
                if (response.code == 0) {
                    Result.success(
                        response.data?.list
                            ?.map { it.copy(source = FavFolderSource.OWNED) }
                            ?: emptyList()
                    )
                } else {
                    Result.failure(Exception("获取收藏夹失败: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
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
            try {
                val response = api.getCollectedFavFolders(mid = mid, pn = pn, ps = ps, platform = platform)
                if (response.code == 0) {
                    Result.success(
                        CollectedFavFoldersPage(
                            folders = response.data?.list
                                ?.map { it.copy(source = FavFolderSource.SUBSCRIBED) }
                                ?: emptyList(),
                            totalCount = response.data?.count ?: 0
                        )
                    )
                } else {
                    Result.failure(Exception("获取收藏合集失败: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
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
            try {
                // pn defaults to 1 if not passed, but here we pass it
                val response = api.getFavoriteList(
                    mediaId = mediaId,
                    pn = pn,
                    ps = ps,
                    keyword = keyword,
                    platform = platform
                )
                if (response.code == 0 && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(Exception(response.message))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getWatchLaterList(): Result<WatchLaterData> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getWatchLaterList()
                if (response.code == 0 && response.data != null) {
                    Result.success(response.data)
                } else {
                    Result.failure(
                        Exception(response.message.ifEmpty { "获取稍后再看失败: ${response.code}" })
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun removeResource(mediaId: Long, resourceId: Long): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val csrf = com.bbttvv.app.core.store.TokenManager.csrfCache ?: ""
                // type=2 代表视频
                val resourceStr = "$resourceId:2"
                val response = api.batchDelFavResource(mediaId, resourceStr, csrf)
                
                if (response.code == 0) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(response.message))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

