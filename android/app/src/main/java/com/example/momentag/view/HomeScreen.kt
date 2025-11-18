package com.example.momentag.view

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.LogoutState
import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.ChipSearchBar
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.SearchHistoryItem
import com.example.momentag.ui.components.SuggestionChip
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.components.confirmDialog
import com.example.momentag.ui.components.tagX
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.util.ShareUtils
import com.example.momentag.viewmodel.AuthViewModel
import com.example.momentag.viewmodel.DatedPhotoGroup
import com.example.momentag.viewmodel.HomeViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import com.example.momentag.viewmodel.SearchViewModel
import com.example.momentag.viewmodel.TagSortOrder
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun HomeScreen(navController: NavController) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    remember { context.getSharedPreferences("MomenTagPrefs", Context.MODE_PRIVATE) }

    // 2. ViewModel 인스턴스
    val authViewModel: AuthViewModel = hiltViewModel()
    val photoViewModel: PhotoViewModel = hiltViewModel()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val searchViewModel: SearchViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val logoutState by authViewModel.logoutState.collectAsState()
    val homeLoadingState by homeViewModel.homeLoadingState.collectAsState()
    val homeDeleteState by homeViewModel.homeDeleteState.collectAsState()
    val uiState by photoViewModel.uiState.collectAsState()
    val selectedPhotos by homeViewModel.selectedPhotos.collectAsState()
    val isLoadingPhotos by homeViewModel.isLoadingPhotos.collectAsState()
    val isLoadingMorePhotos by homeViewModel.isLoadingMorePhotos.collectAsState()
    val isShowingAllPhotos by homeViewModel.isShowingAllPhotos.collectAsState()
    val isSelectionMode by homeViewModel.isSelectionMode.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val shouldReturnToAllPhotos by homeViewModel.shouldReturnToAllPhotos.collectAsState()
    val groupedPhotos by homeViewModel.groupedPhotos.collectAsState()
    val allPhotos by homeViewModel.allPhotos.collectAsState()
    val allPhotosInitialIndex by homeViewModel.allPhotosScrollIndex.collectAsState()
    val allPhotosInitialOffset by homeViewModel.allPhotosScrollOffset.collectAsState()
    val tagAlbumInitialIndex by homeViewModel.tagAlbumScrollIndex.collectAsState()
    val tagAlbumInitialOffset by homeViewModel.tagAlbumScrollOffset.collectAsState()
    val shouldShowSearchHistoryDropdown by searchViewModel.shouldShowSearchHistoryDropdown.collectAsState()
    val focusedElementId by searchViewModel.focusedElementId
    val ignoreFocusLoss by searchViewModel.ignoreFocusLoss

    // 4. 로컬 상태 변수 (remember, mutableStateOf)
    var hasPermission by remember { mutableStateOf(false) }
    var isOnlyTag by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isSelectionModeDelay by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(BottomTab.HomeScreen) }
    var isDeleteConfirmationDialogVisible by remember { mutableStateOf(false) }
    var tagToDeleteInfo by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorBannerTitle by remember { mutableStateOf("Error") }
    var errorBannerMessage by remember { mutableStateOf<String?>(null) }
    var searchBarWidth by remember { mutableStateOf(0) }
    var searchBarRowHeight by remember { mutableStateOf(0) }
    var backPressedTime by remember { mutableStateOf(0L) }
    var isCursorHidden by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    var previousImeBottom by remember { mutableStateOf(imeBottom) }

    // 5. Derived 상태 및 계산된 값
    val allTags = (homeLoadingState as? HomeViewModel.HomeLoadingState.Success)?.tags ?: emptyList()
    val topSpacerHeight = 8.dp
    val textStates = searchViewModel.textStates
    val contentItems = searchViewModel.contentItems
    val focusRequesters = searchViewModel.focusRequesters
    val bringIntoViewRequesters = searchViewModel.bringIntoViewRequesters
    val currentFocusedElementId = rememberUpdatedState(focusedElementId)
    val currentFocusManager = rememberUpdatedState(focusManager)

    // 6. rememberCoroutineScope
    val scope = rememberCoroutineScope()

    // 7. Remember된 객체들
    val listState = rememberLazyListState()
    val allPhotosGridState =
        rememberLazyGridState(
            initialFirstVisibleItemIndex = allPhotosInitialIndex,
            initialFirstVisibleItemScrollOffset = allPhotosInitialOffset,
        )
    val tagAlbumGridState =
        rememberLazyGridState(
            initialFirstVisibleItemIndex = tagAlbumInitialIndex,
            initialFirstVisibleItemScrollOffset = tagAlbumInitialOffset,
        )
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // SearchResultScreen에서 돌아올 때 등
                    // HomeScreen이 다시 보일 때 검색창 내용을 지웁니다.
                    searchViewModel.clearSearchContent()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(allPhotosGridState) {
        snapshotFlow {
            Pair(
                allPhotosGridState.firstVisibleItemIndex,
                allPhotosGridState.firstVisibleItemScrollOffset,
            )
        }.distinctUntilChanged()
            .collect { (index, offset) ->
                homeViewModel.setAllPhotosScrollPosition(index, offset)
            }
    }

    LaunchedEffect(tagAlbumGridState) {
        snapshotFlow {
            Pair(
                tagAlbumGridState.firstVisibleItemIndex,
                tagAlbumGridState.firstVisibleItemScrollOffset,
            )
        }.distinctUntilChanged()
            .collect { (index, offset) ->
                homeViewModel.setTagAlbumScrollPosition(index, offset)
            }
    }

    LaunchedEffect(searchViewModel.requestFocus) {
        searchViewModel.requestFocus.collect { id ->
            Log.d("home cursor", "true1")
            isCursorHidden = true

            try {
                snapshotFlow { focusRequesters.containsKey(id) }
                    .filter { it == true }
                    .first()
            } catch (e: Exception) {
                Log.d("home cursor", "false1")
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

            Log.d("home cursor", "false2")
            isCursorHidden = false
            searchViewModel.resetIgnoreFocusLossFlag()
        }
    }

    LaunchedEffect(searchViewModel.bringIntoView) {
        searchViewModel.bringIntoView.collect { id ->
            bringIntoViewRequesters[id]?.bringIntoView()
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

    val tagSuggestions by searchViewModel.tagSuggestions.collectAsState()

    val performSearch = {
        focusManager.clearFocus()

        searchViewModel.performSearch { route ->
            navController.navigate(route)
        }
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
                    homeViewModel.setSelectionMode(false)
                    navBackStackEntry.savedStateHandle.remove<Boolean>("selectionModeComplete")
                }
            }
    }

    var isUploadBannerDismissed by remember { mutableStateOf(false) }

    val currentSortOrder by homeViewModel.sortOrder.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val gradientBrush =
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0.5f to MaterialTheme.colorScheme.surface,
                    1.0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                ),
        )

    val tagItems = (homeLoadingState as? HomeViewModel.HomeLoadingState.Success)?.tags ?: emptyList()
    val isTagsLoaded =
        homeLoadingState is HomeViewModel.HomeLoadingState.Success || homeLoadingState is HomeViewModel.HomeLoadingState.Error
    val arePhotosLoaded = !isLoadingPhotos
    val isDataReady = isTagsLoaded && arePhotosLoaded
    val areTagsEmpty = tagItems.isEmpty()
    val arePhotosEmpty = groupedPhotos.isEmpty()

    val showEmptyTagGradient = !isShowingAllPhotos && areTagsEmpty && isDataReady && !arePhotosEmpty

    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            isUploadBannerDismissed = false
        }
    }

    val bannerVisible = uiState.isLoading && !isUploadBannerDismissed

    LaunchedEffect(bannerVisible) {
        if (bannerVisible) {
            delay(5000)
            isUploadBannerDismissed = true
        }
    }

    LaunchedEffect(Unit) {
        if (shouldReturnToAllPhotos) {
            homeViewModel.setIsShowingAllPhotos(true)
            isOnlyTag = false
            homeViewModel.setShouldReturnToAllPhotos(false) // flag reset
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted -> if (isGranted) hasPermission = true },
        )

    // 권한 요청 및 이미지 로드
    LaunchedEffect(Unit) {
        val permission = requiredImagePermission()
        permissionLauncher.launch(permission)

        // Pre-generate stories once on app launch
        homeViewModel.preGenerateStoriesOnce()
    }

    // 성공 시 로그인 화면으로 이동(백스택 초기화)
    LaunchedEffect(logoutState) {
        when (logoutState) {
            is LogoutState.Success -> {
                Toast.makeText(context, "로그아웃되었습니다", Toast.LENGTH_SHORT).show()
                navController.navigate(Screen.Login.route) {
                    popUpTo(0)
                    launchSingleTop = true
                }
            }
            is LogoutState.Error -> {
                errorBannerTitle = "Logout Failed"
                errorBannerMessage = (logoutState as LogoutState.Error).message ?: "Logout failed"
                isErrorBannerVisible = true
            }
            else -> Unit
        }
    }

    // done once when got permission
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            homeViewModel.loadServerTags()
            searchViewModel.loadServerTags()
            homeViewModel.loadAllPhotos() // 서버에서 모든 사진 가져오기
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            photoViewModel.infoMessageShown()
        }
    }

    LaunchedEffect(homeLoadingState) {
        when (homeLoadingState) {
            is HomeViewModel.HomeLoadingState.Error -> {
                errorBannerTitle = "Failed to Load Tags"
                errorBannerMessage = (homeLoadingState as HomeViewModel.HomeLoadingState.Error).message
                isErrorBannerVisible = true
            }
            is HomeViewModel.HomeLoadingState.Success -> {
                isErrorBannerVisible = false // 로드 성공 시 배너 숨김
            }
            else -> Unit // Loading, Idle
        }
    }

    LaunchedEffect(homeDeleteState) {
        when (val state = homeDeleteState) {
            is HomeViewModel.HomeDeleteState.Success -> {
                Toast.makeText(context, "Tag Deleted", Toast.LENGTH_SHORT).show()
                homeViewModel.loadServerTags()
                searchViewModel.loadServerTags()
                isDeleteMode = false
                homeViewModel.resetDeleteState()
                isErrorBannerVisible = false
            }
            is HomeViewModel.HomeDeleteState.Error -> {
                errorBannerTitle = "Failed to Delete Tag"
                errorBannerMessage = state.message
                isErrorBannerVisible = true
                isDeleteMode = false
                homeViewModel.resetDeleteState()
            }
            is HomeViewModel.HomeDeleteState.Loading -> {
            }
            is HomeViewModel.HomeDeleteState.Idle -> {
            }
        }
    }

    LaunchedEffect(navController, hasPermission) {
        val navBackStackEntry = navController.currentBackStackEntry
        navBackStackEntry?.savedStateHandle?.getLiveData<Boolean>("shouldRefresh")?.observe(navBackStackEntry) { shouldRefresh ->
            if (shouldRefresh) {
                if (hasPermission) {
                    homeViewModel.loadServerTags()
                    searchViewModel.loadServerTags()
                    homeViewModel.loadAllPhotos()
                }
                navBackStackEntry.savedStateHandle.remove<Boolean>("shouldRefresh")
            }
        }
    }

    BackHandler(enabled = isSelectionMode && isShowingAllPhotos) {
        homeViewModel.setSelectionMode(false)
        homeViewModel.resetSelection()
    }

    BackHandler(enabled = isDeleteConfirmationDialogVisible) {
        isDeleteConfirmationDialogVisible = false
        tagToDeleteInfo = null
    }

    BackHandler(enabled = !isSelectionMode && isShowingAllPhotos) {
        homeViewModel.setIsShowingAllPhotos(false)
    }

    BackHandler(enabled = !isSelectionMode && !isShowingAllPhotos && !isDeleteConfirmationDialogVisible && !isDeleteMode) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < 2000) {
            activity?.finish()
        } else {
            backPressedTime = currentTime
            Toast.makeText(context, "Press again to exit.", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = isDeleteMode && !isShowingAllPhotos) {
        isDeleteMode = false
    }

    Scaffold(
        topBar = {
            CommonTopBar(
                title = "MomenTag",
                onTitleClick = {
                    navController.navigate(Screen.LocalGallery.route)
                },
                showLogout = true,
                onLogoutClick = { authViewModel.logout() },
                isLogoutLoading = logoutState is LogoutState.Loading,
                actions = {
                    if (isShowingAllPhotos && groupedPhotos.isNotEmpty() && isSelectionMode) {
                        val isEnabled = selectedPhotos.isNotEmpty()
                        IconButton(
                            onClick = {
                                val photos = homeViewModel.getPhotosToShare()
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
                        BottomTab.HomeScreen -> {
                        }
                        BottomTab.MyTagsScreen -> {
                            homeViewModel.resetSelection()
                            navController.navigate(Screen.MyTags.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                        BottomTab.StoryScreen -> {
                            homeViewModel.resetSelection()
                            navController.navigate(Screen.Story.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            // 태그 앨범 뷰(!isShowingAllPhotos)에서는 Create Tag 버튼을 표시하지 않음
            // Only show when in selection mode and photos are selected
            if (isShowingAllPhotos && groupedPhotos.isNotEmpty() && isSelectionMode && selectedPhotos.isNotEmpty()) {
                CreateTagButton(
                    modifier = Modifier.padding(start = 32.dp, bottom = 16.dp),
                    text = "Add Tag (${selectedPhotos.size})",
                    onClick = {
                        // selectedPhotos는 이미 draftTagRepository에 저장되어 있음!
                        // SearchResultScreen과 동일한 패턴
                        // isSelectionMode = false
                        navController.navigate(Screen.MyTags.route)
                    },
                )
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing =
                (homeLoadingState is HomeViewModel.HomeLoadingState.Loading && groupedPhotos.isEmpty()) ||
                    (isLoadingPhotos && groupedPhotos.isEmpty()),
            onRefresh = {
                if (hasPermission) {
                    isDeleteMode = false
                    homeViewModel.loadServerTags()
                    searchViewModel.loadServerTags()
                    homeViewModel.loadAllPhotos() // 서버 사진도 새로고침
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            focusManager.clearFocus()
                            isDeleteMode = false
                        },
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (showEmptyTagGradient) {
                                    Modifier.background(gradientBrush)
                                } else {
                                    Modifier
                                },
                            ).padding(horizontal = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                focusManager.clearFocus()
                                isDeleteMode = false
                            },
                ) {
                    Spacer(modifier = Modifier.height(topSpacerHeight))

                    // Search Bar with Filter Button
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { layoutCoordinates ->
                                    searchBarRowHeight = layoutCoordinates.size.height
                                },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChipSearchBar(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .onSizeChanged { intSize ->
                                        searchBarWidth = intSize.width
                                    },
                            listState = listState,
                            isFocused = (focusedElementId != null),
                            isCursorHidden = isCursorHidden,
                            contentItems = contentItems,
                            textStates = textStates,
                            focusRequesters = focusRequesters,
                            bringIntoViewRequesters = bringIntoViewRequesters,
                            onSearch = { performSearch() },
                            onContainerClick = searchViewModel::onContainerClick,
                            onChipClick = searchViewModel::onChipClick,
                            onFocus = searchViewModel::onFocus,
                            onTextChange = searchViewModel::onTextChange,
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
                                    onClick = { searchViewModel.addTagFromSuggestion(tag) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // "태그 앨범" 뷰일 때만 정렬 버튼 표시
                        if (!isShowingAllPhotos) {
                            IconButton(onClick = { scope.launch { sheetState.show() } }) {
                                StandardIcon.Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort Tag Albums",
                                    intent = IconIntent.Muted,
                                )
                            }
                        } else {
                            // "All Photos" 뷰일 때 공간을 차지할 빈 Spacer
                            Spacer(modifier = Modifier.size(48.dp)) // IconButton 크기만큼
                        }
                        ViewToggle(
                            isOnlyTag = isOnlyTag,
                            isShowingAllPhotos = isShowingAllPhotos,
                            onToggle = { tagOnly, allPhotos ->
                                isOnlyTag = tagOnly
                                homeViewModel.setIsShowingAllPhotos(allPhotos)
                                if (isSelectionMode) {
                                    homeViewModel.setSelectionMode(false)
                                    homeViewModel.resetSelection() // draftRepository 초기화
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (!hasPermission) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "To view tags and images,\nplease allow access to your photos.",
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        MainContent(
                            modifier = Modifier.weight(1f),
                            isOnlyTag = isOnlyTag, // Pass the actual state
                            isShowingAllPhotos = isShowingAllPhotos, // Pass the actual state
                            tagItems = allTags, // allTags 전달 (이미 로드됨)
                            groupedPhotos = groupedPhotos,
                            navController = navController,
                            onDeleteClick = { tagId ->
                                // 삭제 확인 대화상자 띄우기
                                val tagItem = allTags.find { it.tagId == tagId }
                                if (tagItem != null) {
                                    tagToDeleteInfo = Pair(tagItem.tagId, tagItem.tagName)
                                    isDeleteConfirmationDialogVisible = true
                                    isDeleteMode = false
                                }
                            },
                            isDeleteMode = isDeleteMode,
                            onEnterDeleteMode = { isDeleteMode = true },
                            onExitDeleteMode = { isDeleteMode = false },
                            isSelectionMode = isSelectionMode,
                            onEnterSelectionMode = { homeViewModel.setSelectionMode(true) },
                            selectedItems = selectedPhotos.map { it.photoId }.toSet(),
                            onItemSelectionToggle = { photoId ->
                                val photo = allPhotos.find { it.photoId == photoId }
                                photo?.let { homeViewModel.togglePhoto(it) }
                            },
                            homeViewModel = homeViewModel,
                            allPhotosGridState = allPhotosGridState,
                            tagAlbumGridState = tagAlbumGridState,
                            isLoadingPhotos = false,
                            homeLoadingState = homeLoadingState,
                            isDataReady = isDataReady,
                            arePhotosEmpty = arePhotosEmpty,
                            areTagsEmpty = areTagsEmpty,
                        )

                        // 페이지네이션 로직을 MainContent 밖으로 이동
                        if (isShowingAllPhotos) {
                            LaunchedEffect(allPhotosGridState, isLoadingMorePhotos) {
                                // 로딩 중일 때는 스크롤 감지 로직 자체를 실행하지 않도록
                                if (!isLoadingMorePhotos) {
                                    snapshotFlow {
                                        allPhotosGridState.layoutInfo.visibleItemsInfo
                                            .lastOrNull()
                                            ?.index
                                    }.distinctUntilChanged()
                                        .debounce(150)
                                        .collect { lastVisibleIndex ->
                                            val totalItemCount = allPhotosGridState.layoutInfo.totalItemsCount
                                            if (lastVisibleIndex != null && totalItemCount > 0) {
                                                val remainingItems =
                                                    totalItemCount - (lastVisibleIndex + 1)
                                                // 3열 그리드 기준, 약 11줄(33개) 미만일 때 로드
                                                if (remainingItems < 33) {
                                                    homeViewModel.loadMorePhotos()
                                                }
                                            }
                                        }
                                }
                            }
                        }
                    }

                    if (isShowingAllPhotos && isLoadingMorePhotos) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    AnimatedVisibility(visible = bannerVisible) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            WarningBanner(
                                title = "Uploading...🚀",
                                message = "Photos are uploading in the background.",
                                onActionClick = { },
                                showActionButton = false,
                                backgroundColor = MaterialTheme.colorScheme.onErrorContainer,
                                icon = Icons.Default.Upload,
                                showDismissButton = true,
                                onDismiss = {
                                    isUploadBannerDismissed = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    AnimatedVisibility(visible = isErrorBannerVisible && errorBannerMessage != null) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            WarningBanner(
                                title = errorBannerTitle,
                                message = errorBannerMessage!!,
                                onActionClick = {
                                    // 재시도 로직
                                    if (hasPermission) {
                                        homeViewModel.loadServerTags()
                                        searchViewModel.loadServerTags()
                                        homeViewModel.loadAllPhotos()
                                    }
                                    isErrorBannerVisible = false
                                },
                                onDismiss = { isErrorBannerVisible = false },
                                showActionButton = true,
                                showDismissButton = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                // search history dropdown
                AnimatedVisibility(
                    visible = shouldShowSearchHistoryDropdown,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    modifier =
                        Modifier
                            .offset(y = with(LocalDensity.current) { searchBarRowHeight.toDp() + 16.dp }) // change to move y-axis
                            .padding(horizontal = 16.dp)
                            .zIndex(1f), // z-index for overlay
                ) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(end = 48.dp + 8.dp),
                        shape =
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp,
                            ),
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
                                    parser = searchViewModel::parseQueryToElements,
                                    onHistoryClick = { clickedQuery ->
                                        searchViewModel.selectHistoryItem(clickedQuery)
                                        focusManager.clearFocus()
                                        performSearch()
                                    },
                                    onHistoryDelete = {
                                        searchViewModel.removeSearchHistory(it)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isDeleteConfirmationDialogVisible && tagToDeleteInfo != null) {
        val (tagId, tagName) = tagToDeleteInfo!!

        confirmDialog(
            title = "Delete Tag",
            message = "Are you sure you want to delete '$tagName' tag?",
            confirmButtonText = "Delete Tag",
            onConfirm = {
                homeViewModel.deleteTag(tagId)
                isDeleteConfirmationDialogVisible = false
                tagToDeleteInfo = null
            },
            onDismiss = {
                isDeleteConfirmationDialogVisible = false
                tagToDeleteInfo = null
            },
            dismissible = true,
        )
    }

    if (sheetState.isVisible) {
        ModalBottomSheet(
            onDismissRequest = { scope.launch { sheetState.hide() } },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            SortOptionsSheet(
                currentOrder = currentSortOrder,
                onOrderChange = { newOrder ->
                    homeViewModel.setSortOrder(newOrder)
                    scope.launch { sheetState.hide() }
                },
            )
        }
    }
}

// -------------------- Helpers --------------------

@Composable
private fun ViewToggle(
    isOnlyTag: Boolean,
    isShowingAllPhotos: Boolean,
    onToggle: (tagOnly: Boolean, allPhotos: Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(8.dp))
                    .padding(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tag Albums (Grid)
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (!isOnlyTag && !isShowingAllPhotos) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            ).clickable { onToggle(false, false) }
                            .padding(8.dp),
                ) {
                    val isTagAlbumsSelected = !isOnlyTag && !isShowingAllPhotos
                    StandardIcon.Icon(
                        imageVector = Icons.Default.CollectionsBookmark,
                        contentDescription = "Tag Albums",
                        sizeRole = IconSizeRole.Navigation,
                        intent = if (isTagAlbumsSelected) IconIntent.Surface else IconIntent.Muted,
                    )
                }
                // All Photos (Grid)
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isShowingAllPhotos) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            ).clickable { onToggle(false, true) }
                            .padding(8.dp),
                ) {
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Photo,
                        contentDescription = "All Photos",
                        sizeRole = IconSizeRole.Navigation,
                        intent = if (isShowingAllPhotos) IconIntent.Surface else IconIntent.Muted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    isOnlyTag: Boolean,
    isShowingAllPhotos: Boolean,
    tagItems: List<TagItem>,
    groupedPhotos: List<DatedPhotoGroup> = emptyList(),
    navController: NavController,
    onDeleteClick: (String) -> Unit,
    isDeleteMode: Boolean,
    onEnterDeleteMode: () -> Unit,
    onExitDeleteMode: () -> Unit,
    isSelectionMode: Boolean,
    onEnterSelectionMode: () -> Unit,
    selectedItems: Set<String>,
    onItemSelectionToggle: (String) -> Unit,
    homeViewModel: HomeViewModel? = null,
    allPhotosGridState: LazyGridState,
    tagAlbumGridState: LazyGridState,
    isLoadingMorePhotos: Boolean = false,
    isLoadingPhotos: Boolean,
    homeLoadingState: HomeViewModel.HomeLoadingState,
    isDataReady: Boolean,
    arePhotosEmpty: Boolean,
    areTagsEmpty: Boolean,
) {
    when {
        isDataReady && arePhotosEmpty -> {
            EmptyStatePhotos(modifier = modifier, navController = navController)
        }

        // 로직 2순위: 'All Photos' 뷰 (사진이 반드시 있음)
        isShowingAllPhotos -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = allPhotosGridState,
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                groupedPhotos.forEach { group ->
                    item(
                        key = group.date,
                        span = { GridItemSpan(3) },
                    ) {
                        Text(
                            text = group.date,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier =
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .padding(top = 4.dp, bottom = 4.dp),
                        )
                    }

                    items(
                        items = group.photos,
                        key = { photo -> photo.photoId },
                    ) { photo ->
                        val isSelected = selectedItems.contains(photo.photoId)

                        Box(modifier = Modifier.aspectRatio(1f)) {
                            AsyncImage(
                                model = photo.contentUri,
                                contentDescription = "Photo ${photo.photoId}",
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(4.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    onItemSelectionToggle(photo.photoId)
                                                } else {
                                                    homeViewModel?.setGalleryBrowsingSession()
                                                    homeViewModel?.setShouldReturnToAllPhotos(true)

                                                    navController.navigate(
                                                        Screen.Image.createRoute(
                                                            uri = photo.contentUri,
                                                            imageId = photo.photoId,
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    onEnterSelectionMode()
                                                    onItemSelectionToggle(photo.photoId)
                                                }
                                            },
                                        ),
                                contentScale = ContentScale.Crop,
                            )

                            if (isSelectionMode) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(4.dp))
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
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                        .copy(
                                                            alpha = 0.8f,
                                                        )
                                                },
                                                RoundedCornerShape(12.dp),
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        StandardIcon.Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            sizeRole = IconSizeRole.InlineAction,
                                            intent = IconIntent.OnPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 로딩 인디케이터
                    if (isLoadingMorePhotos) {
                        item(span = { GridItemSpan(3) }) {
                            // 3칸 모두 차지
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 로직 3순위: 'Tag Album' 뷰 (사진이 반드시 있음)
        !isShowingAllPhotos && !arePhotosEmpty -> {
            if (areTagsEmpty && isDataReady) {
                // 시나리오 2-b: 사진은 있으나, 태그가 없음
                EmptyStateTags(navController = navController, modifier = modifier)
            } else {
                // 시나리오 3: 사진도 있고, 태그도 있음 (또는 태그 로딩 중)
                if (isOnlyTag) {
                    // 태그 Flow 뷰
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        tagItems.forEach { item ->
                            tagX(
                                text = item.tagName,
                                onDismiss = {
                                    onDeleteClick(item.tagId)
                                },
                            )
                        }
                    }
                } else {
                    // 태그 Grid 뷰
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = modifier,
                        state = tagAlbumGridState,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(tagItems) { item ->
                            TagGridItem(
                                tagId = item.tagId,
                                tagName = item.tagName,
                                imageId = item.coverImageId,
                                navController = navController,
                                onDeleteClick = onDeleteClick,
                                isDeleteMode = isDeleteMode,
                                onEnterDeleteMode = onEnterDeleteMode,
                                onExitDeleteMode = onExitDeleteMode,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun requiredImagePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagGridItem(
    tagId: String,
    tagName: String,
    imageId: Long?,
    navController: NavController,
    onDeleteClick: (String) -> Unit,
    isDeleteMode: Boolean,
    onEnterDeleteMode: () -> Unit,
    onExitDeleteMode: () -> Unit,
) {
    val imageUri: Uri? =
        remember(imageId) {
            imageId?.let { id ->
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
            }
        }

    Box(modifier = Modifier) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = tagName,
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .align(Alignment.BottomCenter)
                        .combinedClickable(
                            onClick = {
                                if (isDeleteMode) {
                                    onExitDeleteMode()
                                } else {
                                    navController.navigate(Screen.Album.createRoute(tagId, tagName))
                                }
                            },
                            onLongClick = {
                                if (!isDeleteMode) {
                                    onEnterDeleteMode()
                                }
                            },
                        ),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .aspectRatio(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                        ).align(Alignment.BottomCenter)
                        .combinedClickable(
                            onClick = {
                                if (isDeleteMode) {
                                    onExitDeleteMode()
                                } else {
                                    navController.navigate(Screen.Album.createRoute(tagId, tagName))
                                }
                            },
                            onLongClick = {
                                if (!isDeleteMode) {
                                    onEnterDeleteMode()
                                }
                            },
                        ),
            )
        }

        Text(
            text = tagName,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
        if (isDeleteMode) {
            IconButton(
                onClick = {
                    onDeleteClick(tagId)
                },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), RoundedCornerShape(50))
                        .size(24.dp),
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete Tag",
                    sizeRole = IconSizeRole.InlineAction,
                    intent = IconIntent.Surface,
                )
            }
        }
    }
}

@Composable
fun EmptyStateTags(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_empty_tags),
            contentDescription = "Create memories",
            modifier =
                Modifier
                    .size(120.dp)
                    .rotate(45f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. 텍스트
        Text(
            text = "Create memories",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Organize your memories\nby keyword",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 3. "Create New Tag" 버튼
        Button(
            onClick = {
                navController.navigate(Screen.AddTag.route)
            },
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(52.dp),
            shape = RoundedCornerShape(50.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            contentPadding = PaddingValues(horizontal = 32.dp),
        ) {
            Text(
                text = "+ Create New Tag",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * '모든 사진' 뷰가 비어있을 때 표시할 화면 (image_72d81f.png)
 */
@Composable
fun EmptyStatePhotos(
    modifier: Modifier = Modifier,
    navController: NavController,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_empty_photos),
            contentDescription = "Please upload photos",
            modifier = Modifier.size(120.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. 텍스트
        Text(
            text = "Please upload photos",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select photos\nto store your memories",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                navController.navigate(Screen.LocalGallery.route)
            },
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(52.dp),
            shape = RoundedCornerShape(50.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            contentPadding = PaddingValues(horizontal = 32.dp),
        ) {
            Text(
                text = "Upload Photos",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SortOptionsSheet(
    currentOrder: TagSortOrder,
    onOrderChange: (TagSortOrder) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            "Sort by",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SortOptionItem(
            text = "Most Recently Added",
            icon = Icons.Default.FiberNew,
            isSelected = currentOrder == TagSortOrder.CREATED_DESC,
            onClick = { onOrderChange(TagSortOrder.CREATED_DESC) },
        )
        SortOptionItem(
            text = "Name (A-Z)",
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.NAME_ASC,
            onClick = { onOrderChange(TagSortOrder.NAME_ASC) },
        )
        SortOptionItem(
            text = "Name (Z-A)",
            icon = Icons.Default.ArrowDownward,
            isSelected = currentOrder == TagSortOrder.NAME_DESC,
            onClick = { onOrderChange(TagSortOrder.NAME_DESC) },
        )
        SortOptionItem(
            text = "Count (Ascending)",
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.COUNT_ASC,
            onClick = { onOrderChange(TagSortOrder.COUNT_ASC) },
        )
        SortOptionItem(
            text = "Count (Descending)",
            icon = Icons.Default.ArrowDownward,
            isSelected = currentOrder == TagSortOrder.COUNT_DESC,
            onClick = { onOrderChange(TagSortOrder.COUNT_DESC) },
        )
    }
}

@Composable
private fun SortOptionItem(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val optionIntent = if (isSelected) IconIntent.Primary else IconIntent.Muted
        StandardIcon.Icon(
            imageVector = icon,
            contentDescription = text,
            intent = optionIntent,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            StandardIcon.Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                intent = IconIntent.Primary,
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
