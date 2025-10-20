package com.example.momentag

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.ImageContext
import com.example.momentag.model.Photo
import com.example.momentag.model.RecommendState
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Picture
import com.example.momentag.ui.theme.Word
import com.example.momentag.ui.theme.Button
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectImageScreen(
    navController: NavController,
    tagName: String,
    initSelectedPhotos: List<Photo>,
    onDone: (String, List<Photo>, NavController) -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    /* TODO: GET /api/photos/ */
    val allPhotos : List<Photo> = emptyList();

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    LaunchedEffect(key1 = true) {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        permissionLauncher.launch(permission)
    }

    var selectedPhotos = remember { mutableStateListOf<Photo>() }

    LaunchedEffect(initSelectedPhotos) {
        selectedPhotos.clear()
        selectedPhotos.addAll(initSelectedPhotos)
    }

    val onPhotoClick: (Photo) -> Unit = { photo ->
        if (selectedPhotos.contains(photo)) {
            selectedPhotos.remove(photo)
        } else {
            selectedPhotos.add(photo)
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "MomenTag",
                onBackClick = { navController.popBackStack() },
                modifier = Modifier.background(Background)
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. 태그 이름 표시
            Text(
                text = "#$tagName",
                fontSize = 20.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Word,
            )
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                color = Word,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose more than 5 pictures",
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Word,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 2. 사진 그리드
            if (hasPermission) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(allPhotos, key = { it.photoId }) { photo ->
                        val isSelected = selectedPhotos.contains(photo)
                        PhotoCheckedItem(
                            photo = photo,
                            isSelected = isSelected,
                            onClick = { onPhotoClick(photo) },
                            modifier = Modifier.aspectRatio(1f),
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(allPhotos, key = { it.photoId }) { _ ->
                        Box(modifier = Modifier) {
                            Spacer(
                                modifier =
                                    Modifier
                                        .padding(top = 12.dp)
                                        .aspectRatio(1f)
                                        .background(
                                            color = Picture,
                                            shape = RoundedCornerShape(16.dp),
                                        ).align(Alignment.BottomCenter)
                            )
                        }
                    }
                }
            }

            // 3. 하단 'Done' 버튼
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    color = Word,
                )
                Button(
                    onClick = {
                        onDone(tagName, selectedPhotos.toList(), navController)
                        navController.popBackStack()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Button,
                        contentColor = Color.White
                    ),
                ) {
                    Text(text = "Done", modifier = Modifier.padding(horizontal = 24.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
