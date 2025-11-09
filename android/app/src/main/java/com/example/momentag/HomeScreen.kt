package com.example.momentag

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.LogoutState
import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.SearchBar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.viewmodel.AuthViewModel
import com.example.momentag.viewmodel.DatedPhotoGroup
import com.example.momentag.viewmodel.HomeViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import com.example.momentag.viewmodel.TagSortOrder
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val sharedPreferences = remember { context.getSharedPreferences("MomenTagPrefs", Context.MODE_PRIVATE) }
    var hasPermission by remember { mutableStateOf(false) }
    val authViewModel: AuthViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val logoutState by authViewModel.logoutState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val photoViewModel: PhotoViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val homeViewModel: HomeViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val homeLoadingState by homeViewModel.homeLoadingState.collectAsState()
    val homeDeleteState by homeViewModel.homeDeleteState.collectAsState()
    val uiState by photoViewModel.uiState.collectAsState()
    val selectedPhotos by homeViewModel.selectedPhotos.collectAsState() // draftTagRepository에서 가져옴!
    val isLoadingPhotos by homeViewModel.isLoadingPhotos.collectAsState()
    val isLoadingMorePhotos by homeViewModel.isLoadingMorePhotos.collectAsState()
    var currentTab by remember { mutableStateOf(BottomTab.HomeScreen) }

    var onlyTag by remember { mutableStateOf(false) }
    var showAllPhotos by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }

    val shouldReturnToAllPhotos by homeViewModel.shouldReturnToAllPhotos.collectAsState()

    val groupedPhotos by homeViewModel.groupedPhotos.collectAsState()
    val allPhotos by homeViewModel.allPhotos.collectAsState()

    var isUploadBannerDismissed by remember { mutableStateOf(false) }

    val currentSortOrder by homeViewModel.sortOrder.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            isUploadBannerDismissed = false
        }
    }

    val bannerVisible = uiState.isLoading && !isUploadBannerDismissed

    LaunchedEffect(bannerVisible) {
        if (bannerVisible) {
            kotlinx.coroutines.delay(5000)
            isUploadBannerDismissed = true
        }
    }

    LaunchedEffect(Unit) {
        if (shouldReturnToAllPhotos) {
            showAllPhotos = true
            onlyTag = false
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
                val msg = (logoutState as LogoutState.Error).message ?: "Logout failed"
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
            else -> Unit
        }
    }

    // done once when got permission
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            if (homeViewModel.allPhotos.value.isEmpty()) {
                homeViewModel.loadServerTags()
                homeViewModel.loadAllPhotos() // 서버에서 모든 사진 가져오기

                val hasAlreadyUploaded = sharedPreferences.getBoolean("INITIAL_UPLOAD_COMPLETED_112", false)
                if (!hasAlreadyUploaded) {
                    photoViewModel.uploadPhotos()
                    sharedPreferences.edit().putBoolean("INITIAL_UPLOAD_COMPLETED_112", true).apply()
                }
            }
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            photoViewModel.userMessageShown()
        }
    }

    LaunchedEffect(homeLoadingState) {
        val message =
            when (homeLoadingState) {
                is HomeViewModel.HomeLoadingState.Error -> (homeLoadingState as HomeViewModel.HomeLoadingState.Error).message
                else -> null
            }
        message?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
        }
    }

    LaunchedEffect(homeDeleteState) {
        when (val state = homeDeleteState) {
            is HomeViewModel.HomeDeleteState.Success -> {
                Toast.makeText(context, "Tag Deleted", Toast.LENGTH_SHORT).show()
                homeViewModel.loadServerTags()
                isDeleteMode = false
                homeViewModel.resetDeleteState()
            }
            is HomeViewModel.HomeDeleteState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(state.message) }
                isDeleteMode = false
                homeViewModel.resetDeleteState()
            }
            is HomeViewModel.HomeDeleteState.Loading -> {
            }
            is HomeViewModel.HomeDeleteState.Idle -> {
            }
        }
    }

    // Show snackbar when selection count changes
    LaunchedEffect(selectedPhotos.size, isSelectionMode) {
        if (isSelectionMode && selectedPhotos.isNotEmpty()) {
            snackbarHostState.showSnackbar("${selectedPhotos.size}개 선택됨")
        }
    }

    Scaffold(
        topBar = {
            CommonTopBar(
                title = "#MomenTag",
                onTitleClick = {
                    navController.navigate(Screen.LocalGallery.route)
                },
                showLogout = true,
                onLogoutClick = { authViewModel.logout() },
                isLogoutLoading = logoutState is LogoutState.Loading,
                actions = {
                    // 태그 앨범 뷰(!showAllPhotos)에서는 선택 모드 버튼을 표시하지 않음
                    if (showAllPhotos) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isSelectionMode) {
                                // Cancel button when in selection mode
                                IconButton(
                                    onClick = {
                                        isSelectionMode = false
                                        homeViewModel.resetSelection() // draftRepository 초기화!
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isSelectionMode) {
                                        // Share action
                                        val photos = homeViewModel.getPhotosToShare()
                                        ShareUtils.sharePhotos(context, photos)
                                    } else {
                                        // Enter selection mode
                                        isSelectionMode = true
                                        homeViewModel.resetSelection() // 진입 시 초기화
                                    }
                                },
                                enabled = selectedPhotos.isNotEmpty(),
                            ) {
                                Icon(
                                    imageVector = if (isSelectionMode) Icons.Default.Share else Icons.Default.Edit,
                                    contentDescription = if (isSelectionMode) "Share" else "Edit",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = data.visuals.message,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        RoundedCornerShape(20.dp),
                                    ).padding(horizontal = 16.dp, vertical = 8.dp),
                        )
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
                            // 이미 홈 화면
                        }
                        BottomTab.SearchResultScreen -> {
                            navController.navigate(Screen.SearchResult.initialRoute())
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
        },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            // 태그 앨범 뷰(!showAllPhotos)에서는 Create Tag 버튼을 표시하지 않음
            if (showAllPhotos) {
                CreateTagButton(
                    modifier = Modifier.padding(start = 32.dp, bottom = 16.dp),
                    text = if (isSelectionMode && selectedPhotos.isNotEmpty()) "Create with ${selectedPhotos.size}" else "Create Tag",
                    onClick = {
                        // selectedPhotos는 이미 draftTagRepository에 저장되어 있음!
                        // SearchResultScreen과 동일한 패턴
                        isSelectionMode = false
                        navController.navigate(Screen.AddTag.route)
                    },
                )
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = homeLoadingState is HomeViewModel.HomeLoadingState.Loading || isLoadingPhotos,
            onRefresh = {
                if (hasPermission) {
                    isDeleteMode = false
                    homeViewModel.loadServerTags()
                    homeViewModel.loadAllPhotos() // 서버 사진도 새로고침
                }
            },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Search Bar with Filter Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchBar(
                        onSearch = { query ->
                            if (query.isNotEmpty()) {
                                navController.navigate(Screen.SearchResult.createRoute(query))
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier.weight(1f),
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
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 7. "태그 앨범" 뷰일 때만 정렬 버튼 표시
                    if (!showAllPhotos) {
                        IconButton(onClick = { scope.launch { sheetState.show() } }) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Sort Tag Albums",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // "All Photos" 뷰일 때 공간을 차지할 빈 Spacer
                        Spacer(modifier = Modifier.size(48.dp)) // IconButton 크기만큼
                    }
                    ViewToggle(
                        onlyTag = onlyTag,
                        showAllPhotos = showAllPhotos,
                        onToggle = { tagOnly, allPhotos ->
                            onlyTag = tagOnly
                            showAllPhotos = allPhotos
                            if (isSelectionMode) {
                                isSelectionMode = false
                                homeViewModel.resetSelection() // draftRepository 초기화
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                if (!hasPermission) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("태그와 이미지를 보려면\n이미지 접근 권한을 허용해주세요.")
                    }
                } else if (showAllPhotos) {
                    if (isLoadingPhotos) {
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val listState = rememberLazyGridState()
                        MainContent(
                            onlyTag = false,
                            showAllPhotos = true,
                            tagItems = emptyList(),
                            groupedPhotos = groupedPhotos,
                            navController = navController,
                            onDeleteClick = { },
                            modifier = Modifier.weight(1f),
                            isDeleteMode = false,
                            onEnterDeleteMode = {
                                isSelectionMode = true
                            },
                            onExitDeleteMode = { },
                            isSelectionMode = isSelectionMode,
                            selectedItems = selectedPhotos.map { it.photoId }.toSet(),
                            onItemSelectionToggle = { photoId ->
                                val photo = allPhotos.find { it.photoId == photoId }
                                photo?.let { homeViewModel.togglePhoto(it) }
                            },
                            homeViewModel = homeViewModel,
                            lazyGridState = listState,
                            isLoadingMorePhotos = isLoadingMorePhotos,
                        )
                        LaunchedEffect(listState, isLoadingMorePhotos) {
                            if (!isLoadingMorePhotos) {
                                snapshotFlow {
                                    listState.layoutInfo.visibleItemsInfo
                                        .lastOrNull()
                                        ?.index
                                }.distinctUntilChanged()
                                    .debounce(150)
                                    .collect { lastVisibleIndex ->
                                        // 총 아이템 개수 = (날짜 헤더 개수) + (총 사진 개수)
                                        val totalItemCount = groupedPhotos.size + allPhotos.size
                                        if (lastVisibleIndex != null && totalItemCount > 0) {
                                            // 남은 아이템 수 = 총 아이템 - (마지막 인덱스 + 1)
                                            val remainingItems = totalItemCount - (lastVisibleIndex + 1)
                                            // 3열 그리드 기준, 약 11줄(33개) 미만일 때 로드
                                            if (remainingItems < 33) {
                                                homeViewModel.loadMorePhotos()
                                            }
                                        }
                                    }
                            }
                        }
                    }
                } else {
                    when (homeLoadingState) {
                        is HomeViewModel.HomeLoadingState.Success -> {
                            val tagItems = (homeLoadingState as HomeViewModel.HomeLoadingState.Success).tags
                            MainContent(
                                onlyTag = onlyTag,
                                showAllPhotos = showAllPhotos,
                                tagItems = tagItems,
                                groupedPhotos = emptyList(),
                                navController = navController,
                                onDeleteClick = { tagId ->
                                    homeViewModel.deleteTag(tagId)
                                },
                                modifier = Modifier.weight(1f),
                                isDeleteMode = isDeleteMode,
                                onEnterDeleteMode = {
                                    isDeleteMode = true
                                },
                                onExitDeleteMode = { isDeleteMode = false },
                                isSelectionMode = false, // 태그 앨범 뷰에서는 선택 모드 비활성화
                                selectedItems = emptySet(),
                                onItemSelectionToggle = { }, // 사용되지 않음
                                homeViewModel = homeViewModel,
                                lazyGridState = null,
                                isLoadingMorePhotos = false,
                            )
                        }

                        is HomeViewModel.HomeLoadingState.Loading -> {
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        else -> { // Error, NetworkError, Idle
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "태그를 불러오지 못했습니다.\n아래로 당겨 새로고침하세요.",
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
                if (showAllPhotos && isLoadingMorePhotos) {
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
                AnimatedVisibility(visible = uiState.isLoading && !isUploadBannerDismissed) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        WarningBanner(
                            title = "업로드 진행 중 🚀",
                            message = "사진을 백그라운드에서 업로드하고 있습니다.",
                            onActionClick = { },
                            showActionButton = false,
                            backgroundColor = MaterialTheme.colorScheme.onErrorContainer,
                            icon = Icons.Default.Upload,
                            showDismissButton = true,
                            onDismiss = {
                                isUploadBannerDismissed = true
                            },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
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
    onlyTag: Boolean,
    showAllPhotos: Boolean,
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
                                if (!onlyTag && !showAllPhotos) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            ).clickable { onToggle(false, false) }
                            .padding(8.dp),
                ) {
                    Icon(
                        Icons.Default.CollectionsBookmark,
                        contentDescription = "Tag Albums",
                        tint =
                            if (!onlyTag &&
                                !showAllPhotos
                            ) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
                // All Photos (Grid)
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (showAllPhotos) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            ).clickable { onToggle(false, true) }
                            .padding(8.dp),
                ) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = "All Photos",
                        tint = if (showAllPhotos) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
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
    onlyTag: Boolean,
    showAllPhotos: Boolean,
    tagItems: List<TagItem>,
    groupedPhotos: List<DatedPhotoGroup> = emptyList(),
    navController: NavController,
    onDeleteClick: (String) -> Unit,
    isDeleteMode: Boolean,
    onEnterDeleteMode: () -> Unit,
    onExitDeleteMode: () -> Unit,
    isSelectionMode: Boolean,
    selectedItems: Set<String>,
    onItemSelectionToggle: (String) -> Unit,
    homeViewModel: HomeViewModel? = null,
    lazyGridState: LazyGridState? = null,
    isLoadingMorePhotos: Boolean = false,
) {
    when {
        onlyTag -> {
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
        }

        showAllPhotos -> {
            val listState = lazyGridState ?: rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = listState,
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
                                    .padding(top = 16.dp, bottom = 8.dp),
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
                                                    onEnterDeleteMode()
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
        else -> {
            // Tag Albums Grid View
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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

private fun requiredImagePermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

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
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Delete Tag",
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(16.dp),
                )
            }
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
            "정렬 기준",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SortOptionItem(
            text = "이름 (가나다순)",
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.NAME_ASC,
            onClick = { onOrderChange(TagSortOrder.NAME_ASC) },
        )
        SortOptionItem(
            text = "이름 (가나다 역순)",
            icon = Icons.Default.ArrowDownward,
            isSelected = currentOrder == TagSortOrder.NAME_DESC,
            onClick = { onOrderChange(TagSortOrder.NAME_DESC) },
        )
        SortOptionItem(
            text = "최근 추가 순",
            icon = Icons.Default.FiberNew,
            isSelected = currentOrder == TagSortOrder.CREATED_DESC,
            onClick = { onOrderChange(TagSortOrder.CREATED_DESC) },
        )
        SortOptionItem(
            text = "항목 많은 순",
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.COUNT_DESC,
            onClick = { onOrderChange(TagSortOrder.COUNT_DESC) },
        )
        SortOptionItem(
            text = "항목 적은 순",
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.COUNT_ASC,
            onClick = { onOrderChange(TagSortOrder.COUNT_ASC) },
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
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
