package com.bbttvv.app.core.network

import android.content.Context
import com.bbttvv.app.core.store.DEFAULT_APP_USER_AGENT
import com.bbttvv.app.core.store.SettingsManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

internal fun resolveAppUserAgent(context: Context?): String {
    return context?.let(SettingsManager::getUserAgentSync) ?: DEFAULT_APP_USER_AGENT
}

internal fun isIpv4OnlyEnabled(context: Context?): Boolean {
    return context?.let(SettingsManager::getIpv4OnlyEnabledSync) ?: false
}

internal fun filterPreferredInetAddresses(
    addresses: List<InetAddress>,
    ipv4OnlyEnabled: Boolean
): List<InetAddress> {
    if (!ipv4OnlyEnabled) return addresses
    val ipv4Addresses = addresses.filterIsInstance<Inet4Address>()
    if (ipv4Addresses.isNotEmpty()) return ipv4Addresses
    throw UnknownHostException("No IPv4 address available")
}
