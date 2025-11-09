package com.example.momentag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.Photo
import com.example.momentag.model.SearchResultItem
import com.example.momentag.model.SearchUiState
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.ErrorOverlay
import com.example.momentag.ui.components.SearchBarControlledCustom
import com.example.momentag.ui.search.components.SearchEmptyStateCustom
import com.example.momentag.ui.search.components.SearchErrorStateFallbackCustom
import com.example.momentag.ui.search.components.SearchIdleCustom
import com.example.momentag.ui.search.components.SearchLoadingStateCustom
import com.example.momentag.viewmodel.SearchViewModel
import com.example.momentag.viewmodel.ViewModelFactory

/**
 *  * ========================================
 *  * SearchResultScreen - 검색 결과 화면
 *  * ========================================
 * Semantic Search 결과를 표시하는 검색 결과 메인 화면
 */
@Composable
fun SearchResultScreen(
    initialQuery: String,
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val searchViewModel: SearchViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    var searchText by remember { mutableStateOf(initialQuery) }
    val semanticSearchState by searchViewModel.searchState.collectAsState()
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedPhotos by searchViewModel.selectedPhotos.collectAsState()
    var currentTab by remember { mutableStateOf(BottomTab.SearchResultScreen) }

    // 초기 검색어가 있으면 자동으로 Semantic Search 실행
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            searchViewModel.search(initialQuery)
        }
    }

    // SemanticSearchState를 SearchUiState로 변환
    val uiState =
        remember(semanticSearchState) {
            when (semanticSearchState) {
                is SemanticSearchState.Idle -> SearchUiState.Idle
                is SemanticSearchState.Loading -> SearchUiState.Loading
                is SemanticSearchState.Success -> {
                    val photos = (semanticSearchState as SemanticSearchState.Success).photos
                    val searchResults =
                        photos.map { photo ->
                            SearchResultItem(
                                query = (semanticSearchState as SemanticSearchState.Success).query,
                                photo = photo,
                            )
                        }
                    SearchUiState.Success(searchResults, (semanticSearchState as SemanticSearchState.Success).query)
                }
                is SemanticSearchState.Empty -> {
                    SearchUiState.Empty((semanticSearchState as SemanticSearchState.Empty).query)
                }
                is SemanticSearchState.NetworkError -> {
                    SearchUiState.Error((semanticSearchState as SemanticSearchState.NetworkError).message)
                }
                is SemanticSearchState.Error -> {
                    SearchUiState.Error((semanticSearchState as SemanticSearchState.Error).message)
                }
            }
        }

    SearchResultScreenUi(
        searchText = searchText,
        onSearchTextChange = { searchText = it },
        onSearchSubmit = {
            if (searchText.isNotEmpty()) {
                searchViewModel.search(searchText)
            }
        },
        uiState = uiState,
        isSelectionMode = isSelectionMode,
        selectedPhotos = selectedPhotos,
        onBackClick = onNavigateBack,
        onToggleSelectionMode = {
            isSelectionMode = !isSelectionMode
            if (!isSelectionMode) {
                searchViewModel.resetSelection()
            }
        },
        onToggleImageSelection = { photo ->
            searchViewModel.togglePhoto(photo)
        },
        onImageLongPress = {
            if (!isSelectionMode) {
                isSelectionMode = true
            }
        },
        onCreateTagClick = {
            // Get the current search results
            val currentState = uiState as? SearchUiState.Success
            if (currentState != null) {
                // photo selection already stored in draftRepository
                isSelectionMode = false
                navController.navigate(Screen.AddTag.route)
            }
        },
        onRetry = {
            if (searchText.isNotEmpty()) {
                searchViewModel.search(searchText)
            }
        },
        navController = navController,
        currentTab = currentTab,
        onTabSelected = { tab ->
            currentTab = tab
            when (tab) {
                BottomTab.HomeScreen -> {
                    navController.navigate(Screen.Home.route)
                }
                BottomTab.SearchResultScreen -> {
                    // 이미 Search 화면
                }
                BottomTab.AddTagScreen -> {
                    navController.navigate(Screen.AddTag.route)
                }
                BottomTab.StoryScreen -> {
                    navController.navigate(Screen.Story.route)
                }
            }
        },
    )
}

/**
 * UI 전용 검색 결과 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreenUi(
    modifier: Modifier = Modifier,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    uiState: SearchUiState,
    isSelectionMode: Boolean,
    selectedPhotos: List<Photo>,
    onBackClick: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleImageSelection: (Photo) -> Unit,
    onImageLongPress: () -> Unit,
    onCreateTagClick: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
    currentTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                BackTopBar(
                    title = "Search Results",
                    onBackClick = onBackClick,
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
                    onTabSelected = onTabSelected,
                )
            },
        ) { paddingValues ->
            SearchResultContent(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                onSearchSubmit = onSearchSubmit,
                uiState = uiState,
                isSelectionMode = isSelectionMode,
                selectedPhotos = selectedPhotos,
                onToggleSelectionMode = onToggleSelectionMode,
                onToggleImageSelection = onToggleImageSelection,
                onImageLongPress = onImageLongPress,
                onCreateTagClick = onCreateTagClick,
                onRetry = onRetry,
                navController = navController,
            )
        }
        // TODO : 혹시 네트워크 에러일 때만 오버레이 띄울거면 NetworkError 상태로 바꾸기
        if (uiState is SearchUiState.Error) {
            Box(modifier = Modifier.matchParentSize()) {
                ErrorOverlay(
                    modifier = Modifier.fillMaxSize(),
                    errorMessage = uiState.message,
                    onRetry = onRetry,
                    onDismiss = { navController.popBackStack() },
                )
            }
        }
    }
}

/**
 * 검색 결과 컨텐츠 (순수 UI)
 */
@Composable
private fun SearchResultContent(
    modifier: Modifier = Modifier,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    uiState: SearchUiState,
    isSelectionMode: Boolean,
    selectedPhotos: List<Photo>,
    onToggleSelectionMode: () -> Unit,
    onToggleImageSelection: (Photo) -> Unit,
    onImageLongPress: () -> Unit,
    onCreateTagClick: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
) {
    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Instruction + Selection mode toggle
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Search for Photo",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "카메라 아이콘",
                    )
                }

                if (uiState is SearchUiState.Success && uiState.results.isNotEmpty()) {
                    Button(
                        onClick = onToggleSelectionMode,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    if (isSelectionMode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                contentColor =
                                    if (isSelectionMode) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                            ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = if (isSelectionMode) "선택 모드 해제" else "선택 모드",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSelectionMode) "선택 모드" else "선택",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Search Input
            SearchBarControlledCustom(
                value = searchText,
                onValueChange = onSearchTextChange,
                onSearch = onSearchSubmit,
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Results
            SearchResultsFromState(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                uiState = uiState,
                isSelectionMode = isSelectionMode,
                selectedPhotos = selectedPhotos,
                onToggleImageSelection = onToggleImageSelection,
                onImageLongPress = onImageLongPress,
                onRetry = onRetry,
                navController = navController,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom overlay: Photo count + Create Tag button
        if (uiState is SearchUiState.Success) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
            ) {
                CreateTagButton(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(start = 16.dp),
                    text = "Create Tag",
                    enabled = !isSelectionMode || selectedPhotos.isNotEmpty(),
                    onClick = {
                        if (!isSelectionMode) {
                            onToggleSelectionMode()
                        } else if (selectedPhotos.isNotEmpty()) {
                            onCreateTagClick()
                        }
                    },
                )

                Text(
                    text = "총 ${uiState.results.size}장",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(end = 16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(8.dp),
                            ).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * UI 상태에 따라 적절한 검색 결과를 표시
 */
@Composable
private fun SearchResultsFromState(
    modifier: Modifier = Modifier,
    uiState: SearchUiState,
    isSelectionMode: Boolean,
    selectedPhotos: List<Photo>,
    onToggleImageSelection: (Photo) -> Unit,
    onImageLongPress: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
) {
    Box(modifier = modifier) {
        when (uiState) {
            is SearchUiState.Idle -> {
                SearchIdleCustom(modifier)
            }

            is SearchUiState.Loading -> {
                SearchLoadingStateCustom(
                    modifier = modifier,
                    onRefresh = onRetry,
                )
            }

            is SearchUiState.Empty -> {
                SearchEmptyStateCustom(
                    query = uiState.query,
                    modifier = modifier,
                )
            }

            is SearchUiState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = modifier,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = uiState.results.size,
                        key = { index -> index },
                    ) { index ->
                        val result = uiState.results[index]
                        val photo = result.photo
                        ImageGridUriItem(
                            photo = photo,
                            navController = navController,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedPhotos.contains(photo),
                            onToggleSelection = { onToggleImageSelection(photo) },
                            onLongPress = {
                                onImageLongPress()
                                onToggleImageSelection(photo)
                            },
                            cornerRadius = 12.dp,
                            topPadding = 0.dp,
                        )
                    }
                }
            }

            is SearchUiState.Error -> {
                SearchErrorStateFallbackCustom(modifier)
            }
        }
    }
}
