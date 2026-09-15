package com.bbttvv.app.feature.video.danmaku

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

/** Binary framing kept independent of Android so real webmask files can be tested on the JVM. */
internal class WebMaskArchive private constructor(
    private val bytes: ByteArray,
    val segments: List<Segment>,
) {
    data class Segment(val startMs: Long, val offset: Int, val endOffset: Int)
    data class Frame(val timeMs: Long, val dataUri: String)

    fun segmentIndexAt(positionMs: Long): Int = segments.indexOfLast { it.startMs <= positionMs }

    fun readFrames(index: Int): List<Frame> {
        val segment = segments[index]
        val frames = ArrayList<Frame>()
        DataInputStream(
            GZIPInputStream(ByteArrayInputStream(bytes, segment.offset, segment.endOffset - segment.offset)),
        ).use { input ->
            var totalBytes = 0L
            while (true) {
                val firstByte = input.read()
                if (firstByte == -1) break
                val length = (firstByte shl 24) or (input.readUnsignedByte() shl 16) or
                    (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
                require(length in 1..2 * 1024 * 1024)
                totalBytes += length + 12L
                require(totalBytes <= 8 * 1024 * 1024 && frames.size < 2_000)
                val timestamp = input.readLong()
                require(timestamp >= segment.startMs && (frames.lastOrNull()?.timeMs ?: 0) <= timestamp)
                val raw = ByteArray(length)
                input.readFully(raw)
                val dataUri = raw.toString(Charsets.UTF_8)
                require(dataUri.startsWith("data:image/svg+xml;base64,"))
                frames += Frame(timestamp, dataUri)
            }
        }
        return frames
    }

    companion object {
        fun parse(bytes: ByteArray): WebMaskArchive {
            return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == 0x4D41534B)
                require(input.readInt() == 1)
                input.readInt()
                val count = input.readInt()
                require(count in 1..10_000)
                val headerSize = 16L + count * 16L
                require(headerSize < bytes.size)
                val times = LongArray(count)
                val offsets = LongArray(count)
                repeat(count) {
                    times[it] = input.readLong()
                    offsets[it] = input.readLong()
                }
                val segments = List(count) { index ->
                    val start = offsets[index]
                    val end = offsets.getOrElse(index + 1) { bytes.size.toLong() }
                    require(start >= headerSize && start < end && end <= bytes.size)
                    require(times[index] >= 0 && (index == 0 || times[index] > times[index - 1]))
                    Segment(times[index], start.toInt(), end.toInt())
                }
                WebMaskArchive(bytes, segments)
            }
        }
    }
}
