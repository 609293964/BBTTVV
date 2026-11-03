package com.bbttvv.app.feature.video.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuProtoCompatibilityTest {
    @Test
    fun `segment wire payload preserves fields and skips unknown fields and blank comments`() {
        // Fixed wire bytes, independent of the generated serializer under test.
        // Field 99 is unknown; the second element deliberately has no content.
        val payload = hexBytes(
            "0A 23 08 2A 10 DC 0B 18 01 20 19 28 FF FF FF 07 " +
                "32 04 74 65 73 74 3A 06 E6 B5 8B E8 AF 95 48 05 58 01 98 06 01 " +
                "0A 02 08 01"
        )

        assertEquals(
            listOf(
                DanmakuProto.DanmakuElem(
                    id = 42L, progress = 1500, mode = 1, fontsize = 25,
                    color = 0xFFFFFF, midHash = "test", content = "测试", weight = 5, pool = 1,
                )
            ),
            DanmakuProto.parse(payload),
        )
    }

    @Test
    fun `metadata wire payload preserves segment config commands and player settings`() {
        val payload = hexBytes(
            "1A 02 6F 6B 22 06 08 C0 FC 15 10 03 40 07 " +
                "4A 11 08 07 22 06 23 56 4F 54 45 23 30 DC 0B 4A 02 7B 7D " +
                "52 07 08 01 5D 00 00 00 3F 98 06 01"
        )

        val reply = DanmakuProto.parseWebViewReply(payload)

        assertEquals("ok", reply.textSide)
        assertEquals(DanmakuProto.DmSegConfig(pageSize = 360000L, total = 3L), reply.dmSge)
        assertEquals(7L, reply.count)
        assertEquals(
            listOf(DanmakuProto.CommandDm(id = 7L, command = "#VOTE#", progress = 1500, extra = "{}")),
            reply.commandDms,
        )
        val setting = requireNotNull(reply.dmSetting)
        assertTrue(setting.dmSwitch)
        assertFalse(setting.aiSwitch)
        assertEquals(0.5f, setting.opacity, 0f)
    }

    @Test
    fun `older metadata without optional messages preserves absence`() {
        val reply = DanmakuProto.parseWebViewReply(hexBytes("40 02"))

        assertEquals(2L, reply.count)
        assertNull(reply.dmSge)
        assertNull(reply.dmSetting)
        assertTrue(reply.commandDms.isEmpty())
    }

    @Test
    fun `empty and truncated payloads retain recoverable fallback`() {
        assertTrue(DanmakuProto.parse(byteArrayOf()).isEmpty())
        assertTrue(DanmakuProto.parse(hexBytes("0A 05 08")).isEmpty())
        assertEquals(DanmakuProto.DmWebViewReply(), DanmakuProto.parseWebViewReply(byteArrayOf()))
        assertEquals(DanmakuProto.DmWebViewReply(), DanmakuProto.parseWebViewReply(hexBytes("22 05 08")))
    }

    private fun hexBytes(value: String): ByteArray =
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray()
}
