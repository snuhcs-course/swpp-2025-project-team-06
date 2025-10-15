package com.example.momentag

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.momentag.model.ImageContext
import com.example.momentag.model.SearchResultItem
import com.example.momentag.model.SearchUiState
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.errorOverlay
import com.example.momentag.ui.components.warningBanner
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Semi_background
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.ImageDetailViewModel
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
    imageDetailViewModel: ImageDetailViewModel? = null,
    searchViewModel: SearchViewModel =
        viewModel(
            factory = ViewModelFactory(LocalContext.current),
        ),
) {
    var searchText by remember { mutableStateOf(initialQuery) }
    val semanticSearchState by searchViewModel.searchState.collectAsState()
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }

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
                    val photoIds = (semanticSearchState as SemanticSearchState.Success).photoIds
                    // photo_path_id (MediaStore ID)를 Uri로 변환
                    val searchResults =
                        photoIds.mapNotNull { photoId ->
                            try {
                                val uri =
                                    android.content.ContentUris.withAppendedId(
                                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        photoId.toLong(),
                                    )
                                SearchResultItem(
                                    query = (semanticSearchState as SemanticSearchState.Success).query,
                                    imageUri = uri,
                                )
                            } catch (e: NumberFormatException) {
                                // 잘못된 ID는 무시
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
                // Semantic Search 실행
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
            // 이미지 컨텍스트 설정
            if (imageDetailViewModel != null && uiState is SearchUiState.Success) {
                val allImages = uiState.results.mapNotNull { it.imageUri }
                val index = allImages.indexOf(uri)
                imageDetailViewModel.setImageContext(
                    ImageContext(
                        images = allImages,
                        currentIndex = index.coerceAtLeast(0),
                        contextType = ImageContext.ContextType.SEARCH_RESULT
                    )
                )
            }
            // 이미지 상세 화면으로 이동
            navController.navigate(Screen.Image.createRoute(uri))
        },
        onImageLongPress = {
            if (!isSelectionMode) {
                isSelectionMode = true
            }
        },
        onCreateTagClick = {
            // TODO: 태그 생성 화면으로 이동
        },
        onRetry = {
            // 재시도 로직 - Semantic Search 재실행
            if (searchText.isNotEmpty()) {
                searchViewModel.search(searchText)
            }
        },
        navController = navController,
    )
}

/**
 * UI 전용 검색 결과 화면
 *
 * @param searchText 현재 검색어 (외부에서 관리)
 * @param onSearchTextChange 검색어 변경 콜백
 * @param onSearchSubmit 검색 실행 콜백
 * @param uiState 검색 결과 UI 상태 (Loading/Success/Error/Empty/Idle)
 * @param isSelectionMode 선택 모드 활성화 여부
 * @param selectedImages 선택된 이미지 URI 목록
 * @param onBackClick 뒤로가기 버튼 클릭 콜백
 * @param onToggleSelectionMode 선택 모드 토글 콜백
 * @param onToggleImageSelection 개별 이미지 선택/해제 콜백
 * @param onImageClick 이미지 클릭 콜백 (선택 모드가 아닐 때)
 * @param onImageLongPress 이미지 롱프레스 콜백
 * @param onCreateTagClick 태그 생성 버튼 클릭 콜백
 * @param modifier Modifier
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreenUi(
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
    modifier: Modifier = Modifier,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            containerColor = Background,
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
            )

            if (uiState is SearchUiState.Error) {
                // 🔥 Scaffold 위를 완전히 덮는 오버레이
                Box(
                    modifier = Modifier
                        .matchParentSize()
                ) {
                    errorOverlay(
                        modifier = Modifier.fillMaxSize(),
                        errorMessage = uiState.message,
                        onRetry = onRetry,
                        onDismiss = { navController.popBackStack() }
                    )
                }
            }  }
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
    selectedImages: List<Uri>,
    onBackClick: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleImageSelection: (Uri) -> Unit,
    onImageClick: (Uri) -> Unit,
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
            Spacer(modifier = Modifier.height(32.dp))

            // Header
            SearchResultHeader(onBackClick = onBackClick)
            Spacer(modifier = Modifier.height(24.dp))

            // Instruction + Selection mode toggle (같은 라인)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Search for Photo instruction
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
                // TODO : 선택모드 자체를 빼도 괜찮을 수도 있겠다? 선택하고 할 일이 tag만드는거? 아니면 삭제하거나? 복사하거나 공유하는 일이 있을 수도 있으니까.... 있어야 될 것도 같고;;
                // TODO : UI TopBar에 다 올려두기
                // Right: Selection mode toggle (결과가 있을 때만)
                if (uiState is SearchUiState.Success && uiState.results.isNotEmpty()) {
                    Button(
                        onClick = onToggleSelectionMode,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor =
                                    if (isSelectionMode) {
                                        com.example.momentag.ui.theme.Button
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
            SearchInputField(
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
                // Create Tag button (항상 표시)
                // 선택 모드가 아닐 때: 클릭 시 자동으로 선택 모드 활성화
                // 선택 모드일 때: 선택된 이미지가 있어야 활성화
                CreateTagButton(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp),
                    text = "Create Tag",
                    enabled = !isSelectionMode || selectedImages.isNotEmpty(),
                    onClick = {
                        if (!isSelectionMode) {
                            // 선택 모드로 전환
                            onToggleSelectionMode()
                        } else if (selectedImages.isNotEmpty()) {
                            // 태그 생성
                            onCreateTagClick()
                        }
                    },
                )

                // Photo count (오른쪽에 표시 - Create Tag 버튼과 대칭)
                Text(
                    text = "총 ${uiState.results.size}장",
                    fontSize = 14.sp,
                    color = Temp_word,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
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
 * Error 상태일 때는 기존 Success 결과 위에 오버레이로 표시 (내용 대체 X)
 */

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
) {
    Box(modifier = modifier) {
        when (uiState) {
            is SearchUiState.Idle -> {
                SearchStatusMessage(Modifier.fillMaxSize()) {
                    Text(text = "검색어를 입력해주세요.", color = Temp_word)
                }
            }

            is SearchUiState.Loading -> {
                LoadingScreen(
                    modifier = Modifier.fillMaxSize(),
                    onRefresh = onRetry,
                )
            }

            is SearchUiState.Empty -> {
                SearchStatusMessage(Modifier.fillMaxSize()) {
                    Text(
                        text = "\"${uiState.query}\"에 대한 검색 결과가 없습니다.",
                        color = Temp_word,
                    )
                }
            }

            is SearchUiState.Success -> {
                SearchResultGrid(
                    modifier = Modifier.fillMaxSize(),
                    results = uiState.results,
                    isSelectionMode = isSelectionMode,
                    selectedImages = selectedImages,
                    onToggleImageSelection = onToggleImageSelection,
                    onImageClick = onImageClick,
                    onImageLongPress = onImageLongPress,
                )
            }

            is SearchUiState.Error -> {
                // Error 상태: 기본 메시지를 표시 (Success 결과가 없을 때)
                SearchStatusMessage(Modifier.fillMaxSize()) {
                    Text(text = "오류가 발생했습니다.", color = Temp_word)
                }
            }
        }
    }
}

/**
 * 헤더 (뒤로가기 기능)
 */

@Composable
private fun SearchResultHeader(onBackClick: () -> Unit) {
    Text(
        text = "Search Results",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Serif,
        modifier = Modifier.clickable { onBackClick() },
    )
}

/**
 * 검색 안내 문구
 */

@Composable
private fun SearchInstructionRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
}

/**
 * 선택 모드 토글 버튼
 */

@Composable
private fun SelectionModeToggle(
    isSelectionMode: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            onClick = onToggle,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (isSelectionMode) {
                            com.example.momentag.ui.theme.Button
                        } else {
                            Semi_background
                        },
                    contentColor = if (isSelectionMode) Color.White else Word,
                ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = if (isSelectionMode) "선택 모드 해제" else "선택 모드",
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isSelectionMode) "선택 모드" else "선택",
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * 검색 입력 필드
 */

@Composable
private fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Search Anything...", color = Temp_word) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Semi_background,
                unfocusedContainerColor = Semi_background,
                unfocusedTextColor = Word,
                disabledTextColor = Word,
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "검색 실행",
                )
            }
        },
    )
}

/**
 * 상태 메시지 표시 (로딩/에러/빈 결과 등)
 */

@Composable
private fun SearchStatusMessage(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * 검색 결과 그리드
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultGrid(
    modifier: Modifier,
    results: List<SearchResultItem>,
    isSelectionMode: Boolean,
    selectedImages: List<Uri>,
    onToggleImageSelection: (Uri) -> Unit,
    onImageClick: (Uri) -> Unit,
    onImageLongPress: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = results.size,
            key = { index -> index }, // 🔑 인덱스 기반 고유 키 (중복 방지)
        ) { index ->
            val result = results[index]
            result.imageUri?.let { uri ->
                SearchPhotoItem(
                    imageUri = uri,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedImages.contains(uri),
                    onToggleSelection = { onToggleImageSelection(uri) },
                    onClick = { onImageClick(uri) },
                    onLongPress = {
                        onImageLongPress()
                        // 롱프레스 시 해당 아이템도 선택
                        onToggleImageSelection(uri)
                    },
                )
            }
        }
    }
}

/**
 * 개별 사진 아이템 (선택 모드 지원)
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchPhotoItem(
    imageUri: Uri,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
    ) {
        // 이미지
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelection()
                            } else {
                                onClick()
                            }
                        },
                        onLongClick = {
                            // 선택 모드가 아닐 때만 롱프레스로 선택 모드 진입
                            if (!isSelectionMode) {
                                onLongPress() // 선택 모드 활성화 + 아이템 선택
                            }
                        },
                    ).alpha(if (isSelectionMode && isSelected) 0.5f else 1f),
            contentScale = ContentScale.Crop,
        )

        // 선택 표시 (선택 모드일 때만)
        if (isSelectionMode) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (isSelected) {
                                Color.Black.copy(alpha = 0.4f)
                            } else {
                                Color.Transparent
                            },
                        ),
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(48.dp),
                )
            }
        }
    }
}

/**
 * 로딩 화면 (곰돌이 + Loading 텍스트 + Progress Bar + 경고 메시지)
 *
 * @param modifier Modifier
 * @param onRefresh 새로고침 버튼 클릭 콜백
 */

@Composable
private fun LoadingScreen(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
) {
    // 5초 후에 경고 메시지 표시
    var showWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000) // 5초 대기
        showWarning = true
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        // 🔹 중앙 로딩 섹션
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🐻", fontSize = 80.sp, modifier = Modifier.padding(bottom = 16.dp))
            Text(
                text = "Loading ...",
                fontSize = 18.sp,
                color = Word,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = com.example.momentag.ui.theme.Button,
                strokeWidth = 4.dp,
            )
        }

        // 🔹 하단 경고 배너
        if (showWarning) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
            ) {
                warningBanner(
                    title = "Loading is taking longer than usual.",
                    message = "Please refresh the page.",
                    onActionClick = onRefresh,
                )
            }
        }
    }
}

// ========================================
// 프리뷰 (UI 테스트용)
// ========================================

/**
 * 로딩 화면만 보는 프리뷰 (경고 메시지 없음 - 5초 전)
 */

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewLoadingScreenWithoutWarning() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background),
    ) {
        LoadingScreen(
            modifier = Modifier.fillMaxSize(),
            onRefresh = {},
        )
    }
}

/**
 * 로딩 화면만 보는 프리뷰 (경고 메시지 있음 - 5초 후)
 */

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewLoadingScreenWithWarning() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background),
    ) {
        // 🔹 중앙 로딩 섹션
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🐻", fontSize = 80.sp, modifier = Modifier.padding(bottom = 16.dp))
            Text(
                text = "Loading ...",
                fontSize = 18.sp,
                color = Word,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = com.example.momentag.ui.theme.Button,
                strokeWidth = 4.dp,
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
        ) {
            warningBanner(
                title = "Loading is taking longer than usual.",
                message = "Please refresh the page.",
                onActionClick = {},
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewSearchResultScreenIdle() {
    SearchResultScreenUi(
        searchText = "",
        onSearchTextChange = {},
        onSearchSubmit = {},
        uiState = SearchUiState.Idle,
        isSelectionMode = false,
        selectedImages = emptyList(),
        onBackClick = {},
        onToggleSelectionMode = {},
        onToggleImageSelection = {},
        onImageClick = {},
        onImageLongPress = {},
        onCreateTagClick = {},
        onRetry = {},
        navController = rememberNavController(),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewSearchResultScreenLoading() {
    SearchResultScreenUi(
        searchText = "nature",
        onSearchTextChange = {},
        onSearchSubmit = {},
        uiState = SearchUiState.Loading,
        isSelectionMode = false,
        selectedImages = emptyList(),
        onBackClick = {},
        onToggleSelectionMode = {},
        onToggleImageSelection = {},
        onImageClick = {},
        onImageLongPress = {},
        onCreateTagClick = {},
        onRetry = {},
        navController = rememberNavController(),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewSearchResultScreenEmpty() {
    SearchResultScreenUi(
        searchText = "nonexistent",
        onSearchTextChange = {},
        onSearchSubmit = {},
        uiState = SearchUiState.Empty("nonexistent"),
        isSelectionMode = false,
        selectedImages = emptyList(),
        onBackClick = {},
        onToggleSelectionMode = {},
        onToggleImageSelection = {},
        onImageClick = {},
        onImageLongPress = {},
        onCreateTagClick = {},
        onRetry = {},
        navController = rememberNavController(),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewSearchResultScreenError() {
    SearchResultScreenUi(
        searchText = "error",
        onSearchTextChange = {},
        onSearchSubmit = {},
        uiState = SearchUiState.Error("Network Error!\nPlease check your internet connection."),
        isSelectionMode = false,
        selectedImages = emptyList(),
        onBackClick = {},
        onToggleSelectionMode = {},
        onToggleImageSelection = {},
        onImageClick = {},
        onImageLongPress = {},
        onCreateTagClick = {},
        onRetry = {},
        navController = rememberNavController(),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewSearchResultScreenSuccess() {
    val dummyResults =
        listOf(
            SearchResultItem("nature", Uri.parse("content://media/1")),
            SearchResultItem("nature", Uri.parse("content://media/2")),
            SearchResultItem("nature", Uri.parse("content://media/3")),
            SearchResultItem("nature", Uri.parse("content://media/4")),
            SearchResultItem("nature", Uri.parse("content://media/5")),
            SearchResultItem("nature", Uri.parse("content://media/6")),
        )

    SearchResultScreenUi(
        searchText = "nature",
        onSearchTextChange = {},
        onSearchSubmit = {},
        uiState = SearchUiState.Success(dummyResults, "nature"),
        isSelectionMode = false,
        selectedImages = emptyList(),
        onBackClick = {},
        onToggleSelectionMode = {},
        onToggleImageSelection = {},
        onImageClick = {},
        onImageLongPress = {},
        onCreateTagClick = {},
        onRetry = {},
        navController = rememberNavController(),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PreviewSearchResultScreenSelectionMode() {
    val dummyResults =
        listOf(
            SearchResultItem("nature", Uri.parse("content://media/1")),
            SearchResultItem("nature", Uri.parse("content://media/2")),
            SearchResultItem("nature", Uri.parse("content://media/3")),
            SearchResultItem("nature", Uri.parse("content://media/4")),
            SearchResultItem("nature", Uri.parse("content://media/5")),
            SearchResultItem("nature", Uri.parse("content://media/6")),
        )

    val selectedImages =
        listOf(
            Uri.parse("content://media/1"),
            Uri.parse("content://media/3"),
        )

    SearchResultScreenUi(
        searchText = "nature",
        onSearchTextChange = {},
        onSearchSubmit = {},
        uiState = SearchUiState.Success(dummyResults, "nature"),
        isSelectionMode = true,
        selectedImages = selectedImages,
        onBackClick = {},
        onToggleSelectionMode = {},
        onToggleImageSelection = {},
        onImageClick = {},
        onImageLongPress = {},
        onCreateTagClick = {},
        onRetry = {},
        navController = rememberNavController(),
    )
}
