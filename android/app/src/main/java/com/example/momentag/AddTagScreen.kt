package com.example.momentag

import android.Manifest
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.momentag.model.Photo
import com.example.momentag.model.RecommendState
import com.example.momentag.model.TagAlbum
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.Picture
import com.example.momentag.ui.theme.Semi_background
import com.example.momentag.ui.theme.TagColor
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.LocalViewModel
import com.example.momentag.viewmodel.PhotoTagViewModel
import com.example.momentag.viewmodel.RecommendViewModel
import com.example.momentag.viewmodel.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTagScreen(
    viewModel: PhotoTagViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }

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

    val tagName by remember(viewModel.tagName) { mutableStateOf(viewModel.tagName) }
    val initSelectedPhotos = viewModel.initSelectedPhotos

    val recommendViewModel: RecommendViewModel = viewModel(factory = ViewModelFactory(context))

    var inputTagName by remember(tagName) {
        mutableStateOf(tagName ?: "")
    }

    var selectedPhotos = remember { mutableStateListOf<Long>() }
    var recommendedPhotos = remember { mutableStateListOf<Long>() }

    LaunchedEffect(initSelectedPhotos) {
        selectedPhotos.clear()
        selectedPhotos.addAll(initSelectedPhotos)
    }

    val onDeselectPhoto: (Long) -> Unit = { photoId ->
        selectedPhotos.remove(photoId)
    }

    val onSelectPhoto: (Long) -> Unit = { photoId ->
        recommendedPhotos.remove(photoId)
        selectedPhotos.add(photoId)
        var tagAlbum = TagAlbum(inputTagName, selectedPhotos)
        recommendViewModel.recommend(tagAlbum)
        if (recommendViewModel.recommendState.value is RecommendState.Success) {
            val successState = recommendViewModel.recommendState.value as RecommendState.Success
            recommendedPhotos.clear()
            recommendedPhotos.addAll(successState.photos)
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
        containerColor = Background,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                TagNameSection(
                    tagName = inputTagName,
                    onTagNameChange = { viewModel.updateTagName(it) },
                )

                Spacer(modifier = Modifier.height(32.dp))

                SelectPicturesButton(onClick = { navController.navigate(Screen.SelectImage.route) })
            }

            if (hasPermission) {

                SelectedPhotosSection(
                    photos = selectedPhotos,
                    onPhotoClick = onDeselectPhoto,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (hasPermission) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .weight(1f)
                ) {
                    RecommendedPicturesSection(
                        photos = recommendedPhotos,
                        onPhotoClick = onSelectPhoto,
                        modifier = Modifier.fillMaxSize()
                    )
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
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = tagName,
            onValueChange = onTagNameChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 21.sp),
            placeholder = { Text("태그 입력") },
            leadingIcon = { Text("#", fontSize = 16.sp, color = Word) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "Select Pictures",
            modifier = Modifier
                .clip(CircleShape)
                .background(Button)
                .padding(2.dp),
            tint = Word,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Select Pictures",
            fontSize = 18.sp,
            fontFamily = FontFamily.Serif,
            color = Word,
        )
    }
}

@Composable
private fun SelectedPhotosSection(
    photos: List<Long>,
    onPhotoClick: (Long) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Semi_background),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(photos) { photoId ->
            PhotoCheckedItem(
                photoId = photoId,
                isSelected = true,
                onClick = { onPhotoClick(photoId) },
                modifier = Modifier.aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun RecommendedPicturesSection(
    photos: List<Long>,
    onPhotoClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Recommended Pictures",
            fontSize = 21.sp,
            fontFamily = FontFamily.Serif,
            color = Word,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(photos) { photoId ->
                PhotoCheckedItem(
                    photoId = photoId,
                    isSelected = false,
                    onClick = { onPhotoClick(photoId) },
                    modifier = Modifier.aspectRatio(1f),
                )
            }
        }
    }
}

@Composable
fun PhotoCheckedItem(
    photoId: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageUri = getUriFromPhotoId(photoId)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Semi_background)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = "사진 ${photoId}",
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
        modifier = modifier
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

@Composable
private fun getUriFromPhotoId(photoId: Long): Uri {
    return ContentUris.withAppendedId(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        photoId,
    )
}
