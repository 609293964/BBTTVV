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
import kotlinx.coroutines.awaitAll
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
    private val images: List<ByteArray>,
    private val imageCountX: Int,
    private val imageCountY: Int,
    val fallbackAspectWidth: Int,
    val fallbackAspectHeight: Int,
) {
    private val imageCache = VideoShotImageCache()

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

            val downloadedImages = imageUrls
                .map { url ->
                    async(Dispatchers.IO) {
                        runCatching { downloadBytes(url) }
                            .onFailure { Logger.w(TAG, "download failed: ${url.takeLast(32)} ${it.message}") }
                            .getOrNull()
                            ?.takeIf { it.isNotEmpty() }
                    }
                }
                .awaitAll()

            if (downloadedImages.any { it == null }) {
                Logger.w(TAG, "download videoshot images failed: total=${downloadedImages.size}")
                return@coroutineScope null
            }

            VideoShot(
                timesMs = timesMs,
                images = downloadedImages.filterNotNull(),
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

        private fun downloadBytes(url: String): ByteArray {
            val request = Request.Builder()
                .url(url)
                .header("Referer", "https://www.bilibili.com")
                .header(
                    "User-Agent",
                    resolveAppUserAgent(NetworkModule.appContext),
                )
                .build()
            NetworkModule.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                return response.body?.bytes() ?: ByteArray(0)
            }
        }
    }
}

private class VideoShotImageCache {
    private val lock = Any()
    private val memoryCache = LruCache<Int, Bitmap>(3)
    private val activeTasks = mutableMapOf<Int, Deferred<Bitmap>>()

    suspend fun getOrDecodeImage(imageIndex: Int, imageData: ByteArray): Bitmap = coroutineScope {
        synchronized(lock) {
            memoryCache.get(imageIndex)
        }?.let { return@coroutineScope it }

        val task = synchronized(lock) {
            activeTasks[imageIndex] ?: async(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(
                    imageData,
                    0,
                    imageData.size,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inScaled = false
                    },
                ) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
            }.also { activeTasks[imageIndex] = it }
        }

        try {
            val bitmap = task.await()
            synchronized(lock) {
                memoryCache.put(imageIndex, bitmap)
            }
            bitmap
        } finally {
            synchronized(lock) {
                activeTasks.remove(imageIndex)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            memoryCache.evictAll()
            activeTasks.clear()
        }
    }
}
