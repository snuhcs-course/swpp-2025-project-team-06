package com.example.momentag

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.momentag.model.ImageDetailTagState
import com.example.momentag.model.Photo
import com.example.momentag.model.Tag
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.viewmodel.ImageDetailViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.abs

@Composable
fun ZoomableImage(
    model: Any?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onScaleChanged: (isZoomed: Boolean) -> Unit,
    onSingleTap: () -> Unit = {},
) {
    // 1. 상태 변수 선언
    val scaleAnim = remember { Animatable(1f) }
    var scale by remember { mutableFloatStateOf(1f) }

    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val scope = rememberCoroutineScope()
    var tapJob by remember { mutableStateOf<Job?>(null) }

    // 2. 페이지 전환 시 모든 상태를 완벽하게 초기화
    LaunchedEffect(model) {
        scale = 1f
        offset = Offset.Zero
        scaleAnim.snapTo(1f)
        onScaleChanged(false)
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    // Single tap detection
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPosition = down.position

                        // Wait for up event
                        val up = waitForUpOrCancellation()

                        if (up != null) {
                            val upTime = System.currentTimeMillis()
                            val upPosition = up.position
                            val timeDiff = upTime - downTime
                            val positionDiff = (upPosition - downPosition).getDistance()

                            // Single tap: quick and no movement
                            if (timeDiff < 300 && positionDiff < 20f) {
                                // Cancel any pending tap job and start new one
                                tapJob?.cancel()
                                tapJob =
                                    scope.launch {
                                        delay(200) // Timeout to ensure it's not a double tap
                                        onSingleTap()
                                    }
                            }
                        }
                    }
                }.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false) // 다른 제스처와 경쟁하기 위해 false로 설정
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            // 두 손가락 제스처(줌)이거나, 이미 확대된 상태에서의 한 손가락 드래그일 경우
                            if (event.changes.size > 1 || scale > 1.01f) { // scale > 1.01f 로 약간의 여유를 줌
                                // Cancel tap job if zoom/pan starts
                                tapJob?.cancel()
                                val oldScale = scale
                                val newScale = (scale * (1f + (zoom - 1f) * 1f)).coerceIn(1f, 5f)

                                // 줌 중심점을 기준으로 오프셋 계산
                                val centroid = event.calculateCentroid(useCurrentPosition = true)

                                // 화면 좌표 → 이미지 중심 좌표로 보정
                                val centroidInImageSpace =
                                    centroid - Offset(size.width / 2f, size.height / 2f)

                                val rawOffset =
                                    offset - (centroidInImageSpace * (newScale / oldScale - 1f)) + pan

                                // 계산된 오프셋을 경계 내로 제한하여 상태에 저장 (오프셋 누적 방지)
                                val maxX = (size.width * (newScale - 1) / 2f).coerceAtLeast(0f)
                                val maxY = (size.height * (newScale - 1) / 2f).coerceAtLeast(0f)

                                val clampedX = rawOffset.x.coerceIn(-maxX, maxX)
                                val clampedY = rawOffset.y.coerceIn(-maxY, maxY)
                                val overScrollX = rawOffset.x - clampedX
                                val overScrollY = rawOffset.y - clampedY

                                offset = Offset(clampedX, clampedY)

                                scale = newScale
                                scope.launch {
                                    scaleAnim.snapTo(scale)
                                }
                                onScaleChanged(scale > 1f)

                                if (overScrollX != 0f || overScrollY != 0f) {
                                    val overScrollAmount = abs(overScrollX) + abs(overScrollY)
                                    val bounceScale = 1f + (overScrollAmount / size.width) * 0.05f
                                    scope.launch {
                                        scaleAnim.snapTo(scale * bounceScale)
                                    }
                                }

                                // 현재 이벤트를 소비하여 HorizontalPager로 전파되는 것을 막음
                                event.changes.forEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                            }
                            // 그 외의 경우 (줌 안 된 상태에서의 한 손가락 스와이프)는 이벤트를 소비하지 않고 Pager로 전달
                        } while (event.changes.any { it.pressed })
                        scope.launch {
                            scaleAnim.animateTo(
                                targetValue = scale,
                                animationSpec =
                                    spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                            )
                        }
                    }
                }.graphicsLayer {
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                },
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// event.calculateCentroid()는 internal API이므로, 직접 구현합니다.
internal fun androidx.compose.ui.input.pointer.PointerEvent.calculateCentroid(useCurrentPosition: Boolean = true): Offset {
    var totalX = 0.0f
    var totalY = 0.0f
    var count = 0
    changes.forEach {
        val position = if (useCurrentPosition) it.position else it.previousPosition
        totalX += position.x
        totalY += position.y
        count++
    }
    return if (count == 0) Offset.Zero else Offset(totalX / count, totalY / count)
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
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
    val imageDetailViewModel: ImageDetailViewModel =
        viewModel(factory = ViewModelFactory.getInstance(context))

    // Observe ImageContext from ViewModel
    val imageContext by imageDetailViewModel.imageContext.collectAsState()

    // Observe PhotoTagState from ViewModel
    val imageDetailTagState by imageDetailViewModel.imageDetailTagState.collectAsState()
    val tagDeleteState by imageDetailViewModel.tagDeleteState.collectAsState()

    // Observe the photo address from ViewModel
    val photoAddress by imageDetailViewModel.photoAddress.collectAsState()

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
                    createdAt = "",
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

    // 현재 보고 있는 페이지의 확대/축소 상태를 기억할 변수
    var isZoomed by remember { mutableStateOf(false) }

    // Focus mode state
    var isFocusMode by remember { mutableStateOf(false) }

    // 페이지가 변경되면 확대 상태를 초기화
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }

    // Tags state - managed by ViewModel
    var isDeleteMode by remember { mutableStateOf(false) }

    // Extract tags from photoTagState
    val successState = imageDetailTagState as? ImageDetailTagState.Success
    val existingTags = successState?.existingTags ?: emptyList()
    val recommendedTags = successState?.recommendedTags ?: emptyList()
    val isExistingLoading = successState?.isExistingLoading ?: false
    val isRecommendedLoading = successState?.isRecommendedLoading ?: false

    val isError = imageDetailTagState is ImageDetailTagState.Error
    val errorMessage = (imageDetailTagState as? ImageDetailTagState.Error)?.message

    val sheetState =
        rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    val isSheetExpanded = sheetState.targetValue == SheetValue.Expanded

    val overlappingTagsAlpha by animateFloatAsState(
        targetValue = if (isSheetExpanded) 0f else 1f,
        label = "tagsAlpha",
    )
    val metadataAlpha by animateFloatAsState(
        targetValue = if (isSheetExpanded) 1f else 0f,
        label = "metadataAlpha",
    )

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = if (isFocusMode) Color.Black else MaterialTheme.colorScheme.surface,
            snackbarHost = {
                if (!isFocusMode) {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.inverseSurface,
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                // 1. 이미지가 표시될 영역 (전체 화면을 차지)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clipToBounds(),
                ) {
                    // HorizontalPager로 이미지 스와이프 기능 구현
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        // 줌 상태가 아닐 때만(= isZoomed가 false일 때만) 스와이프를 허용
                        userScrollEnabled = !isZoomed,
                    ) { page ->
                        val photo = photos.getOrNull(page)
                        ZoomableImage(
                            model = photo?.contentUri,
                            contentDescription = "Detail image",
                            modifier = Modifier.fillMaxSize(),
                            onScaleChanged = { zoomed ->
                                isZoomed = zoomed
                            },
                            onSingleTap = {
                                isFocusMode = !isFocusMode
                            },
                        )
                    }
                }
            }
        }

        // Overlay date and address info on top of image
        if (!isFocusMode) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(top = 64.dp),
            ) {
                dateTime?.let { dt ->
                    val datePart = dt.split(" ")[0]
                    val formattedDate = datePart.replace(":", ".")
                    Text(
                        text = formattedDate,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 2.dp, start = 12.dp, end = 12.dp),
                        textAlign = TextAlign.Left,
                    )
                }

                photoAddress?.let { address ->
                    if (address.isNotBlank()) {
                        Text(
                            text = address,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.headlineLarge,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 0.dp, bottom = 8.dp, start = 12.dp, end = 12.dp),
                            textAlign = TextAlign.Left,
                        )
                    }
                }
            }
        }

        // Overlay tags section at the bottom
        if (!isFocusMode) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart),
            ) {
                if (isError) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "태그를 불러오는 중 오류가 발생했습니다.\n($errorMessage)",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    TagsSection(
                        modifier =
                            Modifier
                                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                        existingTags = existingTags,
                        recommendedTags = recommendedTags,
                        isExistingTagsLoading = isExistingLoading,
                        isRecommendedTagsLoading = isRecommendedLoading,
                        isDeleteMode = isDeleteMode,
                        onEnterDeleteMode = { isDeleteMode = true },
                        onExitDeleteMode = { isDeleteMode = false },
                        onDeleteClick = { tagId ->
                            val currentPhotoId =
                                currentPhoto?.photoId?.takeIf { it.isNotEmpty() } ?: imageId
                            if (currentPhotoId.isNotEmpty()) {
                                imageDetailViewModel.deleteTagFromPhoto(currentPhotoId, tagId)
                            } else {
                                Toast.makeText(context, "No photo", Toast.LENGTH_SHORT).show()
                            }
                        },
                        snackbarHostState = snackbarHostState,
                    )
                }
            }
        }

        // Overlay top bar on top of content
        if (!isFocusMode) {
            BackTopBar(
                title = "MomenTag",
                onBackClick = onNavigateBack,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagsSection(
    existingTags: List<Tag>,
    recommendedTags: List<String>,
    modifier: Modifier = Modifier,
    isExistingTagsLoading: Boolean,
    isRecommendedTagsLoading: Boolean,
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
        // --- 1. 기존 태그 로딩 처리 ---
        if (isExistingTagsLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
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
        }

        // --- 2. 추천 태그 로딩 처리 ---
        if (isRecommendedTagsLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            // Display recommended tags
            recommendedTags.forEach { tagName ->
                Box {
                    tagRecommended(
                        text = tagName,
                    )
                }
            }
        }

        // Add Tag 버튼 (기존과 동일)
        IconButton(
            onClick = {
                scope.launch {
                    snackbarHostState.showSnackbar("🛠️개발예정")
                }
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Tag")
        }
    }
}
