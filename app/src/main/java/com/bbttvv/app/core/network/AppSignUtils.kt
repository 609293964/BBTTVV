package com.bbttvv.app.core.network

/**
 * Backward-compatible facade for APP signing.
 *
 * New code should use [AppSigner] directly.
 */
object AppSignUtils {
    const val TV_APP_KEY = AppSigner.TV_APP_KEY
    const val ANDROID_APP_KEY = AppSigner.ANDROID_APP_KEY

    fun sign(params: Map<String, String>, appSec: String = "59b43e04ad6965f34319062b478f83dd"): Map<String, String> {
        return AppSigner.sign(params, appSec)
    }

    fun signForTvLogin(params: Map<String, String>): Map<String, String> {
        return AppSigner.signForTvLogin(params)
    }

    fun signForAndroidApi(params: Map<String, String>): Map<String, String> {
        return AppSigner.signForAndroidApi(params)
    }

    fun getTimestamp(): Long {
        return AppSigner.getTimestamp()
    }
}
