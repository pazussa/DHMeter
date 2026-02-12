package com.dhmeter.signal.processor

import com.dhmeter.domain.model.*
import com.dhmeter.domain.usecase.RunProcessor
import com.dhmeter.sensing.data.SensorBuffers
import com.dhmeter.signal.metrics.*
import com.dhmeter.signal.validation.RunValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Main signal processor that analyzes LINEAR_ACCELERATION + GYROSCOPE + GPS data.
 * MVP2: No barometer - produces Impact, Harshness, Stability metrics and Landing events.
 */
@Singleton
class SignalProcessor @Inject constructor(
    private val sensorBuffers: SensorBuffers,
    private val impactAnalyzer: ImpactAnalyzer,
    private val harshnessAnalyzer: HarshnessAnalyzer,
    private val stabilityAnalyzer: StabilityAnalyzer,
    private val landingDetector: LandingDetector,
    private val distanceMapper: DistanceMapper,
    private val runValidator: RunValidator,
    private val gpsPolylineProcessor: GpsPolylineProcessor
) : RunProcessor {

    companion object {
        const val NUM_OUTPUT_POINTS = 200
        const val WINDOW_SIZE_SEC = 1.0f
        const val HOP_SIZE_SEC = 0.25f
    }

    override suspend fun process(handle: RawCaptureHandle): ProcessedRun = withContext(Dispatchers.Default) {
        val accelSamples = sensorBuffers.accel.getAll()
        val gyroSamples = sensorBuffers.gyro.getAll()
        val gpsSamples = sensorBuffers.gps.getAll()

        val durationMs = (handle.endTimeNs - handle.startTimeNs) / 1_000_000
        val totalDistance = calculateFilteredDistance(gpsSamples)

        // Validate run (no barometer - uses GPS/movement)
        val validation = runValidator.validateRun(
            accelSamples = accelSamples,
            gpsSamples = gpsSamples,
            durationMs = durationMs,
            totalDistanceM = totalDistance
        )

        // Create distance mapping
        val distMapping = distanceMapper.createMapping(gpsSamples, handle.startTimeNs)

        // Process windows
        val windowResults = processWindows(accelSamples, gyroSamples, distMapping, handle)

        // Detect events
        val events = detectEvents(accelSamples, distMapping, handle)

        // Calculate summary metrics
        // Sum all impact densities to get cumulative impact score for the run
        val impactScore = windowResults.impactDensity.sum()
        val harshnessAvg = windowResults.harshnessRms.average().toFloat()
        val harshnessP90 = percentile(windowResults.harshnessRms, 90)
        val stabilityScore = windowResults.stabilityVar.average().toFloat()
        
        // Calculate landing quality score
        val landingEvents = events.filter { it.type == EventType.LANDING }
        val landingQualityScore = if (landingEvents.isNotEmpty()) {
            landingEvents.map { it.severity }.average().toFloat()
        } else null

        // Create run
        val runId = UUID.randomUUID().toString()
        // Use actual wall-clock time for startedAt/endedAt (not nanoTime which is boot-relative)
        val currentTimeMs = System.currentTimeMillis()
        val run = Run(
            runId = runId,
            trackId = handle.trackId,
            startedAt = currentTimeMs - durationMs,
            endedAt = currentTimeMs,
            durationMs = durationMs,
            isValid = validation.isValid,
            invalidReason = validation.issues.firstOrNull()?.description,
            deviceModel = handle.deviceModel,
            sampleRateAccelHz = handle.accelSampleRate,
            sampleRateGyroHz = handle.gyroSampleRate,
            sampleRateBaroHz = null, // No barometer in MVP2
            gpsQuality = validation.gpsQuality.overallQuality,
            distanceMeters = totalDistance,
            impactScore = impactScore,
            harshnessAvg = harshnessAvg,
            harshnessP90 = harshnessP90,
            stabilityScore = stabilityScore,
            landingQualityScore = landingQualityScore,
            avgSpeed = if (durationMs > 0) totalDistance / (durationMs / 1000f) else 0f,
            slopeClassAvg = null // No slope in MVP2
        )

        // Create series (3 charts: Impact, Harshness, Stability - no Slope)
        val series = listOf(
            createSeries(runId, SeriesType.IMPACT_DENSITY, windowResults.impactDensity, windowResults.distPcts),
            createSeries(runId, SeriesType.HARSHNESS, windowResults.harshnessRms, windowResults.distPcts),
            createSeries(runId, SeriesType.STABILITY, windowResults.stabilityVar, windowResults.distPcts)
        )

        // Set runId on events
        val eventsWithRunId = events.map { it.copy(runId = runId) }
        
        // Create GPS polyline for map visualization
        val gpsPolyline = gpsPolylineProcessor.processToPolyline(
            runId = runId,
            samples = gpsSamples,
            totalDistanceM = totalDistance
        )

        ProcessedRun(run, series, eventsWithRunId, gpsPolyline)
    }

    private fun processWindows(
        accelSamples: List<com.dhmeter.sensing.data.AccelSample>,
        gyroSamples: List<com.dhmeter.sensing.data.GyroSample>,
        distMapping: DistanceMapping,
        handle: RawCaptureHandle
    ): WindowResults {
        val impactDensities = mutableListOf<Float>()
        val harshnessValues = mutableListOf<Float>()
        val stabilityValues = mutableListOf<Float>()
        val distPcts = mutableListOf<Float>()

        if (accelSamples.isEmpty()) {
            return WindowResults(impactDensities, harshnessValues, stabilityValues, distPcts)
        }

        val sampleRate = handle.accelSampleRate
        val windowSamples = (WINDOW_SIZE_SEC * sampleRate).toInt()
        val hopSamples = (HOP_SIZE_SEC * sampleRate).toInt()

        var windowStart = 0
        while (windowStart + windowSamples <= accelSamples.size) {
            val windowAccel = accelSamples.subList(windowStart, windowStart + windowSamples)
            val centerTimeNs = windowAccel[windowSamples / 2].timestampNs
            val distPct = distMapping.getDistPct(centerTimeNs)

            val windowGyro = gyroSamples.filter { sample ->
                sample.timestampNs >= windowAccel.first().timestampNs &&
                sample.timestampNs <= windowAccel.last().timestampNs
            }

            val impact = impactAnalyzer.analyzeWindow(windowAccel)
            val harshness = harshnessAnalyzer.analyzeWindow(windowAccel, sampleRate)
            val stability = stabilityAnalyzer.analyzeWindow(windowGyro)

            impactDensities.add(impact)
            harshnessValues.add(harshness)
            stabilityValues.add(stability.stabilityIndex)
            distPcts.add(distPct)

            windowStart += hopSamples
        }

        return WindowResults(impactDensities, harshnessValues, stabilityValues, distPcts)
    }

    private fun detectEvents(
        accelSamples: List<com.dhmeter.sensing.data.AccelSample>,
        distMapping: DistanceMapping,
        handle: RawCaptureHandle
    ): List<RunEvent> {
        val events = mutableListOf<RunEvent>()
        val startTimeNs = handle.startTimeNs
        val sampleRate = handle.accelSampleRate

        val landings = landingDetector.detectLandings(accelSamples, sampleRate)
        landings.forEach { landing ->
            val timeSec = (landing.timestampMs - startTimeNs / 1_000_000) / 1000f
            val estimatedTimeNs = landing.timestampMs * 1_000_000
            val distPct = distMapping.getDistPct(estimatedTimeNs)

            events.add(
                RunEvent(
                    eventId = UUID.randomUUID().toString(),
                    runId = "",
                    type = EventType.LANDING,
                    distPct = distPct,
                    timeSec = timeSec,
                    severity = if (landing.peakG > 4f) 2f else 1f,
                    meta = EventMeta(
                        peakG = landing.peakG,
                        energy300ms = landing.energy300ms,
                        recoveryMs = landing.recoveryMs.toInt()
                    )
                )
            )
        }

        return events.sortedBy { it.distPct }
    }

    private fun createSeries(
        runId: String,
        type: SeriesType,
        values: List<Float>,
        distPcts: List<Float>
    ): RunSeries {
        val outputX = FloatArray(NUM_OUTPUT_POINTS) { it * 100f / (NUM_OUTPUT_POINTS - 1) }
        val outputY = FloatArray(NUM_OUTPUT_POINTS)
        val samples = sanitizeSeriesSamples(values, distPcts)

        if (samples.isNotEmpty()) {
            val firstSample = samples.first()
            val lastSample = samples.last()

            if (samples.size == 1) {
                for (i in 0 until NUM_OUTPUT_POINTS) {
                    outputY[i] = firstSample.y
                }
            } else {
                var segmentIdx = 0
                for (i in 0 until NUM_OUTPUT_POINTS) {
                    val targetPct = outputX[i]

                    outputY[i] = when {
                        targetPct <= firstSample.x -> firstSample.y
                        targetPct >= lastSample.x -> lastSample.y
                        else -> {
                            while (
                                segmentIdx < samples.lastIndex - 1 &&
                                samples[segmentIdx + 1].x < targetPct
                            ) {
                                segmentIdx++
                            }

                            val low = samples[segmentIdx]
                            val high = samples[segmentIdx + 1]
                            val dx = high.x - low.x

                            if (abs(dx) < 1e-6f) {
                                low.y
                            } else {
                                val fraction = (targetPct - low.x) / dx
                                low.y + fraction * (high.y - low.y)
                            }
                        }
                    }
                }
            }
        }

        val points = FloatArray(NUM_OUTPUT_POINTS * 2)
        for (i in 0 until NUM_OUTPUT_POINTS) {
            points[i * 2] = outputX[i]
            points[i * 2 + 1] = outputY[i]
        }

        return RunSeries(runId, type, XAxisType.DIST_PCT, points, NUM_OUTPUT_POINTS)
    }

    private fun sanitizeSeriesSamples(
        values: List<Float>,
        distPcts: List<Float>
    ): List<SeriesSample> {
        if (values.isEmpty() || distPcts.isEmpty()) return emptyList()

        val rawSamples = values.zip(distPcts).mapNotNull { (y, x) ->
            if (!x.isFinite() || !y.isFinite()) {
                null
            } else {
                SeriesSample(x = x.coerceIn(0f, 100f), y = y)
            }
        }
        if (rawSamples.isEmpty()) return emptyList()

        val sorted = rawSamples.sortedBy { it.x }
        val merged = ArrayList<SeriesSample>(sorted.size)

        sorted.forEach { sample ->
            val last = merged.lastOrNull()
            if (last != null && abs(last.x - sample.x) < 1e-5f) {
                merged[merged.lastIndex] = SeriesSample(
                    x = last.x,
                    y = (last.y + sample.y) / 2f
                )
            } else {
                merged.add(sample)
            }
        }

        return merged
    }

    private fun percentile(values: List<Float>, p: Int): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val index = (p / 100.0 * (sorted.size - 1)).toInt()
        return sorted[index]
    }

    /**
     * Calculate total distance with GPS drift filtering.
     * Only accumulates distance when:
     * - Accuracy is acceptable (<= 20m)
     * - Speed is above minimum threshold (>= 0.5 m/s)
     * - Movement distance exceeds accuracy uncertainty
     */
    private fun calculateFilteredDistance(
        gpsSamples: List<com.dhmeter.sensing.data.GpsSample>
    ): Float {
        if (gpsSamples.size < 2) return 0f
        
        val minSpeedMps = 0.5f
        val maxAccuracyM = 20f
        val minDistanceFactor = 0.5f
        
        var totalDistance = 0.0
        
        gpsSamples.zipWithNext { a, b ->
            val segmentDist = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
            
            val isValidMovement = b.accuracy <= maxAccuracyM &&
                    b.speed >= minSpeedMps &&
                    segmentDist > (maxOf(a.accuracy, b.accuracy) * minDistanceFactor)
            
            if (isValidMovement) {
                totalDistance += segmentDist
            }
        }
        
        return totalDistance.toFloat()
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }
}

data class WindowResults(
    val impactDensity: List<Float>,
    val harshnessRms: List<Float>,
    val stabilityVar: List<Float>,
    val distPcts: List<Float>
)

private data class SeriesSample(
    val x: Float,
    val y: Float
)
