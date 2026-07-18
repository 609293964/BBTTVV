package com.bbttvv.app.feature.video.screen

import com.bbttvv.app.data.model.response.SponsorActionType
import com.bbttvv.app.data.model.response.SponsorBlockMarkerMode
import com.bbttvv.app.data.model.response.SponsorCategory
import com.bbttvv.app.data.model.response.SponsorSegment
import com.bbttvv.app.feature.plugin.SponsorBlockConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSponsorNavigationTest {
    @Test
    fun `navigation options include sorted chapter and highlight only`() {
        val options = buildSponsorNavigationOptions(
            listOf(
                segment("skip", 5f, SponsorCategory.SPONSOR, SponsorActionType.SKIP),
                segment("chapter", 30f, SponsorCategory.CHAPTER, SponsorActionType.CHAPTER, "第二章"),
                segment("poi", 10f, SponsorCategory.POI_HIGHLIGHT, SponsorActionType.POI),
            )
        )

        assertEquals(listOf("poi", "chapter"), options.map { it.key })
        assertEquals("第二章", options.last().label)
    }

    @Test
    fun `all markers use distinct shapes for ranges points and chapters`() {
        val marks = buildSponsorProgressMarks(
            segments = listOf(
                segment("skip", 5f, SponsorCategory.SPONSOR, SponsorActionType.SKIP, endSeconds = 8f),
                segment("poi", 10f, SponsorCategory.POI_HIGHLIGHT, SponsorActionType.POI),
                segment("chapter", 30f, SponsorCategory.CHAPTER, SponsorActionType.CHAPTER),
            ),
            durationMs = 60_000L,
            enabled = true,
            config = SponsorBlockConfig(markerModeRaw = SponsorBlockMarkerMode.ALL_SKIPPABLE.name),
        )

        assertEquals(
            listOf(
                SponsorProgressMarkKind.Range,
                SponsorProgressMarkKind.Point,
                SponsorProgressMarkKind.Chapter,
            ),
            marks.map { it.kind },
        )
        assertTrue(marks.drop(1).all { it.startFraction == it.endFraction })
    }

    private fun segment(
        uuid: String,
        startSeconds: Float,
        category: String,
        actionType: String,
        description: String? = null,
        endSeconds: Float = startSeconds,
    ) = SponsorSegment(
        segment = listOf(startSeconds, endSeconds),
        UUID = uuid,
        category = category,
        actionType = actionType,
        description = description,
    )
}
