package com.example.momentag.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.momentag.R
import kotlinx.coroutines.delay

/**
 * Loading screen with progress indicator and warning banner
 *
 * @param onRefresh Refresh button click callback
 * @param modifier Modifier
 */
@Composable
fun SearchLoadingStateCustom(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Show warning message after 5 seconds
    var isWarningVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(5000) // Wait 5 seconds
        isWarningVisible = true
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
            )
            Text(
                text = stringResource(R.string.loading_with_ellipsis),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

        // Warning banner at bottom
        if (isWarningVisible) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
            ) {
                WarningBanner(
                    title = stringResource(R.string.banner_loading_delay_title),
                    message = stringResource(R.string.banner_loading_delay_message),
                    onActionClick = onRefresh,
                )
            }
        }
    }
}
