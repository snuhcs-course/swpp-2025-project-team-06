package com.example.momentag.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.R

/**
 * Common TopAppBar component
 *
 * Reusable TopBar component for all screens
 * Supports back button, logout, and custom action buttons
 *
 * @param title Screen title
 * @param showBackButton Whether to show back button
 * @param onBackClick Back button click handler
 * @param showLogout Whether to show logout button
 * @param onLogoutClick Logout click handler
 * @param isLogoutLoading Logout loading state
 * @param actions Additional custom action buttons
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    showLogout: Boolean = false,
    onLogoutClick: (() -> Unit)? = null,
    isLogoutLoading: Boolean = false,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                modifier =
                    if (onTitleClick != null) {
                        Modifier.clickable { onTitleClick() }
                    } else {
                        Modifier
                    },
            )
        },
        navigationIcon = {
            var isLogoutConfirmVisible by remember { mutableStateOf(false) }
            if (showLogout && onLogoutClick != null) {
                if (isLogoutLoading) {
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    IconButton(onClick = { isLogoutConfirmVisible = true }) {
                        StandardIcon.Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = stringResource(R.string.cd_logout),
                            sizeRole = IconSizeRole.Navigation,
                        )
                    }
                }

                if (isLogoutConfirmVisible) {
                    confirmDialog(
                        title = stringResource(R.string.dialog_logout_title),
                        message = stringResource(R.string.dialog_logout_message),
                        onConfirm = {
                            isLogoutConfirmVisible = false
                            onLogoutClick()
                        },
                        onDismiss = { isLogoutConfirmVisible = false },
                        confirmButtonText = stringResource(R.string.action_logout),
                        dismissible = true,
                    )
                }
            } else if (showBackButton && onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    StandardIcon.Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        sizeRole = IconSizeRole.Navigation,
                        contentDescription = stringResource(R.string.cd_navigate_back),
                    )
                }
            }
        },
        actions = {
            // Custom action buttons
            actions()
        },
        colors =
            TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        modifier = modifier,
    )
}

/**
 * Simple TopBar with back button only
 *
 * @param title Screen title
 * @param onBackClick Back button click handler
 * @param modifier Modifier
 * @param actions Additional custom action buttons
 */
@Composable
fun BackTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    CommonTopBar(
        title = title,
        showBackButton = true,
        onBackClick = onBackClick,
        modifier = modifier,
        actions = actions,
    )
}

/**
 * Home screen TopBar (includes logout button)
 *
 * @param onLogoutClick Logout click handler
 * @param isLogoutLoading Logout loading state
 * @param onTitleClick Title click handler (navigate to LocalGallery)
 * @param modifier Modifier
 */
@Composable
fun HomeTopBar(
    modifier: Modifier = Modifier,
    onLogoutClick: () -> Unit,
    isLogoutLoading: Boolean = false,
    onTitleClick: (() -> Unit)? = null,
) {
    CommonTopBar(
        title = "MomenTag",
        showLogout = true,
        onLogoutClick = onLogoutClick,
        isLogoutLoading = isLogoutLoading,
        onTitleClick = onTitleClick,
        modifier = modifier,
    )
}
