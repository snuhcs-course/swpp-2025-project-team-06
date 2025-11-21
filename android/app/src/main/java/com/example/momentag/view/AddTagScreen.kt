package com.example.momentag.view

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.AddPhotosButton
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import com.example.momentag.viewmodel.AddTagViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagScreen(navController: NavController) {
    // Context and platform-related variables
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // ViewModel instance
    val addTagViewModel: AddTagViewModel = hiltViewModel()

    // State collected from ViewModel
    val tagName by addTagViewModel.tagName.collectAsState()
    val selectedPhotos by addTagViewModel.selectedPhotos.collectAsState()
    val saveState by addTagViewModel.saveState.collectAsState()
    val isTagNameDuplicate by addTagViewModel.isTagNameDuplicate.collectAsState()

    // Local state variables
    var hasPermission by remember { mutableStateOf(false) }
    var isChanged by remember { mutableStateOf(true) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    val backgroundBrush = rememberAppBackgroundBrush()

    // Permission launcher
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    // Callback functions
    val onDeselectPhoto: (Photo) -> Unit = { photo ->
        isChanged = true
        addTagViewModel.removePhoto(photo)
    }

    // Request permissions on first launch
    LaunchedEffect(key1 = true) {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        permissionLauncher.launch(permission)
    }

    LaunchedEffect(saveState) {
        when (saveState) {
            is AddTagViewModel.SaveState.Success -> {
                addTagViewModel.clearDraft()
                navController.popBackStack()
            }
            is AddTagViewModel.SaveState.Error -> {
                isErrorBannerVisible = true
                delay(2000)
                isErrorBannerVisible = false
            }
            else -> { }
        }
    }

    // Handle back button
    BackHandler {
        addTagViewModel.clearDraft()
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.tag_create_title),
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
                    addTagViewModel.clearDraft()

                    when (tab) {
                        BottomTab.HomeScreen ->
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        BottomTab.MyTagsScreen ->
                            navController.navigate(Screen.MyTags.route) {
                                popUpTo(Screen.Home.route)
                            }
                        BottomTab.StoryScreen ->
                            navController.navigate(Screen.Story.route) {
                                popUpTo(Screen.Home.route)
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
                    .background(backgroundBrush)
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
                            .padding(horizontal = Dimen.ScreenHorizontalPadding),
                ) {
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                    // Tag Name Section
                    TagNameSection(
                        tagName = tagName,
                        onTagNameChange = { addTagViewModel.updateTagName(it) },
                        isDuplicate = isTagNameDuplicate,
                    )

                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                    // Added Pictures Section
                    Text(
                        text =
                            if (selectedPhotos.isEmpty()) {
                                stringResource(R.string.tag_photos_label)
                            } else {
                                stringResource(R.string.tag_photos_count, selectedPhotos.size)
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

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
                            .padding(horizontal = Dimen.ScreenHorizontalPadding, vertical = Dimen.ItemSpacingSmall),
                ) {
                    // Error Banner - Floating above Done button
                    AnimatedVisibility(
                        visible = isErrorBannerVisible && saveState is AddTagViewModel.SaveState.Error,
                        enter = Animation.EnterFromBottom,
                        exit = Animation.ExitToBottom,
                    ) {
                        val errorState = saveState as AddTagViewModel.SaveState.Error
                        val errorMessage =
                            when (errorState.error) {
                                AddTagViewModel.AddTagError.NetworkError -> stringResource(R.string.error_message_network)
                                AddTagViewModel.AddTagError.Unauthorized -> stringResource(R.string.error_message_authentication_required)
                                AddTagViewModel.AddTagError.EmptyName -> stringResource(R.string.validation_required_tag_name)
                                AddTagViewModel.AddTagError.NoPhotos -> stringResource(R.string.help_select_photos)
                                AddTagViewModel.AddTagError.UnknownError -> stringResource(R.string.error_message_save_tag)
                            }
                        WarningBanner(
                            title = stringResource(R.string.error_message_save_tag),
                            message = errorMessage,
                            onActionClick = { },
                            onDismiss = { isErrorBannerVisible = false },
                            showActionButton = false,
                            showDismissButton = true,
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                    // Done Button
                    AnimatedVisibility(
                        visible = isChanged,
                        enter = Animation.EnterFromBottom,
                        exit = Animation.ExitToBottom,
                    ) {
                        val isFormValid = selectedPhotos.isNotEmpty() && tagName.isNotBlank()
                        val canSubmit = isFormValid && saveState != AddTagViewModel.SaveState.Loading

                        Button(
                            onClick = {
                                addTagViewModel.saveTagAndPhotos()
                            },
                            shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(Dimen.ButtonHeightLarge)
                                    .shadow(
                                        elevation = if (canSubmit) Dimen.ButtonShadowElevation else Dimen.ButtonDisabledShadowElevation,
                                        shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
                                        clip = false,
                                    ),
                            enabled = canSubmit,
                        ) {
                            Text(
                                stringResource(R.string.action_done),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                // Full Screen Loading Overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = saveState == AddTagViewModel.SaveState.Loading,
                    enter = Animation.DefaultFadeIn,
                    exit = Animation.DefaultFadeOut,
                ) {
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
                                    .size(Dimen.BottomNavBarHeight)
                                    .shadow(
                                        elevation = Dimen.BottomNavShadowElevation,
                                        shape = CircleShape,
                                        clip = false,
                                    ),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = Dimen.CircularProgressStrokeWidthBig,
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
    isDuplicate: Boolean,
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    var showHelpText by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        showHelpText = true
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.width(Dimen.GridItemSpacing))
            BasicTextField(
                value = tagName,
                onValueChange = onTagNameChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused },
                textStyle =
                    MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (tagName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.field_enter_tag_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                },
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = Dimen.ItemSpacingSmall, bottom = Dimen.ItemSpacingSmall),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        )

        AnimatedVisibility(
            visible = isDuplicate,
            enter = expandVertically(expandFrom = Alignment.Top) + Animation.DefaultFadeIn,
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + Animation.DefaultFadeOut,
        ) {
            Text(
                text = stringResource(R.string.validation_tag_exists, tagName),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        AnimatedVisibility(
            visible = showHelpText && tagName.isEmpty() && !isFocused,
            enter = expandVertically(expandFrom = Alignment.Top) + Animation.DefaultFadeIn,
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + Animation.DefaultFadeOut,
        ) {
            Text(
                text = stringResource(R.string.help_tag_name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        verticalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
        horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = Dimen.ItemSpacingSmall),
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
                .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.contentUri,
            contentDescription = stringResource(R.string.cd_photo_item, photo.photoId),
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
                .padding(Dimen.GridItemSpacing)
                .size(Dimen.IconButtonSizeSmall)
                .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
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
            StandardIcon.Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.cd_photo_selected),
                sizeRole = IconSizeRole.InlineAction,
                intent = IconIntent.OnPrimaryContainer,
            )
        }
    }
}
