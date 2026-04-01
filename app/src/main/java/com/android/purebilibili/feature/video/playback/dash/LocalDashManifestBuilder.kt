package com.android.purebilibili.feature.video.playback.dash

import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.SegmentBase
import java.util.Locale

fun buildLocalDashManifest(
    durationMs: Long,
    minBufferTimeMs: Long,
    videoTracks: List<DashVideo>,
    audioTracks: List<DashAudio>
): String {
    return buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append('\n')
        append(
            """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" mediaPresentationDuration="${durationMs.toIsoDuration()}" minBufferTime="${minBufferTimeMs.toIsoDuration()}">
              <Period>
            """.trimIndent()
        )
        append('\n')
        if (videoTracks.isNotEmpty()) {
            append("""    <AdaptationSet contentType="video" mimeType="${videoTracks.first().mimeType.escapeXml()}">""")
            append('\n')
            videoTracks.forEach { track ->
                appendRepresentation(track)
            }
            append("    </AdaptationSet>\n")
        }
        if (audioTracks.isNotEmpty()) {
            append("""    <AdaptationSet contentType="audio" mimeType="${audioTracks.first().mimeType.escapeXml()}">""")
            append('\n')
            audioTracks.forEach { track ->
                appendRepresentation(track)
            }
            append("    </AdaptationSet>\n")
        }
        append("  </Period>\n")
        append("</MPD>\n")
    }
}

private fun StringBuilder.appendRepresentation(track: DashVideo) {
    append(
        """      <Representation id="${track.id}" bandwidth="${track.bandwidth}" codecs="${track.codecs.escapeXml()}" width="${track.width}" height="${track.height}" frameRate="${track.frameRate.escapeXml()}">"""
    )
    append('\n')
    append("        <BaseURL>${track.baseUrl.escapeXml()}</BaseURL>\n")
    appendSegmentBase(track.segmentBase)
    append("      </Representation>\n")
}

private fun StringBuilder.appendRepresentation(track: DashAudio) {
    append(
        """      <Representation id="${track.id}" bandwidth="${track.bandwidth}" codecs="${track.codecs.escapeXml()}">"""
    )
    append('\n')
    append("        <BaseURL>${track.baseUrl.escapeXml()}</BaseURL>\n")
    appendSegmentBase(track.segmentBase)
    append("      </Representation>\n")
}

private fun StringBuilder.appendSegmentBase(segmentBase: SegmentBase?) {
    if (segmentBase == null) return
    val indexRange = segmentBase.indexRange?.takeIf { it.isNotBlank() } ?: return
    append("""        <SegmentBase indexRange="${indexRange.escapeXml()}">""")
    append('\n')
    segmentBase.initialization?.takeIf { it.isNotBlank() }?.let { initialization ->
        append("""          <Initialization range="${initialization.escapeXml()}"/>""")
        append('\n')
    }
    append("        </SegmentBase>\n")
}

private fun Long.toIsoDuration(): String {
    val seconds = this.coerceAtLeast(0L) / 1000.0
    return String.format(Locale.US, "PT%.3fS", seconds)
}

private fun String.escapeXml(): String {
    return this
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
