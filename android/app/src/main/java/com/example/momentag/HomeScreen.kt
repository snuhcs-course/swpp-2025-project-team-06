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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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
import android.util.Log
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import java.util.UUID
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.momentag.ui.components.ChipSearchBar
import com.example.momentag.ui.components.SearchContentElement
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

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

    val listState = rememberLazyListState()

    // HomeViewModel에서 로드된 전체 태그 목록 가져오기
    val allTags = (homeLoadingState as? HomeViewModel.HomeLoadingState.Success)?.tags ?: emptyList()
    // 빠른 조회를 위한 태그 맵 (소문자 키)
    val allTagsMap = remember(allTags) {
        allTags.associateBy { it.tagName.lowercase() }
    }

    // 1. [신규] 모든 텍스트 필드의 UI 상태(TextFieldValue)를 저장하는 맵
    val textStates = remember { mutableStateMapOf<String, TextFieldValue>() }
    // 2. [신규] 검색창의 구조(Chip/Text)를 정의하는 리스트
    val contentItems = remember { mutableStateListOf<SearchContentElement>() }
    // 3. [신규] 현재 포커스된 Text 요소의 ID
    var focusedElementId by remember { mutableStateOf<String?>(null) }
    // 4. [신규] 모든 텍스트 필드의 FocusRequester 맵
    val focusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    // 5. [신규] BringIntoViewRequester 맵
    val bringIntoViewRequesters = remember { mutableStateMapOf<String, BringIntoViewRequester>() }
    var searchBarWidth by remember { mutableStateOf(0) }

    // **[수정] 현재 포커스된 텍스트 필드의 값을 추적합니다.**
    val currentFocusedTextState = textStates[focusedElementId]

    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0 // 1. 키보드 가시성 확인
    val currentFocusedElementId = rememberUpdatedState(focusedElementId) // 2. 최신 ID 추적

    /**
     * [신규] 특정 ID의 텍스트 필드에 포커스를 요청하는 헬퍼 함수
     */
    fun requestFocusById(id: String) {
        scope.launch {
            focusRequesters[id]?.requestFocus()
        }
    }

    /**
     * [신규] 현재 인덱스에서 가장 가까운 "이전" 텍스트 필드의 ID를 찾는 헬퍼 함수
     */
    fun findPreviousTextElementId(startIndex: Int): String? {
        if (startIndex <= 0) return null
        // 역순으로 탐색
        for (i in (startIndex - 1) downTo 0) {
            val item = contentItems.getOrNull(i)
            if (item is SearchContentElement.Text) {
                return item.id
            }
        }
        return null // 못 찾으면 null
    }

    /**
     * [신규] 현재 인덱스에서 가장 가까운 "다음" 텍스트 필드의 ID를 찾는 헬퍼 함수
     */
    fun findNextTextElementId(startIndex: Int): String? {
        if (startIndex >= contentItems.size - 1) return null
        // 순방향 탐색
        for (i in (startIndex + 1) until contentItems.size) {
            val item = contentItems.getOrNull(i)
            if (item is SearchContentElement.Text) {
                return item.id
            }
        }
        return null
    }

    // [신규] 3. 키보드가 사라졌을 때 포커스를 해제하는 LaunchedEffect
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible && currentFocusedElementId.value != null) {
            // 키보드가 닫혔는데, 여전히 포커스가 있다고 생각되면
            focusManager.clearFocus() // 포커스를 강제 해제 (onFocus(null)이 호출됨)
        }
    }

    // 5. [신규] 상태 초기화 로직 (처음 한 번만 실행)
    LaunchedEffect(Unit) {
        if (contentItems.isEmpty()) {
            val initialId = UUID.randomUUID().toString()
            // 리스트에 Text 요소 추가
            contentItems.add(SearchContentElement.Text(id = initialId, text = ""))
            // UI 상태 추가
            textStates[initialId] = TextFieldValue("\u200B", TextRange(1))
            // 포커스 리퀘스터 추가
            focusRequesters[initialId] = FocusRequester()
            bringIntoViewRequesters[initialId] = BringIntoViewRequester()
//            // 초기 포커스 설정
//            focusedElementId = initialId
//            // 실제 포커스 요청
//            requestFocusById(initialId)
        }
    }

    var hideCursor by remember { mutableStateOf(false) }

    // TODO : prevent text after a tag chip from shifting left.
    // focusedElementId가 변경될 때마다 포커스와 스크롤을 처리하는 Effect
    LaunchedEffect(focusedElementId) {
        hideCursor = true
        val id = focusedElementId ?: return@LaunchedEffect

        // FocusRequester가 등록될 때까지 기다림
        snapshotFlow { focusRequesters[id] }
            .filterNotNull()
            .first()

        // UI가 새로 그려질 때까지 한 프레임 기다림 (중요!)
        awaitFrame()

        // 새로 생성된 TextField 위치로 스크롤
        val index = contentItems.indexOfFirst { it.id == id }

        if (index == -1) {
            hideCursor = false
            return@LaunchedEffect
        }

//        listState.scrollToItem(index)

        // 1. 현재 화면에 보이는지 확인
        val visibleItemInfo = listState.layoutInfo.visibleItemsInfo.find { it.index == index }

        val isFullyVisible = if (visibleItemInfo != null) {
            // 2. 화면에 보이긴 하는데, 완전히 보이는지 (오른쪽 끝이 잘리지 않았는지) 확인
            val viewportEnd = listState.layoutInfo.viewportEndOffset
            val itemEnd = visibleItemInfo.offset + visibleItemInfo.size
            itemEnd <= viewportEnd + 1 // (1.dp 정도의 오차 허용)
        } else {
            false
        }

        // 4. [수정] 아이템이 "완전히" 보이지 않을 때만 스크롤 실행
        if (!isFullyVisible) {
//            Log.d("searchbar", "width : $searchBarWidth")
            listState.scrollToItem(index, searchBarWidth - 10) // TODO : modify this
        } else {
            bringIntoViewRequesters[id]?.bringIntoView()
        }

//        awaitFrame()

//        bringIntoViewRequesters[id]?.bringIntoView()

//        awaitFrame()

        // focus 요청 (이제 attach 완료 상태)
        focusRequesters[id]?.requestFocus()

        hideCursor = false
    }

    // [수정됨] 태그 추천 로직 (현재 포커스된 필드 기준)
    val (isTagSearch, tagQuery) = remember(focusedElementId, textStates[focusedElementId]) {
        val currentInput = textStates[focusedElementId] ?: TextFieldValue()

        val cursorPosition = currentInput.selection.start
        if (cursorPosition == 0) Pair(false, "")
        else {
            val textUpToCursor = currentInput.text.substring(0, cursorPosition)
            // [수정] onClick과 동일한 로직을 사용 (이전 로직 버그 수정)
            val lastHashIndex = textUpToCursor.lastIndexOf('#')

            if (lastHashIndex == -1) {
                Pair(false, "") // '#' 없음
            } else {
                // '#' 뒤에 띄어쓰기가 있는지 확인
                val potentialTag = textUpToCursor.substring(lastHashIndex)
                if (" " in potentialTag) {
                    Pair(false, "") // '#' 뒤에 띄어쓰기 있음
                } else {
                    Pair(true, potentialTag.substring(1)) // '#abc' -> "abc"
                }
            }
        }
    }

    // [수정됨] 태그 추천 목록 필터링
    val tagSuggestions by remember(isTagSearch, tagQuery, allTags) {
        derivedStateOf {
            if (isTagSearch) {
//                val currentChipTagIds = contentItems
//                    .filterIsInstance<SearchContentElement.Chip>()
//                    .map { it.tag.tagId }
//                    .toSet()

                allTags.filter {
                    it.tagName.contains(tagQuery, ignoreCase = true)
//                            && it.tagId !in currentChipTagIds
                }
            } else {
                emptyList()
            }
        }
    }

    // [수정됨] 검색 실행 로직
    val performSearch = {
        focusManager.clearFocus()
        val finalQuery = contentItems.joinToString(separator = "") {
            when (it) {
                // [수정] Text 요소의 순수 text 값 사용
                is SearchContentElement.Text -> it.text
                is SearchContentElement.Chip -> "{${it.tag.tagName}} "
            }
        }.trim().replace(Regex("\\s+"), " ")

        if (finalQuery.isNotEmpty()) {
            navController.navigate(Screen.SearchResult.createRoute(finalQuery))
        }
    }
    // --- [검색창 상태 관리 끝] ---

//    LaunchedEffect(contentItems.size) {
//        focusRequester.requestFocus() // 칩 추가 시 키보드 유지
//    }
//
//    // 자동 스크롤 유지
//    LaunchedEffect(currentInput.text, contentItems.size) {
//        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) // 입력 따라가기
//    }

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
                val msg = (logoutState as LogoutState.Error).message ?: "Logout failed"
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
            else -> Unit
        }
    }

    // done once when got permission
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            homeViewModel.loadServerTags()
            homeViewModel.loadAllPhotos() // 서버에서 모든 사진 가져오기

            val hasAlreadyUploaded = sharedPreferences.getBoolean("INITIAL_UPLOAD_COMPLETED_112", false)
            if (!hasAlreadyUploaded) {
                // photoViewModel.uploadPhotos() // <--- 초기 자동 업로드 비활성화 (LocalGallery에서 수동)
                // sharedPreferences.edit().putBoolean("INITIAL_UPLOAD_COMPLETED_112", true).apply()
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
                    // (기존 로직 유지)
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
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
            // (기존 로직 유지)
            if (showAllPhotos && groupedPhotos.isNotEmpty()) {
                CreateTagButton(
                    modifier = Modifier.padding(start = 32.dp, bottom = 16.dp),
                    text = if (isSelectionMode && selectedPhotos.isNotEmpty()) "Create with ${selectedPhotos.size}" else "Create Tag",
                    onClick = {
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
                        .padding(horizontal = 16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusManager.clearFocus() // 키보드를 내리고 모든 포커스 해제
                        },
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // --- [검색창 UI 수정됨] ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // [수정] ChipSearchBar 하나로 교체
                    ChipSearchBar(
                        modifier = Modifier
                            .weight(1f)
                            .onSizeChanged { intSize ->
                                searchBarWidth = intSize.width
                            },
                        listState = listState,
                        isFocused = (focusedElementId != null),
                        hideCursor = hideCursor,

                        // 1. 상태 전달
                        contentItems = contentItems,
                        textStates = textStates,
                        focusRequesters = focusRequesters,
                        bringIntoViewRequesters = bringIntoViewRequesters,

                        // 2. 검색 실행 로직 전달
                        onSearch = { performSearch() },
                        // [수정] 3. 검색창 배경 클릭: 마지막 텍스트 ID로 'focusedElementId' 설정
                        onContainerClick = {
                            val lastTextElement = contentItems.lastOrNull { it is SearchContentElement.Text }
                            if (lastTextElement != null) {
                                // [신규] 1. 포커스를 주기 전, 커서 위치를 맨 뒤로 설정
                                val currentTfv = textStates[lastTextElement.id]
                                if (currentTfv != null) {
                                    val end = currentTfv.text.length
                                    textStates[lastTextElement.id] = currentTfv.copy(selection = TextRange(end))
                                }
                                // [유지] 2. 포커스 ID 설정 (LaunchedEffect가 실행됨)
                                focusedElementId = lastTextElement.id
                            }
                        },
                        // [수정] 칩 클릭: 칩 뒤의 텍스트 ID로 'focusedElementId' 설정
                        onChipClick = { index ->
                            val nextTextId = findNextTextElementId(index)
                            if (nextTextId != null) {
                                // [신규] 1. 포커스를 주기 전, 커서 위치를 맨 앞으로 설정 (ZWSP 뒤)
                                val currentTfv = textStates[nextTextId]
                                if (currentTfv != null) {
                                    textStates[nextTextId] = currentTfv.copy(selection = TextRange(1))
                                }
                                // [유지] 2. 포커스 ID 설정 (LaunchedEffect가 실행됨)
                                focusedElementId = nextTextId
                            }
                        },
                        onFocus = { id ->
                            focusedElementId = id
                        },
                        onTextChange = { id, newValue ->
                            // [수정] 스크롤 로직은 그대로 둠
                            scope.launch {
                                bringIntoViewRequesters[id]?.bringIntoView()
                            }

                            val oldValue = textStates[id] ?: TextFieldValue() // 이전 TextFieldValue
                            val oldText = oldValue.text
                            val newText = newValue.text

                            // [신규] 2. 한글 등 IME 조합 중인지 확인
                            val isComposing = newValue.composition != null

                            // [신규] 3. 조합 중일 때는 UI 상태만 업데이트하고,
                            // ZWSP/백스페이스/데이터 동기화 로직은 실행하지 않음
                            if (isComposing) {
                                textStates[id] = newValue // UI만 갱신
                                return@ChipSearchBar
                            }

                            // [수정] ZWSP가 삭제되었는지(커서가 1이었는지) 감지
                            val didBackspaceAtStart = oldText.startsWith("\u200B") &&
                                    !newText.startsWith("\u200B") &&
                                    oldValue.selection.start == 1

                            if (didBackspaceAtStart) {
                                // --- 백스페이스 로직 (병합 기능 추가) ---
                                val currentIndex = contentItems.indexOfFirst { it.id == id }
                                val currentItem =
                                    contentItems[currentIndex] as SearchContentElement.Text

                                if (currentIndex <= 0) { // 첫 번째 요소
                                    textStates[id] = TextFieldValue("\u200B", TextRange(1)) // 리셋
                                    return@ChipSearchBar
                                }

                                val prevItem = contentItems[currentIndex - 1]

                                // 1a. 바로 앞이 칩인 경우 (e.g., [TextA] [ChipB] [TextC(현재)])
                                if (prevItem is SearchContentElement.Chip) {
                                    val prevPrevIndex = currentIndex - 2

                                    // 1a-1. [TextA] [ChipB] [TextC] -> [TextA + TextC]
                                    if (prevPrevIndex >= 0 && contentItems[prevPrevIndex] is SearchContentElement.Text) {
                                        val textA =
                                            contentItems[prevPrevIndex] as SearchContentElement.Text
                                        val textC = currentItem
                                        val mergedText = textA.text + textC.text // A와 C의 텍스트 병합

                                        // ChipB(index-1)와 TextC(index) 제거
                                        contentItems.removeAt(currentIndex)
                                        contentItems.removeAt(currentIndex - 1)
                                        textStates.remove(id)
                                        focusRequesters.remove(id)
                                        bringIntoViewRequesters.remove(id)

                                        // TextA(index-2) 업데이트
                                        contentItems[prevPrevIndex] = textA.copy(text = mergedText)
                                        val newTfv = TextFieldValue(
                                            "\u200B" + mergedText,
                                            TextRange(textA.text.length + 1)
                                        )
                                        textStates[textA.id] = newTfv

                                        // TextA로 포커스 이동
                                        requestFocusById(textA.id)
                                    }
                                    // 1a-2. [ChipA] [ChipB] [TextC] 또는 [Start] [ChipB] [TextC] -> [ChipA] [TextC]
                                    else {
                                        // ChipB(index-1)만 제거 (TextC는 남김)
                                        contentItems.removeAt(currentIndex - 1)
                                        // TextC로 포커스 유지 (ID는 동일)
                                        requestFocusById(id)
                                    }
                                }
                                // 1b. 바로 앞이 텍스트인 경우 (e.g., [TextA] [TextC(현재)])
                                else if (prevItem is SearchContentElement.Text) {
                                    val textA = prevItem
                                    val textC = currentItem
                                    val mergedText = textA.text + textC.text

                                    // TextC(index) 제거
                                    contentItems.removeAt(currentIndex)
                                    textStates.remove(id)
                                    focusRequesters.remove(id)
                                    bringIntoViewRequesters.remove(id)

                                    // TextA(index-1) 업데이트
                                    contentItems[currentIndex - 1] = textA.copy(text = mergedText)
                                    val newTfv = TextFieldValue(
                                        "\u200B" + mergedText,
                                        TextRange(textA.text.length + 1)
                                    )
                                    textStates[textA.id] = newTfv

                                    // TextA로 포커스 이동
                                    requestFocusById(textA.id)
                                }
                                return@ChipSearchBar
                            }

                            // --- ZWSP 및 커서 위치 강제 로직 ---
                            val (text, selection) = if (newText.startsWith("\u200B")) {
                                Pair(newText, newValue.selection)
                            } else {
                                Pair("\u200B$newText", TextRange(newValue.selection.start + 1, newValue.selection.end + 1))
                            }
                            val finalSelection = if (selection.start == 0 && selection.end == 0) {
                                TextRange(1)
                            } else {
                                selection
                            }
                            val finalValue = TextFieldValue(text, finalSelection)

                            // --- 상태 동기화 로직 ---
                            textStates[id] = finalValue
                            val currentItemIndex = contentItems.indexOfFirst { it.id == id }
                            if (currentItemIndex != -1) {
                                contentItems[currentItemIndex] = (contentItems[currentItemIndex] as SearchContentElement.Text).copy(
                                    text = text.removePrefix("\u200B")
                                )
                            }
                        }
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
                                    color = MaterialTheme.colorScheme.primary, // 이미지와 다른 색상 (primary)
                                    shape = RoundedCornerShape(12.dp),
                                ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList, // 필터 아이콘
                            contentDescription = "Filter",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                // [수정됨] 태그 추천 목록 (LazyRow)
                if (tagSuggestions.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable( // [신규] 1. 이 클릭 리스너가
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { } // 2. 탭 이벤트를 "소비"하여 배경으로 전파되는 것을 막음
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(tagSuggestions, key = { it.tagId }) { tag ->
                            SuggestionChip(
                                tag = tag,
                                onClick = {
                                    // [신규] 텍스트 필드 분할 로직
                                    if (focusedElementId == null) return@SuggestionChip

                                    val currentId = focusedElementId!!
                                    val currentIndex = contentItems.indexOfFirst { it.id == currentId }
                                    val currentInput = textStates[currentId] ?: return@SuggestionChip

                                    val text = currentInput.text
                                    val cursor = currentInput.selection.start
                                    val textUpToCursor = text.substring(0, cursor)
                                    val lastHashIndex = textUpToCursor.lastIndexOf('#')

                                    if (lastHashIndex != -1) {
                                        // 1. 텍스트 분리
                                        val precedingText = text.substring(0, lastHashIndex).removePrefix("\u200B")
                                        val succeedingText = text.substring(cursor)

                                        // 2. 새 칩과 새 텍스트 필드 생성
                                        val newChipId = UUID.randomUUID().toString()
                                        val newChip = SearchContentElement.Chip(newChipId, tag)

                                        val newTextId = UUID.randomUUID().toString()
                                        val newText = SearchContentElement.Text(newTextId, succeedingText)

                                        // 3. 현재 텍스트 필드 업데이트
                                        contentItems[currentIndex] = (contentItems[currentIndex] as SearchContentElement.Text).copy(text = precedingText)
                                        textStates[currentId] = TextFieldValue("\u200B" + precedingText, TextRange(precedingText.length + 1))

                                        // 4. 새 칩과 새 텍스트 필드 삽입
                                        contentItems.add(currentIndex + 1, newChip)
                                        contentItems.add(currentIndex + 2, newText)

                                        // 5. 새 텍스트 필드 상태 및 포커스 설정
                                        textStates[newTextId] = TextFieldValue("\u200B" + succeedingText, TextRange(1))
                                        focusRequesters[newTextId] = FocusRequester()
                                        bringIntoViewRequesters[newTextId] = BringIntoViewRequester()

                                        // 6. [수정] "포커스 의도"만 상태에 반영
                                        focusedElementId = newTextId
                                    }
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
                    // (기존 로직 유지)
                    if (!showAllPhotos) {
                        IconButton(onClick = { scope.launch { sheetState.show() } }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort Tag Albums",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
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

                // --- [이하 모든 UI 로직은 기존과 동일] ---
                if (!hasPermission) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("태그와 이미지를 보려면\n이미지 접근 권한을 허용해주세요.")
                    }
                } else if ((isLoadingPhotos || homeLoadingState is HomeViewModel.HomeLoadingState.Loading) && groupedPhotos.isEmpty()) {
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
                            if (!isLoadingMorePhotos) {
                                snapshotFlow {
                                    listState.layoutInfo.visibleItemsInfo
                                        .lastOrNull()
                                        ?.index
                                }.distinctUntilChanged()
                                    .debounce(150)
                                    .collect { lastVisibleIndex ->
                                        val totalItemCount = listState.layoutInfo.totalItemsCount
                                        if (lastVisibleIndex != null && totalItemCount > 0) {
                                            val remainingItems =
                                                totalItemCount - (lastVisibleIndex + 1)
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
                            )
                            .clickable { onToggle(false, false) }
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
                            )
                            .clickable { onToggle(false, true) }
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
    val arePhotosLoaded = !isLoadingPhotos || groupedPhotos.isNotEmpty()
    val isDataReady = isTagsLoaded && arePhotosLoaded

    val arePhotosEmpty = groupedPhotos.isEmpty()
    val areTagsEmpty = tagItems.isEmpty()

    when {
        isDataReady && arePhotosEmpty -> {
            EmptyStatePhotos(modifier = modifier, navController = navController)
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

                    if (isLoadingMorePhotos) {
                        item(span = { GridItemSpan(3) }) {
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
        !showAllPhotos && !arePhotosEmpty -> {
            if (areTagsEmpty && isDataReady) {
                EmptyStateTags(navController = navController, modifier = modifier)
            } else {
                if (onlyTag) {
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
                        )
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
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
            modifier = Modifier
                .size(120.dp)
                .rotate(45f),
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

@Composable
private fun SuggestionChip(
    tag: TagItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .clip(CircleShape)
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 6.dp), // SearchChipView와 유사한 패딩
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "#${tag.tagName}",
            fontSize = 15.sp, // SearchChipView와 유사한 폰트 크기
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}