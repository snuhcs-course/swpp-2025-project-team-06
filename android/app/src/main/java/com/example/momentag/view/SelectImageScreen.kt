package com.example.momentag.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import com.example.momentag.viewmodel.SelectImageViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Helper data class for item info in the grid
// private data class PhotoGridItemInfo(
//    val photoId: String,
//    val photo: Photo,
// )

// Extension function for LazyGridState to find an item at a given position
private fun LazyGridState.findPhotoItemAtPosition(
    position: Offset,
    allPhotos: List<Photo>,
): Pair<String, Photo>? {
    for (itemInfo in layoutInfo.visibleItemsInfo) {
        val key = itemInfo.key
        if (key is String) {
            val itemBounds =
                Rect(
                    itemInfo.offset.x.toFloat(),
                    itemInfo.offset.y.toFloat(),
                    (itemInfo.offset.x + itemInfo.size.width).toFloat(),
                    (itemInfo.offset.y + itemInfo.size.height).toFloat(),
                )
            if (itemBounds.contains(position)) {
                val photo = allPhotos.find { it.photoId == key }
                if (photo != null) {
                    return key to photo
                }
            }
        }
    }
    return null
}

private suspend fun PointerInputScope.detectDragAfterLongPressIgnoreConsumed(
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress != null) {
            onDragStart(longPress.position)
            drag(longPress.id) { change ->
                onDrag(change)
            }
            onDragEnd()
        } else {
            onDragCancel()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SelectImageScreen(navController: NavController) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    LocalDensity.current

    // 2. ViewModel 인스턴스
    val selectImageViewModel: SelectImageViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val allPhotos by selectImageViewModel.allPhotos.collectAsState()
    val tagName by selectImageViewModel.tagName.collectAsState()
    val selectedPhotos: Map<String, Photo> by selectImageViewModel.selectedPhotos.collectAsState()
    val isLoading by selectImageViewModel.isLoading.collectAsState()
    val isLoadingMore by selectImageViewModel.isLoadingMore.collectAsState()
    val recommendState by selectImageViewModel.recommendState.collectAsState()
    val recommendedPhotos by selectImageViewModel.recommendedPhotos.collectAsState()
    val selectedRecommendPhotos by selectImageViewModel.selectedRecommendPhotos.collectAsState()
    val isSelectionMode by selectImageViewModel.isSelectionMode.collectAsState()
    val addPhotosState by selectImageViewModel.addPhotosState.collectAsState()
    // 4. 로컬 상태 변수
    var isRecommendationExpanded by remember { mutableStateOf(false) }
    var isSelectionModeDelay by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(BottomTab.MyTagsScreen) }
    val backgroundBrush = rememberAppBackgroundBrush()
    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val listState = selectImageViewModel.lazyGridState

    // 5. Derived 상태 및 계산된 값
    val minHeight = Dimen.ExpandedPanelMinHeight
    val maxHeight = (configuration.screenHeightDp * 0.6f).dp
    var panelHeight by remember { mutableStateOf((configuration.screenHeightDp / 3).dp) }

    // 6. ActivityResultLauncher
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    // 7. 콜백 함수 정의
    val onPhotoClick: (Photo) -> Unit = { photo ->
        selectImageViewModel.handlePhotoClick(
            photo = photo,
            isSelectionMode = isSelectionMode,
            onNavigate = { selectedPhoto ->
                navController.navigate(
                    Screen.Image.createRoute(
                        uri = selectedPhoto.contentUri,
                        imageId = selectedPhoto.photoId,
                    ),
                )
            },
        )
    }

    val onRecommendedPhotoClick: (Photo) -> Unit = { photo ->
        selectImageViewModel.toggleRecommendPhoto(photo)
    }

    // 8. LaunchedEffect
    LaunchedEffect(key1 = true) {
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(isSelectionMode) {
        delay(200L) // 0.2초
        isSelectionModeDelay = isSelectionMode
    }

    // Load recommendation when panel is expanded (like AlbumScreen)
    LaunchedEffect(isRecommendationExpanded) {
        if (isRecommendationExpanded) {
            selectImageViewModel.recommendPhoto()
            // 패널이 열리면 선택 모드로 전환
            if (!isSelectionMode) {
                selectImageViewModel.setSelectionMode(true)
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && selectImageViewModel.allPhotos.value.isEmpty()) {
            selectImageViewModel.getAllPhotos()
        }
    }

    // Handle add photos success
    LaunchedEffect(addPhotosState) {
        if (addPhotosState is SelectImageViewModel.AddPhotosState.Success) {
            // Set result to trigger refresh in AlbumScreen
            navController.previousBackStackEntry?.savedStateHandle?.set("photos_added", true)
            navController.popBackStack()
        }
    }

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

    // 9. BackHandler
    BackHandler(enabled = isSelectionMode) {
        isSelectionModeDelay = false
        selectImageViewModel.setSelectionMode(false)
    }

    // 10. UI 구성
    Scaffold(
        topBar = {
            CommonTopBar(
                title = stringResource(R.string.select_image_title),
                showBackButton = true,
                onBackClick = {
                    navController.popBackStack()
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                navigationIcon =
                    if (isSelectionMode) {
                        {
                            IconButton(
                                onClick = {
                                    isSelectionModeDelay = false
                                    selectImageViewModel.clearDraft()
                                    selectImageViewModel.setSelectionMode(false)
                                },
                            ) {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Close,
                                    sizeRole = IconSizeRole.Navigation,
                                    contentDescription = stringResource(R.string.cd_deselect_all),
                                )
                            }
                        }
                    } else {
                        {
                            IconButton(onClick = { navController.popBackStack() }) {
                                StandardIcon.Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    sizeRole = IconSizeRole.Navigation,
                                    contentDescription = stringResource(R.string.cd_navigate_back),
                                )
                            }
                        }
                    },
                actions = {},
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
                currentTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
                    selectImageViewModel.clearDraft()

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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(paddingValues),
        ) {
            // Main Content
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimen.FormScreenHorizontalPadding),
            ) {
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                // Pictures Header with count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.select_image_add_to_tag, tagName),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (isSelectionMode && selectedPhotos.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))

                        Text(
                            text = stringResource(R.string.select_image_selected_count, selectedPhotos.size),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                if (isLoading) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .size(Dimen.BottomNavBarHeight),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = Dimen.CircularProgressStrokeWidthBig,
                        )
                    }
                } else if (hasPermission) {
                    var lastProcessedPhotoId by remember { mutableStateOf<String?>(null) }
                    val lastProcessedPhotoIdRef = rememberUpdatedState(lastProcessedPhotoId)
                    val updatedSelectedPhotos = rememberUpdatedState(selectedPhotos)
                    val updatedIsSelectionMode = rememberUpdatedState(isSelectionMode)
                    val allPhotosState = rememberUpdatedState(allPhotos)

                    val gridGestureModifier =
                        Modifier.pointerInput(Unit) {
                            coroutineScope {
                                val pointerScope = this
                                val autoScrollViewport = 80.dp.toPx()
                                var autoScrollJob: Job? = null

                                detectDragAfterLongPressIgnoreConsumed(
                                    onDragStart = { offset ->
                                        autoScrollJob?.cancel()
                                        listState.findPhotoItemAtPosition(offset, allPhotosState.value)?.let { (photoId, photo) ->
                                            if (!updatedIsSelectionMode.value) {
                                                selectImageViewModel.setSelectionMode(true)
                                            }
                                            if (!updatedSelectedPhotos.value.containsKey(photoId)) {
                                                selectImageViewModel.addPhoto(photo)
                                            }
                                            lastProcessedPhotoId = photoId
                                        }
                                    },
                                    onDragEnd = {
                                        lastProcessedPhotoId = null
                                        autoScrollJob?.cancel()
                                    },
                                    onDragCancel = {
                                        lastProcessedPhotoId = null
                                        autoScrollJob?.cancel()
                                    },
                                    onDrag = { change ->
                                        // --- Item Selection Logic ---
                                        listState.findPhotoItemAtPosition(change.position, allPhotosState.value)?.let { (photoId, photo) ->
                                            if (photoId != lastProcessedPhotoIdRef.value) {
                                                if (!updatedSelectedPhotos.value.containsKey(photoId)) {
                                                    selectImageViewModel.addPhoto(photo)
                                                }
                                                lastProcessedPhotoId = photoId
                                            }
                                        }

                                        // --- Auto-Scroll Logic ---
                                        val viewportHeight =
                                            listState.layoutInfo.viewportSize.height
                                                .toFloat()
                                        val pointerY = change.position.y

                                        val scrollAmount =
                                            when {
                                                pointerY < autoScrollViewport -> -50f // Scroll up
                                                pointerY > viewportHeight - autoScrollViewport -> 50f // Scroll down
                                                else -> 0f
                                            }

                                        if (scrollAmount != 0f) {
                                            if (autoScrollJob?.isActive != true) {
                                                autoScrollJob =
                                                    pointerScope.launch {
                                                        while (true) {
                                                            listState.scrollBy(scrollAmount)
                                                            delay(50)
                                                        }
                                                    }
                                            }
                                        } else {
                                            autoScrollJob?.cancel()
                                        }
                                    },
                                )
                            }
                        }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = listState,
                        horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        modifier = Modifier.fillMaxSize().then(gridGestureModifier),
                        contentPadding =
                            PaddingValues(
                                bottom =
                                    if (isRecommendationExpanded) {
                                        panelHeight + Dimen.FloatingButtonAreaPadding
                                    } else {
                                        Dimen.FloatingButtonAreaPaddingLarge
                                    },
                            ),
                    ) {
                        items(
                            count = allPhotos.size,
                            key = { index -> allPhotos[index].photoId },
                        ) { index ->
                            val photo = allPhotos[index]
                            val isSelected = selectedPhotos.containsKey(photo.photoId)

                            PhotoSelectableItem(
                                photo = photo,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = { onPhotoClick(photo) },
                                onLongClick = {
                                    // This now primarily handles "long-press-and-release"
                                    selectImageViewModel.handleLongClick(photo)
                                },
                                modifier = Modifier.aspectRatio(1f),
                            )
                        }

                        // Loading indicator
                        if (isLoadingMore) {
                            item(span = {
                                GridItemSpan(3)
                            }) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(Dimen.ComponentPadding),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Dimen.IconButtonSizeMediumLarge),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = Dimen.CircularProgressStrokeWidthMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AI Recommendation Section - 축소/확장 가능
            AnimatedVisibility(
                visible = !isRecommendationExpanded,
                enter = Animation.DefaultFadeIn + expandVertically(animationSpec = Animation.mediumTween()),
                exit = Animation.DefaultFadeOut + shrinkVertically(animationSpec = Animation.mediumTween()),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Dimen.FloatingButtonAreaPadding),
            ) {
                // 축소 상태: 화면 하단 중앙 칩
                RecommendChip(
                    recommendState = recommendState,
                    onExpand = { isRecommendationExpanded = true },
                )
            }

            AnimatedVisibility(
                visible = isRecommendationExpanded,
                enter = Animation.EnterFromBottom,
                exit = Animation.ExitToBottom,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // 확장 상태: 하단 패널
                RecommendExpandedPanel(
                    recommendState = recommendState,
                    recommendedPhotos = recommendedPhotos,
                    selectedRecommendPhotos = selectedRecommendPhotos,
                    onToggleRecommendPhoto = onRecommendedPhotoClick,
                    onResetRecommendSelection = { selectImageViewModel.resetRecommendSelection() },
                    onAddPhotosToSelection = { photos ->
                        selectImageViewModel.addRecommendedPhotosToSelection(photos)
                    },
                    onRetry = { selectImageViewModel.recommendPhoto() },
                    panelHeight = panelHeight,
                    onHeightChange = { delta ->
                        panelHeight = (panelHeight - delta).coerceIn(minHeight, maxHeight)
                    },
                    onCollapse = {
                        selectImageViewModel.resetRecommendSelection()
                        isRecommendationExpanded = false
                    },
                )
            }

            // Done Button - AI Recommendation이 확장되지 않았을 때만 표시
            if (!isRecommendationExpanded) {
                Button(
                    onClick = {
                        if (selectImageViewModel.isAddingToExistingTag()) {
                            // Add photos to existing tag
                            selectImageViewModel.handleDoneButtonClick()
                        } else {
                            // Navigate to AddTagScreen for new tag creation
                            navController.navigate(Screen.AddTag.route) {
                                popUpTo(Screen.AddTag.route) { inclusive = true }
                            }
                        }
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
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = Dimen.FormScreenHorizontalPadding)
                            .padding(bottom = Dimen.ItemSpacingSmall)
                            .height(Dimen.ButtonHeightLarge)
                            .shadow(
                                elevation =
                                    if (selectedPhotos.isNotEmpty()) {
                                        Dimen.ButtonShadowElevation
                                    } else {
                                        Dimen.ButtonDisabledShadowElevation
                                    },
                                shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
                                clip = false,
                            ),
                    enabled = selectedPhotos.isNotEmpty() && addPhotosState !is SelectImageViewModel.AddPhotosState.Loading,
                ) {
                    AnimatedContent(
                        targetState = addPhotosState is SelectImageViewModel.AddPhotosState.Loading,
                        transitionSpec = {
                            (Animation.DefaultFadeIn).togetherWith(Animation.DefaultFadeOut)
                        },
                        label = "DoneButtonContent",
                    ) { isLoading ->
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimen.IconButtonSizeSmall),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.tag_add_to_tag),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoSelectableItem(
    photo: Photo,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        AsyncImage(
            model = photo.contentUri,
            contentDescription = stringResource(R.string.cd_photo, photo.photoId),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Selection overlay
        if (isSelectionMode) {
            // Dimmed overlay when not selected
            if (!isSelected) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f)),
                )
            }

            // Checkbox
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimen.GridItemSpacing)
                        .size(Dimen.IconButtonSizeSmall)
                        .clip(RoundedCornerShape(Dimen.ComponentCornerRadius))
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
                        sizeRole = IconSizeRole.InlineAction,
                        intent = IconIntent.OnPrimaryContainer,
                        contentDescription = stringResource(R.string.cd_photo_selected),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendChip(
    recommendState: SelectImageViewModel.RecommendState,
    onExpand: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .shadow(elevation = Dimen.BottomNavTonalElevation, shape = RoundedCornerShape(Dimen.TagCornerRadius))
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Dimen.TagCornerRadius),
                ).clip(RoundedCornerShape(Dimen.TagCornerRadius))
                .clickable(onClick = onExpand)
                .padding(horizontal = Dimen.ButtonPaddingHorizontal, vertical = Dimen.ButtonPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when (recommendState) {
            is SelectImageViewModel.RecommendState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimen.CircularProgressSizeXSmall),
                    strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.album_ai_recommending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is SelectImageViewModel.RecommendState.Success -> {
                StandardIcon.Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.cd_ai),
                    sizeRole = IconSizeRole.StatusIndicator,
                    intent = IconIntent.Primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.album_ai_recommend),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is SelectImageViewModel.RecommendState.Error -> {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.error_title),
                    sizeRole = IconSizeRole.StatusIndicator,
                    intent = IconIntent.Error,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.album_recommendation_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            else -> {
                StandardIcon.Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.cd_ai),
                    sizeRole = IconSizeRole.StatusIndicator,
                    intent = IconIntent.Primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.album_ai_recommend),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.width(Dimen.GridItemSpacing))
        StandardIcon.Icon(
            imageVector = Icons.Default.ExpandLess,
            sizeRole = IconSizeRole.StatusIndicator,
            contentDescription = stringResource(R.string.cd_expand),
        )
    }
}

@Composable
private fun RecommendExpandedPanel(
    recommendState: SelectImageViewModel.RecommendState,
    recommendedPhotos: List<Photo>,
    selectedRecommendPhotos: Map<String, Photo>,
    onToggleRecommendPhoto: (Photo) -> Unit,
    onResetRecommendSelection: () -> Unit,
    onAddPhotosToSelection: (List<Photo>) -> Unit,
    onRetry: () -> Unit,
    panelHeight: Dp,
    onHeightChange: (Dp) -> Unit,
    onCollapse: () -> Unit,
) {
    val density = LocalDensity.current
    val selectedRecommendPhotoIds = selectedRecommendPhotos.keys

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
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
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Drag Handle + Header 영역 (드래그 가능)
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
                // Drag Handle
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
                                    RoundedCornerShape(Dimen.SpacingXXSmall),
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
                    if (recommendState is SelectImageViewModel.RecommendState.Success &&
                        recommendedPhotos.isNotEmpty() &&
                        selectedRecommendPhotos.isNotEmpty()
                    ) {
                        // 사진 선택 시: Add와 Cancel 버튼
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onResetRecommendSelection) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(
                                onClick = {
                                    onAddPhotosToSelection(selectedRecommendPhotos.values.toList())
                                    onCollapse()
                                },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                    ),
                                shape = RoundedCornerShape(Dimen.Radius20),
                                contentPadding =
                                    PaddingValues(
                                        horizontal = Dimen.ButtonPaddingLargeHorizontal,
                                        vertical = Dimen.ButtonPaddingVertical,
                                    ),
                            ) {
                                Text(
                                    text = stringResource(R.string.add_tag_with_count, selectedRecommendPhotos.size),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    } else {
                        // 기본 상태: AI Recommend 텍스트
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (recommendState) {
                                is SelectImageViewModel.RecommendState.Loading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(Dimen.CircularProgressSizeSmall),
                                        strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                                    )
                                }
                                else -> {
                                    StandardIcon.Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        sizeRole = IconSizeRole.Navigation,
                                        intent = IconIntent.Primary,
                                        contentDescription = stringResource(R.string.cd_ai),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                            Text(
                                text = stringResource(R.string.photos_suggested_for_you),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Right side: Close button
                    IconButton(onClick = onCollapse) {
                        StandardIcon.Icon(
                            imageVector = Icons.Default.ExpandMore,
                            sizeRole = IconSizeRole.DefaultAction,
                            contentDescription = stringResource(R.string.cd_collapse),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = Dimen.ScreenHorizontalPadding)) {
                // Header

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                // Grid
                when (recommendState) {
                    is SelectImageViewModel.RecommendState.Loading -> {
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
                    is SelectImageViewModel.RecommendState.Success -> {
                        if (recommendedPhotos.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.select_image_no_suggestions),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                                verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                                modifier = Modifier.weight(1f),
                                userScrollEnabled = true,
                            ) {
                                items(
                                    count = recommendedPhotos.size,
                                    key = { index -> recommendedPhotos[index].photoId },
                                ) { index ->
                                    val photo = recommendedPhotos[index]
                                    val isSelected = selectedRecommendPhotoIds.contains(photo.photoId)
                                    PhotoSelectableItem(
                                        photo = photo,
                                        isSelected = isSelected,
                                        isSelectionMode = true,
                                        onClick = { onToggleRecommendPhoto(photo) },
                                        onLongClick = { onToggleRecommendPhoto(photo) },
                                        modifier = Modifier.aspectRatio(1f),
                                    )
                                }
                            }
                        }
                    }
                    is SelectImageViewModel.RecommendState.Error -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val (title, message) =
                                when (recommendState.error) {
                                    SelectImageViewModel.SelectImageError.NetworkError ->
                                        stringResource(R.string.select_image_connection_lost) to
                                            stringResource(R.string.error_message_network)
                                    SelectImageViewModel.SelectImageError.Unauthorized ->
                                        stringResource(R.string.select_image_couldnt_load) to
                                            stringResource(R.string.error_message_authentication_required)
                                    SelectImageViewModel.SelectImageError.UnknownError ->
                                        stringResource(R.string.select_image_couldnt_load) to
                                            stringResource(R.string.error_message_unknown)
                                }

                            WarningBanner(
                                title = title,
                                message = message,
                                onActionClick = onRetry,
                                showActionButton = true,
                                actionIcon = Icons.Default.Refresh,
                                showDismissButton = false,
                                modifier = Modifier.padding(Dimen.ComponentPadding),
                            )
                        }
                    }
                    is SelectImageViewModel.RecommendState.Idle -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
