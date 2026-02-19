package com.dropindh.app.ui.screens.gpslab

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dropindh.app.ui.i18n.tr
import com.dropindh.app.ui.theme.dhGlassCardColors
import com.dropindh.app.ui.theme.dhTopBarColors
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsCalibrationScreen(
    onBack: () -> Unit
) {
    // Sample inputs (isolated, no persistence to app repositories)
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

    // Acquisition knobs (simulation only)
    var requestIntervalMs by rememberSaveable { mutableIntStateOf(1000) }
    var minUpdateIntervalMs by rememberSaveable { mutableIntStateOf(500) }
    var maxUpdateDelayMs by rememberSaveable { mutableIntStateOf(2000) }

    val random = remember { Random(System.currentTimeMillis()) }

    val normalizedSensitivity = gpsSensitivity.coerceAtLeast(0.01f)
    val collectorMaxAccuracyM = (collectorMaxAccuracyBaseM / normalizedSensitivity).coerceIn(10f, 60f)
    val previewMaxAccuracyM = (previewMaxAccuracyBaseM / normalizedSensitivity).coerceIn(10f, 60f)

    val movementThresholdDistanceM = max(sampleAccuracyM, sampleLastAccuracyM) * collectorMinDistanceFactor
    val collectorAccuracyPass = sampleAccuracyM <= collectorMaxAccuracyM
    val collectorSpeedPass = sampleSpeedMps >= collectorMinSpeedMps
    val collectorDistancePass = sampleStepDistanceM > movementThresholdDistanceM
    val collectorMovementPass = collectorAccuracyPass && collectorSpeedPass && collectorDistancePass

    val previewAccuracyPass = sampleAccuracyM > 0f && sampleAccuracyM <= previewMaxAccuracyM
    val previewSpeedPass = sampleSpeedMps >= previewMinSpeedMps
    val previewArmedPass = previewAccuracyPass && previewSpeedPass

    val acquisitionOrderPass = minUpdateIntervalMs <= requestIntervalMs &&
        requestIntervalMs <= maxUpdateDelayMs

    val confidenceScore = (
        if (collectorMovementPass) {
            1f
        } else {
            val accScore = (collectorMaxAccuracyM / sampleAccuracyM.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
            val speedScore = (sampleSpeedMps / collectorMinSpeedMps.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
            val distScore = (sampleStepDistanceM / movementThresholdDistanceM.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
            ((accScore * 0.4f) + (speedScore * 0.3f) + (distScore * 0.3f)).coerceIn(0f, 1f)
        }
    ).coerceIn(0f, 1f)

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
                                "Simulation-only screen",
                                "Pantalla solo de simulacion"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tr(
                                "All parameters here are local and do not modify recording, auto-start, or global app settings.",
                                "Todos los parametros aqui son locales y no modifican grabacion, auto-start ni ajustes globales."
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                SectionCard(
                    title = tr("Simulated GPS Sample", "Muestra GPS simulada"),
                    subtitle = tr(
                        "Adjust current sample values to test movement filtering behavior.",
                        "Ajusta los valores de la muestra para probar el filtro de movimiento."
                    )
                ) {
                    FloatSliderRow(
                        label = tr("Current accuracy", "Precision actual"),
                        value = sampleAccuracyM,
                        range = 1f..60f,
                        unit = "m",
                        onValueChange = { sampleAccuracyM = it }
                    )
                    FloatSliderRow(
                        label = tr("Previous accuracy", "Precision previa"),
                        value = sampleLastAccuracyM,
                        range = 1f..60f,
                        unit = "m",
                        onValueChange = { sampleLastAccuracyM = it }
                    )
                    FloatSliderRow(
                        label = tr("Speed", "Velocidad"),
                        value = sampleSpeedMps,
                        range = 0f..20f,
                        unit = tr("m/s", "m/s"),
                        onValueChange = { sampleSpeedMps = it }
                    )
                    FloatSliderRow(
                        label = tr("Distance between points", "Distancia entre puntos"),
                        value = sampleStepDistanceM,
                        range = 0f..40f,
                        unit = "m",
                        onValueChange = { sampleStepDistanceM = it }
                    )
                    FloatSliderRow(
                        label = tr("GPS jitter/noise", "Jitter/ruido GPS"),
                        value = sampleJitterM,
                        range = 0f..20f,
                        unit = "m",
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
                    title = tr("Movement Filter Calibration", "Calibracion del filtro de movimiento"),
                    subtitle = tr(
                        "Equivalent to the GPS movement gate in GpsCollector.",
                        "Equivalente al filtro de movimiento GPS en GpsCollector."
                    )
                ) {
                    FloatSliderRow(
                        label = tr("GPS sensitivity", "Sensibilidad GPS"),
                        value = gpsSensitivity,
                        range = 0.1f..5f,
                        onValueChange = { gpsSensitivity = it }
                    )
                    FloatSliderRow(
                        label = tr("Base max accuracy", "Precision maxima base"),
                        value = collectorMaxAccuracyBaseM,
                        range = 5f..60f,
                        unit = "m",
                        onValueChange = { collectorMaxAccuracyBaseM = it }
                    )
                    FloatSliderRow(
                        label = tr("Min speed threshold", "Umbral minimo de velocidad"),
                        value = collectorMinSpeedMps,
                        range = 0f..8f,
                        unit = tr("m/s", "m/s"),
                        onValueChange = { collectorMinSpeedMps = it }
                    )
                    FloatSliderRow(
                        label = tr("Min distance factor", "Factor minimo de distancia"),
                        value = collectorMinDistanceFactor,
                        range = 0f..2f,
                        onValueChange = { collectorMinDistanceFactor = it }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    ResultRow(
                        label = tr("Accuracy condition", "Condicion de precision"),
                        passed = collectorAccuracyPass,
                        detail = tr(
                            "${fmt(sampleAccuracyM)}m <= ${fmt(collectorMaxAccuracyM)}m",
                            "${fmt(sampleAccuracyM)}m <= ${fmt(collectorMaxAccuracyM)}m"
                        )
                    )
                    ResultRow(
                        label = tr("Speed condition", "Condicion de velocidad"),
                        passed = collectorSpeedPass,
                        detail = tr(
                            "${fmt(sampleSpeedMps)} >= ${fmt(collectorMinSpeedMps)} m/s",
                            "${fmt(sampleSpeedMps)} >= ${fmt(collectorMinSpeedMps)} m/s"
                        )
                    )
                    ResultRow(
                        label = tr("Distance condition", "Condicion de distancia"),
                        passed = collectorDistancePass,
                        detail = tr(
                            "${fmt(sampleStepDistanceM)}m > ${fmt(movementThresholdDistanceM)}m",
                            "${fmt(sampleStepDistanceM)}m > ${fmt(movementThresholdDistanceM)}m"
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
                        label = tr("Preview max accuracy base", "Precision maxima base de preview"),
                        value = previewMaxAccuracyBaseM,
                        range = 5f..60f,
                        unit = "m",
                        onValueChange = { previewMaxAccuracyBaseM = it }
                    )
                    FloatSliderRow(
                        label = tr("Preview min speed", "Velocidad minima de preview"),
                        value = previewMinSpeedMps,
                        range = 0f..8f,
                        unit = tr("m/s", "m/s"),
                        onValueChange = { previewMinSpeedMps = it }
                    )
                    ResultRow(
                        label = tr("Preview accuracy", "Precision preview"),
                        passed = previewAccuracyPass,
                        detail = tr(
                            "${fmt(sampleAccuracyM)}m <= ${fmt(previewMaxAccuracyM)}m",
                            "${fmt(sampleAccuracyM)}m <= ${fmt(previewMaxAccuracyM)}m"
                        )
                    )
                    ResultRow(
                        label = tr("Preview speed", "Velocidad preview"),
                        passed = previewSpeedPass,
                        detail = tr(
                            "${fmt(sampleSpeedMps)} >= ${fmt(previewMinSpeedMps)} m/s",
                            "${fmt(sampleSpeedMps)} >= ${fmt(previewMinSpeedMps)} m/s"
                        )
                    )
                }
            }

            item {
                SectionCard(
                    title = tr("Acquisition Parameters (Simulation)", "Parametros de adquisicion (simulacion)"),
                    subtitle = tr(
                        "These controls are for testing only and are not applied to the real collector.",
                        "Estos controles son solo de prueba y no se aplican al colector real."
                    )
                ) {
                    IntSliderRow(
                        label = tr("Request interval", "Intervalo solicitado"),
                        value = requestIntervalMs,
                        range = 250..3000,
                        unit = "ms",
                        onValueChange = { requestIntervalMs = it }
                    )
                    IntSliderRow(
                        label = tr("Min update interval", "Intervalo minimo"),
                        value = minUpdateIntervalMs,
                        range = 100..2000,
                        unit = "ms",
                        onValueChange = { minUpdateIntervalMs = it }
                    )
                    IntSliderRow(
                        label = tr("Max update delay", "Delay maximo"),
                        value = maxUpdateDelayMs,
                        range = 500..6000,
                        unit = "ms",
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
                            text = tr("Simulation Result", "Resultado de simulacion"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        ResultRow(
                            label = tr("Movement accepted", "Movimiento aceptado"),
                            passed = collectorMovementPass,
                            detail = tr(
                                "GpsCollector gate",
                                "Filtro de GpsCollector"
                            )
                        )
                        ResultRow(
                            label = tr("Auto-start armable by GPS", "Auto-start armable por GPS"),
                            passed = previewArmedPass,
                            detail = tr(
                                "Preview GPS gate",
                                "Filtro GPS de preview"
                            )
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
                                "Noise-aware distance: ${fmt(sampleStepDistanceM + sampleJitterM)} m",
                                "Distancia con ruido: ${fmt(sampleStepDistanceM + sampleJitterM)} m"
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
    unit: String = ""
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
    }
}

@Composable
private fun IntSliderRow(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    unit: String = ""
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
