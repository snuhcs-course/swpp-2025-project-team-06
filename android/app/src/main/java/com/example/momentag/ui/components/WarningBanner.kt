package com.example.momentag.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 재사용 가능한 경고 배너 컴포넌트
 *
 * 로딩 지연, 네트워크 오류 등의 경고 메시지를 표시하는 배너입니다.
 * 주로 화면 하단에 배치되며, 사용자에게 상황을 알리고 액션을 제공합니다.
 *
 * @param title 경고 제목 (Bold로 표시)
 * @param message 경고 메시지
 * @param onActionClick 액션 버튼 클릭 콜백 (예: 새로고침)
 * @param onDismiss 닫기 버튼 클릭 콜백 (선택적)
 * @param modifier Modifier
 * @param backgroundColor 배너 배경색 (기본값: 빨간색)
 * @param icon 왼쪽에 표시할 아이콘 (기본값: Error)
 * @param actionIcon 오른쪽 액션 버튼 아이콘 (기본값: Refresh)
 * @param showActionButton 액션 버튼 표시 여부 (기본값: true)
 * @param showDismissButton 닫기 버튼 표시 여부 (기본값: false)
 */
@Composable
fun warningBanner(
    title: String,
    message: String,
    onActionClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFE57373),
    icon: ImageVector = Icons.Default.Error,
    actionIcon: ImageVector = Icons.Default.Refresh,
    showActionButton: Boolean = true,
    showDismissButton: Boolean = false,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = backgroundColor,
                    shape = RoundedCornerShape(12.dp),
                ).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 왼쪽 아이콘
        Icon(
            imageVector = icon,
            contentDescription = "Warning Icon",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 텍스트 컨텐츠
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 액션 버튼 (새로고침 등)
        if (showActionButton) {
            IconButton(
                onClick = onActionClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = "Action",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // 닫기 버튼 (선택적)
        if (showDismissButton && onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ========================================
// 프리뷰
// ========================================

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun previewWarningBannerLoadingDelay() {
    warningBanner(
        title = "Loading is taking longer than usual.",
        message = "Please refresh the page.",
        onActionClick = {},
        modifier = Modifier.padding(24.dp),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun previewWarningBannerWithDismiss() {
    warningBanner(
        title = "Connection timeout",
        message = "Unable to reach server.",
        onActionClick = {},
        onDismiss = {},
        showDismissButton = true,
        modifier = Modifier.padding(24.dp),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun previewWarningBannerCustomColor() {
    warningBanner(
        title = "Update available",
        message = "A new version is ready to install.",
        onActionClick = {},
        backgroundColor = Color(0xFF4CAF50),
        icon = Icons.Default.Warning,
        actionIcon = Icons.Default.Refresh,
        modifier = Modifier.padding(24.dp),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun previewWarningBannerNoAction() {
    warningBanner(
        title = "Syncing data...",
        message = "This may take a moment.",
        onActionClick = {},
        showActionButton = false,
        backgroundColor = Color(0xFF2196F3),
        modifier = Modifier.padding(24.dp),
    )
}
