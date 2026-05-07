// 文件路径: core/util/BilibiliUrlParser.kt
package com.bbttvv.app.core.util

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Bilibili URL 解析工具类
 * 
 * 支持解析以下格式：
 * - https://www.bilibili.com/video/BVxxxxxx
 * - https://www.bilibili.com/video/avxxxxxx
 * - https://m.bilibili.com/video/BVxxxxxx
 * - https://b23.tv/xxxxxx (短链接，需要重定向)
 * - BV1xx411c7mD / av12345 (纯 ID)
 */
object BilibiliUrlParser {
    
    private const val TAG = "BilibiliUrlParser"
    
    // BV 号正则表达式
    private val BV_REGEX = Regex("BV([a-zA-Z0-9]{10})", RegexOption.IGNORE_CASE)
    
    // AV 号正则表达式
    private val AV_REGEX = Regex("av(\\d+)", RegexOption.IGNORE_CASE)

    // /video/170001 这类纯数字 aid 路径（兼容 bilibili://video/170001 与 https://.../video/170001）
    private val VIDEO_AID_PATH_REGEX = Regex("/video/(\\d+)(?:[/?#]|$)", RegexOption.IGNORE_CASE)

    // /opus/{id} 图文动态链接（兼容 path-only 与完整 https URL）
    private val OPUS_DYNAMIC_REGEX = Regex(
        "(?:^|https?://(?:www\\.|m\\.)?bilibili\\.com)/opus/(\\d+)(?:[/?#]|$)",
        RegexOption.IGNORE_CASE
    )

    // https://t.bilibili.com/{id} 动态详情链接
    private val T_BILIBILI_DYNAMIC_REGEX = Regex(
        "https?://t\\.bilibili\\.com/(\\d+)(?:[/?#]|$)",
        RegexOption.IGNORE_CASE
    )
    
    /**
     * 解析结果数据类
     */
    data class ParseResult(
        val bvid: String? = null,
        val aid: Long? = null,
        val dynamicId: String? = null,
        val isValid: Boolean = false
    ) {
        /**
         * 获取用于 API 调用的视频 ID (优先 BV)
         */
        fun getVideoId(): String? = bvid ?: aid?.let { "av$it" }

        fun getDynamicTargetId(): String? = dynamicId
    }
    
    /**
     * 从任意文本中提取 Bilibili 视频信息
     * 
     * @param input URL 或包含 URL 的文本
     * @return 解析结果
     */
    fun parse(input: String): ParseResult {
        if (input.isBlank()) return ParseResult()
        
        // 首先尝试直接匹配 BV 号
        BV_REGEX.find(input)?.let { match ->
            val normalizedBvid = "BV${match.groupValues[1]}"
            Logger.d(TAG, "Found BV: $normalizedBvid")
            return ParseResult(bvid = normalizedBvid, isValid = true)
        }
        
        // 尝试匹配 AV 号
        AV_REGEX.find(input)?.let { match ->
            val aid = match.groupValues[1].toLongOrNull()
            if (aid != null) {
                Logger.d(TAG, "Found AV: $aid")
                return ParseResult(aid = aid, isValid = true)
            }
        }

        // 尝试匹配 /video/{aid} 结构
        VIDEO_AID_PATH_REGEX.find(input)?.let { match ->
            val aid = match.groupValues[1].toLongOrNull()
            if (aid != null) {
                Logger.d(TAG, "Found numeric video path aid: $aid")
                return ParseResult(aid = aid, isValid = true)
            }
        }

        resolveDynamicId(input)?.let { dynamicId ->
            Logger.d(TAG, "Found dynamic ID: $dynamicId")
            return ParseResult(dynamicId = dynamicId, isValid = true)
        }

        // 没有找到视频 ID
        Logger.d(TAG, "No video ID found in: $input")
        return ParseResult()
    }
    
    /**
     * 从 Uri 中提取视频信息
     */
    fun parseUri(uri: Uri?): ParseResult {
        if (uri == null) return ParseResult()
        return parseDeepLink(uri.toString())
    }

    fun parseDeepLink(rawUri: String): ParseResult {
        if (rawUri.isBlank()) return ParseResult()

        val javaUri = runCatching { URI(rawUri) }.getOrNull()
        val host = javaUri?.host ?: ""
        val path = javaUri?.path ?: ""
        val scheme = javaUri?.scheme?.lowercase() ?: ""
        val pathSegments = path.split("/").filter { it.isNotEmpty() }
        val queryMap = extractQueryParameters(javaUri?.rawQuery)

        Logger.d(TAG, "Parsing URI: scheme=$scheme, host=$host, path=$path")

        if (scheme in listOf("bilibili", "bili")) {
            return parseCustomSchemeUri(rawUri)
        }
        
        // b23.tv 短链接需要特殊处理
        if (host.contains("b23.tv")) {
            // 短链接优先尝试路径；失败时回退全量 URI，避免 query/fragment 携带视频 ID 被漏掉。
            val pathResult = parse(path)
            return if (pathResult.isValid) pathResult else parse(rawUri)
        }
        
        // bilibili.com 链接
        if (host.contains("bilibili.com")) {
            val pathResult = parse(path)
            if (pathResult.isValid) return pathResult
            resolveAidFromVideoPath(
                pathSegments = pathSegments,
                allowBareNumericLeading = false
            )?.let { aid ->
                Logger.d(TAG, "Found structured aid from bilibili.com path: $aid")
                return ParseResult(aid = aid, isValid = true)
            }
            resolveDynamicIdFromUri(host = host, pathSegments = pathSegments)?.let { dynamicId ->
                Logger.d(TAG, "Found structured dynamic ID from bilibili.com path: $dynamicId")
                return ParseResult(dynamicId = dynamicId, isValid = true)
            }
            return parse(rawUri)
        }

        resolveDynamicIdFromUri(host = host, pathSegments = pathSegments)?.let { dynamicId ->
            Logger.d(TAG, "Found structured dynamic ID from URI: $dynamicId")
            return ParseResult(dynamicId = dynamicId, isValid = true)
        }

        resolveWrappedUrlFromUri(queryMap)?.let { wrappedUrl ->
            val wrappedResult = parse(wrappedUrl)
            if (wrappedResult.isValid) {
                Logger.d(TAG, "Found target from wrapped URL: $wrappedUrl")
                return wrappedResult
            }
        }

        resolveDynamicIdFromCustomScheme(
            scheme = scheme,
            host = host,
            pathSegments = pathSegments,
            queryMap = queryMap
        )?.let { dynamicId ->
            Logger.d(TAG, "Found dynamic ID from custom scheme: $dynamicId")
            return ParseResult(dynamicId = dynamicId, isValid = true)
        }

        if (isExplicitVideoDeepLink(host = host, pathSegments = pathSegments)) {
            // 仅在明确视频上下文时，才接受 aid/bvid 这些视频参数，避免 opus/dynamic 等链接误判成视频。
            val queryCandidates = listOfNotNull(
                queryMap["bvid"],
                queryMap["BVID"],
                queryMap["aid"],
                queryMap["AID"],
                queryMap["avid"],
                queryMap["AVID"]
            )
            queryCandidates.forEach { candidate ->
                val parsed = parse(candidate)
                if (parsed.isValid) return parsed
            }
            resolveAidFromVideoPath(
                pathSegments = pathSegments,
                allowBareNumericLeading = true
            )?.let { aid ->
                Logger.d(TAG, "Found structured aid from custom scheme path: $aid")
                return ParseResult(aid = aid, isValid = true)
            }
        }
        
        // 尝试从整个 URI 中提取
        return parse(rawUri)
    }

    private fun resolveAidFromVideoPath(
        pathSegments: List<String>,
        allowBareNumericLeading: Boolean
    ): Long? {
        if (pathSegments.isEmpty()) return null
        // https://www.bilibili.com/video/170001
        if (pathSegments[0].equals("video", ignoreCase = true)) {
            return pathSegments.getOrNull(1)?.toLongOrNull()
        }
        // bilibili://video/170001 -> host=video, pathSegments[0]=170001
        return if (allowBareNumericLeading) {
            pathSegments[0].toLongOrNull()
        } else {
            null
        }
    }

    private fun resolveDynamicId(input: String): String? {
        OPUS_DYNAMIC_REGEX.find(input)?.groupValues?.getOrNull(1)?.takeIf(::isNumericId)?.let {
            return it
        }
        T_BILIBILI_DYNAMIC_REGEX.find(input)?.groupValues?.getOrNull(1)?.takeIf(::isNumericId)?.let {
            return it
        }
        return null
    }

    private fun resolveDynamicIdFromUri(
        host: String,
        pathSegments: List<String>
    ): String? {
        if (pathSegments.isEmpty()) return null
        if (host.equals("t.bilibili.com", ignoreCase = true)) {
            return pathSegments.firstOrNull()?.takeIf(::isNumericId)
        }
        if (pathSegments.first().equals("opus", ignoreCase = true)) {
            return pathSegments.getOrNull(1)?.takeIf(::isNumericId)
        }
        return null
    }

    private fun resolveWrappedUrlFromUri(queryMap: Map<String, String>): String? {
        val wrappedUrlKeys = listOf("url", "jump_url", "target_url", "web_url", "origin_url")
        wrappedUrlKeys.forEach { key ->
            queryMap[key]?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                return value
            }
        }
        return null
    }

    private fun resolveDynamicIdFromCustomScheme(
        scheme: String,
        host: String,
        pathSegments: List<String>,
        queryMap: Map<String, String>
    ): String? {
        if (scheme !in listOf("bilibili", "bili")) return null

        listOf("dynamic_id", "dyn_id", "dyn_id_str", "opus_id").forEach { key ->
            queryMap[key]?.takeIf(::isNumericId)?.let { return it }
        }

        if (host.equals("opus", ignoreCase = true) || host.equals("dynamic", ignoreCase = true)) {
            pathSegments.firstOrNull()?.takeIf(::isNumericId)?.let { return it }
            pathSegments.lastOrNull()?.takeIf(::isNumericId)?.let { return it }
            queryMap["id"]?.takeIf(::isNumericId)?.let { return it }
        }

        val opusIndex = pathSegments.indexOfFirst { it.equals("opus", ignoreCase = true) }
        if (opusIndex >= 0) {
            pathSegments.getOrNull(opusIndex + 1)?.takeIf(::isNumericId)?.let { return it }
        }

        return null
    }

    private fun parseCustomSchemeUri(rawUri: String): ParseResult {
        val javaUri = runCatching { URI(rawUri) }.getOrNull()
        val scheme = javaUri?.scheme?.lowercase() ?: ""
        val host = javaUri?.host ?: ""
        val pathSegments = javaUri?.path
            ?.split("/")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val queryMap = extractQueryParameters(javaUri?.rawQuery)

        resolveWrappedUrlFromUri(queryMap)?.let { wrappedUrl ->
            val wrappedResult = parse(wrappedUrl)
            if (wrappedResult.isValid) {
                Logger.d(TAG, "Found target from wrapped custom URL: $wrappedUrl")
                return wrappedResult
            }
        }

        resolveDynamicIdFromCustomScheme(
            scheme = scheme,
            host = host,
            pathSegments = pathSegments,
            queryMap = queryMap
        )?.let { dynamicId ->
            Logger.d(TAG, "Found dynamic ID from custom URI: $dynamicId")
            return ParseResult(dynamicId = dynamicId, isValid = true)
        }

        if (isExplicitVideoDeepLink(host = host, pathSegments = pathSegments)) {
            val queryCandidates = listOfNotNull(
                queryMap["bvid"],
                queryMap["BVID"],
                queryMap["aid"],
                queryMap["AID"],
                queryMap["avid"],
                queryMap["AVID"]
            )
            queryCandidates.forEach { candidate ->
                val parsed = parse(candidate)
                if (parsed.isValid) return parsed
            }
            resolveAidFromVideoPath(
                pathSegments = pathSegments,
                allowBareNumericLeading = true
            )?.let { aid ->
                Logger.d(TAG, "Found structured aid from custom URI path: $aid")
                return ParseResult(aid = aid, isValid = true)
            }
        }

        return parse(rawUri)
    }

    private fun extractQueryParameters(encodedQuery: String?): Map<String, String> {
        if (encodedQuery == null) return emptyMap()
        if (encodedQuery.isBlank()) return emptyMap()

        return encodedQuery
            .split("&")
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val pair = part.split("=", limit = 2)
                val key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(pair.getOrElse(1) { "" }, StandardCharsets.UTF_8.name())
                key to value
            }
            .toMap()
    }

    private fun isExplicitVideoDeepLink(
        host: String,
        pathSegments: List<String>
    ): Boolean {
        return host.equals("video", ignoreCase = true) ||
            pathSegments.firstOrNull()?.equals("video", ignoreCase = true) == true
    }

    private fun isNumericId(value: String): Boolean = value.isNotEmpty() && value.all(Char::isDigit)
    
    /**
     * 解析 b23.tv 短链接 (需要在 IO 线程调用)
     * 
     * @param shortUrl b23.tv 短链接
     * @return 完整 URL 或 null
     */
    suspend fun resolveShortUrl(shortUrl: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = URL(shortUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                
                val responseCode = connection.responseCode
                if (responseCode in 300..399) {
                    val redirectUrl = connection.getHeaderField("Location")
                    Logger.d(TAG, "Short URL redirected to: $redirectUrl")
                    connection.disconnect()
                    redirectUrl
                } else {
                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to resolve short URL: ${e.message}")
                null
            }
        }
    }
    
    /**
     * 从任意文本中提取所有可能的 URL
     */
    fun extractUrls(text: String): List<String> {
        val urlRegex = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
        return urlRegex.findAll(text).map { it.value }.toList()
    }
}

