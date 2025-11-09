package com.example.momentag

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.search.components.SearchLoadingStateCustom
import com.example.momentag.ui.theme.horizontalArrangement
import com.example.momentag.ui.theme.imageCornerRadius
import com.example.momentag.ui.theme.verticalArrangement
import com.example.momentag.viewmodel.SelectImageViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SelectImageScreen(navController: NavController) {
    val context = LocalContext.current

    val selectImageViewModel: SelectImageViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val allPhotos by selectImageViewModel.allPhotos.collectAsState()
    val tagName by selectImageViewModel.tagName.collectAsState()
    val selectedPhotos by selectImageViewModel.selectedPhotos.collectAsState()
    val isLoading by selectImageViewModel.isLoading.collectAsState()
    val isLoadingMore by selectImageViewModel.isLoadingMore.collectAsState()
    val isSelectionMode by selectImageViewModel.isSelectionMode.collectAsState()

    var isSelectionModeDelay by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
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

    LaunchedEffect(key1 = true) {
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(isSelectionMode) {
        kotlinx.coroutines.delay(200L) // 0.2초
        isSelectionModeDelay = isSelectionMode
    }

    BackHandler(enabled = !isSelectionMode) {
        selectImageViewModel.setSelectionMode(true)
    }

    val onPhotoClick: (Photo) -> Unit = { photo ->
        if (isSelectionMode) {
            selectImageViewModel.togglePhoto(photo)
        } else {
            selectImageViewModel.setGalleryBrowsingSession()
            navController.navigate(
                Screen.Image.createRoute(
                    uri = photo.contentUri,
                    imageId = photo.photoId,
                ),
            )
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && selectImageViewModel.allPhotos.value.isEmpty()) {
            selectImageViewModel.getAllPhotos()
        }
    }

    // LazyGrid state for pagination
    val listState = selectImageViewModel.lazyGridState

    // Scroll detection for pagination
    LaunchedEffect(listState, isLoadingMore) {
        if (!isLoadingMore) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
            }.distinctUntilChanged()
                .debounce(150)
                .collect { lastVisibleIndex ->
                    if (lastVisibleIndex != null && allPhotos.isNotEmpty()) {
                        val totalItems = allPhotos.size
                        val remainingItems = totalItems - (lastVisibleIndex + 1)

                        if (remainingItems < 33) {
                            selectImageViewModel.loadMorePhotos()
                        }
                    }
                }
        }
    }

    Scaffold(
        topBar = {
            CommonTopBar(
                title = "MomenTag",
                showBackButton = true,
                onBackClick = {
                    navController.popBackStack()
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            if (isSelectionModeDelay) {
                                DropdownMenuItem(
                                    text = { Text("View") },
                                    onClick = {
                                        selectImageViewModel.setSelectionMode(false)
                                        showMenu = false
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    onClick = {
                                        selectImageViewModel.setSelectionMode(true)
                                        showMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "#$tagName",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge,
            )
            HorizontalDivider(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose more than 5 pictures",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge,
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                SearchLoadingStateCustom(
                    onRefresh = { selectImageViewModel.getAllPhotos() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(453.dp),
                )
            } else if (hasPermission) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(verticalArrangement),
                    horizontalArrangement = Arrangement.spacedBy(horizontalArrangement),
                    modifier = Modifier.height(453.dp),
                ) {
                    items(
                        count = allPhotos.size,
                        key = { index -> allPhotos[index].photoId },
                    ) { index ->
                        val photo = allPhotos[index]
                        val isSelected = selectedPhotos.any { it.photoId == photo.photoId }

                        Box(
                            modifier =
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(imageCornerRadius))
                                    .combinedClickable(
                                        onClick = { onPhotoClick(photo) },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                selectImageViewModel.setSelectionMode(true)
                                                if (photo !in selectedPhotos) {
                                                    selectImageViewModel.togglePhoto(photo)
                                                }
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
                                                if (isSelected) MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f) else Color.Transparent,
                                            ),
                                )

                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                        .copy(
                                                            alpha = 0.8f,
                                                        )
                                                },
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

                    // Loading indicator
                    if (isLoadingMore) {
                        item(span = {
                            androidx.compose.foundation.lazy.grid
                                .GridItemSpan(3)
                        }) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(21.dp),
                    modifier = Modifier.height(453.dp),
                ) {
                    items(allPhotos.size) { _ ->
                        Box(modifier = Modifier) {
                            Spacer(
                                modifier =
                                    Modifier
                                        .padding(top = 12.dp)
                                        .aspectRatio(1f)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(16.dp),
                                        ).align(Alignment.BottomCenter),
                            )
                        }
                    }
                }
            }

            Column {
                HorizontalDivider(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Button(
                    onClick = {
                        navController.navigate(Screen.AddTag.route) {
                            popUpTo(Screen.AddTag.route) { inclusive = true }
                        }
                    },
                    shape = RoundedCornerShape(15.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                ) {
                    Text(text = "Done")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
