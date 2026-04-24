package com.bbttvv.app.feature.video.danmaku

import android.util.Xml
import com.bytedance.danmaku.render.engine.data.DanmakuData
import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_BOTTOM_CENTER
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_SCROLL
import com.bytedance.danmaku.render.engine.utils.LAYER_TYPE_TOP_CENTER
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream

object DanmakuParser {
    fun parseProtobuf(segments: List<ByteArray>): ParsedDanmaku {
        val standard = mutableListOf<DanmakuData>()
        val advanced = mutableListOf<AdvancedDanmakuData>()

        segments.forEach { segment ->
            runCatching {
                DanmakuProto.parse(segment).forEach { elem ->
                    if (elem.mode == 7) {
                        parseAdvancedDanmaku(
                            jsonContent = elem.content,
                            startTimeMs = elem.progress.toLong(),
                            color = elem.color
                        )?.let(advanced::add)
                    } else {
                        createTextDataFromProto(elem)?.let(standard::add)
                    }
                }
            }
        }

        return ParsedDanmaku(
            standardList = standard.sortedBy { it.showAtTime },
            advancedList = advanced.sortedBy { it.startTimeMs }
        )
    }

    fun parse(rawData: ByteArray): ParsedDanmaku {
        val standard = mutableListOf<DanmakuData>()
        val advanced = mutableListOf<AdvancedDanmakuData>()

        runCatching {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(rawData), "UTF-8")

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "d") {
                    val attr = parser.getAttributeValue(null, "p")
                    parser.next()
                    val content = if (parser.eventType == XmlPullParser.TEXT) parser.text else ""
                    if (!attr.isNullOrBlank() && content.isNotBlank()) {
                        val parts = attr.split(",")
                        val mode = parts.getOrNull(1)?.toIntOrNull() ?: 1
                        val timeMs = ((parts.firstOrNull()?.toFloatOrNull() ?: 0f) * 1000f).toLong()
                        val color = (parts.getOrNull(3)?.toLongOrNull() ?: 0xFFFFFF).toInt()
                        if (mode == 7) {
                            parseAdvancedDanmaku(content, timeMs, color)?.let(advanced::add)
                        } else {
                            createTextData(attr, content)?.let(standard::add)
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        return ParsedDanmaku(
            standardList = standard.sortedBy { it.showAtTime },
            advancedList = advanced.sortedBy { it.startTimeMs }
        )
    }

    private fun createTextDataFromProto(elem: DanmakuProto.DanmakuElem): TextData? {
        if (elem.content.isBlank() || elem.mode >= 8) return null
        return WeightedTextData().apply {
            danmakuId = elem.id
            userHash = elem.midHash
            weight = elem.weight
            pool = elem.pool
            text = elem.content
            showAtTime = elem.progress.toLong()
            layerType = mapLayerType(elem.mode)
            textColor = elem.color or 0xFF000000.toInt()
            textSize = elem.fontsize.toFloat()
        }
    }

    private fun createTextData(attr: String, content: String): TextData? {
        val parts = attr.split(",")
        if (parts.size < 4) return null

        val mode = parts[1].toIntOrNull() ?: 1
        if (mode >= 7) return null

        return WeightedTextData().apply {
            danmakuId = parts.getOrNull(7)?.toLongOrNull() ?: 0L
            userHash = parts.getOrNull(6).orEmpty()
            pool = parts.getOrNull(5)?.toIntOrNull() ?: 0
            text = content
            showAtTime = ((parts[0].toFloatOrNull() ?: 0f) * 1000f).toLong()
            layerType = mapLayerType(mode)
            textColor = ((parts[3].toLongOrNull() ?: 0xFFFFFF).toInt() or 0xFF000000.toInt())
            textSize = parts[2].toFloatOrNull() ?: 25f
        }
    }

    private fun parseAdvancedDanmaku(
        jsonContent: String,
        startTimeMs: Long,
        color: Int
    ): AdvancedDanmakuData? {
        if (!jsonContent.trim().startsWith("[")) return null
        return runCatching {
            val array = JSONArray(jsonContent)
            if (array.length() < 5) return null
            AdvancedDanmakuData(
                content = array.optString(4, ""),
                startTimeMs = startTimeMs,
                durationMs = (array.optDouble(3, 1.0) * 1000).toLong(),
                startX = array.optDouble(0, 0.0).toFloat(),
                startY = array.optDouble(1, 0.0).toFloat(),
                rotateZ = array.optDouble(5, 0.0).toFloat(),
                rotateY = array.optDouble(6, 0.0).toFloat(),
                color = color
            )
        }.getOrNull()
    }

    private fun mapLayerType(mode: Int): Int = when (mode) {
        4 -> LAYER_TYPE_BOTTOM_CENTER
        5 -> LAYER_TYPE_TOP_CENTER
        else -> LAYER_TYPE_SCROLL
    }
}
