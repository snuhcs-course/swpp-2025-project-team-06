package com.example.momentag.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush

/**
 * Shared app background gradient used across all screens to keep the look consistent.
 */
@Composable
fun rememberAppBackgroundBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme.surface, colorScheme.primaryContainer) {
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0.5f to colorScheme.surface,
                    1.0f to colorScheme.primaryContainer.copy(alpha = 0.7f),
                ),
        )
    }
}
