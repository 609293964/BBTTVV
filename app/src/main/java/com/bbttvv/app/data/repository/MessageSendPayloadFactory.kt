package com.bbttvv.app.data.repository

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object MessageSendPayloadFactory {

    fun buildTextContent(content: String): String {
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return "{\"content\":\"$escaped\"}"
    }

    fun buildWithdrawContent(msgKey: Long): String = msgKey.toString()

    fun buildImageContent(
        imageUrl: String,
        width: Int,
        height: Int,
        imageType: String,
        size: Float
    ): String {
        return buildJsonObject {
            put("url", imageUrl)
            put("height", height)
            put("width", width)
            put("imageType", imageType)
            put("original", 1)
            put("size", size)
        }.toString()
    }
}

