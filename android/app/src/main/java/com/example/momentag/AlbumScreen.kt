package com.example.momentag

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.ImageContext
import com.example.momentag.model.ImageOfTagLoadState
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Picture
import com.example.momentag.viewmodel.ImageDetailViewModel
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.TagViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    tagName: String,
    navController: NavController,
    onNavigateBack: () -> Unit,
    imageDetailViewModel: ImageDetailViewModel,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val tagViewModel: TagViewModel = viewModel(factory = ViewModelFactory(context))

    val imageLoadState by tagViewModel.imageOfTagLoadState.collectAsState()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    LaunchedEffect(hasPermission, tagName) {
        if (hasPermission) {
            tagViewModel.loadImagesOfTag(tagName)
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
            BackTopBar(
                title = "MomenTag",
                onBackClick = onNavigateBack,
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = imageLoadState is ImageOfTagLoadState.Loading,
            onRefresh = {
                scope.launch {
                    if (hasPermission) {
                        tagViewModel.loadImagesOfTag(tagName)
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
                    text = tagName,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Serif,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                )

                if (!hasPermission) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("이미지 접근 권한을 허용해주세요.")
                    }
                } else {
                    when (imageLoadState) {
                        is ImageOfTagLoadState.Loading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        is ImageOfTagLoadState.Success -> {
                            val photosWrapper = (imageLoadState as ImageOfTagLoadState.Success).photos
                            val imageUris: List<Uri> = remember(photosWrapper) {
                                photosWrapper.photos.mapNotNull { photo ->
                                    photo.photoId?.let { id ->
                                        ContentUris.withAppendedId(
                                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                            id
                                        )
                                    }
                                }
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(imageUris) { imageUri ->
                                    ImageGridUriItem(
                                        imageUri = imageUri,
                                        navController = navController,
                                        imageDetailViewModel = imageDetailViewModel,
                                        allImages = imageUris,
                                        contextType = ImageContext.ContextType.TAG_ALBUM,
                                    )
                                }
                            }
                        }
                        is ImageOfTagLoadState.Error, is ImageOfTagLoadState.NetworkError -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("사진을 불러오지 못했습니다.\n아래로 당겨 새로고침하세요.")
                            }
                        }
                        is ImageOfTagLoadState.Idle -> {
                        }
                    }
                }
            }
        }
    }
}
