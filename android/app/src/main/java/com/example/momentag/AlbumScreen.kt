package com.example.momentag

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.viewmodel.AlbumViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    // State for main album delete mode
    var isAlbumDeleteMode by remember { mutableStateOf(false) }

    // State for tag name text field
    var currentTagName by remember(tagName) { mutableStateOf(tagName) } // actual name, updates on successful rename
    var editableTagName by remember(tagName) { mutableStateOf(tagName) } // text field's current content

    // State for focus tracking
    var isFocused by remember { mutableStateOf(false) }

    // Controllers for keyboard and focus
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Handle back press to exit delete mode
    BackHandler(enabled = isAlbumDeleteMode) {
        isAlbumDeleteMode = false
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
        if (hasPermission) {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BackTopBar(
                title = "MomenTag",
                onBackClick = {
                    if (isAlbumDeleteMode) {
                        isAlbumDeleteMode = false
                    } else {
                        onNavigateBack()
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
                        textStyle = MaterialTheme.typography.headlineMedium,
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
                        navController = navController,
                        onToggleRecommendPhoto = { photo -> albumViewModel.toggleRecommendPhoto(photo) },
                        onResetSelection = { albumViewModel.resetRecommendSelection() },
                        tagId = tagId,
                        isAlbumDeleteMode = isAlbumDeleteMode,
                        onEnterAlbumDeleteMode = { isAlbumDeleteMode = true },
                        onDeleteTagFromPhoto = { photoId, tagIdValue ->
                            albumViewModel.deleteTagFromPhoto(photoId, tagIdValue)
                        },
                        onAddPhotosToAlbum = { photos ->
                            albumViewModel.addRecommendedPhotosToTagAlbum(photos, tagId, tagName)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumContent(
    albumLoadState: AlbumViewModel.AlbumLoadingState,
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    selectedRecommendPhotos: List<Photo>,
    navController: NavController,
    onToggleRecommendPhoto: (Photo) -> Unit,
    onResetSelection: () -> Unit,
    tagId: String,
    isAlbumDeleteMode: Boolean,
    onEnterAlbumDeleteMode: () -> Unit,
    onDeleteTagFromPhoto: (String, String) -> Unit,
    onAddPhotosToAlbum: (List<Photo>) -> Unit,
) {
    var isRecommendSelectionMode by remember { mutableStateOf(false) }
    var recommendOffsetY by remember { mutableFloatStateOf(600f) }
    val minOffset = 0f
    val maxOffset = 600f

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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 650.dp), // Space for AI Recommend section
                ) {
                    items(
                        count = photos.size,
                        key = { index -> index },
                    ) { index ->
                        ImageGridUriItem(
                            photo = photos[index],
                            navController = navController,
                            onLongPress = {
                                onEnterAlbumDeleteMode()
                            },
                            cornerRadius = 12.dp,
                            topPadding = 0.dp,
                            isAlbumDeleteMode = isAlbumDeleteMode,
                            onDeleteClick = {
                                onDeleteTagFromPhoto(photos[index].photoId, tagId)
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
            is AlbumViewModel.AlbumLoadingState.Idle -> {
            }
        }

        // AI Recommend Section (draggable)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, recommendOffsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                // Snap to closest position
                                recommendOffsetY =
                                    if (recommendOffsetY < (minOffset + maxOffset) / 2) {
                                        minOffset
                                    } else {
                                        maxOffset
                                    }
                            },
                        ) { _, dragAmount ->
                            val newOffset = recommendOffsetY + dragAmount
                            recommendOffsetY = newOffset.coerceIn(minOffset, maxOffset)
                        }
                    }.background(MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp),
        ) {
            Column {
                // Drag handle
                Box(
                    modifier =
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape = CircleShape)
                            .align(Alignment.CenterHorizontally),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // AI Recommend Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI Recommend",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Selection mode toggle (only show when recommendations loaded)
                    if (recommendLoadState is AlbumViewModel.RecommendLoadingState.Success && recommendLoadState.photos.isNotEmpty()) {
                        Button(
                            onClick = {
                                isRecommendSelectionMode = !isRecommendSelectionMode
                                if (!isRecommendSelectionMode) {
                                    onResetSelection()
                                }
                            },
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor =
                                        if (isRecommendSelectionMode) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                        },
                                    contentColor =
                                        if (isRecommendSelectionMode) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        },
                                ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = if (isRecommendSelectionMode) "취소" else "선택",
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // AI Recommendations Grid
                when (recommendLoadState) {
                    is AlbumViewModel.RecommendLoadingState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Success -> {
                        val recommendPhotos = recommendLoadState.photos

                        if (recommendPhotos.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                                Text("추천 사진이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.height(300.dp),
                                ) {
                                    items(
                                        count = recommendPhotos.size,
                                        key = { index -> index },
                                    ) { index ->
                                        ImageGridUriItem(
                                            photo = recommendPhotos[index],
                                            navController = navController,
                                            isSelectionMode = isRecommendSelectionMode,
                                            isSelected = selectedRecommendPhotos.contains(recommendPhotos[index]),
                                            onToggleSelection = {
                                                onToggleRecommendPhoto(recommendPhotos[index])
                                            },
                                            onLongPress = {
                                                isRecommendSelectionMode = true
                                            },
                                            cornerRadius = 12.dp,
                                            topPadding = 0.dp,
                                        )
                                    }
                                }

                                // Add to Album button
                                if (isRecommendSelectionMode && selectedRecommendPhotos.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            // Call the new lambda from ViewModel
                                            onAddPhotosToAlbum(selectedRecommendPhotos)

                                            // Reset UI state
                                            isRecommendSelectionMode = false
                                            onResetSelection()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                    ) {
                                        Text(
                                            text = "Add to Album (${selectedRecommendPhotos.size})",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Error -> {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            Text("추천 사진을 불러오지 못했습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Idle -> {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
