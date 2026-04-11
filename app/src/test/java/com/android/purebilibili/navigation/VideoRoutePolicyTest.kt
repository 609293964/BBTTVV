package com.android.purebilibili.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoRoutePolicyTest {

    @Test
    fun resolveVideoRoutePath_includesStartAudioFlag() {
        val route = VideoRoute.resolveVideoRoutePath(
            bvid = "BV1abc",
            cid = 233L,
            encodedCover = "https%3A%2F%2Fimg",
            startAudio = true,
            autoPortrait = false,
            resumePositionMs = 0L
        )

        assertEquals(
            "video/BV1abc?cid=233&cover=https%3A%2F%2Fimg&startAudio=true&autoPortrait=false&resumePositionMs=0&commentRootRpid=0",
            route
        )
    }

    @Test
    fun resolveVideoRoutePath_defaultsToStartAudioFalseWhenDisabled() {
        val route = VideoRoute.resolveVideoRoutePath(
            bvid = "BV9xyz",
            cid = 0L,
            encodedCover = "",
            startAudio = false,
            autoPortrait = true,
            resumePositionMs = 0L
        )

        assertEquals(
            "video/BV9xyz?cid=0&cover=&startAudio=false&autoPortrait=true&resumePositionMs=0&commentRootRpid=0",
            route
        )
    }

    @Test
    fun standardVideoRoute_disablesAutoPortraitByDefault() {
        assertEquals(
            "video/BV1std?cid=77&cover=https%3A%2F%2Fimg.test%2Fcover.jpg&startAudio=false&autoPortrait=false&resumePositionMs=0&commentRootRpid=0",
            resolveStandardVideoRoute(
                bvid = "BV1std",
                cid = 77L,
                coverUrl = "https://img.test/cover.jpg"
            )
        )
    }

    @Test
    fun standardVideoRoute_keepsAudioModeWithoutAutoPortrait() {
        assertEquals(
            "video/BV1audio?cid=9&cover=&startAudio=true&autoPortrait=false&resumePositionMs=0&commentRootRpid=0",
            resolveStandardVideoRoute(
                bvid = "BV1audio",
                cid = 9L,
                coverUrl = "",
                startAudio = true
            )
        )
    }

    @Test
    fun resolveVideoRoutePath_keepsResumePositionWhenProvided() {
        val route = VideoRoute.resolveVideoRoutePath(
            bvid = "BV1resume",
            cid = 4455L,
            encodedCover = "",
            startAudio = false,
            autoPortrait = false,
            resumePositionMs = 98_000L
        )

        assertEquals(
            "video/BV1resume?cid=4455&cover=&startAudio=false&autoPortrait=false&resumePositionMs=98000&commentRootRpid=0",
            route
        )
    }
}
