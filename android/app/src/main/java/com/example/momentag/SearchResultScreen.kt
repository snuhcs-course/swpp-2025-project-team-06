package com.example.momentag

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.ImageContext
import com.example.momentag.model.SearchResultItem
import com.example.momentag.model.SearchUiState
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.ErrorOverlay
import com.example.momentag.ui.components.SearchBarControlledCustom
import com.example.momentag.ui.search.components.SearchEmptyStateCustom
import com.example.momentag.ui.search.components.SearchErrorStateFallbackCustom
import com.example.momentag.ui.search.components.SearchIdleCustom
import com.example.momentag.ui.search.components.SearchLoadingStateCustom
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.Semi_background
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.ImageDetailViewModel
import com.example.momentag.viewmodel.PhotoTagViewModel
import com.example.momentag.viewmodel.SearchViewModel
import com.example.momentag.viewmodel.ServerViewModel
import com.example.momentag.viewmodel.ViewModelFactory

@Suppress("ktlint:standard:function-naming")
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
    imageDetailViewModel: ImageDetailViewModel? = null,
    searchViewModel: SearchViewModel =
        viewModel(
            factory = ViewModelFactory(LocalContext.current),
        ),
    serverViewModel: ServerViewModel =
        viewModel(
            factory = ViewModelFactory(LocalContext.current),
        ),
    photoTagViewModel: PhotoTagViewModel,
) {
    var searchText by remember { mutableStateOf(initialQuery) }
    val semanticSearchState by searchViewModel.searchState.collectAsState()
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val allServerPhotos by serverViewModel.allPhotos.collectAsState()

    // 초기 검색어가 있으면 자동으로 Semantic Search 실행
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            searchViewModel.search(initialQuery)
        }
        serverViewModel.getAllPhotos()
    }

    // SemanticSearchState를 SearchUiState로 변환
    val uiState =
        remember(semanticSearchState) {
            when (semanticSearchState) {
                is SemanticSearchState.Idle -> SearchUiState.Idle
                is SemanticSearchState.Loading -> SearchUiState.Loading
                is SemanticSearchState.Success -> {
                    val photoIds = (semanticSearchState as SemanticSearchState.Success).photoIds
                    val searchResults =
                        photoIds.mapNotNull { photoId ->
                            try {
                                val uri =
                                    ContentUris.withAppendedId(
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        photoId.toLong(),
                                    )
                                SearchResultItem(
                                    query = (semanticSearchState as SemanticSearchState.Success).query,
                                    imageUri = uri,
                                )
                            } catch (e: NumberFormatException) {
                                null
                            }
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
        selectedImages = selectedImages,
        onBackClick = onNavigateBack,
        onToggleSelectionMode = {
            isSelectionMode = !isSelectionMode
            if (!isSelectionMode) {
                selectedImages = emptyList()
            }
        },
        onToggleImageSelection = { uri ->
            selectedImages =
                if (selectedImages.contains(uri)) {
                    selectedImages - uri
                } else {
                    selectedImages + uri
                }
        },
        onImageClick = { uri ->
            if (imageDetailViewModel != null && uiState is SearchUiState.Success) {
                val allImages = uiState.results.mapNotNull { it.imageUri }
                val index = allImages.indexOf(uri)
                imageDetailViewModel.setImageContext(
                    ImageContext(
                        images = allImages,
                        currentIndex = index.coerceAtLeast(0),
                        contextType = ImageContext.ContextType.SEARCH_RESULT,
                    ),
                )
            }
            navController.navigate(Screen.Image.createRoute(uri))
        },
        onImageLongPress = {
            if (!isSelectionMode) {
                isSelectionMode = true
            }
        },
        onCreateTagClick = {
            var selectedImagesId = mutableListOf<Long>()
            for (uri in selectedImages) {
                selectedImagesId.add(ContentUris.parseId(uri))
            }
            val selectedPhotoObjects = allServerPhotos.filter { photo ->
                photo.photoPathId in selectedImagesId
            }
            photoTagViewModel.setInitialData(null, selectedPhotoObjects)
            navController.navigate(Screen.AddTag.route)
            isSelectionMode = false
            selectedImages = emptyList()
        },
        onRetry = {
            if (searchText.isNotEmpty()) {
                searchViewModel.search(searchText)
            }
        },
        navController = navController,
        imageDetailViewModel = imageDetailViewModel,
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
    selectedImages: List<Uri>,
    onBackClick: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleImageSelection: (Uri) -> Unit,
    onImageClick: (Uri) -> Unit,
    onImageLongPress: () -> Unit,
    onCreateTagClick: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
    imageDetailViewModel: ImageDetailViewModel? = null,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            containerColor = Background,
            topBar = {
                BackTopBar(
                    title = "Search Results",
                    onBackClick = onBackClick,
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
                selectedImages = selectedImages,
                onBackClick = onBackClick,
                onToggleSelectionMode = onToggleSelectionMode,
                onToggleImageSelection = onToggleImageSelection,
                onImageClick = onImageClick,
                onImageLongPress = onImageLongPress,
                onCreateTagClick = onCreateTagClick,
                onRetry = onRetry,
                navController = navController,
                imageDetailViewModel = imageDetailViewModel,
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
@Suppress("ktlint:standard:function-naming")
@Composable
private fun SearchResultContent(
    modifier: Modifier = Modifier,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    uiState: SearchUiState,
    isSelectionMode: Boolean,
    selectedImages: List<Uri>,
    onBackClick: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleImageSelection: (Uri) -> Unit,
    onImageClick: (Uri) -> Unit,
    onImageLongPress: () -> Unit,
    onCreateTagClick: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
    imageDetailViewModel: ImageDetailViewModel? = null,
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
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Serif,
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
                                        Button
                                    } else {
                                        Semi_background
                                    },
                                contentColor = if (isSelectionMode) Color.White else Word,
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
                            fontSize = 14.sp,
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
                selectedImages = selectedImages,
                onToggleImageSelection = onToggleImageSelection,
                onImageClick = onImageClick,
                onImageLongPress = onImageLongPress,
                onRetry = onRetry,
                navController = navController,
                imageDetailViewModel = imageDetailViewModel,
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
                    enabled = !isSelectionMode || selectedImages.isNotEmpty(),
                    onClick = {
                        if (!isSelectionMode) {
                            onToggleSelectionMode()
                        } else if (selectedImages.isNotEmpty()) {
                            onCreateTagClick()
                        }
                    },
                )

                Text(
                    text = "총 ${uiState.results.size}장",
                    fontSize = 14.sp,
                    color = Temp_word,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(end = 16.dp)
                            .background(
                                color = Background.copy(alpha = 0.9f),
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
@Suppress("ktlint:standard:function-naming")
@Composable
private fun SearchResultsFromState(
    modifier: Modifier,
    uiState: SearchUiState,
    isSelectionMode: Boolean,
    selectedImages: List<Uri>,
    onToggleImageSelection: (Uri) -> Unit,
    onImageClick: (Uri) -> Unit,
    onImageLongPress: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
    imageDetailViewModel: ImageDetailViewModel? = null,
) {
    Box(modifier = modifier) {
        when (uiState) {
            is SearchUiState.Idle -> {
                SearchIdleCustom(Modifier.fillMaxSize())
            }

            is SearchUiState.Loading -> {
                SearchLoadingStateCustom(
                    modifier = Modifier.fillMaxSize(),
                    onRefresh = onRetry,
                )
            }

            is SearchUiState.Empty -> {
                SearchEmptyStateCustom(
                    query = uiState.query,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SearchUiState.Success -> {
                val allImages = uiState.results.mapNotNull { it.imageUri }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        count = uiState.results.size,
                        key = { index -> index },
                    ) { index ->
                        val result = uiState.results[index]
                        result.imageUri?.let { uri ->
                            ImageGridUriItem(
                                imageUri = uri,
                                navController = navController,
                                imageDetailViewModel = imageDetailViewModel,
                                allImages = allImages,
                                contextType = ImageContext.ContextType.SEARCH_RESULT,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedImages.contains(uri),
                                onToggleSelection = { onToggleImageSelection(uri) },
                                onLongPress = {
                                    onImageLongPress()
                                    onToggleImageSelection(uri)
                                },
                                cornerRadius = 12.dp,
                                topPadding = 0.dp,
                            )
                        }
                    }
                }
            }

            is SearchUiState.Error -> {
                SearchErrorStateFallbackCustom(Modifier.fillMaxSize())
            }
        }
    }
}
