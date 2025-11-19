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
import androidx.compose.ui.text.font.FontWeight
import com.example.momentag.ui.theme.Dimen
import kotlinx.coroutines.delay

/**
 * 로딩 화면 (곰돌이 + Loading 텍스트 + Progress Bar + 경고 메시지)
 *
 * @param onRefresh 새로고침 버튼 클릭 콜백
 * @param modifier Modifier
 */
@Composable
fun SearchLoadingStateCustom(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 5초 후에 경고 메시지 표시
    var isWarningVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(5000) // 5초 대기
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
                modifier = Modifier.size(Dimen.CircularProgressSizeBig),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = Dimen.CircularProgressStrokeWidth,
            )
            Text(
                text = "Loading ...",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = Dimen.ItemSpacingLarge),
            )
        }

        // 🔹 하단 경고 배너
        if (isWarningVisible) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Dimen.SectionSpacing),
            ) {
                WarningBanner(
                    title = "Loading is taking longer than usual.",
                    message = "Please refresh the page.",
                    onActionClick = onRefresh,
                )
            }
        }
    }
}
