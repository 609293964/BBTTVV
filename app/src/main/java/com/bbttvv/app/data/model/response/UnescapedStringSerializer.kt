package com.bbttvv.app.data.model.response

import com.bbttvv.app.core.util.HtmlEntityUtils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Decodes HTML entities in Bilibili comment text while reading API responses.
 */
object UnescapedStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor(
        "com.bbttvv.app.data.model.response.UnescapedString",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        return HtmlEntityUtils.unescape(decoder.decodeString())
    }
}
