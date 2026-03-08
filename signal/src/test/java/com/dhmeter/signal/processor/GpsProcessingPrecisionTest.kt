package com.dhmeter.signal.processor

import com.dhmeter.sensing.data.GpsSample
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsProcessingPrecisionTest {

    @Test
    fun sanitizer_discards_unrealistic_jump_when_accuracy_is_poor() {
        val samples = listOf(
            gpsSample(timestampNs = 0L, lat = 4.700000, lon = -74.070000, speed = 5f, accuracy = 5f),
            gpsSample(timestampNs = 1_000_000_000L, lat = 4.700050, lon = -74.070000, speed = 5f, accuracy = 6f),
            gpsSample(timestampNs = 2_000_000_000L, lat = 4.710000, lon = -74.080000, speed = 5f, accuracy = 75f)
        )

        val sanitized = GpsSampleSanitizer.sanitize(samples, gpsSensitivity = 1f)

        assertTrue("Expected sanitizer to remove jump outlier", sanitized.size <= 2)
    }

    @Test
    fun distance_mapper_ignores_distance_from_bad_accuracy_jump() {
        val samples = listOf(
            gpsSample(timestampNs = 0L, lat = 4.700000, lon = -74.070000, speed = 5f, accuracy = 5f),
            gpsSample(timestampNs = 1_000_000_000L, lat = 4.700050, lon = -74.070000, speed = 5f, accuracy = 6f),
            gpsSample(timestampNs = 2_000_000_000L, lat = 4.710000, lon = -74.080000, speed = 4f, accuracy = 70f)
        )

        val mapping = DistanceMapper().createMapping(
            samples = samples,
            startTimeNs = 0L,
            gpsSensitivity = 1f
        )

        // The first segment is only a few meters. Without filtering the jump, this would be >1km.
        assertTrue("Expected filtered mapping distance to stay below 30m", mapping.totalDistance < 30.0)
    }

    @Test
    fun polyline_processor_keeps_turn_geometry_after_simplification() {
        val metersToDegrees = 1.0 / 111_320.0
        val samples = mutableListOf<GpsSample>()
        var timestampNs = 0L

        // Horizontal section
        for (i in 0..70) {
            samples += gpsSample(
                timestampNs = timestampNs,
                lat = 4.700000,
                lon = -74.070000 + (i * 2.0 * metersToDegrees),
                speed = 6f,
                accuracy = 5f
            )
            timestampNs += 250_000_000L
        }

        val cornerLon = -74.070000 + (70 * 2.0 * metersToDegrees)
        // Vertical section
        for (i in 1..70) {
            samples += gpsSample(
                timestampNs = timestampNs,
                lat = 4.700000 + (i * 2.0 * metersToDegrees),
                lon = cornerLon,
                speed = 6f,
                accuracy = 5f
            )
            timestampNs += 250_000_000L
        }

        val processor = GpsPolylineProcessor()
        val polyline = processor.processToPolyline(
            runId = "test-run",
            samples = samples,
            totalDistanceM = 280f
        )

        assertTrue("Expected non-empty polyline", polyline.points.isNotEmpty())

        val cornerLat = 4.700000
        val maxCornerDistanceDeg = 8.0 * metersToDegrees
        val hasCornerPoint = polyline.points.any { point ->
            kotlin.math.abs(point.lat - cornerLat) <= maxCornerDistanceDeg &&
                kotlin.math.abs(point.lon - cornerLon) <= maxCornerDistanceDeg
        }
        assertTrue("Expected simplified polyline to keep corner geometry", hasCornerPoint)
    }

    private fun gpsSample(
        timestampNs: Long,
        lat: Double,
        lon: Double,
        speed: Float,
        accuracy: Float
    ): GpsSample {
        return GpsSample(
            timestampNs = timestampNs,
            latitude = lat,
            longitude = lon,
            altitude = null,
            speed = speed,
            accuracy = accuracy
        )
    }
}
