package com.dhmeter.data.mapper

import com.dhmeter.data.local.entity.GpsPolylineEntity
import com.dhmeter.domain.model.GpsPoint
import com.dhmeter.domain.model.GpsPolyline
import com.dhmeter.domain.model.MapGpsQuality
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Maps between GpsPolylineEntity and domain models.
 * Points are stored as packed: [lat (double), lon (double), distPct (float)] per point
 * = 8 + 8 + 4 = 20 bytes per point
 */
object GpsPolylineMapper {
    
    private const val BYTES_PER_POINT = 20 // 8 (lat) + 8 (lon) + 4 (distPct)
    
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
        val buffer = ByteBuffer.allocate(points.size * BYTES_PER_POINT)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        points.forEach { point ->
            buffer.putDouble(point.lat)
            buffer.putDouble(point.lon)
            buffer.putFloat(point.distPct)
        }
        
        return buffer.array()
    }
    
    private fun unpackPoints(data: ByteArray, count: Int): List<GpsPoint> {
        if (data.isEmpty() || count == 0) return emptyList()
        
        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        return (0 until count).map {
            GpsPoint(
                lat = buffer.getDouble(),
                lon = buffer.getDouble(),
                distPct = buffer.getFloat()
            )
        }
    }
}
