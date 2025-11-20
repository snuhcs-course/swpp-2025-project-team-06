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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.example.momentag.R
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon

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
        // Left icon
        StandardIcon.Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.cd_warning_icon),
            sizeRole = IconSizeRole.DefaultAction,
            intent = iconIntent,
        )

        Spacer(modifier = Modifier.width(Dimen.ItemSpacingMedium))

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

        Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))

        // Action button (e.g., refresh)
        if (showActionButton) {
            IconButton(
                onClick = onActionClick,
                modifier = Modifier.size(Dimen.IconButtonSizeLarge),
            ) {
                StandardIcon.Icon(
                    imageVector = actionIcon,
                    sizeRole = IconSizeRole.BannerAction,
                    intent = iconIntent,
                    contentDescription = stringResource(R.string.cd_refresh_action),
                )
            }
        }

        // Close button (optional)
        if (showDismissButton && onDismiss != null) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(Dimen.IconButtonSizeLarge),
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Close,
                    sizeRole = IconSizeRole.Navigation,
                    intent = iconIntent,
                    contentDescription = stringResource(R.string.cd_dismiss_notification),
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
