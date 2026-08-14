package com.bbttvv.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Field

class DanmakuVoteApiContractTest {
    @Test
    fun `vote request keeps required web player source fields`() {
        val method = BilibiliApi::class.java.methods.single { it.name == "submitDanmakuVote" }
        val fieldNames = method.parameterAnnotations
            .mapNotNull { annotations -> annotations.filterIsInstance<Field>().singleOrNull()?.value }

        assertTrue("polaris_app_id missing from vote form", "polaris_app_id" in fieldNames)
        assertTrue("polaris_platform missing from vote form", "polaris_platform" in fieldNames)
        assertEquals(100, DANMAKU_VOTE_POLARIS_APP_ID)
        assertEquals(5, DANMAKU_VOTE_POLARIS_PLATFORM)
    }
}
