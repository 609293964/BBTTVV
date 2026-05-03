package com.bbttvv.app.core.network

import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

fun filterPreferredInetAddresses(
    addresses: List<InetAddress>,
    ipv4OnlyEnabled: Boolean
): List<InetAddress> {
    if (!ipv4OnlyEnabled) return addresses
    val ipv4Addresses = addresses.filterIsInstance<Inet4Address>()
    if (ipv4Addresses.isNotEmpty()) return ipv4Addresses
    throw UnknownHostException("No IPv4 address available")
}
