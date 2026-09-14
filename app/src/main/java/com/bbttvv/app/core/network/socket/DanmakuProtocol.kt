package com.bbttvv.app.core.network.socket

import com.bbttvv.app.core.util.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.brotli.dec.BrotliInputStream

/**
 * Bilibili 直播弹幕协议解析器。
 *
 * 协议格式 (Big Endian):
 * [0-3]   Packet Length (Header + Body)
 * [4-5]   Header Length
 * [6-7]   Protocol Version
 * [8-11]  Operation
 * [12-15] Sequence ID
 * [16-..] Optional extended header + body
 */
object DanmakuProtocol {
    const val HEAD_LENGTH = 16

    const val PROTO_VER_JSON = 0
    const val PROTO_VER_HEARTBEAT = 1
    const val PROTO_VER_ZLIB = 2
    const val PROTO_VER_BROTLI = 3

    const val OP_HEARTBEAT = 2
    const val OP_HEARTBEAT_REPLY = 3
    const val OP_MESSAGE = 5
    const val OP_AUTH = 7
    const val OP_AUTH_REPLY = 8

    internal const val MAX_INPUT_BYTES = 8 * 1024 * 1024
    internal const val MAX_DECOMPRESSED_BYTES = 16 * 1024 * 1024
    internal const val MAX_COMPRESSION_DEPTH = 4
    internal const val MAX_DECODED_PACKETS = 4096
    internal const val MAX_COMPRESSION_RATIO = 64
    private const val MIN_DECOMPRESSED_BUDGET_BYTES = 256 * 1024

    data class Packet(
        val version: Int,
        val operation: Int,
        val sequence: Int = 1,
        val body: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Packet
            if (version != other.version) return false
            if (operation != other.operation) return false
            if (sequence != other.sequence) return false
            if (!body.contentEquals(other.body)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = version
            result = 31 * result + operation
            result = 31 * result + sequence
            result = 31 * result + body.contentHashCode()
            return result
        }
    }

    private class DecodeBudget(
        var remainingPackets: Int = MAX_DECODED_PACKETS
    )

    fun encode(packet: Packet): ByteArray {
        val totalLength = HEAD_LENGTH + packet.body.size
        require(totalLength <= MAX_INPUT_BYTES) { "Danmaku packet is too large" }
        val buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(totalLength)
        buffer.putShort(HEAD_LENGTH.toShort())
        buffer.putShort(packet.version.toShort())
        buffer.putInt(packet.operation)
        buffer.putInt(packet.sequence)
        if (packet.body.isNotEmpty()) buffer.put(packet.body)
        return buffer.array()
    }

    /**
     * 解码数据包 (Server -> Client)。所有嵌套解压共享同一个包数量预算。
     */
    suspend fun decode(data: ByteArray): List<Packet> = withContext(Dispatchers.Default) {
        if (data.size < HEAD_LENGTH || data.size > MAX_INPUT_BYTES) {
            return@withContext emptyList()
        }
        decodeInternal(data, depth = 0, budget = DecodeBudget())
    }

    private fun decodeInternal(
        data: ByteArray,
        depth: Int,
        budget: DecodeBudget
    ): List<Packet> {
        if (data.size < HEAD_LENGTH || data.size > MAX_DECOMPRESSED_BYTES) return emptyList()

        val packets = mutableListOf<Packet>()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        while (buffer.remaining() >= HEAD_LENGTH && budget.remainingPackets > 0) {
            val packetStart = buffer.position()
            val totalLength = buffer.int
            val availableFromPacketStart = buffer.capacity() - packetStart
            if (
                totalLength < HEAD_LENGTH ||
                totalLength > availableFromPacketStart ||
                totalLength > MAX_INPUT_BYTES
            ) {
                break
            }

            val headLength = buffer.short.toInt() and 0xFFFF
            val version = buffer.short.toInt() and 0xFFFF
            val operation = buffer.int
            val sequence = buffer.int
            if (headLength < HEAD_LENGTH || headLength > totalLength) break

            val extendedHeaderLength = headLength - HEAD_LENGTH
            if (extendedHeaderLength > buffer.remaining()) break
            if (extendedHeaderLength > 0) {
                buffer.position(buffer.position() + extendedHeaderLength)
            }

            val bodyLength = totalLength - headLength
            if (bodyLength < 0 || bodyLength > buffer.remaining()) break
            val body = ByteArray(bodyLength)
            buffer.get(body)
            budget.remainingPackets -= 1

            when (version) {
                PROTO_VER_JSON, PROTO_VER_HEARTBEAT -> {
                    packets.add(Packet(version, operation, sequence, body))
                }

                PROTO_VER_ZLIB, PROTO_VER_BROTLI -> {
                    if (depth >= MAX_COMPRESSION_DEPTH) {
                        Logger.w(
                            "DanmakuProtocol",
                            "Dropped compressed danmaku packet beyond depth=$MAX_COMPRESSION_DEPTH"
                        )
                        continue
                    }
                    try {
                        val decompressed = when (version) {
                            PROTO_VER_ZLIB -> decompressZlib(body)
                            else -> decompressBrotli(body)
                        }
                        packets.addAll(decodeInternal(decompressed, depth + 1, budget))
                    } catch (e: Exception) {
                        Logger.e(
                            "DanmakuProtocol",
                            "Failed to decompress danmaku packet version=$version",
                            e
                        )
                    }
                }

                else -> packets.add(Packet(version, operation, sequence, body))
            }
        }

        return packets
    }

    private fun decompressZlib(data: ByteArray): ByteArray {
        if (data.isEmpty()) throw IOException("Empty zlib danmaku payload")
        return InflaterInputStream(ByteArrayInputStream(data)).use { input ->
            readBoundedDecompressed(input, data.size)
        }
    }

    private fun decompressBrotli(data: ByteArray): ByteArray {
        if (data.isEmpty()) throw IOException("Empty brotli danmaku payload")
        return BrotliInputStream(ByteArrayInputStream(data)).use { input ->
            readBoundedDecompressed(input, data.size)
        }
    }

    private fun readBoundedDecompressed(input: InputStream, compressedSize: Int): ByteArray {
        val ratioBudget = compressedSize.toLong()
            .times(MAX_COMPRESSION_RATIO.toLong())
            .coerceAtLeast(MIN_DECOMPRESSED_BUDGET_BYTES.toLong())
            .coerceAtMost(MAX_DECOMPRESSED_BYTES.toLong())
        val initialCapacity = minOf(ratioBudget, 64L * 1024L).toInt().coerceAtLeast(1024)
        val output = ByteArrayOutputStream(initialCapacity)
        val chunk = ByteArray(8192)
        var total = 0L

        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            if (read == 0) continue
            total += read.toLong()
            if (total > ratioBudget || total > MAX_DECOMPRESSED_BYTES) {
                throw IOException(
                    "Danmaku decompressed payload exceeded budget: compressed=$compressedSize, output=$total"
                )
            }
            output.write(chunk, 0, read)
        }
        return output.toByteArray()
    }
}
