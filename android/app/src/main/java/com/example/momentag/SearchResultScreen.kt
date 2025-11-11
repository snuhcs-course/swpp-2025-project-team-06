package com.example.momentag

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.Photo
import com.example.momentag.model.SearchResultItem
import com.example.momentag.model.SearchUiState
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.SearchBarControlledCustom
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.search.components.SearchEmptyStateCustom
import com.example.momentag.ui.search.components.SearchIdleCustom
import com.example.momentag.ui.search.components.SearchLoadingStateCustom
import com.example.momentag.ui.theme.horizontalArrangement
import com.example.momentag.ui.theme.verticalArrangement
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

    val semanticSearchState by searchViewModel.searchState.collectAsState()
    val selectedPhotos by searchViewModel.selectedPhotos.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val searchText by searchViewModel.searchText.collectAsState()

    var isSelectionMode by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(BottomTab.SearchResultScreen) }
    var showMenu by remember { mutableStateOf(false) }
    var isSelectionModeDelay by remember { mutableStateOf(false) } // for dropdown animation

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        searchViewModel.resetSelection()
    }

    LaunchedEffect(isSelectionMode) {
        kotlinx.coroutines.delay(200L) // 0.2초
        isSelectionModeDelay = isSelectionMode
    }

    val focusManager = LocalFocusManager.current

    // 초기 검색어가 있으면 자동으로 Semantic Search 실행
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            searchViewModel.onSearchTextChanged(initialQuery)
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

    var showErrorBanner by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is SearchUiState.Error) {
            errorMessage = uiState.message
            showErrorBanner = true
        } else {
            // 로딩이 성공하거나, Idle 상태가 되면 배너를 숨깁니다.
            if (uiState is SearchUiState.Success || uiState is SearchUiState.Loading || uiState is SearchUiState.Idle) {
                showErrorBanner = false
            }
        }
    }

    val topBarActions = @Composable {
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
                        text = { Text("Share") },
                        onClick = {
                            val photos = searchViewModel.getPhotosToShare()
                            ShareUtils.sharePhotos(context, photos)

                            Toast
                                .makeText(
                                    context,
                                    "Share ${photos.size} photo(s)",
                                    Toast.LENGTH_SHORT,
                                ).show()

                            showMenu = false
                            isSelectionMode = false
                            searchViewModel.resetSelection()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Cancel") },
                        onClick = {
                            isSelectionMode = false
                            searchViewModel.resetSelection()
                            showMenu = false
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Select") },
                        onClick = {
                            isSelectionMode = true
                            showMenu = false
                        },
                    )
                }
            }
        }
    }

    SearchResultScreenUi(
        searchText = searchText,
        onSearchTextChange = { searchViewModel.onSearchTextChanged(it) },
        onSearchSubmit = {
            if (searchText.isNotEmpty()) {
                if (isSelectionMode) {
                    isSelectionMode = false
                    searchViewModel.resetSelection()
                }
                searchViewModel.search(searchText)
                focusManager.clearFocus()
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
            showErrorBanner = false
        },
        navController = navController,
        currentTab = currentTab,
        onTabSelected = { tab ->
            currentTab = tab
            when (tab) {
                BottomTab.HomeScreen -> {
                    searchViewModel.resetSelection()
                    navController.navigate(Screen.Home.route)
                }
                BottomTab.SearchResultScreen -> {
                    // 이미 Search 화면
                }
                BottomTab.MyTagsScreen -> {
                    searchViewModel.resetSelection()
                    navController.navigate(Screen.MyTags.route)
                }
                BottomTab.StoryScreen -> {
                    searchViewModel.resetSelection()
                    navController.navigate(Screen.Story.route)
                }
            }
        },
        topBarActions =
            if (uiState is SearchUiState.Success) {
                topBarActions
            } else {
                {}
            },
        searchHistory = searchHistory,
        onHistoryClick = { query ->
            searchViewModel.onSearchTextChanged(query)
            searchViewModel.search(query)
        },
        onHistoryDelete = { query ->
            searchViewModel.removeSearchHistory(query)
        },
        showErrorBanner = showErrorBanner,
        errorMessage = errorMessage,
        onDismissError = { showErrorBanner = false },
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
    topBarActions: @Composable () -> Unit = {},
    searchHistory: List<String>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    showErrorBanner: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CommonTopBar(
                title = "Search Results",
                showBackButton = true,
                onBackClick = onBackClick,
                actions = topBarActions,
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
            searchHistory = searchHistory,
            onHistoryClick = onHistoryClick,
            onHistoryDelete = onHistoryDelete,
            showErrorBanner = showErrorBanner,
            errorMessage = errorMessage,
            onDismissError = onDismissError,
        )
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
    searchHistory: List<String>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    showErrorBanner: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
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
                horizontalArrangement = Arrangement.Start,
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
                searchHistory = searchHistory,
                onHistoryClick = onHistoryClick,
                onHistoryDelete = onHistoryDelete,
            )
            AnimatedVisibility(visible = showErrorBanner && errorMessage != null) {
                WarningBanner(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    title = "Search Failed",
                    message = errorMessage ?: "Unknown error",
                    onActionClick = onRetry, // 재시도
                    onDismiss = onDismissError, // 닫기
                    showActionButton = true,
                    showDismissButton = true,
                )
            }

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
                    text = "${uiState.results.size} photos total",
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
    searchHistory: List<String>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
) {
    Box(modifier = modifier) {
        when (uiState) {
            is SearchUiState.Idle -> {
                SearchIdleCustom(
                    history = searchHistory,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete,
                    modifier = Modifier.fillMaxSize(),
                )
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
                    horizontalArrangement = Arrangement.spacedBy(horizontalArrangement),
                    verticalArrangement = Arrangement.spacedBy(verticalArrangement),
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
                            },
                        )
                    }
                }
            }

            is SearchUiState.Error -> {
                Box(modifier = Modifier.fillMaxHeight())
            }
        }
    }
}
