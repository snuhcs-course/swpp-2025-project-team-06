package com.example.momentag

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.viewmodel.LocalViewModel
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
    var hasPermission by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val imageUris by localViewModel.imagesInAlbum.collectAsState()

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
    LaunchedEffect(imageUris, albumName) {
        if (imageUris.isNotEmpty()) {
            localViewModel.setLocalAlbumBrowsingSession(imageUris, albumName)
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
                // Convert Uri list to Photo list (local images have empty photoId)
                val photos =
                    imageUris.map { uri ->
                        com.example.momentag.model.Photo(
                            photoId = "", // Local images don't have backend photoId
                            contentUri = uri,
                        )
                    }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(photos) { photo ->
                        ImageGridUriItem(
                            photo = photo,
                            navController = navController,
                        )
                    }
                }
            }
        }
    }
}
