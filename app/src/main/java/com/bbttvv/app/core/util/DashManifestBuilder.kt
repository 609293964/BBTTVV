package com.bbttvv.app.core.util

import com.bbttvv.app.data.model.response.Dash
import com.bbttvv.app.data.model.response.DashAudio
import com.bbttvv.app.data.model.response.DashVideo
import com.bbttvv.app.data.model.response.SegmentBase

object DashManifestBuilder {
    fun build(
        dash: Dash,
        selectedVideo: DashVideo,
        selectedAudio: DashAudio?,
        fallbackDurationMs: Long
    ): String {
        val durationSeconds = dash.duration.takeIf { it > 0 }?.toDouble()
            ?: (fallbackDurationMs.coerceAtLeast(0L) / 1000.0)
        val minBufferSeconds = dash.minBufferTime.takeIf { it > 0f } ?: 1.5f
        return buildManifest(
            selectedVideo = selectedVideo,
            selectedAudio = selectedAudio,
            durationSeconds = durationSeconds,
            minBufferSeconds = minBufferSeconds.toDouble()
        )
    }

    fun buildFromTracks(
        selectedVideo: DashVideo,
        selectedAudio: DashAudio?,
        durationMs: Long
    ): String {
        val durationSeconds = durationMs.coerceAtLeast(0L) / 1000.0
        return buildManifest(
            selectedVideo = selectedVideo,
            selectedAudio = selectedAudio,
            durationSeconds = durationSeconds,
            minBufferSeconds = 1.5
        )
    }

    private fun buildManifest(
        selectedVideo: DashVideo,
        selectedAudio: DashAudio?,
        durationSeconds: Double,
        minBufferSeconds: Double
    ): String {
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011"""")
            appendLine("""     profiles="urn:mpeg:dash:profile:isoff-on-demand:2011"""")
            appendLine("""     minBufferTime="PT${formatDecimal(minBufferSeconds)}S"""")
            appendLine("""     type="static"""")
            appendLine("""     mediaPresentationDuration="PT${formatDecimal(durationSeconds)}S">""")
            appendLine("""  <Period>""")
            appendVideoAdaptationSet(selectedVideo)
            selectedAudio?.let { appendAudioAdaptationSet(it) }
            appendLine("""  </Period>""")
            appendLine("""</MPD>""")
        }
    }

    private fun StringBuilder.appendVideoAdaptationSet(video: DashVideo) {
        appendLine(
            """    <AdaptationSet mimeType="${video.mimeType.ifBlank { "video/mp4" }}" contentType="video" subsegmentAlignment="true" subsegmentStartsWithSAP="1">"""
        )
        appendRepresentation(video = video)
        appendLine("""    </AdaptationSet>""")
    }

    private fun StringBuilder.appendAudioAdaptationSet(audio: DashAudio) {
        appendLine(
            """    <AdaptationSet mimeType="${audio.mimeType.ifBlank { "audio/mp4" }}" contentType="audio" subsegmentAlignment="true" subsegmentStartsWithSAP="1">"""
        )
        appendAudioChannelConfiguration(audio)
        appendRepresentation(audio = audio)
        appendLine("""    </AdaptationSet>""")
    }

    private fun StringBuilder.appendAudioChannelConfiguration(audio: DashAudio) {
        val channelCount = resolveAudioChannelCount(audio)
        appendLine(
            """      <AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="$channelCount"/>"""
        )
    }

    private fun resolveAudioChannelCount(audio: DashAudio): Int {
        val codecs = audio.codecs.lowercase()
        return when {
            codecs.startsWith("ec-3") || codecs.startsWith("eac-3") -> 6
            codecs.startsWith("ac-3") -> 6
            codecs.contains("atmos") -> 8
            codecs.startsWith("flac") -> 2
            else -> 2
        }
    }

    private fun StringBuilder.appendRepresentation(
        video: DashVideo? = null,
        audio: DashAudio? = null
    ) {
        val streamId = video?.id ?: audio?.id ?: 0
        val codecs = video?.codecs ?: audio?.codecs ?: ""
        val bandwidth = video?.bandwidth ?: audio?.bandwidth ?: 0
        val width = video?.width
        val height = video?.height
        val frameRate = video?.frameRate.orEmpty()
        val urls = buildList {
            video?.baseUrl?.takeIf { it.isNotBlank() }?.let(::add)
            audio?.baseUrl?.takeIf { it.isNotBlank() }?.let(::add)
            video?.backupUrl?.filterTo(this) { it.isNotBlank() }
            audio?.backupUrl?.filterTo(this) { it.isNotBlank() }
        }.map(::preferHttpsUrl).distinct()
        val segmentBase = video?.segmentBase ?: audio?.segmentBase

        append("""      <Representation id="$streamId" codecs="${escapeXml(codecs)}" bandwidth="$bandwidth"""")
        if (video != null) {
            append(""" width="${width ?: 0}" height="${height ?: 0}"""")
            if (frameRate.isNotBlank()) {
                append(""" frameRate="${escapeXml(frameRate)}"""")
            }
        }
        appendLine(""">""")

        urls.forEach { url ->
            appendLine("""        <BaseURL>${escapeXml(url)}</BaseURL>""")
        }

        segmentBase?.let { appendSegmentBase(it) }
        appendLine("""      </Representation>""")
    }

    private fun StringBuilder.appendSegmentBase(segmentBase: SegmentBase) {
        val indexRange = segmentBase.indexRange?.takeIf { it.isNotBlank() } ?: return
        appendLine("""        <SegmentBase indexRange="${escapeXml(indexRange)}">""")
        segmentBase.initialization
            ?.takeIf { it.isNotBlank() }
            ?.let { initialization ->
                appendLine("""          <Initialization range="${escapeXml(initialization)}"/>""")
            }
        appendLine("""        </SegmentBase>""")
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun formatDecimal(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
}
