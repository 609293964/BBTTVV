package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.InteractChoice
import com.bbttvv.app.data.model.response.InteractQuestion
import com.bbttvv.app.data.model.response.InteractEdgeInfoResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveVideoStateTest {
    @Test
    fun `sanitized edge fixture decodes and excludes conditional route`() {
        val fixture = requireNotNull(javaClass.getResource("/fixtures/interactive_edge_regular.json")).readText()
        val edge = Json { ignoreUnknownKeys = true }
            .decodeFromString<InteractEdgeInfoResponse>(fixture)
            .data!!
        assertEquals(1001L, edge.edgeId)
        assertEquals(listOf("Route A"), edge.firstRegularQuestion()!!.visibleRegularOptions().map { it.text })
    }

    @Test
    fun `trigger is measured back from media end and clamped`() {
        assertEquals(9_500L, resolveInteractiveTriggerPositionMs(10_000L, 500))
        assertEquals(9_700L, resolveInteractiveTriggerPositionMs(10_000L, 0))
        assertEquals(0L, resolveInteractiveTriggerPositionMs(10_000L, 20_000))
    }

    @Test
    fun `duration uses eight second fallback and clamps positive values`() {
        assertEquals(8_000L, resolveInteractiveDurationMs(-1))
        assertEquals(8_000L, resolveInteractiveDurationMs(0))
        assertEquals(2_000L, resolveInteractiveDurationMs(1))
        assertEquals(30_000L, resolveInteractiveDurationMs(60))
    }

    @Test
    fun `regular options filter hidden conditional invalid and blank choices`() {
        val question = InteractQuestion(
            choices = listOf(
                InteractChoice(id = 1, cid = 11, option = "A", isDefault = 1),
                InteractChoice(id = 2, cid = 12, option = "hidden", isHidden = 1),
                InteractChoice(id = 3, cid = 13, option = "condition", condition = "x > 0"),
                InteractChoice(id = 0, cid = 14, option = "invalid edge"),
                InteractChoice(id = 4, cid = 0, option = "invalid cid"),
                InteractChoice(id = 5, cid = 15, option = " "),
            ),
        )

        val options = question.visibleRegularOptions()

        assertEquals(listOf("A"), options.map { it.text })
        assertTrue(options.single().isDefault)
        assertEquals(0, resolveInteractiveDefaultIndex(options))
    }

    @Test
    fun `default falls back to first visible option`() {
        val options = listOf(
            InteractiveVideoOption(1, 11, "A", false),
            InteractiveVideoOption(2, 12, "B", false),
        )
        assertEquals(0, resolveInteractiveDefaultIndex(options))
        assertFalse(options.any { it.isDefault })
    }
}
