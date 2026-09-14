package com.bbttvv.app.core.network.socket

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuProtocolTest {
    @Test
    fun `extended header bytes are skipped before body`() = runBlocking {
        val body = "abc".toByteArray()
        val headerLength = DanmakuProtocol.HEAD_LENGTH + 4
        val totalLength = headerLength + body.size
        val frame = ByteBuffer.allocate(totalLength)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(totalLength)
            .putShort(headerLength.toShort())
            .putShort(DanmakuProtocol.PROTO_VER_JSON.toShort())
            .putInt(DanmakuProtocol.OP_MESSAGE)
            .putInt(1)
            .putInt(0x01020304)
            .put(body)
            .array()

        val packets = DanmakuProtocol.decode(frame)

        assertEquals(1, packets.size)
        assertArrayEquals(body, packets.single().body)
    }

    @Test
    fun `oversized input is rejected before parsing`() = runBlocking {
        val data = ByteArray(DanmakuProtocol.MAX_INPUT_BYTES + 1)
        assertTrue(DanmakuProtocol.decode(data).isEmpty())
    }

    @Test
    fun `nested compressed packets stop at depth limit`() = runBlocking {
        var payload = DanmakuProtocol.encode(
            DanmakuProtocol.Packet(
                version = DanmakuProtocol.PROTO_VER_JSON,
                operation = DanmakuProtocol.OP_MESSAGE,
                body = "leaf".toByteArray()
            )
        )
        repeat(DanmakuProtocol.MAX_COMPRESSION_DEPTH + 1) {
            payload = DanmakuProtocol.encode(
                DanmakuProtocol.Packet(
                    version = DanmakuProtocol.PROTO_VER_ZLIB,
                    operation = DanmakuProtocol.OP_MESSAGE,
                    body = zlib(payload)
                )
            )
        }

        assertTrue(DanmakuProtocol.decode(payload).isEmpty())
    }

    @Test
    fun `high ratio compressed payload is rejected`() = runBlocking {
        val compressed = zlib(ByteArray(1024 * 1024))
        val frame = DanmakuProtocol.encode(
            DanmakuProtocol.Packet(
                version = DanmakuProtocol.PROTO_VER_ZLIB,
                operation = DanmakuProtocol.OP_MESSAGE,
                body = compressed
            )
        )

        assertTrue(DanmakuProtocol.decode(frame).isEmpty())
    }

    @Test
    fun `decoded packet count is bounded`() = runBlocking {
        val packet = DanmakuProtocol.encode(
            DanmakuProtocol.Packet(
                version = DanmakuProtocol.PROTO_VER_JSON,
                operation = DanmakuProtocol.OP_MESSAGE,
                body = byteArrayOf(1)
            )
        )
        val data = ByteArrayOutputStream().apply {
            repeat(DanmakuProtocol.MAX_DECODED_PACKETS + 100) { write(packet) }
        }.toByteArray()

        assertEquals(
            DanmakuProtocol.MAX_DECODED_PACKETS,
            DanmakuProtocol.decode(data).size
        )
    }

    private fun zlib(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output).use { it.write(data) }
        return output.toByteArray()
    }
}
