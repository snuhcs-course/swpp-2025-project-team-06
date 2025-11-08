package com.example.momentag

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.momentag.model.Photo
import com.example.momentag.model.Tag
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Pretendard
import com.example.momentag.viewmodel.ImageDetailViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ImageDetailScreen(
    imageUri: Uri?,
    imageId: String,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Screen-scoped ViewModel - fresh instance per screen
    val imageDetailViewModel: ImageDetailViewModel = viewModel(factory = ViewModelFactory.getInstance(context))

    // Observe ImageContext from ViewModel
    val imageContext by imageDetailViewModel.imageContext.collectAsState()

    // Observe PhotoTagState from ViewModel
    val photoTagState by imageDetailViewModel.photoTagState.collectAsState()
    val tagDeleteState by imageDetailViewModel.tagDeleteState.collectAsState()

    // Load ImageContext from Repository when screen opens
    LaunchedEffect(imageUri) {
        imageUri?.let { uri ->
            imageDetailViewModel.loadImageContextByUri(uri)
        }
    }

    // Cleanup on dispose
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            imageDetailViewModel.clearImageContext()
        }
    }

    // Extract photos from ImageContext or create single photo using backend imageId with provided imageUri
    // Important: Do NOT derive backend photoId from local media URI; always use backend-provided imageId
    val photos =
        imageContext?.images?.takeIf { it.isNotEmpty() } ?: imageUri?.let { uri ->
            listOf(
                Photo(
                    photoId = imageId, // backend photo_id must be used
                    contentUri = uri,
                ),
            )
        } ?: emptyList()

    val startIndex = imageContext?.currentIndex ?: 0

    val pagerState =
        rememberPagerState(
            initialPage = startIndex.coerceIn(0, photos.size - 1),
            pageCount = { photos.size },
        )

    // Scroll to correct page when imageContext loads
    LaunchedEffect(imageContext?.currentIndex) {
        imageContext?.currentIndex?.let { index ->
            if (index in 0 until photos.size && pagerState.currentPage != index) {
                pagerState.scrollToPage(index)
            }
        }
    }

    // Current photo based on pager state
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    // Tags state - managed by ViewModel
    var isDeleteMode by remember { mutableStateOf(false) }

    // Extract tags from photoTagState
    val existingTags =
        when (photoTagState) {
            is com.example.momentag.model.PhotoTagState.Success ->
                (photoTagState as com.example.momentag.model.PhotoTagState.Success)
                    .existingTags
            else -> emptyList()
        }

    val recommendedTags =
        when (photoTagState) {
            is com.example.momentag.model.PhotoTagState.Success ->
                (photoTagState as com.example.momentag.model.PhotoTagState.Success)
                    .recommendedTags
            else -> emptyList()
        }

    val sheetState =
        rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    val isSheetExpanded = sheetState.targetValue == SheetValue.Expanded

    val overlappingTagsAlpha by animateFloatAsState(targetValue = if (isSheetExpanded) 0f else 1f, label = "tagsAlpha")
    val metadataAlpha by animateFloatAsState(targetValue = if (isSheetExpanded) 1f else 0f, label = "metadataAlpha")

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permission: String = Manifest.permission.ACCESS_MEDIA_LOCATION
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(tagDeleteState) {
        when (val state = tagDeleteState) {
            is ImageDetailViewModel.TagDeleteState.Success -> {
                Toast.makeText(context, "Tag Deleted", Toast.LENGTH_SHORT).show()
                val currentPhotoId = currentPhoto?.photoId?.takeIf { it.isNotEmpty() } ?: imageId
                if (currentPhotoId.isNotEmpty()) {
                    imageDetailViewModel.loadPhotoTags(currentPhotoId)
                }

                isDeleteMode = false
                imageDetailViewModel.resetDeleteState()
            }
            is ImageDetailViewModel.TagDeleteState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                isDeleteMode = false
                imageDetailViewModel.resetDeleteState()
            }
            else -> Unit
        }
    }

    // 현재 페이지의 이미지 Uri
    val currentImageUri = currentPhoto?.contentUri

    var dateTime: String? by remember { mutableStateOf(null) }
    var latLong: DoubleArray? by remember { mutableStateOf(null) }

    // 페이지가 변경될 때마다 EXIF 데이터 및 태그 업데이트
    LaunchedEffect(pagerState.currentPage, hasPermission, imageContext) {
        val photo = photos.getOrNull(pagerState.currentPage)

        // Load EXIF data
        if (hasPermission && photo != null) {
            try {
                context.contentResolver.openInputStream(photo.contentUri)?.use { inputStream ->
                    val exifInterface = ExifInterface(inputStream)
                    dateTime = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    latLong = exifInterface.latLong
                }
            } catch (e: IOException) {
                e.printStackTrace()
                dateTime = null
                latLong = null
            }
        }

        // Load tags from backend using photo.photoId
        if (photo != null && photo.photoId.isNotEmpty()) {
            if (photo.photoId.isNotEmpty()) {
                imageDetailViewModel.loadPhotoTags(photo.photoId)
            } else if (imageContext == null && pagerState.currentPage == 0) {
                imageDetailViewModel.loadPhotoTags(imageId)
            }
        }
    }

    BackHandler(enabled = isDeleteMode) {
        isDeleteMode = false
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        containerColor = Background,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Color.DarkGray,
                    contentColor = Color.White,
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "MomenTag",
                        fontFamily = Pretendard,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Background,
                    ),
            )
        },
        sheetContent = {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "$dateTime\n(${latLong?.get(0)}, ${latLong?.get(1)})",
                    modifier = Modifier.alpha(metadataAlpha).padding(vertical = 16.dp),
                    lineHeight = 22.sp,
                )
                TagsSection(
                    existingTags = existingTags,
                    recommendedTags = recommendedTags,
                    isDeleteMode = isDeleteMode,
                    onEnterDeleteMode = { isDeleteMode = true },
                    onExitDeleteMode = { isDeleteMode = false },
                    onDeleteClick = { tagId ->
                        val currentPhotoId = currentPhoto?.photoId?.takeIf { it.isNotEmpty() } ?: imageId
                        if (currentPhotoId.isNotEmpty()) {
                            imageDetailViewModel.deleteTagFromPhoto(currentPhotoId, tagId)
                        } else {
                            Toast.makeText(context, "No photo", Toast.LENGTH_SHORT).show()
                        }
                    },
                    snackbarHostState = snackbarHostState,
                )
                Spacer(modifier = Modifier.height(200.dp))
            }
        },
        sheetPeekHeight = 100.dp,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Box(modifier = Modifier) {
                // HorizontalPager로 이미지 스와이프 기능 구현
                HorizontalPager(
                    state = pagerState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                ) { page ->
                    val photo = photos.getOrNull(page)
                    AsyncImage(
                        model = photo?.contentUri,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clickable {
                                    if (isDeleteMode) isDeleteMode = false
                                },
                        contentScale = ContentScale.Fit,
                    )
                }

                val scrollState = rememberScrollState()
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp)
                            .padding(bottom = 8.dp)
                            .alpha(overlappingTagsAlpha),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Display existing tags with delete mode
                    existingTags.forEach { tagItem ->
                        Box(
                            modifier =
                                Modifier.combinedClickable(
                                    onLongClick = { isDeleteMode = true },
                                    onClick = {
                                        if (isDeleteMode) isDeleteMode = false
                                    },
                                ),
                        ) {
                            tagXMode(
                                text = tagItem.tagName,
                                isDeleteMode = isDeleteMode,
                                onDismiss = {
                                    val currentPhotoId = currentPhoto?.photoId?.takeIf { it.isNotEmpty() } ?: imageId

                                    if (currentPhotoId.isNotEmpty()) {
                                        imageDetailViewModel.deleteTagFromPhoto(currentPhotoId, tagItem.tagId)
                                    } else {
                                        Toast.makeText(context, "No photo", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }

                    // Display recommended tags with transparency
                    recommendedTags.forEach { tagName ->
                        Box {
                            tagRecommended(text = tagName)
                        }
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("🛠️개발예정")
                            }
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tag", tint = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagsSection(
    existingTags: List<Tag>,
    recommendedTags: List<String>,
    modifier: Modifier = Modifier,
    isDeleteMode: Boolean,
    onDeleteClick: (String) -> Unit,
    onEnterDeleteMode: () -> Unit,
    onExitDeleteMode: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Display existing tags
        existingTags.forEach { tagItem ->
            Box(
                modifier =
                    Modifier.combinedClickable(
                        onLongClick = onEnterDeleteMode,
                        onClick = {
                            if (isDeleteMode) onExitDeleteMode()
                        },
                    ),
            ) {
                tagXMode(
                    text = tagItem.tagName,
                    isDeleteMode = isDeleteMode,
                    onDismiss = { onDeleteClick(tagItem.tagId) },
                )
            }
        }

        // Display recommended tags with transparency
        recommendedTags.forEach { tagName ->
            Box {
                tagRecommended(
                    text = tagName,
                )
            }
        }

        IconButton(
            onClick = {
                scope.launch {
                    snackbarHostState.showSnackbar("🛠️개발예정")
                }
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tag", tint = Color.Gray)
        }
    }
}
