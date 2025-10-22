package com.example.momentag

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.NavController
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.Picture
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.PhotoTagViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectImageScreen(
    navController: NavController,
    viewModel: PhotoTagViewModel,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    // TODO: GET /api/photos/
    val allPhotos: List<Long> = emptyList()

    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                hasPermission = true
            }
        },
    )

    var selectedPhotos = remember { mutableStateListOf<Long>() }
    val tagName = viewModel.tagName
    val initSelectedPhotos = viewModel.initSelectedPhotos.toList()

    LaunchedEffect(initSelectedPhotos) {
        selectedPhotos.clear()
        selectedPhotos.addAll(initSelectedPhotos)
    }

    val onPhotoClick: (Long) -> Unit = { photoId ->
        if (selectedPhotos.contains(photoId)) {
            selectedPhotos.remove(photoId)
        } else {
            selectedPhotos.add(photoId)
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "MomenTag",
                onBackClick = { navController.popBackStack() },
                modifier = Modifier.background(Background),
            )
        },
        containerColor = Background,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "#$tagName",
                fontSize = 21.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Word,
            )
            HorizontalDivider(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                color = Word,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose more than 5 pictures",
                fontSize = 21.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Word,
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (hasPermission) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(453.dp),
                ) {
                    items(allPhotos) { photoId ->
                        val isSelected = selectedPhotos.contains(photoId)
                        PhotoCheckedItem(
                            photoId = photoId,
                            isSelected = isSelected,
                            onClick = { onPhotoClick(photoId) },
                            modifier = Modifier.aspectRatio(1f),
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(21.dp),
                    modifier = Modifier.height(453.dp),
                ) {
                    items(allPhotos) { _ ->
                        Box(modifier = Modifier) {
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
                    }
                }
            }

            Column {
                HorizontalDivider(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    color = Word,
                )
                Button(
                    onClick = {
                        viewModel.updateSelectedPhotos(selectedPhotos)
                        navController.navigate(Screen.AddTag.route) {
                            popUpTo(Screen.AddTag.route) { inclusive = true }
                        }
                    },
                    shape = RoundedCornerShape(15.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Button,
                            contentColor = Color.White,
                        ),
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                ) {
                    Text(text = "Done")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
