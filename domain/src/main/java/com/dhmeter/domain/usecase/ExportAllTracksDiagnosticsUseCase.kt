package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.EventMeta
import com.dhmeter.domain.model.GpsPoint
import com.dhmeter.domain.model.GpsPolyline
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.Track
import com.dhmeter.domain.repository.RunRepository
import com.dhmeter.domain.repository.TrackRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ExportAllTracksDiagnosticsUseCase @Inject constructor(
    private val trackRepository: TrackRepository,
    private val runRepository: RunRepository
) {

    suspend operator fun invoke(): Result<String> = runCatching {
        val tracks = trackRepository.getAllTracks().first()
            .sortedBy { it.createdAt }

        val payload = JSONObject().apply {
            put("schemaVersion", 2)
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("exportType", "all_tracks")
            put(
                "tracks",
                JSONArray().apply {
                    tracks.forEach { track ->
                        put(trackWithRunsToJson(track))
                    }
                }
            )
        }

        payload.toString(2)
    }

    private suspend fun trackWithRunsToJson(track: Track): JSONObject {
        val runs = runRepository.getRunsByTrack(track.id).first()
            .sortedBy { it.startedAt }

        return JSONObject().apply {
            put("track", trackToJson(track))
            put(
                "runs",
                JSONArray().apply {
                    runs.forEach { run ->
                        val series = runRepository.getAllSeries(run.runId)
                        val events = runRepository.getEvents(run.runId)
                        val polyline = runRepository.getGpsPolyline(run.runId)
                        put(
                            JSONObject().apply {
                                put("run", runToJson(run))
                                put("events", eventsToJson(events))
                                put("series", seriesListToJson(series))
                                put("mapData", mapDataToJson(polyline))
                            }
                        )
                    }
                }
            )
        }
    }

    private fun trackToJson(track: Track): JSONObject = JSONObject().apply {
        put("trackId", track.id)
        put("trackName", track.name)
        put("createdAt", track.createdAt)
        put("locationHint", track.locationHint ?: JSONObject.NULL)
        put("notes", track.notes ?: JSONObject.NULL)
    }

    private fun runToJson(run: Run): JSONObject = JSONObject().apply {
        put("runId", run.runId)
        put("trackId", run.trackId)
        put("startedAt", run.startedAt)
        put("endedAt", run.endedAt)
        put("durationMs", run.durationMs)
        put("isValid", run.isValid)
        put("invalidReason", run.invalidReason ?: JSONObject.NULL)
        put("phonePlacement", run.phonePlacement)
        put("deviceModel", run.deviceModel)
        put("sampleRateAccelHz", run.sampleRateAccelHz.toDouble())
        put("sampleRateGyroHz", run.sampleRateGyroHz.toDouble())
        put("sampleRateBaroHz", run.sampleRateBaroHz?.toDouble() ?: JSONObject.NULL)
        put("gpsQuality", run.gpsQuality.name)
        put("distanceMeters", run.distanceMeters?.toDouble() ?: JSONObject.NULL)
        put("pauseCount", run.pauseCount)
        put("impactScore", run.impactScore?.toDouble() ?: JSONObject.NULL)
        put("harshnessAvg", run.harshnessAvg?.toDouble() ?: JSONObject.NULL)
        put("harshnessP90", run.harshnessP90?.toDouble() ?: JSONObject.NULL)
        put("stabilityScore", run.stabilityScore?.toDouble() ?: JSONObject.NULL)
        put("landingQualityScore", run.landingQualityScore?.toDouble() ?: JSONObject.NULL)
        put("avgSpeedMps", run.avgSpeed?.toDouble() ?: JSONObject.NULL)
        put("maxSpeedMps", run.maxSpeed?.toDouble() ?: JSONObject.NULL)
        put("setupNote", run.setupNote ?: JSONObject.NULL)
        put("conditionsNote", run.conditionsNote ?: JSONObject.NULL)
    }

    private fun eventsToJson(events: List<RunEvent>): JSONArray = JSONArray().apply {
        events.forEach { event ->
            put(
                JSONObject().apply {
                    put("eventId", event.eventId)
                    put("type", event.type)
                    put("distPct", event.distPct.toDouble())
                    put("timeSec", event.timeSec.toDouble())
                    put("severity", event.severity.toDouble())
                    put("meta", eventMetaToJson(event.meta))
                }
            )
        }
    }

    private fun eventMetaToJson(meta: EventMeta?): JSONObject {
        if (meta == null) return JSONObject()
        return JSONObject().apply {
            put("peakG", meta.peakG?.toDouble() ?: JSONObject.NULL)
            put("energy300ms", meta.energy300ms?.toDouble() ?: JSONObject.NULL)
            put("recoveryMs", meta.recoveryMs ?: JSONObject.NULL)
            put("rmsValue", meta.rmsValue?.toDouble() ?: JSONObject.NULL)
            put("durationMs", meta.durationMs ?: JSONObject.NULL)
        }
    }

    private fun seriesListToJson(seriesList: List<RunSeries>): JSONArray = JSONArray().apply {
        seriesList.forEach { series ->
            put(
                JSONObject().apply {
                    put("seriesType", series.seriesType.name)
                    put("xType", series.xType.name)
                    put("pointCount", series.effectivePointCount)
                    put("points", seriesPointsToJson(series))
                }
            )
        }
    }

    private fun seriesPointsToJson(series: RunSeries): JSONArray = JSONArray().apply {
        for (idx in 0 until series.effectivePointCount) {
            val (x, y) = series.getPoint(idx)
            put(
                JSONObject().apply {
                    put("x", x.toDouble())
                    put("y", y.toDouble())
                }
            )
        }
    }

    private fun mapDataToJson(polyline: GpsPolyline?): JSONObject {
        if (polyline == null) return JSONObject()
        return JSONObject().apply {
            put(
                "polyline",
                JSONObject().apply {
                    put("totalDistanceM", polyline.totalDistanceM.toDouble())
                    put("avgAccuracyM", polyline.avgAccuracyM.toDouble())
                    put("gpsQuality", polyline.gpsQuality.name)
                    put(
                        "points",
                        JSONArray().apply {
                            polyline.points.forEach { point ->
                                put(gpsPointToJson(point))
                            }
                        }
                    )
                }
            )
        }
    }

    private fun gpsPointToJson(point: GpsPoint): JSONObject = JSONObject().apply {
        put("lat", point.lat)
        put("lon", point.lon)
        put("distPct", point.distPct.toDouble())
        put("altitudeM", point.altitudeM?.toDouble() ?: JSONObject.NULL)
    }
}

