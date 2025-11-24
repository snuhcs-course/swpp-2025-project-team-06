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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.model.SearchResultItem
import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.ChipSearchBar
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.CreateTagButton
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
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

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
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val isSelectionMode by searchViewModel.isSelectionMode.collectAsState()
    val tagLoadingState by searchViewModel.tagLoadingState.collectAsState()
    val focusedElementId by searchViewModel.focusedElementId
    val tagSuggestions by searchViewModel.tagSuggestions.collectAsState()
    val shouldShowSearchHistoryDropdown by searchViewModel.shouldShowSearchHistoryDropdown.collectAsState()
    val ignoreFocusLoss by searchViewModel.ignoreFocusLoss
    val scrollToIndex by searchViewModel.scrollToIndex.collectAsState()
    val isLoadingMore by searchViewModel.isLoadingMore.collectAsState()
    val hasMore by searchViewModel.hasMore.collectAsState()

    // 4. 로컬 상태 변수
    var searchBarWidth by remember { mutableStateOf(0) }
    var searchBarRowHeight by remember { mutableStateOf(0) }
    var isCursorHidden by remember { mutableStateOf(false) }
    var isSelectionModeDelay by remember { mutableStateOf(false) }
    var previousImeBottom by remember { mutableStateOf(imeBottom) }

    // 5. Derived 상태 및 계산된 값
    val allTags = (tagLoadingState as? SearchViewModel.TagLoadingState.Success)?.tags ?: emptyList<TagItem>()
    val topSpacerHeight = Dimen.ItemSpacingSmall
    val contentItems = searchViewModel.contentItems
    val textStates = searchViewModel.textStates
    val focusRequesters = searchViewModel.focusRequesters
    val bringIntoViewRequesters = searchViewModel.bringIntoViewRequesters
    val currentFocusedElementId = rememberUpdatedState(focusedElementId)
    val currentFocusManager = rememberUpdatedState(focusManager)

    // 6. Remember된 객체들
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(tagLoadingState) {
        if (tagLoadingState is SearchViewModel.TagLoadingState.Error) {
            val errorState = tagLoadingState as SearchViewModel.TagLoadingState.Error
            val errorMessage =
                when (errorState.error) {
                    SearchViewModel.SearchError.NetworkError -> context.getString(R.string.error_message_network)
                    SearchViewModel.SearchError.Unauthorized -> context.getString(R.string.error_message_authentication_required)
                    SearchViewModel.SearchError.EmptyQuery -> context.getString(R.string.error_message_empty_query)
                    SearchViewModel.SearchError.UnknownError -> context.getString(R.string.error_message_search)
                }

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
                    // Restore scroll position when returning from ImageDetailScreen
                    searchViewModel.restoreScrollPosition()
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Scroll restoration: restore scroll position when returning from ImageDetailScreen
    LaunchedEffect(scrollToIndex) {
        scrollToIndex?.let { index ->
            gridState.animateScrollToItem(index)
            searchViewModel.clearScrollToIndex()
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
        }
    }

    LaunchedEffect(initialQuery, hasPerformedInitialSearch, tagLoadingState) {
        if (initialQuery.isNotEmpty() && !hasPerformedInitialSearch && tagLoadingState is SearchViewModel.TagLoadingState.Success) {
            searchViewModel.selectHistoryItem(initialQuery)
            hasPerformedInitialSearch = true
        }
    }

    // SemanticSearchState를 SearchUiState로 변환
    val uiState =
        remember(semanticSearchState) {
            when (semanticSearchState) {
                is SearchViewModel.SemanticSearchState.Idle -> SearchViewModel.SearchUiState.Idle
                is SearchViewModel.SemanticSearchState.Loading -> SearchViewModel.SearchUiState.Loading
                is SearchViewModel.SemanticSearchState.Success -> {
                    val photos = (semanticSearchState as SearchViewModel.SemanticSearchState.Success).photos
                    val searchResults =
                        photos.map { photo ->
                            SearchResultItem(
                                query = (semanticSearchState as SearchViewModel.SemanticSearchState.Success).query,
                                photo = photo,
                            )
                        }
                    SearchViewModel.SearchUiState.Success(
                        searchResults,
                        (semanticSearchState as SearchViewModel.SemanticSearchState.Success).query,
                    )
                }
                is SearchViewModel.SemanticSearchState.Empty -> {
                    SearchViewModel.SearchUiState.Empty((semanticSearchState as SearchViewModel.SemanticSearchState.Empty).query)
                }
                is SearchViewModel.SemanticSearchState.Error -> {
                    val errorState = semanticSearchState as SearchViewModel.SemanticSearchState.Error
                    SearchViewModel.SearchUiState.Error(errorState.error)
                }
            }
        }

    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        if (uiState is SearchViewModel.SearchUiState.Error) {
            errorMessage =
                when (uiState.error) {
                    SearchViewModel.SearchError.NetworkError -> context.getString(R.string.error_message_network)
                    SearchViewModel.SearchError.Unauthorized -> context.getString(R.string.error_message_authentication_required)
                    SearchViewModel.SearchError.EmptyQuery -> context.getString(R.string.error_message_empty_query)
                    SearchViewModel.SearchError.UnknownError -> context.getString(R.string.error_message_search)
                }
            isErrorBannerVisible = true
        } else {
            // 로딩이 성공하거나, Idle 상태가 되면 배너를 숨깁니다.
            if (uiState is SearchViewModel.SearchUiState.Success ||
                uiState is SearchViewModel.SearchUiState.Loading ||
                uiState is SearchViewModel.SearchUiState.Idle
            ) {
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
        searchViewModel.performSearch { route ->
            // Already on search result screen
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
            val currentState = uiState as? SearchViewModel.SearchUiState.Success
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
            if (uiState is SearchViewModel.SearchUiState.Success) {
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
        onClearSearchContent = { searchViewModel.clearSearchContent(keepFocus = true) },
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
    uiState: SearchViewModel.SearchUiState,
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
    uiState: SearchViewModel.SearchUiState,
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
                uiState = uiState,
                isSelectionMode = isSelectionMode,
                selectedPhotos = selectedPhotos,
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
        if (uiState is SearchViewModel.SearchUiState.Success) {
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
                    .offset(y = topSpacerHeight + with(LocalDensity.current) { searchBarRowHeight.toDp() } + Dimen.GridItemSpacing)
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
        if (uiState is SearchViewModel.SearchUiState.Success) {
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
    uiState: SearchViewModel.SearchUiState,
    isSelectionMode: Boolean,
    selectedPhotos: List<Photo>,
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
        when (uiState) {
            is SearchViewModel.SearchUiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize()) // ?
            }

            is SearchViewModel.SearchUiState.Loading -> {
                SearchLoadingStateCustom(
                    modifier = modifier,
                    onRefresh = onRetry,
                    text = stringResource(R.string.loading_results),
                )
            }

            is SearchViewModel.SearchUiState.Empty -> {
                SearchEmptyStateCustom(
                    query = uiState.query,
                    modifier = modifier,
                )
            }

            is SearchViewModel.SearchUiState.Success -> {
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

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
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

            is SearchViewModel.SearchUiState.Error -> {
                Box(modifier = Modifier.fillMaxHeight())
            }
        }
    }
}
