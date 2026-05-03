package com.bbttvv.app.core.network

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NetworkAddressPolicyTest {

    @Test
    fun `ipv4 only keeps ipv4 addresses when available`() {
        val ipv4 = InetAddress.getByName("127.0.0.1")
        val ipv6 = InetAddress.getByName("::1")

        assertEquals(
            listOf(ipv4),
            filterPreferredInetAddresses(
                addresses = listOf(ipv6, ipv4),
                ipv4OnlyEnabled = true
            )
        )
    }

    @Test
    fun `ipv4 only rejects ipv6 only result`() {
        val ipv6 = InetAddress.getByName("::1")

        assertFailsWith<UnknownHostException> {
            filterPreferredInetAddresses(
                addresses = listOf(ipv6),
                ipv4OnlyEnabled = true
            )
        }
    }

    @Test
    fun `disabled ipv4 preference preserves resolver order`() {
        val ipv4 = InetAddress.getByName("127.0.0.1")
        val ipv6 = InetAddress.getByName("::1")

        assertEquals(
            listOf(ipv6, ipv4),
            filterPreferredInetAddresses(
                addresses = listOf(ipv6, ipv4),
                ipv4OnlyEnabled = false
            )
        )
    }
}
