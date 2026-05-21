package com.bbttvv.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil.size.Size
import coil.transform.Transformation

/**
 * 一个高性能的 Coil 高斯模糊转换器。
 * 专门针对 TV 列表页卡片背景模糊进行了下采样 (sampling) 优化。
 */
class BlurTransformation(
    private val context: Context,
    private val radius: Float = 25f,
    private val sampling: Float = 4f
) : Transformation {

    override val cacheKey: String = "BlurTransformation-$radius-$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = (input.width / sampling).toInt().coerceAtLeast(1)
        val height = (input.height / sampling).toInt().coerceAtLeast(1)

        // 1. 创建超轻量的下采样缩略图，极大地减少像素计算量
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.scale(1f / sampling, 1f / sampling)
        canvas.drawBitmap(input, 0f, 0f, paint)

        // 2. 使用硬件加速的 RenderScript 执行高斯模糊
        var rs: RenderScript? = null
        var inputAlloc: Allocation? = null
        var outputAlloc: Allocation? = null
        var blur: ScriptIntrinsicBlur? = null
        try {
            rs = RenderScript.create(context)
            inputAlloc = Allocation.createFromBitmap(
                rs,
                bitmap,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT
            )
            outputAlloc = Allocation.createTyped(rs, inputAlloc.type)
            blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            blur.setRadius(radius.coerceIn(1f, 25f))
            blur.setInput(inputAlloc)
            blur.forEach(outputAlloc)
            outputAlloc.copyTo(bitmap)
        } catch (e: Exception) {
            // 降级兜底：若 RenderScript 出错，则返回采样的缩略图（略微带一点马赛克感，也算是一种模糊效果）
            e.printStackTrace()
        } finally {
            // 3. 安全释放硬件分配的内存
            inputAlloc?.destroy()
            outputAlloc?.destroy()
            blur?.destroy()
            rs?.destroy()
        }

        return bitmap
    }
}
