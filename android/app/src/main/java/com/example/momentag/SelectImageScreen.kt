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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.search.components.SearchLoadingStateCustom
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.Picture
import com.example.momentag.ui.theme.Pretendard
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.SelectImageViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SelectImageScreen(navController: NavController) {
    var hasPermission by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val selectImageViewModel: SelectImageViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val allPhotos by selectImageViewModel.allPhotos.collectAsState()
    val tagName by selectImageViewModel.tagName.collectAsState()
    val selectedPhotos by selectImageViewModel.selectedPhotos.collectAsState()
    val isLoading by selectImageViewModel.isLoading.collectAsState()
    val isLoadingMore by selectImageViewModel.isLoadingMore.collectAsState()

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

    val onPhotoClick: (Photo) -> Unit = { photo ->
        if (isSelectionMode) {
            selectImageViewModel.togglePhoto(photo)
        } else {
            selectImageViewModel.setGalleryBrowsingSession()
            navController.navigate(
                Screen.Image.createRoute(
                    uri = photo.contentUri,
                    imageId = photo.photoId,
                ),
            )
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            selectImageViewModel.getAllPhotos()
        }
    }

    // LazyGrid state for pagination
    val listState = rememberLazyGridState()

    // Scroll detection for pagination
    LaunchedEffect(listState, isLoadingMore) {
        if (!isLoadingMore) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index
            }.distinctUntilChanged()
                .debounce(150)
                .collect { lastVisibleIndex ->
                    if (lastVisibleIndex != null && allPhotos.isNotEmpty()) {
                        val totalItems = allPhotos.size
                        val remainingItems = totalItems - (lastVisibleIndex + 1)

                        if (remainingItems < 33) {
                            selectImageViewModel.loadMorePhotos()
                        }
                    }
                }
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
                fontFamily = Pretendard,
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

            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Choose more than 5 pictures",
                    fontSize = 21.sp,
                    fontFamily = Pretendard,
                    fontWeight = FontWeight.Bold,
                    color = Word,
                    modifier = Modifier.align(Alignment.CenterStart),
                )

                IconButton(
                    onClick = { isSelectionMode = !isSelectionMode },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = if (isSelectionMode) "Exit Selection Mode" else "Enter Selection Mode",
                        tint = if (isSelectionMode) Button else Word,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                SearchLoadingStateCustom(
                    onRefresh = { selectImageViewModel.getAllPhotos() },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(453.dp),
                )
            } else if (hasPermission) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(453.dp),
                ) {
                    items(
                        count = allPhotos.size,
                        key = { index -> allPhotos[index].photoId },
                    ) { index ->
                        val photo = allPhotos[index]
                        val isSelected = selectedPhotos.any { it.photoId == photo.photoId }

                        Box(
                            modifier =
                                Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPhotoClick(photo) },
                        ) {
                            AsyncImage(
                                model = photo.contentUri,
                                contentDescription = "Photo ${photo.photoId}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )

                            if (isSelectionMode && isSelected) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f)),
                                )
                            }

                            if (isSelectionMode) {
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Loading indicator
                    if (isLoadingMore) {
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
                                    color = Word,
                                )
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(21.dp),
                    modifier = Modifier.height(453.dp),
                ) {
                    items(allPhotos.size) { _ ->
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
