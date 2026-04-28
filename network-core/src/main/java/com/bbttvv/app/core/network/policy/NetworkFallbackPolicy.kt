package com.bbttvv.app.core.network.policy

data class HardcodedDnsFallback(
    val hostname: String,
    val ipAddress: String,
    val description: String
)

private val supportedDnsFallbacks = mapOf(
    "api.bilibili.com" to HardcodedDnsFallback(
        hostname = "api.bilibili.com",
        ipAddress = "47.103.24.173",
        description = "Bilibili API"
    ),
    "passport.bilibili.com" to HardcodedDnsFallback(
        hostname = "passport.bilibili.com",
        ipAddress = "47.103.24.175",
        description = "Bilibili Passport"
    ),
    "i0.hdslb.com" to HardcodedDnsFallback(
        hostname = "i0.hdslb.com",
        ipAddress = "116.63.10.36",
        description = "Bilibili Image CDN"
    )
)

fun resolveHardcodedDnsFallback(
    hostname: String,
    allowHardcodedIpFallback: Boolean
): HardcodedDnsFallback? {
    if (!allowHardcodedIpFallback) return null
    return supportedDnsFallbacks[hostname.trim().lowercase()]
}

