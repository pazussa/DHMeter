package com.dropindh.app.ui.charts

import com.dhmeter.charts.model.ChartPoint
import com.dhmeter.charts.model.HeatmapPoint
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SeriesType
import kotlin.math.abs
import kotlin.math.max

private const val MIN_DT_SEC = 1e-3f
private const val MIN_DX_PCT = 1e-4f

private data class TimingSample(
    val distPct: Float,
    val timeSec: Float
)

private data class DerivedSpeedSample(
    val distanceM: Float,
    val timeSec: Float,
    val speedMps: Float
)

fun distanceFromPct(distPct: Float, totalDistanceM: Float?): Float? {
    val distance = totalDistanceM?.takeIf { it.isFinite() && it > 0f } ?: return null
    return (distance * (distPct.coerceIn(0f, 100f) / 100f)).coerceIn(0f, distance)
}

fun pctFromDistance(distanceM: Float, totalDistanceM: Float?): Float? {
    val distance = totalDistanceM?.takeIf { it.isFinite() && it > 0f } ?: return null
    return ((distanceM.coerceIn(0f, distance) / distance) * 100f).coerceIn(0f, 100f)
}

fun RunSeries.toBurdenChartPointsMeters(totalDistanceM: Float?): List<ChartPoint> {
    val distance = totalDistanceM?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val count = effectivePointCount
    return (0 until count).mapNotNull { i ->
        val distPct = points[i * 2]
        val y = points[i * 2 + 1]
        if (!distPct.isFinite() || !y.isFinite()) return@mapNotNull null
        ChartPoint(
            x = (distance * (distPct.coerceIn(0f, 100f) / 100f)).coerceIn(0f, distance),
            y = y
        )
    }
}

fun RunSeries.toSpeedChartPointsMeters(totalDistanceM: Float?): List<ChartPoint> {
    return toDerivedSpeedSamples(totalDistanceM)
        .map { sample -> ChartPoint(sample.distanceM, sample.speedMps * 3.6f) }
}

fun RunSeries.toAccelerationChartPointsMeters(totalDistanceM: Float?): List<ChartPoint> {
    val speedSamples = toDerivedSpeedSamples(totalDistanceM)
    if (speedSamples.size < 3) return emptyList()

    val rawAcceleration = ArrayList<ChartPoint>(speedSamples.size - 2)
    for (i in 1 until speedSamples.lastIndex) {
        val prev = speedSamples[i - 1]
        val next = speedSamples[i + 1]
        val dtSec = next.timeSec - prev.timeSec
        if (dtSec <= MIN_DT_SEC) continue
        val acceleration = (next.speedMps - prev.speedMps) / dtSec
        if (!acceleration.isFinite()) continue
        rawAcceleration += ChartPoint(speedSamples[i].distanceM, acceleration)
    }

    if (rawAcceleration.size < 3) return rawAcceleration
    return smoothSeriesY(rawAcceleration, windowRadius = 2)
}

fun RunSeries.toSpeedHeatmapPointsMeters(totalDistanceM: Float?): List<HeatmapPoint> {
    return toSpeedChartPointsMeters(totalDistanceM)
        .map { point -> HeatmapPoint(x = point.x, value = point.y.coerceAtLeast(0f)) }
}

private fun RunSeries.toDerivedSpeedSamples(totalDistanceM: Float?): List<DerivedSpeedSample> {
    if (seriesType != SeriesType.SPEED_TIME) return emptyList()
    val distance = totalDistanceM?.takeIf { it.isFinite() && it > 0f } ?: return emptyList()
    val timingSamples = toTimingSamples()
    if (timingSamples.size < 3) return emptyList()

    val monotonicTime = enforceMonotonic(timingSamples.map { it.timeSec }, minStep = 2e-3f)

    val smoothedSamples = timingSamples.indices.map { index ->
        TimingSample(
            distPct = timingSamples[index].distPct,
            timeSec = monotonicTime[index]
        )
    }

    val rawSpeed = ArrayList<DerivedSpeedSample>(smoothedSamples.size)
    for (i in 1 until smoothedSamples.size) {
        val prev = smoothedSamples[i - 1]
        val curr = smoothedSamples[i]
        val dxPct = curr.distPct - prev.distPct
        val dtSec = curr.timeSec - prev.timeSec
        if (dxPct <= MIN_DX_PCT || dtSec <= MIN_DT_SEC) continue

        val distanceM = distance * (curr.distPct / 100f)
        val speedMps = (distance * (dxPct / 100f)) / dtSec
        if (!distanceM.isFinite() || !speedMps.isFinite() || speedMps < 0f) continue

        rawSpeed += DerivedSpeedSample(
            distanceM = distanceM.coerceIn(0f, distance),
            timeSec = curr.timeSec,
            speedMps = speedMps
        )
    }

    if (rawSpeed.isEmpty()) return emptyList()
    if (rawSpeed.size == 1) {
        val firstDistanceM = distance * (smoothedSamples.first().distPct / 100f)
        return listOf(rawSpeed.first()).let { only ->
            if (!firstDistanceM.isFinite()) only else listOf(
                only.first().copy(distanceM = firstDistanceM.coerceIn(0f, distance)),
                only.first()
            )
        }
    }
    if (rawSpeed.size < 3) return rawSpeed

    val smoothedSpeed = smoothFloatSeries(rawSpeed.map { it.speedMps }, windowRadius = 1)
    return rawSpeed.indices.map { index ->
        rawSpeed[index].copy(
            speedMps = max(rawSpeed[index].speedMps, smoothedSpeed[index]).coerceAtLeast(0f)
        )
    }
}

private fun RunSeries.toTimingSamples(): List<TimingSample> {
    val count = effectivePointCount
    if (count < 2) return emptyList()

    val samples = ArrayList<TimingSample>(count)
    for (i in 0 until count) {
        val x = points[i * 2]
        val y = points[i * 2 + 1]
        if (!x.isFinite() || !y.isFinite()) continue
        samples += TimingSample(
            distPct = x.coerceIn(0f, 100f),
            timeSec = y.coerceAtLeast(0f)
        )
    }
    if (samples.isEmpty()) return emptyList()

    val sorted = samples.sortedBy { it.distPct }
    val deduped = ArrayList<TimingSample>(sorted.size)
    sorted.forEach { sample ->
        val last = deduped.lastOrNull()
        if (last != null && abs(last.distPct - sample.distPct) < 1e-4f) {
            deduped[deduped.lastIndex] = TimingSample(
                distPct = last.distPct,
                timeSec = max(last.timeSec, sample.timeSec)
            )
        } else {
            deduped += sample
        }
    }
    return deduped
}

private fun smoothSeriesY(points: List<ChartPoint>, windowRadius: Int): List<ChartPoint> {
    if (points.size < 3 || windowRadius <= 0) return points
    val ys = points.map { it.y }
    val smoothedY = smoothFloatSeries(ys, windowRadius)
    return points.indices.map { index ->
        ChartPoint(points[index].x, smoothedY[index])
    }
}

private fun smoothFloatSeries(values: List<Float>, windowRadius: Int): List<Float> {
    if (values.size < 3 || windowRadius <= 0) return values
    return values.indices.map { index ->
        val from = (index - windowRadius).coerceAtLeast(0)
        val to = (index + windowRadius).coerceAtMost(values.lastIndex)
        var weightedSum = 0f
        var weightSum = 0f
        for (i in from..to) {
            val distance = abs(i - index).toFloat()
            val weight = 1f / (1f + distance * distance)
            weightedSum += values[i] * weight
            weightSum += weight
        }
        if (weightSum <= 0f) values[index] else weightedSum / weightSum
    }
}

private fun enforceMonotonic(values: List<Float>, minStep: Float): List<Float> {
    if (values.isEmpty()) return values
    val result = FloatArray(values.size)
    result[0] = values[0]
    for (index in 1 until values.size) {
        result[index] = max(result[index - 1] + minStep, values[index])
    }
    return result.toList()
}
