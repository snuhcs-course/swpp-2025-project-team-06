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
import androidx.compose.foundation.Image // [추가] Image import
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues // [추가]
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
import androidx.compose.foundation.layout.width // [추가]
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button // [추가]
import androidx.compose.material3.ButtonDefaults // [추가]
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource // [추가]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp // [추가]
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.LogoutState
import com.example.momentag.model.Photo
import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.SearchBar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.viewmodel.AuthViewModel
import com.example.momentag.viewmodel.HomeViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val allPhotos by homeViewModel.allPhotos.collectAsState() // 서버에서 가져온 사진들
    val isLoadingPhotos by homeViewModel.isLoadingPhotos.collectAsState()
    val isLoadingMorePhotos by homeViewModel.isLoadingMorePhotos.collectAsState()
    var currentTab by remember { mutableStateOf(BottomTab.HomeScreen) }

    var onlyTag by remember { mutableStateOf(false) }
    var showAllPhotos by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }

    val allPhotosListState = homeViewModel.allPhotosListState
    val shouldReturnToAllPhotos by homeViewModel.shouldReturnToAllPhotos.collectAsState()

    var isUploadBannerDismissed by remember { mutableStateOf(false) }

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

                Spacer(modifier = Modifier.height(16.dp))

                // [수정] 로직 순서 변경
                if (!hasPermission) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("태그와 이미지를 보려면\n이미지 접근 권한을 허용해주세요.")
                    }
                }
                // [수정] 로딩 상태를 최우선으로 확인
                else if (isLoadingPhotos || homeLoadingState is HomeViewModel.HomeLoadingState.Loading) {
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
                // [수정] 로딩이 끝난 후, 데이터 상태에 따라 분기
                else {
                    // 로딩이 끝났으므로, 태그 데이터 추출
                    val tagItems = (homeLoadingState as? HomeViewModel.HomeLoadingState.Success)?.tags ?: emptyList()

                    // [수정] listState를 여기서 생성
                    val listState = if (showAllPhotos) allPhotosListState else null

                    MainContent(
                        modifier = Modifier.weight(1f),
                        onlyTag = onlyTag, // Pass the actual state
                        showAllPhotos = showAllPhotos, // Pass the actual state
                        tagItems = tagItems, // Pass the loaded tags
                        serverPhotos = allPhotos, // Pass the loaded photos
                        navController = navController,
                        onDeleteClick = { tagId ->
                            homeViewModel.deleteTag(tagId)
                        },
                        isDeleteMode = isDeleteMode,
                        onEnterDeleteMode = { isDeleteMode = true },
                        onExitDeleteMode = { isDeleteMode = false },
                        isSelectionMode = isSelectionMode,
                        selectedItems = selectedPhotos.map { it.photoId }.toSet(),
                        onItemSelectionToggle = { photoId ->
                            val photo = allPhotos.find { it.photoId == photoId }
                            photo?.let { homeViewModel.togglePhoto(it) }
                        },
                        homeViewModel = homeViewModel,
                        allPhotosListState = listState, // [수정] 생성한 state 주입
                        isLoadingMorePhotos = isLoadingMorePhotos,

                        // [수정] 로딩 완료 상태 전달
                        isLoadingPhotos = false, // 이 블록은 로딩이 끝났을 때만 실행됨
                        homeLoadingState = homeLoadingState // Success 또는 Error 상태 전달
                    )

                    // [수정] 페이지네이션 로직을 MainContent 밖으로 이동
                    if (showAllPhotos && listState != null) {
                        LaunchedEffect(listState, isLoadingMorePhotos) {
                            // 로딩 중일 때는 스크롤 감지 로직 자체를 실행하지 않도록
                            if (!isLoadingMorePhotos) {
                                snapshotFlow {
                                    listState.layoutInfo.visibleItemsInfo
                                        .lastOrNull()
                                        ?.index
                                }.distinctUntilChanged() // 같은 값이 연속으로 올 때 필터링
                                    .debounce(150) // 빠른 스크롤 시 150ms 대기 후 처리 렉 방지
                                    .collect { lastVisibleIndex ->
                                        if (lastVisibleIndex != null && allPhotos.isNotEmpty()) {
                                            val totalItems = allPhotos.size
                                            val remainingItems = totalItems - (lastVisibleIndex + 1)

                                            // 남은 아이템이 33개 미만이면 다음 페이지 로드
                                            if (remainingItems < 33) {
                                                homeViewModel?.loadMorePhotos()
                                            }
                                        }
                                    }
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
                AnimatedVisibility(visible = bannerVisible) {
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

@OptIn(ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
private fun MainContent(
    modifier: Modifier = Modifier,
    onlyTag: Boolean,
    showAllPhotos: Boolean,
    tagItems: List<TagItem>,
    serverPhotos: List<Photo> = emptyList(),
    navController: NavController,
    onDeleteClick: (String) -> Unit,
    isDeleteMode: Boolean,
    onEnterDeleteMode: () -> Unit,
    onExitDeleteMode: () -> Unit,
    isSelectionMode: Boolean,
    selectedItems: Set<String>,
    onItemSelectionToggle: (String) -> Unit,
    homeViewModel: HomeViewModel? = null,
    isLoadingMorePhotos: Boolean = false,
    allPhotosListState: LazyGridState? = null,
    // [추가] 로딩 상태를 받아옴
    isLoadingPhotos: Boolean,
    homeLoadingState: HomeViewModel.HomeLoadingState,
) {
    // [수정] 데이터 로딩 완료 시점 정의
    val isTagsLoaded = homeLoadingState is HomeViewModel.HomeLoadingState.Success || homeLoadingState is HomeViewModel.HomeLoadingState.Error
    val arePhotosLoaded = !isLoadingPhotos
    // [수정] 태그와 사진 로딩이 모두 끝나야 빈 화면 여부를 최종 결정
    val isDataReady = isTagsLoaded && arePhotosLoaded

    // [수정] 데이터 상태 정의
    val arePhotosEmpty = serverPhotos.isEmpty()
    val areTagsEmpty = tagItems.isEmpty()

    when {
        // [수정] 로직 1순위: 데이터 로딩이 완료되었고, 사진이 아예 없는 경우
        isDataReady && arePhotosEmpty -> {
            // 시나리오 1 & 2-a: 뷰와 상관없이 "사진 업로드" 표시
            EmptyStatePhotos(modifier = modifier)
        }

        // [수정] 로직 2순위: 'All Photos' 뷰 (사진이 반드시 있음)
        showAllPhotos -> {
            // 사진이 있거나, 아직 로딩 중
            val listState = allPhotosListState ?: rememberLazyGridState()

            // [삭제] 페이지네이션 LaunchedEffect (HomeScreen으로 이동됨)

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = listState,
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    count = serverPhotos.size,
                    key = { index -> serverPhotos[index].photoId }, // 성능 최적화
                ) { index ->
                    val photo = serverPhotos[index]
                    val isSelected = selectedItems.contains(photo.photoId)

                    Box(modifier = Modifier.aspectRatio(1f)) {
                        AsyncImage(
                            model = photo.contentUri,
                            contentDescription = "Photo ${index + 1}",
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
                                            if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else Color.Transparent,
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
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        // [수정] 로직 3순위: 'Tag Album' 뷰 (사진이 반드시 있음)
        !showAllPhotos && !arePhotosEmpty -> {
            if (areTagsEmpty && isDataReady) {
                // 시나리오 2-b: 사진은 있으나, 태그가 없음
                EmptyStateTags(navController = navController, modifier = modifier)
            } else {
                // 시나리오 3: 사진도 있고, 태그도 있음 (또는 태그 로딩 중)
                if (onlyTag) {
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

// ======================================================================
// [추가] Empty State Composable 함수들
// ======================================================================

/**
 * 태그 앨범이 비어있을 때 표시할 화면 (image_72d4b7.png)
 */
@Composable
fun EmptyStateTags(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 1. 일러스트레이션 (ic_empty_tags.png)
        // [주의] 이 파일을 res/drawable에 추가해야 합니다.
        Image(
            painter = painterResource(id = R.drawable.ic_empty_tags),
            contentDescription = "추억을 만들어보세요",
            modifier = Modifier.size(120.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. 텍스트
        Text(
            text = "추억을 만들어보세요",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "키워드로 추억을\n모아보세요",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
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
fun EmptyStatePhotos(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 1. 일러스트레이션 (ic_empty_photos.png)
        // [주의] 이 파일을 res/drawable에 추가해야 합니다.
        Image(
            painter = painterResource(id = R.drawable.ic_empty_photos),
            contentDescription = "사진을 업로드해주세요",
            modifier = Modifier.size(120.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 2. 텍스트
        Text(
            text = "사진을 업로드해주세요",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "추억을 담을 사진들을\n골라보아요",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}