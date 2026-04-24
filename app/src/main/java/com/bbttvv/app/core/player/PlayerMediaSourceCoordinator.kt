package com.bbttvv.app.core.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.bbttvv.app.core.network.NetworkModule
import com.bbttvv.app.core.network.resolveAppUserAgent
import com.bbttvv.app.core.util.Logger
import java.io.File

internal class PlayerMediaSourceCoordinator(
    private val playerProvider: () -> PlayerEngine?,
    private val tag: String,
) {
    private var currentDashManifestFile: File? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playDashVideo(
        videoUrl: String,
        audioUrl: String?,
        videoUrlCandidates: List<String> = emptyList(),
        audioUrlCandidates: List<String> = emptyList(),
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://www.bilibili.com",
        playWhenReady: Boolean = true,
    ) {
        val player = requireExoPlayer("playDashVideo") ?: return

        Logger.d(
            tag,
            "▶️ playDashVideo: referer=$referer, seekTo=${seekToMs}ms, reset=$resetPlayer, video=${videoUrl.take(50)}..."
        )

        player.volume = 1.0f

        val videoSource = buildProgressiveMediaSource(
            url = videoUrl,
            candidates = videoUrlCandidates,
            kind = PlaybackStreamKind.Video,
            referer = referer,
        )

        val finalSource = if (!audioUrl.isNullOrEmpty()) {
            val audioSource = buildProgressiveMediaSource(
                url = audioUrl,
                candidates = audioUrlCandidates,
                kind = PlaybackStreamKind.Audio,
                referer = referer,
            )
            MergingMediaSource(videoSource, audioSource)
        } else {
            videoSource
        }

        player.setMediaSource(finalSource, resetPlayer)
        player.prepare()
        if (seekToMs > 0L) {
            player.seekTo(seekToMs)
        }
        player.playWhenReady = playWhenReady
        Logger.d(tag, "✅ playDashVideo: Player prepared and started, playWhenReady=$playWhenReady")
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playDashManifestVideo(
        manifestContent: String,
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://www.bilibili.com",
        playWhenReady: Boolean = true,
    ): Boolean {
        val player = requireExoPlayer("playDashManifestVideo") ?: return false
        val manifestUri = writeDashManifestToCache(manifestContent) ?: return false
        val dataSourceFactory = buildDefaultDataSourceFactory(referer) ?: return false

        return runCatching {
            player.volume = 1.0f
            val mediaSource = DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(manifestUri))
            player.setMediaSource(mediaSource, resetPlayer)
            player.prepare()
            if (seekToMs > 0L) {
                player.seekTo(seekToMs)
            }
            player.playWhenReady = playWhenReady
            Logger.d(tag, "✅ playDashManifestVideo: uri=$manifestUri, seekTo=${seekToMs}ms")
            true
        }.getOrElse { error ->
            Logger.e(tag, "❌ playDashManifestVideo failed: ${error.message}")
            false
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playSegmentedVideo(
        segmentUrls: List<String>,
        segmentUrlCandidates: List<List<String>> = emptyList(),
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://www.bilibili.com",
        playWhenReady: Boolean = true,
    ) {
        val player = requireExoPlayer("playSegmentedVideo") ?: return
        val cleanUrls = segmentUrls.filter { it.isNotBlank() }
        if (cleanUrls.isEmpty()) return

        if (cleanUrls.size == 1) {
            playDashVideo(
                videoUrl = cleanUrls.first(),
                audioUrl = null,
                videoUrlCandidates = segmentUrlCandidates.firstOrNull().orEmpty(),
                seekToMs = seekToMs,
                resetPlayer = resetPlayer,
                referer = referer,
                playWhenReady = playWhenReady,
            )
            return
        }

        player.volume = 1.0f
        val concatenated = ConcatenatingMediaSource().apply {
            cleanUrls.forEachIndexed { index, url ->
                addMediaSource(
                    buildProgressiveMediaSource(
                        url = url,
                        candidates = segmentUrlCandidates.getOrNull(index).orEmpty(),
                        kind = PlaybackStreamKind.Segment,
                        referer = referer,
                    )
                )
            }
        }

        player.setMediaSource(concatenated, resetPlayer)
        player.prepare()
        if (seekToMs > 0L) {
            player.seekTo(seekToMs)
        }
        player.playWhenReady = playWhenReady
        Logger.d(tag, "✅ playSegmentedVideo: segmentCount=${cleanUrls.size}, seekTo=${seekToMs}ms")
    }

    fun playVideo(
        url: String,
        seekToMs: Long = 0L,
    ) {
        val player = requireExoPlayer("playVideo") ?: return
        Logger.d(tag, " playVideo: seekTo=${seekToMs}ms, url=${url.take(50)}...")

        player.volume = 1.0f
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (seekToMs > 0L) {
            player.seekTo(seekToMs)
        }
        player.playWhenReady = true
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun playStreamingUrl(
        url: String,
        seekToMs: Long = 0L,
        resetPlayer: Boolean = true,
        referer: String = "https://live.bilibili.com",
        playWhenReady: Boolean = true,
    ) {
        val player = requireExoPlayer("playStreamingUrl") ?: return
        if (url.isBlank()) return

        Logger.d(
            tag,
            "▶️ playStreamingUrl: referer=$referer, seekTo=${seekToMs}ms, reset=$resetPlayer, url=${url.take(80)}..."
        )

        player.volume = 1.0f
        val mediaItem = MediaItem.fromUri(url)
        val dataSourceFactory = buildDefaultDataSourceFactory(referer)

        if (dataSourceFactory != null) {
            val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(mediaItem)
            player.setMediaSource(mediaSource, resetPlayer)
        } else {
            player.setMediaItem(mediaItem, resetPlayer)
        }

        player.prepare()
        if (seekToMs > 0L) {
            player.seekTo(seekToMs)
        }
        player.playWhenReady = playWhenReady
    }

    fun clear() {
        currentDashManifestFile?.takeIf { it.exists() }?.delete()
        currentDashManifestFile = null
    }

    private fun requireExoPlayer(operation: String): ExoPlayer? {
        val engine = playerProvider()
        val player = engine?.asExoPlayerOrNull()
        if (player == null) {
            Logger.e(tag, "$operation: player engine is unavailable or is not backed by ExoPlayer")
        }
        return player
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun buildProgressiveMediaSource(
        url: String,
        candidates: List<String>,
        kind: PlaybackStreamKind,
        referer: String,
    ): MediaSource {
        val candidateUrls = normalizeCandidateUrls(primaryUrl = url, candidates = candidates)
        val candidateUris = candidateUrls.map(Uri::parse)
        val dataSourceFactory: DataSource.Factory =
            if (candidateUris.size > 1) {
                CdnFailoverDataSourceFactory(
                    upstreamFactory = buildBaseDataSourceFactory(referer),
                    state = CdnFailoverState(kind = kind, candidates = candidateUris),
                )
            } else {
                buildBaseDataSourceFactory(referer)
            }
        val mediaItem = MediaItem.fromUri(candidateUris.firstOrNull() ?: Uri.parse(url))
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        return ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
            .createMediaSource(mediaItem)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun buildBaseDataSourceFactory(referer: String): DataSource.Factory {
        return buildDefaultDataSourceFactory(referer) ?: buildOkHttpDataSourceFactory(referer)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun buildDefaultDataSourceFactory(referer: String): DefaultDataSource.Factory? {
        val context = NetworkModule.appContext ?: return null
        val headers = mapOf(
            "Referer" to referer,
            "User-Agent" to resolveAppUserAgent(context),
        )
        val httpFactory = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
            .setDefaultRequestProperties(headers)
        return DefaultDataSource.Factory(context, httpFactory)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun buildOkHttpDataSourceFactory(referer: String): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to referer,
                    "User-Agent" to resolveAppUserAgent(NetworkModule.appContext),
                )
            )
    }

    private fun normalizeCandidateUrls(primaryUrl: String, candidates: List<String>): List<String> {
        return buildList {
            primaryUrl.trim().takeIf { it.isNotBlank() }?.let(::add)
            candidates.map { it.trim() }.filter { it.isNotBlank() }.forEach(::add)
        }.distinct()
    }

    private fun writeDashManifestToCache(manifestContent: String): Uri? {
        val context = NetworkModule.appContext ?: return null
        return runCatching {
            currentDashManifestFile?.takeIf { it.exists() }?.delete()
            val manifestDir = File(context.cacheDir, "dash_manifests").apply { mkdirs() }
            val manifestFile = File(manifestDir, "active_${System.currentTimeMillis()}.mpd")
            manifestFile.writeText(manifestContent)
            currentDashManifestFile = manifestFile
            Uri.fromFile(manifestFile)
        }.getOrElse { error ->
            Logger.e(tag, "❌ writeDashManifestToCache failed: ${error.message}")
            null
        }
    }
}
