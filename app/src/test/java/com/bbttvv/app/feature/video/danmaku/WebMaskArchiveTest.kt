package com.bbttvv.app.feature.video.danmaku

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebMaskArchiveTest {
    private val svg = """<svg viewBox="0 0 320 180"><path d="M0 0H320V180H0Z"/></svg>"""
    private val uri = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray())

    @Test
    fun `reads length timestamp and SVG for every frame including last frame`() {
        val archive = WebMaskArchive.parse(file(segment(0, 33, 66), segment(10_000, 10_033)))
        val frames = archive.readFrames(0)
        assertEquals(listOf(0L, 33L, 66L), frames.map { it.timeMs })
        assertEquals(listOf(uri, uri, uri), frames.map { it.dataUri })
        assertEquals(listOf(10_000L, 10_033L), archive.readFrames(1).map { it.timeMs })
    }

    @Test
    fun `seek selects segment by start timestamp including exact boundary and backward seek`() {
        val archive = WebMaskArchive.parse(file(segment(0), segment(10_000)))
        assertEquals(-1, archive.segmentIndexAt(-1))
        assertEquals(0, archive.segmentIndexAt(9_999))
        assertEquals(1, archive.segmentIndexAt(10_000))
        assertEquals(0, archive.segmentIndexAt(0))
    }

    @Test
    fun `rejects truncated header and offsets outside file instead of empty success`() {
        assertThrows(Exception::class.java) { WebMaskArchive.parse(byteArrayOf(0x4d, 0x41)) }
        val bytes = file(segment(0))
        bytes[24] = 0x7f
        assertThrows(IllegalArgumentException::class.java) { WebMaskArchive.parse(bytes) }
    }

    @Test
    fun `rejects truncated gzip instead of keeping a stale frame`() {
        val bytes = file(segment(0, 33))
        val archive = WebMaskArchive.parse(bytes.copyOf(bytes.size - 5))
        assertThrows(Exception::class.java) { archive.readFrames(0) }
    }

    private fun segment(vararg timestamps: Long): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(GZIPOutputStream(buffer)).use { output ->
            timestamps.forEach {
                val encoded = uri.toByteArray()
                output.writeInt(encoded.size)
                output.writeLong(it)
                output.write(encoded)
            }
        }
        return buffer.toByteArray()
    }

    private fun file(vararg segments: ByteArray): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(0x4D41534B)
            output.writeInt(1)
            output.writeInt(0x02000000)
            output.writeInt(segments.size)
            var offset = 16L + segments.size * 16L
            segments.forEachIndexed { index, bytes ->
                output.writeLong(index * 10_000L)
                output.writeLong(offset)
                offset += bytes.size
            }
            segments.forEach { output.write(it) }
        }
        return buffer.toByteArray()
    }
}
