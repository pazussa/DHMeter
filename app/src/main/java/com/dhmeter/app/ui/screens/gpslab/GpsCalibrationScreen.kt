package com.dropindh.app.ui.screens.gpslab

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dropindh.app.BuildConfig
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.dhGlassCardColors
import com.dropindh.app.ui.theme.dhTopBarColors
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsCalibrationScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fusedClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionGranted = hasLocationPermission(context)

    // Manual sample inputs (isolated, no persistence to app repositories)
    var sampleAccuracyM by rememberSaveable { mutableFloatStateOf(8f) }
    var sampleLastAccuracyM by rememberSaveable { mutableFloatStateOf(8f) }
    var sampleSpeedMps by rememberSaveable { mutableFloatStateOf(3f) }
    var sampleStepDistanceM by rememberSaveable { mutableFloatStateOf(5f) }
    var sampleJitterM by rememberSaveable { mutableFloatStateOf(2f) }

    // Movement filter (GpsCollector-equivalent) parameters
    var gpsSensitivity by rememberSaveable { mutableFloatStateOf(1f) }
    var collectorMaxAccuracyBaseM by rememberSaveable { mutableFloatStateOf(20f) }
    var collectorMinSpeedMps by rememberSaveable { mutableFloatStateOf(0.5f) }
    var collectorMinDistanceFactor by rememberSaveable { mutableFloatStateOf(0.5f) }

    // Preview/auto-start gating parameters (RecordingService/RecordingViewModel-equivalent)
    var previewMaxAccuracyBaseM by rememberSaveable { mutableFloatStateOf(25f) }
    var previewMinSpeedMps by rememberSaveable { mutableFloatStateOf(2.5f) }

    // Acquisition knobs (applied to live lab location request only)
    var requestIntervalMs by rememberSaveable { mutableIntStateOf(1000) }
    var minUpdateIntervalMs by rememberSaveable { mutableIntStateOf(500) }
    var maxUpdateDelayMs by rememberSaveable { mutableIntStateOf(2000) }

    // Live lab state (screen-local only)
    var isLiveTracking by rememberSaveable { mutableStateOf(false) }
    var useLiveSampleForValidation by rememberSaveable { mutableStateOf(true) }
    var autoCenterOnLatest by rememberSaveable { mutableStateOf(true) }
    var useHybridMapType by rememberSaveable { mutableStateOf(false) }
    var liveUiState by remember { mutableStateOf(LiveUiState()) }
    var liveError by rememberSaveable { mutableStateOf<String?>(null) }
    var liveUiLastCommitMs by remember { mutableLongStateOf(0L) }

    val liveTrackPoints = remember { mutableStateListOf<LatLng>() }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    val isMapsKeyConfigured = BuildConfig.HAS_MAPS_API_KEY

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-34.6037, -58.3816), 14f)
    }
    val mapType = if (useHybridMapType) MapType.HYBRID else MapType.NORMAL
    val mapProperties = remember(locationPermissionGranted, mapType) {
        MapProperties(
            mapType = mapType,
            isMyLocationEnabled = locationPermissionGranted
        )
    }
    val mapUiSettings = remember(locationPermissionGranted) {
        MapUiSettings(
            zoomControlsEnabled = true,
            mapToolbarEnabled = true,
            myLocationButtonEnabled = locationPermissionGranted,
            compassEnabled = true
        )
    }

    val random = remember { Random(System.currentTimeMillis()) }

    DisposableEffect(
        isLiveTracking,
        locationPermissionGranted,
        requestIntervalMs,
        minUpdateIntervalMs,
        maxUpdateDelayMs
    ) {
        if (!isLiveTracking) {
            onDispose { }
        } else if (!locationPermissionGranted) {
            liveError = tr(
                context,
                "Location permission is required for live GPS validation.",
                "Se requiere permiso de ubicación para la validación GPS en vivo."
            )
            onDispose { }
        } else {
            val request = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                requestIntervalMs.toLong()
            ).apply {
                setMinUpdateIntervalMillis(minUpdateIntervalMs.toLong())
                setMaxUpdateDelayMillis(maxUpdateDelayMs.toLong())
            }.build()
            var sampleCounter = liveUiState.sampleCount

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    val previous = lastLocation
                    val segmentDistance = previous?.distanceTo(location) ?: 0f
                    val previousAccuracy = previous?.accuracy ?: location.accuracy
                    val nowMs = System.currentTimeMillis()

                    sampleCounter += 1
                    val shouldCommitUi = sampleCounter <= 2 ||
                        (nowMs - liveUiLastCommitMs) >= LIVE_UI_REFRESH_MS
                    if (shouldCommitUi) {
                        liveUiState = LiveUiState(
                            accuracyM = location.accuracy,
                            lastAccuracyM = previousAccuracy,
                            speedMps = location.speed.coerceAtLeast(0f),
                            stepDistanceM = segmentDistance,
                            jitterM = abs(location.accuracy - previousAccuracy),
                            sampleCount = sampleCounter,
                            lastFixTimestampMs = nowMs
                        )
                        liveUiLastCommitMs = nowMs
                    }
                    if (liveError != null) liveError = null

                    lastLocation = Location(location)

                    val point = LatLng(location.latitude, location.longitude)
                    val shouldAppend = liveTrackPoints.lastOrNull()?.let { lastPoint ->
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            lastPoint.latitude,
                            lastPoint.longitude,
                            point.latitude,
                            point.longitude,
                            distance
                        )
                        distance[0] >= MIN_TRACE_POINT_DISTANCE_M
                    } ?: true

                    if (shouldAppend) {
                        liveTrackPoints.add(point)
                        if (liveTrackPoints.size > MAX_TRACE_POINTS) {
                            liveTrackPoints.removeAt(0)
                        }
                    }
                }
            }

            requestLocationUpdates(
                context = context,
                request = request,
                callback = callback,
                onFailure = { throwable ->
                    liveError = throwable.message ?: tr(
                        context,
                        "Failed to start live GPS updates.",
                        "No se pudieron iniciar las actualizaciones GPS en vivo."
                    )
                    isLiveTracking = false
                },
                fusedClient = fusedClient
            )

            onDispose {
                fusedClient.removeLocationUpdates(callback)
            }
        }
    }

    LaunchedEffect(liveTrackPoints.size, autoCenterOnLatest) {
        if (!autoCenterOnLatest) return@LaunchedEffect
        val size = liveTrackPoints.size
        if (size == 0) return@LaunchedEffect
        val shouldCenter = size <= 2 || (size % 10 == 0)
        if (!shouldCenter) return@LaunchedEffect

        val latest = liveTrackPoints.last()
        runCatching {
            cameraPositionState.move(
                CameraUpdateFactory.newLatLngZoom(latest, 17f)
            )
        }
    }

    val hasLiveSample = liveUiState.sampleCount > 0 && liveUiState.accuracyM > 0f
    val usingLiveSample = useLiveSampleForValidation && hasLiveSample

    val effectiveAccuracyM = if (usingLiveSample) liveUiState.accuracyM else sampleAccuracyM
    val effectiveLastAccuracyM = if (usingLiveSample) {
        if (liveUiState.lastAccuracyM > 0f) liveUiState.lastAccuracyM else liveUiState.accuracyM
    } else {
        sampleLastAccuracyM
    }
    val effectiveSpeedMps = if (usingLiveSample) liveUiState.speedMps else sampleSpeedMps
    val effectiveStepDistanceM = if (usingLiveSample) liveUiState.stepDistanceM else sampleStepDistanceM
    val effectiveJitterM = if (usingLiveSample) liveUiState.jitterM else sampleJitterM

    val normalizedSensitivity = gpsSensitivity.coerceAtLeast(0.01f)
    val collectorMaxAccuracyM = (collectorMaxAccuracyBaseM / normalizedSensitivity).coerceIn(10f, 60f)
    val previewMaxAccuracyM = (previewMaxAccuracyBaseM / normalizedSensitivity).coerceIn(10f, 60f)

    val movementThresholdDistanceM = max(effectiveAccuracyM, effectiveLastAccuracyM) * collectorMinDistanceFactor
    val collectorAccuracyPass = effectiveAccuracyM <= collectorMaxAccuracyM
    val collectorSpeedPass = effectiveSpeedMps >= collectorMinSpeedMps
    val collectorDistancePass = effectiveStepDistanceM > movementThresholdDistanceM
    val collectorMovementPass = collectorAccuracyPass && collectorSpeedPass && collectorDistancePass

    val previewAccuracyPass = effectiveAccuracyM > 0f && effectiveAccuracyM <= previewMaxAccuracyM
    val previewSpeedPass = effectiveSpeedMps >= previewMinSpeedMps
    val previewArmedPass = previewAccuracyPass && previewSpeedPass

    val acquisitionOrderPass = minUpdateIntervalMs <= requestIntervalMs &&
        requestIntervalMs <= maxUpdateDelayMs

    val confidenceScore = (
        if (collectorMovementPass) {
            1f
        } else {
            val accScore = (collectorMaxAccuracyM / effectiveAccuracyM.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
            val speedScore = (effectiveSpeedMps / collectorMinSpeedMps.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
            val distScore = (effectiveStepDistanceM / movementThresholdDistanceM.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
            ((accScore * 0.4f) + (speedScore * 0.3f) + (distScore * 0.3f)).coerceIn(0f, 1f)
        }
    ).coerceIn(0f, 1f)

    val liveAgeSeconds = if (liveUiState.lastFixTimestampMs > 0L) {
        ((System.currentTimeMillis() - liveUiState.lastFixTimestampMs) / 1000L).coerceAtLeast(0L)
    } else {
        null
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = dhTopBarColors(),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Text(
                            text = tr("GPS Calibration Lab", "Laboratorio de calibracion GPS"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("Back", "Atras")
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = dhGlassCardColors(emphasis = true)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = tr(
                                "Experimental GPS lab (isolated)",
                                "Laboratorio GPS experimental (aislado)"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tr(
                                "This screen lets you validate calibration against real GPS traces and does not modify recording, auto-start, or global app settings.",
                                "Esta pantalla permite validar calibración contra trazas GPS reales y no modifica grabación, auto-start ni ajustes globales."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = tr("Live GPS Experimental Validation", "Validación experimental GPS en vivo"),
                    subtitle = tr(
                        "Collect real GPS points and evaluate each calibration change in this same screen.",
                        "Captura puntos GPS reales y evalúa cada cambio de calibración en esta misma pantalla."
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                isLiveTracking = !isLiveTracking
                                if (!isLiveTracking) {
                                    liveError = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isLiveTracking) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Text(tr("Stop live", "Detener vivo"))
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text(tr("Start live", "Iniciar vivo"))
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                liveTrackPoints.clear()
                                liveUiState = LiveUiState()
                                liveUiLastCommitMs = 0L
                                lastLocation = null
                                liveError = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Text(tr("Clear trace", "Limpiar traza"))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(
                                    "Use live sample for calibration",
                                    "Usar muestra en vivo para calibración"
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = if (usingLiveSample) {
                                    tr("Using real GPS values", "Usando valores GPS reales")
                                } else {
                                    tr("Using manual sample values", "Usando valores manuales")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useLiveSampleForValidation,
                            onCheckedChange = { useLiveSampleForValidation = it }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Auto-center map", "Auto-centrar mapa"),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = tr(
                                    "Centers camera every few samples to reduce lag.",
                                    "Centra la cámara cada algunas muestras para reducir lag."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoCenterOnLatest,
                            onCheckedChange = { autoCenterOnLatest = it }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Hybrid map", "Mapa híbrido"),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = tr(
                                    "Disable for better performance on low-end devices.",
                                    "Desactívalo para mejor rendimiento en equipos modestos."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useHybridMapType,
                            onCheckedChange = { useHybridMapType = it }
                        )
                    }

                    if (!locationPermissionGranted) {
                        Text(
                            text = tr(
                                "Location permission is missing. Grant it from Home and return here.",
                                "Falta permiso de ubicación. Otórgalo desde Home y vuelve aquí."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    liveError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = tr(
                                "Samples: ${liveUiState.sampleCount}",
                                "Muestras: ${liveUiState.sampleCount}"
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (liveAgeSeconds != null) {
                                tr("Last fix: ${liveAgeSeconds}s", "Último fix: ${liveAgeSeconds}s")
                            } else {
                                tr("Last fix: --", "Último fix: --")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (hasLiveSample) {
                        Text(
                            text = tr(
                                "Live accuracy ${fmt(liveUiState.accuracyM)}m | speed ${fmt(liveUiState.speedMps)} m/s | step ${fmt(liveUiState.stepDistanceM)}m",
                                "Precisión viva ${fmt(liveUiState.accuracyM)}m | velocidad ${fmt(liveUiState.speedMps)} m/s | paso ${fmt(liveUiState.stepDistanceM)}m"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        if (!isMapsKeyConfigured) {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = dhGlassCardColors()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = tr(
                                            "Google Maps API key is missing.",
                                            "Falta la API key de Google Maps."
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = tr(
                                            "Set MAPS_API_KEY in ~/.gradle/gradle.properties or DHMeter/local.properties and rebuild.",
                                            "Configura MAPS_API_KEY en ~/.gradle/gradle.properties o en DHMeter/local.properties y recompila."
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = tr(
                                            "Current live sample keeps updating so you can still validate thresholds.",
                                            "La muestra en vivo sigue actualizándose para validar umbrales."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            GoogleMap(
                                modifier = Modifier.fillMaxSize(),
                                cameraPositionState = cameraPositionState,
                                properties = mapProperties,
                                uiSettings = mapUiSettings
                            ) {
                                if (liveTrackPoints.size >= 2) {
                                    Polyline(
                                        points = liveTrackPoints.toList(),
                                        color = Color(0xFF00BCD4),
                                        width = 8f
                                    )
                                }

                                liveTrackPoints.firstOrNull()?.let { start ->
                                    Marker(
                                        state = MarkerState(position = start),
                                        title = tr("Trace start", "Inicio traza"),
                                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                                    )
                                }

                                liveTrackPoints.lastOrNull()?.let { latest ->
                                    Marker(
                                        state = MarkerState(position = latest),
                                        title = tr("Latest fix", "Último fix"),
                                        snippet = tr(
                                            "Acc: ${fmt(liveUiState.accuracyM)}m | Speed: ${fmt(liveUiState.speedMps)} m/s",
                                            "Prec: ${fmt(liveUiState.accuracyM)}m | Vel: ${fmt(liveUiState.speedMps)} m/s"
                                        )
                                    )
                                    if (liveUiState.accuracyM > 0f) {
                                        Circle(
                                            center = latest,
                                            radius = liveUiState.accuracyM.toDouble(),
                                            fillColor = Color(0x334FC3F7),
                                            strokeColor = Color(0xFF4FC3F7),
                                            strokeWidth = 2f
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = tr("Manual Sample Override", "Muestra manual alternativa"),
                    subtitle = tr(
                        "Use this when you want to test hypothetical scenarios or when live mode is off.",
                        "Úsala para probar escenarios hipotéticos o cuando el modo vivo está apagado."
                    )
                ) {
                    FloatSliderRow(
                        label = tr("Current accuracy", "Precisión actual"),
                        value = sampleAccuracyM,
                        range = 1f..60f,
                        unit = "m",
                        description = tr(
                            "GPS accuracy for the current fix. Lower is better.",
                            "Precisión GPS del fix actual. Más bajo es mejor."
                        ),
                        onValueChange = { sampleAccuracyM = it }
                    )
                    FloatSliderRow(
                        label = tr("Previous accuracy", "Precisión previa"),
                        value = sampleLastAccuracyM,
                        range = 1f..60f,
                        unit = "m",
                        description = tr(
                            "Accuracy of the previous fix, used with current accuracy for movement threshold.",
                            "Precisión del fix previo; se usa con la actual para el umbral de movimiento."
                        ),
                        onValueChange = { sampleLastAccuracyM = it }
                    )
                    FloatSliderRow(
                        label = tr("Speed", "Velocidad"),
                        value = sampleSpeedMps,
                        range = 0f..20f,
                        unit = tr("m/s", "m/s"),
                        description = tr(
                            "Current speed sample used by movement and auto-start checks.",
                            "Muestra de velocidad actual usada por los filtros de movimiento y auto-start."
                        ),
                        onValueChange = { sampleSpeedMps = it }
                    )
                    FloatSliderRow(
                        label = tr("Distance between points", "Distancia entre puntos"),
                        value = sampleStepDistanceM,
                        range = 0f..40f,
                        unit = "m",
                        description = tr(
                            "Distance between consecutive points; must exceed the dynamic threshold to pass.",
                            "Distancia entre puntos consecutivos; debe superar el umbral dinámico para aprobar."
                        ),
                        onValueChange = { sampleStepDistanceM = it }
                    )
                    FloatSliderRow(
                        label = tr("GPS jitter/noise", "Jitter/ruido GPS"),
                        value = sampleJitterM,
                        range = 0f..20f,
                        unit = "m",
                        description = tr(
                            "Synthetic GPS noise for simulation; useful to test robustness.",
                            "Ruido GPS sintético para simulación; útil para probar robustez."
                        ),
                        onValueChange = { sampleJitterM = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                sampleAccuracyM = random.nextDouble(2.0, 35.0).toFloat()
                                sampleLastAccuracyM = random.nextDouble(2.0, 35.0).toFloat()
                                sampleSpeedMps = random.nextDouble(0.0, 12.0).toFloat()
                                sampleStepDistanceM = random.nextDouble(0.0, 20.0).toFloat()
                                sampleJitterM = random.nextDouble(0.0, 10.0).toFloat()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Text(tr("Random sample", "Muestra aleatoria"))
                        }
                        FilledTonalButton(
                            onClick = {
                                sampleAccuracyM = 8f
                                sampleLastAccuracyM = 8f
                                sampleSpeedMps = 3f
                                sampleStepDistanceM = 5f
                                sampleJitterM = 2f
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Text(tr("Reset sample", "Reset muestra"))
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = tr("Movement Filter Calibration", "Calibración del filtro de movimiento"),
                    subtitle = tr(
                        "Equivalent to the GPS movement gate in GpsCollector.",
                        "Equivalente al filtro de movimiento GPS en GpsCollector."
                    )
                ) {
                    FloatSliderRow(
                        label = tr("GPS sensitivity", "Sensibilidad GPS"),
                        value = gpsSensitivity,
                        range = 0.1f..5f,
                        description = tr(
                            "Higher values make accuracy gates stricter by scaling thresholds down.",
                            "Valores más altos vuelven más estrictos los filtros de precisión al reducir umbrales."
                        ),
                        onValueChange = { gpsSensitivity = it }
                    )
                    FloatSliderRow(
                        label = tr("Base max accuracy", "Precisión máxima base"),
                        value = collectorMaxAccuracyBaseM,
                        range = 5f..60f,
                        unit = "m",
                        description = tr(
                            "Base accuracy cap before sensitivity scaling. Higher is more permissive.",
                            "Tope base de precisión antes de aplicar sensibilidad. Más alto es más permisivo."
                        ),
                        onValueChange = { collectorMaxAccuracyBaseM = it }
                    )
                    FloatSliderRow(
                        label = tr("Min speed threshold", "Umbral mínimo de velocidad"),
                        value = collectorMinSpeedMps,
                        range = 0f..8f,
                        unit = tr("m/s", "m/s"),
                        description = tr(
                            "Minimum speed required for movement acceptance.",
                            "Velocidad mínima requerida para aceptar movimiento."
                        ),
                        onValueChange = { collectorMinSpeedMps = it }
                    )
                    FloatSliderRow(
                        label = tr("Min distance factor", "Factor mínimo de distancia"),
                        value = collectorMinDistanceFactor,
                        range = 0f..2f,
                        description = tr(
                            "Distance factor in: threshold = max(currAcc, prevAcc) * factor.",
                            "Factor de distancia en: umbral = max(precActual, precPrevia) * factor."
                        ),
                        onValueChange = { collectorMinDistanceFactor = it }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    ResultRow(
                        label = tr("Accuracy condition", "Condición de precisión"),
                        passed = collectorAccuracyPass,
                        detail = tr(
                            "${fmt(effectiveAccuracyM)}m <= ${fmt(collectorMaxAccuracyM)}m",
                            "${fmt(effectiveAccuracyM)}m <= ${fmt(collectorMaxAccuracyM)}m"
                        )
                    )
                    ResultRow(
                        label = tr("Speed condition", "Condición de velocidad"),
                        passed = collectorSpeedPass,
                        detail = tr(
                            "${fmt(effectiveSpeedMps)} >= ${fmt(collectorMinSpeedMps)} m/s",
                            "${fmt(effectiveSpeedMps)} >= ${fmt(collectorMinSpeedMps)} m/s"
                        )
                    )
                    ResultRow(
                        label = tr("Distance condition", "Condición de distancia"),
                        passed = collectorDistancePass,
                        detail = tr(
                            "${fmt(effectiveStepDistanceM)}m > ${fmt(movementThresholdDistanceM)}m",
                            "${fmt(effectiveStepDistanceM)}m > ${fmt(movementThresholdDistanceM)}m"
                        )
                    )
                }
            }

            item {
                SectionCard(
                    title = tr("Auto-start GPS Gate", "Filtro GPS para auto-start"),
                    subtitle = tr(
                        "Simulates GPS arming checks used before auto-start triggers.",
                        "Simula las validaciones GPS usadas antes de disparar auto-start."
                    )
                ) {
                    FloatSliderRow(
                        label = tr("Preview max accuracy base", "Precisión máxima base de preview"),
                        value = previewMaxAccuracyBaseM,
                        range = 5f..60f,
                        unit = "m",
                        description = tr(
                            "Base accuracy cap for GPS auto-start arming checks.",
                            "Tope base de precisión para el filtro GPS que arma el auto-start."
                        ),
                        onValueChange = { previewMaxAccuracyBaseM = it }
                    )
                    FloatSliderRow(
                        label = tr("Preview min speed", "Velocidad mínima de preview"),
                        value = previewMinSpeedMps,
                        range = 0f..8f,
                        unit = tr("m/s", "m/s"),
                        description = tr(
                            "Minimum speed required to arm GPS auto-start.",
                            "Velocidad mínima requerida para armar el auto-start por GPS."
                        ),
                        onValueChange = { previewMinSpeedMps = it }
                    )
                    ResultRow(
                        label = tr("Preview accuracy", "Precisión preview"),
                        passed = previewAccuracyPass,
                        detail = tr(
                            "${fmt(effectiveAccuracyM)}m <= ${fmt(previewMaxAccuracyM)}m",
                            "${fmt(effectiveAccuracyM)}m <= ${fmt(previewMaxAccuracyM)}m"
                        )
                    )
                    ResultRow(
                        label = tr("Preview speed", "Velocidad preview"),
                        passed = previewSpeedPass,
                        detail = tr(
                            "${fmt(effectiveSpeedMps)} >= ${fmt(previewMinSpeedMps)} m/s",
                            "${fmt(effectiveSpeedMps)} >= ${fmt(previewMinSpeedMps)} m/s"
                        )
                    )
                }
            }

            item {
                SectionCard(
                    title = tr("Acquisition Parameters (Live Lab)", "Parámetros de adquisición (lab en vivo)"),
                    subtitle = tr(
                        "These controls apply only to this screen's live GPS request.",
                        "Estos controles se aplican solo a la solicitud GPS en vivo de esta pantalla."
                    )
                ) {
                    IntSliderRow(
                        label = tr("Request interval", "Intervalo solicitado"),
                        value = requestIntervalMs,
                        range = 250..3000,
                        unit = "ms",
                        description = tr(
                            "Target interval for live location updates in this lab.",
                            "Intervalo objetivo para actualizaciones de ubicación en vivo de este laboratorio."
                        ),
                        onValueChange = { requestIntervalMs = it }
                    )
                    IntSliderRow(
                        label = tr("Min update interval", "Intervalo mínimo"),
                        value = minUpdateIntervalMs,
                        range = 100..2000,
                        unit = "ms",
                        description = tr(
                            "Fastest allowed update interval. Keep this <= request interval.",
                            "Intervalo mínimo permitido entre actualizaciones. Mantén esto <= intervalo solicitado."
                        ),
                        onValueChange = { minUpdateIntervalMs = it }
                    )
                    IntSliderRow(
                        label = tr("Max update delay", "Delay máximo"),
                        value = maxUpdateDelayMs,
                        range = 500..6000,
                        unit = "ms",
                        description = tr(
                            "Maximum batching delay before updates are delivered. Keep this >= request interval.",
                            "Retraso máximo de agrupación antes de entregar actualizaciones. Mantén esto >= intervalo solicitado."
                        ),
                        onValueChange = { maxUpdateDelayMs = it }
                    )
                    ResultRow(
                        label = tr("Interval ordering", "Orden de intervalos"),
                        passed = acquisitionOrderPass,
                        detail = tr(
                            "Expected: min <= request <= max",
                            "Esperado: min <= request <= max"
                        )
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = dhGlassCardColors(emphasis = true)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = tr("Validation Result", "Resultado de validación"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (usingLiveSample) {
                                tr("Data source: Live GPS", "Fuente de datos: GPS en vivo")
                            } else {
                                tr("Data source: Manual sample", "Fuente de datos: Muestra manual")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ResultRow(
                            label = tr("Movement accepted", "Movimiento aceptado"),
                            passed = collectorMovementPass,
                            detail = tr("GpsCollector gate", "Filtro de GpsCollector")
                        )
                        ResultRow(
                            label = tr("Auto-start armable by GPS", "Auto-start armable por GPS"),
                            passed = previewArmedPass,
                            detail = tr("Preview GPS gate", "Filtro GPS de preview")
                        )
                        Text(
                            text = tr(
                                "Confidence score: ${fmt(confidenceScore * 100f)}%",
                                "Score de confianza: ${fmt(confidenceScore * 100f)}%"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = tr(
                                "Noise-aware distance: ${fmt(effectiveStepDistanceM + effectiveJitterM)} m",
                                "Distancia con ruido: ${fmt(effectiveStepDistanceM + effectiveJitterM)} m"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                gpsSensitivity = 1f
                                collectorMaxAccuracyBaseM = 20f
                                collectorMinSpeedMps = 0.5f
                                collectorMinDistanceFactor = 0.5f
                                previewMaxAccuracyBaseM = 25f
                                previewMinSpeedMps = 2.5f
                                requestIntervalMs = 1000
                                minUpdateIntervalMs = 500
                                maxUpdateDelayMs = 2000
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null)
                            Text(tr("Reset all calibration values", "Resetear todos los valores"))
                        }
                    }
                }
            }
        }
    }
}

private const val LIVE_UI_REFRESH_MS = 750L
private const val MIN_TRACE_POINT_DISTANCE_M = 2f
private const val MAX_TRACE_POINTS = 600

private data class LiveUiState(
    val accuracyM: Float = -1f,
    val lastAccuracyM: Float = -1f,
    val speedMps: Float = 0f,
    val stepDistanceM: Float = 0f,
    val jitterM: Float = 0f,
    val sampleCount: Int = 0,
    val lastFixTimestampMs: Long = 0L
)

@SuppressLint("MissingPermission")
private fun requestLocationUpdates(
    context: Context,
    request: LocationRequest,
    callback: LocationCallback,
    onFailure: (Throwable) -> Unit,
    fusedClient: com.google.android.gms.location.FusedLocationProviderClient
) {
    if (!hasLocationPermission(context)) return
    runCatching {
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }.onFailure(onFailure)
}

private fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fineGranted || coarseGranted
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = dhGlassCardColors()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

@Composable
private fun FloatSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    unit: String = "",
    description: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = if (unit.isBlank()) {
                    fmt(value)
                } else {
                    "${fmt(value)} $unit"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IntSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    unit: String = "",
    description: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = if (unit.isBlank()) {
                    value.toString()
                } else {
                    "$value $unit"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat()
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    passed: Boolean,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (passed) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (passed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun fmt(value: Float): String = String.format(Locale.US, "%.2f", value)
