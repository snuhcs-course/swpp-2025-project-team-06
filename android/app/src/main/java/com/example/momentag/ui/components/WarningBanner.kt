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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon

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
fun WarningBanner(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    onActionClick: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    backgroundColor: Color? = null,
    icon: ImageVector = Icons.Default.Error,
    actionIcon: ImageVector = Icons.Default.Refresh,
    showActionButton: Boolean = true,
    showDismissButton: Boolean = false,
) {
    val bgColor = backgroundColor ?: MaterialTheme.colorScheme.errorContainer
    val contentColor =
        if (backgroundColor == null) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        }
    val iconIntent =
        if (backgroundColor == null) {
            IconIntent.OnErrorContainer
        } else {
            IconIntent.InverseSurface
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = bgColor,
                    shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
                ).padding(Dimen.ComponentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 왼쪽 아이콘
        StandardIcon.Icon(
            imageVector = icon,
            contentDescription = "Warning Icon",
            sizeRole = IconSizeRole.DefaultAction,
            intent = iconIntent,
        )

        Spacer(modifier = Modifier.width(Dimen.ItemSpacingMedium))

        // 텍스트 컨텐츠
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = message,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))

        // 액션 버튼 (새로고침 등)
        if (showActionButton) {
            IconButton(
                onClick = onActionClick,
                modifier = Modifier.size(Dimen.IconButtonSizeLarge),
            ) {
                StandardIcon.Icon(
                    imageVector = actionIcon,
                    contentDescription = "Action",
                    sizeRole = IconSizeRole.BannerAction,
                    intent = iconIntent,
                )
            }
        }

        // 닫기 버튼 (선택적)
        if (showDismissButton && onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(Dimen.IconButtonSizeLarge),
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    sizeRole = IconSizeRole.Navigation,
                    intent = iconIntent,
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
    WarningBanner(
        title = "Loading is taking longer than usual.",
        message = "Please refresh the page.",
        onActionClick = {},
        modifier = Modifier.padding(Dimen.SectionSpacing),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun previewWarningBannerWithDismiss() {
    WarningBanner(
        title = "Connection timeout",
        message = "Unable to reach server.",
        onActionClick = {},
        onDismiss = {},
        showDismissButton = true,
        modifier = Modifier.padding(Dimen.SectionSpacing),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun previewWarningBannerCustomColor() {
    WarningBanner(
        title = "Update available",
        message = "A new version is ready to install.",
        onActionClick = {},
        backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
        icon = Icons.Default.Warning,
        actionIcon = Icons.Default.Refresh,
        modifier = Modifier.padding(Dimen.SectionSpacing),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun previewWarningBannerNoAction() {
    WarningBanner(
        title = "Syncing data...",
        message = "This may take a moment.",
        onActionClick = {},
        showActionButton = false,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(Dimen.SectionSpacing),
    )
}
