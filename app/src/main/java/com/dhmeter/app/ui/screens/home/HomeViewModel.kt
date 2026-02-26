package com.dropindh.app.ui.screens.home

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropindh.app.monetization.EventTracker
import com.dropindh.app.ui.i18n.tr
import com.dhmeter.domain.model.Track
import com.dhmeter.domain.usecase.CreateTrackUseCase
import com.dhmeter.domain.usecase.ExportAllTracksDiagnosticsUseCase
import com.dhmeter.domain.usecase.GetTracksUseCase
import com.dhmeter.domain.usecase.ImportDiagnosticsTrackUseCase
import com.dhmeter.sensing.SensorAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SensorStatus(
    val hasAccelerometer: Boolean = false,
    val hasGyroscope: Boolean = false,
    val hasRotationVector: Boolean = false,
    val hasGps: Boolean = false
)

data class HomeUiState(
    val tracks: List<Track> = emptyList(),
    val sensorStatus: SensorStatus = SensorStatus(),
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val importMessage: String? = null,
    val exportMessage: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getTracksUseCase: GetTracksUseCase,
    private val createTrackUseCase: CreateTrackUseCase,
    private val importDiagnosticsTrackUseCase: ImportDiagnosticsTrackUseCase,
    private val exportAllTracksDiagnosticsUseCase: ExportAllTracksDiagnosticsUseCase,
    private val sensorAvailability: SensorAvailability,
    private val eventTracker: EventTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTracks()
        checkSensors()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            getTracksUseCase()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                .collect { tracks ->
                    _uiState.update { it.copy(tracks = tracks, isLoading = false) }
                }
        }
    }

    private fun checkSensors() {
        val status = SensorStatus(
            hasAccelerometer = sensorAvailability.hasAccelerometer(),
            hasGyroscope = sensorAvailability.hasGyroscope(),
            hasRotationVector = sensorAvailability.hasRotationVector(),
            hasGps = sensorAvailability.hasGps()
        )
        _uiState.update { it.copy(sensorStatus = status) }
    }

    fun createTrack(name: String, locationHint: String?) {
        viewModelScope.launch {
            createTrackUseCase(name, locationHint)
                .onSuccess {
                    eventTracker.trackOnboardingCompleteIfNeeded()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun importTrackFromDiagnostics(uri: Uri) {
        if (_uiState.value.isImporting || _uiState.value.isExporting) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    error = null,
                    importMessage = null,
                    exportMessage = null
                )
            }

            val result = runCatching {
                val payload = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error(
                    tr(
                        appContext,
                        "Could not open selected file.",
                        "No se pudo abrir el archivo seleccionado."
                    )
                )
                importDiagnosticsTrackUseCase(payload).getOrThrow()
            }

            _uiState.update { state ->
                state.copy(
                    isImporting = false,
                    importMessage = result.getOrNull()?.let { imported ->
                        val tracksLabel = if (imported.trackCount == 1) {
                            tr(appContext, "1 track", "1 track")
                        } else {
                            tr(
                                appContext,
                                "${imported.trackCount} tracks",
                                "${imported.trackCount} tracks"
                            )
                        }
                        val runsLabel = if (imported.runCount == 1) {
                            tr(appContext, "1 run", "1 bajada")
                        } else {
                            tr(
                                appContext,
                                "${imported.runCount} runs",
                                "${imported.runCount} bajadas"
                            )
                        }
                        tr(
                            appContext,
                            "Imported $tracksLabel with $runsLabel.",
                            "Importado $tracksLabel con $runsLabel."
                        )
                    },
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun exportAllTracksDiagnostics() {
        if (_uiState.value.isExporting || _uiState.value.isImporting) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    error = null,
                    importMessage = null,
                    exportMessage = null
                )
            }

            val result = runCatching {
                val payload = exportAllTracksDiagnosticsUseCase().getOrThrow()
                writeDiagnosticsJsonToStorage(payload)
            }

            _uiState.update { state ->
                state.copy(
                    isExporting = false,
                    exportMessage = result.fold(
                        onSuccess = { destination ->
                            tr(
                                appContext,
                                "Tracks exported to $destination",
                                "Tracks exportados en $destination"
                            )
                        },
                        onFailure = { error ->
                            tr(
                                appContext,
                                "Failed to export tracks: ${error.message ?: "unknown error"}",
                                "No se pudieron exportar los tracks: ${error.message ?: "error desconocido"}"
                            )
                        }
                    ),
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun consumeImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }

    fun consumeExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    private fun writeDiagnosticsJsonToStorage(payload: String): String {
        val fileName = "dhmeter_tracks_${System.currentTimeMillis()}.json"
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
}

