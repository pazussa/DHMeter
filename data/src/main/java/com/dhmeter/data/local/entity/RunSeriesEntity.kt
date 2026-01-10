package com.dhmeter.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "run_series",
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
data class RunSeriesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val runId: String,
    val seriesType: String,
    val xType: String,
    val points: ByteArray, // Packed float pairs [x0,y0,x1,y1,...]
    val pointCount: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RunSeriesEntity

        if (id != other.id) return false
        if (runId != other.runId) return false
        if (seriesType != other.seriesType) return false
        if (xType != other.xType) return false
        if (!points.contentEquals(other.points)) return false
        if (pointCount != other.pointCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + runId.hashCode()
        result = 31 * result + seriesType.hashCode()
        result = 31 * result + xType.hashCode()
        result = 31 * result + points.contentHashCode()
        result = 31 * result + pointCount
        return result
    }
}
