package com.example.momentag.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momentag.R

/**
 * Search results empty state screen
 *
 * @param query Search query
 * @param modifier Modifier
 */
@Composable
fun SearchEmptyStateCustom(
    query: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.search_empty_state_no_results, query),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Search idle state (no search performed yet)
 *
 * @param modifier Modifier
 */
@Composable
fun SearchIdleCustom(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.search_empty_state_idle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val displayedHistory = history.take(3) // showing history num

        Column(modifier = modifier) {
            Text(
                text = stringResource(R.string.search_recent_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                items(displayedHistory) { query ->
                    HistoryItem(
                        query = query,
                        onClick = { onHistoryClick(query) },
                        onDelete = { onHistoryDelete(query) },
                    )
                }
            }
        }
    }
}

/**
 * Recent search history item
 */
@Composable
private fun HistoryItem(
    query: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = stringResource(R.string.cd_recent_search_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.cd_delete_search_history),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Search error state (fallback)
 * Rarely used since errors are shown as full-screen overlays
 * @param modifier Modifier
 */
@Composable
fun SearchErrorStateFallbackCustom(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.search_error_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
