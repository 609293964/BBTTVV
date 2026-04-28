package com.bbttvv.app.feature.video.usecase

import com.bbttvv.app.core.store.SettingsManager
import com.bbttvv.app.data.model.VideoQuality
import com.bbttvv.app.data.model.response.Dash
import com.bbttvv.app.data.model.response.DashAudio
import com.bbttvv.app.data.model.response.DashVideo
import com.bbttvv.app.data.model.response.Page
import com.bbttvv.app.data.model.response.PlayUrlData
import com.bbttvv.app.data.model.response.RelatedVideo
import com.bbttvv.app.data.model.response.ViewInfo
import com.bbttvv.app.data.model.response.getBestAudio
import com.bbttvv.app.data.model.response.getBestVideo
import com.bbttvv.app.data.repository.PlaybackRepository
import com.bbttvv.app.data.repository.SubtitleAndAuxRepository
import com.bbttvv.app.data.repository.resolveAutoResumePositionMs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class PlaybackQualityInfo(
    val id: Int,
    val label: String,
    val isSupported: Boolean,
    val unsupportedReason: String? = null
)

data class PlaybackSource(
    val videoUrl: String,
    val audioUrl: String?,
    val segmentUrls: List<String>,
    val videoUrlCandidates: List<String> = emptyList(),
    val audioUrlCandidates: List<String> = emptyList(),
    val segmentUrlCandidates: List<List<String>> = emptyList(),
    val actualQuality: Int,
    val qualityOptions: List<PlaybackQualityInfo>,
    val dashVideos: List<DashVideo>,
    val dashAudios: List<DashAudio>,
    val durationMs: Long,
    val selectedVideoCodec: String,
    val selectedAudioCodec: String,
    val selectedVideoCodecId: Int,
    val selectedAudioQualityId: Int,
    val selectedAudioCodecId: Int,
    val selectedVideoWidth: Int,
    val selectedVideoHeight: Int,
    val selectedVideoFrameRate: String,
    val selectedVideoBandwidth: Int,
    val selectedAudioBandwidth: Int,
    val activeDynamicRangeLabel: String?,
    val hasHdrTrack: Boolean,
    val hasDolbyVisionTrack: Boolean,
    val hasDolbyAudioTrack: Boolean,
    val capabilityHints: List<String>,
    val resumePositionMs: Long = 0L
)

data class ResumePlaybackCandidate(
    val cid: Long,
    val positionMs: Long,
)

sealed class PlaybackLoadResult {
    data class Success(
        val info: ViewInfo,
        val pages: List<Page>,
        val currentPageIndex: Int,
        val relatedVideos: List<RelatedVideo>,
        val source: PlaybackSource,
        val resumeCandidate: ResumePlaybackCandidate? = null,
    ) : PlaybackLoadResult()

    data class Error(val message: String) : PlaybackLoadResult()
}

class VideoPlaybackUseCase {
    private companion object {
        const val TV_SAFE_PRIMARY_CODEC = "avc1"
        const val TV_SAFE_SECONDARY_CODEC = "hev1"
    }

    suspend fun loadOnlineCountText(
        bvid: String,
        cid: Long
    ): String? {
        return SubtitleAndAuxRepository.getOnlineCountText(bvid = bvid, cid = cid)
    }

    suspend fun loadVideo(
        bvid: String,
        aid: Long = 0L,
        cid: Long = 0L,
        preferredQuality: Int,
        cdnPreference: SettingsManager.PlayerCdnPreference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
        isHevcSupported: Boolean = true,
        isAv1Supported: Boolean = false,
        isHdrSupported: Boolean = true,
        isDolbyVisionSupported: Boolean = true
    ): PlaybackLoadResult = coroutineScope {
        val detailDeferred = async {
            PlaybackRepository.getVideoDetails(
                bvid = bvid,
                aid = aid,
                requestedCid = cid,
                targetQuality = preferredQuality
            )
        }

        detailDeferred.await().fold(
            onSuccess = { (info, playData) ->
                val pages = info.pages.ifEmpty {
                    listOf(Page(cid = info.cid, page = 1, part = info.title))
                }
                val resumeCandidate = resolveResumePlaybackCandidate(
                    bvid = info.bvid,
                    requestedCid = cid,
                    currentCid = info.cid,
                    availablePages = pages,
                    playUrlData = playData,
                    preferredQuality = preferredQuality,
                )
                val source = resolvePlaybackSource(
                    playUrlData = playData,
                    currentCid = info.cid,
                    preferredQuality = preferredQuality,
                    cdnPreference = cdnPreference,
                    isHevcSupported = isHevcSupported,
                    isAv1Supported = isAv1Supported,
                    isHdrSupported = isHdrSupported,
                    isDolbyVisionSupported = isDolbyVisionSupported
                ) ?: return@fold PlaybackLoadResult.Error("No playable stream found.")
                val currentPageIndex = pages.indexOfFirst { it.cid == info.cid }.takeIf { it >= 0 } ?: 0
                PlaybackLoadResult.Success(
                    info = info,
                    pages = pages,
                    currentPageIndex = currentPageIndex,
                    relatedVideos = emptyList(),
                    source = source,
                    resumeCandidate = resumeCandidate,
                )
            },
            onFailure = { error ->
                PlaybackLoadResult.Error(error.message ?: "Load playback failed.")
            }
        )
    }

    suspend fun changeQuality(
        bvid: String,
        cid: Long,
        qualityId: Int,
        cdnPreference: SettingsManager.PlayerCdnPreference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
        isHevcSupported: Boolean = true,
        isAv1Supported: Boolean = false,
        isHdrSupported: Boolean = true,
        isDolbyVisionSupported: Boolean = true
    ): PlaybackSource? {
        val playUrlData = PlaybackRepository.getPlayUrlData(
            bvid = bvid,
            cid = cid,
            qn = qualityId
        ) ?: return null
        return resolvePlaybackSource(
            playUrlData = playUrlData,
            currentCid = cid,
            preferredQuality = qualityId,
            cdnPreference = cdnPreference,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported,
            isHdrSupported = isHdrSupported,
            isDolbyVisionSupported = isDolbyVisionSupported
        )
    }

    private suspend fun resolveResumePlaybackCandidate(
        bvid: String,
        requestedCid: Long,
        currentCid: Long,
        availablePages: List<Page>,
        playUrlData: PlayUrlData,
        preferredQuality: Int,
    ): ResumePlaybackCandidate? {
        val samePageResumePositionMs = resolveAutoResumePositionMs(
            currentCid = currentCid,
            playUrlData = playUrlData,
        )
        if (samePageResumePositionMs > 0L) {
            return ResumePlaybackCandidate(
                cid = currentCid,
                positionMs = samePageResumePositionMs,
            )
        }
        if (requestedCid != 0L) return null
        val resumeCid = playUrlData.lastPlayCid
            ?.takeIf { candidateCid ->
                candidateCid > 0L &&
                    candidateCid != currentCid &&
                    availablePages.any { page -> page.cid == candidateCid }
            }
            ?: return null
        val resumePlayUrlData = PlaybackRepository.getPlayUrlData(
            bvid = bvid,
            cid = resumeCid,
            qn = preferredQuality,
        ) ?: return null
        val resumePositionMs = resolveAutoResumePositionMs(
            currentCid = resumeCid,
            playUrlData = resumePlayUrlData,
        )
        if (resumePositionMs <= 0L) return null
        return ResumePlaybackCandidate(
            cid = resumeCid,
            positionMs = resumePositionMs,
        )
    }

    fun resolvePlaybackSource(
        playUrlData: PlayUrlData,
        currentCid: Long,
        preferredQuality: Int,
        cdnPreference: SettingsManager.PlayerCdnPreference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
        isHevcSupported: Boolean = true,
        isAv1Supported: Boolean = false,
        isHdrSupported: Boolean = true,
        isDolbyVisionSupported: Boolean = true
    ): PlaybackSource? {
        val resumePositionMs = resolveAutoResumePositionMs(
            currentCid = currentCid,
            playUrlData = playUrlData
        )
        val dashVideo = playUrlData.dash?.getBestVideo(
            targetQn = preferredQuality,
            preferCodec = TV_SAFE_PRIMARY_CODEC,
            secondPreferCodec = TV_SAFE_SECONDARY_CODEC,
            isHevcSupported = isHevcSupported,
            isAv1Supported = isAv1Supported
        )
        val dashAudio = playUrlData.dash?.getBestAudio()
        val allDashAudios = collectDashAudios(playUrlData.dash)
        val segmentUrlCandidates = playUrlData.durl
            ?.map { segment ->
                PlaybackUrlCandidates.orderedPlaybackUrls(
                    preference = cdnPreference,
                    primaryUrl = segment.url,
                    backupUrls = segment.backupUrl.orEmpty()
                )
            }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val segmentUrls = segmentUrlCandidates.map { it.first() }

        val dashVideoUrlCandidates = dashVideo?.getPreferredUrls(cdnPreference).orEmpty()
        val dashAudioUrlCandidates = dashAudio?.getPreferredUrls(cdnPreference).orEmpty()
        val dashVideoUrl = dashVideoUrlCandidates.firstOrNull().orEmpty()
        val qualityIds = buildQualityIds(
            acceptQualityIds = playUrlData.accept_quality,
            dashQualityIds = playUrlData.dash?.video?.map { it.id }.orEmpty(),
            selectedQuality = dashVideo?.id ?: playUrlData.quality
        )
        val qualityOptions = qualityIds.map { qualityId ->
            buildQualityOption(
                qualityId = qualityId,
                isHdrSupported = isHdrSupported,
                isDolbyVisionSupported = isDolbyVisionSupported
            )
        }
        val hasHdrTrack = qualityIds.contains(VideoQuality.HDR.code)
        val hasDolbyVisionTrack = qualityIds.contains(VideoQuality.DOLBY_VISION.code)
        val hasDolbyAudioTrack = playUrlData.dash?.dolby?.audio?.isNotEmpty() == true ||
            playUrlData.dolbyType == 1
        val capabilityHints = buildCapabilityHints(
            hasHdrTrack = hasHdrTrack,
            hasDolbyVisionTrack = hasDolbyVisionTrack,
            hasDolbyAudioTrack = hasDolbyAudioTrack,
            isHdrSupported = isHdrSupported,
            isDolbyVisionSupported = isDolbyVisionSupported
        )
        return when {
            dashVideoUrl.isNotBlank() -> PlaybackSource(
                videoUrl = dashVideoUrl,
                audioUrl = dashAudioUrlCandidates.firstOrNull(),
                segmentUrls = emptyList(),
                videoUrlCandidates = dashVideoUrlCandidates,
                audioUrlCandidates = dashAudioUrlCandidates,
                actualQuality = dashVideo?.id ?: playUrlData.quality,
                qualityOptions = qualityOptions,
                dashVideos = playUrlData.dash?.video.orEmpty(),
                dashAudios = allDashAudios,
                durationMs = playUrlData.timelength,
                selectedVideoCodec = resolveCodecLabel(dashVideo?.codecs, dashVideo?.codecid),
                selectedAudioCodec = resolveCodecLabel(dashAudio?.codecs, dashAudio?.codecid),
                selectedVideoCodecId = dashVideo?.videoCodecSelectionKey() ?: playUrlData.videoCodecid,
                selectedAudioQualityId = dashAudio?.id ?: 0,
                selectedAudioCodecId = dashAudio?.codecid ?: 0,
                selectedVideoWidth = dashVideo?.width ?: 0,
                selectedVideoHeight = dashVideo?.height ?: 0,
                selectedVideoFrameRate = dashVideo?.frameRate.orEmpty(),
                selectedVideoBandwidth = dashVideo?.bandwidth ?: 0,
                selectedAudioBandwidth = dashAudio?.bandwidth ?: 0,
                activeDynamicRangeLabel = resolveDynamicRangeLabel(dashVideo?.id ?: playUrlData.quality),
                hasHdrTrack = hasHdrTrack,
                hasDolbyVisionTrack = hasDolbyVisionTrack,
                hasDolbyAudioTrack = hasDolbyAudioTrack,
                capabilityHints = capabilityHints,
                resumePositionMs = resumePositionMs
            )

            segmentUrls.isNotEmpty() -> PlaybackSource(
                videoUrl = segmentUrls.first(),
                audioUrl = null,
                segmentUrls = segmentUrls,
                segmentUrlCandidates = segmentUrlCandidates,
                actualQuality = playUrlData.quality,
                qualityOptions = qualityOptions,
                dashVideos = emptyList(),
                dashAudios = emptyList(),
                durationMs = playUrlData.timelength,
                selectedVideoCodec = resolveCodecLabel(null, playUrlData.videoCodecid),
                selectedAudioCodec = "",
                selectedVideoCodecId = playUrlData.videoCodecid,
                selectedAudioQualityId = 0,
                selectedAudioCodecId = 0,
                selectedVideoWidth = 0,
                selectedVideoHeight = 0,
                selectedVideoFrameRate = "",
                selectedVideoBandwidth = 0,
                selectedAudioBandwidth = 0,
                activeDynamicRangeLabel = resolveDynamicRangeLabel(playUrlData.quality),
                hasHdrTrack = hasHdrTrack,
                hasDolbyVisionTrack = hasDolbyVisionTrack,
                hasDolbyAudioTrack = hasDolbyAudioTrack,
                capabilityHints = capabilityHints,
                resumePositionMs = resumePositionMs
            )

            else -> null
        }
    }

    fun selectDashTracks(
        source: PlaybackSource,
        qualityId: Int = source.actualQuality,
        videoCodecId: Int = source.selectedVideoCodecId,
        audioQualityId: Int = source.selectedAudioQualityId,
        cdnPreference: SettingsManager.PlayerCdnPreference = SettingsManager.PlayerCdnPreference.BILIVIDEO,
        isHevcSupported: Boolean = true,
        isAv1Supported: Boolean = false
    ): PlaybackSource? {
        if (source.segmentUrls.isNotEmpty() || source.dashVideos.isEmpty()) return null

        val validVideos = source.dashVideos.filter { it.getPreferredUrls(cdnPreference).isNotEmpty() }
        if (validVideos.isEmpty()) return null

        val qualityVideos = validVideos
            .filter { it.id == qualityId }
            .ifEmpty { validVideos.filter { it.id == source.actualQuality } }
            .ifEmpty { validVideos }

        val supportedVideos = qualityVideos
            .filter { it.isSupportedByDevice(isHevcSupported = isHevcSupported, isAv1Supported = isAv1Supported) }
            .ifEmpty { qualityVideos.filter { it.codecs.startsWith("avc", ignoreCase = true) } }
            .ifEmpty { qualityVideos }

        val selectedVideo = supportedVideos.firstOrNull { it.videoCodecSelectionKey() == videoCodecId }
            ?: supportedVideos.firstOrNull { it.videoCodecSelectionKey() == source.selectedVideoCodecId }
            ?: supportedVideos.firstOrNull()
            ?: return null
        val selectedAudio = selectAudioTrack(source.dashAudios, audioQualityId, cdnPreference)
        val selectedVideoCandidates = selectedVideo.getPreferredUrls(cdnPreference)
        val selectedAudioCandidates = selectedAudio?.getPreferredUrls(cdnPreference).orEmpty()

        return source.copy(
            videoUrl = selectedVideoCandidates.firstOrNull().orEmpty(),
            audioUrl = selectedAudioCandidates.firstOrNull(),
            videoUrlCandidates = selectedVideoCandidates,
            audioUrlCandidates = selectedAudioCandidates,
            actualQuality = selectedVideo.id,
            selectedVideoCodec = resolveCodecLabel(selectedVideo.codecs, selectedVideo.codecid),
            selectedAudioCodec = resolveCodecLabel(selectedAudio?.codecs, selectedAudio?.codecid),
            selectedVideoCodecId = selectedVideo.videoCodecSelectionKey(),
            selectedAudioQualityId = selectedAudio?.id ?: 0,
            selectedAudioCodecId = selectedAudio?.codecid ?: 0,
            selectedVideoWidth = selectedVideo.width,
            selectedVideoHeight = selectedVideo.height,
            selectedVideoFrameRate = selectedVideo.frameRate,
            selectedVideoBandwidth = selectedVideo.bandwidth,
            selectedAudioBandwidth = selectedAudio?.bandwidth ?: 0,
            activeDynamicRangeLabel = resolveDynamicRangeLabel(selectedVideo.id)
        )
    }

    private fun collectDashAudios(dash: Dash?): List<DashAudio> {
        if (dash == null) return emptyList()
        return buildList {
            dash.audio.orEmpty().forEach(::add)
            dash.dolby?.audio.orEmpty().forEach(::add)
            dash.flac?.audio?.let(::add)
        }.distinctBy { audio -> audio.id }
    }

    private fun selectAudioTrack(
        audios: List<DashAudio>,
        preferredAudioQualityId: Int,
        cdnPreference: SettingsManager.PlayerCdnPreference
    ): DashAudio? {
        val validAudios = audios.filter { it.getPreferredUrls(cdnPreference).isNotEmpty() }
        if (validAudios.isEmpty()) return null
        if (preferredAudioQualityId > 0) {
            validAudios.firstOrNull { it.id == preferredAudioQualityId }?.let { return it }
        }
        return validAudios.maxByOrNull { it.bandwidth }
    }

    private fun DashVideo.getPreferredUrls(preference: SettingsManager.PlayerCdnPreference): List<String> {
        return PlaybackUrlCandidates.orderedPlaybackUrls(
            preference = preference,
            primaryUrl = baseUrl,
            backupUrls = backupUrl.orEmpty()
        )
    }

    private fun DashAudio.getPreferredUrls(preference: SettingsManager.PlayerCdnPreference): List<String> {
        return PlaybackUrlCandidates.orderedPlaybackUrls(
            preference = preference,
            primaryUrl = baseUrl,
            backupUrls = backupUrl.orEmpty()
        )
    }

    private fun DashVideo.videoCodecSelectionKey(): Int {
        return codecid ?: codecs.lowercase().hashCode()
    }

    private fun DashVideo.isSupportedByDevice(
        isHevcSupported: Boolean,
        isAv1Supported: Boolean
    ): Boolean {
        val codecText = codecs.lowercase()
        return when {
            codecText.startsWith("avc") -> true
            codecText.startsWith("hev") || codecText.startsWith("hvc") -> isHevcSupported
            codecText.startsWith("av01") -> isAv1Supported
            codecText.startsWith("dvh1") || codecText.startsWith("dvhe") -> isHevcSupported
            else -> true
        }
    }

    private fun buildQualityIds(
        acceptQualityIds: List<Int>,
        dashQualityIds: List<Int>,
        selectedQuality: Int
    ): List<Int> {
        return (acceptQualityIds + dashQualityIds + listOf(selectedQuality))
            .distinct()
            .filter { it > 0 }
            .sortedDescending()
    }

    private fun resolveQualityLabel(qualityId: Int): String {
        return VideoQuality.fromCode(qualityId)?.description ?: "${qualityId}P"
    }

    private fun buildQualityOption(
        qualityId: Int,
        isHdrSupported: Boolean,
        isDolbyVisionSupported: Boolean
    ): PlaybackQualityInfo {
        val label = resolveQualityLabel(qualityId)
        return when {
            qualityId == VideoQuality.DOLBY_VISION.code && !isDolbyVisionSupported -> {
                PlaybackQualityInfo(
                    id = qualityId,
                    label = "$label (设备不支持)",
                    isSupported = false,
                    unsupportedReason = "当前设备不支持杜比视界"
                )
            }

            qualityId == VideoQuality.HDR.code && !isHdrSupported -> {
                PlaybackQualityInfo(
                    id = qualityId,
                    label = "$label (设备不支持)",
                    isSupported = false,
                    unsupportedReason = "当前设备不支持 HDR"
                )
            }

            else -> PlaybackQualityInfo(
                id = qualityId,
                label = label,
                isSupported = true
            )
        }
    }

    private fun buildCapabilityHints(
        hasHdrTrack: Boolean,
        hasDolbyVisionTrack: Boolean,
        hasDolbyAudioTrack: Boolean,
        isHdrSupported: Boolean,
        isDolbyVisionSupported: Boolean
    ): List<String> {
        return buildList {
            if (hasDolbyVisionTrack && !isDolbyVisionSupported) {
                add("资源提供杜比视界，当前设备暂不支持显示/解码")
            }
            if (hasHdrTrack && !isHdrSupported) {
                add("资源提供 HDR 真彩，当前设备暂不支持显示")
            }
            if (hasDolbyAudioTrack) {
                add("资源提供杜比音频轨道")
            }
        }
    }

    private fun resolveDynamicRangeLabel(qualityId: Int): String? {
        return when (qualityId) {
            VideoQuality.DOLBY_VISION.code -> "杜比视界"
            VideoQuality.HDR.code -> "HDR 真彩"
            else -> null
        }
    }

    private fun resolveCodecLabel(codecs: String?, codecId: Int?): String {
        val codecText = codecs.orEmpty().lowercase()
        return when {
            codecText.startsWith("dvh1") || codecText.startsWith("dvhe") -> "Dolby Vision"
            codecText.startsWith("av01") -> "AV1"
            codecText.startsWith("hev1") || codecText.startsWith("hvc1") -> "HEVC"
            codecText.startsWith("avc1") -> "AVC"
            codecText.contains("ec-3") || codecText.contains("eac-3") -> "杜比音频"
            codecText.contains("flac") -> "FLAC"
            codecText.contains("mp4a") -> "AAC"
            codecId == 12 -> "HEVC"
            codecId == 13 -> "AV1"
            codecId == 7 -> "AVC"
            else -> codecs.orEmpty()
        }
    }
}
