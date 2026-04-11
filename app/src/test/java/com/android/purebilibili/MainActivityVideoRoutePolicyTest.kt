package com.android.purebilibili

import kotlin.test.Test
import kotlin.test.assertEquals

class MainActivityVideoRoutePolicyTest {

    @Test
    fun notificationRoute_defaultsToAutoPortraitAndStartAudioFalse() {
        assertEquals(
            "video/BV1abc?cid=0&cover=&startAudio=false&autoPortrait=true&resumePositionMs=0&commentRootRpid=0",
            resolveMainActivityVideoRoute(bvid = "BV1abc", cid = 0L)
        )
    }

    @Test
    fun notificationRoute_keepsCidValue() {
        assertEquals(
            "video/BV2xyz?cid=12345&cover=&startAudio=false&autoPortrait=true&resumePositionMs=0&commentRootRpid=0",
            resolveMainActivityVideoRoute(bvid = "BV2xyz", cid = 12345L)
        )
    }
}
