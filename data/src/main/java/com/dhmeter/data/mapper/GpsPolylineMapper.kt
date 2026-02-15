package com.dhmeter.data.mapper

import com.dhmeter.data.local.entity.GpsPolylineEntity
import com.dhmeter.domain.model.GpsPoint
import com.dhmeter.domain.model.GpsPolyline
import com.dhmeter.domain.model.MapGpsQuality
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Maps between GpsPolylineEntity and domain models.
 * Backward-compatible packed formats:
 * V1: [lat (double), lon (double), distPct (float)] = 20 bytes
 * V2: [lat (double), lon (double), distPct (float), altitudeM (float)] = 24 bytes
 */
object GpsPolylineMapper {
    
    private const val BYTES_PER_POINT_V1 = 20 // 8 (lat) + 8 (lon) + 4 (distPct)
    private const val BYTES_PER_POINT_V2 = 24 // + 4 (altitudeM)
    
    fun entityToDomain(entity: GpsPolylineEntity): GpsPolyline {
        val points = unpackPoints(entity.points, entity.pointCount)
        val quality = try {
            MapGpsQuality.valueOf(entity.gpsQuality)
        } catch (e: Exception) {
            MapGpsQuality.OK
        }
        
        return GpsPolyline(
            runId = entity.runId,
            points = points,
            totalDistanceM = entity.totalDistanceM,
            avgAccuracyM = entity.avgAccuracyM,
            gpsQuality = quality
        )
    }
    
    fun domainToEntity(polyline: GpsPolyline): GpsPolylineEntity {
        return GpsPolylineEntity(
            runId = polyline.runId,
            points = packPoints(polyline.points),
            pointCount = polyline.points.size,
            totalDistanceM = polyline.totalDistanceM,
            avgAccuracyM = polyline.avgAccuracyM,
            gpsQuality = polyline.gpsQuality.name
        )
    }
    
    private fun packPoints(points: List<GpsPoint>): ByteArray {
        val buffer = ByteBuffer.allocate(points.size * BYTES_PER_POINT_V2)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        points.forEach { point ->
            buffer.putDouble(point.lat)
            buffer.putDouble(point.lon)
            buffer.putFloat(point.distPct)
            buffer.putFloat(point.altitudeM ?: Float.NaN)
        }
        
        return buffer.array()
    }
    
    private fun unpackPoints(data: ByteArray, count: Int): List<GpsPoint> {
        if (data.isEmpty() || count == 0) return emptyList()
        val bytesPerPoint = resolveBytesPerPoint(data, count)
        val safeCount = minOf(count.coerceAtLeast(0), data.size / bytesPerPoint)
        if (safeCount == 0) return emptyList()
        
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        return (0 until safeCount).map {
            val lat = buffer.getDouble()
            val lon = buffer.getDouble()
            val distPct = buffer.getFloat()
            val altitude = if (bytesPerPoint == BYTES_PER_POINT_V2) {
                buffer.getFloat().takeIf { it.isFinite() }
            } else null
            GpsPoint(
                lat = lat,
                lon = lon,
                distPct = distPct,
                altitudeM = altitude
            )
        }
    }

    private fun resolveBytesPerPoint(data: ByteArray, count: Int): Int {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount > 0) {
            if (data.size == safeCount * BYTES_PER_POINT_V2) return BYTES_PER_POINT_V2
            if (data.size == safeCount * BYTES_PER_POINT_V1) return BYTES_PER_POINT_V1
        }
        return when {
            data.size % BYTES_PER_POINT_V2 == 0 -> BYTES_PER_POINT_V2
            else -> BYTES_PER_POINT_V1
        }
    }
}
