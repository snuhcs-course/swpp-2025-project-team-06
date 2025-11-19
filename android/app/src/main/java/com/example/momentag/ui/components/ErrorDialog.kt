package com.example.momentag.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.momentag.R
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.StandardIcon

/**
 * Reusable error dialog component
 *
 * Covers the entire screen with a translucent black overlay and displays an error dialog in the center.
 * Used to provide consistent error UI across all screens.
 *
 * @param errorMessage Error message to display
 * @param onRetry Retry button click callback
 * @param onDismiss Dialog dismiss callback (optional, called when backdrop is clicked)
 * @param title Dialog title (default: "Error")
 * @param retryButtonText Retry button text (default: "Try Again")
 * @param dismissible Whether dialog can be closed by clicking backdrop (default: false)
 */
@Composable
fun errorDialog(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    title: String? = null,
    retryButtonText: String? = null,
    dismissible: Boolean = false,
) {
    val dialogTitle = title ?: stringResource(R.string.error_title)
    val retryText = retryButtonText ?: stringResource(R.string.action_retry)
    Dialog(
        onDismissRequest = {
            if (dismissible) {
                onDismiss?.invoke()
            }
        },
        properties =
            DialogProperties(
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // 반투명 검은색 배경 (Backdrop/Scrim)
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            )

            // 에러 다이얼로그 카드
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth(0.85f)
                        .padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Title
                    Text(
                        text = dialogTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    // Error message
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )

                    // Retry button
                    Button(
                        onClick = onRetry,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = retryText,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-screen error overlay (placed directly without Dialog)
 *
 * Version that places Box directly without using Dialog.
 * Useful when using with Navigation or other composables.
 *
 * @param errorMessage Error message to display
 * @param onRetry Retry button click callback
 * @param onDismiss Close (X) button click callback (optional, no X button if null)
 * @param modifier Modifier
 * @param title Dialog title (default: "Error")
 * @param retryButtonText Retry button text (default: "Try Again")
 */
@Composable
fun ErrorOverlay(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    title: String? = null,
    retryButtonText: String? = null,
) {
    val dialogTitle = title ?: stringResource(R.string.error_title)
    val retryText = retryButtonText ?: stringResource(R.string.action_retry)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Translucent black background (Backdrop/Scrim) - covers full screen
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
        )

        // Error dialog card
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.85f)
                    .padding(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Title
                    Text(
                        text = dialogTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    // Error message
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp),
                    )

                    // Retry button
                    Button(
                        onClick = onRetry,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color.Red,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = retryText,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                // X close button (top right)
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp),
                    ) {
                        StandardIcon.Icon(
                            imageVector = Icons.Default.Close,
                            intent = IconIntent.Muted,
                            contentDescription = stringResource(R.string.cd_close_dialog),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun confirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    confirmButtonText: String,
    dismissible: Boolean = false,
) {
    Dialog(
        onDismissRequest = {
            if (dismissible) {
                onDismiss?.invoke()
            }
        },
        properties =
            DialogProperties(
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            )

            Card(
                modifier =
                    Modifier
                        .fillMaxWidth(0.85f)
                        .padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )

                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )

                        Button(
                            onClick = onConfirm,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Red,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = confirmButtonText,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }

                    if (onDismiss != null) {
                        IconButton(
                            onClick = onDismiss,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(32.dp),
                        ) {
                            StandardIcon.Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                intent = IconIntent.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameTagDialog(
    title: String,
    message: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
) {
    var editedTagName by remember(initialValue) { mutableStateOf(initialValue) }

    Dialog(
        onDismissRequest = {
            if (dismissible) {
                onDismiss()
            }
        },
        properties =
            DialogProperties(
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Backdrop/Scrim
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            )

            // Dialog Card
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth(0.85f)
                        .padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 1. Title (Font size matches confirmDialog)
                        Text(
                            text = title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )

                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )

                        // 2. TextField (Styled as requested)
                        TextField(
                            value = editedTagName,
                            onValueChange = { editedTagName = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.field_tag_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                TextFieldDefaults.colors(
                                    // Background color transparent
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    // Indicator (underline) color settings
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary, // When focused
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceDim, // When not focused
                                    // Text color
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (editedTagName.isNotBlank()) {
                                    onConfirm(editedTagName.trim())
                                }
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Red,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            enabled = editedTagName.isNotBlank(),
                        ) {
                            Text(
                                text = stringResource(R.string.action_update),
                                style = MaterialTheme.typography.labelLarge, // Font size matches confirmDialog
                            )
                        }
                    }

                    // 'X' close button (matches confirmDialog)
                    IconButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp),
                    ) {
                        StandardIcon.Icon(
                            imageVector = Icons.Default.Close,
                            intent = IconIntent.Muted,
                            contentDescription = stringResource(R.string.cd_close_dialog),
                        )
                    }
                }
            }
        }
    }
}

// ========================================
// Previews
// ========================================

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun previewErrorDialog() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        errorDialog(
            errorMessage = "Network Error!\nPlease check your internet connection.",
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun previewErrorDialogCustomText() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        errorDialog(
            errorMessage = "We couldn't connect to the server.\nPlease try again later.",
            onRetry = {},
            title = "Connection Failed",
            retryButtonText = "Try Again",
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun previewErrorOverlay() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        // Background content
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Some Screen Content",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(32.dp),
            )
        }

        // 에러 오버레이
        ErrorOverlay(
            errorMessage = "Failed to load data.\nPlease try again.",
            onRetry = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
