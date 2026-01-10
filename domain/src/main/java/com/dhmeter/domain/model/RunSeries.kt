package com.dhmeter.domain.model

/**
 * Represents a time series of sensor-derived metrics aligned to track distance percentage.
 * Each series contains N points (typically 200) spanning 0-100% of the track.
 */
data class RunSeries(
    val runId: String,
    val seriesType: SeriesType,
    val xType: XAxisType = XAxisType.DIST_PCT,
    val points: FloatArray, // Interleaved [x0, y0, x1, y1, ...]
    val pointCount: Int
) {
    /**
     * Get X values as a list
     */
    fun getXValues(): List<Float> {
        return (0 until pointCount).map { points[it * 2] }
    }

    /**
     * Get Y values as a list
     */
    fun getYValues(): List<Float> {
        return (0 until pointCount).map { points[it * 2 + 1] }
    }

    /**
     * Get point at index
     */
    fun getPoint(index: Int): Pair<Float, Float> {
        require(index in 0 until pointCount)
        return Pair(points[index * 2], points[index * 2 + 1])
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RunSeries

        if (runId != other.runId) return false
        if (seriesType != other.seriesType) return false
        if (xType != other.xType) return false
        if (!points.contentEquals(other.points)) return false
        if (pointCount != other.pointCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = runId.hashCode()
        result = 31 * result + seriesType.hashCode()
        result = 31 * result + xType.hashCode()
        result = 31 * result + points.contentHashCode()
        result = 31 * result + pointCount
        return result
    }
}

enum class SeriesType {
    IMPACT_DENSITY,  // Impact severity per distance segment
    HARSHNESS,       // RMS vibration (15-40Hz band)
    STABILITY,       // Gyroscope variance (pitch/roll)
    SPEED_TIME       // Speed over time (secondary)
}

enum class XAxisType {
    DIST_PCT,  // Distance percentage (0-100)
    TIME_S     // Time in seconds
}
