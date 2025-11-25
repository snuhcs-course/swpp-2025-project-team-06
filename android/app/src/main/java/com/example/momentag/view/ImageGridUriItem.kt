package com.example.momentag.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon

/**
 * Image grid item - Supports both normal and selection modes
 *
 * ImageContext is automatically managed through ImageBrowserRepository.
 * UI only needs to handle navigation.
 *
 * @param photo Photo object (photoId + contentUri)
 * @param navController Navigation controller
 * @param modifier Modifier
 * @param isSelectionMode Whether selection mode is active (default: false)
 * @param isSelected Whether currently selected (default: false)
 * @param onToggleSelection Selection/deselection callback (optional)
 * @param onLongPress Long press callback (optional)
 * @param cornerRadius Corner radius (default: 4dp)
 * @param topPadding Top padding (default: 0dp)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGridUriItem(
    photo: Photo,
    navController: NavController,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    cornerRadius: Dp = Dimen.ImageCornerRadius,
    topPadding: Dp = Dimen.SearchSideEmptyPadding,
) {
    Box(
        modifier = modifier.aspectRatio(1f),
    ) {
        AsyncImage(
            model = photo.contentUri,
            contentDescription = stringResource(R.string.cd_photo_item, photo.photoId),
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode && onToggleSelection != null) {
                                onToggleSelection()
                            } else {
                                // Just navigate - ImageContext loaded from Repository
                                navController.navigate(Screen.Image.createRoute(photo.contentUri, photo.photoId))
                            }
                        },
                        onLongClick = null,
                    ),
            contentScale = ContentScale.Crop,
        )

        // Selection indicator (only in selection mode)
        if (isSelectionMode) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else Color.Transparent,
                        ),
            )

            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimen.GridItemSpacing)
                        .size(Dimen.IconButtonSizeSmall)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface.copy(
                                    alpha = 0.8f,
                                )
                            },
                            RoundedCornerShape(Dimen.ComponentCornerRadius),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Check,
                        sizeRole = IconSizeRole.InlineAction,
                        intent = IconIntent.OnPrimaryContainer,
                        contentDescription = stringResource(R.string.cd_photo_selected),
                    )
                }
            }
        }
    }
}
