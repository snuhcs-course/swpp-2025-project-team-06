package com.example.momentag

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.ui.theme.Background
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.PhotoViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAlbumScreen(
    navController: NavController,
    albumId: Long,
    albumName: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val localViewModel: LocalViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val photoViewModel: PhotoViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    var hasPermission by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val photos by localViewModel.imagesInAlbum.collectAsState()

    val selectedPhotos by localViewModel.selectedPhotosInAlbum.collectAsState()
    var isSelectionMode by remember { mutableStateOf(false) }
    val uploadState by photoViewModel.uiState.collectAsState()

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        localViewModel.clearPhotoSelection()
    }

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
        LaunchedEffect(albumId) {
            localViewModel.getImagesForAlbum(albumId)
        }
    }

    // Set browsing session when album images are loaded
    LaunchedEffect(photos, albumName) {
        if (photos.isNotEmpty()) {
            localViewModel.setLocalAlbumBrowsingSession(photos, albumName)
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

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MomenTag",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            localViewModel.clearPhotoSelection()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Selection",
                            )
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Background,
                    ),
            )
        },
        floatingActionButton = {
            if (selectedPhotos.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = {
                        if (uploadState.isLoading) {
                            Text("업로드 시작됨 (알림 확인)")
                        } else {
                            Text("선택한 ${selectedPhotos.size}개 사진 업로드하기")
                        }
                    },
                    icon = {
                        if (uploadState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        if (uploadState.isLoading) return@ExtendedFloatingActionButton

                        // 👇 [변경] 앨범 ID 대신 선택된 사진 객체들(Set<Photo>)을 전달
                        photoViewModel.uploadSelectedPhotos(selectedPhotos, context)

                        // 업로드 후 선택 모드 해제
                        isSelectionMode = false
                        localViewModel.clearPhotoSelection()
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
                            localViewModel.getImagesForAlbum(albumId)
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
                    text = albumName,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Serif,
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
                    items(
                        count = photos.size,
                        key = { index -> photos[index].photoId },
                    ) { index ->
                        val photo = photos[index]
                        val isSelected = selectedPhotos.any { it.photoId == photo.photoId }

                        Box(
                            modifier =
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp)) // 모서리 둥글게
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                localViewModel.togglePhotoSelection(photo)
                                            } else {
                                                // 기존 로직: 이미지 상세 보기
                                                localViewModel.setLocalAlbumBrowsingSession(photos, albumName)
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
                                                isSelectionMode = true
                                                localViewModel.togglePhotoSelection(photo)
                                            }
                                        },
                                    ),
                        ) {
                            AsyncImage(
                                model = photo.contentUri,
                                contentDescription = "Photo ${photo.photoId}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )

                            if (isSelectionMode) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(
                                                if (isSelected) {
                                                    Color.Black.copy(alpha = 0.3f)
                                                } else {
                                                    Color.Transparent
                                                },
                                            ),
                                )

                                // 👇 체크박스 (HomeScreen.kt 참고)
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(
                                                if (isSelected) {
                                                    Color(0xFFFBC4AB)
                                                } else {
                                                    Color.White.copy(alpha = 0.8f)
                                                },
                                                CircleShape,
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
