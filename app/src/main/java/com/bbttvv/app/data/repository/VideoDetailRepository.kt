package com.bbttvv.app.data.repository

import com.bbttvv.app.core.coroutines.AppScope
import com.bbttvv.app.data.model.response.InteractEdgeInfoData
import com.bbttvv.app.data.model.response.RelatedVideo
import com.bbttvv.app.data.model.response.VideoDetailResponse
import com.bbttvv.app.data.model.response.VideoItem
import com.bbttvv.app.data.model.response.ViewInfo
import kotlinx.coroutines.CoroutineScope

object VideoDetailRepository {
    suspend fun getVideoTitle(bvid: String, aid: Long = 0L): Result<String> {
        return VideoRepository.getVideoTitle(bvid = bvid, aid = aid)
    }

    suspend fun getVideoInfo(bvid: String): VideoDetailResponse {
        return VideoRepository.getVideoInfo(bvid)
    }

    fun getCachedDetailViewInfo(bvid: String): ViewInfo? = VideoRepository.getCachedDetailViewInfo(bvid)

    suspend fun getRelatedVideos(bvid: String): List<RelatedVideo> = VideoRepository.getRelatedVideos(bvid)

    fun getCachedRelatedVideos(bvid: String): List<RelatedVideo>? = VideoRepository.getCachedRelatedVideos(bvid)

    fun cacheVideoPreview(video: VideoItem) {
        VideoRepository.cacheVideoPreview(video)
    }

    fun cacheVideoPreview(video: RelatedVideo) {
        VideoRepository.cacheVideoPreview(video)
    }

    fun prefetchRelatedVideos(bvid: String, scope: CoroutineScope = AppScope.ioScope) {
        VideoRepository.prefetchRelatedVideos(bvid = bvid, scope = scope)
    }

    fun prefetchVideoInfo(bvid: String, scope: CoroutineScope = AppScope.ioScope) {
        VideoRepository.prefetchVideoInfo(bvid = bvid, scope = scope)
    }

    fun prefetchDetailSummary(video: VideoItem, scope: CoroutineScope = AppScope.ioScope) {
        VideoRepository.prefetchDetailSummary(video = video, scope = scope)
    }

    fun prefetchDetailSummaries(videos: Iterable<VideoItem>, scope: CoroutineScope = AppScope.ioScope) {
        VideoRepository.prefetchDetailSummaries(videos = videos, scope = scope)
    }

    fun prefetchDetailLanding(video: VideoItem, scope: CoroutineScope = AppScope.ioScope) {
        VideoRepository.prefetchDetailLanding(video = video, scope = scope)
    }

    fun prefetchDetailLanding(video: RelatedVideo, scope: CoroutineScope = AppScope.ioScope) {
        VideoRepository.prefetchDetailLanding(video = video, scope = scope)
    }

    fun prefetchDetailLandings(videos: Iterable<VideoItem>, scope: CoroutineScope = AppScope.ioScope) {
        VideoRepository.prefetchDetailLandings(videos = videos, scope = scope)
    }

    suspend fun getInteractEdgeInfo(
        bvid: String,
        graphVersion: Long,
        edgeId: Long? = null
    ): Result<InteractEdgeInfoData> {
        return VideoRepository.getInteractEdgeInfo(bvid = bvid, graphVersion = graphVersion, edgeId = edgeId)
    }
}
