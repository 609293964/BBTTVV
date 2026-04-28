package com.bbttvv.app.core.util

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface BilibiliNavigationTarget {
    data class Video(val videoId: String) : BilibiliNavigationTarget
    data class Dynamic(val dynamicId: String) : BilibiliNavigationTarget
    data class Search(val keyword: String) : BilibiliNavigationTarget
    data class Space(val mid: Long) : BilibiliNavigationTarget
    data class Live(val roomId: Long) : BilibiliNavigationTarget
    data class BangumiSeason(val seasonId: Long) : BilibiliNavigationTarget
    data class BangumiEpisode(val epId: Long) : BilibiliNavigationTarget
    data class Music(val musicId: String) : BilibiliNavigationTarget
}

object BilibiliNavigationTargetParser {

    private val knownHosts = setOf(
        "b23.tv",
        "bilibili.com",
        "www.bilibili.com",
        "m.bilibili.com",
        "space.bilibili.com",
        "live.bilibili.com",
        "t.bilibili.com",
        "music.bilibili.com"
    )

    fun parse(input: String): BilibiliNavigationTarget? {
        if (input.isBlank()) return null

        parseSingleCandidate(input)?.let { return it }

        BilibiliUrlParser.extractUrls(input).forEach { url ->
            parseSingleCandidate(url)?.let { return it }
        }

        return null
    }

    suspend fun resolve(input: String): BilibiliNavigationTarget? {
        parse(input)?.let { return it }

        for (shortUrl in collectShortLinkCandidates(input)) {
            val resolvedUrl = BilibiliUrlParser.resolveShortUrl(shortUrl) ?: continue
            parse(resolvedUrl)?.let { return it }
        }

        return null
    }

    private fun parseSingleCandidate(input: String): BilibiliNavigationTarget? {
        val normalizedInput = normalizeInput(input) ?: input.trim()
        val plainTextTarget = mapParseResult(BilibiliUrlParser.parse(normalizedInput))
        if ("://" !in normalizedInput) {
            return plainTextTarget
        }

        val uri = runCatching { URI(normalizedInput) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val pathSegments = uri.path
            ?.split("/")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val queryMap = extractQueryParameters(uri.rawQuery)

        resolveWrappedUrl(queryMap)?.let { wrappedUrl ->
            parse(wrappedUrl)?.let { return it }
        }

        if (scheme in listOf("bilibili", "bili")) {
            mapParseResult(BilibiliUrlParser.parseDeepLink(normalizedInput))?.let { return it }
            resolveCustomSchemeTarget(host, pathSegments, queryMap)?.let { return it }
        }

        if (scheme !in listOf("http", "https")) return null

        if (host.contains("bilibili.com") || host.contains("b23.tv")) {
            mapParseResult(BilibiliUrlParser.parseDeepLink(normalizedInput))?.let { return it }
        }
        resolveHttpTarget(host, pathSegments, queryMap)?.let { return it }
        return null
    }

    private fun resolveCustomSchemeTarget(
        host: String,
        pathSegments: List<String>,
        queryMap: Map<String, String>
    ): BilibiliNavigationTarget? {
        when {
            host == "space" -> {
                pathSegments.firstOrNull()?.toLongOrNull()?.let {
                    return BilibiliNavigationTarget.Space(it)
                }
            }

            host == "search" -> {
                return BilibiliNavigationTarget.Search(
                    keyword = queryMap["keyword"]
                        ?.takeIf { it.isNotBlank() }
                        ?: queryMap["query"].orEmpty()
                )
            }

            host == "live" -> {
                pathSegments.firstOrNull()?.toLongOrNull()?.let {
                    return BilibiliNavigationTarget.Live(it)
                }
            }

            host == "following" && pathSegments.firstOrNull()?.equals("detail", ignoreCase = true) == true -> {
                pathSegments.getOrNull(1)?.takeIf(::isNumericId)?.let {
                    return BilibiliNavigationTarget.Dynamic(it)
                }
            }

            host == "opus" -> {
                pathSegments.lastOrNull()?.takeIf(::isNumericId)?.let {
                    return BilibiliNavigationTarget.Dynamic(it)
                }
            }

            host == "bangumi" -> {
                resolveBangumiTarget(pathSegments)?.let { return it }
            }

            host == "pgc" -> {
                resolvePgcTarget(pathSegments)?.let { return it }
            }

            host == "music" -> {
                queryMap["music_id"]?.takeIf { it.isNotBlank() }?.let {
                    return BilibiliNavigationTarget.Music(it)
                }
            }
        }

        return null
    }

    private fun resolveHttpTarget(
        host: String,
        pathSegments: List<String>,
        queryMap: Map<String, String>
    ): BilibiliNavigationTarget? {
        when {
            host == "space.bilibili.com" -> {
                pathSegments.firstOrNull()?.toLongOrNull()?.let {
                    return BilibiliNavigationTarget.Space(it)
                }
            }

            host == "live.bilibili.com" -> {
                pathSegments.firstOrNull()?.toLongOrNull()?.let {
                    return BilibiliNavigationTarget.Live(it)
                }
            }

            host == "music.bilibili.com" &&
                pathSegments.contains("music-detail") -> {
                queryMap["music_id"]?.takeIf { it.isNotBlank() }?.let {
                    return BilibiliNavigationTarget.Music(it)
                }
            }

            host.contains("bilibili.com") -> {
                resolveBangumiTarget(pathSegments)?.let { return it }
            }
        }

        return null
    }

    private fun resolveBangumiTarget(pathSegments: List<String>): BilibiliNavigationTarget? {
        val playIndex = pathSegments.indexOfFirst { it.equals("play", ignoreCase = true) }
        if (playIndex >= 0) {
            val value = pathSegments.getOrNull(playIndex + 1).orEmpty()
            parseBangumiPlayValue(value)?.let { return it }
        }

        val seasonIndex = pathSegments.indexOfFirst { it.equals("season", ignoreCase = true) }
        if (seasonIndex >= 0) {
            val next = pathSegments.getOrNull(seasonIndex + 1).orEmpty()
            when {
                next.equals("ep", ignoreCase = true) -> {
                    pathSegments.getOrNull(seasonIndex + 2)?.toLongOrNull()?.let {
                        return BilibiliNavigationTarget.BangumiEpisode(it)
                    }
                }

                next.toLongOrNull() != null -> {
                    return BilibiliNavigationTarget.BangumiSeason(next.toLong())
                }
            }
        }

        return null
    }

    private fun resolvePgcTarget(pathSegments: List<String>): BilibiliNavigationTarget? {
        val seasonIndex = pathSegments.indexOfFirst { it.equals("season", ignoreCase = true) }
        if (seasonIndex < 0) return null

        val next = pathSegments.getOrNull(seasonIndex + 1).orEmpty()
        if (next.equals("ep", ignoreCase = true)) {
            pathSegments.getOrNull(seasonIndex + 2)?.toLongOrNull()?.let {
                return BilibiliNavigationTarget.BangumiEpisode(it)
            }
        }
        next.toLongOrNull()?.let { return BilibiliNavigationTarget.BangumiSeason(it) }
        return null
    }

    private fun parseBangumiPlayValue(value: String): BilibiliNavigationTarget? {
        return when {
            value.startsWith("ss", ignoreCase = true) -> value.removePrefix("ss")
                .removePrefix("SS")
                .toLongOrNull()
                ?.let { BilibiliNavigationTarget.BangumiSeason(it) }

            value.startsWith("ep", ignoreCase = true) -> value.removePrefix("ep")
                .removePrefix("EP")
                .toLongOrNull()
                ?.let { BilibiliNavigationTarget.BangumiEpisode(it) }

            else -> null
        }
    }

    private fun mapParseResult(result: BilibiliUrlParser.ParseResult): BilibiliNavigationTarget? {
        if (!result.isValid) return null
        result.getVideoId()?.let { return BilibiliNavigationTarget.Video(it) }
        result.getDynamicTargetId()?.let { return BilibiliNavigationTarget.Dynamic(it) }
        return null
    }

    private fun collectShortLinkCandidates(input: String): List<String> {
        val candidates = linkedSetOf<String>()
        val direct = normalizeInput(input)
        if (direct != null && isShortLinkHost(direct)) {
            candidates += direct
        }
        BilibiliUrlParser.extractUrls(input)
            .mapNotNull(::normalizeInput)
            .filter(::isShortLinkHost)
            .forEach(candidates::add)
        return candidates.toList()
    }

    private fun isShortLinkHost(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.host?.contains("b23.tv", ignoreCase = true) == true
    }

    private fun normalizeInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("//")) return "https:$trimmed"
        if ("://" in trimmed) return trimmed

        return knownHosts.firstOrNull { host ->
            trimmed.startsWith(host, ignoreCase = true)
        }?.let {
            "https://$trimmed"
        }
    }

    private fun extractQueryParameters(encodedQuery: String?): Map<String, String> {
        if (encodedQuery.isNullOrBlank()) return emptyMap()

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

    private fun resolveWrappedUrl(queryMap: Map<String, String>): String? {
        val wrappedUrlKeys = listOf("url", "jump_url", "target_url", "web_url", "origin_url")
        return wrappedUrlKeys.firstNotNullOfOrNull { key ->
            queryMap[key]?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun isNumericId(value: String): Boolean = value.isNotEmpty() && value.all(Char::isDigit)
}

