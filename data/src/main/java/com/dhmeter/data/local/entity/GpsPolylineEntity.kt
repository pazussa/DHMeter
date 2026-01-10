package com.dhmeter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores simplified GPS polyline for map visualization.
 * Points are stored as packed floats: [lat0, lon0, distPct0, lat1, lon1, distPct1, ...]
 * Maximum ~150 points to keep rendering fast.
 */
@Entity(
    tableName = "gps_polylines",
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("runId")]
)
data class GpsPolylineEntity(
    @PrimaryKey
    val runId: String,
    val points: ByteArray, // Packed: [lat, lon, distPct] as doubles/float per point
    val pointCount: Int,
    val totalDistanceM: Float,
    val avgAccuracyM: Float, // Average GPS accuracy
    val gpsQuality: String // GOOD, OK, POOR
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GpsPolylineEntity

        if (runId != other.runId) return false
        if (!points.contentEquals(other.points)) return false
        if (pointCount != other.pointCount) return false
        if (totalDistanceM != other.totalDistanceM) return false
        if (avgAccuracyM != other.avgAccuracyM) return false
        if (gpsQuality != other.gpsQuality) return false

        return true
    }

    override fun hashCode(): Int {
        var result = runId.hashCode()
        result = 31 * result + points.contentHashCode()
        result = 31 * result + pointCount
        result = 31 * result + totalDistanceM.hashCode()
        result = 31 * result + avgAccuracyM.hashCode()
        result = 31 * result + gpsQuality.hashCode()
        return result
    }
}
