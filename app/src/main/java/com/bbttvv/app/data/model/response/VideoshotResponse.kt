package com.bbttvv.app.data.model.response

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Videoshot API 响应
 * 
 * 用于获取视频预览缩略图雪碧图数据
 * API: GET https://api.bilibili.com/x/player/videoshot?bvid={bvid}&cid={cid}
 */
@Serializable
data class VideoshotResponse(
    val code: Int = 0,
    val message: String = "",
    val data: VideoshotData? = null
)

/**
 * 视频预览图数据
 * 
 * 包含雪碧图 URL 列表和时间索引信息
 */
@Serializable
data class VideoshotData(
    val pvdata: String = "",           // PV 数据 URL（可忽略）
    val img_x_len: Int = 10,           // 雪碧图每行缩略图数量
    val img_y_len: Int = 10,           // 雪碧图行数
    val img_x_size: Int = 160,         // 单个缩略图宽度（像素）
    val img_y_size: Int = 90,          // 单个缩略图高度（像素）
    val image: List<String> = emptyList(), // 雪碧图 URL 列表
    val index: List<Long> = emptyList()    // 每个缩略图对应的时间点（毫秒）
) {
    /**
     * 根据播放位置获取对应的预览图信息
     * 
     * @param positionMs 播放位置（毫秒）
     * @param durationMs 视频总时长（毫秒，可选，用于补全缺失索引或单位换算）
     * @return Triple<雪碧图URL, X偏移, Y偏移> 或 null（如果数据不可用）
     */
    fun getPreviewInfo(positionMs: Long, durationMs: Long? = null): Triple<String, Int, Int>? {
        if (image.isEmpty()) return null
        val perImage = img_x_len * img_y_len
        if (perImage <= 0) return null
        
        // 将索引统一成毫秒，若返回值明显是秒（相比总时长小很多），自动换算
        val timelineMs: List<Long>? = when {
            index.isEmpty() -> null
            durationMs != null && index.lastOrNull()?.let { last -> last > 0 && last < durationMs / 2 } == true ->
                index.map { it * 1000 }  // API 可能返回秒级时间戳
            else -> index
        }
        
        // 如果缺少时间轴数据，按进度比例估算一个帧序号
        val targetFrameIndex = when {
            timelineMs != null -> {
                var low = 0
                var high = timelineMs.size - 1
                var resultIndex = 0
                
                while (low <= high) {
                    val mid = (low + high) / 2
                    if (timelineMs[mid] <= positionMs) {
                        resultIndex = mid
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                resultIndex
            }
            durationMs != null && durationMs > 0 -> {
                val totalFrames = image.size * perImage
                if (totalFrames <= 0) return null
                val ratio = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                (ratio * (totalFrames - 1)).roundToInt()
            }
            else -> return null
        }
        
        // 计算所在的雪碧图与偏移
        val imageIndex = targetFrameIndex / perImage
        if (imageIndex >= image.size) return null
        
        val localIndex = targetFrameIndex % perImage
        val row = localIndex / img_x_len
        val col = localIndex % img_x_len
        val offsetX = col * img_x_size
        val offsetY = row * img_y_size
        
        return Triple(image[imageIndex], offsetX, offsetY)
    }
    
    /**
     * 检查数据是否有效
     */
    val isValid: Boolean
        get() = image.isNotEmpty() && index.isNotEmpty() && img_x_size > 0 && img_y_size > 0
}

