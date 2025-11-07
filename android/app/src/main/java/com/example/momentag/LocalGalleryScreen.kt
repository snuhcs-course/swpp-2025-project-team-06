package com.example.momentag

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Picture
import com.example.momentag.ui.theme.Pretendard
import com.example.momentag.ui.theme.TagColor
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalGalleryScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val localViewModel: LocalViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val photoViewModel: PhotoViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    var hasPermission by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val selectedAlbumIds by localViewModel.selectedAlbumIds.collectAsState()
    val uploadState by photoViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    // 👇 [수정] 'selectedAlbumId?.let' (단수) -> 'selectedAlbumIds.isNotEmpty' (복수)
                    if (selectedAlbumIds.isNotEmpty()) {
                        // 👇 [수정] 'uploadPhotosForAlbum(it, ...)' -> 'uploadPhotosForAlbums(selectedAlbumIds, ...)'
                        photoViewModel.uploadPhotosForAlbums(selectedAlbumIds, context)
                    }
                } else {
                    // (권한 거부 시 토스트 메시지 등)
                }
            },
        )

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    if (hasPermission) {
        LaunchedEffect(Unit) {
            localViewModel.getAlbums()
        }
    }

    LaunchedEffect(key1 = true) {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        permissionLauncher.launch(permission)
    }

    val albumSet by localViewModel.albums.collectAsState()

    Scaffold(
        containerColor = Background,
        topBar = {
            BackTopBar(
                title = "MomenTag",
                onBackClick = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // 👇 [수정] 2: floatingActionButton 추가
        floatingActionButton = {
            // 앨범이 선택됐을 때만 FAB 보이기
            if (selectedAlbumIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = {
                        if (uploadState.isLoading) {
                            Text("업로드 시작됨 (알림 확인)")
                        } else {
                            // 👇 [수정] 3: 선택된 개수 표시
                            Text("선택한 ${selectedAlbumIds.size}개 앨범 업로드하기")
                        }
                    },
                    icon = {
                        // ... (로딩 스피너 로직은 동일)
                    },
                    onClick = {
                        if (uploadState.isLoading) return@ExtendedFloatingActionButton

                        // 👇 [수정] 4: 'selectedAlbumId' (단수) -> 'selectedAlbumIds' (복수) 전달
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // [수정]
                            photoViewModel.uploadPhotosForAlbums(selectedAlbumIds, context)
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    try {
                        if (hasPermission) {
                            localViewModel.getAlbums()
                        }
                    } finally {
                        isRefreshing = false
                    }
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Albums",
                    fontSize = 28.sp,
                    fontFamily = Pretendard,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albumSet) { album ->
                        albumGridItem(
                            albumName = album.albumName,
                            albumId = album.albumId,
                            imageUri = album.thumbnailUri,
                            isSelected = (album.albumId in selectedAlbumIds),
                            onClick = {
                                navController.navigate(
                                    Screen.LocalAlbum.createRoute(album.albumId, album.albumName),
                                )
                            },
                            onLongClick = {
                                localViewModel.toggleAlbumSelection(album.albumId)
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                uploadState.userMessage?.let { message ->
                    LaunchedEffect(uploadState.userMessage) {
                        scope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                        photoViewModel.userMessageShown()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun albumGridItem(
    albumName: String,
    albumId: Long,
    imageUri: Uri?,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .then(
                    if (isSelected) {
                        Modifier.border(
                            BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                            RoundedCornerShape(16.dp),
                        )
                    } else {
                        Modifier
                    },
                ).combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = albumName,
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .align(Alignment.BottomCenter),
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
                        ).align(Alignment.BottomCenter),
            )
        }

        Text(
            text = albumName,
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
