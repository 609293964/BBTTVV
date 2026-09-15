package com.bbttvv.app.feature.video.danmaku

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.widget.FrameLayout
import androidx.core.graphics.PathParser
import com.bytedance.danmaku.render.engine.DanmakuView

/** Parsed paths are cached for one segment and resolved on Dispatchers.Default by the overlay. */
class DanmakuMask private constructor(
    private val archive: WebMaskArchive,
) {
    private var cachedSegmentIndex = -1
    private var cachedFrames: List<Frame> = emptyList()

    @Synchronized
    fun frameAt(positionMs: Long): Frame? {
        val index = archive.segmentIndexAt(positionMs)
        if (index < 0) return null
        if (index != cachedSegmentIndex) {
            cachedSegmentIndex = index
            cachedFrames = try {
                val records = archive.readFrames(index)
                records.mapIndexed { frameIndex, record ->
                    val svg = android.util.Base64.decode(
                        record.dataUri.substringAfter(','), android.util.Base64.DEFAULT,
                    ).toString(Charsets.UTF_8)
                    parseFrame(
                        record.timeMs,
                        records.getOrNull(frameIndex + 1)?.timeMs
                            ?: archive.segments.getOrNull(index + 1)?.startMs
                            ?: (record.timeMs + 100L),
                        svg,
                    )
                }
            } catch (_: Exception) {
                com.bbttvv.app.core.util.Logger.w("DanmakuMask", "Invalid webmask segment index=$index")
                emptyList()
            }
        }
        return cachedFrames.lastOrNull { positionMs >= it.startMs && positionMs < it.endMs }
    }

    companion object {
        fun fromBinary(binary: ByteArray): DanmakuMask? =
            runCatching { DanmakuMask(WebMaskArchive.parse(binary)) }.getOrNull()

        private fun parseFrame(startMs: Long, endMs: Long, svg: String): Frame {
            val viewBox = VIEW_BOX.find(svg)?.groupValues?.get(1)?.trim()?.split(Regex("[ ,]+"))
            val width = numberAttribute(svg, "width") ?: viewBox?.getOrNull(2)?.toFloatOrNull() ?: 1920f
            val height = numberAttribute(svg, "height") ?: viewBox?.getOrNull(3)?.toFloatOrNull() ?: 1080f
            val viewBoxX = viewBox?.getOrNull(0)?.toFloatOrNull() ?: 0f
            val viewBoxY = viewBox?.getOrNull(1)?.toFloatOrNull() ?: 0f
            val viewBoxWidth = viewBox?.getOrNull(2)?.toFloatOrNull() ?: width
            val viewBoxHeight = viewBox?.getOrNull(3)?.toFloatOrNull() ?: height
            val transform = GROUP_TRANSFORM.find(svg)?.groupValues?.get(1).orEmpty()
            val scale = SCALE_TRANSFORM.find(transform)
            val scaleX = scale?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 1f
            val scaleY = scale?.groupValues?.getOrNull(2)?.toFloatOrNull() ?: scaleX
            val translate = TRANSLATE_TRANSFORM.find(transform)
            val translateX = translate?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
            val translateY = translate?.groupValues?.getOrNull(2)?.toFloatOrNull() ?: 0f
            val transformMatrix = Matrix().apply {
                setScale(scaleX, scaleY)
                postTranslate(translateX, translateY)
            }
            val paths = PATH_D.findAll(svg).mapNotNull { match ->
                runCatching {
                    PathParser.createPathFromPathData(match.groupValues[1]).also { it.transform(transformMatrix) }
                }.getOrNull()
            }.toList()
            return Frame(
                startMs = startMs,
                endMs = endMs,
                paths = paths,
                viewBoxX = viewBoxX,
                viewBoxY = viewBoxY,
                viewBoxWidth = viewBoxWidth,
                viewBoxHeight = viewBoxHeight,
            )
        }

        private fun numberAttribute(svg: String, name: String): Float? =
            Regex("\\b$name\\s*=\\s*[\\\"']([0-9.]+)").find(svg)?.groupValues?.get(1)?.toFloatOrNull()

        private val VIEW_BOX = Regex("\\bviewBox\\s*=\\s*[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE)
        private val GROUP_TRANSFORM = Regex("<g\\b[^>]*\\btransform\\s*=\\s*[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE)
        private val SCALE_TRANSFORM = Regex("scale\\s*\\(\\s*([-+0-9.eE]+)(?:\\s*[, ]\\s*([-+0-9.eE]+))?\\s*\\)", RegexOption.IGNORE_CASE)
        private val TRANSLATE_TRANSFORM = Regex("translate\\s*\\(\\s*([-+0-9.eE]+)(?:\\s*[, ]\\s*([-+0-9.eE]+))?\\s*\\)", RegexOption.IGNORE_CASE)
        private val PATH_D = Regex("<path\\b[^>]*\\bd\\s*=\\s*[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE)
    }



    data class Frame(
        val startMs: Long,
        val endMs: Long,
        val paths: List<Path>,
        val viewBoxX: Float,
        val viewBoxY: Float,
        val viewBoxWidth: Float,
        val viewBoxHeight: Float,
    )
}

/** Host that clips only the danmaku child, keeping the video and controls untouched. */
internal class DanmakuMaskHostView(context: Context) : FrameLayout(context) {
    val danmakuView = DanmakuView(context)
    var frame: DanmakuMask.Frame? = null
    var maskEnabled: Boolean = false
    var videoAspectRatio: Float = 16f / 9f
    private val mergedPath = Path()
    private var cachedFrame: DanmakuMask.Frame? = null
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var cachedAspect = 0f

    init {
        addView(danmakuView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setWillNotDraw(false)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!maskEnabled || width <= 0 || height <= 0) {
            super.dispatchDraw(canvas)
            return
        }
        val frame = frame
        if (frame == null || frame.paths.isEmpty()) {
            cachedFrame = null
            super.dispatchDraw(canvas)
            return
        }
        if (frame !== cachedFrame || cachedWidth != width || cachedHeight != height ||
            cachedAspect != videoAspectRatio
        ) {
            rebuildPath(frame)
            cachedFrame = frame
            cachedWidth = width
            cachedHeight = height
            cachedAspect = videoAspectRatio
        }
        if (mergedPath.isEmpty) {
            super.dispatchDraw(canvas)
            return
        }
        val save = canvas.save()
        canvas.clipPath(mergedPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    private fun rebuildPath(frame: DanmakuMask.Frame) {
        mergedPath.reset()
        val aspect = videoAspectRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        val viewAspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        val dstWidth: Float
        val dstHeight: Float
        val offsetX: Float
        val offsetY: Float
        if (aspect > viewAspect) {
            dstWidth = width.toFloat()
            dstHeight = dstWidth / aspect
            offsetX = 0f
            offsetY = (height - dstHeight) / 2f
        } else {
            dstHeight = height.toFloat()
            dstWidth = dstHeight * aspect
            offsetX = (width - dstWidth) / 2f
            offsetY = 0f
        }
        val matrix = Matrix().apply {
            setScale(dstWidth / frame.viewBoxWidth.coerceAtLeast(1f), dstHeight / frame.viewBoxHeight.coerceAtLeast(1f))
            postTranslate(offsetX - frame.viewBoxX * (dstWidth / frame.viewBoxWidth.coerceAtLeast(1f)), offsetY - frame.viewBoxY * (dstHeight / frame.viewBoxHeight.coerceAtLeast(1f)))
        }
        frame.paths.forEach { path ->
            val transformed = Path(path)
            transformed.transform(matrix)
            mergedPath.addPath(transformed)
        }
    }
}
