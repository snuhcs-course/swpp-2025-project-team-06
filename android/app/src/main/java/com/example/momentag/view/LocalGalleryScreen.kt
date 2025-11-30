@file:Suppress("ktlint:standard:function-naming")

package com.example.momentag.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.Album
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalGalleryScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 2. ViewModel 인스턴스
    val localViewModel: LocalViewModel = hiltViewModel()
    val photoViewModel: PhotoViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val selectedAlbumIds by localViewModel.selectedAlbumIds.collectAsState()
    val uploadState by photoViewModel.uiState.collectAsState()
    val albumSet by localViewModel.albums.collectAsState()

    // 4. 로컬 상태 변수
    var hasPermission by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }
    var shouldShowNotificationRationale by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 5. Derived 상태
    val isSelectionMode = selectedAlbumIds.isNotEmpty()

    // 6. rememberCoroutineScope
    val scope = rememberCoroutineScope()
    val backgroundBrush = rememberAppBackgroundBrush()

    // 8. ActivityResultLauncher
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasNotificationPermission = true
                } else {
                    shouldShowNotificationRationale = true
                }
            },
        )

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val isGranted =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                        if (isGranted) {
                            hasNotificationPermission = true
                            shouldShowNotificationRationale = false
                        } else {
                            hasNotificationPermission = false
                            shouldShowNotificationRationale = true
                        }
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 10. LaunchedEffect
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uploadState.error) {
        errorMessage =
            when (uploadState.error) {
                PhotoViewModel.PhotoError.NoPhotosSelected -> context.getString(R.string.error_message_no_photo_ids)
                null -> null
            }
        isErrorBannerVisible = errorMessage != null
    }

    if (hasPermission) {
        LaunchedEffect(Unit) {
            localViewModel.getAlbums()
        }
    }

    LaunchedEffect(key1 = true) {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        permissionLauncher.launch(permission)
    }

    // 12. BackHandler
    BackHandler(enabled = isSelectionMode) {
        localViewModel.clearAlbumSelection()
    }

    // 13. UI (Scaffold)
    Scaffold(
        containerColor = Color.Transparent, // Let content handle background
        topBar = {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (Animation.DefaultFadeIn)
                        .togetherWith(Animation.DefaultFadeOut)
                        .using(SizeTransform(clip = false))
                },
                label = "TopAppBarAnimation",
            ) { selectionMode ->
                if (selectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.photos_selected_count, selectedAlbumIds.size)) },
                        navigationIcon = {
                            IconButton(onClick = { localViewModel.clearAlbumSelection() }) {
                                StandardIcon.Icon(
                                    contentDescription = stringResource(R.string.cd_deselect_all),
                                    imageVector = Icons.Default.Close,
                                    sizeRole = IconSizeRole.Navigation,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                if (selectedAlbumIds.size == albumSet.size) {
                                    localViewModel.clearAlbumSelection()
                                } else {
                                    localViewModel.selectAllAlbums(albumSet)
                                }
                            }) {
                                val selectAllIcon =
                                    if (selectedAlbumIds.size == albumSet.size) {
                                        Icons.Default.CheckBox
                                    } else {
                                        Icons.Default.CheckBoxOutlineBlank
                                    }
                                StandardIcon.Icon(
                                    imageVector = selectAllIcon,
                                    contentDescription = stringResource(R.string.cd_select_all),
                                    intent = if (selectedAlbumIds.size == albumSet.size) IconIntent.Primary else IconIntent.Muted,
                                )
                            }
                        },
                    )
                } else {
                    BackTopBar(
                        title = stringResource(R.string.app_name),
                        onBackClick = onNavigateBack,
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = Animation.EnterFromBottom,
                exit = Animation.ExitToBottom,
            ) {
                ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    text = {
                        AnimatedContent(
                            targetState = uploadState.isLoading,
                            transitionSpec = {
                                (Animation.DefaultFadeIn).togetherWith(Animation.DefaultFadeOut)
                            },
                            label = "FabText",
                        ) { isLoading ->
                            if (isLoading) {
                                Text(stringResource(R.string.banner_upload_check_notification))
                            } else {
                                Text(stringResource(R.string.photos_upload_selected_albums, selectedAlbumIds.size))
                            }
                        }
                    },
                    icon = {
                        AnimatedContent(
                            targetState = uploadState.isLoading,
                            transitionSpec = {
                                (Animation.DefaultFadeIn).togetherWith(Animation.DefaultFadeOut)
                            },
                            label = "FabIcon",
                        ) { isLoading ->
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimen.IconButtonSizeSmall),
                                    strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = stringResource(R.string.cd_upload),
                                    tintOverride = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    },
                    onClick = {
                        if (uploadState.isLoading) return@ExtendedFloatingActionButton
                        photoViewModel.uploadPhotosForAlbums(selectedAlbumIds, context)
                    },
                )
            }
        },
    ) { paddingValues ->
        if (hasNotificationPermission) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        try {
                            if (hasPermission) {
                                localViewModel.getAlbums()
                            }
                        } finally {
                            isRefreshing = false
                        }
                    }
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backgroundBrush)
                        .padding(paddingValues),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimen.ScreenHorizontalPadding),
                ) {
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))
                    Text(
                        text = stringResource(R.string.gallery_albums_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = Dimen.ItemSpacingSmall, bottom = Dimen.ItemSpacingXSmall),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                    Text(
                        text = stringResource(R.string.help_upload_albums),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    ) {
                        items(albumSet) { album ->
                            val isSelected = selectedAlbumIds.contains(album.albumId)
                            AlbumGridItem(
                                album = album,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        localViewModel.toggleAlbumSelection(album.albumId)
                                    } else {
                                        navController.navigate(
                                            Screen.LocalAlbum.createRoute(album.albumId, album.albumName),
                                        )
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        localViewModel.toggleAlbumSelection(album.albumId)
                                    }
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                    uploadState.userMessage?.let { message ->
                        LaunchedEffect(uploadState.userMessage) {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            photoViewModel.infoMessageShown()
                        }
                    }

                    AnimatedVisibility(
                        visible = isErrorBannerVisible && errorMessage != null,
                        enter = Animation.EnterFromBottom,
                        exit = Animation.ExitToBottom,
                    ) {
                        WarningBanner(
                            modifier = Modifier.fillMaxWidth(),
                            title = stringResource(R.string.notification_upload_failed),
                            message = errorMessage ?: stringResource(R.string.error_message_generic),
                            onActionClick = { isErrorBannerVisible = false },
                            showActionButton = false,
                            showDismissButton = true,
                            onDismiss = {
                                isErrorBannerVisible = false
                                photoViewModel.errorMessageShown()
                            },
                        )
                    }
                }
            }
        } else if (shouldShowNotificationRationale) {
            PermissionDeniedContent(
                modifier = Modifier.padding(paddingValues),
                onRequestPermission = {
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    context.startActivity(intent)
                },
            )
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit,
) {
    val backgroundBrush = rememberAppBackgroundBrush()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.empty_state_notification_permission_needed),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Button(onClick = onRequestPermission) {
            Text(text = stringResource(R.string.button_go_to_settings))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridItem(
    album: Album,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Dimen.ComponentCornerRadius))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        AsyncImage(
            model = album.thumbnailUri,
            contentDescription = stringResource(R.string.cd_album, album.albumName),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = album.albumName,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(Dimen.ItemSpacingSmall) // Position from the corner
                    .background(
                        color = Color.Black.copy(alpha = 0.6f), // Use black with some transparency
                        shape = RoundedCornerShape(Dimen.ButtonCornerRadius),
                    ).padding(horizontal = Dimen.ItemSpacingSmall, vertical = Dimen.GridItemSpacing), // Padding inside the background
        )

        if (isSelectionMode) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            } else {
                                Color.Transparent
                            },
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
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            },
                            CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.cd_photo_selected),
                        sizeRole = IconSizeRole.InlineAction,
                        intent = IconIntent.OnPrimaryContainer,
                    )
                }
            }
        }
    }
}
