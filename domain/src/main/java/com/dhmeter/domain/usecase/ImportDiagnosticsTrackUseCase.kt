package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.EventMeta
import com.dhmeter.domain.model.GpsPoint
import com.dhmeter.domain.model.GpsPolyline
import com.dhmeter.domain.model.GpsQuality
import com.dhmeter.domain.model.MapGpsQuality
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SeriesType
import com.dhmeter.domain.model.Track
import com.dhmeter.domain.model.XAxisType
import com.dhmeter.domain.repository.RunRepository
import com.dhmeter.domain.repository.TrackRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

data class ImportedDiagnosticsTrackResult(
    val track: Track,
    val trackCount: Int,
    val runCount: Int,
    val seriesCount: Int,
    val eventCount: Int,
    val polylinePointCount: Int
)

class ImportDiagnosticsTrackUseCase @Inject constructor(
    private val trackRepository: TrackRepository,
    private val runRepository: RunRepository
) {

    suspend operator fun invoke(payload: String): Result<ImportedDiagnosticsTrackResult> {
        return runCatching {
            val parsedPayloads = parsePayloads(payload)
            require(parsedPayloads.isNotEmpty()) { "Import JSON has no runs to import." }
            val insertedTrackIds = mutableSetOf<String>()

            parsedPayloads.forEach { parsed ->
                if (insertedTrackIds.add(parsed.track.id)) {
                    trackRepository.insertTrack(parsed.track)
                }
                runRepository.insertRun(parsed.run)
                parsed.series.forEach { series ->
                    runRepository.insertSeries(series)
                }
                if (parsed.events.isNotEmpty()) {
                    runRepository.insertEvents(parsed.events)
                }
                parsed.polyline?.let { polyline ->
                    runRepository.insertGpsPolyline(polyline)
                }
            }

            val firstTrack = parsedPayloads.first().track
            val uniqueTrackCount = parsedPayloads
                .map { it.track.id }
                .distinct()
                .size

            ImportedDiagnosticsTrackResult(
                track = firstTrack,
                trackCount = uniqueTrackCount,
                runCount = parsedPayloads.size,
                seriesCount = parsedPayloads.sumOf { it.series.size },
                eventCount = parsedPayloads.sumOf { it.events.size },
                polylinePointCount = parsedPayloads.sumOf { it.polyline?.points?.size ?: 0 }
            )
        }
    }

    private fun parsePayloads(payload: String): List<ParsedImportPayload> {
        val trimmedPayload = payload.trim()
        require(trimmedPayload.isNotEmpty()) { "Import JSON is empty." }

        if (trimmedPayload.startsWith("[")) {
            val payloadArray = JSONArray(trimmedPayload)
            val result = mutableListOf<ParsedImportPayload>()
            for (index in 0 until payloadArray.length()) {
                val item = payloadArray.optJSONObject(index) ?: continue
                result += parseSinglePayload(item)
            }
            return result
        }

        val root = JSONObject(trimmedPayload)
        val bulkTracks = root.optJSONArray("tracks")
        return if (bulkTracks != null) {
            parseBulkPayload(root, bulkTracks)
        } else {
            listOf(parseSinglePayload(root))
        }
    }

    private fun parseBulkPayload(
        root: JSONObject,
        tracksArray: JSONArray
    ): List<ParsedImportPayload> {
        val result = mutableListOf<ParsedImportPayload>()
        val now = System.currentTimeMillis()
        val exportedAt = root.optLong("exportedAtEpochMs").takeIf { it > 0L } ?: now

        for (trackIndex in 0 until tracksArray.length()) {
            val trackEntry = tracksArray.optJSONObject(trackIndex) ?: continue
            val trackObject = trackEntry.optJSONObject("track")
            val runsArray = trackEntry.optJSONArray("runs") ?: continue

            val sourceTrackName = trackObject.optNullableString("trackName")
                ?.takeIf { it.isNotBlank() }
                ?: "Imported track"
            val sourceTrackId = trackObject.optNullableString("trackId")
            val importedTrackId = UUID.randomUUID().toString()

            val track = Track(
                id = importedTrackId,
                name = buildImportedTrackName(sourceTrackName),
                createdAt = trackObject?.optLong("createdAt")?.takeIf { it > 0L } ?: exportedAt,
                locationHint = trackObject.optNullableString("locationHint"),
                notes = buildTrackNotes(sourceTrackId = sourceTrackId)
            )

            for (runIndex in 0 until runsArray.length()) {
                val runEntry = runsArray.optJSONObject(runIndex) ?: continue
                val runObject = runEntry.optJSONObject("run") ?: continue
                val eventsArray = runEntry.optJSONArray("events")
                val seriesArray = runEntry.optJSONArray("series")
                val polylineObject = runEntry.optJSONObject("mapData")?.optJSONObject("polyline")
                val importedRunId = UUID.randomUUID().toString()

                val run = parseRun(runObject, importedTrackId, importedRunId, now)
                val series = parseSeries(seriesArray, importedRunId)
                val events = parseEvents(eventsArray, importedRunId)
                val polyline = parsePolyline(
                    polylineObject = polylineObject,
                    runId = importedRunId,
                    fallbackDistanceMeters = run.distanceMeters,
                    fallbackGpsQuality = run.gpsQuality
                )

                result += ParsedImportPayload(
                    track = track,
                    run = run,
                    series = series,
                    events = events,
                    polyline = polyline
                )
            }
        }

        return result
    }

    private fun parseSinglePayload(root: JSONObject): ParsedImportPayload {
        val now = System.currentTimeMillis()
        val runObject = root.optJSONObject("run")
            ?: throw IllegalArgumentException("Import JSON does not contain 'run'.")
        val trackObject = root.optJSONObject("track")
        val eventsArray = root.optJSONArray("events")
        val seriesArray = root.optJSONArray("series")
        val polylineObject = root.optJSONObject("mapData")?.optJSONObject("polyline")

        val sourceTrackName = trackObject.optNullableString("trackName")
            ?.takeIf { it.isNotBlank() }
            ?: "Imported track"
        val sourceTrackId = trackObject.optNullableString("trackId")
        val sourceRunId = runObject.optNullableString("runId")

        val importedTrackId = UUID.randomUUID().toString()
        val importedRunId = UUID.randomUUID().toString()
        val createdAt = root.optLong("exportedAtEpochMs").takeIf { it > 0L } ?: now

        val track = Track(
            id = importedTrackId,
            name = buildImportedTrackName(sourceTrackName),
            createdAt = createdAt,
            locationHint = trackObject.optNullableString("locationHint"),
            notes = buildTrackNotes(
                sourceTrackId = sourceTrackId,
                sourceRunId = sourceRunId
            )
        )

        val run = parseRun(runObject, importedTrackId, importedRunId, now)
        val series = parseSeries(seriesArray, importedRunId)
        val events = parseEvents(eventsArray, importedRunId)
        val polyline = parsePolyline(
            polylineObject = polylineObject,
            runId = importedRunId,
            fallbackDistanceMeters = run.distanceMeters,
            fallbackGpsQuality = run.gpsQuality
        )

        return ParsedImportPayload(
            track = track,
            run = run,
            series = series,
            events = events,
            polyline = polyline
        )
    }

    private fun buildTrackNotes(
        sourceTrackId: String?,
        sourceRunId: String? = null
    ): String {
        return listOfNotNull(
            "Imported diagnostics JSON",
            sourceTrackId?.let { "sourceTrackId=$it" },
            sourceRunId?.let { "sourceRunId=$it" }
        ).joinToString(" | ")
    }

    private fun parseRun(
        runObject: JSONObject,
        trackId: String,
        runId: String,
        defaultTimestamp: Long
    ): Run {
        val startedAt = runObject.optLong("startedAt").takeIf { it > 0L } ?: defaultTimestamp
        val rawDuration = runObject.optLong("durationMs").takeIf { it > 0L }
        val rawEndedAt = runObject.optLong("endedAt").takeIf { it >= startedAt }
        val durationMs = rawDuration ?: rawEndedAt?.minus(startedAt)?.takeIf { it > 0L } ?: 1L
        val endedAt = rawEndedAt ?: (startedAt + max(1L, durationMs))

        return Run(
            runId = runId,
            trackId = trackId,
            startedAt = startedAt,
            endedAt = endedAt,
            durationMs = max(1L, durationMs),
            isValid = runObject.optBoolean("isValid", true),
            invalidReason = runObject.optNullableString("invalidReason"),
            phonePlacement = runObject.optNullableString("phonePlacement") ?: "POCKET_THIGH",
            deviceModel = runObject.optNullableString("deviceModel") ?: "IMPORTED",
            sampleRateAccelHz = runObject.optFiniteFloat("sampleRateAccelHz")?.takeIf { it > 0f } ?: 100f,
            sampleRateGyroHz = runObject.optFiniteFloat("sampleRateGyroHz")?.takeIf { it > 0f } ?: 100f,
            sampleRateBaroHz = runObject.optFiniteFloat("sampleRateBaroHz"),
            gpsQuality = parseGpsQuality(runObject.optNullableString("gpsQuality")),
            distanceMeters = runObject.optFiniteFloat("distanceMeters")?.takeIf { it >= 0f },
            pauseCount = runObject.optInt("pauseCount", 0).coerceAtLeast(0),
            impactScore = runObject.optFiniteFloat("impactScore"),
            harshnessAvg = runObject.optFiniteFloat("harshnessAvg"),
            harshnessP90 = runObject.optFiniteFloat("harshnessP90"),
            stabilityScore = runObject.optFiniteFloat("stabilityScore"),
            landingQualityScore = runObject.optFiniteFloat("landingQualityScore"),
            avgSpeed = runObject.optFiniteFloat("avgSpeedMps"),
            maxSpeed = runObject.optFiniteFloat("maxSpeedMps"),
            slopeClassAvg = runObject.optNullableInt("slopeClassAvg"),
            setupNote = runObject.optNullableString("setupNote"),
            conditionsNote = runObject.optNullableString("conditionsNote")
        )
    }

    private fun parseSeries(seriesArray: JSONArray?, runId: String): List<RunSeries> {
        if (seriesArray == null) return emptyList()
        val bySeriesType = LinkedHashMap<SeriesType, RunSeries>()

        for (index in 0 until seriesArray.length()) {
            val seriesObject = seriesArray.optJSONObject(index) ?: continue
            val seriesType = parseEnum<SeriesType>(seriesObject.optNullableString("seriesType")) ?: continue
            if (bySeriesType.containsKey(seriesType)) continue

            val xType = parseEnum<XAxisType>(seriesObject.optNullableString("xType")) ?: XAxisType.DIST_PCT
            val points = parseSeriesPoints(seriesObject.optJSONArray("points"))
            if (points.isEmpty()) continue

            bySeriesType[seriesType] = RunSeries(
                runId = runId,
                seriesType = seriesType,
                xType = xType,
                points = points,
                pointCount = points.size / 2
            )
        }

        return bySeriesType.values.toList()
    }

    private fun parseSeriesPoints(pointsArray: JSONArray?): FloatArray {
        if (pointsArray == null || pointsArray.length() == 0) return FloatArray(0)
        val values = ArrayList<Float>(pointsArray.length() * 2)
        for (index in 0 until pointsArray.length()) {
            val point = pointsArray.optJSONObject(index) ?: continue
            val x = point.optFiniteFloat("x") ?: continue
            val y = point.optFiniteFloat("y") ?: continue
            if (!x.isFinite() || !y.isFinite()) continue
            values += x
            values += y
        }
        return values.toFloatArray()
    }

    private fun parseEvents(eventsArray: JSONArray?, runId: String): List<RunEvent> {
        if (eventsArray == null) return emptyList()
        val result = ArrayList<RunEvent>(eventsArray.length())

        for (index in 0 until eventsArray.length()) {
            val eventObject = eventsArray.optJSONObject(index) ?: continue
            val type = eventObject.optNullableString("type")?.takeIf { it.isNotBlank() } ?: continue
            val distPct = (eventObject.optFiniteFloat("distPct") ?: continue).coerceIn(0f, 100f)
            val timeSec = (eventObject.optFiniteFloat("timeSec") ?: 0f).coerceAtLeast(0f)
            val severity = eventObject.optFiniteFloat("severity") ?: 0f

            result += RunEvent(
                eventId = UUID.randomUUID().toString(),
                runId = runId,
                type = type,
                distPct = distPct,
                timeSec = timeSec,
                severity = severity,
                meta = parseEventMeta(eventObject.optJSONObject("meta"))
            )
        }

        return result
    }

    private fun parseEventMeta(metaObject: JSONObject?): EventMeta? {
        if (metaObject == null) return null

        val peakG = metaObject.optFiniteFloat("peakG")
        val energy300ms = metaObject.optFiniteFloat("energy300ms")
        val recoveryMs = metaObject.optNullableInt("recoveryMs")
        val rmsValue = metaObject.optFiniteFloat("rmsValue")
        val durationMs = metaObject.optNullableInt("durationMs")

        if (
            peakG == null &&
            energy300ms == null &&
            recoveryMs == null &&
            rmsValue == null &&
            durationMs == null
        ) {
            return null
        }

        return EventMeta(
            peakG = peakG,
            energy300ms = energy300ms,
            recoveryMs = recoveryMs,
            rmsValue = rmsValue,
            durationMs = durationMs
        )
    }

    private fun parsePolyline(
        polylineObject: JSONObject?,
        runId: String,
        fallbackDistanceMeters: Float?,
        fallbackGpsQuality: GpsQuality
    ): GpsPolyline? {
        if (polylineObject == null) return null
        val pointsArray = polylineObject.optJSONArray("points") ?: return null
        if (pointsArray.length() < 2) return null

        val points = ArrayList<GpsPoint>(pointsArray.length())
        for (index in 0 until pointsArray.length()) {
            val pointObject = pointsArray.optJSONObject(index) ?: continue
            val lat = pointObject.optFiniteDouble("lat") ?: continue
            val lon = pointObject.optFiniteDouble("lon") ?: continue
            val distPct = (pointObject.optFiniteFloat("distPct") ?: continue).coerceIn(0f, 100f)
            val altitude = pointObject.optFiniteFloat("altitudeM")
            points += GpsPoint(
                lat = lat,
                lon = lon,
                distPct = distPct,
                altitudeM = altitude
            )
        }

        if (points.size < 2) return null

        val sortedPoints = points.sortedBy { it.distPct }
        val totalDistanceM = polylineObject.optFiniteFloat("totalDistanceM")
            ?.takeIf { it >= 0f }
            ?: fallbackDistanceMeters
            ?: 0f
        val avgAccuracyM = polylineObject.optFiniteFloat("avgAccuracyM")
            ?.takeIf { it >= 0f }
            ?: 15f
        val mapGpsQuality = parseMapGpsQuality(polylineObject.optNullableString("gpsQuality"))
            ?: fallbackGpsQuality.toMapGpsQuality()

        return GpsPolyline(
            runId = runId,
            points = sortedPoints,
            totalDistanceM = totalDistanceM,
            avgAccuracyM = avgAccuracyM,
            gpsQuality = mapGpsQuality
        )
    }

    private fun parseGpsQuality(value: String?): GpsQuality {
        return parseEnum<GpsQuality>(value) ?: GpsQuality.GOOD
    }

    private fun parseMapGpsQuality(value: String?): MapGpsQuality? {
        return parseEnum<MapGpsQuality>(value)
    }

    private fun GpsQuality.toMapGpsQuality(): MapGpsQuality {
        return when (this) {
            GpsQuality.EXCELLENT,
            GpsQuality.GOOD -> MapGpsQuality.GOOD
            GpsQuality.FAIR,
            GpsQuality.MEDIUM -> MapGpsQuality.OK
            GpsQuality.POOR -> MapGpsQuality.POOR
        }
    }

    private fun buildImportedTrackName(originalName: String): String {
        return if (originalName.contains("import", ignoreCase = true)) {
            originalName
        } else {
            "$originalName (imported)"
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String?): T? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return enumValues<T>().firstOrNull { it.name.equals(normalized, ignoreCase = true) }
    }

    private fun JSONObject?.optNullableString(key: String): String? {
        if (this == null || isNull(key)) return null
        return optString(key)
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?.trim()
    }

    private fun JSONObject.optFiniteFloat(key: String): Float? {
        if (isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        if (!value.isFinite()) return null
        return value.toFloat().takeIf { it.isFinite() }
    }

    private fun JSONObject.optFiniteDouble(key: String): Double? {
        if (isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (isNull(key)) return null
        return optInt(key)
    }

    private data class ParsedImportPayload(
        val track: Track,
        val run: Run,
        val series: List<RunSeries>,
        val events: List<RunEvent>,
        val polyline: GpsPolyline?
    )
}
