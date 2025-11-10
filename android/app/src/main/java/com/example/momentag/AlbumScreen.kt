package com.example.momentag

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.theme.horizontalArrangement
import com.example.momentag.ui.theme.verticalArrangement
import com.example.momentag.viewmodel.AlbumViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    tagId: String,
    tagName: String,
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val albumViewModel: AlbumViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val imageLoadState by albumViewModel.albumLoadingState.collectAsState()
    val tagDeleteState by albumViewModel.tagDeleteState.collectAsState()
    val tagRenameState by albumViewModel.tagRenameState.collectAsState()
    val tagAddState by albumViewModel.tagAddState.collectAsState()
    val selectedTagAlbumPhotos by albumViewModel.selectedTagAlbumPhotos.collectAsState()

    // State for tag name text field
    var currentTagName by remember(tagName) { mutableStateOf(tagName) } // actual name, updates on successful rename
    var editableTagName by remember(tagName) { mutableStateOf(tagName) } // text field's current content

    // State for focus tracking
    var isFocused by remember { mutableStateOf(false) }

    // Controllers for keyboard and focus
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var isTagAlbumPhotoSelectionMode by remember { mutableStateOf(false) }
    var isTagAlbumPhotoSelectionModeDelay by remember { mutableStateOf(false) } // for dropdown animation

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }

    // BackHandler for Selection Mode
    BackHandler(enabled = isTagAlbumPhotoSelectionMode) {
        isTagAlbumPhotoSelectionMode = false
        albumViewModel.resetTagAlbumPhotoSelection()
    }

    LaunchedEffect(isTagAlbumPhotoSelectionMode) {
        kotlinx.coroutines.delay(200L) // 0.2초
        isTagAlbumPhotoSelectionModeDelay = isTagAlbumPhotoSelectionMode
    }

    LaunchedEffect(tagDeleteState) {
        when (val state = tagDeleteState) {
            is AlbumViewModel.TagDeleteState.Success -> {
                Toast.makeText(context, "Photo removed from album", Toast.LENGTH_SHORT).show()
                albumViewModel.resetDeleteState()
            }
            is AlbumViewModel.TagDeleteState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                albumViewModel.resetDeleteState()
            }
            else -> Unit // Idle, Loading
        }
    }

    LaunchedEffect(tagRenameState) {
        when (val state = tagRenameState) {
            is AlbumViewModel.TagRenameState.Success -> {
                Toast.makeText(context, "Tag renamed", Toast.LENGTH_SHORT).show()
                currentTagName = editableTagName // update the "current" name to the new name
                albumViewModel.resetRenameState()
            }
            is AlbumViewModel.TagRenameState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                editableTagName = currentTagName // reset the text field to the last known good name
                albumViewModel.resetRenameState()
            }
            else -> Unit // Idle, Loading
        }
    }

    // Handle Add Photos state (success/error toast)
    LaunchedEffect(tagAddState) {
        when (val state = tagAddState) {
            is AlbumViewModel.TagAddState.Success -> {
                Toast.makeText(context, "Photos added to album", Toast.LENGTH_SHORT).show()
                albumViewModel.resetAddState()
            }
            is AlbumViewModel.TagAddState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                albumViewModel.resetAddState()
            }
            else -> Unit // Idle, Loading
        }
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

    LaunchedEffect(hasPermission, tagId) {
        if (hasPermission && imageLoadState is AlbumViewModel.AlbumLoadingState.Idle) {
            albumViewModel.loadAlbum(tagId, tagName)
        }
    }

    LaunchedEffect(tagName) {
        currentTagName = tagName
        editableTagName = tagName
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

    val submitAndClearFocus = {
        if (editableTagName.isNotBlank() && editableTagName != currentTagName) {
            albumViewModel.renameTag(tagId, editableTagName)
        } else if (editableTagName.isBlank()) { // If text is blank, revert to the last good name
            editableTagName = currentTagName
        }

        keyboardController?.hide() // Hide keyboard
        focusManager.clearFocus() // Remove focus (cursor)
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = {
                Text(
                    text = "Remove Photos",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove ${selectedTagAlbumPhotos.size} photo(s) from '$currentTagName' tag?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        albumViewModel.deleteTagFromPhotos(
                            photos = selectedTagAlbumPhotos,
                            tagId = tagId,
                        )
                        Toast.makeText(context, "${selectedTagAlbumPhotos.size} photo(s) removed", Toast.LENGTH_SHORT).show()

                        showDeleteConfirmationDialog = false
                        isTagAlbumPhotoSelectionMode = false
                        showMenu = false
                        albumViewModel.resetTagAlbumPhotoSelection()
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CommonTopBar(
                title = "MomenTag",
                showBackButton = true,
                onBackClick = {
                    if (isTagAlbumPhotoSelectionMode) {
                        isTagAlbumPhotoSelectionMode = false
                        albumViewModel.resetTagAlbumPhotoSelection()
                    } else {
                        onNavigateBack()
                    }
                },
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
                            if (isTagAlbumPhotoSelectionModeDelay) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = {
                                        val photos = albumViewModel.getPhotosToShare()
                                        ShareUtils.sharePhotos(context, photos)

                                        Toast
                                            .makeText(
                                                context,
                                                "Share ${photos.size} photo(s)",
                                                Toast.LENGTH_SHORT,
                                            ).show()

                                        showMenu = false
                                        isTagAlbumPhotoSelectionMode = false
                                        albumViewModel.resetTagAlbumPhotoSelection()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        if (selectedTagAlbumPhotos.isNotEmpty()) {
                                            showDeleteConfirmationDialog = true
                                        } else {
                                            Toast.makeText(context, "Please select photos", Toast.LENGTH_SHORT).show()
                                        }
                                        showMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Cancel") },
                                    onClick = {
                                        albumViewModel.resetTagAlbumPhotoSelection()
                                        isTagAlbumPhotoSelectionMode = false
                                        showMenu = false
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    onClick = {
                                        isTagAlbumPhotoSelectionMode = true
                                        showMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = imageLoadState is AlbumViewModel.AlbumLoadingState.Loading,
            onRefresh = {
                scope.launch {
                    if (hasPermission) {
                        albumViewModel.loadAlbum(tagId, tagName)
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
                        .padding(horizontal = 16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            submitAndClearFocus() // Run the same logic as "Done"
                        },
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicTextField(
                        value = editableTagName,
                        onValueChange = { editableTagName = it },
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .onFocusChanged { focusState ->
                                    isFocused = focusState.isFocused
                                },
                        textStyle =
                            MaterialTheme.typography.displayMedium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    submitAndClearFocus()
                                },
                            ),
                        singleLine = true,
                    )

                    // 'Clear text' (x) button
                    // Show only when focused AND not empty
                    if (editableTagName.isNotEmpty() && isFocused) {
                        IconButton(
                            onClick = { editableTagName = "" },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear text",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )

                if (!hasPermission) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("이미지 접근 권한을 허용해주세요.")
                    }
                } else {
                    AlbumContent(
                        albumLoadState = imageLoadState,
                        recommendLoadState = albumViewModel.recommendLoadingState.collectAsState().value,
                        selectedRecommendPhotos = albumViewModel.selectedRecommendPhotos.collectAsState().value,
                        selectedTagAlbumPhotos = selectedTagAlbumPhotos,
                        navController = navController,
                        onToggleRecommendPhoto = { photo -> albumViewModel.toggleRecommendPhoto(photo) },
                        onResetRecommendSelection = { albumViewModel.resetRecommendSelection() },
                        onToggleTagAlbumPhoto = { photo -> albumViewModel.toggleTagAlbumPhoto(photo) },
                        onAddPhotosToAlbum = { photos ->
                            albumViewModel.addRecommendedPhotosToTagAlbum(photos, tagId, tagName)
                        },
                        isTagAlbumPhotoSelectionMode = isTagAlbumPhotoSelectionMode,
                        onSetTagAlbumPhotoSelectionMode = { isTagAlbumPhotoSelectionMode = it },
                    )
                }
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun AlbumContent(
    albumLoadState: AlbumViewModel.AlbumLoadingState,
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    selectedRecommendPhotos: List<Photo>,
    selectedTagAlbumPhotos: List<Photo>,
    navController: NavController,
    onToggleRecommendPhoto: (Photo) -> Unit,
    onResetRecommendSelection: () -> Unit,
    onToggleTagAlbumPhoto: (Photo) -> Unit,
    onAddPhotosToAlbum: (List<Photo>) -> Unit,
    isTagAlbumPhotoSelectionMode: Boolean,
    onSetTagAlbumPhotoSelectionMode: (Boolean) -> Unit,
) {
    var isRecommendationExpanded by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    
    // 사용자가 조절 가능한 패널 높이
    val minHeight = 200.dp
    val maxHeight = (configuration.screenHeightDp * 0.6f).dp
    var panelHeight by remember { mutableStateOf((configuration.screenHeightDp / 3).dp) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tag Album Grid
        when (albumLoadState) {
            is AlbumViewModel.AlbumLoadingState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AlbumViewModel.AlbumLoadingState.Success -> {
                val photos = albumLoadState.photos

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(verticalArrangement),
                    horizontalArrangement = Arrangement.spacedBy(horizontalArrangement),
                    contentPadding = PaddingValues(
                        bottom = if (isRecommendationExpanded) panelHeight else 80.dp
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        count = photos.size,
                        key = { index -> index },
                    ) { index ->
                        ImageGridUriItem(
                            photo = photos[index],
                            navController = navController,
                            isSelectionMode = isTagAlbumPhotoSelectionMode,
                            isSelected = selectedTagAlbumPhotos.contains(photos[index]),
                            onToggleSelection = {
                                onToggleTagAlbumPhoto(photos[index])
                            },
                            onLongPress = {
                                onSetTagAlbumPhotoSelectionMode(true)
                            },
                        )
                    }
                }
            }
            is AlbumViewModel.AlbumLoadingState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("사진을 불러오지 못했습니다.\n아래로 당겨 새로고침하세요.")
                }
            }
            is AlbumViewModel.AlbumLoadingState.Idle -> {}
        }

        // AI Recommend Section - 축소/확장 가능
        AnimatedVisibility(
            visible = !isRecommendationExpanded,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            RecommendChip(
                recommendLoadState = recommendLoadState,
                onExpand = { isRecommendationExpanded = true }
            )
        }

        if (isRecommendationExpanded) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // 확장 상태: 하단 패널 (패딩 없이 딱 붙임)
                RecommendExpandedPanel(
                    recommendLoadState = recommendLoadState,
                    selectedRecommendPhotos = selectedRecommendPhotos,
                    navController = navController,
                    onToggleRecommendPhoto = onToggleRecommendPhoto,
                    onResetRecommendSelection = onResetRecommendSelection,
                    onAddPhotosToAlbum = onAddPhotosToAlbum,
                    panelHeight = panelHeight,
                    onHeightChange = { delta ->
                        panelHeight = (panelHeight - delta).coerceIn(minHeight, maxHeight)
                    },
                    onCollapse = { isRecommendationExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun RecommendChip(
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    onExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onExpand)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        when (recommendLoadState) {
            is AlbumViewModel.RecommendLoadingState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Recommending...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is AlbumViewModel.RecommendLoadingState.Success -> {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Recommend",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is AlbumViewModel.RecommendLoadingState.Error -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recommendation Failed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is AlbumViewModel.RecommendLoadingState.Idle -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Preparing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = "Expand",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun RecommendExpandedPanel(
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    selectedRecommendPhotos: List<Photo>,
    navController: NavController,
    onToggleRecommendPhoto: (Photo) -> Unit,
    onResetRecommendSelection: () -> Unit,
    onAddPhotosToAlbum: (List<Photo>) -> Unit,
    panelHeight: Dp,
    onHeightChange: (Dp) -> Unit,
    onCollapse: () -> Unit
) {
    val density = LocalDensity.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        Column {
            // Drag Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            with(density) {
                                onHeightChange(dragAmount.toDp())
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (recommendLoadState is AlbumViewModel.RecommendLoadingState.Success &&
                        recommendLoadState.photos.isNotEmpty() &&
                        selectedRecommendPhotos.isNotEmpty()
                    ) {
                        // 사진 선택 시: Add와 Cancel 버튼
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onResetRecommendSelection) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    onAddPhotosToAlbum(selectedRecommendPhotos)
                                    onResetRecommendSelection()
                                    onCollapse()
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Add ${selectedRecommendPhotos.size} Photo${if (selectedRecommendPhotos.size > 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    } else {
                        // 선택 없을 시: AI Recommend 텍스트 (왼쪽)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (recommendLoadState) {
                                is AlbumViewModel.RecommendLoadingState.Loading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                                is AlbumViewModel.RecommendLoadingState.Success -> {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AI Recommend",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 오른쪽: 닫기 버튼 (항상 표시)
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Grid
                when (recommendLoadState) {
                    is AlbumViewModel.RecommendLoadingState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Success -> {
                        val recommendPhotos = recommendLoadState.photos

                        if (recommendPhotos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No recommendations available",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(verticalArrangement),
                                horizontalArrangement = Arrangement.spacedBy(horizontalArrangement),
                                modifier = Modifier.weight(1f),
                                userScrollEnabled = true
                            ) {
                                items(
                                    count = recommendPhotos.size,
                                    key = { index -> recommendPhotos[index].photoId }
                                ) { index ->
                                    ImageGridUriItem(
                                        photo = recommendPhotos[index],
                                        navController = navController,
                                        isSelectionMode = true,
                                        isSelected = selectedRecommendPhotos.contains(recommendPhotos[index]),
                                        onToggleSelection = {
                                            onToggleRecommendPhoto(recommendPhotos[index])
                                        },
                                        onLongPress = {}
                                    )
                                }
                            }
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Failed to load recommendations",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Idle -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
