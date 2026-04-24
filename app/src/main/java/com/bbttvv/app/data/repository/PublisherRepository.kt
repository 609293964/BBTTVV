package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.WbiKeyManager
import com.bbttvv.app.core.network.WbiUtils
import com.bbttvv.app.core.util.ApiErrorCodes
import com.bbttvv.app.core.util.getApiErrorMessage
import com.bbttvv.app.data.model.response.Owner
import com.bbttvv.app.data.model.response.SpaceVideoItem
import com.bbttvv.app.data.model.response.Stat
import com.bbttvv.app.data.model.response.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

object PublisherRepository {
    private const val PublisherVideoPageSize = 30

    private val spaceApi = NetworkModule.spaceApi
    private val navApi = NetworkModule.api

    enum class PublisherSortOrder(val apiValue: String, val label: String) {
        TIME("pubdate", "时间"),
        HOT("click", "热度")
    }

    data class PublisherHeader(
        val mid: Long,
        val name: String,
        val face: String
    )

    data class PublisherVideosPage(
        val page: Int,
        val items: List<VideoItem>,
        val hasMore: Boolean
    )

    suspend fun getPublisherHeader(mid: Long): Result<PublisherHeader> = withContext(Dispatchers.IO) {
        try {
            if (mid <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("无效的发布者 ID"))
            }

            val signedParams = signWithWbi(
                linkedMapOf("mid" to mid.toString())
            )
            val infoResponse = runCatching {
                spaceApi.getSpaceInfo(signedParams)
            }.getOrNull()
            val info = infoResponse?.data
            if (infoResponse?.code == 0 && info != null && info.name.isNotBlank()) {
                return@withContext Result.success(
                    PublisherHeader(
                        mid = info.mid.takeIf { it > 0L } ?: mid,
                        name = info.name,
                        face = info.face
                    )
                )
            }

            val aggregateResponse = spaceApi.getSpaceAggregate(mid = mid)
            val card = aggregateResponse.data?.card
            if (aggregateResponse.code == 0 && card != null && card.name.isNotBlank()) {
                return@withContext Result.success(
                    PublisherHeader(
                        mid = card.mid.toLongOrNull() ?: mid,
                        name = card.name,
                        face = card.face
                    )
                )
            }

            val failureMessage = infoResponse?.message
                ?.takeIf { it.isNotBlank() }
                ?: aggregateResponse.message.takeIf { it.isNotBlank() }
                ?: "发布者信息加载失败"
            Result.failure(Exception(failureMessage))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPublisherVideos(
        mid: Long,
        page: Int,
        ownerName: String? = null,
        sortOrder: PublisherSortOrder = PublisherSortOrder.TIME,
        pageSize: Int = PublisherVideoPageSize
    ): Result<PublisherVideosPage> = withContext(Dispatchers.IO) {
        val safePage = page.coerceAtLeast(1)
        loadPublisherVideos(
            mid = mid,
            page = safePage,
            ownerName = ownerName,
            sortOrder = sortOrder,
            pageSize = pageSize,
            allowRetry = true,
            forceRefreshWbi = false
        )
    }

    private suspend fun loadPublisherVideos(
        mid: Long,
        page: Int,
        ownerName: String?,
        sortOrder: PublisherSortOrder,
        pageSize: Int,
        allowRetry: Boolean,
        forceRefreshWbi: Boolean
    ): Result<PublisherVideosPage> {
        return try {
            if (mid <= 0L) {
                return Result.failure(IllegalArgumentException("无效的发布者 ID"))
            }

            val signedParams = signWithWbi(
                linkedMapOf(
                    "mid" to mid.toString(),
                    "pn" to page.toString(),
                    "ps" to pageSize.coerceAtLeast(1).toString(),
                    "order" to sortOrder.apiValue
                ),
                forceRefresh = forceRefreshWbi
            )
            val response = spaceApi.getSpaceVideos(signedParams)
            val data = response.data
            if (response.code != 0 || data == null) {
                if (allowRetry && shouldRetryPublisherVideosApi(response.code, response.message)) {
                    WbiKeyManager.invalidateCache()
                    return loadPublisherVideos(
                        mid = mid,
                        page = page,
                        ownerName = ownerName,
                        sortOrder = sortOrder,
                        pageSize = pageSize,
                        allowRetry = false,
                        forceRefreshWbi = true
                    )
                }
                return Result.failure(
                    Exception(resolvePublisherVideosApiMessage(response.code, response.message))
                )
            }

            val resolvedOwnerName = ownerName?.takeIf { it.isNotBlank() }
            val items = data.list.vlist.map { item ->
                item.toVideoItem(
                    mid = mid,
                    ownerName = resolvedOwnerName ?: item.author
                )
            }
            val hasMore = data.page.count > page * data.page.ps

            Result.success(
                PublisherVideosPage(
                    page = page,
                    items = items,
                    hasMore = hasMore
                )
            )
        } catch (e: Exception) {
            if (allowRetry && shouldRetryPublisherVideosThrowable(e)) {
                WbiKeyManager.invalidateCache()
                return loadPublisherVideos(
                    mid = mid,
                    page = page,
                    ownerName = ownerName,
                    sortOrder = sortOrder,
                    pageSize = pageSize,
                    allowRetry = false,
                    forceRefreshWbi = true
                )
            }
            Result.failure(Exception(resolvePublisherVideosThrowableMessage(e), e))
        }
    }

    private suspend fun signWithWbi(
        params: Map<String, String>,
        forceRefresh: Boolean = false
    ): Map<String, String> {
        return try {
            val keys = if (forceRefresh) {
                WbiKeyManager.refreshKeys().getOrNull()
            } else {
                WbiKeyManager.getWbiKeys().getOrNull()
                    ?: WbiKeyManager.refreshKeys().getOrNull()
            } ?: resolveWbiKeysFromNav()

            if (keys != null && keys.first.isNotBlank() && keys.second.isNotBlank()) {
                WbiUtils.sign(params, keys.first, keys.second)
            } else {
                params
            }
        } catch (_: Exception) {
            params
        }
    }

    private suspend fun resolveWbiKeysFromNav(): Pair<String, String>? {
        val navResp = navApi.getNavInfo()
        val wbiImg = navResp.data?.wbi_img ?: return null
        val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
        val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")
        return if (imgKey.isNotBlank() && subKey.isNotBlank()) {
            imgKey to subKey
        } else {
            null
        }
    }
}

internal fun shouldRetryPublisherVideosApi(code: Int, message: String): Boolean {
    if (code == ApiErrorCodes.TOO_MANY_REQUESTS || code == ApiErrorCodes.RISK_CONTROL) {
        return true
    }
    val text = message.lowercase()
    return text.contains("412") ||
        text.contains("429") ||
        text.contains("precondition") ||
        text.contains("风控") ||
        text.contains("risk")
}

internal fun shouldRetryPublisherVideosThrowable(error: Throwable): Boolean {
    val httpCode = (error as? HttpException)?.code()
    if (httpCode == 412 || httpCode == 429) {
        return true
    }
    val text = error.message.orEmpty().lowercase()
    return text.contains("412") ||
        text.contains("429") ||
        text.contains("precondition") ||
        text.contains("风控") ||
        text.contains("risk")
}

internal fun resolvePublisherVideosApiMessage(code: Int, message: String): String {
    return when {
        code == ApiErrorCodes.SUCCESS -> "成功"
        code == ApiErrorCodes.TOO_MANY_REQUESTS -> getApiErrorMessage(ApiErrorCodes.TOO_MANY_REQUESTS)
        code == ApiErrorCodes.RISK_CONTROL -> getApiErrorMessage(ApiErrorCodes.RISK_CONTROL)
        code == ApiErrorCodes.NOT_LOGIN -> getApiErrorMessage(ApiErrorCodes.NOT_LOGIN)
        message.isBlank() -> getApiErrorMessage(code, "发布者视频加载失败")
        shouldRetryPublisherVideosApi(code, message) -> "请求过于频繁，请稍后重试"
        else -> getApiErrorMessage(code, message)
    }
}

internal fun resolvePublisherVideosThrowableMessage(error: Throwable): String {
    val httpCode = (error as? HttpException)?.code()
    val rawMessage = error.message.orEmpty()
    val normalizedMessage = rawMessage.lowercase()
    return when {
        httpCode == 412 || httpCode == 429 || shouldRetryPublisherVideosThrowable(error) -> "请求过于频繁，请稍后重试"
        httpCode in 500..599 -> "服务暂时不可用，请稍后重试"
        normalizedMessage.contains("timeout") -> "网络请求超时，请稍后重试"
        error is IOException ||
            normalizedMessage.contains("unable to resolve host") ||
            normalizedMessage.contains("failed to connect") ||
            normalizedMessage.contains("connection reset") -> "网络连接失败，请稍后重试"
        rawMessage.isBlank() -> "发布者视频加载失败"
        else -> "发布者视频加载失败"
    }
}

internal fun normalizePublisherVideoErrorMessage(message: String?): String {
    val rawMessage = message.orEmpty().trim()
    val normalizedMessage = rawMessage.lowercase()
    return when {
        rawMessage.isBlank() -> "发布者视频加载失败"
        normalizedMessage.contains("http 412") ||
            normalizedMessage.contains("http 429") ||
            normalizedMessage.contains("precondition") ||
            normalizedMessage.contains("风控") ||
            normalizedMessage.contains("risk") -> "请求过于频繁，请稍后重试"
        normalizedMessage.contains("timeout") -> "网络请求超时，请稍后重试"
        normalizedMessage.contains("unable to resolve host") ||
            normalizedMessage.contains("failed to connect") ||
            normalizedMessage.contains("connection reset") -> "网络连接失败，请稍后重试"
        else -> rawMessage
    }
}

internal fun SpaceVideoItem.toVideoItem(
    mid: Long,
    ownerName: String
): VideoItem {
    return VideoItem(
        id = aid,
        aid = aid,
        bvid = bvid,
        title = title,
        pic = pic,
        owner = Owner(
            mid = mid,
            name = ownerName,
            face = ""
        ),
        stat = Stat(
            view = play,
            danmaku = comment
        ),
        duration = parsePublisherVideoDuration(length),
        pubdate = created
    )
}

internal fun parsePublisherVideoDuration(length: String): Int {
    if (length.isBlank()) return 0
    val parts = length.split(":")
        .map { it.trim().toIntOrNull() ?: return 0 }
    return when (parts.size) {
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}
