package com.example.momentag

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.Photo
import com.example.momentag.model.RecommendState
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
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
    var isRecommendationExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val selectImageViewModel: SelectImageViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val allPhotos by selectImageViewModel.allPhotos.collectAsState()
    val tagName by selectImageViewModel.tagName.collectAsState()
    val selectedPhotos by selectImageViewModel.selectedPhotos.collectAsState()
    val isLoading by selectImageViewModel.isLoading.collectAsState()
    val isLoadingMore by selectImageViewModel.isLoadingMore.collectAsState()
    val recommendState by selectImageViewModel.recommendState.collectAsState()
    val recommendedPhotos by selectImageViewModel.recommendedPhotos.collectAsState()
    val isSelectionMode by selectImageViewModel.isSelectionMode.collectAsState()

    var isSelectionModeDelay by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(BottomTab.HomeScreen) }

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

    BackHandler(enabled = isSelectionMode) {
        isSelectionModeDelay = false
        selectImageViewModel.setSelectionMode(false)
    }

    // Call recommendPhoto when screen is entered
    LaunchedEffect(Unit) {
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
            }
        )
    }

    val onRecommendedPhotoClick: (Photo) -> Unit = { photo ->
        selectImageViewModel.addPhotoFromRecommendation(photo)
    }

    // Re-recommend when AI recommendation is collapsed after adding photos
    LaunchedEffect(isRecommendationExpanded) {
        if (!isRecommendationExpanded && selectedPhotos.isNotEmpty()) {
            // 축소될 때 다시 추천
            selectImageViewModel.recommendPhoto()
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
                                        isSelectionModeDelay = false
                                        selectImageViewModel.setSelectionMode(false)
                                        showMenu = false
                                    },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Select") },
                                    onClick = {
                                        isSelectionModeDelay = true
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Main Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Tab Navigation
                TabNavigation(
                    selectedTab = 1,
                    onTabSelected = { tab ->
                        if (tab == 0) {
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Add to #$tagName",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pictures Header with count
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pictures",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineLarge,
                    )

                    if (isSelectionMode && selectedPhotos.isNotEmpty()) {
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
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(56.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp
                        )
                    }
                } else if (hasPermission) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 200.dp)
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
                                modifier = Modifier.aspectRatio(1f)
                            )
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
                                        modifier = Modifier.size(36.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AI Recommendation Section - Floating on top
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 8.dp)
            ) {
                AIRecommendationSection(
                    isExpanded = isRecommendationExpanded,
                    onToggleExpanded = { isRecommendationExpanded = !isRecommendationExpanded },
                    recommendState = recommendState,
                    recommendedPhotos = recommendedPhotos,
                    onPhotoClick = onRecommendedPhotoClick,
                    onRetry = { selectImageViewModel.recommendPhoto() }
                )

                // Done Button - 숨김 처리 (AI Recommendation이 확장되지 않았을 때만 표시)
                if (!isRecommendationExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            navController.navigate(Screen.AddTag.route) {
                                popUpTo(Screen.AddTag.route) { inclusive = true }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(
                                elevation = if (selectedPhotos.isNotEmpty()) 6.dp else 2.dp,
                                shape = RoundedCornerShape(24.dp),
                                clip = false
                            ),
                        enabled = selectedPhotos.isNotEmpty(),
                    ) {
                        Text(
                            text = "Add Pic to Tag",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabItem(
            text = "Tag Details",
            isSelected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            modifier = Modifier.weight(1f)
        )
        TabItem(
            text = "Select Pictures",
            isSelected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TabItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = if (isSelected) 4.dp else 2.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }

            // Checkbox
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AIRecommendationSection(
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    recommendState: RecommendState,
    recommendedPhotos: List<Photo>,
    onPhotoClick: (Photo) -> Unit,
    onRetry: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp / 3).dp
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false
            )
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // AI 아이콘 또는 로딩/체크 표시
                when (recommendState) {
                    is RecommendState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                    }
                    is RecommendState.Success -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Text(
                    text = when (recommendState) {
                        is RecommendState.Loading -> "AI Recommending..."
                        is RecommendState.Success -> "AI Recommended"
                        is RecommendState.Error -> "AI Recommendation Failed"
                        is RecommendState.NetworkError -> "Network Error"
                        else -> "AI Recommendation"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp
                             else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        // Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) + 
                    expandVertically(animationSpec = androidx.compose.animation.core.tween(300)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) + 
                   shrinkVertically(animationSpec = androidx.compose.animation.core.tween(300))
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                when (recommendState) {
                    is RecommendState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 4.dp
                            )
                        }
                    }
                    is RecommendState.Success -> {
                        if (recommendedPhotos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No recommendations available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = maxHeight),
                                userScrollEnabled = true
                            ) {
                                items(
                                    count = recommendedPhotos.size,
                                    key = { index -> recommendedPhotos[index].photoId }
                                ) { index ->
                                    val photo = recommendedPhotos[index]
                                    PhotoSelectableItem(
                                        photo = photo,
                                        isSelected = false,
                                        isSelectionMode = false,
                                        onClick = { onPhotoClick(photo) },
                                        onLongClick = {},
                                        modifier = Modifier.aspectRatio(1f)
                                    )
                                }
                            }
                        }
                    }
                    is RecommendState.Error, is RecommendState.NetworkError -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when (recommendState) {
                                    is RecommendState.Error ->
                                        (recommendState as RecommendState.Error).message
                                    is RecommendState.NetworkError ->
                                        (recommendState as RecommendState.NetworkError).message
                                    else -> "Unknown error"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}