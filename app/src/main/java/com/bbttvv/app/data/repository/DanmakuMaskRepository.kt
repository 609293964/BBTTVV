package com.bbttvv.app.data.repository

import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.feature.video.danmaku.DanmakuMask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Loads Bilibili's precomputed mask; network and binary parsing stay off the UI thread. */
object DanmakuMaskRepository {
    private const val MAX_MASK_BYTES = 64L * 1024L * 1024L
    private val client by lazy {
        NetworkModule.okHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build()
    }

    suspend fun load(maskUrl: String): DanmakuMask? = withContext(Dispatchers.IO) {
        val normalizedUrl = when {
            maskUrl.startsWith("//") -> "https:$maskUrl"
            maskUrl.startsWith("http://") || maskUrl.startsWith("https://") -> maskUrl
            else -> return@withContext null
        }
        val request = runCatching { Request.Builder().url(normalizedUrl).get().build() }.getOrNull()
            ?: return@withContext null
        val bytes = download(request) ?: return@withContext null
        DanmakuMask.fromBinary(bytes).also {
            Logger.w("DanmakuMask", if (it == null) "Invalid webmask header" else "Webmask loaded bytes=${bytes.size}")
        }
    }

    private suspend fun download(request: Request): ByteArray? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                Logger.w("DanmakuMask", "Webmask download failed")
                continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val bytes = runCatching {
                    response.use {
                        if (!it.isSuccessful) {
                            Logger.w("DanmakuMask", "Webmask HTTP status=${it.code}")
                            return@use null
                        }
                        require(it.body.contentLength() <= MAX_MASK_BYTES)
                        it.body.byteStream().use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val size = input.read(buffer)
                                if (size == -1) break
                                require(output.size().toLong() + size <= MAX_MASK_BYTES)
                                output.write(buffer, 0, size)
                            }
                            output.toByteArray()
                        }
                    }
                }.getOrNull()
                if (continuation.isActive) {
                    if (bytes == null) Logger.w("DanmakuMask", "Webmask body unavailable")
                    continuation.resume(bytes)
                }
            }
        })
    }
}
