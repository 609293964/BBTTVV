package com.bbttvv.app.feature.video.danmaku

import android.util.Log
import com.bbttvv.app.proto.dm.DmSegMobileReply as ProtoDmSegMobileReply
import com.bbttvv.app.proto.dmview.DmWebViewReply as ProtoDmWebViewReply
import java.nio.charset.StandardCharsets

object DanmakuProto {
    private const val TAG = "DanmakuProto"

    data class DanmakuElem(
        val id: Long = 0L,
        val progress: Int = 0,
        val mode: Int = 1,
        val fontsize: Int = 25,
        val color: Int = 0xFFFFFF,
        val midHash: String = "",
        val content: String = "",
        val weight: Int = 0,
        val pool: Int = 0
    )

    data class DmWebViewReply(
        val state: Int = 0,
        val textSide: String = "",
        val dmSge: DmSegConfig? = null,
        val flag: DanmakuFlagConfig? = null,
        val specialDms: List<String> = emptyList(),
        val checkBox: Boolean = true,
        val count: Long = 0L,
        val commandDms: List<CommandDm> = emptyList(),
        val dmSetting: DmSetting? = null
    )

    data class DmSegConfig(
        val pageSize: Long = 0L,
        val total: Long = 0L
    )

    data class DanmakuFlagConfig(
        val recFlag: Int = 0,
        val recText: String = "",
        val recSwitch: Int = 0
    )

    data class CommandDm(
        val id: Long = 0L,
        val oid: Long = 0L,
        val mid: String = "",
        val command: String = "",
        val content: String = "",
        val progress: Int = 0,
        val ctime: String = "",
        val mtime: String = "",
        val extra: String = "",
        val idStr: String = ""
    )

    data class DmSetting(
        val dmSwitch: Boolean = true,
        val aiSwitch: Boolean = false,
        val aiLevel: Int = 0,
        val allowTop: Boolean = true,
        val allowScroll: Boolean = true,
        val allowBottom: Boolean = true,
        val allowColor: Boolean = true,
        val allowSpecial: Boolean = true,
        val opacity: Float = 1.0f,
        val dmarea: Int = 0,
        val speedplus: Float = 1.0f,
        val fontsize: Float = 1.0f
    )

    fun parseWebViewReply(data: ByteArray): DmWebViewReply {
        if (data.isEmpty()) return DmWebViewReply()

        return try {
            val reply = ProtoDmWebViewReply.parseFrom(data)
            val setting = if (reply.hasDmSetting()) reply.dmSetting else null
            DmWebViewReply(
                state = reply.state,
                textSide = reply.textSide,
                dmSge = reply.dmSge.takeIf { reply.hasDmSge() }?.let {
                    DmSegConfig(
                        pageSize = it.pageSize,
                        total = it.total
                    )
                },
                count = reply.count,
                dmSetting = setting?.let {
                    DmSetting(
                        dmSwitch = it.dmSwitch,
                        aiSwitch = it.aiSwitch,
                        aiLevel = it.aiLevel,
                        allowTop = it.blocktop,
                        allowScroll = it.blockscroll,
                        allowBottom = it.blockbottom,
                        allowColor = it.blockcolor,
                        allowSpecial = it.blockspecial,
                        opacity = it.opacity,
                        dmarea = it.dmarea,
                        speedplus = it.speedplus,
                        fontsize = it.fontsize
                    )
                }
            )
        } catch (error: Exception) {
            Log.e(TAG, "Failed to parse danmaku metadata: ${error.message}", error)
            DmWebViewReply()
        }
    }

    fun parse(data: ByteArray): List<DanmakuElem> {
        if (data.isEmpty()) return emptyList()

        return try {
            ProtoDmSegMobileReply.parseFrom(data)
                .elemsList
                .mapNotNull { elem ->
                    DanmakuElem(
                        id = elem.id,
                        progress = elem.progress,
                        mode = elem.mode,
                        fontsize = elem.fontsize,
                        color = elem.color,
                        midHash = elem.midHash,
                        content = elem.content,
                        weight = elem.weight,
                        pool = elem.pool
                    ).takeIf { it.content.isNotBlank() }
                }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to parse protobuf danmaku: ${error.message}", error)
            emptyList()
        }
    }

    private fun parseDanmakuElem(data: ByteArray): DanmakuElem? {
        if (data.isEmpty()) return null

        var id = 0L
        var progress = 0
        var mode = 1
        var fontsize = 25
        var color = 0xFFFFFF
        var midHash = ""
        var content = ""
        var weight = 0
        var pool = 0

        return try {
            val input = ProtoInput(data)
            while (!input.isAtEnd()) {
                val tag = input.readTag()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 -> id = input.readVarint()
                    2 -> progress = input.readVarint().toInt()
                    3 -> mode = input.readVarint().toInt()
                    4 -> fontsize = input.readVarint().toInt()
                    5 -> color = input.readVarint().toInt()
                    6 -> midHash = input.readString()
                    7 -> content = input.readString()
                    8 -> input.readVarint()
                    9 -> weight = input.readVarint().toInt()
                    10 -> input.readString()
                    11 -> pool = input.readVarint().toInt()
                    12 -> input.readString()
                    13 -> input.readVarint()
                    else -> input.skipField(wireType)
                }
            }
            DanmakuElem(
                id = id,
                progress = progress,
                mode = mode,
                fontsize = fontsize,
                color = color,
                midHash = midHash,
                content = content,
                weight = weight,
                pool = pool
            )
        } catch (error: Exception) {
            Log.w(TAG, "Failed to parse a danmaku element: ${error.message}")
            null
        }
    }

    private fun parseDmSegConfig(data: ByteArray): DmSegConfig {
        var pageSize = 0L
        var total = 0L
        runCatching {
            val input = ProtoInput(data)
            while (!input.isAtEnd()) {
                val tag = input.readTag()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 -> pageSize = input.readVarint()
                    2 -> total = input.readVarint()
                    else -> input.skipField(wireType)
                }
            }
        }
        return DmSegConfig(pageSize = pageSize, total = total)
    }

    private fun parseDanmakuFlagConfig(data: ByteArray): DanmakuFlagConfig {
        var recFlag = 0
        var recText = ""
        var recSwitch = 0
        runCatching {
            val input = ProtoInput(data)
            while (!input.isAtEnd()) {
                val tag = input.readTag()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 -> recFlag = input.readVarint().toInt()
                    2 -> recText = input.readString()
                    3 -> recSwitch = input.readVarint().toInt()
                    else -> input.skipField(wireType)
                }
            }
        }
        return DanmakuFlagConfig(recFlag = recFlag, recText = recText, recSwitch = recSwitch)
    }

    private fun parseCommandDm(data: ByteArray): CommandDm? {
        if (data.isEmpty()) return null
        var id = 0L
        var oid = 0L
        var mid = ""
        var command = ""
        var content = ""
        var progress = 0
        var ctime = ""
        var mtime = ""
        var extra = ""
        var idStr = ""

        return runCatching {
            val input = ProtoInput(data)
            while (!input.isAtEnd()) {
                val tag = input.readTag()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 -> id = input.readVarint()
                    2 -> oid = input.readVarint()
                    3 -> mid = input.readString()
                    4 -> command = input.readString()
                    5 -> content = input.readString()
                    6 -> progress = input.readVarint().toInt()
                    7 -> ctime = input.readString()
                    8 -> mtime = input.readString()
                    9 -> extra = input.readString()
                    10 -> idStr = input.readString()
                    else -> input.skipField(wireType)
                }
            }
            CommandDm(id, oid, mid, command, content, progress, ctime, mtime, extra, idStr)
        }.getOrNull()
    }

    private fun parseDmSetting(data: ByteArray): DmSetting {
        if (data.isEmpty()) return DmSetting()

        var dmSwitch = true
        var aiSwitch = false
        var aiLevel = 0
        var allowTop = true
        var allowScroll = true
        var allowBottom = true
        var allowColor = true
        var allowSpecial = true
        var opacity = 1.0f
        var dmarea = 0
        var speedplus = 1.0f
        var fontsize = 1.0f

        runCatching {
            val input = ProtoInput(data)
            while (!input.isAtEnd()) {
                val tag = input.readTag()
                val fieldNumber = tag ushr 3
                val wireType = tag and 0x07
                when (fieldNumber) {
                    1 -> dmSwitch = input.readVarint() != 0L
                    2 -> aiSwitch = input.readVarint() != 0L
                    3 -> aiLevel = input.readVarint().toInt()
                    4 -> allowTop = input.readVarint() != 0L
                    5 -> allowScroll = input.readVarint() != 0L
                    6 -> allowBottom = input.readVarint() != 0L
                    7 -> allowColor = input.readVarint() != 0L
                    8 -> allowSpecial = input.readVarint() != 0L
                    10 -> opacity = java.lang.Float.intBitsToFloat(input.readFixed32())
                    11 -> dmarea = input.readVarint().toInt()
                    12 -> speedplus = java.lang.Float.intBitsToFloat(input.readFixed32())
                    13 -> fontsize = java.lang.Float.intBitsToFloat(input.readFixed32())
                    else -> input.skipField(wireType)
                }
            }
        }

        return DmSetting(
            dmSwitch = dmSwitch,
            aiSwitch = aiSwitch,
            aiLevel = aiLevel,
            allowTop = allowTop,
            allowScroll = allowScroll,
            allowBottom = allowBottom,
            allowColor = allowColor,
            allowSpecial = allowSpecial,
            opacity = opacity,
            dmarea = dmarea,
            speedplus = speedplus,
            fontsize = fontsize
        )
    }

    private fun decodeSpecialDmUrl(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val text = String(data, StandardCharsets.UTF_8).trim()
        return text.takeIf { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("//") }
    }

    private fun isLikelyDmSegConfig(config: DmSegConfig): Boolean {
        return config.pageSize >= 1000L && config.total in 1L..10_000L
    }

    private fun isLikelyDanmakuFlagConfig(config: DanmakuFlagConfig): Boolean {
        return config.recFlag != 0 || config.recSwitch != 0 || config.recText.isNotBlank()
    }

    private class ProtoInput(private val data: ByteArray) {
        private var position = 0

        fun isAtEnd(): Boolean = position >= data.size

        fun readTag(): Int = readVarint().toInt()

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (position < data.size) {
                val byte = data[position++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if ((byte and 0x80) == 0) break
                shift += 7
                if (shift >= 64) {
                    throw IllegalStateException("Varint too long")
                }
            }
            return result
        }

        fun readBytes(): ByteArray {
            val length = readVarint().toInt()
            if (length <= 0 || position + length > data.size) {
                return ByteArray(0)
            }
            val result = data.copyOfRange(position, position + length)
            position += length
            return result
        }

        fun readString(): String = String(readBytes(), StandardCharsets.UTF_8)

        fun readFixed32(): Int {
            if (position + 4 > data.size) {
                position = data.size
                return 0
            }
            val b0 = data[position++].toInt() and 0xFF
            val b1 = data[position++].toInt() and 0xFF
            val b2 = data[position++].toInt() and 0xFF
            val b3 = data[position++].toInt() and 0xFF
            return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        }

        fun skipField(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> position = (position + 8).coerceAtMost(data.size)
                2 -> position = (position + readVarint().toInt()).coerceAtMost(data.size)
                5 -> position = (position + 4).coerceAtMost(data.size)
            }
        }
    }
}
