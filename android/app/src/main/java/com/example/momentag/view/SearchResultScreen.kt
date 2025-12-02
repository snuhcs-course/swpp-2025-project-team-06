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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.ChipSearchBar
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.SearchBarState
import com.example.momentag.ui.components.SearchContentElement
import com.example.momentag.ui.components.SearchEmptyStateCustom
import com.example.momentag.ui.components.SearchHistoryItem
import com.example.momentag.ui.components.SearchLoadingStateCustom
import com.example.momentag.ui.components.SuggestionChip
import com.example.momentag.ui.components.VerticalScrollbar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import com.example.momentag.util.ShareUtils
import com.example.momentag.viewmodel.SearchViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 *  * ========================================
 *  * SearchResultScreen - 검색 결과 화면
 *  * ========================================
 * Main screen displaying Semantic Search results
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
    val selectedPhotoIds = selectedPhotos.keys
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val isSelectionMode by searchViewModel.isSelectionMode.collectAsState()
    val scrollToIndex by searchViewModel.scrollToIndex.collectAsState()
    val isLoadingMore by searchViewModel.isLoadingMore.collectAsState()
    val hasMore by searchViewModel.hasMore.collectAsState()
    val tags by searchViewModel.tags.collectAsState()

    // 4. 로컬 상태 변수
    var searchBarWidth by remember { mutableStateOf(0) }
    var searchBarRowHeight by remember { mutableStateOf(0) }
    var isCursorHidden by remember { mutableStateOf(false) }
    var isSelectionModeDelay by remember { mutableStateOf(false) }
    var previousImeBottom by remember { mutableStateOf(imeBottom) }
    var previousQuery by remember { mutableStateOf<String?>(null) }
    var savedQuery by rememberSaveable { mutableStateOf(initialQuery) }

    // 5. Derived 상태 및 계산된 값
    val allTags = tags
    val topSpacerHeight = Dimen.ItemSpacingSmall

    // 6. Remember된 객체들
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // 7. SearchBarState instance
    val searchBarState = remember { SearchBarState(scope) }
    val contentItems = searchBarState.contentItems
    val textStates = searchBarState.textStates
    val focusRequesters = searchBarState.focusRequesters
    val bringIntoViewRequesters = searchBarState.bringIntoViewRequesters
    val focusedElementId = searchBarState.focusedElementId.value
    val ignoreFocusLoss = searchBarState.ignoreFocusLoss.value
    val currentFocusedElementId = rememberUpdatedState(focusedElementId)
    val currentFocusManager = rememberUpdatedState(focusManager)

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isCursorHidden = false
                    // Restore scroll position when returning from ImageDetailScreen
                    searchViewModel.restoreScrollPosition()
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update saved query when search is performed
    LaunchedEffect(semanticSearchState) {
        if (semanticSearchState is SearchViewModel.SemanticSearchState.Success) {
            savedQuery = (semanticSearchState as SearchViewModel.SemanticSearchState.Success).query
        }
    }

    // Restore query when tags are loaded or saved query exists
    LaunchedEffect(tags, savedQuery) {
        if (savedQuery.isNotEmpty() && tags.isNotEmpty()) {
            val hasContent =
                contentItems.any { it is SearchContentElement.Chip } ||
                    textStates.values.any { it.text.replace("\u200B", "").isNotEmpty() }

            if (!hasContent) {
                searchBarState.selectHistoryItem(savedQuery, tags)
            }
        }
    }

    // Scroll restoration: restore scroll position when returning from ImageDetailScreen
    LaunchedEffect(scrollToIndex) {
        scrollToIndex?.let { index ->
            gridState.animateScrollToItem(index)
            searchViewModel.clearScrollToIndex()
        }
    }

    // Reset scroll position when new search results arrive (but not for pagination)
    LaunchedEffect(semanticSearchState) {
        if (semanticSearchState is SearchViewModel.SemanticSearchState.Success) {
            val currentQuery = (semanticSearchState as SearchViewModel.SemanticSearchState.Success).query
            // Scroll to top only when the query changes (new search)
            // Don't scroll when query is the same (pagination)
            if (previousQuery != null && previousQuery != currentQuery) {
                gridState.scrollToItem(0)
            }
            previousQuery = currentQuery
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

    LaunchedEffect(searchBarState.requestFocus) {
        searchBarState.requestFocus.collect { id ->
            isCursorHidden = true

            try {
                snapshotFlow { focusRequesters.containsKey(id) }
                    .filter { it == true }
                    .first()
            } catch (e: Exception) {
                isCursorHidden = false
                searchBarState.resetIgnoreFocusLossFlag()
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
            searchBarState.resetIgnoreFocusLossFlag()
        }
    }

    LaunchedEffect(searchBarState.bringIntoView) {
        searchBarState.bringIntoView.collect { id ->
            bringIntoViewRequesters[id]?.bringIntoView()
        }
    }

    // Read focused text value outside remember to trigger recomputation on text changes
    val focusedTextValue = focusedElementId?.let { textStates[it] }
    val focusedText = focusedTextValue?.text ?: ""
    val focusedCursorPosition = focusedTextValue?.selection?.start ?: 0

    // Compute tag suggestions locally
    val tagSuggestions =
        remember(focusedElementId, focusedText, focusedCursorPosition, tags) {
            val id = focusedElementId
            if (id == null) {
                emptyList()
            } else {
                val cursorPosition = focusedCursorPosition
                if (cursorPosition == 0) {
                    emptyList()
                } else {
                    val textUpToCursor = focusedText.substring(0, cursorPosition)
                    val lastHashIndex = textUpToCursor.lastIndexOf('#')

                    if (lastHashIndex == -1 || " " in textUpToCursor.substring(lastHashIndex)) {
                        emptyList()
                    } else {
                        val tagQuery = textUpToCursor.substring(lastHashIndex + 1)
                        tags.filter { it.tagName.contains(tagQuery, ignoreCase = true) }
                    }
                }
            }
        }

    // Read first element text value outside remember
    val firstElementText =
        contentItems.firstOrNull()?.let { firstElement ->
            if (firstElement is SearchContentElement.Text) {
                textStates[firstElement.id]?.text ?: ""
            } else {
                null
            }
        }

    // Compute shouldShowSearchHistoryDropdown locally
    val shouldShowSearchHistoryDropdown =
        remember(focusedElementId, contentItems.size, searchHistory, firstElementText) {
            val isFocused = focusedElementId != null
            val isOnlyOneElement = contentItems.size == 1
            val firstElement = contentItems.firstOrNull()

            if (isFocused && isOnlyOneElement && firstElement is SearchContentElement.Text) {
                if (focusedElementId == firstElement.id) {
                    val currentText = firstElementText ?: ""
                    val hasHistory = searchHistory.isNotEmpty()
                    (currentText.isEmpty() || currentText == "\u200B") && hasHistory
                } else {
                    false
                }
            } else {
                false
            }
        }

    var hasPerformedInitialSearch by rememberSaveable { mutableStateOf(false) }

    val placeholderText =
        if (initialQuery.isNotEmpty() && !hasPerformedInitialSearch) {
            initialQuery // While loading (before parsing), use initialQuery as placeholder
        } else {
            stringResource(R.string.search_placeholder_with_tag) // Default placeholder
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
            hasPerformedInitialSearch = true
        }
    }

    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(semanticSearchState) {
        when (val state = semanticSearchState) {
            is SearchViewModel.SemanticSearchState.Error -> {
                errorMessage =
                    when (state.error) {
                        SearchViewModel.SearchError.NetworkError -> context.getString(R.string.error_message_network)
                        SearchViewModel.SearchError.Unauthorized -> context.getString(R.string.error_message_authentication_required)
                        SearchViewModel.SearchError.EmptyQuery -> context.getString(R.string.error_message_empty_query)
                        SearchViewModel.SearchError.UnknownError -> context.getString(R.string.error_message_search)
                    }
                isErrorBannerVisible = true
            }
            is SearchViewModel.SemanticSearchState.Success,
            is SearchViewModel.SemanticSearchState.Loading,
            is SearchViewModel.SemanticSearchState.Idle,
            -> {
                isErrorBannerVisible = false
            }
            else -> {
                // Empty state - keep current banner state
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
                                context.getString(R.string.share_photos_count, photos.size),
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
                    intent = if (isEnabled) IconIntent.Primary else IconIntent.Disabled,
                    contentDescription = stringResource(R.string.cd_share),
                )
            }
        }
    }

    val performSearch = {
        focusManager.clearFocus()
        val query = searchBarState.buildSearchQuery()
        if (query.isNotEmpty()) {
            searchViewModel.search(query)
        }
    }

    SearchResultScreenUi(
        listState = listState,
        gridState = gridState,
        contentItems = contentItems,
        textStates = textStates,
        focusRequesters = focusRequesters,
        bringIntoViewRequesters = bringIntoViewRequesters,
        focusedElementId = focusedElementId,
        isCursorHidden = isCursorHidden,
        tagSuggestions = tagSuggestions,
        shouldShowSearchHistoryDropdown = shouldShowSearchHistoryDropdown,
        allTags = allTags,
        parser = searchBarState::parseQueryToElements,
        onPerformSearch = performSearch,
        onChipSearchBarContainerClick = searchBarState::onContainerClick,
        onChipClick = searchBarState::onChipClick,
        onFocus = searchBarState::onFocus,
        onTextChange = searchBarState::onTextChange,
        onAddTagFromSuggestion = searchBarState::addTagFromSuggestion,
        searchBarRowHeight = searchBarRowHeight,
        onSearchBarRowHeightChange = { searchBarRowHeight = it },
        topSpacerHeight = topSpacerHeight,
        searchBarWidth = searchBarWidth,
        onSearchBarWidthChange = { searchBarWidth = it },
        semanticSearchState = semanticSearchState,
        isSelectionMode = isSelectionMode,
        selectedPhotos = selectedPhotos,
        selectedPhotoIds = selectedPhotoIds,
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
            val currentState = semanticSearchState as? SearchViewModel.SemanticSearchState.Success
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
            if (semanticSearchState is SearchViewModel.SemanticSearchState.Success) {
                topBarActions
            } else {
                {}
            },
        searchHistory = searchHistory,
        onHistoryClick = { clickedQuery ->
            searchBarState.selectHistoryItem(clickedQuery, allTags)
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
        onClearSearchContent = { searchBarState.clearSearchContent(keepFocus = true) },
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        onLoadMore = { searchViewModel.loadMore() },
    )
}

/**
 * UI-only search results screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreenUi(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    gridState: LazyGridState,
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
    semanticSearchState: SearchViewModel.SemanticSearchState,
    isSelectionMode: Boolean,
    selectedPhotos: Map<String, Photo>,
    selectedPhotoIds: Set<String>,
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
    onClearSearchContent: () -> Unit = {},
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
) {
    LocalFocusManager.current
    val backgroundBrush = rememberAppBackgroundBrush()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CommonTopBar(
                title = stringResource(R.string.search_result_title),
                showBackButton = true,
                onBackClick = onBackClick,
                navigationIcon =
                    if (isSelectionMode) {
                        {
                            IconButton(
                                onClick = {
                                    onToggleSelectionMode()
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
                        null
                    },
                actions = topBarActions,
            )
        },
    ) { paddingValues ->
        SearchResultContent(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(paddingValues),
//                    .padding(horizontal = 16.dp),
            listState = listState,
            gridState = gridState,
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
            semanticSearchState = semanticSearchState,
            isSelectionMode = isSelectionMode,
            selectedPhotos = selectedPhotos,
            selectedPhotoIds = selectedPhotoIds,
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
            onClearSearchContent = onClearSearchContent,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            onLoadMore = onLoadMore,
        )
    }
}

/**
 * Search result content (pure UI)
 */
@Composable
private fun SearchResultContent(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    gridState: LazyGridState,
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
    semanticSearchState: SearchViewModel.SemanticSearchState,
    isSelectionMode: Boolean,
    selectedPhotos: Map<String, Photo>,
    selectedPhotoIds: Set<String>,
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
    onClearSearchContent: () -> Unit = {},
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
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
                    .padding(horizontal = Dimen.ScreenHorizontalPadding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focusManager.clearFocus()
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

            // Search Input
            val hasSearchContent =
                remember(contentItems.toList(), textStates.toMap()) {
                    contentItems.any { it is SearchContentElement.Chip } ||
                        textStates.values.any { it.text.replace("\u200B", "").isNotEmpty() }
                }

            ChipSearchBar(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { onSearchBarRowHeightChange(it.size.height) }
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
                onClear = onClearSearchContent,
                hasContent = hasSearchContent,
            )

            // Tag suggestion list (LazyRow)
            if (tagSuggestions.isNotEmpty()) {
                LazyRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Dimen.ItemSpacingSmall)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { },
                            ),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                ) {
                    items(tagSuggestions, key = { it.tagId }) { tag ->
                        SuggestionChip(
                            tag = tag,
                            onClick = { onAddTagFromSuggestion(tag) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

            // Results
            SearchResultsFromState(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                semanticSearchState = semanticSearchState,
                isSelectionMode = isSelectionMode,
                selectedPhotos = selectedPhotos,
                selectedPhotoIds = selectedPhotoIds,
                onToggleImageSelection = onToggleImageSelection,
                onImageLongPress = onImageLongPress,
                onRetry = onRetry,
                navController = navController,
                gridState = gridState,
                isLoadingMore = isLoadingMore,
                hasMore = hasMore,
                onLoadMore = onLoadMore,
            )

            AnimatedVisibility(
                visible = isErrorBannerVisible && errorMessage != null,
                enter = Animation.EnterFromBottom,
                exit = Animation.ExitToBottom,
            ) {
                WarningBanner(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimen.ItemSpacingSmall),
                    title = stringResource(R.string.search_failed_title),
                    message = errorMessage ?: stringResource(R.string.error_message_unknown),
                    onActionClick = onRetry,
                    onDismiss = onDismissError,
                    showActionButton = true,
                    showDismissButton = true,
                )
            }

            Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))
        }

        // Scrollbar positioned outside Column to span padding boundary
        if (semanticSearchState is SearchViewModel.SemanticSearchState.Success) {
            VerticalScrollbar(
                state = gridState,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(
                            top =
                                Dimen.ItemSpacingSmall +
                                    with(
                                        LocalDensity.current,
                                    ) { searchBarRowHeight.toDp() } + Dimen.ItemSpacingLarge,
                            end = Dimen.ScreenHorizontalPadding / 2,
                        ),
            )
        }

        // search history dropdown
        AnimatedVisibility(
            visible = shouldShowSearchHistoryDropdown,
            enter =
                expandVertically(expandFrom = Alignment.Top, animationSpec = Animation.mediumTween()) +
                    fadeIn(animationSpec = Animation.mediumTween()),
            exit =
                shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = Animation.mediumTween()) +
                    fadeOut(animationSpec = Animation.mediumTween()),
            modifier =
                Modifier
                    .offset(y = with(LocalDensity.current) { searchBarRowHeight.toDp() + 16.dp })
                    .padding(horizontal = Dimen.ScreenHorizontalPadding)
                    .zIndex(1f),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Dimen.TagCornerRadius),
                shadowElevation = Dimen.BottomNavShadowElevation,
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
        if (semanticSearchState is SearchViewModel.SemanticSearchState.Success) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Dimen.ItemSpacingLarge)
                        .padding(horizontal = Dimen.ScreenHorizontalPadding),
            ) {
                // Only show CreateTagButton when in selection mode and photos are selected
                AnimatedVisibility(
                    visible = isSelectionMode && selectedPhotos.isNotEmpty(),
                    enter = Animation.EnterFromBottom,
                    exit = Animation.ExitToBottom,
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    CreateTagButton(
                        modifier =
                            Modifier
                                .padding(start = Dimen.ScreenHorizontalPadding),
                        text = stringResource(R.string.add_tag_with_count, selectedPhotos.size),
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
 * Display appropriate search results based on UI state
 */
@Composable
private fun SearchResultsFromState(
    modifier: Modifier = Modifier,
    semanticSearchState: SearchViewModel.SemanticSearchState,
    isSelectionMode: Boolean,
    selectedPhotos: Map<String, Photo>,
    selectedPhotoIds: Set<String>,
    onToggleImageSelection: (Photo) -> Unit,
    onImageLongPress: () -> Unit,
    onRetry: () -> Unit,
    navController: NavController,
    gridState: LazyGridState,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
) {
    Box(modifier = modifier) {
        when (semanticSearchState) {
            is SearchViewModel.SemanticSearchState.Idle -> {
                Box(modifier = Modifier.fillMaxSize()) // ?
            }

            is SearchViewModel.SemanticSearchState.Loading -> {
                SearchLoadingStateCustom(
                    modifier = modifier,
                    onRefresh = onRetry,
                    text = stringResource(R.string.loading_results),
                )
            }

            is SearchViewModel.SemanticSearchState.Empty -> {
                SearchEmptyStateCustom(
                    query = semanticSearchState.query,
                    modifier = modifier,
                )
            }

            is SearchViewModel.SemanticSearchState.Success -> {
                // Infinite scroll detection
                LaunchedEffect(gridState) {
                    snapshotFlow {
                        val layoutInfo = gridState.layoutInfo
                        val totalItemsCount = layoutInfo.totalItemsCount
                        val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

                        // Trigger when 6 items (2 rows) from the end
                        lastVisibleItemIndex >= totalItemsCount - 6
                    }.collect { shouldLoadMore ->
                        if (shouldLoadMore && hasMore && !isLoadingMore) {
                            onLoadMore()
                        }
                    }
                }

                val updatedSelectedPhotos = rememberUpdatedState(selectedPhotos)
                val updatedSelectedPhotoIds = rememberUpdatedState(selectedPhotoIds)
                val updatedIsSelectionMode = rememberUpdatedState(isSelectionMode)
                val updatedOnImageLongPress = rememberUpdatedState(onImageLongPress)
                val updatedOnToggleImageSelection = rememberUpdatedState(onToggleImageSelection)
                val allPhotos = (semanticSearchState as SearchViewModel.SemanticSearchState.Success).photos

                val gridGestureModifier =
                    Modifier.pointerInput(allPhotos) {
                        coroutineScope {
                            val pointerScope = this
                            val autoScrollViewport = 80.dp.toPx()
                            var autoScrollJob: Job? = null
                            var dragAnchorIndex: Int? = null
                            var isDeselectDrag by mutableStateOf(false)
                            val initialSelection = mutableSetOf<String>()

                            fun findNearestItemByRow(position: Offset): Int? {
                                var best: Pair<Int, Float>? = null
                                gridState.layoutInfo.visibleItemsInfo.forEach { itemInfo ->
                                    val key = itemInfo.key as? String ?: return@forEach
                                    val photoIndex = allPhotos.indexOfFirst { it.photoId == key }
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
                                            if (best == null || dist < best!!.second) {
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
                                        updatedOnImageLongPress.value()
                                    }
                                    gridState.findPhotoItemAtPosition(offset, allPhotos)?.let { (photoId, photo) ->
                                        initialSelection.clear()
                                        initialSelection.addAll(updatedSelectedPhotos.value.keys)
                                        isDeselectDrag = initialSelection.contains(photoId)
                                        dragAnchorIndex = allPhotos.indexOfFirst { it.photoId == photoId }.takeIf { it >= 0 }
                                        updatedOnToggleImageSelection.value(photo)
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
                                    val currentItem = gridState.findPhotoItemAtPosition(change.position, allPhotos)
                                    val currentIndex =
                                        currentItem
                                            ?.first
                                            ?.let { id ->
                                                allPhotos.indexOfFirst { it.photoId == id }
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

                                        val photoIdsInRange =
                                            range
                                                .mapNotNull { idx ->
                                                    allPhotos.getOrNull(idx)?.photoId
                                                }.toSet()

                                        val currentSelection = updatedSelectedPhotos.value.keys
                                        val targetSelection =
                                            if (isDeselectDrag) {
                                                initialSelection - photoIdsInRange
                                            } else {
                                                initialSelection + photoIdsInRange
                                            }

                                        val diff = currentSelection.symmetricDifference(targetSelection)
                                        diff.forEach { photoId ->
                                            allPhotos.find { it.photoId == photoId }?.let { photoToToggle ->
                                                updatedOnToggleImageSelection.value(photoToToggle)
                                            }
                                        }
                                    }

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
                    modifier = modifier.fillMaxSize().then(gridGestureModifier),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                ) {
                    items(
                        count = semanticSearchState.photos.size,
                        key = { index -> semanticSearchState.photos[index].photoId }, // Use stable photoId as key
                    ) { index ->
                        val photo = semanticSearchState.photos[index]
                        ImageGridUriItem(
                            photo = photo,
                            navController = navController,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedPhotoIds.contains(photo.photoId),
                            onToggleSelection = { onToggleImageSelection(photo) },
                            onLongPress = {
                                onImageLongPress()
                                onToggleImageSelection(photo)
                            },
                        )
                    }

                    // Loading indicator at the bottom
                    if (isLoadingMore && hasMore) {
                        item(
                            key = "loading_indicator",
                            span = { GridItemSpan(3) },
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Dimen.ItemSpacingLarge),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Dimen.CircularProgressSizeMedium),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            is SearchViewModel.SemanticSearchState.Error -> {
                Box(modifier = Modifier.fillMaxHeight())
            }
        }
    }
}

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

private fun <T> Set<T>.symmetricDifference(other: Set<T>): Set<T> = (this - other) + (other - this)
