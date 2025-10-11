package com.example.momentag.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.TagColor

/**
 * 재사용 가능한 에러 다이얼로그 컴포넌트
 * 
 * 전체 화면을 반투명 검은색으로 덮고, 중앙에 에러 다이얼로그를 표시합니다.
 * 모든 화면에서 일관된 에러 UI를 제공하기 위해 사용됩니다.
 * 
 * @param errorMessage 표시할 에러 메시지
 * @param onRetry 재시도 버튼 클릭 콜백
 * @param onDismiss 다이얼로그 닫기 콜백 (선택적, 백드롭 클릭 시 호출)
 * @param title 다이얼로그 제목 (기본값: "ERROR")
 * @param retryButtonText 재시도 버튼 텍스트 (기본값: "RETRY")
 * @param dismissible 백드롭 클릭으로 닫을 수 있는지 여부 (기본값: false)
 */
@Composable
fun ErrorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    title: String = "ERROR",
    retryButtonText: String = "RETRY",
    dismissible: Boolean = false
) {
    Dialog(
        onDismissRequest = { 
            if (dismissible) {
                onDismiss?.invoke()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 반투명 검은색 배경 (Backdrop/Scrim)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
            
            // 에러 다이얼로그 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 제목
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // 에러 메시지
                    Text(
                        text = errorMessage,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // 재시도 버튼
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFE57373)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = retryButtonText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 전체 화면을 덮는 에러 오버레이 (Dialog 없이 직접 배치)
 * 
 * Dialog를 사용하지 않고 Box로 직접 배치하는 버전입니다.
 * Navigation이나 다른 컴포저블과 함께 사용할 때 유용합니다.
 * 
 * @param errorMessage 표시할 에러 메시지
 * @param onRetry 재시도 버튼 클릭 콜백
 * @param modifier Modifier
 * @param title 다이얼로그 제목 (기본값: "ERROR")
 * @param retryButtonText 재시도 버튼 텍스트 (기본값: "RETRY")
 */
@Composable
fun ErrorOverlay(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "ERROR",
    retryButtonText: String = "RETRY"
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 반투명 검은색 배경 (Backdrop/Scrim)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )
        
        // 에러 다이얼로그 카드
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 제목
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // 에러 메시지
                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // 재시도 버튼
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Red
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = retryButtonText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ========================================
// 프리뷰
// ========================================

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewErrorDialog() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        ErrorDialog(
            errorMessage = "Network Error!\nPlease check your internet connection.",
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewErrorDialog_CustomText() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        ErrorDialog(
            errorMessage = "서버 연결에 실패했습니다.\n잠시 후 다시 시도해주세요.",
            onRetry = {},
            title = "연결 실패",
            retryButtonText = "다시 시도"
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewErrorOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // 뒤 배경 콘텐츠
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Some Screen Content",
                fontSize = 24.sp,
                modifier = Modifier.padding(32.dp)
            )
        }
        
        // 에러 오버레이
        ErrorOverlay(
            errorMessage = "Failed to load data.\nPlease try again.",
            onRetry = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}
