package com.bbttvv.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SignerTest {
    @Test
    fun appSignerSortsParamsBeforeMd5() {
        val signed = AppSigner.sign(
            params = mapOf("b" to "2", "a" to "1"),
            appSec = "sec",
        )

        assertEquals("1", signed["a"])
        assertEquals("2", signed["b"])
        assertEquals("fdb220f719c0b62ada79a1615330e8b5", signed["sign"])
    }

    @Test
    fun appSignUtilsDelegatesToAppSigner() {
        val params = mapOf("b" to "2", "a" to "1")

        assertEquals(AppSigner.sign(params), AppSignUtils.sign(params))
    }

    @Test
    fun wbiSignerFiltersRiskCharactersAndUsesStableTimestamp() {
        val signed = WbiSigner.sign(
            params = mapOf(
                "foo" to "bar!()* baz",
                "z" to "1",
            ),
            imgKey = "abcdefghijklmnopqrstuvwxyz012345",
            subKey = "6789ABCDEFGHIJKLMNOPQRSTUVWXYZabcd",
            nowEpochSec = 1_700_000_000L,
        )

        assertEquals("bar baz", signed["foo"])
        assertEquals("1700000000", signed["wts"])
        assertEquals("9890da3cb3eeec53a67dcefee1df1d04", signed["w_rid"])
    }

    @Test
    fun wbiUtilsDelegatesToWbiSigner() {
        val params = mapOf("foo" to "bar")
        val signed = WbiUtils.sign(
            params = params,
            imgKey = "abcdefghijklmnopqrstuvwxyz012345",
            subKey = "6789ABCDEFGHIJKLMNOPQRSTUVWXYZabcd",
        )

        assertFalse(signed["w_rid"].isNullOrBlank())
        assertEquals("bar", signed["foo"])
    }
}
