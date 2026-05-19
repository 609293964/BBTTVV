package com.bbttvv.app.feature.plugin

import java.net.URI
import kotlinx.serialization.Serializable

@Serializable
data class IpLocationSnapshot(
    val addr: String = "",
    val country: String = "",
    val province: String = "",
    val city: String = "",
    val isp: String = ""
)

data class CdnRegionSelection(
    val region: String,
    val hosts: List<String>,
    val fallbackUsed: Boolean
)

data class CdnRewriteResult(
    val urls: List<String>,
    val rewrittenCount: Int
)

internal fun selectCdnRegionForLocation(
    location: IpLocationSnapshot,
    catalog: Map<String, List<String>>,
    @Suppress("UNUSED_PARAMETER")
    fallbackRegion: () -> String
): CdnRegionSelection {
    val available = catalog.filterValues { it.isNotEmpty() }
    val overseasHosts = available["海外"]
    val normalizedCountry = normalizeRegionName(location.country)

    if (available.isEmpty()) {
        return CdnRegionSelection(region = "", hosts = emptyList(), fallbackUsed = true)
    }

    val directCandidates = listOf(location.city, location.province, location.country)
        .flatMap { value -> listOf(value, normalizeRegionName(value)) }
        .filter { it.isNotBlank() }

    directCandidates.firstNotNullOfOrNull { candidate ->
        available[candidate]?.let { hosts ->
            return CdnRegionSelection(
                candidate,
                sortCdnRegionHostsForIsp(hosts, location.isp),
                fallbackUsed = false
            )
        }
    }

    if (normalizedCountry.isNotBlank() && normalizedCountry !in mainlandCountryNames) {
        overseasHosts?.let {
            return CdnRegionSelection(
                "海外",
                sortCdnRegionHostsForIsp(it, location.isp),
                fallbackUsed = false
            )
        }
    }

    val alias = resolveMainlandRegionAlias(location)
    if (alias != null) {
        available[alias]?.let {
            return CdnRegionSelection(
                alias,
                sortCdnRegionHostsForIsp(it, location.isp),
                fallbackUsed = false
            )
        }
    }

    return CdnRegionSelection(region = "", hosts = emptyList(), fallbackUsed = true)
}

internal fun rewriteCdnUrlCandidates(
    originalUrls: List<String>,
    preferredHosts: List<String>
): CdnRewriteResult {
    if (originalUrls.isEmpty() || preferredHosts.isEmpty()) {
        return CdnRewriteResult(urls = originalUrls.distinct(), rewrittenCount = 0)
    }

    val rewritten = buildList {
        originalUrls.forEach { original ->
            preferredHosts.forEach { host ->
                rewriteBilivideoHost(original, host)?.let(::add)
            }
        }
        addAll(originalUrls)
    }.distinct()

    return CdnRewriteResult(
        urls = rewritten,
        rewrittenCount = (rewritten.size - originalUrls.distinct().size).coerceAtLeast(0)
    )
}

internal fun shouldRefreshCdnIpLocation(
    enabled: Boolean,
    nowMs: Long,
    lastRefreshMs: Long,
    ttlMs: Long = CDN_REGION_LOCATION_TTL_MS,
    hasSelection: Boolean
): Boolean {
    if (!enabled) return false
    if (!hasSelection) return true
    if (lastRefreshMs <= 0L) return true
    return nowMs - lastRefreshMs >= ttlMs
}

internal fun resolveCdnRegionHosts(
    region: String,
    cachedHosts: List<String>,
    catalog: Map<String, List<String>>,
    isp: String = ""
): List<String> {
    if (region.isBlank()) return emptyList()
    val catalogHosts = catalog[region].orEmpty()
    val distinctCachedHosts = cachedHosts.distinct()
    if (distinctCachedHosts.isEmpty()) return emptyList()
    if (catalogHosts.isEmpty()) return sortCdnRegionHostsForIsp(distinctCachedHosts, isp)
    return if (distinctCachedHosts.all { it in catalogHosts }) {
        sortCdnRegionHostsForIsp(distinctCachedHosts, isp)
    } else {
        sortCdnRegionHostsForIsp(catalogHosts, isp)
    }
}

internal fun hasUsableCdnRegionSelection(
    region: String,
    cachedHosts: List<String>,
    catalog: Map<String, List<String>>
): Boolean {
    if (region.isBlank() || cachedHosts.isEmpty()) return false
    val catalogHosts = catalog[region].orEmpty()
    if (catalogHosts.isEmpty()) return true
    return cachedHosts.all { it in catalogHosts }
}

internal const val CDN_REGION_LOCATION_TTL_MS: Long = 24L * 60L * 60L * 1000L

internal fun sortCdnRegionHostsForIsp(
    hosts: List<String>,
    isp: String
): List<String> {
    val carrier = resolveCarrierKey(isp)
    if (carrier == null) return hosts.distinct()
    return hosts.distinct().sortedWith(
        compareByDescending<String> { host -> scoreHostForCarrier(host, carrier) }
    )
}

internal fun maskIpAddressForLog(addr: String): String {
    val trimmed = addr.trim()
    if (trimmed.isBlank()) return ""
    val ipv4Parts = trimmed.split(".")
    if (ipv4Parts.size == 4 && ipv4Parts.all { it.isNotBlank() }) {
        return "${ipv4Parts[0]}.${ipv4Parts[1]}.*.*"
    }
    val ipv6Parts = trimmed.split(":")
    if (ipv6Parts.size > 2) {
        return ipv6Parts.take(2).joinToString(":") + ":*"
    }
    return "***"
}

private val mainlandCountryNames = setOf(
    "中国",
    "中国大陆",
    "中华人民共和国"
)

private val provinceRegionAliases = mapOf(
    "上海" to "上海",
    "北京" to "北京",
    "天津" to "天津",
    "重庆" to "重庆",
    "广东" to "广州",
    "四川" to "成都",
    "陕西" to "西安",
    "湖北" to "武汉",
    "江苏" to "南京",
    "黑龙江" to "哈市",
    "内蒙古" to "呼市",
    "香港" to "香港",
    "澳门" to "澳门"
)

private val cityRegionAliases = mapOf(
    "广州" to "广州",
    "深圳" to "深圳",
    "成都" to "成都",
    "西安" to "西安",
    "武汉" to "武汉",
    "南京" to "南京",
    "哈尔滨" to "哈市",
    "呼和浩特" to "呼市",
    "香港" to "香港",
    "澳门" to "澳门"
)

private fun resolveCarrierKey(isp: String): String? {
    val normalized = isp.trim().lowercase()
    return when {
        normalized.contains("电信") || normalized.contains("chinanet") ||
            normalized.contains("telecom") -> "ct"
        normalized.contains("移动") || normalized.contains("cmcc") ||
            normalized.contains("mobile") -> "cm"
        normalized.contains("联通") || normalized.contains("unicom") -> "cu"
        normalized.contains("广电") || normalized.contains("broadcast") -> "gd"
        else -> null
    }
}

private fun scoreHostForCarrier(host: String, carrier: String): Int {
    val normalized = host.lowercase()
    return when {
        normalized.contains("-$carrier-") -> 3
        normalized.contains("-$carrier.") -> 3
        normalized.contains("$carrier-") -> 2
        normalized.contains(carrier) -> 1
        else -> 0
    }
}

private fun resolveMainlandRegionAlias(location: IpLocationSnapshot): String? {
    val city = normalizeRegionName(location.city)
    cityRegionAliases[city]?.let { return it }

    val province = normalizeRegionName(location.province)
    return provinceRegionAliases[province]
}

private fun normalizeRegionName(value: String): String {
    return value.trim()
        .removeSuffix("特别行政区")
        .removeSuffix("壮族自治区")
        .removeSuffix("回族自治区")
        .removeSuffix("维吾尔自治区")
        .removeSuffix("自治区")
        .removeSuffix("省")
        .removeSuffix("市")
}

private fun rewriteBilivideoHost(url: String, newHost: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val originalHost = uri.host ?: return null
    if (originalHost != "bilivideo.com" && !originalHost.endsWith(".bilivideo.com")) return null
    if (newHost.isBlank()) return null

    val scheme = uri.scheme ?: return null
    val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
    val port = if (uri.port >= 0) ":${uri.port}" else ""
    val path = uri.rawPath.orEmpty()
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
    return "$scheme://$userInfo$newHost$port$path$query$fragment"
}
