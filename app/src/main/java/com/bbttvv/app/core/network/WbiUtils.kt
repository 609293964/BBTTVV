package com.bbttvv.app.core.network

/**
 * Backward-compatible facade for WBI signing.
 *
 * New code should use [WbiSigner] directly.
 */
object WbiUtils {
    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String,
        includeRiskFingerprint: Boolean = false
    ): Map<String, String> {
        return WbiSigner.sign(
            params = params,
            imgKey = imgKey,
            subKey = subKey,
            includeRiskFingerprint = includeRiskFingerprint,
        )
    }
}
