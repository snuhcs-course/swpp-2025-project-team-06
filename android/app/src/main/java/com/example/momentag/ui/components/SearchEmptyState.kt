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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.StandardIcon

/**
 * 검색 결과 빈 상태 화면
 *
 * @param query 검색어
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
            text = "\"$query\"에 대한 검색 결과가 없습니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 검색 대기 상태 (Idle)
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
                text = "Please enter a search term.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val displayedHistory = history.take(3) // showing history num

        Column(modifier = modifier) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimen.ScreenHorizontalPadding, vertical = Dimen.ItemSpacingSmall),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimen.ItemSpacingSmall),
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
 * 최근 검색어 아이템
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
                .padding(vertical = Dimen.ItemSpacingMedium, horizontal = Dimen.ItemSpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            StandardIcon.Icon(
                imageVector = Icons.Default.History,
                contentDescription = "recent search",
                intent = IconIntent.Muted,
            )
            Spacer(modifier = Modifier.width(Dimen.ItemSpacingLarge))
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onDelete) {
            StandardIcon.Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "delete single history",
                intent = IconIntent.Muted,
            )
        }
    }
}

/**
 * 검색 에러 상태 (fallback)
 * 쓰일일이 없을 듯? (왜냐면 Error는 전체화면 에러로 전환해서 보여주니까?)
 * @param modifier Modifier
 */
@Composable
fun SearchErrorStateFallbackCustom(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "An error occurred.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
