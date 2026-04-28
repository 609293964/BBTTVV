package com.bbttvv.app.core.network

import java.security.MessageDigest

object AppSigner {
    const val TV_APP_KEY = "4409e2ce8ffd12b8"
    private const val TV_APP_SEC = "59b43e04ad6965f34319062b478f83dd"

    const val ANDROID_APP_KEY = "1d8b6e7d45233436"
    private const val ANDROID_APP_SEC = "560c52ccd288fed045859ed18bffd973"

    fun sign(params: Map<String, String>, appSec: String = TV_APP_SEC): Map<String, String> {
        val sortedParams = params.toSortedMap()
        val queryString = sortedParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return sortedParams + ("sign" to md5(queryString + appSec))
    }

    fun signForTvLogin(params: Map<String, String>): Map<String, String> {
        return sign(params, TV_APP_SEC)
    }

    fun signForAndroidApi(params: Map<String, String>): Map<String, String> {
        return sign(params, ANDROID_APP_SEC)
    }

    fun getTimestamp(): Long = System.currentTimeMillis() / 1000

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
