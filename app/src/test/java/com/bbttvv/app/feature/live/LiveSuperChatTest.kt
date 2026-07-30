package com.bbttvv.app.feature.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSuperChatTest {
    private var nowSeconds = 1_000L
    private val queue = LiveSuperChatQueue { nowSeconds }

    @Test
    fun `payload mapper accepts numeric strings and fallback message`() {
        val item = buildLiveSuperChat(
            id = "7",
            userName = "测试用户",
            price = 30,
            message = "",
            fallbackMessage = "醒目留言",
            endTimeSeconds = "1060",
            durationSeconds = 60,
        )

        requireNotNull(item)
        assertEquals(7L, item.id)
        assertEquals("测试用户", item.userName)
        assertEquals(30L, item.price)
        assertEquals("醒目留言", item.message)
    }

    @Test
    fun `queue is bounded by lifetime and deduplicates ids`() {
        val first = item(id = 1L)
        val second = item(id = 2L)
        assertTrue(queue.offer(first))
        assertTrue(queue.offer(second))
        assertFalse(queue.offer(first.copy(message = "duplicate")))
        assertSame(first, queue.startNext())
        assertTrue(queue.finishCurrent(first))
        assertSame(second, queue.startNext())

        assertFalse(queue.offer(item(id = 3L, endTimeSeconds = nowSeconds)))
        assertEquals(10_000L, first.displayDurationMs(nowSeconds))
        assertNull(item(id = 4L, endTimeSeconds = nowSeconds).displayDurationMs(nowSeconds))
    }

    @Test
    fun `delete removes current and queued items`() {
        val first = item(id = 1L)
        val second = item(id = 2L)
        queue.offer(first)
        queue.offer(second)
        queue.startNext()
        assertTrue(queue.delete(setOf(1L, 2L)))
        assertNull(queue.startNext())
    }

    private fun item(
        id: Long,
        endTimeSeconds: Long? = nowSeconds + 60L,
    ) = LiveSuperChat(
        id = id,
        userName = "用户",
        price = 30L,
        message = "正文",
        endTimeSeconds = endTimeSeconds,
        durationSeconds = 60L,
    )
}
