package com.dhmeter.signal.processor

import com.dhmeter.domain.model.*
import com.dhmeter.domain.repository.SensorSensitivityRepository
import com.dhmeter.domain.usecase.RunProcessor
import com.dhmeter.sensing.data.AccelSample
import com.dhmeter.sensing.data.SensorBuffers
import com.dhmeter.signal.metrics.*
import com.dhmeter.signal.validation.RunValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

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
    private val gpsPolylineProcessor: GpsPolylineProcessor,
    private val sensitivityRepository: SensorSensitivityRepository
) : RunProcessor {

    companion object {
        const val NUM_OUTPUT_POINTS = 200
        const val WINDOW_SIZE_SEC = 1.0f
        const val HOP_SIZE_SEC = 0.25f
        private const val IMPACT_EVENT_DEBOUNCE_MS = 250L
        private const val IMPACT_NEAR_LANDING_MS = 350L
        private const val HARSHNESS_BURST_WINDOW_SEC = 0.35f
        private const val HARSHNESS_BURST_HOP_SEC = 0.10f
        private const val HARSHNESS_BURST_MIN_DURATION_MS = 180L
        private const val HARSHNESS_BURST_MERGE_GAP_MS = 220L
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
        val harshnessAvg = if (windowResults.harshnessRms.isNotEmpty()) {
            windowResults.harshnessRms.average().toFloat()
        } else 0f
        val harshnessP90 = percentile(windowResults.harshnessRms, 90)
        val stabilityScore = if (windowResults.stabilityVar.isNotEmpty()) {
            windowResults.stabilityVar.average().toFloat()
        } else 0f
        
        // Calculate landing quality score
        val landingEvents = events.filter { it.type == EventType.LANDING }
        val landingQualityScore = if (landingEvents.isNotEmpty()) {
            landingEvents.map { it.severity }.average().toFloat()
        } else null

        // Create run
        val runId = UUID.randomUUID().toString()
        // Use actual wall-clock time for startedAt/endedAt (not nanoTime which is boot-relative)
        val currentTimeMs = System.currentTimeMillis()
        val maxSpeed = gpsSamples.maxOfOrNull { it.speed }?.takeIf { it.isFinite() }
        val run = Run(
            runId = runId,
            trackId = handle.trackId,
            startedAt = currentTimeMs - durationMs,
            endedAt = currentTimeMs,
            durationMs = durationMs,
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
            maxSpeed = maxSpeed,
            slopeClassAvg = null // No slope in MVP2
        )

        // Create series (3 burden charts + optional timing profile for split deltas)
        val series = mutableListOf(
            createSeries(runId, SeriesType.IMPACT_DENSITY, windowResults.impactDensity, windowResults.distPcts),
            createSeries(runId, SeriesType.HARSHNESS, windowResults.harshnessRms, windowResults.distPcts),
            createSeries(runId, SeriesType.STABILITY, windowResults.stabilityVar, windowResults.distPcts)
        )
        createTimingSeries(
            runId = runId,
            gpsSamples = gpsSamples,
            totalDistanceM = totalDistance,
            startTimeNs = handle.startTimeNs,
            durationMs = durationMs
        )?.let { series.add(it) }

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

    private fun createTimingSeries(
        runId: String,
        gpsSamples: List<com.dhmeter.sensing.data.GpsSample>,
        totalDistanceM: Float,
        startTimeNs: Long,
        durationMs: Long
    ): RunSeries? {
        if (durationMs <= 0L) return null
        if (gpsSamples.size < 2 || totalDistanceM <= 0f) {
            return createSeries(
                runId = runId,
                type = SeriesType.SPEED_TIME,
                values = listOf(0f, durationMs / 1000f),
                distPcts = listOf(0f, 100f)
            )
        }

        val distPcts = ArrayList<Float>(gpsSamples.size)
        val elapsedSec = ArrayList<Float>(gpsSamples.size)
        var cumulativeDistanceM = 0.0

        distPcts.add(0f)
        elapsedSec.add(((gpsSamples.first().timestampNs - startTimeNs).coerceAtLeast(0L) / 1_000_000_000.0).toFloat())

        for (i in 1 until gpsSamples.size) {
            val prev = gpsSamples[i - 1]
            val curr = gpsSamples[i]
            cumulativeDistanceM += haversineDistance(
                prev.latitude,
                prev.longitude,
                curr.latitude,
                curr.longitude
            )
            val distPct = ((cumulativeDistanceM / totalDistanceM) * 100.0).toFloat().coerceIn(0f, 100f)
            distPcts.add(distPct)
            elapsedSec.add(((curr.timestampNs - startTimeNs).coerceAtLeast(0L) / 1_000_000_000.0).toFloat())
        }

        // Ensure the profile always spans full 0..100% and full duration for robust split interpolation.
        val durationSec = durationMs / 1000f
        if (distPcts.lastOrNull()?.let { it < 99.5f } != false) {
            distPcts.add(100f)
            elapsedSec.add(durationSec)
        } else {
            val lastIndex = elapsedSec.lastIndex
            if (lastIndex >= 0) {
                elapsedSec[lastIndex] = maxOf(elapsedSec[lastIndex], durationSec)
            }
        }

        return createSeries(
            runId = runId,
            type = SeriesType.SPEED_TIME,
            values = elapsedSec,
            distPcts = distPcts
        )
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

        val sampleRate = resolveSampleRate(handle.accelSampleRate, accelSamples)
        val windowSamples = (WINDOW_SIZE_SEC * sampleRate).roundToInt().coerceAtLeast(1)
        val hopSamples = (HOP_SIZE_SEC * sampleRate).roundToInt().coerceAtLeast(1)
        val gyroCount = gyroSamples.size
        var gyroStartIdx = 0
        var gyroEndExclusive = 0

        var windowStart = 0
        while (windowStart < accelSamples.size) {
            val windowEnd = (windowStart + windowSamples).coerceAtMost(accelSamples.size)
            if (windowEnd - windowStart < 3) break
            val windowAccel = accelSamples.subList(windowStart, windowEnd)
            val windowStartNs = windowAccel.first().timestampNs
            val windowEndNs = windowAccel.last().timestampNs
            val centerTimeNs = windowAccel[windowAccel.size / 2].timestampNs
            val distPct = distMapping.getDistPct(centerTimeNs)

            while (gyroStartIdx < gyroCount && gyroSamples[gyroStartIdx].timestampNs < windowStartNs) {
                gyroStartIdx++
            }
            if (gyroEndExclusive < gyroStartIdx) {
                gyroEndExclusive = gyroStartIdx
            }
            while (gyroEndExclusive < gyroCount && gyroSamples[gyroEndExclusive].timestampNs <= windowEndNs) {
                gyroEndExclusive++
            }
            val windowGyro = if (gyroStartIdx < gyroEndExclusive) {
                gyroSamples.subList(gyroStartIdx, gyroEndExclusive)
            } else {
                emptyList()
            }

            val impact = impactAnalyzer.analyzeWindow(windowAccel)
            val harshness = harshnessAnalyzer.analyzeWindow(windowAccel, sampleRate)
            val stability = stabilityAnalyzer.analyzeWindow(windowGyro)

            impactDensities.add(impact)
            harshnessValues.add(harshness)
            stabilityValues.add(stability.stabilityIndex)
            distPcts.add(distPct)

            if (windowEnd >= accelSamples.size) break
            windowStart += hopSamples
        }

        return WindowResults(impactDensities, harshnessValues, stabilityValues, distPcts)
    }

    private fun detectEvents(
        accelSamples: List<com.dhmeter.sensing.data.AccelSample>,
        distMapping: DistanceMapping,
        handle: RawCaptureHandle
    ): List<RunEvent> {
        val startTimeNs = handle.startTimeNs
        val sampleRate = resolveSampleRate(handle.accelSampleRate, accelSamples)

        val events = mutableListOf<RunEvent>()

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

        val landingTimesNs = landings.map { it.timestampMs * 1_000_000L }
        events += detectImpactPeakEvents(
            accelSamples = accelSamples,
            sampleRate = sampleRate,
            distMapping = distMapping,
            startTimeNs = startTimeNs,
            landingTimesNs = landingTimesNs
        )
        events += detectHarshnessBurstEvents(
            accelSamples = accelSamples,
            sampleRate = sampleRate,
            distMapping = distMapping,
            startTimeNs = startTimeNs
        )

        return events.sortedBy { it.distPct }
    }

    private fun detectImpactPeakEvents(
        accelSamples: List<com.dhmeter.sensing.data.AccelSample>,
        sampleRate: Float,
        distMapping: DistanceMapping,
        startTimeNs: Long,
        landingTimesNs: List<Long>
    ): List<RunEvent> {
        val peaks = impactAnalyzer.detectPeaks(accelSamples, sampleRate)
        if (peaks.isEmpty()) return emptyList()

        val impactEvents = mutableListOf<RunEvent>()
        val debounceNs = IMPACT_EVENT_DEBOUNCE_MS * 1_000_000L
        val nearLandingNs = IMPACT_NEAR_LANDING_MS * 1_000_000L
        val sortedLandingTimes = landingTimesNs.sorted()
        var lastImpactNs = Long.MIN_VALUE

        peaks.forEach { peak ->
            val timestampNs = peak.timestampNs
            if (timestampNs - lastImpactNs < debounceNs) return@forEach
            if (isTimestampNearAny(sortedLandingTimes, timestampNs, nearLandingNs)) return@forEach

            val timeSec = ((timestampNs - startTimeNs).coerceAtLeast(0L)) / 1_000_000_000f
            val distPct = distMapping.getDistPct(timestampNs)
            val peakG = peak.peakG.coerceAtLeast(0f)

            impactEvents.add(
                RunEvent(
                    eventId = UUID.randomUUID().toString(),
                    runId = "",
                    type = EventType.IMPACT_PEAK,
                    distPct = distPct,
                    timeSec = timeSec,
                    severity = peakG,
                    meta = EventMeta(
                        peakG = peakG
                    )
                )
            )
            lastImpactNs = timestampNs
        }

        return impactEvents
    }

    private fun isTimestampNearAny(
        sortedTimestamps: List<Long>,
        targetNs: Long,
        toleranceNs: Long
    ): Boolean {
        if (sortedTimestamps.isEmpty()) return false

        var low = 0
        var high = sortedTimestamps.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            val value = sortedTimestamps[mid]
            when {
                value < targetNs -> low = mid + 1
                value > targetNs -> high = mid - 1
                else -> return true
            }
        }

        val leftIdx = high.coerceAtLeast(0)
        val rightIdx = low.coerceAtMost(sortedTimestamps.lastIndex)
        return abs(sortedTimestamps[leftIdx] - targetNs) <= toleranceNs ||
                abs(sortedTimestamps[rightIdx] - targetNs) <= toleranceNs
    }

    private fun detectHarshnessBurstEvents(
        accelSamples: List<AccelSample>,
        sampleRate: Float,
        distMapping: DistanceMapping,
        startTimeNs: Long
    ): List<RunEvent> {
        if (sampleRate <= 0f || accelSamples.size < 50) return emptyList()

        val windowSamples = (HARSHNESS_BURST_WINDOW_SEC * sampleRate).roundToInt().coerceAtLeast(30)
        val hopSamples = (HARSHNESS_BURST_HOP_SEC * sampleRate).roundToInt().coerceAtLeast(8)
        if (windowSamples >= accelSamples.size) return emptyList()

        val windows = mutableListOf<HarshnessWindow>()
        var startIdx = 0
        while (startIdx + windowSamples <= accelSamples.size) {
            val endIdxExclusive = startIdx + windowSamples
            val window = accelSamples.subList(startIdx, endIdxExclusive)
            val rms = harshnessAnalyzer.analyzeWindow(window, sampleRate)
            if (rms.isFinite() && rms > 0f) {
                windows.add(
                    HarshnessWindow(
                        startNs = window.first().timestampNs,
                        endNs = window.last().timestampNs,
                        rms = rms
                    )
                )
            }
            startIdx += hopSamples
        }

        if (windows.size < 3) return emptyList()

        val rmsValues = windows.map { it.rms }
        val p75 = percentile(rmsValues, 75)
        val p90 = percentile(rmsValues, 90)
        val burstThreshold = maxOf(0.25f, p75 + (p90 - p75) * 0.65f)
        if (!burstThreshold.isFinite() || burstThreshold <= 0f) return emptyList()

        val rawBursts = mutableListOf<HarshnessBurst>()
        var activeStartNs = -1L
        var activeEndNs = -1L
        var activePeakNs = -1L
        var activePeakRms = 0f

        fun flushActiveBurst() {
            if (activeStartNs < 0L || activeEndNs < activeStartNs) return
            val durationMs = ((activeEndNs - activeStartNs) / 1_000_000L).coerceAtLeast(0L)
            if (durationMs >= HARSHNESS_BURST_MIN_DURATION_MS && activePeakNs >= 0L) {
                rawBursts.add(
                    HarshnessBurst(
                        startNs = activeStartNs,
                        endNs = activeEndNs,
                        peakNs = activePeakNs,
                        peakRms = activePeakRms
                    )
                )
            }
            activeStartNs = -1L
            activeEndNs = -1L
            activePeakNs = -1L
            activePeakRms = 0f
        }

        windows.forEach { window ->
            if (window.rms >= burstThreshold) {
                if (activeStartNs < 0L) {
                    activeStartNs = window.startNs
                    activeEndNs = window.endNs
                    activePeakNs = window.centerNs
                    activePeakRms = window.rms
                } else {
                    activeEndNs = window.endNs
                    if (window.rms > activePeakRms) {
                        activePeakRms = window.rms
                        activePeakNs = window.centerNs
                    }
                }
            } else {
                flushActiveBurst()
            }
        }
        flushActiveBurst()

        if (rawBursts.isEmpty()) return emptyList()
        val mergedBursts = mergeHarshnessBursts(rawBursts)

        return mergedBursts.map { burst ->
            val durationMs = ((burst.endNs - burst.startNs) / 1_000_000L).coerceAtLeast(0L)
            val timeSec = ((burst.peakNs - startTimeNs).coerceAtLeast(0L)) / 1_000_000_000f
            val distPct = distMapping.getDistPct(burst.peakNs)
            val severity = ((burst.peakRms / burstThreshold) * 2f).coerceIn(1f, 6f)

            RunEvent(
                eventId = UUID.randomUUID().toString(),
                runId = "",
                type = EventType.HARSHNESS_BURST,
                distPct = distPct,
                timeSec = timeSec,
                severity = severity,
                meta = EventMeta(
                    rmsValue = burst.peakRms,
                    durationMs = durationMs.toInt()
                )
            )
        }
    }

    private fun mergeHarshnessBursts(bursts: List<HarshnessBurst>): List<HarshnessBurst> {
        if (bursts.isEmpty()) return emptyList()
        val sorted = bursts.sortedBy { it.startNs }
        val merged = mutableListOf<HarshnessBurst>()
        val mergeGapNs = HARSHNESS_BURST_MERGE_GAP_MS * 1_000_000L

        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.startNs - current.endNs <= mergeGapNs) {
                current = if (next.peakRms > current.peakRms) {
                    current.copy(
                        endNs = maxOf(current.endNs, next.endNs),
                        peakNs = next.peakNs,
                        peakRms = next.peakRms
                    )
                } else {
                    current.copy(endNs = maxOf(current.endNs, next.endNs))
                }
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        return merged
    }

    private fun resolveSampleRate(
        declaredSampleRate: Float,
        accelSamples: List<com.dhmeter.sensing.data.AccelSample>
    ): Float {
        if (declaredSampleRate.isFinite() && declaredSampleRate >= 5f) {
            return declaredSampleRate
        }
        if (accelSamples.size < 2) return 200f

        val durationNs = (accelSamples.last().timestampNs - accelSamples.first().timestampNs).coerceAtLeast(1L)
        val intervals = (accelSamples.size - 1).coerceAtLeast(1)
        val estimated = intervals * 1_000_000_000f / durationNs.toFloat()
        return if (estimated.isFinite()) {
            estimated.coerceIn(20f, 500f)
        } else {
            200f
        }
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

        val gpsSensitivity = sensitivityRepository.currentSettings.gpsSensitivity
        val minSpeedMps = 0.5f
        val maxAccuracyM = (20f / gpsSensitivity.coerceAtLeast(0.01f)).coerceIn(10f, 60f)
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

private data class HarshnessWindow(
    val startNs: Long,
    val endNs: Long,
    val rms: Float
) {
    val centerNs: Long
        get() = startNs + (endNs - startNs) / 2L
}

private data class HarshnessBurst(
    val startNs: Long,
    val endNs: Long,
    val peakNs: Long,
    val peakRms: Float
)
