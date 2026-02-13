package com.dropindh.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.ceil

@Composable
fun DHRaceBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(BackdropTop, BackdropMiddle, BackdropBottom)
                )
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(BackdropAccentA, Color.Transparent),
                    center = Offset(220f, 200f),
                    radius = 820f
                )
            )
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(BackdropAccentB, Color.Transparent),
                    center = Offset(820f, 1320f),
                    radius = 980f
                )
            )
            .drawBehind {
                val spacingPx = 86f
                val diagonalLength = size.height + size.width
                val lineCount = ceil(diagonalLength / spacingPx).toInt()
                for (index in -2..lineCount) {
                    val x = index * spacingPx
                    drawLine(
                        color = lineColor,
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = Stroke.HairlineWidth
                    )
                }
            }
    ) {
        content()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun dhTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    actionIconContentColor = MaterialTheme.colorScheme.primary
)

@Composable
fun dhGlassCardColors(emphasis: Boolean = false): CardColors = CardDefaults.cardColors(
    containerColor = if (emphasis) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    }
)

