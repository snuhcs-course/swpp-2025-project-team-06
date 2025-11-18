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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.momentag.R

/**
 * Reusable warning banner component
 *
 * Banner displaying warning messages for loading delays, network errors, etc.
 * Typically placed at the bottom of the screen to inform users and provide actions.
 *
 * @param title Warning title (displayed in bold)
 * @param message Warning message
 * @param onActionClick Action button click callback (e.g., refresh)
 * @param onDismiss Close button click callback (optional)
 * @param modifier Modifier
 * @param backgroundColor Banner background color (default: error container)
 * @param icon Icon to display on the left (default: Error)
 * @param actionIcon Action button icon on the right (default: Refresh)
 * @param showActionButton Whether to show action button (default: true)
 * @param showDismissButton Whether to show close button (default: false)
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
        if (backgroundColor ==
            null
        ) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.inverseOnSurface
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = bgColor,
                    shape = RoundedCornerShape(12.dp),
                ).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left icon
        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.cd_warning_icon),
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Text content
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

        Spacer(modifier = Modifier.width(8.dp))

        // Action button (e.g., refresh)
        if (showActionButton) {
            IconButton(
                onClick = onActionClick,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = stringResource(R.string.cd_refresh_action),
                    tint = contentColor,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Close button (optional)
        if (showDismissButton && onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.cd_dismiss_notification),
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ========================================
// Previews
// ========================================

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun previewWarningBannerLoadingDelay() {
    WarningBanner(
        title = "Loading is taking longer than usual",
        message = "Pull down to refresh",
        onActionClick = {},
        modifier = Modifier.padding(24.dp),
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
        modifier = Modifier.padding(24.dp),
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
        modifier = Modifier.padding(24.dp),
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
        modifier = Modifier.padding(24.dp),
    )
}
