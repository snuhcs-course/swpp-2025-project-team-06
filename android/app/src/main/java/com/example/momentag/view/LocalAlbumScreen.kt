package com.example.momentag.view

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.VerticalScrollbar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAlbumScreen(
    navController: NavController,
    albumId: Long,
    albumName: String,
    onNavigateBack: () -> Unit,
) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current

    // 2. ViewModel 인스턴스
    val localViewModel: LocalViewModel = hiltViewModel()
    val photoViewModel: PhotoViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val photos by localViewModel.imagesInAlbum.collectAsState()
    val selectedPhotos: Map<Uri, Photo> by localViewModel.selectedPhotosInAlbum.collectAsState()
    val uploadState by photoViewModel.uiState.collectAsState()
    val scrollToIndex by localViewModel.scrollToIndex.collectAsState()
    val isLoadingMorePhotos by localViewModel.isLoadingMorePhotos.collectAsState()

    // 4. 로컬 상태 변수
    var hasPermission by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 6. rememberCoroutineScope
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val backgroundBrush = rememberAppBackgroundBrush()

    // 8. ActivityResultLauncher
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
    LaunchedEffect(uploadState.userMessage) {
        uploadState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            photoViewModel.infoMessageShown()
        }
    }

    LaunchedEffect(uploadState.error) {
        errorMessage =
            when (uploadState.error) {
                PhotoViewModel.PhotoError.NoPhotosSelected -> context.getString(R.string.error_message_no_photo_ids)
                null -> null
            }
        isErrorBannerVisible = errorMessage != null
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && photos.isEmpty()) {
            localViewModel.loadAlbumPhotos(albumId, albumName)
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

    // Scroll restoration: restore scroll position when returning from ImageDetailScreen
    LaunchedEffect(scrollToIndex) {
        scrollToIndex?.let { index ->
            scope.launch {
                gridState.animateScrollToItem(index)
                localViewModel.clearScrollToIndex()
            }
        }
    }

    // Restore scroll position when screen becomes visible (returning from ImageDetailScreen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // Fetch last viewed index from ImageBrowserRepository and restore scroll
                    localViewModel.restoreScrollPosition()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Pagination: Load more photos as user scrolls near end
    @OptIn(FlowPreview::class)
    LaunchedEffect(gridState, isLoadingMorePhotos) {
        if (!isLoadingMorePhotos) {
            snapshotFlow {
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
            }.distinctUntilChanged()
                .debounce(150)
                .collect { lastVisibleIndex ->
                    val totalItemCount = gridState.layoutInfo.totalItemsCount
                    if (lastVisibleIndex != null && totalItemCount > 0) {
                        val remainingItems = totalItemCount - (lastVisibleIndex + 1)
                        // Trigger when 15 items (5 rows) from end
                        if (remainingItems < 15) {
                            localViewModel.loadMorePhotos()
                        }
                    }
                }
        }
    }

    // 12. BackHandler
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        localViewModel.clearPhotoSelection()
    }

    // 13. UI (Scaffold)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = {
                    (Animation.DefaultFadeIn)
                        .togetherWith(Animation.DefaultFadeOut)
                        .using(SizeTransform(clip = false))
                },
                label = "TopAppBarAnimation",
            ) { selectionMode ->
                if (selectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.photos_selected_count, selectedPhotos.size)) },
                        navigationIcon = {
                            IconButton(onClick = {
                                isSelectionMode = false
                                localViewModel.clearPhotoSelection()
                            }) {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_cancel_selection),
                                    sizeRole = IconSizeRole.DefaultAction,
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                    )
                } else {
                    BackTopBar(
                        title = stringResource(R.string.app_name),
                        onBackClick = onNavigateBack,
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isSelectionMode && selectedPhotos.isNotEmpty(),
                enter = Animation.EnterFromBottom,
                exit = Animation.ExitToBottom,
            ) {
                ExtendedFloatingActionButton(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    text = {
                        AnimatedContent(
                            targetState = uploadState.isLoading,
                            transitionSpec = {
                                (Animation.DefaultFadeIn).togetherWith(Animation.DefaultFadeOut)
                            },
                            label = "FabText",
                        ) { isLoading ->
                            if (isLoading) {
                                Text(stringResource(R.string.banner_upload_check_notification))
                            } else {
                                Text(stringResource(R.string.photos_upload_selected_photos, selectedPhotos.size))
                            }
                        }
                    },
                    icon = {
                        AnimatedContent(
                            targetState = uploadState.isLoading,
                            transitionSpec = {
                                (Animation.DefaultFadeIn).togetherWith(Animation.DefaultFadeOut)
                            },
                            label = "FabIcon",
                        ) { isLoading ->
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimen.IconButtonSizeSmall),
                                    strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = stringResource(R.string.cd_upload),
                                    sizeRole = IconSizeRole.DefaultAction,
                                    tintOverride = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    },
                    onClick = {
                        if (uploadState.isLoading) return@ExtendedFloatingActionButton

                        photoViewModel.uploadSelectedPhotos(selectedPhotos.values.toSet(), context)
                    },
                )
            }
        },
    ) { paddingValues ->
        val bodyModifier =
            Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(paddingValues)

        val albumContent: @Composable () -> Unit = {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimen.ScreenHorizontalPadding),
                ) {
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))
                    Text(
                        text = albumName,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = Dimen.ItemSpacingSmall, bottom = Dimen.ItemSpacingXSmall),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                    Text(
                        text = stringResource(R.string.help_upload_pictures),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                    AnimatedVisibility(
                        visible = isErrorBannerVisible && errorMessage != null,
                        enter = Animation.EnterFromBottom,
                        exit = Animation.ExitToBottom,
                    ) {
                        WarningBanner(
                            modifier = Modifier.fillMaxWidth().padding(bottom = Dimen.ItemSpacingSmall),
                            title = stringResource(R.string.notification_upload_failed),
                            message = errorMessage ?: stringResource(R.string.error_message_generic),
                            onActionClick = { isErrorBannerVisible = false },
                            showActionButton = false,
                            showDismissButton = true,
                            onDismiss = {
                                isErrorBannerVisible = false
                                photoViewModel.errorMessageShown()
                            },
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        val updatedSelectedPhotos = rememberUpdatedState(selectedPhotos)
                        val updatedIsSelectionMode = rememberUpdatedState(isSelectionMode)
                        val allPhotosState = rememberUpdatedState(photos)

                        val gridGestureModifier =
                            Modifier.pointerInput(Unit) {
                                coroutineScope {
                                    val pointerScope = this
                                    val autoScrollViewport = 80.dp.toPx()
                                    var autoScrollJob: Job? = null
                                    var dragAnchorIndex: Int? = null
                                    var isDeselectDrag by mutableStateOf(false)
                                    val initialSelection = mutableSetOf<Uri>()

                                    fun findNearestItemByRow(position: Offset): Int? {
                                        var best: Pair<Int, Float>? = null
                                        gridState.layoutInfo.visibleItemsInfo.forEach { itemInfo ->
                                            val key = itemInfo.key as? Uri ?: return@forEach
                                            val photoIndex = allPhotosState.value.indexOfFirst { it.contentUri == key }
                                            if (photoIndex >= 0) {
                                                val top = itemInfo.offset.y.toFloat()
                                                val bottom = (itemInfo.offset.y + itemInfo.size.height).toFloat()
                                                if (position.y in top..bottom) {
                                                    val left = itemInfo.offset.x.toFloat()
                                                    val right = (itemInfo.offset.x + itemInfo.size.width).toFloat()
                                                    val dist =
                                                        when {
                                                            position.x < left -> left - position.x
                                                            position.x > right -> position.x - right
                                                            else -> 0f
                                                        }
                                                    if (best == null || dist < best.second) {
                                                        best = photoIndex to dist
                                                    }
                                                }
                                            }
                                        }
                                        return best?.first
                                    }

                                    detectDragAfterLongPressIgnoreConsumed(
                                        onDragStart = { offset ->
                                            autoScrollJob?.cancel()
                                            if (!updatedIsSelectionMode.value) {
                                                isSelectionMode = true
                                            }
                                            gridState.findPhotoItemAtPosition(offset, allPhotosState.value)?.let { (contentUri, photo) ->
                                                initialSelection.clear()
                                                initialSelection.addAll(updatedSelectedPhotos.value.keys)
                                                isDeselectDrag = initialSelection.contains(contentUri)
                                                dragAnchorIndex =
                                                    allPhotosState.value.indexOfFirst { it.contentUri == contentUri }.takeIf { it >= 0 }
                                                localViewModel.togglePhotoSelection(photo)
                                            }
                                        },
                                        onDragEnd = {
                                            dragAnchorIndex = null
                                            autoScrollJob?.cancel()
                                        },
                                        onDragCancel = {
                                            dragAnchorIndex = null
                                            autoScrollJob?.cancel()
                                        },
                                        onDrag = { change ->
                                            change.consume()
                                            val currentItem = gridState.findPhotoItemAtPosition(change.position, allPhotosState.value)
                                            val currentIndex =
                                                currentItem
                                                    ?.first
                                                    ?.let { id ->
                                                        allPhotosState.value.indexOfFirst { it.contentUri == id }
                                                    }?.takeIf { it >= 0 }
                                                    ?: findNearestItemByRow(change.position)

                                            if (currentIndex != null) {
                                                if (dragAnchorIndex == null) dragAnchorIndex = currentIndex
                                                val startIndex = dragAnchorIndex ?: currentIndex
                                                val range =
                                                    if (currentIndex >= startIndex) {
                                                        startIndex..currentIndex
                                                    } else {
                                                        currentIndex..startIndex
                                                    }

                                                val photoUrisInRange =
                                                    range
                                                        .mapNotNull { idx ->
                                                            allPhotosState.value.getOrNull(idx)?.contentUri
                                                        }.toSet()

                                                val currentSelection = updatedSelectedPhotos.value.keys
                                                val targetSelection =
                                                    if (isDeselectDrag) {
                                                        initialSelection - photoUrisInRange
                                                    } else {
                                                        initialSelection + photoUrisInRange
                                                    }

                                                val diff = currentSelection.symmetricDifference(targetSelection)
                                                diff.forEach { uri ->
                                                    allPhotosState.value.find { it.contentUri == uri }?.let { photoToToggle ->
                                                        localViewModel.togglePhotoSelection(photoToToggle)
                                                    }
                                                }
                                            }

                                            // --- Auto-Scroll Logic ---
                                            val viewportHeight =
                                                gridState.layoutInfo.viewportSize.height
                                                    .toFloat()
                                            val pointerY = change.position.y

                                            val scrollAmount =
                                                when {
                                                    pointerY < autoScrollViewport -> -50f
                                                    pointerY > viewportHeight - autoScrollViewport -> 50f
                                                    else -> 0f
                                                }

                                            if (scrollAmount != 0f) {
                                                if (autoScrollJob?.isActive != true) {
                                                    autoScrollJob =
                                                        pointerScope.launch {
                                                            while (true) {
                                                                gridState.scrollBy(scrollAmount)
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
                            state = gridState,
                            modifier = Modifier.fillMaxSize().then(gridGestureModifier),
                            verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                            horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        ) {
                            items(
                                count = photos.size,
                                key = { index -> photos[index].contentUri },
                            ) { index ->
                                val photo = photos[index]
                                val isSelected = selectedPhotos.containsKey(photo.contentUri)

                                Box(modifier = Modifier.aspectRatio(1f)) {
                                    AsyncImage(
                                        model = photo.contentUri,
                                        contentDescription = stringResource(R.string.cd_photo_item, photo.photoId),
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
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
                                                    onLongClick = null, // Drag selector handles long-press entry
                                                ),
                                        contentScale = ContentScale.Crop,
                                    )

                                    if (isSelectionMode) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
                                                    .background(
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.3f,
                                                            )
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
                                                    .size(Dimen.IconButtonSizeSmall)
                                                    .background(
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.primaryContainer
                                                        } else {
                                                            MaterialTheme.colorScheme.surface
                                                                .copy(
                                                                    alpha = 0.8f,
                                                                )
                                                        },
                                                        RoundedCornerShape(Dimen.ComponentCornerRadius),
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
                                }
                            }

                            // Add loading indicator
                            if (isLoadingMorePhotos) {
                                item(span = { GridItemSpan(3) }) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(Dimen.ItemSpacingLarge),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(Dimen.IconButtonSizeSmall),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Scrollbar positioned outside Column to span padding boundary
                if (hasPermission && photos.isNotEmpty()) {
                    VerticalScrollbar(
                        state = gridState,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(
                                    top = Dimen.ItemSpacingLarge + Dimen.TitleRowHeight + Dimen.SectionSpacing, // Spacer + Title + Divider
                                    end = Dimen.ScreenHorizontalPadding / 2,
                                ),
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (isSelectionMode) return@PullToRefreshBox // Keep gesture coroutine alive when toggling selection

                scope.launch {
                    isRefreshing = true
                    try {
                        if (hasPermission) {
                            localViewModel.loadAlbumPhotos(albumId, albumName)
                        }
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = bodyModifier,
        ) {
            albumContent()
        }
    }
}

// Extension function for LazyGridState to find an item at a given position
private fun LazyGridState.findPhotoItemAtPosition(
    position: Offset,
    allPhotos: List<Photo>,
): Pair<Uri, Photo>? {
    for (itemInfo in layoutInfo.visibleItemsInfo) {
        val key = itemInfo.key
        if (key is Uri) {
            val itemBounds =
                Rect(
                    itemInfo.offset.x.toFloat(),
                    itemInfo.offset.y.toFloat(),
                    (itemInfo.offset.x + itemInfo.size.width).toFloat(),
                    (itemInfo.offset.y + itemInfo.size.height).toFloat(),
                )
            if (itemBounds.contains(position)) {
                val photo = allPhotos.find { it.contentUri == key }
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

private fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> {
    return (this - other) + (other - this)
}

