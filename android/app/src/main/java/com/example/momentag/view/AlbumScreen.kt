package com.example.momentag.view

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.AddPhotosButton
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.util.ShareUtils
import com.example.momentag.viewmodel.AlbumViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    tagId: String,
    tagName: String,
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 2. ViewModel 인스턴스
    val albumViewModel: AlbumViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val imageLoadState by albumViewModel.albumLoadingState.collectAsState()
    val tagDeleteState by albumViewModel.tagDeleteState.collectAsState()
    val tagRenameState by albumViewModel.tagRenameState.collectAsState()
    val tagAddState by albumViewModel.tagAddState.collectAsState()
    val selectedTagAlbumPhotos by albumViewModel.selectedTagAlbumPhotos.collectAsState()

    // 4. 로컬 상태 변수
    var hasPermission by remember { mutableStateOf(false) }
    var currentTagName by remember(tagName) { mutableStateOf(tagName) }
    var editableTagName by remember(tagName) { mutableStateOf(tagName) }
    var isFocused by remember { mutableStateOf(false) }
    var isTagAlbumPhotoSelectionMode by remember { mutableStateOf(false) }
    var isTagAlbumPhotoSelectionModeDelay by remember { mutableStateOf(false) }
    var isDeleteConfirmationDialogVisible by remember { mutableStateOf(false) }
    var isRecommendationExpanded by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorBannerTitle by remember { mutableStateOf("Error") }
    var errorBannerMessage by remember { mutableStateOf("An error occurred") }
    var isSelectPhotosBannerShareVisible by remember { mutableStateOf(false) }
    var isSelectPhotosBannerUntagVisible by remember { mutableStateOf(false) }

    // 5. Derived 상태 및 계산된 값
    val minPanelHeight = Dimen.ExpandedPanelMinHeight
    val maxPanelHeight = (config.screenHeightDp * 0.6f).dp
    var panelHeight by remember(config) { mutableStateOf((config.screenHeightDp / 3).dp) }

    // 6. rememberCoroutineScope & ActivityResultLauncher
    val scope = rememberCoroutineScope()
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )
    val submitAndClearFocus = {
        if (editableTagName.isNotBlank() && editableTagName != currentTagName) {
            albumViewModel.renameTag(tagId, editableTagName)
        } else if (editableTagName.isBlank()) { // If text is blank, revert to the last good name
            editableTagName = currentTagName
        }

        keyboardController?.hide() // Hide keyboard
        focusManager.clearFocus() // Remove focus (cursor)
    }
    // 7. LaunchedEffect
    LaunchedEffect(isSelectPhotosBannerShareVisible) {
        if (isSelectPhotosBannerShareVisible) {
            delay(2000)
            isSelectPhotosBannerShareVisible = false
        }
    }

    LaunchedEffect(isSelectPhotosBannerUntagVisible) {
        if (isSelectPhotosBannerUntagVisible) {
            delay(2000)
            isSelectPhotosBannerUntagVisible = false
        }
    }

    LaunchedEffect(isTagAlbumPhotoSelectionMode) {
        delay(200L) // 0.2초
        isTagAlbumPhotoSelectionModeDelay = isTagAlbumPhotoSelectionMode
    }

    LaunchedEffect(tagDeleteState) {
        when (val state = tagDeleteState) {
            is AlbumViewModel.TagDeleteState.Success -> {
                Toast.makeText(context, "Photo removed from album", Toast.LENGTH_SHORT).show() // 성공: Toast
                albumViewModel.resetDeleteState()
                isErrorBannerVisible = false
            }
            is AlbumViewModel.TagDeleteState.Error -> {
                errorBannerTitle = "Failed to Remove Photo"
                errorBannerMessage = state.message
                isErrorBannerVisible = true // 실패: Banner
                albumViewModel.resetDeleteState()
            }
            else -> Unit // Idle, Loading
        }
    }

    LaunchedEffect(tagRenameState) {
        when (val state = tagRenameState) {
            is AlbumViewModel.TagRenameState.Success -> {
                Toast.makeText(context, "Tag renamed", Toast.LENGTH_SHORT).show() // 성공: Toast
                currentTagName = editableTagName
                albumViewModel.resetRenameState()
                isErrorBannerVisible = false
            }
            is AlbumViewModel.TagRenameState.Error -> {
                errorBannerTitle = "Failed to Rename Tag"
                errorBannerMessage = state.message
                isErrorBannerVisible = true // 실패: Banner
                editableTagName = currentTagName
                albumViewModel.resetRenameState()
            }
            else -> Unit // Idle, Loading
        }
    }

    LaunchedEffect(tagAddState) {
        when (val state = tagAddState) {
            is AlbumViewModel.TagAddState.Success -> {
                Toast.makeText(context, "Photos added to album", Toast.LENGTH_SHORT).show() // 성공: Toast
                albumViewModel.resetAddState()
                isErrorBannerVisible = false
            }
            is AlbumViewModel.TagAddState.Error -> {
                errorBannerTitle = "Failed to Add Photos"
                errorBannerMessage = state.message
                isErrorBannerVisible = true // 실패: Banner
                albumViewModel.resetAddState()
            }
            else -> Unit
        }
    }

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

    // Refresh album when returning from SelectImageScreen
    LaunchedEffect(navController) {
        val currentBackStackEntry = navController.currentBackStackEntry
        val savedStateHandle = currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<Boolean>("photos_added")?.observeForever { photosAdded ->
            if (photosAdded == true) {
                albumViewModel.loadAlbum(tagId, currentTagName)
                savedStateHandle.remove<Boolean>("photos_added")
            }
        }
    }

    BackHandler(enabled = isRecommendationExpanded || isTagAlbumPhotoSelectionMode) {
        if (isRecommendationExpanded) {
            isRecommendationExpanded = false
        } else if (isTagAlbumPhotoSelectionMode) {
            isTagAlbumPhotoSelectionMode = false
            albumViewModel.resetTagAlbumPhotoSelection()
        }
    }

    if (isDeleteConfirmationDialogVisible) {
        AlertDialog(
            onDismissRequest = { isDeleteConfirmationDialogVisible = false },
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
                        Toast
                            .makeText(
                                context,
                                "${selectedTagAlbumPhotos.size} photo(s) removed",
                                Toast.LENGTH_SHORT,
                            ).show()

                        isDeleteConfirmationDialogVisible = false
                        isTagAlbumPhotoSelectionMode = false
                        albumViewModel.resetTagAlbumPhotoSelection()
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { isDeleteConfirmationDialogVisible = false }) {
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
                    if (isTagAlbumPhotoSelectionMode) {
                        val isEnabled = selectedTagAlbumPhotos.isNotEmpty()
                        Row {
                            IconButton(
                                onClick = {
                                    val photos = albumViewModel.getPhotosToShare()
                                    ShareUtils.sharePhotos(context, photos)
                                    if (photos.isNotEmpty()) {
                                        Toast
                                            .makeText(
                                                context,
                                                "Share ${photos.size} photo(s)",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                                enabled = isEnabled,
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    ),
                                modifier = Modifier.width(Dimen.IconButtonSizeMediumLarge),
                            ) {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    sizeRole = IconSizeRole.DefaultAction,
                                    intent = if (isEnabled) IconIntent.Primary else IconIntent.Disabled,
                                )
                            }
                            IconButton(
                                onClick = { isDeleteConfirmationDialogVisible = true },
                                enabled = isEnabled,
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        contentColor = Color(0xFFD32F2F),
                                        disabledContentColor = Color(0xFFD32F2F).copy(alpha = 0.38f),
                                    ),
                            ) {
                                StandardIcon.Icon(
                                    imageVector = Icons.AutoMirrored.Filled.LabelOff,
                                    contentDescription = "Untag",
                                    sizeRole = IconSizeRole.DefaultAction,
                                    intent = if (isEnabled) IconIntent.Error else IconIntent.Disabled,
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->

        // === 최상단 레이어 컨테이너: Edge-to-edge 오버레이를 Column 밖의 sibling으로 렌더링 ===
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // 당겨서 새로고침은 본문 레이어에만
            PullToRefreshBox(
                isRefreshing = imageLoadState is AlbumViewModel.AlbumLoadingState.Loading,
                onRefresh = {
                    scope.launch {
                        if (hasPermission) albumViewModel.loadAlbum(tagId, tagName)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                // 본문: 가로 16dp 패딩
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimen.ScreenHorizontalPadding)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { submitAndClearFocus() },
                ) {
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                    // 제목(태그명) 행
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
                                    .padding(end = Dimen.ItemSpacingSmall)
                                    .onFocusChanged { isFocused = it.isFocused },
                            textStyle =
                                MaterialTheme.typography.displayMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitAndClearFocus() }),
                            singleLine = true,
                        )

                        if (editableTagName.isNotEmpty() && isFocused) {
                            IconButton(
                                onClick = { editableTagName = "" },
                                modifier = Modifier.size(Dimen.IconButtonSizeMedium),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(Dimen.IconButtonSizeSmall)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                shape = CircleShape,
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    StandardIcon.Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear text",
                                        sizeRole = IconSizeRole.InlineAction,
                                        tintOverride = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = Dimen.ItemSpacingSmall, bottom = Dimen.SectionSpacing),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )

                    AnimatedVisibility(visible = isSelectPhotosBannerShareVisible) {
                        WarningBanner(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimen.ItemSpacingSmall),
                            title = "No Photos Selected",
                            message = "Please select photos to share.",
                            onActionClick = { isSelectPhotosBannerShareVisible = false },
                            showActionButton = false,
                            showDismissButton = true,
                            onDismiss = { isSelectPhotosBannerShareVisible = false },
                        )
                    }

                    AnimatedVisibility(visible = isSelectPhotosBannerUntagVisible) {
                        WarningBanner(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimen.ItemSpacingSmall),
                            title = "No Photos Selected",
                            message = "Please select photos to untag.",
                            onActionClick = { isSelectPhotosBannerUntagVisible = false },
                            showActionButton = false,
                            showDismissButton = true,
                            onDismiss = { isSelectPhotosBannerUntagVisible = false },
                        )
                    }

                    AnimatedVisibility(visible = isErrorBannerVisible) {
                        WarningBanner(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimen.ItemSpacingSmall),
                            title = errorBannerTitle,
                            message = errorBannerMessage,
                            onActionClick = { isErrorBannerVisible = false },
                            showActionButton = false,
                            showDismissButton = true,
                            onDismiss = { isErrorBannerVisible = false },
                        )
                    }

                    if (!hasPermission) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Please allow access to your photos.")
                        }
                    } else {
                        AlbumGridArea(
                            albumLoadState = imageLoadState,
                            recommendLoadState = albumViewModel.recommendLoadingState.collectAsState().value,
                            selectedTagAlbumPhotos = selectedTagAlbumPhotos,
                            navController = navController,
                            isTagAlbumPhotoSelectionMode = isTagAlbumPhotoSelectionMode,
                            onSetTagAlbumPhotoSelectionMode = { isTagAlbumPhotoSelectionMode = it },
                            onToggleTagAlbumPhoto = { photo -> albumViewModel.toggleTagAlbumPhoto(photo) },
                            // 펼쳐짐 여부와 패널 높이에 따라 그리드 bottom padding 조절
                            isRecommendationExpanded = isRecommendationExpanded,
                            panelHeight = panelHeight,
                            // Chip 클릭 시 오버레이 열기
                            onExpandRecommend = { isRecommendationExpanded = true },
                            // Add photos button handler
                            albumViewModel = albumViewModel,
                            tagId = tagId,
                            tagName = currentTagName,
                        )
                    }
                }
            }

            // === Edge-to-edge 오버레이 (Column 바깥, 동일 Box의 sibling) ===
            if (isRecommendationExpanded) {
                RecommendExpandedPanel(
                    recommendLoadState = albumViewModel.recommendLoadingState.collectAsState().value,
                    selectedRecommendPhotos = albumViewModel.selectedRecommendPhotos.collectAsState().value,
                    navController = navController,
                    onToggleRecommendPhoto = { photo -> albumViewModel.toggleRecommendPhoto(photo) },
                    onResetRecommendSelection = { albumViewModel.resetRecommendSelection() },
                    onAddPhotosToAlbum = { photos ->
                        albumViewModel.addRecommendedPhotosToTagAlbum(photos, tagId, tagName)
                    },
                    panelHeight = panelHeight,
                    onHeightChange = { delta ->
                        panelHeight = (panelHeight - delta).coerceIn(minPanelHeight, maxPanelHeight)
                    },
                    onCollapse = { isRecommendationExpanded = false },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun AlbumGridArea(
    albumLoadState: AlbumViewModel.AlbumLoadingState,
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    selectedTagAlbumPhotos: List<Photo>,
    navController: NavController,
    isTagAlbumPhotoSelectionMode: Boolean,
    onSetTagAlbumPhotoSelectionMode: (Boolean) -> Unit,
    onToggleTagAlbumPhoto: (Photo) -> Unit,
    isRecommendationExpanded: Boolean,
    panelHeight: Dp,
    onExpandRecommend: () -> Unit,
    albumViewModel: AlbumViewModel,
    tagId: String,
    tagName: String,
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
                    verticalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                    contentPadding =
                        PaddingValues(
                            bottom = if (isRecommendationExpanded) panelHeight else Dimen.FloatingButtonAreaPadding,
                        ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Add Photos Button as first item
                    item(
                        key = "add_photos_button",
                    ) {
                        AddPhotosButton(
                            onClick = {
                                albumViewModel.initializeAddPhotosFlow(tagId, tagName)
                                navController.navigate(Screen.SelectImage.route)
                            },
                            modifier = Modifier.aspectRatio(1f),
                        )
                    }

                    // Photo grid items
                    items(
                        count = photos.size,
                        key = { index -> "photo_$index" },
                    ) { index ->
                        ImageGridUriItem(
                            photo = photos[index],
                            navController = navController,
                            isSelectionMode = isTagAlbumPhotoSelectionMode,
                            isSelected = selectedTagAlbumPhotos.contains(photos[index]),
                            onToggleSelection = { onToggleTagAlbumPhoto(photos[index]) },
                            onLongPress = { onSetTagAlbumPhotoSelectionMode(true) },
                        )
                    }
                }
            }
            is AlbumViewModel.AlbumLoadingState.Error -> {
                WarningBanner(
                    modifier = Modifier.fillMaxWidth(),
                    title = "Failed to Load Album",
                    message = albumLoadState.message,
                    onActionClick = { /* PullToRefresh가 처리 */ },
                    showActionButton = false,
                    showDismissButton = false,
                )
            }
            is AlbumViewModel.AlbumLoadingState.Idle -> {}
        }

        // 축소 상태의 Chip (그리드 위에)
        AnimatedVisibility(
            visible = !isRecommendationExpanded,
            enter = fadeIn(tween(300)) + expandVertically(tween(300)),
            exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimen.ItemSpacingLarge),
        ) {
            RecommendChip(
                recommendLoadState = recommendLoadState,
                onExpand = onExpandRecommend,
            )
        }
    }
}

@Composable
private fun RecommendChip(
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    onExpand: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .shadow(elevation = Dimen.BottomNavTonalElevation, shape = RoundedCornerShape(Dimen.Radius20))
                .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(Dimen.Radius20))
                .clip(RoundedCornerShape(Dimen.Radius20))
                .clickable(onClick = onExpand)
                .padding(horizontal = Dimen.ButtonPaddingHorizontal, vertical = Dimen.ButtonPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when (recommendLoadState) {
            is AlbumViewModel.RecommendLoadingState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimen.CircularProgressSizeXSmall),
                    strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = "AI Recommending...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is AlbumViewModel.RecommendLoadingState.Success -> {
                StandardIcon.Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    sizeRole = IconSizeRole.StatusIndicator,
                    intent = IconIntent.Primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = "AI Recommend",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is AlbumViewModel.RecommendLoadingState.Error -> {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Error",
                    sizeRole = IconSizeRole.StatusIndicator,
                    intent = IconIntent.Error,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = "Recommendation Failed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is AlbumViewModel.RecommendLoadingState.Idle -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimen.CircularProgressSizeXSmall),
                    strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = "Preparing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimen.GridItemSpacing))
        StandardIcon.Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = "Expand",
            sizeRole = IconSizeRole.StatusIndicator,
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
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 터치이벤트 뒤로 전달 되지 않도록
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(panelHeight)
                .shadow(
                    elevation = Dimen.BottomNavShadowElevation,
                    shape = RoundedCornerShape(topStart = Dimen.SearchBarCornerRadius, topEnd = Dimen.SearchBarCornerRadius),
                ).background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(topStart = Dimen.SearchBarCornerRadius, topEnd = Dimen.SearchBarCornerRadius),
                ).clip(RoundedCornerShape(topStart = Dimen.SearchBarCornerRadius, topEnd = Dimen.SearchBarCornerRadius)),
    ) {
        Column {
            // Drag handle + Header 영역 (드래그 가능)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                with(density) {
                                    onHeightChange(dragAmount.toDp())
                                }
                            }
                        }.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { /* 클릭 효과 없음 */ },
                        ),
            ) {
                // Drag handle
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimen.IconButtonSizeLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(Dimen.IconButtonSizeLarge)
                                .height(Dimen.GridItemSpacing)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(Dimen.Radius2),
                                ),
                    )
                }

                // Header
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimen.ScreenHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (recommendLoadState is AlbumViewModel.RecommendLoadingState.Success &&
                        recommendLoadState.photos.isNotEmpty() &&
                        selectedRecommendPhotos.isNotEmpty()
                    ) {
                        // 사진 선택 시: Add와 Cancel 버튼
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                            verticalAlignment = Alignment.CenterVertically,
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
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                    ),
                                shape = RoundedCornerShape(Dimen.Radius20),
                                contentPadding =
                                    PaddingValues(
                                        horizontal = Dimen.ButtonPaddingLargeHorizontal,
                                        vertical = Dimen.ButtonPaddingVertical,
                                    ),
                            ) {
                                Text(
                                    text = "Add ${selectedRecommendPhotos.size} Photo${if (selectedRecommendPhotos.size > 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StandardIcon.Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI",
                                sizeRole = IconSizeRole.Navigation,
                                intent = IconIntent.Primary,
                            )
                            Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                            Text(
                                text = "AI Recommend",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    IconButton(onClick = onCollapse) {
                        StandardIcon.Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Collapse",
                            sizeRole = IconSizeRole.DefaultAction,
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = Dimen.ScreenHorizontalPadding)) {
                // Grid / states
                when (recommendLoadState) {
                    is AlbumViewModel.RecommendLoadingState.Loading,
                    is AlbumViewModel.RecommendLoadingState.Idle,
                    -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimen.CircularProgressSizeBig),
                                strokeWidth = Dimen.CircularProgressStrokeWidth,
                            )
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Success -> {
                        val recommendPhotos = recommendLoadState.photos
                        if (recommendPhotos.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No recommendations available",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                                horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                                modifier = Modifier.weight(1f),
                                userScrollEnabled = true,
                            ) {
                                items(
                                    count = recommendPhotos.size,
                                    key = { idx -> recommendPhotos[idx].photoId },
                                ) { idx ->
                                    ImageGridUriItem(
                                        photo = recommendPhotos[idx],
                                        navController = navController,
                                        isSelectionMode = true,
                                        isSelected = selectedRecommendPhotos.contains(recommendPhotos[idx]),
                                        onToggleSelection = { onToggleRecommendPhoto(recommendPhotos[idx]) },
                                        onLongPress = {},
                                    )
                                }
                            }
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Error -> {
                        Box(modifier = Modifier.fillMaxWidth().height(Dimen.ExpandedPanelHeight), contentAlignment = Alignment.Center) {
                            WarningBanner(
                                modifier = Modifier.fillMaxWidth(),
                                title = "Recommendation Failed",
                                message = recommendLoadState.message,
                                onActionClick = { /* TODO: 재시도 로직 필요 */ },
                                showActionButton = false,
                                showDismissButton = false,
                            )
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Idle -> {
                        Box(modifier = Modifier.fillMaxWidth().height(Dimen.ExpandedPanelHeight), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
