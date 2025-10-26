package com.example.momentag

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.Photo
import com.example.momentag.model.RecommendState
import com.example.momentag.model.TagAlbum
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.Semi_background
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.AddTagViewModel
import com.example.momentag.viewmodel.RecommendViewModel
import com.example.momentag.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagScreen(navController: NavController) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var isChanged by remember { mutableStateOf(true) }

    // Screen-scoped ViewModels using DraftTagRepository
    val addTagViewModel: AddTagViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val recommendViewModel: RecommendViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    val tagName by addTagViewModel.tagName.collectAsState()
    val selectedPhotos by addTagViewModel.selectedPhotos.collectAsState()
    val recommendState by recommendViewModel.recommendState.collectAsState()

    val recommendedPhotos = remember { mutableStateListOf<Photo>() }

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

    // Handle back button - clear draft when leaving workflow
    BackHandler {
        addTagViewModel.clearDraft()
        navController.popBackStack()
    }

    LaunchedEffect(recommendState) {
        if (recommendState is RecommendState.Success) {
            val successState = recommendState as RecommendState.Success
            recommendedPhotos.clear()
            // Filter out photos that are already selected
            val selectedPhotoIds = selectedPhotos.map { it.photoId }.toSet()
            val newRecommended = successState.photos.filter { it.photoId !in selectedPhotoIds }
            recommendedPhotos.addAll(newRecommended)
        }
    }

    val onDeselectPhoto: (Photo) -> Unit = { photo ->
        isChanged = true
        addTagViewModel.removePhoto(photo)
        recommendedPhotos.add(photo)
    }

    val onSelectPhoto: (Photo) -> Unit = { photo ->
        isChanged = true
        addTagViewModel.addPhoto(photo)
        recommendedPhotos.remove(photo)
        // Extract photoIds for TagAlbum
        val photoIds = (selectedPhotos + photo).map { it.photoId }
        val tagAlbum = TagAlbum(tagName, photoIds)
        recommendViewModel.recommend(tagAlbum)
    }

    Scaffold(
        topBar = {
            BackTopBar(
                title = "MomenTag",
                onBackClick = {
                    addTagViewModel.clearDraft()
                    navController.popBackStack()
                },
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
                    .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                TagNameSection(
                    tagName = tagName,
                    onTagNameChange = { addTagViewModel.updateTagName(it) },
                )

                Spacer(modifier = Modifier.height(41.dp))

                SelectPicturesButton(onClick = { navController.navigate(Screen.SelectImage.route) })
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (hasPermission) {
                SelectedPhotosSection(
                    photos = selectedPhotos,
                    onPhotoClick = onDeselectPhoto,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (hasPermission) {
                Column(
                    modifier =
                        Modifier
                            .padding(horizontal = 24.dp)
                            .weight(1f),
                ) {
                    RecommendedPicturesSection(
                        photos = recommendedPhotos,
                        onPhotoClick = onSelectPhoto,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (isChanged) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                ) {
                    Button(
                        onClick = {
                            // TODO: Save tag to backend
                            // Clear draft when workflow completes
                            addTagViewModel.clearDraft()
                            navController.popBackStack()
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
}

@Composable
private fun TagNameSection(
    tagName: String,
    onTagNameChange: (String) -> Unit,
) {
    Column {
        Text(
            text = "New tag name",
            fontSize = 21.sp,
            fontFamily = FontFamily.Serif,
            color = Word,
        )
        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = tagName,
            onValueChange = onTagNameChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 21.sp),
            placeholder = { Text("태그 입력") },
            leadingIcon = { Text("#", fontSize = 21.sp, color = Word) },
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background,
                    focusedIndicatorColor = Word,
                    unfocusedIndicatorColor = Temp_word,
                ),
            singleLine = true,
        )
    }
}

@Composable
private fun SelectPicturesButton(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Select Pictures",
            modifier =
                Modifier
                    .clip(CircleShape)
                    .background(Button)
                    .padding(2.dp),
            tint = Word,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Select Pictures",
            fontSize = 21.sp,
            fontFamily = FontFamily.Serif,
            color = Word,
        )
    }
}

@Composable
private fun SelectedPhotosSection(
    photos: List<com.example.momentag.model.Photo>,
    onPhotoClick: (com.example.momentag.model.Photo) -> Unit,
) {
    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Semi_background),
        horizontalArrangement = Arrangement.spacedBy(21.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(photos) { photo ->
            PhotoCheckedItem(
                photo = photo,
                isSelected = true,
                onClick = { onPhotoClick(photo) },
                modifier = Modifier.aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun RecommendedPicturesSection(
    photos: List<com.example.momentag.model.Photo>,
    onPhotoClick: (com.example.momentag.model.Photo) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Recommended Pictures",
            fontSize = 21.sp,
            fontFamily = FontFamily.Serif,
            color = Word,
        )
        Spacer(modifier = Modifier.height(11.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(21.dp),
            modifier = Modifier.height(193.dp),
        ) {
            items(photos) { photo ->
                PhotoCheckedItem(
                    photo = photo,
                    isSelected = false,
                    onClick = { onPhotoClick(photo) },
                    modifier = Modifier.aspectRatio(1f),
                )
            }
        }
    }
}

@Composable
fun PhotoCheckedItem(
    photo: com.example.momentag.model.Photo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Semi_background)
                .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.contentUri,
            contentDescription = "사진 ${photo.photoId}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        CheckboxOverlay(
            isSelected = isSelected,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

@Composable
private fun CheckboxOverlay(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(8.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Word,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
