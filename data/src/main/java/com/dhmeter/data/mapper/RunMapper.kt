package com.dhmeter.data.mapper

import com.dhmeter.data.local.entity.EventEntity
import com.dhmeter.data.local.entity.RunEntity
import com.dhmeter.data.local.entity.RunSeriesEntity
import com.dhmeter.domain.model.*
import com.google.gson.Gson
import java.nio.ByteBuffer
import java.nio.ByteOrder

private val gson = Gson()

// Run Mapping
fun RunEntity.toDomain(): Run = Run(
    runId = runId,
    trackId = trackId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationMs = durationMs,
    isValid = isValid,
    invalidReason = invalidReason,
    phonePlacement = phonePlacement,
    deviceModel = deviceModel,
    sampleRateAccelHz = sampleRateAccelHz,
    sampleRateGyroHz = sampleRateGyroHz,
    sampleRateBaroHz = sampleRateBaroHz,
    gpsQuality = GpsQuality.valueOf(gpsQuality),
    distanceMeters = distanceMeters,
    pauseCount = pauseCount,
    impactScore = impactScore,
    harshnessAvg = harshnessAvg,
    harshnessP90 = harshnessP90,
    stabilityScore = stabilityScore,
    landingQualityScore = landingQualityScore,
    avgSpeed = avgSpeed,
    slopeClassAvg = slopeClassAvg,
    setupNote = setupNote,
    conditionsNote = conditionsNote
)

fun Run.toEntity(): RunEntity = RunEntity(
    runId = runId,
    trackId = trackId,
    startedAt = startedAt,
    endedAt = endedAt,
    durationMs = durationMs,
    isValid = isValid,
    invalidReason = invalidReason,
    phonePlacement = phonePlacement,
    deviceModel = deviceModel,
    sampleRateAccelHz = sampleRateAccelHz,
    sampleRateGyroHz = sampleRateGyroHz,
    sampleRateBaroHz = sampleRateBaroHz,
    gpsQuality = gpsQuality.name,
    distanceMeters = distanceMeters,
    pauseCount = pauseCount,
    impactScore = impactScore,
    harshnessAvg = harshnessAvg,
    harshnessP90 = harshnessP90,
    stabilityScore = stabilityScore,
    landingQualityScore = landingQualityScore,
    avgSpeed = avgSpeed,
    slopeClassAvg = slopeClassAvg,
    setupNote = setupNote,
    conditionsNote = conditionsNote
)

// Series Mapping
fun RunSeriesEntity.toDomain(): RunSeries {
    // Unpack float array from bytes
    val buffer = ByteBuffer.wrap(points).order(ByteOrder.LITTLE_ENDIAN)
    val floatArray = FloatArray(pointCount * 2)
    for (i in floatArray.indices) {
        floatArray[i] = buffer.float
    }
    
    return RunSeries(
        runId = runId,
        seriesType = SeriesType.valueOf(seriesType),
        xType = XAxisType.valueOf(xType),
        points = floatArray,
        pointCount = pointCount
    )
}

fun RunSeries.toEntity(): RunSeriesEntity {
    // Pack float array to bytes
    val buffer = ByteBuffer.allocate(points.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (f in points) {
        buffer.putFloat(f)
    }
    
    return RunSeriesEntity(
        runId = runId,
        seriesType = seriesType.name,
        xType = xType.name,
        points = buffer.array(),
        pointCount = pointCount
    )
}

// Event Mapping
fun EventEntity.toDomain(): RunEvent = RunEvent(
    eventId = eventId,
    runId = runId,
    type = type,
    distPct = distPct,
    timeSec = timeSec,
    severity = severity,
    meta = meta?.let { gson.fromJson(it, EventMeta::class.java) }
)

fun RunEvent.toEntity(): EventEntity = EventEntity(
    eventId = eventId,
    runId = runId,
    type = type,
    distPct = distPct,
    timeSec = timeSec,
    severity = severity,
    meta = meta?.let { gson.toJson(it) }
)
