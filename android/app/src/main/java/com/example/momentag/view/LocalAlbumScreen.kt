package com.example.momentag.view

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.Screen
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAlbumScreen(
    navController: NavController,
    albumId: Long,
    albumName: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val localViewModel: LocalViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val photoViewModel: PhotoViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    var hasPermission by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val photos by localViewModel.imagesInAlbum.collectAsState()

    val selectedPhotos by localViewModel.selectedPhotosInAlbum.collectAsState()
    var isSelectionMode by remember { mutableStateOf(false) }
    val uploadState by photoViewModel.uiState.collectAsState()

    var showErrorBanner by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uploadState.userMessage) {
        uploadState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            photoViewModel.infoMessageShown()
        }
    }

    LaunchedEffect(uploadState.errorMessage) {
        if (uploadState.errorMessage != null) {
            errorMessage = uploadState.errorMessage
            showErrorBanner = true
        } else {
            showErrorBanner = false
        }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        localViewModel.clearPhotoSelection()
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MomenTag",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            localViewModel.clearPhotoSelection()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Selection",
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
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
            if (isSelectionMode && selectedPhotos.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = {
                        if (uploadState.isLoading) {
                            Text("Upload started (check notification)")
                        } else {
                            Text("Upload ${selectedPhotos.size} selected photos")
                        }
                    },
                    icon = {
                        if (uploadState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = "Upload",
                            )
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
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.headlineMedium,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )

                AnimatedVisibility(visible = showErrorBanner && errorMessage != null) {
                    WarningBanner(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        title = "Upload Failed",
                        message = errorMessage ?: "An error occurred",
                        onActionClick = { showErrorBanner = false },
                        showActionButton = false,
                        showDismissButton = true,
                        onDismiss = {
                            showErrorBanner = false
                            photoViewModel.errorMessageShown()
                        },
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                    .clip(RoundedCornerShape(12.dp))
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
                                contentDescription = "Photo ${photo.photoId}",
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
                                            .padding(4.dp)
                                            .size(24.dp)
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
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
