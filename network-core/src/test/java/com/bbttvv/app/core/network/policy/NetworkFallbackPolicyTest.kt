package com.bbttvv.app.core.network.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetworkFallbackPolicyTest {

    @Test
    fun `supported hosts use explicit hardcoded ip fallback when enabled`() {
        assertEquals(
            HardcodedDnsFallback(
                hostname = "api.bilibili.com",
                ipAddress = "47.103.24.173",
                description = "Bilibili API"
            ),
            resolveHardcodedDnsFallback(
                hostname = "api.bilibili.com",
                allowHardcodedIpFallback = true
            )
        )
    }

    @Test
    fun `supported hosts do not use hardcoded ip fallback when disabled`() {
        assertNull(
            resolveHardcodedDnsFallback(
                hostname = "passport.bilibili.com",
                allowHardcodedIpFallback = false
            )
        )
    }

    @Test
    fun `unsupported hosts never use implicit fallback`() {
        assertNull(
            resolveHardcodedDnsFallback(
                hostname = "example.com",
                allowHardcodedIpFallback = true
            )
        )
    }
}

