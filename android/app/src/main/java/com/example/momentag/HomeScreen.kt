package com.example.momentag

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.LogoutState
import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.components.confirmDialog
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
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val authViewModel: AuthViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val photoViewModel: PhotoViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val homeViewModel: HomeViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val logoutState by authViewModel.logoutState.collectAsState()
    val homeLoadingState by homeViewModel.homeLoadingState.collectAsState()
    val homeDeleteState by homeViewModel.homeDeleteState.collectAsState()
    val uiState by photoViewModel.uiState.collectAsState()
    val selectedPhotos by homeViewModel.selectedPhotos.collectAsState()
    val isLoadingPhotos by homeViewModel.isLoadingPhotos.collectAsState()
    val isLoadingMorePhotos by homeViewModel.isLoadingMorePhotos.collectAsState()
    var currentTab by remember { mutableStateOf(BottomTab.HomeScreen) }

    var onlyTag by remember { mutableStateOf(false) }
    var showAllPhotos by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var isSelectionModeDelay by remember { mutableStateOf(false) }

    val shouldReturnToAllPhotos by homeViewModel.shouldReturnToAllPhotos.collectAsState()

    val groupedPhotos by homeViewModel.groupedPhotos.collectAsState()
    val allPhotos by homeViewModel.allPhotos.collectAsState()

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var tagToDeleteInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    // --- [새 검색창] 상태 관리 ---
    // HomeViewModel에서 로드된 전체 태그 목록 가져오기
    val allTags = (homeLoadingState as? HomeViewModel.HomeLoadingState.Success)?.tags ?: emptyList()
    // 칩으로 선택된 태그 목록
    var selectedSearchTags by remember { mutableStateOf(listOf<TagItem>()) }
    // 현재 텍스트 필드에 입력 중인 값
    var currentSearchInput by remember { mutableStateOf("") }
    // 텍스트 필드 포커스 관리를 위함
    val focusRequester = remember { FocusRequester() }

    // 1. 입력값이 '#'으로 시작하는지, 추천 검색어는 무엇인지 파생
    val (isTagSearch, tagQuery) =
        remember(currentSearchInput) {
            if (currentSearchInput.startsWith("#") && currentSearchInput.length > 0) {
                // #만 입력된 경우에도 태그 검색 모드 활성화, 쿼리는 비움
                Pair(true, currentSearchInput.substring(1))
            } else {
                Pair(false, "")
            }
        }

    // 2. 태그 추천 목록 필터링
    val tagSuggestions by remember(isTagSearch, tagQuery, allTags, selectedSearchTags) {
        derivedStateOf {
            if (isTagSearch) {
                allTags.filter {
                    // 태그 이름에 검색어가 포함되고
                    it.tagName.contains(tagQuery, ignoreCase = true) &&
                            // 이미 선택된 칩 목록에는 없는지 확인
                            selectedSearchTags.none { selected -> selected.tagId == it.tagId }
                }
            } else {
                emptyList() // #으로 시작하지 않으면 추천 목록 비움
            }
        }
    }

    // 3. 실제 검색 실행 로직
    val performSearch = {
        focusManager.clearFocus()
        // 칩으로 선택된 태그 + 현재 입력 중인 텍스트를 조합
        val tagQueryPart = selectedSearchTags.joinToString(" ") { "#${it.tagName}" }
        val textQueryPart =
            if (isTagSearch) "" else currentSearchInput.trim() // # 입력 중이었다면 무시

        val finalQuery = "$tagQueryPart $textQueryPart".trim()

        if (finalQuery.isNotEmpty()) {
            navController.navigate(Screen.SearchResult.createRoute(finalQuery))
            // 검색 후 입력 필드 초기화 안 함 (검색 결과 화면에서 뒤로 왔을 때 유지되도록)
            // selectedSearchTags = emptyList()
            // currentSearchInput = ""
        }
    }
    // --- [새 검색창] 상태 관리 끝 ---

    LaunchedEffect(isSelectionMode) {
        kotlinx.coroutines.delay(200L) // 0.2초
        isSelectionModeDelay = isSelectionMode
    }

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
            // 앱 실행 시 항상 태그와 사진을 새로고침 (AddTag에서 돌아올 때 자동 반영을 위해)
            // if (homeViewModel.allPhotos.value.isEmpty()) { // <--- 이 조건 제거
            homeViewModel.loadServerTags()
            homeViewModel.loadAllPhotos() // 서버에서 모든 사진 가져오기

            val hasAlreadyUploaded = sharedPreferences.getBoolean("INITIAL_UPLOAD_COMPLETED_112", false)
            if (!hasAlreadyUploaded) {
                // photoViewModel.uploadPhotos() // <--- 초기 자동 업로드 비활성화 (LocalGallery에서 수동)
                // sharedPreferences.edit().putBoolean("INITIAL_UPLOAD_COMPLETED_112", true).apply()
            }
            // }
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

    BackHandler(enabled = isSelectionMode && showAllPhotos) {
        isSelectionMode = false
        homeViewModel.resetSelection()
    }

    BackHandler(enabled = showDeleteConfirmationDialog) {
        showDeleteConfirmationDialog = false
        tagToDeleteInfo = null
    }

    // 화면이 다시 보일 때 (ON_RESUME) 태그와 사진 새로고침
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, hasPermission) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (hasPermission) {
                        homeViewModel.loadServerTags()
                        homeViewModel.loadAllPhotos()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
                    if (showAllPhotos && groupedPhotos.isNotEmpty()) {
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
                                            val photos = homeViewModel.getPhotosToShare()
                                            ShareUtils.sharePhotos(context, photos)

                                            Toast
                                                .makeText(
                                                    context,
                                                    "Share ${photos.size} photo(s)",
                                                    Toast.LENGTH_SHORT,
                                                ).show()

                                            homeViewModel.resetSelection()
                                            showMenu = false
                                        },
                                        enabled = selectedPhotos.isNotEmpty(),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Cancel") },
                                        onClick = {
                                            isSelectionMode = false
                                            homeViewModel.resetSelection()
                                            showMenu = false
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Select") },
                                        onClick = {
                                            isSelectionMode = true
                                            homeViewModel.resetSelection()
                                            showMenu = false
                                        },
                                    )
                                }
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
                            homeViewModel.resetSelection()
                            navController.navigate(Screen.SearchResult.initialRoute())
                        }
                        // [오류 수정] MyTagsScreen 브랜치 경로 수정
                        BottomTab.MyTagsScreen -> {
                            homeViewModel.resetSelection()
                            navController.navigate(Screen.MyTags.route)
                        }
                        BottomTab.StoryScreen -> {
                            homeViewModel.resetSelection()
                            navController.navigate(Screen.Story.route)
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            // [수정] showAllPhotos 뷰일 때 + 사진이 실제로 있을 때만 버튼 표시
            if (showAllPhotos && groupedPhotos.isNotEmpty()) {
                CreateTagButton(
                    modifier = Modifier.padding(start = 32.dp, bottom = 16.dp),
                    text = if (isSelectionMode && selectedPhotos.isNotEmpty()) "Create with ${selectedPhotos.size}" else "Create Tag",
                    onClick = {
                        // selectedPhotos는 이미 draftTagRepository에 저장되어 있음!
                        isSelectionMode = false
                        navController.navigate(Screen.AddTag.route)
                    },
                )
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = (homeLoadingState is HomeViewModel.HomeLoadingState.Loading && groupedPhotos.isEmpty()) || (isLoadingPhotos && groupedPhotos.isEmpty()),
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

                // --- [검색창 UI 수정] ---
                // Search Bar with Filter Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // [대체] 기존 SearchBar(...)를 TagSearchInputArea로 대체
                    TagSearchInputArea(
                        modifier = Modifier.weight(1f),
                        focusRequester = focusRequester,
                        inputValue = currentSearchInput,
                        onValueChange = { currentSearchInput = it },
                        selectedTags = selectedSearchTags,
                        onRemoveTag = { tag ->
                            selectedSearchTags = selectedSearchTags - tag
                        },
                        onSearch = {
                            performSearch() // 키보드 액션으로 검색 실행
                        },
                    )

                    // [유지] 기존 '필터' 버튼 (이제 검색 실행 버튼 역할)
                    IconButton(
                        onClick = {
                            performSearch() // 버튼 클릭으로 검색 실행
                        },
                        modifier =
                            Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(12.dp),
                                ),
                    ) {
                        // [수정] 아이콘을 FilterList -> Search로 변경 (더 명확함)
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                // [추가] 태그 추천 목록
                if (tagSuggestions.isNotEmpty()) {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp) // 추천 목록의 최대 높이 제한
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    ) {
                        items(tagSuggestions, key = { it.tagId }) { tag ->
                            TagSuggestionItem(
                                tagItem = tag,
                                onClick = {
                                    // 항목 클릭 시 "칩" 추가 및 텍스트 초기화
                                    selectedSearchTags = selectedSearchTags + tag
                                    currentSearchInput = ""
                                },
                            )
                        }
                    }
                }
                // --- [검색창 UI 수정 끝] ---

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // "태그 앨범" 뷰일 때만 정렬 버튼 표시
                    if (!showAllPhotos) {
                        IconButton(onClick = { scope.launch { sheetState.show() } }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
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
                } else if ((isLoadingPhotos || homeLoadingState is HomeViewModel.HomeLoadingState.Loading) && groupedPhotos.isEmpty()) {
                    // [수정] 로딩 조건: 로딩 중이면서 아직 표시할 사진이 없을 때
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
                    val listState = if (showAllPhotos) rememberLazyGridState() else null

                    MainContent(
                        modifier = Modifier.weight(1f),
                        onlyTag = onlyTag, // Pass the actual state
                        showAllPhotos = showAllPhotos, // Pass the actual state
                        tagItems = allTags, // [수정] allTags 전달 (이미 로드됨)
                        groupedPhotos = groupedPhotos,
                        navController = navController,
                        onDeleteClick = { tagId ->
                            // [수정] 삭제 확인 대화상자 띄우기
                            val tagItem = allTags.find { it.tagId == tagId }
                            if (tagItem != null) {
                                tagToDeleteInfo = Pair(tagItem.tagId, tagItem.tagName)
                                showDeleteConfirmationDialog = true
                                isDeleteMode = false // 대화상자를 띄우면 삭제 모드(x 아이콘)는 해제
                            }
                        },
                        isDeleteMode = isDeleteMode,
                        onEnterDeleteMode = { isDeleteMode = true },
                        onExitDeleteMode = { isDeleteMode = false },
                        isSelectionMode = isSelectionMode,
                        onEnterSelectionMode = { isSelectionMode = true },
                        selectedItems = selectedPhotos.map { it.photoId }.toSet(),
                        onItemSelectionToggle = { photoId ->
                            val photo = allPhotos.find { it.photoId == photoId }
                            photo?.let { homeViewModel.togglePhoto(it) }
                        },
                        homeViewModel = homeViewModel,
                        lazyGridState = listState,
                        isLoadingMorePhotos = isLoadingMorePhotos,
                        isLoadingPhotos = isLoadingPhotos, // 로딩 상태 전달
                        homeLoadingState = homeLoadingState, // Success 또는 Error 상태 전달
                    )

                    // 페이지네이션 로직을 MainContent 밖으로 이동
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
                                        val totalItemCount = listState.layoutInfo.totalItemsCount
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

    // [수정] errorDialog 호출을 confirmDialog 호출로 변경
    if (showDeleteConfirmationDialog && tagToDeleteInfo != null) {
        val (tagId, tagName) = tagToDeleteInfo!!

        confirmDialog(
            title = "태그 삭제",
            message = "'$tagName' 태그를 삭제하시겠습니까?",
            confirmButtonText = "Delete Tag",
            onConfirm = {
                // "Delete Tag" 버튼 클릭 시
                homeViewModel.deleteTag(tagId)
                Toast.makeText(context, "삭제되었습니다", Toast.LENGTH_SHORT).show()
                showDeleteConfirmationDialog = false
                tagToDeleteInfo = null
            },
            onDismiss = {
                // X 버튼 또는 바깥쪽 클릭 시 (취소)
                showDeleteConfirmationDialog = false
                tagToDeleteInfo = null
            },
            dismissible = true, // 바깥쪽 클릭 및 뒤로가기 버튼으로 닫기 허용
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
// [추가] 헬퍼 컴포저블 3개 (Tag Chip Search)
// --------------------

/**
 * 칩과 텍스트 입력 필드가 포함된 핵심 입력 영역
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSearchInputArea(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    inputValue: String,
    onValueChange: (String) -> Unit,
    selectedTags: List<TagItem>,
    onRemoveTag: (TagItem) -> Unit,
    onSearch: () -> Unit,
) {
    // 1. TextField 모양의 컨테이너
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight() // 내용물(칩)이 많아지면 자동으로 늘어남
                .heightIn(min = 48.dp) // 최소 높이
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable( // 컨테이너 어디를 눌러도 텍스트 필드에 포커스
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    focusRequester.requestFocus()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp), // 내부 패딩
    ) {
        // 2. 칩과 텍스트 필드를 자동으로 줄바꿈해주는 FlowRow
        FlowRow(
            // [오류 수정] verticalArrangement 파라미터 사용 (spacing)
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // 3. 선택된 태그 칩 목록
            selectedTags.forEach { tag ->
                TagChip(
                    tagItem = tag,
                    onRemove = { onRemoveTag(tag) },
                    // [오류 수정] align으로 수직 중앙 정렬
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }

            // 4. 실제 텍스트 입력 필드
            BasicTextField(
                value = inputValue,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .focusRequester(focusRequester)
                        // [오류 수정] .weight(1f) 삭제 (FlowRow에서 지원 안 함)
                        .padding(vertical = 4.dp) // 칩과 수직 정렬
                        .heightIn(min = 24.dp) // 최소 높이 보장
                        // [오류 수정] align으로 수직 중앙 정렬
                        .align(Alignment.CenterVertically),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    // Placeholder (입력 힌트)
                    if (inputValue.isEmpty() && selectedTags.isEmpty()) {
                        Text(
                            "검색 또는 #태그 입력",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField() // 실제 입력 필드가 그려지는 부분
                },
            )
        }
    }
}

/**
 * 선택된 태그를 표시하는 칩
 */
@Composable
private fun TagChip(
    tagItem: TagItem,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier, // [수정] modifier 파라미터 추가
) {
    Row(
        modifier =
            modifier // [수정] 전달받은 modifier 적용
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "#${tagItem.tagName}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Remove ${tagItem.tagName}",
            modifier =
                Modifier
                    .size(16.dp)
                    .clickable { onRemove() },
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * 태그 추천 목록에 표시되는 항목
 */
@Composable
private fun TagSuggestionItem(
    tagItem: TagItem,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tagItem.tagName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${tagItem.photoCount} photos",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


// --- [기존 Helper 함수들] ---

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
    onEnterSelectionMode: () -> Unit,
    selectedItems: Set<String>,
    onItemSelectionToggle: (String) -> Unit,
    homeViewModel: HomeViewModel? = null,
    lazyGridState: LazyGridState? = null,
    isLoadingMorePhotos: Boolean = false,
    isLoadingPhotos: Boolean,
    homeLoadingState: HomeViewModel.HomeLoadingState,
) {
    val isTagsLoaded =
        homeLoadingState is HomeViewModel.HomeLoadingState.Success || homeLoadingState is HomeViewModel.HomeLoadingState.Error
    val arePhotosLoaded = !isLoadingPhotos || groupedPhotos.isNotEmpty() // [수정] 사진이 있으면 로드된 것으로 간주
    // 태그와 사진 로딩이 모두 끝나야 빈 화면 여부를 최종 결정
    val isDataReady = isTagsLoaded && arePhotosLoaded

    // 데이터 상태 정의
    val arePhotosEmpty = groupedPhotos.isEmpty()
    val areTagsEmpty = tagItems.isEmpty()

    when {
        isDataReady && arePhotosEmpty -> {
            EmptyStatePhotos(modifier = modifier, navController = navController)
        }

        // 로직 2순위: 'All Photos' 뷰 (사진이 반드시 있음)
        showAllPhotos -> {
            // 사진이 있거나, 아직 로딩 중
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

        // 로직 3순위: 'Tag Album' 뷰 (사진이 반드시 있음)
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
            contentDescription = "추억을 만들어보세요",
            modifier = Modifier.size(120.dp).rotate(45f),
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
            icon = Icons.Default.ArrowDownward,
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