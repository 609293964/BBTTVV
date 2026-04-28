package com.bbttvv.app.core.network

import com.bbttvv.app.core.util.Logger
import java.net.URLEncoder
import java.security.MessageDigest

object WbiSigner {
    private val illegalCharsRegex = Regex("[!'()*]")
    private val hexChars = "0123456789abcdef".toCharArray()
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
        includeRiskFingerprint: Boolean = false,
        nowEpochSec: Long = System.currentTimeMillis() / 1000,
    ): Map<String, String> {
        val mixinKey = getMixinKey(imgKey + subKey)
        val rawParams = mutableMapOf<String, String>()
        for ((key, value) in params) {
            rawParams[key] = filterIllegalChars(value)
        }
        rawParams["wts"] = nowEpochSec.toString()
        if (includeRiskFingerprint) {
            rawParams.appendRiskFingerprintParams()
        }

        val query = rawParams.keys.sorted()
            .joinToString("&") { key -> "$key=${encodeURIComponent(rawParams.getValue(key))}" }
        rawParams["w_rid"] = md5(query + mixinKey)

        Logger.d("WbiUtils") { " w_rid: ${rawParams["w_rid"]}, params count: ${rawParams.size}" }
        return rawParams
    }

    private fun getMixinKey(orig: String): String {
        val builder = StringBuilder()
        for (index in mixinKeyEncTab) {
            if (index < orig.length) builder.append(orig[index])
        }
        return builder.toString().substring(0, 32)
    }

    private fun filterIllegalChars(value: String): String {
        return value.replace(illegalCharsRegex, "")
    }

    private fun encodeURIComponent(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun MutableMap<String, String>.appendRiskFingerprintParams() {
        this["dm_img_list"] = "[]"
        this["dm_img_str"] = "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ"
        this["dm_cover_img_str"] = "QU5HTEUgKE5WSURJQSwgTlZJRElBIEdlRm9yY2UgR1RYIDEwNjAgNkdCIERpcmVjdDNEMTEgdnNfNV8wIHBzXzVfMCwgRDNEMTEp"
        this["dm_img_inter"] = """{"ds":[],"wh":[0,0,0],"of":[0,0,0]}"""
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        val chars = CharArray(bytes.size * 2)
        var offset = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            chars[offset++] = hexChars[value ushr 4]
            chars[offset++] = hexChars[value and 0x0F]
        }
        return String(chars)
    }
}
