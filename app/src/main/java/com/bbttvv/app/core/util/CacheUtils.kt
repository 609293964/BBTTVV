package com.bbttvv.app.core.util

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.bbttvv.app.core.cache.PlayUrlCache
import com.bbttvv.app.core.cooldown.PlaybackCooldownManager
import com.bbttvv.app.core.store.FollowingCacheStore
import com.bbttvv.app.data.repository.DanmakuRepository
import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.Locale

enum class CacheClearTarget {
    PLAYBACK_QUALITY,
    NETWORK,
    IMAGE_PREVIEW,
    SUBTITLE_DANMAKU,
    TEMP_FILES_AND_LOGS,
    APP_METADATA
}

/**
 *  缓存工具类 - 优化版
 * 
 * 改进点:
 * 1. 使用 walkTopDown() 惰性序列替代递归遍历
 * 2. 分类统计缓存大小（图片/HTTP/视频URL/其他）
 * 3. 正确的清理顺序避免冲突
 * 4. 完整的内存缓存清理（包含 PlayUrlCache）
 */
@OptIn(ExperimentalCoilApi::class)
object CacheUtils {

    private const val TAG = "CacheUtils"

    /**
     * 缓存详情数据类
     */
    data class CacheBreakdown(
        val imageDiskCache: Long = 0L,  // Coil 图片磁盘缓存
        val imageMemoryCache: Long = 0L, // Coil 图片内存缓存
        val httpCache: Long = 0L,       // OkHttp HTTP 缓存
        val otherCache: Long = 0L,      // 其他文件缓存
        val playUrlMemoryCache: Long = 0L, // PlayUrl 内存缓存
        val subtitleDanmakuMemoryCache: Long = 0L // 字幕/弹幕内存缓存
    ) {
        val imageCache: Long get() = imageDiskCache + imageMemoryCache
        val memoryCache: Long get() = imageMemoryCache + playUrlMemoryCache + subtitleDanmakuMemoryCache
        val totalSize: Long get() = imageDiskCache + httpCache + otherCache + memoryCache
        
        fun format(): String = formatSize(totalSize.toDouble())
        
        fun formatBreakdown(): String = buildString {
            append("图片: ${formatSize(imageCache.toDouble())}")
            append(" | HTTP: ${formatSize(httpCache.toDouble())}")
            append(" | 其他: ${formatSize(otherCache.toDouble())}")
            if (memoryCache > 0) {
                append(" | 内存: ${formatSize(memoryCache.toDouble())}")
            }
        }
    }

    /**
     *  获取总缓存大小（格式化字符串）
     */
    suspend fun getTotalCacheSize(context: Context): String = withContext(Dispatchers.IO) {
        getCacheBreakdown(context).format()
    }

    /**
     *  获取详细缓存统计
     */
    suspend fun getCacheBreakdown(context: Context): CacheBreakdown = withContext(Dispatchers.IO) {
        var imageDiskCache = 0L
        var imageMemoryCache = 0L
        var httpCache = 0L
        var otherCache = 0L
        var playUrlMemoryCache = 0L
        var subtitleDanmakuMemoryCache = 0L

        // 1. Coil 内存缓存
        context.imageLoader.memoryCache?.size?.let { imageMemoryCache += it }
        
        // 2. PlayUrlCache 内存缓存（估算：每条约 2KB）
        val playUrlCacheSize = PlayUrlCache.size()
        playUrlMemoryCache += playUrlCacheSize * 2048L

        // 3. 字幕与弹幕内存缓存
        subtitleDanmakuMemoryCache += SubtitleAndAuxRepository.getSubtitleCueCacheStats().estimatedBytes
        subtitleDanmakuMemoryCache += DanmakuRepository.getDanmakuCacheStats().totalBytes

        // 4. 内部缓存目录分类统计
        context.cacheDir?.let { cacheDir ->
            cacheDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val size = file.length()
                    when {
                        // Coil 图片缓存目录
                        file.absolutePath.contains("image_cache") -> imageDiskCache += size
                        // OkHttp 缓存目录
                        file.absolutePath.contains("http_cache") ||
                        file.absolutePath.contains("okhttp") -> httpCache += size
                        // 其他缓存
                        else -> otherCache += size
                    }
                }
        }

        // 5. 应用私有日志文件
        otherCache += Logger.getPrivateLogArtifactsSize(context)

        // 6. 外部缓存目录
        context.externalCacheDir?.let { extCacheDir ->
            otherCache += getDirSizeFast(extCacheDir)
        }

        CacheBreakdown(
            imageDiskCache = imageDiskCache,
            imageMemoryCache = imageMemoryCache,
            httpCache = httpCache,
            otherCache = otherCache,
            playUrlMemoryCache = playUrlMemoryCache,
            subtitleDanmakuMemoryCache = subtitleDanmakuMemoryCache
        )
    }

    /**
     *  清除所有缓存（优化顺序，避免冲突）
     */
    suspend fun clearAllCache(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        clearCache(context, CacheClearTarget.entries.toSet())
    }

    suspend fun clearCache(
        context: Context,
        targets: Set<CacheClearTarget>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (CacheClearTarget.IMAGE_PREVIEW in targets) {
                context.imageLoader.memoryCache?.clear()
                context.imageLoader.diskCache?.clear()
                Logger.d(TAG, " Image preview cache cleared")
            }

            if (CacheClearTarget.PLAYBACK_QUALITY in targets) {
                PlayUrlCache.clear()
                PlaybackCooldownManager.clearAll()
                Logger.d(TAG, " Playback quality cache cleared")
            }

            if (CacheClearTarget.SUBTITLE_DANMAKU in targets) {
                SubtitleAndAuxRepository.clearSubtitleCueCache()
                DanmakuRepository.clearDanmakuCache()
                Logger.d(TAG, " Subtitle and danmaku cache cleared")
            }

            if (CacheClearTarget.NETWORK in targets) {
                try {
                    com.bbttvv.app.core.network.NetworkModule.okHttpClient.cache?.evictAll()
                    Logger.d(TAG, " Network cache cleared")
                } catch (e: Exception) {
                    Logger.w(TAG, "OkHttp cache clear failed: ${e.message}")
                }
            }

            if (CacheClearTarget.TEMP_FILES_AND_LOGS in targets) {
                val excludePatterns = buildList {
                    if (CacheClearTarget.IMAGE_PREVIEW !in targets) add("image_cache")
                    if (CacheClearTarget.NETWORK !in targets) {
                        add("okhttp")
                        add("http_cache")
                    }
                }
                context.cacheDir?.let { cacheDir ->
                    clearDirContentsSelective(cacheDir, excludePatterns = excludePatterns)
                }
                context.externalCacheDir?.let { clearDirContents(it) }
                Logger.clearPrivateLogArtifacts(context)
                Logger.d(TAG, " Temp files and log artifacts cleared")
            }

            if (CacheClearTarget.APP_METADATA in targets) {
                FollowingCacheStore.clear(context)
                com.bbttvv.app.core.network.WbiKeyManager.invalidateCache()
                Logger.d(TAG, " App metadata cache cleared")
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error clearing cache", e)
        }
    }

    /**
     *  清除缓存并返回进度 Flow
     */
    fun clearAllCacheWithProgress(context: Context): Flow<ClearProgress> = flow {
        emit(ClearProgress(0, "正在清除内存缓存..."))
        
        // 内存缓存
        context.imageLoader.memoryCache?.clear()
        PlayUrlCache.clear()
        SubtitleAndAuxRepository.clearSubtitleCueCache()
        DanmakuRepository.clearDanmakuCache()
        emit(ClearProgress(20, "内存缓存已清除"))
        
        // 磁盘缓存
        emit(ClearProgress(30, "正在清除图片缓存..."))
        context.imageLoader.diskCache?.clear()
        emit(ClearProgress(50, "图片缓存已清除"))
        
        emit(ClearProgress(60, "正在清除网络缓存..."))
        try {
            com.bbttvv.app.core.network.NetworkModule.okHttpClient.cache?.evictAll()
        } catch (_: Exception) {}
        emit(ClearProgress(70, "网络缓存已清除"))
        
        // 文件缓存
        emit(ClearProgress(80, "正在清除临时文件..."))
        context.cacheDir?.let { clearDirContentsSelective(it, listOf("image_cache", "okhttp")) }
        context.externalCacheDir?.let { clearDirContents(it) }
        emit(ClearProgress(90, "临时文件已清除"))
        
        // 应用缓存
        FollowingCacheStore.clear(context)
        com.bbttvv.app.core.network.WbiKeyManager.invalidateCache()
        PlaybackCooldownManager.clearAll()
        Logger.clearPrivateLogArtifacts(context)
        
        emit(ClearProgress(100, "清理完成"))
    }.flowOn(Dispatchers.IO)

    /**
     * 清理进度数据类
     */
    data class ClearProgress(
        val percent: Int,
        val message: String
    )
    
    /**
     *  清除缓存并返回进度 Flow (增强版 - 支持动画)
     * 返回已清理的字节数和总字节数
     */
    data class ClearProgressV2(
        val cleared: Long,       // 已清理字节数
        val total: Long,         // 总字节数
        val isComplete: Boolean, // 是否完成
        val message: String      // 状态消息
    ) {
        fun formatCleared(): String = formatSizeStatic(cleared.toDouble())
        
        companion object {
            private fun formatSizeStatic(size: Double): String {
                val kiloByte = size / 1024
                if (kiloByte < 1) return "0 KB"
                val megaByte = kiloByte / 1024
                if (megaByte < 1) return String.format(Locale.US, "%.1f KB", kiloByte)
                val gigaByte = megaByte / 1024
                if (gigaByte < 1) return String.format(Locale.US, "%.1f MB", megaByte)
                return String.format(Locale.US, "%.2f GB", gigaByte)
            }
        }
    }

    fun clearAllCacheWithProgressV2(context: Context): Flow<ClearProgressV2> = flow {
        // 首先获取总大小
        val breakdown = getCacheBreakdown(context)
        val totalSize = breakdown.totalSize
        var clearedSize = 0L
        
        emit(ClearProgressV2(0, totalSize, false, "正在清除内存缓存..."))
        
        // 内存缓存
        val memorySize = breakdown.memoryCache
        context.imageLoader.memoryCache?.clear()
        PlayUrlCache.clear()
        SubtitleAndAuxRepository.clearSubtitleCueCache()
        DanmakuRepository.clearDanmakuCache()
        clearedSize += memorySize
        emit(ClearProgressV2(clearedSize, totalSize, false, "内存缓存已清除"))
        kotlinx.coroutines.delay(100)
        
        // 磁盘图片缓存
        emit(ClearProgressV2(clearedSize, totalSize, false, "正在清除图片缓存..."))
        val imageSize = breakdown.imageCache
        context.imageLoader.diskCache?.clear()
        clearedSize += imageSize
        emit(ClearProgressV2(clearedSize, totalSize, false, "图片缓存已清除"))
        kotlinx.coroutines.delay(100)
        
        // 网络缓存
        emit(ClearProgressV2(clearedSize, totalSize, false, "正在清除网络缓存..."))
        val httpSize = breakdown.httpCache
        try {
            com.bbttvv.app.core.network.NetworkModule.okHttpClient.cache?.evictAll()
        } catch (_: Exception) {}
        clearedSize += httpSize
        emit(ClearProgressV2(clearedSize, totalSize, false, "网络缓存已清除"))
        kotlinx.coroutines.delay(100)
        
        // 文件缓存
        emit(ClearProgressV2(clearedSize, totalSize, false, "正在清除临时文件..."))
        val otherSize = breakdown.otherCache
        context.cacheDir?.let { clearDirContentsSelective(it, listOf("image_cache", "okhttp")) }
        context.externalCacheDir?.let { clearDirContents(it) }
        clearedSize += otherSize
        emit(ClearProgressV2(clearedSize, totalSize, false, "临时文件已清除"))
        kotlinx.coroutines.delay(100)
        
        // 应用缓存
        FollowingCacheStore.clear(context)
        com.bbttvv.app.core.network.WbiKeyManager.invalidateCache()
        PlaybackCooldownManager.clearAll()
        Logger.clearPrivateLogArtifacts(context)
        
        emit(ClearProgressV2(totalSize, totalSize, true, "清理完成"))
    }.flowOn(Dispatchers.IO)

    /**
     *  使用 walkTopDown 惰性序列快速计算目录大小
     */
    private fun getDirSizeFast(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        return try {
            dir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        } catch (e: Exception) {
            Logger.w(TAG, "Error calculating dir size: ${e.message}")
            0L
        }
    }

    /**
     * 清空目录内容（保留目录本身）
     */
    private fun clearDirContents(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        return try {
            dir.walkTopDown()
                .filter { it != dir }  // 排除根目录
                .sortedByDescending { it.absolutePath.length }  // 深度优先删除
                .forEach { it.delete() }
            true
        } catch (e: Exception) {
            Logger.w(TAG, "Error clearing directory: ${dir.path}")
            false
        }
    }

    /**
     *  选择性清空目录（排除指定模式的子目录）
     */
    private fun clearDirContentsSelective(dir: File, excludePatterns: List<String>): Boolean {
        if (!dir.exists()) return false
        return try {
            dir.walkTopDown()
                .filter { file ->
                    file != dir && excludePatterns.none { pattern -> 
                        file.absolutePath.contains(pattern) 
                    }
                }
                .sortedByDescending { it.absolutePath.length }
                .forEach { it.delete() }
            true
        } catch (e: Exception) {
            Logger.w(TAG, "Error clearing directory selectively: ${dir.path}")
            false
        }
    }

    /**
     * 格式化文件大小
     */
    private fun formatSize(size: Double): String {
        val kiloByte = size / 1024
        if (kiloByte < 1) return "0 KB"
        val megaByte = kiloByte / 1024
        if (megaByte < 1) return String.format(Locale.US, "%.1f KB", kiloByte)
        val gigaByte = megaByte / 1024
        if (gigaByte < 1) return String.format(Locale.US, "%.1f MB", megaByte)
        return String.format(Locale.US, "%.2f GB", gigaByte)
    }
}

