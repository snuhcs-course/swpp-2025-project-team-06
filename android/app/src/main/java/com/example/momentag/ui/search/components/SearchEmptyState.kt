package com.example.momentag.ui.search.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

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
fun SearchIdleCustom(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "검색어를 입력해주세요.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            text = "오류가 발생했습니다.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
