package com.example.momentag.view

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.model.RecommendState
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.horizontalArrangement
import com.example.momentag.ui.theme.verticalArrangement
import com.example.momentag.viewmodel.SelectImageViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SelectImageScreen(navController: NavController) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    LocalDensity.current

    // 2. ViewModel 인스턴스
    val selectImageViewModel: SelectImageViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val allPhotos by selectImageViewModel.allPhotos.collectAsState()
    val tagName by selectImageViewModel.tagName.collectAsState()
    val selectedPhotos by selectImageViewModel.selectedPhotos.collectAsState()
    val isLoading by selectImageViewModel.isLoading.collectAsState()
    val isLoadingMore by selectImageViewModel.isLoadingMore.collectAsState()
    val recommendState by selectImageViewModel.recommendState.collectAsState()
    val recommendedPhotos by selectImageViewModel.recommendedPhotos.collectAsState()
    val isSelectionMode by selectImageViewModel.isSelectionMode.collectAsState()
    val addPhotosState by selectImageViewModel.addPhotosState.collectAsState()

    // 4. 로컬 상태 변수
    var isRecommendationExpanded by remember { mutableStateOf(false) }
    var isSelectionModeDelay by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf(BottomTab.MyTagsScreen) }
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

    // 5. Derived 상태 및 계산된 값
    val minHeight = 200.dp
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

    LaunchedEffect(key1 = true) {
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(isSelectionMode) {
        delay(200L) // 0.2초
        isSelectionModeDelay = isSelectionMode
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionModeDelay = false
        selectImageViewModel.setSelectionMode(false)
    }

    // Delay AI recommendation to improve initial screen load performance
    LaunchedEffect(Unit) {
        // Wait for initial photos to load first
        delay(500L) // 0.5초 지연으로 화면 로딩 우선
        selectImageViewModel.recommendPhoto()
    }

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
        selectImageViewModel.addPhotoFromRecommendation(photo)
    }

    // Re-recommend when AI recommendation is collapsed after adding photos
    // Only re-recommend if recommendation was successful before
    LaunchedEffect(isRecommendationExpanded) {
        if (!isRecommendationExpanded && selectedPhotos.isNotEmpty()) {
            selectImageViewModel.recommendPhoto()
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
                title = "Select Photos",
                showBackButton = true,
                onBackClick = {
                    navController.popBackStack()
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface),
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
                    .padding(paddingValues),
        ) {
            // Main Content
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Pictures Header with count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Add to #$tagName",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (isSelectionMode && selectedPhotos.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${selectedPhotos.size} selected",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                                    .size(56.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp,
                        )
                    }
                } else if (hasPermission) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(verticalArrangement),
                        horizontalArrangement = Arrangement.spacedBy(horizontalArrangement),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                bottom = if (isRecommendationExpanded) panelHeight + 80.dp else 200.dp,
                            ),
                    ) {
                        items(
                            count = allPhotos.size,
                            key = { index -> allPhotos[index].photoId },
                        ) { index ->
                            val photo = allPhotos[index]
                            val isSelected = selectedPhotos.any { it.photoId == photo.photoId }

                            PhotoSelectableItem(
                                photo = photo,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = { onPhotoClick(photo) },
                                onLongClick = { selectImageViewModel.handleLongClick(photo) },
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
                                            .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp,
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
                enter =
                    fadeIn(
                        tween(300),
                    ) +
                        expandVertically(
                            tween(300),
                        ),
                exit =
                    fadeOut(
                        tween(300),
                    ) +
                        shrinkVertically(
                            tween(300),
                        ),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp),
            ) {
                // 축소 상태: 화면 하단 중앙 칩
                RecommendChip(
                    recommendState = recommendState,
                    onExpand = { isRecommendationExpanded = true },
                )
            }

            if (isRecommendationExpanded) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    // 확장 상태: 하단 패널
                    RecommendExpandedPanel(
                        recommendState = recommendState,
                        recommendedPhotos = recommendedPhotos,
                        onPhotoClick = onRecommendedPhotoClick,
                        onRetry = { selectImageViewModel.recommendPhoto() },
                        panelHeight = panelHeight,
                        onHeightChange = { delta ->
                            panelHeight = (panelHeight - delta).coerceIn(minHeight, maxHeight)
                        },
                        onCollapse = { isRecommendationExpanded = false },
                    )
                }
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
                    shape = RoundedCornerShape(24.dp),
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
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 8.dp)
                            .height(52.dp)
                            .shadow(
                                elevation = if (selectedPhotos.isNotEmpty()) 6.dp else 2.dp,
                                shape = RoundedCornerShape(24.dp),
                                clip = false,
                            ),
                    enabled = selectedPhotos.isNotEmpty() && addPhotosState !is SelectImageViewModel.AddPhotosState.Loading,
                ) {
                    if (addPhotosState is SelectImageViewModel.AddPhotosState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Add to Tag",
                            style = MaterialTheme.typography.labelLarge,
                        )
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
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        AsyncImage(
            model = photo.contentUri,
            contentDescription = "Photo ${photo.photoId}",
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
    }
}

@Composable
private fun RecommendChip(
    recommendState: RecommendState,
    onExpand: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(20.dp))
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                ).clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onExpand)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when (recommendState) {
            is RecommendState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Finding suggestions...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is RecommendState.Success -> {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Suggested for You",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is RecommendState.Error, is RecommendState.NetworkError -> {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Could not load suggestions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Getting ready...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = "Expand",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RecommendExpandedPanel(
    recommendState: RecommendState,
    recommendedPhotos: List<Photo>,
    onPhotoClick: (Photo) -> Unit,
    onRetry: () -> Unit,
    panelHeight: Dp,
    onHeightChange: (Dp) -> Unit,
    onCollapse: () -> Unit,
) {
    val density = LocalDensity.current

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(panelHeight)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ).clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
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
                            .height(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(2.dp),
                                ),
                    )
                }

                // Header
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // 왼쪽: AI Recommend 텍스트
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when (recommendState) {
                            is RecommendState.Loading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            is RecommendState.Success -> {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Suggested for You",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // 오른쪽: 닫기 버튼
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Header

                Spacer(modifier = Modifier.height(16.dp))

                // Grid
                when (recommendState) {
                    is RecommendState.Loading -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp,
                            )
                        }
                    }
                    is RecommendState.Success -> {
                        if (recommendedPhotos.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No suggestions at this time",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                                userScrollEnabled = true,
                            ) {
                                items(
                                    count = recommendedPhotos.size,
                                    key = { index -> recommendedPhotos[index].photoId },
                                ) { index ->
                                    val photo = recommendedPhotos[index]
                                    PhotoSelectableItem(
                                        photo = photo,
                                        isSelected = false,
                                        isSelectionMode = false,
                                        onClick = { onPhotoClick(photo) },
                                        onLongClick = {},
                                        modifier = Modifier.aspectRatio(1f),
                                    )
                                }
                            }
                        }
                    }
                    is RecommendState.Error, is RecommendState.NetworkError -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val (title, message) =
                                if (recommendState is RecommendState.Error) {
                                    "Couldn't load suggestions" to recommendState.message
                                } else {
                                    "Connection lost" to (recommendState as RecommendState.NetworkError).message
                                }

                            WarningBanner(
                                title = title,
                                message = message,
                                onActionClick = onRetry,
                                showActionButton = true,
                                actionIcon = Icons.Default.Refresh,
                                showDismissButton = false,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    is RecommendState.Idle -> {
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
