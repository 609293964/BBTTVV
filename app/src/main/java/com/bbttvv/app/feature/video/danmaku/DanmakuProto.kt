package com.bbttvv.app.feature.video.danmaku

import android.util.Log
import com.bbttvv.app.proto.dm.DmSegMobileReply as ProtoDmSegMobileReply
import com.bbttvv.app.proto.dmview.DmWebViewReply as ProtoDmWebViewReply

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
                commandDms = reply.commandDmsList.map { command ->
                    CommandDm(
                        id = command.id,
                        oid = command.oid,
                        mid = command.mid,
                        command = command.command,
                        content = command.content,
                        progress = command.progress,
                        ctime = command.ctime,
                        mtime = command.mtime,
                        extra = command.extra,
                        idStr = command.idStr,
                    )
                },
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

}
