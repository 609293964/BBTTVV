package com.bbttvv.app.feature.video.danmaku

import com.bytedance.danmaku.render.engine.render.draw.text.TextData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DanmakuAppendPolicyTest {
    @Test
    fun `forward extension returns append boundary`() {
        val first = item("a", 1_000L)
        val second = item("b", 2_000L)
        val third = item("c", 3_000L)

        assertEquals(2, resolveDanmakuAppendStartIndex(listOf(first, second), listOf(first, second, third)))
    }

    @Test
    fun `prepend replacement and out of order additions require replacement`() {
        val first = item("a", 1_000L)
        val second = item("b", 2_000L)
        assertNull(resolveDanmakuAppendStartIndex(listOf(second), listOf(first, second)))
        assertNull(resolveDanmakuAppendStartIndex(listOf(first, second), listOf(first, second, item("late", 500L))))
    }

    private fun item(text: String, timeMs: Long): TextData = TextData().also {
        it.text = text
        it.showAtTime = timeMs
    }
}
