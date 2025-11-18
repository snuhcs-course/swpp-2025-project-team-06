package com.example.momentag.view

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.model.SearchResultItem
import com.example.momentag.model.SearchUiState
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.model.TagItem
import com.example.momentag.model.TagLoadingState
import com.example.momentag.ui.components.ChipSearchBar
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.SearchContentElement
import com.example.momentag.ui.components.SearchEmptyStateCustom
import com.example.momentag.ui.components.SearchHistoryItem
import com.example.momentag.ui.components.SearchLoadingStateCustom
import com.example.momentag.ui.components.SuggestionChip
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.horizontalArrangement
import com.example.momentag.ui.theme.verticalArrangement
import com.example.momentag.util.ShareUtils
import com.example.momentag.viewmodel.SearchViewModel
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

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
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    // 2. ViewModel 인스턴스
    val searchViewModel: SearchViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val semanticSearchState by searchViewModel.searchState.collectAsState()
    val selectedPhotos by searchViewModel.selectedPhotos.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val isSelectionMode by searchViewModel.isSelectionMode.collectAsState()
    val tagLoadingState by searchViewModel.tagLoadingState.collectAsState()
    val focusedElementId by searchViewModel.focusedElementId
    val tagSuggestions by searchViewModel.tagSuggestions.collectAsState()
    val shouldShowSearchHistoryDropdown by searchViewModel.shouldShowSearchHistoryDropdown.collectAsState()
    val ignoreFocusLoss by searchViewModel.ignoreFocusLoss

    // 4. 로컬 상태 변수
    var searchBarWidth by remember { mutableStateOf(0) }
    var searchBarRowHeight by remember { mutableStateOf(0) }
    var isCursorHidden by remember { mutableStateOf(false) }
    var isSelectionModeDelay by remember { mutableStateOf(false) }
    var previousImeBottom by remember { mutableStateOf(imeBottom) }

    // 5. Derived 상태 및 계산된 값
    val allTags = (tagLoadingState as? TagLoadingState.Success)?.tags ?: emptyList<TagItem>()
    val topSpacerHeight = 8.dp
    val contentItems = searchViewModel.contentItems
    val textStates = searchViewModel.textStates
    val focusRequesters = searchViewModel.focusRequesters
    val bringIntoViewRequesters = searchViewModel.bringIntoViewRequesters
    val currentFocusedElementId = rememberUpdatedState(focusedElementId)
    val currentFocusManager = rememberUpdatedState(focusManager)

    // 6. Remember된 객체들
    val listState = rememberLazyListState()

    LaunchedEffect(tagLoadingState) {
        if (tagLoadingState is TagLoadingState.Error) {
            val errorMessage = (tagLoadingState as TagLoadingState.Error).message ?: "Unknown error"

            // TODO : change to error box
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()

            searchViewModel.resetTagLoadingState()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    searchViewModel.loadServerTags()
                    isCursorHidden = false
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(imeBottom) {
        awaitFrame()

        val isClosing = imeBottom < previousImeBottom && imeBottom > 0
        val isClosed = imeBottom == 0 && previousImeBottom > 0

        if ((isClosing || isClosed) && currentFocusedElementId.value != null && !ignoreFocusLoss) {
            currentFocusManager.value.clearFocus()
        }

        previousImeBottom = imeBottom
    }

    LaunchedEffect(searchViewModel.requestFocus) {
        searchViewModel.requestFocus.collect { id ->
            isCursorHidden = true

            try {
                snapshotFlow { focusRequesters.containsKey(id) }
                    .filter { it == true }
                    .first()
            } catch (e: Exception) {
                isCursorHidden = false
                searchViewModel.resetIgnoreFocusLossFlag()
                return@collect
            }

            awaitFrame()

            val index = contentItems.indexOfFirst { it.id == id }
            if (index != -1) {
                val visibleItemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                val isFullyVisible =
                    if (visibleItemInfo != null) {
                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                        val itemEnd = visibleItemInfo.offset + visibleItemInfo.size
                        itemEnd <= viewportEnd + 1
                    } else {
                        false
                    }

                if (!isFullyVisible) {
                    listState.scrollToItem(index, searchBarWidth - 10)
                } else {
                    bringIntoViewRequesters[id]?.bringIntoView()
                }
            }

            focusRequesters[id]?.requestFocus()

            isCursorHidden = false
            searchViewModel.resetIgnoreFocusLossFlag()
        }
    }

    LaunchedEffect(searchViewModel.bringIntoView) {
        searchViewModel.bringIntoView.collect { id ->
            bringIntoViewRequesters[id]?.bringIntoView()
        }
    }

    var hasPerformedInitialSearch by rememberSaveable { mutableStateOf(false) }

    val placeholderText =
        if (initialQuery.isNotEmpty() && !hasPerformedInitialSearch) {
            initialQuery // 로딩 중(파싱 전)에는 initialQuery를 플레이스홀더로 사용
        } else {
            "Search with \"#tag\"" // 기본 플레이스홀더
        }

    BackHandler(enabled = isSelectionMode) {
        searchViewModel.setSelectionMode(false)
        searchViewModel.resetSelection()
    }

    BackHandler(enabled = !isSelectionMode) {
        onNavigateBack()
    }

    LaunchedEffect(isSelectionMode) {
        delay(200L) // 0.2초
        isSelectionModeDelay = isSelectionMode
    }

    val navBackStackEntry = navController.currentBackStackEntry

    LaunchedEffect(navBackStackEntry) {
        navBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<Boolean>("selectionModeComplete")
            ?.observe(navBackStackEntry) { isSuccess ->
                if (isSuccess) {
                    searchViewModel.setSelectionMode(false)
                    navBackStackEntry.savedStateHandle.remove<Boolean>("selectionModeComplete")
                }
            }
    }

    // 초기 검색어가 있으면 자동으로 Semantic Search 실행
    LaunchedEffect(initialQuery, hasPerformedInitialSearch) {
        if (initialQuery.isNotEmpty() && !hasPerformedInitialSearch) {
            searchViewModel.search(initialQuery)
        }
    }

    LaunchedEffect(initialQuery, hasPerformedInitialSearch, tagLoadingState) {
        if (initialQuery.isNotEmpty() && !hasPerformedInitialSearch && tagLoadingState is TagLoadingState.Success) {
            searchViewModel.selectHistoryItem(initialQuery)
            hasPerformedInitialSearch = true
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

    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is SearchUiState.Error) {
            errorMessage = uiState.message
            isErrorBannerVisible = true
        } else {
            // 로딩이 성공하거나, Idle 상태가 되면 배너를 숨깁니다.
            if (uiState is SearchUiState.Success || uiState is SearchUiState.Loading || uiState is SearchUiState.Idle) {
                isErrorBannerVisible = false
            }
        }
    }

    val topBarActions = @Composable {
        if (isSelectionModeDelay) {
            val isEnabled = selectedPhotos.isNotEmpty()
            IconButton(
                onClick = {
                    val photos = searchViewModel.getPhotosToShare()
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
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    intent = if (isEnabled) IconIntent.Primary else IconIntent.Disabled,
                )
            }
        }
    }

    val performSearch = {
        focusManager.clearFocus()
        searchViewModel.performSearch { route ->
            // 이미 search result 화면
        }
    }

    SearchResultScreenUi(
        listState = listState,
        contentItems = contentItems,
        textStates = textStates,
        focusRequesters = focusRequesters,
        bringIntoViewRequesters = bringIntoViewRequesters,
        focusedElementId = focusedElementId,
        isCursorHidden = isCursorHidden,
        tagSuggestions = tagSuggestions,
        shouldShowSearchHistoryDropdown = shouldShowSearchHistoryDropdown,
        allTags = allTags,
        parser = searchViewModel::parseQueryToElements,
        onPerformSearch = performSearch,
        onChipSearchBarContainerClick = searchViewModel::onContainerClick,
        onChipClick = searchViewModel::onChipClick,
        onFocus = searchViewModel::onFocus,
        onTextChange = searchViewModel::onTextChange,
        onAddTagFromSuggestion = searchViewModel::addTagFromSuggestion,
        searchBarRowHeight = searchBarRowHeight,
        onSearchBarRowHeightChange = { searchBarRowHeight = it },
        topSpacerHeight = topSpacerHeight,
        searchBarWidth = searchBarWidth,
        onSearchBarWidthChange = { searchBarWidth = it },
        uiState = uiState,
        isSelectionMode = isSelectionMode,
        selectedPhotos = selectedPhotos,
        onBackClick = {
            if (isSelectionMode) {
                searchViewModel.setSelectionMode(false)
                searchViewModel.resetSelection()
            } else {
                onNavigateBack()
            }
        },
        onToggleSelectionMode = {
            searchViewModel.setSelectionMode(!isSelectionMode)
            if (!isSelectionMode) {
                searchViewModel.resetSelection()
            }
        },
        onToggleImageSelection = { photo ->
            searchViewModel.togglePhoto(photo)
        },
        onImageLongPress = {
            if (!isSelectionMode) {
                searchViewModel.setSelectionMode(true)
            }
        },
        onCreateTagClick = {
            // Get the current search results
            val currentState = uiState as? SearchUiState.Success
            if (currentState != null) {
                // photo selection already stored in draftRepository
                // isSelectionMode = false
                navController.navigate(Screen.MyTags.route)
            }
        },
        onRetry = {
            performSearch()
            isErrorBannerVisible = false
        },
        navController = navController,
        topBarActions =
            if (uiState is SearchUiState.Success) {
                topBarActions
            } else {
                {}
            },
        searchHistory = searchHistory,
        onHistoryClick = { clickedQuery ->
            searchViewModel.selectHistoryItem(clickedQuery)
            focusManager.clearFocus()
            performSearch()
        },
        onHistoryDelete = { query ->
            searchViewModel.removeSearchHistory(query)
        },
        isErrorBannerVisible = isErrorBannerVisible,
        errorMessage = errorMessage,
        onDismissError = { isErrorBannerVisible = false },
        placeholder = placeholderText,
    )
}

/**
 * UI 전용 검색 결과 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreenUi(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    contentItems: List<SearchContentElement>,
    textStates: Map<String, TextFieldValue>,
    focusRequesters: Map<String, FocusRequester>,
    bringIntoViewRequesters: Map<String, BringIntoViewRequester>,
    focusedElementId: String?,
    isCursorHidden: Boolean,
    tagSuggestions: List<TagItem>,
    shouldShowSearchHistoryDropdown: Boolean,
    allTags: List<TagItem>,
    parser: (String, List<TagItem>) -> List<SearchContentElement>,
    onPerformSearch: () -> Unit,
    onChipSearchBarContainerClick: () -> Unit,
    onChipClick: (Int) -> Unit,
    onFocus: (String?) -> Unit,
    onTextChange: (String, TextFieldValue) -> Unit,
    onAddTagFromSuggestion: (TagItem) -> Unit,
    searchBarRowHeight: Int,
    onSearchBarRowHeightChange: (Int) -> Unit,
    topSpacerHeight: Dp,
    searchBarWidth: Int,
    onSearchBarWidthChange: (Int) -> Unit,
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
    topBarActions: @Composable () -> Unit = {},
    searchHistory: List<String>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    isErrorBannerVisible: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    placeholder: String,
) {
    LocalFocusManager.current
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
    ) { paddingValues ->
        SearchResultContent(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
//                    .padding(horizontal = 16.dp),
            listState = listState,
            contentItems = contentItems,
            textStates = textStates,
            focusRequesters = focusRequesters,
            bringIntoViewRequesters = bringIntoViewRequesters,
            focusedElementId = focusedElementId,
            isCursorHidden = isCursorHidden,
            tagSuggestions = tagSuggestions,
            shouldShowSearchHistoryDropdown = shouldShowSearchHistoryDropdown,
            allTags = allTags,
            parser = parser,
            onPerformSearch = onPerformSearch,
            onChipSearchBarContainerClick = onChipSearchBarContainerClick,
            onChipClick = onChipClick,
            onFocus = onFocus,
            onTextChange = onTextChange,
            onAddTagFromSuggestion = onAddTagFromSuggestion,
            searchBarRowHeight = searchBarRowHeight,
            onSearchBarRowHeightChange = onSearchBarRowHeightChange,
            topSpacerHeight = topSpacerHeight,
            searchBarWidth = searchBarWidth,
            onSearchBarWidthChange = onSearchBarWidthChange,
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
            isErrorBannerVisible = isErrorBannerVisible,
            errorMessage = errorMessage,
            onDismissError = onDismissError,
            placeholder = placeholder,
        )
    }
}

/**
 * 검색 결과 컨텐츠 (순수 UI)
 */
@Composable
private fun SearchResultContent(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    contentItems: List<SearchContentElement>,
    textStates: Map<String, TextFieldValue>,
    focusRequesters: Map<String, FocusRequester>,
    bringIntoViewRequesters: Map<String, BringIntoViewRequester>,
    focusedElementId: String?,
    isCursorHidden: Boolean,
    tagSuggestions: List<TagItem>,
    shouldShowSearchHistoryDropdown: Boolean,
    allTags: List<TagItem>,
    parser: (String, List<TagItem>) -> List<SearchContentElement>,
    onPerformSearch: () -> Unit,
    onChipSearchBarContainerClick: () -> Unit,
    onChipClick: (Int) -> Unit,
    onFocus: (String?) -> Unit,
    onTextChange: (String, TextFieldValue) -> Unit,
    onAddTagFromSuggestion: (TagItem) -> Unit,
    searchBarRowHeight: Int,
    onSearchBarRowHeightChange: (Int) -> Unit,
    topSpacerHeight: Dp,
    searchBarWidth: Int,
    onSearchBarWidthChange: (Int) -> Unit,
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
    isErrorBannerVisible: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    placeholder: String,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Box(
        modifier =
            modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    focusManager.clearFocus()
                },
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
                        focusManager.clearFocus()
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Input
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { onSearchBarRowHeightChange(it.size.height) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChipSearchBar(
                    modifier =
                        Modifier
                            .weight(1f)
                            .onSizeChanged { onSearchBarWidthChange(it.width) },
                    listState = listState,
                    isFocused = (focusedElementId != null),
                    isCursorHidden = isCursorHidden,
                    contentItems = contentItems,
                    textStates = textStates,
                    focusRequesters = focusRequesters,
                    bringIntoViewRequesters = bringIntoViewRequesters,
                    onSearch = onPerformSearch,
                    onContainerClick = onChipSearchBarContainerClick,
                    onChipClick = onChipClick,
                    onFocus = onFocus,
                    onTextChange = onTextChange,
                    placeholder = placeholder,
                )
                IconButton(
                    onClick = {
                        // TODO: Show filter dialog
                        Toast.makeText(context, "Filter", Toast.LENGTH_SHORT).show()
                    },
                    modifier =
                        Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp),
                            ),
                ) {
                    StandardIcon.Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter",
                        intent = IconIntent.Inverse,
                    )
                }
            }

            // 태그 추천 목록 (LazyRow)
            if (tagSuggestions.isNotEmpty()) {
                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { },
                            ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tagSuggestions, key = { it.tagId }) { tag ->
                        SuggestionChip(
                            tag = tag,
                            onClick = { onAddTagFromSuggestion(tag) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            AnimatedVisibility(visible = isErrorBannerVisible && errorMessage != null) {
                WarningBanner(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    title = "Search Failed",
                    message = errorMessage ?: "Unknown error",
                    onActionClick = onRetry,
                    onDismiss = onDismissError,
                    showActionButton = true,
                    showDismissButton = true,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // search history dropdown
        AnimatedVisibility(
            visible = shouldShowSearchHistoryDropdown,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            modifier =
                Modifier
                    .offset(y = topSpacerHeight + with(LocalDensity.current) { searchBarRowHeight.toDp() } + 4.dp)
                    .padding(horizontal = 16.dp)
                    .zIndex(1f),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 48.dp + 8.dp),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(searchHistory.take(4), key = { it }) { query ->
                        SearchHistoryItem(
                            query = query,
                            allTags = allTags,
                            parser = parser,
                            onHistoryClick = onHistoryClick,
                            onHistoryDelete = onHistoryDelete,
                        )
                    }
                }
            }
        }

        // Bottom overlay: Photo count + Create Tag button
        if (uiState is SearchUiState.Success) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .padding(horizontal = 16.dp),
            ) {
                // Only show CreateTagButton when in selection mode and photos are selected
                if (isSelectionMode && selectedPhotos.isNotEmpty()) {
                    CreateTagButton(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(start = 16.dp),
                        text = "Add Tag (${selectedPhotos.size})",
                        onClick = {
                            onCreateTagClick()
                        },
                    )
                }
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
                Box(modifier = Modifier.fillMaxSize()) // ?
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
