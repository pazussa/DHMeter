package com.dropindh.app.ui.charts

import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SeriesType
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedDistanceDerivationTest {

    @Test
    fun toSpeedChartPointsMeters_returns_points_with_minimum_valid_profile() {
        val series = runSeries(
            0f, 0f,
            50f, 20f,
            100f, 40f
        )

        val points = series.toSpeedChartPointsMeters(totalDistanceM = 1_000f)

        assertTrue("Expected non-empty speed points for a valid 3-point profile", points.isNotEmpty())
    }

    @Test
    fun toSpeedChartPointsMeters_preserves_peak_near_finish() {
        val series = runSeries(
            0f, 0f,
            30f, 30f,
            60f, 60f,
            85f, 80f,
            100f, 84f
        )

        val points = series.toSpeedChartPointsMeters(totalDistanceM = 1_000f)
        val peakKmh = points.maxOfOrNull { it.y } ?: 0f

        assertTrue("Expected speed peak near finish to remain visible, got $peakKmh km/h", peakKmh >= 110f)
    }

    private fun runSeries(vararg values: Float): RunSeries {
        return RunSeries(
            runId = "test-run",
            seriesType = SeriesType.SPEED_TIME,
            points = floatArrayOf(*values),
            pointCount = values.size / 2
        )
    }
}
