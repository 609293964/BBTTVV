package com.bbttvv.app.feature.video.viewmodel

internal data class AudioFallbackTrack(
    val id: Int,
    val codecs: String,
    val bandwidth: Int,
    val hasUrl: Boolean,
)

/** Only advertised AAC tracks are automatic recovery candidates; every id is attempted once. */
internal class AudioFallbackPolicy {
    private val attempted = mutableSetOf<Int>()

    fun reset() = attempted.clear()

    fun next(currentId: Int, tracks: List<AudioFallbackTrack>): Int? {
        attempted.add(currentId)
        val current = tracks.firstOrNull { it.id == currentId }
        val next = tracks.asSequence()
            .filter { it.id > 0 && it.id !in attempted && it.hasUrl }
            .filter { it.codecs.startsWith("mp4a", ignoreCase = true) }
            .filter { current == null || !current.codecs.startsWith("mp4a", true) || it.bandwidth < current.bandwidth }
            .sortedByDescending { it.bandwidth }
            .firstOrNull() ?: return null
        attempted.add(next.id)
        return next.id
    }
}
