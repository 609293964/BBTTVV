package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.ui.layout.ContentScale
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.Stat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortraitPagerSwitchPolicyTest {

    private fun related(
        bvid: String,
        aid: Long = 0L,
        title: String = bvid
    ): RelatedVideo {
        return RelatedVideo(
            aid = aid,
            bvid = bvid,
            title = title,
            owner = Owner(mid = 1L, name = "up")
        )
    }

    @Test
    fun resolveCommittedPage_returnsNullWhileScrolling() {
        val target = resolveCommittedPage(
            isScrollInProgress = true,
            currentPage = 2,
            lastCommittedPage = 1
        )

        assertNull(target)
    }

    @Test
    fun resolveCommittedPage_returnsPageWhenSettledAndChanged() {
        val target = resolveCommittedPage(
            isScrollInProgress = false,
            currentPage = 2,
            lastCommittedPage = 1
        )

        assertEquals(2, target)
    }

    @Test
    fun resolveCommittedPage_returnsNullWhenSettledButUnchanged() {
        val target = resolveCommittedPage(
            isScrollInProgress = false,
            currentPage = 2,
            lastCommittedPage = 2
        )

        assertNull(target)
    }

    @Test
    fun shouldHandlePortraitSeekGesture_disablesSeekWhenZoomed() {
        assertFalse(
            shouldHandlePortraitSeekGesture(scale = 1.02f)
        )
        assertTrue(
            shouldHandlePortraitSeekGesture(scale = 1f)
        )
    }

    @Test
    fun shouldHandlePortraitTapGesture_disablesTapWhenZoomed() {
        assertFalse(
            shouldHandlePortraitTapGesture(scale = 1.1f)
        )
        assertTrue(
            shouldHandlePortraitTapGesture(scale = 1f)
        )
    }

    @Test
    fun shouldHandlePortraitLongPressGesture_disablesLongPressWhenZoomed() {
        assertFalse(
            shouldHandlePortraitLongPressGesture(scale = 1.08f)
        )
        assertTrue(
            shouldHandlePortraitLongPressGesture(scale = 1f)
        )
    }

    @Test
    fun shouldRestorePortraitLongPressSpeed_restoresWhenPageStopsBeingCurrent() {
        assertTrue(
            shouldRestorePortraitLongPressSpeed(
                isLongPressing = true,
                isCurrentPage = false
            )
        )
        assertFalse(
            shouldRestorePortraitLongPressSpeed(
                isLongPressing = false,
                isCurrentPage = false
            )
        )
        assertFalse(
            shouldRestorePortraitLongPressSpeed(
                isLongPressing = true,
                isCurrentPage = true
            )
        )
    }

    @Test
    fun shouldApplyLoadResult_acceptsOnlyLatestGenerationForSameVideo() {
        assertTrue(
            shouldApplyLoadResult(
                requestGeneration = 5,
                activeGeneration = 5,
                expectedBvid = "BV1xx411c7mD",
                currentPlayingBvid = "BV1xx411c7mD"
            )
        )

        assertFalse(
            shouldApplyLoadResult(
                requestGeneration = 4,
                activeGeneration = 5,
                expectedBvid = "BV1xx411c7mD",
                currentPlayingBvid = "BV1xx411c7mD"
            )
        )

        assertFalse(
            shouldApplyLoadResult(
                requestGeneration = 5,
                activeGeneration = 5,
                expectedBvid = "BV17x411w7KC",
                currentPlayingBvid = "BV1xx411c7mD"
            )
        )
    }

    @Test
    fun shouldSkipPortraitReloadForCurrentMedia_onlyWhenBvidAndMediaIdAlreadyMatch() {
        assertTrue(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV1xx411c7mD",
                currentPlayerMediaId = "BV1xx411c7mD"
            )
        )
        assertFalse(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV17x411w7KC",
                currentPlayerMediaId = "BV1xx411c7mD"
            )
        )
        assertFalse(
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = "BV1xx411c7mD",
                targetBvid = "BV1xx411c7mD",
                currentPlayerMediaId = " "
            )
        )
    }

    @Test
    fun shouldShowPortraitCover_showOnlyWhenLoadingOrNotReady() {
        assertTrue(
            shouldShowPortraitCover(
                isLoading = true,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = false
            )
        )

        assertTrue(
            shouldShowPortraitCover(
                isLoading = false,
                isCurrentPage = false,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = false
            )
        )

        assertTrue(
            shouldShowPortraitCover(
                isLoading = false,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = false,
                hasRenderedFirstFrame = false
            )
        )

        assertTrue(
            shouldShowPortraitCover(
                isLoading = false,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = false
            )
        )

        assertFalse(
            shouldShowPortraitCover(
                isLoading = false,
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = true
            )
        )
    }

    @Test
    fun portraitCover_usesVideoViewportSizingBeforeFirstFrame() {
        assertTrue(
            shouldUseViewportBoundPortraitCover(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = false
            )
        )
        assertFalse(
            shouldUseViewportBoundPortraitCover(
                isCurrentPage = true,
                isPlayerReadyForThisVideo = true,
                hasRenderedFirstFrame = true
            )
        )
    }

    @Test
    fun portraitCover_prefersFitScaleToAvoidTemporaryCropJump() {
        assertEquals(
            ContentScale.Fit,
            resolvePortraitCoverContentScale()
        )
    }

    @Test
    fun shouldShowPortraitPauseIcon_hidesWhileBufferingOrLoading() {
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = false,
                playWhenReady = true,
                isLoading = false,
                isSeekGesture = false
            )
        )
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = false,
                playWhenReady = false,
                isLoading = true,
                isSeekGesture = false
            )
        )
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = false,
                playWhenReady = false,
                isLoading = false,
                isSeekGesture = true
            )
        )
    }

    @Test
    fun shouldShowPortraitPauseIcon_showsOnlyWhenActuallyPaused() {
        assertTrue(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = false,
                playWhenReady = false,
                isLoading = false,
                isSeekGesture = false
            )
        )
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = false,
                isPlaying = false,
                playWhenReady = false,
                isLoading = false,
                isSeekGesture = false
            )
        )
        assertFalse(
            shouldShowPortraitPauseIcon(
                isCurrentPage = true,
                isPlaying = true,
                playWhenReady = true,
                isLoading = false,
                isSeekGesture = false
            )
        )
    }

    @Test
    fun resolvePortraitInitialProgressPosition_usesEntryPositionOnlyForFirstPage() {
        assertEquals(
            12000L,
            resolvePortraitInitialProgressPosition(
                isFirstPage = true,
                initialStartPositionMs = 12000L
            )
        )
        assertEquals(
            0L,
            resolvePortraitInitialProgressPosition(
                isFirstPage = false,
                initialStartPositionMs = 12000L
            )
        )
    }

    @Test
    fun shouldLoadMorePortraitRecommendations_onlyWhenNearTailAndNotAlreadyLoading() {
        assertTrue(
            shouldLoadMorePortraitRecommendations(
                committedPage = 3,
                totalItemsCount = 5,
                isLoadingMoreRecommendations = false
            )
        )
        assertFalse(
            shouldLoadMorePortraitRecommendations(
                committedPage = 1,
                totalItemsCount = 5,
                isLoadingMoreRecommendations = false
            )
        )
        assertFalse(
            shouldLoadMorePortraitRecommendations(
                committedPage = 3,
                totalItemsCount = 5,
                isLoadingMoreRecommendations = true
            )
        )
        assertFalse(
            shouldLoadMorePortraitRecommendations(
                committedPage = -1,
                totalItemsCount = 5,
                isLoadingMoreRecommendations = false
            )
        )
    }

    @Test
    fun mergePortraitRecommendationAppendItems_filtersDuplicatesAndCurrentVideo() {
        val appendItems = mergePortraitRecommendationAppendItems(
            currentBvid = "BV_CURRENT",
            existingBvids = setOf("BV_CURRENT", "BV_A", "BV_B"),
            fetchedRecommendations = listOf(
                related("BV_CURRENT", aid = 1L),
                related("BV_B", aid = 2L),
                related("BV_C", aid = 3L),
                related("BV_D", aid = 4L),
                related("BV_C", aid = 5L)
            )
        )

        assertEquals(listOf("BV_C", "BV_D"), appendItems.map { it.bvid })
    }

    @Test
    fun toViewInfoForPortraitDetail_mapsCoreFieldsFromRelatedVideo() {
        val related = RelatedVideo(
            aid = 1001L,
            bvid = "BV1xK4y1d7Q1",
            title = "sample",
            pic = "https://example.com/cover.jpg",
            owner = Owner(mid = 123L, name = "up"),
            stat = Stat(view = 999, like = 77),
            duration = 65
        )

        val info = toViewInfoForPortraitDetail(related)

        assertEquals(related.bvid, info.bvid)
        assertEquals(related.aid, info.aid)
        assertEquals(related.title, info.title)
        assertEquals(related.pic, info.pic)
        assertEquals(related.owner.mid, info.owner.mid)
        assertEquals(related.stat.view, info.stat.view)
        assertEquals(65L, info.pages.firstOrNull()?.duration)
    }
}
