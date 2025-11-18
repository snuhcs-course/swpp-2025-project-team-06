@file:Suppress("ktlint:standard:function-naming")

package com.example.momentag.view

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.Screen
import com.example.momentag.model.Album
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
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

    // 2. ViewModel 인스턴스
    val localViewModel: LocalViewModel = hiltViewModel()
    val photoViewModel: PhotoViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val selectedAlbumIds by localViewModel.selectedAlbumIds.collectAsState()
    val uploadState by photoViewModel.uiState.collectAsState()
    val albumSet by localViewModel.albums.collectAsState()

    // 4. 로컬 상태 변수
    var hasPermission by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 5. Derived 상태
    val isSelectionMode = selectedAlbumIds.isNotEmpty()

    // 6. rememberCoroutineScope
    val scope = rememberCoroutineScope()

    // 8. ActivityResultLauncher
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    if (selectedAlbumIds.isNotEmpty()) {
                        photoViewModel.uploadPhotosForAlbums(selectedAlbumIds, context)
                    }
                } else {
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

    // 10. LaunchedEffect
    LaunchedEffect(uploadState.errorMessage) {
        if (uploadState.errorMessage != null) {
            errorMessage = uploadState.errorMessage
            isErrorBannerVisible = true
        } else {
            isErrorBannerVisible = false
        }
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
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedAlbumIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { localViewModel.clearAlbumSelection() }) {
                            StandardIcon.Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
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
                                contentDescription = "Select All",
                                intent = if (selectedAlbumIds.size == albumSet.size) IconIntent.Primary else IconIntent.Muted,
                            )
                        }
                    },
                )
            } else {
                BackTopBar(
                    title = "MomenTag",
                    onBackClick = onNavigateBack,
                )
            }
        },
        floatingActionButton = {
            if (isSelectionMode) {
                ExtendedFloatingActionButton(
                    text = {
                        if (uploadState.isLoading) {
                            Text("Upload started (check notification)")
                        } else {
                            Text("Upload ${selectedAlbumIds.size} selected albums")
                        }
                    },
                    icon = {
                        if (uploadState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            StandardIcon.Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = "Upload",
                                intent = IconIntent.Inverse,
                            )
                        }
                    },
                    onClick = {
                        if (uploadState.isLoading) return@ExtendedFloatingActionButton

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            photoViewModel.uploadPhotosForAlbums(selectedAlbumIds, context)
                        }
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
                    text = "Albums",
                    style = MaterialTheme.typography.displayMedium,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = Dimen.ItemSpacingSmall, bottom = Dimen.SectionSpacing),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimen.AlbumGridItemSpacing),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.AlbumGridItemSpacing),
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

                AnimatedVisibility(visible = isErrorBannerVisible && errorMessage != null) {
                    WarningBanner(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Upload Failed",
                        message = errorMessage ?: "An error occurred",
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
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        AsyncImage(
            model = album.thumbnailUri,
            contentDescription = "Album ${album.albumName}",
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
                        shape = RoundedCornerShape(8.dp),
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
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        sizeRole = IconSizeRole.InlineAction,
                        intent = IconIntent.OnPrimaryContainer,
                    )
                }
            }
        }
    }
}
