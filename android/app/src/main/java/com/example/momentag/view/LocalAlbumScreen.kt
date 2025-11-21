package com.example.momentag.view

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.ui.component.VerticalScrollbar
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
fun LocalAlbumScreen(
    navController: NavController,
    albumId: Long,
    albumName: String,
    onNavigateBack: () -> Unit,
) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current

    // 2. ViewModel 인스턴스
    val localViewModel: LocalViewModel = hiltViewModel()
    val photoViewModel: PhotoViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val photos by localViewModel.imagesInAlbum.collectAsState()
    val selectedPhotos by localViewModel.selectedPhotosInAlbum.collectAsState()
    val uploadState by photoViewModel.uiState.collectAsState()

    // 4. 로컬 상태 변수
    var hasPermission by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 6. rememberCoroutineScope
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val backgroundBrush = rememberAppBackgroundBrush()

    // 8. ActivityResultLauncher
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    // 10. LaunchedEffect
    LaunchedEffect(uploadState.userMessage) {
        uploadState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            photoViewModel.infoMessageShown()
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
        LaunchedEffect(albumId) {
            localViewModel.getImagesForAlbum(albumId)
        }
    }

    // Set browsing session when album images are loaded
    LaunchedEffect(photos, albumName) {
        if (photos.isNotEmpty()) {
            localViewModel.setLocalAlbumBrowsingSession(photos, albumName)
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

    LaunchedEffect(selectedPhotos) {
        if (selectedPhotos.isEmpty()) {
            isSelectionMode = false
        }
    }

    // 12. BackHandler
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        localViewModel.clearPhotoSelection()
    }

    // 13. UI (Scaffold)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    AnimatedContent(
                        targetState = isSelectionMode,
                        transitionSpec = {
                            (Animation.DefaultFadeIn)
                                .togetherWith(Animation.DefaultFadeOut)
                                .using(SizeTransform(clip = false))
                        },
                        label = "TopAppBarNavIcon",
                    ) { selectionMode ->
                        if (selectionMode) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                localViewModel.clearPhotoSelection()
                            }) {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_cancel_selection),
                                    sizeRole = IconSizeRole.DefaultAction,
                                )
                            }
                        } else {
                            IconButton(onClick = onNavigateBack) {
                                StandardIcon.Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_navigate_back),
                                    sizeRole = IconSizeRole.Navigation,
                                )
                            }
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isSelectionMode && selectedPhotos.isNotEmpty(),
                enter = Animation.EnterFromBottom,
                exit = Animation.ExitToBottom,
            ) {
                ExtendedFloatingActionButton(
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
                                Text(stringResource(R.string.photos_upload_selected_photos, selectedPhotos.size))
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
                                )
                            } else {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = stringResource(R.string.cd_upload),
                                    sizeRole = IconSizeRole.DefaultAction,
                                )
                            }
                        }
                    },
                    onClick = {
                        if (uploadState.isLoading) return@ExtendedFloatingActionButton

                        photoViewModel.uploadSelectedPhotos(selectedPhotos, context)
                    },
                )
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    try {
                        if (hasPermission) {
                            localViewModel.getImagesForAlbum(albumId)
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
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimen.ScreenHorizontalPadding),
                ) {
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = Dimen.ItemSpacingSmall, bottom = Dimen.SectionSpacing),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )

                    AnimatedVisibility(
                        visible = isErrorBannerVisible && errorMessage != null,
                        enter = Animation.EnterFromBottom,
                        exit = Animation.ExitToBottom,
                    ) {
                        WarningBanner(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimen.ItemSpacingSmall),
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

                    Box(modifier = Modifier.weight(1f)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(Dimen.AlbumGridItemSpacing),
                            horizontalArrangement = Arrangement.spacedBy(Dimen.AlbumGridItemSpacing),
                        ) {
                            items(
                                count = photos.size,
                                key = { index -> photos[index].photoId },
                            ) { index ->
                                val photo = photos[index]
                                val isSelected = selectedPhotos.any { it.photoId == photo.photoId }

                                Box(
                                    modifier =
                                        Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(Dimen.ComponentCornerRadius))
                                            .combinedClickable(
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        localViewModel.togglePhotoSelection(photo)
                                                    } else {
                                                        localViewModel.setLocalAlbumBrowsingSession(photos, albumName)
                                                        navController.navigate(
                                                            Screen.Image.createRoute(
                                                                uri = photo.contentUri,
                                                                imageId = photo.photoId,
                                                            ),
                                                        )
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isSelectionMode) {
                                                        isSelectionMode = true
                                                        localViewModel.togglePhotoSelection(photo)
                                                    }
                                                },
                                            ),
                                ) {
                                    AsyncImage(
                                        model = photo.contentUri,
                                        contentDescription = stringResource(R.string.cd_photo_item, photo.photoId),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
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
                        }
                    }
                }

                // Scrollbar positioned outside Column to span padding boundary
                if (hasPermission && photos.isNotEmpty()) {
                    VerticalScrollbar(
                        state = gridState,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(
                                    top = Dimen.ItemSpacingLarge + Dimen.TitleRowHeight + Dimen.SectionSpacing, // Spacer + Title + Divider
                                    end = Dimen.ScreenHorizontalPadding / 2,
                                ),
                    )
                }
            }
        }
    }
}
