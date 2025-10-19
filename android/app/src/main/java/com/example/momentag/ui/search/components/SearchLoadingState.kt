package com.example.momentag.ui.search.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Word

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
    var showWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000) // 5초 대기
        showWarning = true
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // 🔹 중앙 로딩 섹션
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🐻", fontSize = 80.sp, modifier = Modifier.padding(bottom = 16.dp))
            Text(
                text = "Loading ...",
                fontSize = 18.sp,
                color = Word,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = com.example.momentag.ui.theme.Button,
                strokeWidth = 4.dp,
            )
        }

        // 🔹 하단 경고 배너
        if (showWarning) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
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
