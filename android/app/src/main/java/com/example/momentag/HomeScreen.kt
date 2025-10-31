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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.LogoutState
import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CreateTagButton
import com.example.momentag.ui.components.HomeTopBar
import com.example.momentag.ui.components.SearchBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Picture
import com.example.momentag.ui.theme.Semi_background
import com.example.momentag.ui.theme.TagColor
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.AuthViewModel
import com.example.momentag.viewmodel.HomeViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("MomenTagPrefs", Context.MODE_PRIVATE) }
    var hasPermission by remember { mutableStateOf(false) }
    val authViewModel: AuthViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val logoutState by authViewModel.logoutState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val photoViewModel: PhotoViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val homeViewModel: HomeViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val homeLoadingState by homeViewModel.homeLoadingState.collectAsState()
    val uiState by photoViewModel.uiState.collectAsState()
    var currentTab by remember { mutableStateOf(BottomTab.HomeScreen) }

    var onlyTag by remember { mutableStateOf(false) }

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
            homeViewModel.loadServerTags()

            val hasAlreadyUploaded = sharedPreferences.getBoolean("INITIAL_UPLOAD_COMPLETED", false)
            if (!hasAlreadyUploaded) {
                photoViewModel.uploadPhotos()
                sharedPreferences.edit().putBoolean("INITIAL_UPLOAD_COMPLETED", true).apply()
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

    Scaffold(
        topBar = {
            HomeTopBar(
                onTitleClick = {
                    navController.navigate(Screen.LocalGallery.route)
                },
                onLogoutClick = { authViewModel.logout() },
                isLogoutLoading = logoutState is LogoutState.Loading,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            // 이미 홈이면 유지. 필요하면 navController.navigate(Screen.Home.route)
                        }
                        BottomTab.SearchScreen -> {
                            // 예: 검색 화면으로 이동
                            navController.navigate(Screen.SearchResult.route)
                        }
                        BottomTab.TagScreen -> {
                            // 예: 태그 생성 / 업로드 등
                            navController.navigate(Screen.Album.route)
                            // TODO : 여기도 Tag 화면으로 이동
                        }
                        BottomTab.StoryScreen -> {
                            // ✅ 여기서 스토리 화면으로 이동하면 돼
                            navController.navigate(Screen.Story.route)
                        }
                    }
                },
            )
        },
        containerColor = Background,
        floatingActionButton = {
            CreateTagButton(
                modifier = Modifier.padding(start = 32.dp, bottom = 16.dp),
                text = "Create Tag",
                onClick = {
                    navController.navigate(Screen.AddTag.route)
                },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = homeLoadingState is HomeViewModel.HomeLoadingState.Loading,
            onRefresh = {
                if (hasPermission) {
                    homeViewModel.loadServerTags()
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
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                SearchHeader()

                Spacer(modifier = Modifier.height(8.dp))
                SearchBar(
                    onSearch = { query ->
                        if (query.isNotEmpty()) {
                            navController.navigate(Screen.SearchResult.createRoute(query))
                        }
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))
                ViewToggle(
                    onlyTag = onlyTag,
                    onToggle = { onlyTag = it },
                )

                Spacer(modifier = Modifier.height(16.dp))
                if (!hasPermission) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("태그와 이미지를 보려면\n이미지 접근 권한을 허용해주세요.")
                    }
                } else {
                    when (homeLoadingState) {
                        is HomeViewModel.HomeLoadingState.Success -> {
                            val tagItems = (homeLoadingState as HomeViewModel.HomeLoadingState.Success).tags
                            MainContent(
                                onlyTag = onlyTag,
                                tagItems = tagItems,
                                navController = navController,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        is HomeViewModel.HomeLoadingState.Loading -> {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        else -> { // Error, NetworkError, Idle
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("태그를 불러오지 못했습니다.\n아래로 당겨 새로고침하세요.")
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------- Helpers --------------------
@Composable
private fun SearchHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Search for Photo", fontSize = 18.sp, fontFamily = FontFamily.Serif)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Camera Icon")
    }
}

// SearchBar는 이제 ui.components.SearchBar로 이동됨

@Composable
private fun ViewToggle(
    onlyTag: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier =
                Modifier
                    .background(Semi_background, RoundedCornerShape(8.dp))
                    .padding(4.dp),
        ) {
            Row {
                Icon(
                    Icons.Default.GridView,
                    contentDescription = "Grid View",
                    tint = if (!onlyTag) Color.White else Color.Gray,
                    modifier = Modifier.clickable { onToggle(false) },
                )
                Icon(
                    Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = "List View",
                    tint = if (onlyTag) Color.White else Color.Gray,
                    modifier = Modifier.clickable { onToggle(true) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainContent(
    onlyTag: Boolean,
    tagItems: List<TagItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (!onlyTag) {
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
                )
            }
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tagItems.forEach { item ->
                tagX(
                    text = item.tagName,
                    onDismiss = { /* TODO */ },
                )
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
                        .clickable {
                            navController.navigate(Screen.Album.createRoute(tagId, tagName))
                        },
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .aspectRatio(1f)
                        .background(
                            color = Picture,
                            shape = RoundedCornerShape(16.dp),
                        ).align(Alignment.BottomCenter)
                        .clickable {
                            navController.navigate(Screen.Album.createRoute(tagId, tagName))
                        },
            )
        }
        Text(
            text = tagName,
            color = Word,
            fontSize = 12.sp,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp)
                    .background(
                        color = TagColor,
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
