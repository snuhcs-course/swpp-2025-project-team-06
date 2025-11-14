package com.example.momentag

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.AddPhotosButton
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.imageCornerRadius
import com.example.momentag.viewmodel.AddTagViewModel
import com.example.momentag.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var hasPermission by remember { mutableStateOf(false) }
    var isChanged by remember { mutableStateOf(true) }
    var showErrorBanner by remember { mutableStateOf(false) }

    val addTagViewModel: AddTagViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val tagName by addTagViewModel.tagName.collectAsState()
    val selectedPhotos by addTagViewModel.selectedPhotos.collectAsState()
    val saveState by addTagViewModel.saveState.collectAsState()

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
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        permissionLauncher.launch(permission)
    }

    BackHandler {
        addTagViewModel.clearDraft()
        navController.popBackStack()
    }

    LaunchedEffect(saveState) {
        when (saveState) {
            is AddTagViewModel.SaveState.Success -> {
                addTagViewModel.clearDraft()
                navController.popBackStack()
            }
            is AddTagViewModel.SaveState.Error -> {
                showErrorBanner = true
                kotlinx.coroutines.delay(2000) // 2초 후 자동 사라짐
                showErrorBanner = false
            }
            else -> { }
        }
    }

    val onDeselectPhoto: (Photo) -> Unit = { photo ->
        isChanged = true
        addTagViewModel.removePhoto(photo)
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Create Tag",
                onBackClick = {
                    addTagViewModel.clearDraft()
                    navController.popBackStack()
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            BottomNavBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            WindowInsets.navigationBars
                                .only(WindowInsetsSides.Bottom)
                                .asPaddingValues(),
                        ),
                currentTab = BottomTab.MyTagsScreen,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomTab.HomeScreen -> navController.navigate(Screen.Home.route)
                        BottomTab.SearchResultScreen -> navController.navigate(Screen.SearchResult.createRoute(""))
                        BottomTab.MyTagsScreen -> navController.navigate(Screen.MyTags.route)
                        BottomTab.StoryScreen -> navController.navigate(Screen.Story.route)
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
                    .padding(paddingValues),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                focusManager.clearFocus()
                            })
                        },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                ) {
                    // Tag Name Section
                    TagNameSection(
                        tagName = tagName,
                        onTagNameChange = { addTagViewModel.updateTagName(it) },
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Added Pictures Section
                    Text(
                        text = if (selectedPhotos.isEmpty()) "Photos" else "Photos (${selectedPhotos.size})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (hasPermission) {
                        SelectedPhotosGrid(
                            photos = selectedPhotos,
                            onPhotoClick = onDeselectPhoto,
                            onAddPhotosClick = {
                                navController.navigate(Screen.SelectImage.route)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Bottom Section - Floating
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    // Error Banner - Floating above Done button
                    if (showErrorBanner && saveState is AddTagViewModel.SaveState.Error) {
                        WarningBanner(
                            title = "Unable to save tag",
                            message = (saveState as AddTagViewModel.SaveState.Error).message,
                            onActionClick = { },
                            onDismiss = { showErrorBanner = false },
                            showActionButton = false,
                            showDismissButton = true,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Done Button
                    if (isChanged) {
                        val isFormValid = selectedPhotos.isNotEmpty() && tagName.isNotBlank()
                        val canSubmit = isFormValid && saveState != AddTagViewModel.SaveState.Loading

                        Button(
                            onClick = {
                                addTagViewModel.saveTagAndPhotos()
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        if (isFormValid) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    contentColor =
                                        if (isFormValid) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .shadow(
                                        elevation = if (canSubmit) 6.dp else 2.dp,
                                        shape = RoundedCornerShape(24.dp),
                                        clip = false,
                                    ),
                            enabled = canSubmit,
                        ) {
                            Text(
                                "Done",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                // Full Screen Loading Overlay
                if (saveState == AddTagViewModel.SaveState.Loading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .size(56.dp)
                                    .shadow(
                                        elevation = 8.dp,
                                        shape = CircleShape,
                                        clip = false,
                                    ),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagNameSection(
    tagName: String,
    onTagNameChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column {
        TextField(
            value = tagName,
            onValueChange = onTagNameChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.headlineSmall,
            placeholder = {
                Text(
                    "Enter tag name",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            },
            leadingIcon = {
                Text(
                    text = "#",
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        )
    }
}

@Composable
private fun SelectedPhotosGrid(
    photos: List<Photo>,
    onPhotoClick: (Photo) -> Unit,
    onAddPhotosClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        // Add Picture Button - Always first
        item {
            AddPhotosButton(
                onClick = onAddPhotosClick,
                modifier = Modifier.aspectRatio(1f),
            )
        }

        // Photos
        items(photos) { photo ->
            PhotoItem(
                photo = photo,
                onClick = { onPhotoClick(photo) },
                modifier = Modifier.aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun PhotoItem(
    photo: Photo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(imageCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.contentUri,
            contentDescription = "Photo ${photo.photoId}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        CheckboxOverlay(
            isSelected = true,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun CheckboxOverlay(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(4.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
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
