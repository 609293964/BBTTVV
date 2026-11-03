package com.bbttvv.app.feature.video.videoshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.LruCache
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.resolveAppUserAgent
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.VideoshotData
import java.io.IOException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlinx.coroutines.coroutineScope
import okhttp3.Request

data class VideoShotFrame(
    val spriteSheet: Bitmap,
    val srcRect: Rect,
    val aspectWidth: Int,
    val aspectHeight: Int,
)

class VideoShot private constructor(
    private val timesMs: List<Long>,
    private val images: List<String>,
    private val imageCountX: Int,
    private val imageCountY: Int,
    val fallbackAspectWidth: Int,
    val fallbackAspectHeight: Int,
) {
    private val imageCache = VideoShotImageCache { url -> downloadBytes(url) }

    suspend fun getFrame(positionMs: Long): VideoShotFrame {
        if (timesMs.isEmpty() || images.isEmpty() || imageCountX <= 0 || imageCountY <= 0) {
            throw IllegalStateException("videoshot is not ready")
        }

        val frameIndex = findClosestValueIndex(timesMs, positionMs.coerceAtLeast(0L))
        val framesPerImage = (imageCountX * imageCountY).coerceAtLeast(1)
        val imageIndex = (frameIndex / framesPerImage).coerceIn(0, images.lastIndex)
        val localIndex = (frameIndex % framesPerImage).coerceIn(0, framesPerImage - 1)

        val spriteSheet = imageCache.getOrDecodeImage(imageIndex, images[imageIndex])
        val cellWidth = (spriteSheet.width / imageCountX).coerceAtLeast(1)
        val cellHeight = (spriteSheet.height / imageCountY).coerceAtLeast(1)
        val left = (localIndex % imageCountX) * cellWidth
        val top = (localIndex / imageCountX) * cellHeight

        return VideoShotFrame(
            spriteSheet = spriteSheet,
            srcRect = Rect(left, top, left + cellWidth, top + cellHeight),
            aspectWidth = fallbackAspectWidth,
            aspectHeight = fallbackAspectHeight,
        )
    }

    fun clear() {
        imageCache.clear()
    }

    private fun findClosestValueIndex(array: List<Long>, target: Long): Int {
        if (array.isEmpty()) return 0
        var left = 0
        var right = array.size - 1
        while (left < right) {
            val mid = left + (right - left) / 2
            if (array[mid] < target) {
                left = mid + 1
            } else {
                right = mid
            }
        }
        return left
    }

    companion object {
        private const val TAG = "VideoShot"

        suspend fun fromData(data: VideoshotData): VideoShot? = coroutineScope {
            val imageUrls = data.image
                .map(::normalizeVideoShotUrl)
                .filter { it.isNotBlank() }
                .distinct()
            if (imageUrls.isEmpty()) return@coroutineScope null

            val timesMs = normalizeIndexTimes(data.index)
            if (timesMs.isEmpty()) return@coroutineScope null

            VideoShot(
                timesMs = timesMs,
                images = imageUrls,
                imageCountX = data.img_x_len.takeIf { it > 0 } ?: 10,
                imageCountY = data.img_y_len.takeIf { it > 0 } ?: 10,
                fallbackAspectWidth = data.img_x_size.takeIf { it > 0 } ?: 160,
                fallbackAspectHeight = data.img_y_size.takeIf { it > 0 } ?: 90,
            )
        }

        private fun normalizeIndexTimes(index: List<Long>): List<Long> {
            val raw = index
                .takeIf { it.size > 1 }
                ?.drop(1)
                ?.filter { it >= 0L }
                .orEmpty()
            if (raw.isEmpty()) return emptyList()
            val last = raw.lastOrNull() ?: 0L
            return if (last in 1L until 200_000L) {
                raw.map { it * 1000L }
            } else {
                raw
            }
        }

        private fun normalizeVideoShotUrl(url: String): String {
            val trimmed = url.trim()
            return when {
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("http://") -> "https://" + trimmed.removePrefix("http://")
                trimmed.startsWith("https://") -> trimmed
                trimmed.isNotBlank() -> "https://$trimmed"
                else -> ""
            }
        }

        private suspend fun downloadBytes(url: String): ByteArray = suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://www.bilibili.com")
                .header(
                    "User-Agent",
                    resolveAppUserAgent(NetworkModule.appContext),
                )
                .build()
            val call = NetworkModule.okHttpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            if (!it.isSuccessful) throw IOException("HTTP "+it.code)
                            // Read one bounded sheet on OkHttp's worker, never on the UI thread.
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            val input = it.body.byteStream()
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > 4 * 1024 * 1024) throw IOException("Preview sheet too large")
                                output.write(buffer, 0, count)
                            }
                            val bytes = output.toByteArray()
                            if (bytes.size > 4 * 1024 * 1024) throw IOException("Preview sheet too large")
                            bytes
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
}

private class VideoShotImageCache(private val download: suspend (String) -> ByteArray) {
    private val lock = Any()
    private val slots = Semaphore(2)
    private var closed = false
    private val memoryCache = object : LruCache<Int, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
    }
    private val activeTasks = mutableMapOf<Int, Deferred<Bitmap>>()

    suspend fun getOrDecodeImage(imageIndex: Int, url: String): Bitmap = coroutineScope {
        synchronized(lock) { memoryCache.get(imageIndex) }?.let { return@coroutineScope it }
        val task = synchronized(lock) {
            check(!closed) { "Preview released" }
            activeTasks[imageIndex] ?: async(Dispatchers.IO) {
                slots.withPermit {
                    val bytes = download(url)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                        bounds.outWidth.toLong() * bounds.outHeight > 6 * 1024 * 1024L) {
                        throw IOException("Invalid preview dimensions")
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                        BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                            inScaled = false
                        }) ?: throw IOException("Preview decode failed")
                }
            }.also { activeTasks[imageIndex] = it }
        }
        try {
            val bitmap = task.await()
            synchronized(lock) { if (!closed) memoryCache.put(imageIndex, bitmap) }
            bitmap
        } finally {
            synchronized(lock) { if (activeTasks[imageIndex] === task) activeTasks.remove(imageIndex) }
        }
    }

    fun clear() = synchronized(lock) {
        closed = true
        activeTasks.values.forEach { it.cancel() }
        activeTasks.clear()
        memoryCache.evictAll()
    }
}
