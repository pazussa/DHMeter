package com.dropindh.app.ui.screens.runsummary

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropindh.app.BuildConfig
import com.dropindh.app.ui.i18n.tr
import com.dhmeter.domain.model.ElevationProfile
import com.dhmeter.domain.model.EventMeta
import com.dhmeter.domain.model.GpsPoint
import com.dhmeter.domain.model.GpsQuality
import com.dhmeter.domain.model.MapSegment
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.model.RunMapData
import com.dhmeter.domain.model.RunSeries
import com.dhmeter.domain.model.SeriesType
import com.dhmeter.domain.model.Run
import com.dhmeter.domain.usecase.GetComparableRunsUseCase
import com.dhmeter.domain.usecase.GetRunMapDataUseCase
import com.dhmeter.domain.usecase.GetRunByIdUseCase
import com.dhmeter.domain.usecase.GetRunEventsUseCase
import com.dhmeter.domain.usecase.GetRunSeriesUseCase
import com.dhmeter.domain.usecase.GetTrackByIdUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

data class RunSummaryUiState(
    val run: Run? = null,
    val comparableRuns: List<Run> = emptyList(),
    val impactSeries: RunSeries? = null,
    val harshnessSeries: RunSeries? = null,
    val stabilitySeries: RunSeries? = null,
    val speedSeries: RunSeries? = null,
    val mapData: RunMapData? = null,
    val elevationProfile: ElevationProfile? = null,
    val events: List<RunEvent> = emptyList(),
    val isChartsLoading: Boolean = false,
    val chartsError: String? = null,
    val isExportingDiagnostics: Boolean = false,
    val exportMessage: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class RunSummaryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getRunByIdUseCase: GetRunByIdUseCase,
    private val getTrackByIdUseCase: GetTrackByIdUseCase,
    private val getComparableRunsUseCase: GetComparableRunsUseCase,
    private val getRunSeriesUseCase: GetRunSeriesUseCase,
    private val getRunMapDataUseCase: GetRunMapDataUseCase,
    private val getRunEventsUseCase: GetRunEventsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunSummaryUiState())
    val uiState: StateFlow<RunSummaryUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var compareJob: Job? = null
    private var chartsJob: Job? = null

    fun loadRun(runId: String) {
        loadJob?.cancel()
        compareJob?.cancel()
        chartsJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    run = null,
                    isLoading = true,
                    error = null,
                    comparableRuns = emptyList(),
                    impactSeries = null,
                    harshnessSeries = null,
                    stabilitySeries = null,
                    speedSeries = null,
                    mapData = null,
                    elevationProfile = null,
                    events = emptyList(),
                    isChartsLoading = false,
                    chartsError = null,
                    isExportingDiagnostics = false,
                    exportMessage = null
                )
            }
            
            getRunByIdUseCase(runId)
                .onSuccess { run ->
                    _uiState.update { it.copy(run = run, isLoading = false, isChartsLoading = true) }
                    
                    // Load comparable runs
                    compareJob = viewModelScope.launch {
                        loadComparableRuns(run.trackId, runId)
                    }

                    chartsJob = viewModelScope.launch {
                        loadRunCharts(runId)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(run = null, error = e.message, isLoading = false) }
                }
        }
    }

    private suspend fun loadComparableRuns(trackId: String, currentRunId: String) {
        getComparableRunsUseCase(trackId, currentRunId)
            .onSuccess { runs ->
                _uiState.update { it.copy(comparableRuns = runs) }
            }
    }

    private suspend fun loadRunCharts(runId: String) {
        _uiState.update { it.copy(isChartsLoading = true, chartsError = null) }

        try {
            coroutineScope {
                val impactDeferred = async {
                    getRunSeriesUseCase(runId, SeriesType.IMPACT_DENSITY).getOrNull()
                }
                val harshnessDeferred = async {
                    getRunSeriesUseCase(runId, SeriesType.HARSHNESS).getOrNull()
                }
                val stabilityDeferred = async {
                    getRunSeriesUseCase(runId, SeriesType.STABILITY).getOrNull()
                }
                val speedDeferred = async {
                    getRunSeriesUseCase(runId, SeriesType.SPEED_TIME).getOrNull()
                }
                val mapDataDeferred = async {
                    getRunMapDataUseCase(runId).getOrNull()
                }
                val eventsDeferred = async {
                    getRunEventsUseCase(runId).getOrDefault(emptyList())
                }

                val mapData = mapDataDeferred.await()
                _uiState.update {
                    it.copy(
                        impactSeries = impactDeferred.await(),
                        harshnessSeries = harshnessDeferred.await(),
                        stabilitySeries = stabilityDeferred.await(),
                        speedSeries = speedDeferred.await(),
                        mapData = mapData,
                        elevationProfile = mapData?.elevationProfile,
                        events = eventsDeferred.await(),
                        isChartsLoading = false,
                        chartsError = null
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isChartsLoading = false,
                    chartsError = e.message ?: tr(
                        appContext,
                        "Failed to load charts",
                        "No se pudieron cargar las gráficas"
                    )
                )
            }
        }
    }

    fun exportRunDiagnosticsJson() {
        val runId = _uiState.value.run?.runId ?: return
        if (_uiState.value.isExportingDiagnostics) return

        viewModelScope.launch {
            _uiState.update { it.copy(isExportingDiagnostics = true, exportMessage = null) }

            val result = runCatching {
                val jsonPayload = buildRunDiagnosticsJson(runId)
                writeDiagnosticsJsonToStorage(runId, jsonPayload)
            }

            _uiState.update { state ->
                state.copy(
                    isExportingDiagnostics = false,
                    exportMessage = result.fold(
                        onSuccess = { destination ->
                            tr(
                                appContext,
                                "Diagnostics JSON exported: $destination",
                                "JSON de diagnóstico exportado: $destination"
                            )
                        },
                        onFailure = { error ->
                            tr(
                                appContext,
                                "Failed to export diagnostics JSON: ${error.message ?: "unknown error"}",
                                "No se pudo exportar el JSON de diagnóstico: ${error.message ?: "error desconocido"}"
                            )
                        }
                    )
                )
            }
        }
    }

    fun consumeExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    private suspend fun buildRunDiagnosticsJson(runId: String): String = coroutineScope {
        val run = getRunByIdUseCase(runId).getOrThrow()
        val track = getTrackByIdUseCase(run.trackId).getOrNull()

        val impactDeferred = async { getRunSeriesUseCase(runId, SeriesType.IMPACT_DENSITY).getOrNull() }
        val harshnessDeferred = async { getRunSeriesUseCase(runId, SeriesType.HARSHNESS).getOrNull() }
        val stabilityDeferred = async { getRunSeriesUseCase(runId, SeriesType.STABILITY).getOrNull() }
        val speedDeferred = async { getRunSeriesUseCase(runId, SeriesType.SPEED_TIME).getOrNull() }
        val mapDataDeferred = async { getRunMapDataUseCase(runId).getOrNull() }
        val eventsDeferred = async { getRunEventsUseCase(runId).getOrDefault(emptyList()) }

        val impactSeries = impactDeferred.await()
        val harshnessSeries = harshnessDeferred.await()
        val stabilitySeries = stabilityDeferred.await()
        val speedSeries = speedDeferred.await()
        val mapData = mapDataDeferred.await()
        val events = eventsDeferred.await()
        val availableSeries = listOfNotNull(impactSeries, harshnessSeries, stabilitySeries, speedSeries)

        val diagnostics = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("appVersionName", BuildConfig.VERSION_NAME)
            put("appVersionCode", BuildConfig.VERSION_CODE)
            put("deviceModel", Build.MODEL)
            put("androidSdkInt", Build.VERSION.SDK_INT)

            put("run", runToJson(run))
            put(
                "track",
                JSONObject().apply {
                    put("trackId", run.trackId)
                    put("trackName", track?.name ?: "")
                    put("locationHint", track?.locationHint ?: "")
                }
            )
            put("events", eventsToJson(events))
            put("series", seriesListToJson(availableSeries))
            put("mapData", mapDataToJson(mapData))
            put(
                "diagnosticFlags",
                buildDiagnosticFlags(
                    run = run,
                    events = events,
                    series = availableSeries,
                    mapData = mapData
                )
            )
        }

        diagnostics.toString(2)
    }

    private fun writeDiagnosticsJsonToStorage(runId: String, payload: String): String {
        val fileName = "dhmeter_run_${runId.take(8)}_${System.currentTimeMillis()}.json"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DHMeter")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.toByteArray(Charsets.UTF_8))
                } ?: error("Failed to open output stream")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Download/DHMeter/$fileName"
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        } else {
            val targetDir = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "DHMeter"
            ).apply { mkdirs() }
            val outFile = File(targetDir, fileName)
            outFile.writeText(payload)
            outFile.absolutePath
        }
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

    private fun mapDataToJson(mapData: RunMapData?): JSONObject {
        if (mapData == null) return JSONObject()
        return JSONObject().apply {
            put("runId", mapData.runId)
            put("activeMetric", mapData.activeMetric.name)
            put(
                "polyline",
                JSONObject().apply {
                    put("totalDistanceM", mapData.polyline.totalDistanceM.toDouble())
                    put("avgAccuracyM", mapData.polyline.avgAccuracyM.toDouble())
                    put("gpsQuality", mapData.polyline.gpsQuality.name)
                    put(
                        "points",
                        JSONArray().apply {
                            mapData.polyline.points.forEach { point ->
                                put(gpsPointToJson(point))
                            }
                        }
                    )
                }
            )
            put(
                "segments",
                JSONArray().apply {
                    mapData.segments.forEach { segment ->
                        put(mapSegmentToJson(segment))
                    }
                }
            )
            put(
                "percentiles",
                JSONObject().apply {
                    put("p20", mapData.percentiles.p20.toDouble())
                    put("p40", mapData.percentiles.p40.toDouble())
                    put("p60", mapData.percentiles.p60.toDouble())
                    put("p80", mapData.percentiles.p80.toDouble())
                }
            )
            put(
                "elevationProfile",
                mapData.elevationProfile?.let { profile ->
                    JSONObject().apply {
                        put("totalDescentM", profile.totalDescentM.toDouble())
                        put("totalAscentM", profile.totalAscentM.toDouble())
                        put("minAltitudeM", profile.minAltitudeM.toDouble())
                        put("maxAltitudeM", profile.maxAltitudeM.toDouble())
                        put(
                            "points",
                            JSONArray().apply {
                                profile.points.forEach { p ->
                                    put(
                                        JSONObject().apply {
                                            put("distPct", p.distPct.toDouble())
                                            put("altitudeM", p.altitudeM.toDouble())
                                        }
                                    )
                                }
                            }
                        )
                    }
                } ?: JSONObject.NULL
            )
        }
    }

    private fun gpsPointToJson(point: GpsPoint): JSONObject = JSONObject().apply {
        put("lat", point.lat)
        put("lon", point.lon)
        put("distPct", point.distPct.toDouble())
        put("altitudeM", point.altitudeM?.toDouble() ?: JSONObject.NULL)
    }

    private fun mapSegmentToJson(segment: MapSegment): JSONObject = JSONObject().apply {
        put("start", gpsPointToJson(segment.start))
        put("end", gpsPointToJson(segment.end))
        put("distPct", segment.distPct.toDouble())
        put("severity", segment.severity.name)
    }

    private fun buildDiagnosticFlags(
        run: Run,
        events: List<RunEvent>,
        series: List<RunSeries>,
        mapData: RunMapData?
    ): JSONObject {
        val inferredIssues = mutableListOf<String>()
        if (!run.isValid) {
            inferredIssues.add("Run marked invalid: ${run.invalidReason ?: "unknown"}")
        }
        if (run.durationMs < 30_000L) {
            inferredIssues.add("Duration is below 30s (${run.durationMs}ms)")
        }
        if ((run.distanceMeters ?: 0f) < 250f) {
            inferredIssues.add("Distance is below 250m (${run.distanceMeters ?: 0f}m)")
        }
        if (run.gpsQuality == GpsQuality.FAIR || run.gpsQuality == GpsQuality.MEDIUM || run.gpsQuality == GpsQuality.POOR) {
            inferredIssues.add("GPS quality is ${run.gpsQuality.name}")
        }
        if (series.isEmpty() || series.any { it.effectivePointCount == 0 }) {
            inferredIssues.add("One or more metric series are empty")
        }
        if (mapData?.polyline?.points.isNullOrEmpty()) {
            inferredIssues.add("Map polyline has no points")
        }
        if (events.isEmpty()) {
            inferredIssues.add("No events detected in this run")
        }

        return JSONObject().apply {
            put("hasIssues", inferredIssues.isNotEmpty())
            put(
                "summary",
                JSONObject().apply {
                    put("eventCount", events.size)
                    put("seriesCount", series.size)
                    put("polylinePointCount", mapData?.polyline?.points?.size ?: 0)
                    put("gpsQuality", run.gpsQuality.name)
                    put("pauseCount", run.pauseCount)
                }
            )
            put(
                "inferredIssues",
                JSONArray().apply { inferredIssues.forEach(::put) }
            )
        }
    }
}
