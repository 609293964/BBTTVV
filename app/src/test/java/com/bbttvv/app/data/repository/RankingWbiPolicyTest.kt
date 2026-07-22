package com.bbttvv.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RankingWbiPolicyTest {
    @Test
    fun `ranking parameters keep category identity and include wbi signature`() {
        val params = buildRankingWbiParams(
            rid = 1005,
            type = "all",
            imgKey = "abcdefghijklmnopqrstuvwxyz012345",
            subKey = "6789ABCDEFGHIJKLMNOPQRSTUVWXYZabcd",
        )

        assertEquals("1005", params["rid"])
        assertEquals("all", params["type"])
        assertFalse(params["wts"].isNullOrBlank())
        assertFalse(params["w_rid"].isNullOrBlank())
    }
}
