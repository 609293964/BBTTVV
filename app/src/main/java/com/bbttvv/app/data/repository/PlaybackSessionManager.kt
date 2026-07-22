package com.bbttvv.app.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.bbttvv.app.core.network.WbiKeyManager
import com.bbttvv.app.data.service.video.VideoSessionService
import com.bbttvv.app.core.store.TokenManager
import org.json.JSONObject
import java.util.Random

internal object PlaybackSessionManager {
    private const val TAG = "PlaybackSession"
    private const val APP_API_COOLDOWN_MS = 120_000L

    private val api get() = VideoSessionService.api
    private val buvidApi get() = VideoSessionService.buvidApi

    private var appApiCooldownUntilMs: Long
        get() = VideoSessionService.appApiCooldownUntilMs
        set(value) {
            VideoSessionService.appApiCooldownUntilMs = value
        }

    private var buvidInitialized: Boolean
        get() = VideoSessionService.buvidInitialized
        set(value) {
            VideoSessionService.buvidInitialized = value
        }

    private var last412Time: Long
        get() = VideoSessionService.last412Time
        set(value) {
            VideoSessionService.last412Time = value
        }

    var applicationContext: Context?
        get() = VideoSessionService.applicationContext
        private set(value) {
            VideoSessionService.applicationContext = value
        }

    val isBuvidInitialized: Boolean
        get() = buvidInitialized

    fun init(context: Context) {
        // Keep only ApplicationContext because playback services live for the process.
        applicationContext = context.applicationContext
    }

    fun getAppApiCooldownRemainingMs(nowMs: Long = System.currentTimeMillis()): Long {
        return (appApiCooldownUntilMs - nowMs).coerceAtLeast(0L)
    }

    fun isAppApiCoolingDown(nowMs: Long = System.currentTimeMillis()): Boolean {
        return getAppApiCooldownRemainingMs(nowMs) > 0L
    }

    fun canUseAppApi(hasAccessToken: Boolean, nowMs: Long = System.currentTimeMillis()): Boolean {
        return shouldCallAccessTokenApi(
            nowMs = nowMs,
            cooldownUntilMs = appApiCooldownUntilMs,
            hasAccessToken = hasAccessToken
        )
    }

    fun recordAppApiSuccess() {
        appApiCooldownUntilMs = 0L
    }

    fun recordAppApiRiskHit(nowMs: Long = System.currentTimeMillis()) {
        appApiCooldownUntilMs = nowMs + APP_API_COOLDOWN_MS
    }

    suspend fun ensureBuvid3() {
        if (buvidInitialized) return
        try {
            com.bbttvv.app.core.util.Logger.d(TAG, "Fetching buvid3 from SPI API")
            val response = buvidApi.getSpi()
            if (response.code == 0 && response.data != null) {
                val b3 = response.data.b_3
                if (b3.isNotEmpty()) {
                    TokenManager.buvid3Cache = b3
                    com.bbttvv.app.core.util.Logger.d(TAG, "buvid3 ready: ${b3.take(20)}...")
                    runCatching { activateBuvid() }
                        .onSuccess { com.bbttvv.app.core.util.Logger.d(TAG, "buvid activated") }
                        .onFailure { error -> Log.w(TAG, "buvid activation failed: ${error.message}") }
                    buvidInitialized = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get buvid3 from SPI: ${e.message}")
        }
    }

    suspend fun getWbiKeys(): Pair<String, String> {
        return WbiKeyManager.getWbiKeys().getOrElse { error ->
            throw Exception("Wbi Keys Error: ${error.message}", error)
        }
    }

    fun invalidateWbiKeys() {
        WbiKeyManager.invalidateCache()
    }

    fun resetSessionDerivedState() {
        invalidateWbiKeys()
        appApiCooldownUntilMs = 0L
        last412Time = 0L
        buvidInitialized = false
    }

    private suspend fun activateBuvid() {
        val random = Random()
        val randBytes = ByteArray(32) { random.nextInt(256).toByte() }
        val endBytes = byteArrayOf(0, 0, 0, 0, 73, 69, 78, 68) + ByteArray(4) { random.nextInt(256).toByte() }
        val randPngEnd = Base64.encodeToString(randBytes + endBytes, Base64.NO_WRAP)

        val payload = JSONObject().apply {
            put("3064", 1)
            put("39c8", "333.999.fp.risk")
            put("3c43", JSONObject().apply {
                put("adca", "Windows")
                put("bfe9", randPngEnd.takeLast(50))
            })
        }.toString()

        buvidApi.activateBuvid(payload)
    }
}
