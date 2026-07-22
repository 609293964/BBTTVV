package com.bbttvv.app.core.network

import com.bbttvv.app.core.util.Logger

object WbiRequestSigner {
    suspend fun sign(params: Map<String, String>): Result<Map<String, String>> {
        return WbiKeyManager.getWbiKeys().mapCatching { keys ->
            signWithKeys(params, keys)
        }
    }

    suspend fun signOrUnsigned(
        params: Map<String, String>,
        logTag: String,
    ): Map<String, String> {
        return sign(params).getOrElse { error ->
            Logger.e(logTag, "WBI signing unavailable, use unsigned params", error)
            params
        }
    }

    internal fun signWithKeys(
        params: Map<String, String>,
        keys: Pair<String, String>,
    ): Map<String, String> {
        require(keys.first.isNotBlank() && keys.second.isNotBlank()) {
            "WBI img/sub key is missing"
        }
        return WbiSigner.sign(params, keys.first, keys.second)
    }
}
