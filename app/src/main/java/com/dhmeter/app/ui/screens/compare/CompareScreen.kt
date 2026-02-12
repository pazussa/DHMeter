package com.dhmeter.app.ui.screens.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhmeter.app.ui.theme.*
import com.dhmeter.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    trackId: String,
    runIds: List<String>,
    onViewCharts: () -> Unit,
    onBack: () -> Unit,
    viewModel: CompareViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(trackId, runIds) {
        viewModel.loadComparison(trackId, runIds)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare ${runIds.size} Runs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = onViewCharts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = uiState.comparison != null
                ) {
                    Icon(Icons.Default.ShowChart, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Charts (${runIds.size} runs)")
                }
            }
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.comparison != null -> {
                MultiRunComparisonContent(
                    comparison = uiState.comparison!!,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error ?: "Could not load comparison")
                }
            }
        }
    }
}

@Composable
private fun MultiRunComparisonContent(
    comparison: MultiRunComparisonResult,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Warning banner for invalid runs
        val invalidRuns = comparison.runs.filter { !it.run.isValid }
        if (invalidRuns.isNotEmpty()) {
            InvalidRunsWarningBanner(invalidRuns)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Runs header (horizontal scroll for many runs)
        RunsHeader(runs = comparison.runs)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Verdict card
        VerdictCard(verdict = comparison.verdict)
        
        Spacer(modifier = Modifier.height(24.dp))

        // Metrics comparison table
        Text(
            text = "Metrics Comparison",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        MultiMetricsTable(
            runs = comparison.runs,
            comparisons = comparison.metricComparisons
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Section analysis
        if (comparison.sectionInsights.isNotEmpty()) {
            Text(
                text = "Insights",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            SectionInsights(insights = comparison.sectionInsights)
        }
    }
}

@Composable
private fun InvalidRunsWarningBanner(invalidRuns: List<RunWithColor>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = YellowWarning.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = YellowWarning
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Comparison includes invalid run(s)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                invalidRuns.forEach { runWithColor ->
                    Text(
                        text = "- ${runWithColor.label}: ${runWithColor.run.invalidReason ?: "Invalid"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Results may not be fully reliable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun RunsHeader(runs: List<RunWithColor>) {
    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(runs) { runWithColor ->
            RunCard(
                runWithColor = runWithColor,
                dateFormat = dateFormat
            )
        }
    }
}

@Composable
private fun RunCard(
    runWithColor: RunWithColor,
    dateFormat: SimpleDateFormat
) {
    Card(
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(runWithColor.color), shape = MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = runWithColor.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(runWithColor.color)
                )
                if (!runWithColor.run.isValid) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Invalid",
                        tint = YellowWarning,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = dateFormat.format(Date(runWithColor.run.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = formatDuration(runWithColor.run.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun VerdictCard(verdict: MultiRunVerdict) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (verdict.type) {
                MultiRunVerdict.Type.CLEAR_WINNER -> GreenPositive.copy(alpha = 0.1f)
                MultiRunVerdict.Type.MIXED -> YellowWarning.copy(alpha = 0.1f)
                MultiRunVerdict.Type.SIMILAR -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (verdict.type) {
                        MultiRunVerdict.Type.CLEAR_WINNER -> Icons.Default.EmojiEvents
                        MultiRunVerdict.Type.MIXED -> Icons.Default.SwapVert
                        MultiRunVerdict.Type.SIMILAR -> Icons.Default.DragHandle
                    },
                    contentDescription = null,
                    tint = when (verdict.type) {
                        MultiRunVerdict.Type.CLEAR_WINNER -> GreenPositive
                        MultiRunVerdict.Type.MIXED -> YellowWarning
                        MultiRunVerdict.Type.SIMILAR -> MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = verdict.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = verdict.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MultiMetricsTable(
    runs: List<RunWithColor>,
    comparisons: List<MultiMetricComparison>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row with run labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Metric",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(100.dp)
                )
                runs.forEach { runWithColor ->
                    Text(
                        text = runWithColor.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(runWithColor.color),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(70.dp)
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Metric rows
            comparisons.forEach { comparison ->
                MultiMetricRow(comparison = comparison)
            }
        }
    }
}

@Composable
private fun MultiMetricRow(comparison: MultiMetricComparison) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = comparison.metricName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(100.dp)
        )
        
        comparison.values.forEachIndexed { index, value ->
            val isBest = comparison.bestRunIndex == index
            val displayValue = if (comparison.metricName == "Duration" && value != null) {
                formatDuration((value * 1000).toLong())
            } else {
                value?.let { String.format(Locale.US, "%.2f", it) } ?: "--"
            }
            
            Box(
                modifier = Modifier.width(70.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBest) "* $displayValue" else displayValue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                    color = if (isBest) GreenPositive else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SectionInsights(insights: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            insights.forEach { insight ->
                Row {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = insight,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, secs)
}

