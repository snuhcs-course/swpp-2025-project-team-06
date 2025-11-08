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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Purple80
import com.example.momentag.ui.theme.horizontalArrangement
import com.example.momentag.ui.theme.verticalArrangement
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

    // Dropdown Menu States
    var showMenu by remember { mutableStateOf(false) }

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
        containerColor = Background,
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
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            if (isTagAlbumPhotoSelectionModeDelay) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    onClick = {
                                        // TODO: share selectedTagAlbumPhotos
                                        Toast.makeText(
                                            context,
                                            "Share ${selectedTagAlbumPhotos.size} photo(s) (TODO)",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        showMenu = false
                                        isTagAlbumPhotoSelectionMode = false
                                        albumViewModel.resetTagAlbumPhotoSelection()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        if (selectedTagAlbumPhotos.isNotEmpty()) {
                                            albumViewModel.deleteTagFromPhotos(
                                                photos = selectedTagAlbumPhotos,
                                                tagId = tagId
                                            )
                                            Toast.makeText(
                                                context,
                                                "delete ${selectedTagAlbumPhotos.size} photo(s) from tag",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        showMenu = false
                                        isTagAlbumPhotoSelectionMode = false
                                        albumViewModel.resetTagAlbumPhotoSelection()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    onClick = {
                                        isTagAlbumPhotoSelectionMode = true
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
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
                            TextStyle(
                                fontSize = 28.sp,
                                fontFamily = FontFamily.Serif,
                                color = LocalContentColor.current,
                            ),
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
                                        .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear text",
                                    tint = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    color = Color.Black.copy(alpha = 0.5f),
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
                        onResetTagAlbumPhotoSelection = { albumViewModel.resetTagAlbumPhotoSelection() },
                        tagId = tagId,
                        onAddPhotosToAlbum = { photos ->
                            albumViewModel.addRecommendedPhotosToTagAlbum(photos, tagId, tagName)
                        },
                        isTagAlbumPhotoSelectionMode = isTagAlbumPhotoSelectionMode,
                        onSetTagAlbumPhotoSelectionMode = { isTagAlbumPhotoSelectionMode = it }
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
    selectedTagAlbumPhotos: List<Photo>,
    navController: NavController,
    onToggleRecommendPhoto: (Photo) -> Unit,
    onResetRecommendSelection: () -> Unit,
    onToggleTagAlbumPhoto: (Photo) -> Unit,
    onResetTagAlbumPhotoSelection: () -> Unit,
    tagId: String,
    onAddPhotosToAlbum: (List<Photo>) -> Unit,
    isTagAlbumPhotoSelectionMode: Boolean,
    onSetTagAlbumPhotoSelectionMode: (Boolean) -> Unit,
) {
    var isRecommendSelectionMode by remember { mutableStateOf(false) }
    var recommendOffsetY by remember { mutableFloatStateOf(600f) }
    val minOffset = 0f
    val maxOffset = 600f

    val buttonShape = RoundedCornerShape(16.dp)
    val buttonPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

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
                    contentPadding = PaddingValues(bottom = 650.dp), // Space for AI Recommend section
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
                    }.background(Background, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp),
        ) {
            Column {
                // Drag handle
                Box(
                    modifier =
                        Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color.Gray.copy(alpha = 0.4f), shape = CircleShape)
                            .align(Alignment.CenterHorizontally),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // AI Recommend Badge
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (recommendLoadState is AlbumViewModel.RecommendLoadingState.Success && recommendLoadState.photos.isNotEmpty()) {
                        if (isRecommendSelectionMode) {
                            // X button
                            IconButton(
                                onClick = {
                                    isRecommendSelectionMode = false
                                    onResetRecommendSelection()
                                },
                                modifier = Modifier.align(Alignment.CenterStart),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel selection",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Add to album button
                            Button(
                                onClick = {
                                    onAddPhotosToAlbum(selectedRecommendPhotos)
                                    isRecommendSelectionMode = false
                                    onResetRecommendSelection()
                                },
                                enabled = selectedRecommendPhotos.isNotEmpty(), // 선택된 사진이 있어야 활성화
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                                shape = buttonShape,
                                contentPadding = buttonPadding,
                            ) {
                                Text(
                                    text = "Add to Album (${selectedRecommendPhotos.size})",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Non-selection mode
                            Button(
                                onClick = { isRecommendSelectionMode = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple80),
                                shape = buttonShape,
                                contentPadding = buttonPadding,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "AI Recommend",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    } else {
                        // Empty recommended result or Loading state - non-clickable button "AI recommend"
                        Button(
                            onClick = {}, // can not click
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Purple80,
                                disabledContainerColor = Purple80.copy(alpha = 0.5f) // 비활성 색
                            ),
                            shape = buttonShape,
                            contentPadding = buttonPadding,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AI Recommend",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
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
                                Text("추천 사진이 없습니다.", color = Color.Gray)
                            }
                        } else {
                            Column {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    verticalArrangement = Arrangement.spacedBy(verticalArrangement),
                                    horizontalArrangement = Arrangement.spacedBy(horizontalArrangement),
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
                                        )
                                    }
                                }
                            }
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Error -> {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            Text("추천 사진을 불러오지 못했습니다.", color = Color.Gray)
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
