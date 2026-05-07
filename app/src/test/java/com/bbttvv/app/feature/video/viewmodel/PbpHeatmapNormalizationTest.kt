package com.bbttvv.app.feature.video.viewmodel

import com.bbttvv.app.data.model.response.PbpHeatmapEvents
import com.bbttvv.app.data.model.response.PbpHeatmapResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PbpHeatmapNormalizationTest {
    @Test
    fun `normalizes heatmap points by duration and max value`() {
        val points = normalizePbpHeatmapPoints(
            response = PbpHeatmapResponse(
                stepSec = 5.0,
                events = PbpHeatmapEvents(default = listOf(0.0, 50.0, 100.0)),
            ),
            durationMs = 10_000L,
        )

        assertEquals(3, points.size)
        assertEquals(0f, points[0].fraction, FLOAT_DELTA)
        assertEquals(0f, points[0].intensity, FLOAT_DELTA)
        assertEquals(0.5f, points[1].fraction, FLOAT_DELTA)
        assertEquals(0.5f, points[1].intensity, FLOAT_DELTA)
        assertEquals(1f, points[2].fraction, FLOAT_DELTA)
        assertEquals(1f, points[2].intensity, FLOAT_DELTA)
    }

    @Test
    fun `returns empty points for invalid source data`() {
        assertTrue(
            normalizePbpHeatmapPoints(
                response = PbpHeatmapResponse(
                    stepSec = 5.0,
                    events = PbpHeatmapEvents(default = listOf(1.0)),
                ),
                durationMs = 0L,
            ).isEmpty()
        )
        assertTrue(
            normalizePbpHeatmapPoints(
                response = PbpHeatmapResponse(
                    stepSec = 0.0,
                    events = PbpHeatmapEvents(default = listOf(1.0)),
                ),
                durationMs = 10_000L,
            ).isEmpty()
        )
        assertTrue(
            normalizePbpHeatmapPoints(
                response = PbpHeatmapResponse(
                    stepSec = 5.0,
                    events = PbpHeatmapEvents(default = emptyList()),
                ),
                durationMs = 10_000L,
            ).isEmpty()
        )
        assertTrue(
            normalizePbpHeatmapPoints(
                response = PbpHeatmapResponse(
                    stepSec = 5.0,
                    events = PbpHeatmapEvents(default = listOf(0.0, 0.0)),
                ),
                durationMs = 10_000L,
            ).isEmpty()
        )
    }

    @Test
    fun `clamps fractions and negative intensities`() {
        val points = normalizePbpHeatmapPoints(
            response = PbpHeatmapResponse(
                stepSec = 10.0,
                events = PbpHeatmapEvents(default = listOf(100.0, -50.0, 200.0)),
            ),
            durationMs = 10_000L,
        )

        assertEquals(3, points.size)
        assertEquals(0f, points[0].fraction, FLOAT_DELTA)
        assertEquals(0.5f, points[0].intensity, FLOAT_DELTA)
        assertEquals(1f, points[1].fraction, FLOAT_DELTA)
        assertEquals(0f, points[1].intensity, FLOAT_DELTA)
        assertEquals(1f, points[2].fraction, FLOAT_DELTA)
        assertEquals(1f, points[2].intensity, FLOAT_DELTA)
    }

    private companion object {
        const val FLOAT_DELTA = 0.0001f
    }
}
