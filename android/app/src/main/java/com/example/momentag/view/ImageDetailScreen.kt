package com.example.momentag.view

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.model.Photo
import com.example.momentag.model.Tag
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.ConfirmableRecommendedTag
import com.example.momentag.ui.components.CustomTagChip
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.components.tagXMode
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.viewmodel.ImageDetailViewModel
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
    // 1. 로컬 상태 변수
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var tapJob by remember { mutableStateOf<Job?>(null) }

    // 2. rememberCoroutineScope
    val scope = rememberCoroutineScope()

    // 3. Remember된 객체들
    val scaleAnim = remember { Animatable(1f) }

    // 4. LaunchedEffect - 페이지 전환 시 상태 초기화
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

// event.calculateCentroid()는 internal API이므로, 직접 구현해야 함.
internal fun PointerEvent.calculateCentroid(useCurrentPosition: Boolean = true): Offset {
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
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current
    val view = LocalView.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 2. ViewModel 인스턴스
    val imageDetailViewModel: ImageDetailViewModel =
        hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val imageContext by imageDetailViewModel.imageContext.collectAsState()
    val imageDetailTagState by imageDetailViewModel.imageDetailTagState.collectAsState()
    val tagDeleteState by imageDetailViewModel.tagDeleteState.collectAsState()
    val tagAddState by imageDetailViewModel.tagAddState.collectAsState()
    val photoAddress by imageDetailViewModel.photoAddress.collectAsState()

    // 4. 로컬 상태 변수 (remember, mutableStateOf)
    var isWarningBannerVisible by remember { mutableStateOf(false) }
    var warningBannerMessage by remember { mutableStateOf("") }
    var isFocusMode by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    var isDeleteMode by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var dateTime: String? by remember { mutableStateOf(null) }
    var latLong: DoubleArray? by remember { mutableStateOf(null) }

    // 5. Derived 상태 및 계산된 값
    val photos =
        imageContext?.images?.takeIf { it.isNotEmpty() } ?: imageUri?.let { uri ->
            listOf(
                Photo(
                    photoId = imageId,
                    contentUri = uri,
                    createdAt = "",
                ),
            )
        } ?: emptyList()

    val startIndex = imageContext?.currentIndex ?: 0

    val successState = imageDetailTagState as? ImageDetailViewModel.ImageDetailTagState.Success
    val existingTags = successState?.existingTags ?: emptyList()
    val recommendedTags = successState?.recommendedTags ?: emptyList()
    val isExistingLoading = successState?.isExistingLoading ?: false
    val isRecommendedLoading = successState?.isRecommendedLoading ?: false
    val isError = imageDetailTagState is ImageDetailViewModel.ImageDetailTagState.Error
    val errorMessage =
        (imageDetailTagState as? ImageDetailViewModel.ImageDetailTagState.Error)?.let { errorState ->
            when (errorState.error) {
                ImageDetailViewModel.ImageDetailError.NetworkError -> context.getString(R.string.error_message_network)
                ImageDetailViewModel.ImageDetailError.Unauthorized -> context.getString(R.string.error_message_authentication_required)
                ImageDetailViewModel.ImageDetailError.NotFound -> context.getString(R.string.error_message_photo_not_found)
                ImageDetailViewModel.ImageDetailError.UnknownError -> context.getString(R.string.error_message_unknown)
            }
        }

    // 6. Remember된 객체들
    val pagerState =
        rememberPagerState(
            initialPage = startIndex.coerceIn(0, photos.size - 1),
            pageCount = { photos.size },
        )

    val sheetState =
        rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true,
        )
    rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    sheetState.targetValue == SheetValue.Expanded
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    // 7. ActivityResultLauncher
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )

    // 8. LaunchedEffect (초기화 및 부수 효과)
    LaunchedEffect(key1 = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permission: String = Manifest.permission.ACCESS_MEDIA_LOCATION
            permissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(imageUri) {
        imageUri?.let { uri ->
            imageDetailViewModel.loadImageContextByUri(uri)
        }
    }

    LaunchedEffect(imageContext?.currentIndex) {
        imageContext?.currentIndex?.let { index ->
            if (index in 0 until photos.size && pagerState.currentPage != index) {
                pagerState.scrollToPage(index)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }

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

    LaunchedEffect(tagDeleteState) {
        when (tagDeleteState) {
            is ImageDetailViewModel.TagDeleteState.Success -> {
                Toast.makeText(context, context.getString(R.string.success_tag_deleted), Toast.LENGTH_SHORT).show()
                val currentPhotoId = currentPhoto?.photoId?.takeIf { it.isNotEmpty() } ?: imageId
                if (currentPhotoId.isNotEmpty()) {
                    imageDetailViewModel.loadPhotoTags(currentPhotoId)
                }

                isDeleteMode = false
                imageDetailViewModel.resetDeleteState()
            }

            is ImageDetailViewModel.TagDeleteState.Error -> {
                val errorState = tagDeleteState as ImageDetailViewModel.TagDeleteState.Error
                warningBannerMessage =
                    when (errorState.error) {
                        ImageDetailViewModel.ImageDetailError.NetworkError -> context.getString(R.string.error_message_network)
                        ImageDetailViewModel.ImageDetailError.Unauthorized ->
                            context.getString(
                                R.string.error_message_authentication_required,
                            )
                        ImageDetailViewModel.ImageDetailError.NotFound -> context.getString(R.string.error_message_photo_not_found)
                        ImageDetailViewModel.ImageDetailError.UnknownError -> context.getString(R.string.error_message_unknown)
                    }
                isWarningBannerVisible = true
                isDeleteMode = false
                imageDetailViewModel.resetDeleteState()
            }

            else -> Unit
        }
    }

    LaunchedEffect(tagAddState) {
        when (tagAddState) {
            is ImageDetailViewModel.TagAddState.Success -> {
                Toast.makeText(context, context.getString(R.string.success_tag_added), Toast.LENGTH_SHORT).show()
                imageDetailViewModel.resetAddState()
            }
            is ImageDetailViewModel.TagAddState.Error -> {
                val errorState = tagAddState as ImageDetailViewModel.TagAddState.Error
                warningBannerMessage =
                    when (errorState.error) {
                        ImageDetailViewModel.ImageDetailError.NetworkError -> context.getString(R.string.error_message_network)
                        ImageDetailViewModel.ImageDetailError.Unauthorized ->
                            context.getString(
                                R.string.error_message_authentication_required,
                            )
                        ImageDetailViewModel.ImageDetailError.NotFound -> context.getString(R.string.error_message_photo_not_found)
                        ImageDetailViewModel.ImageDetailError.UnknownError -> context.getString(R.string.error_message_unknown)
                    }
                isWarningBannerVisible = true
                imageDetailViewModel.resetAddState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(isError, errorMessage) {
        if (isError) {
            warningBannerMessage = context.getString(R.string.image_detail_error_loading_tags)
            isWarningBannerVisible = true
        }
    }

    LaunchedEffect(isWarningBannerVisible) {
        if (isWarningBannerVisible) {
            delay(2000)
            isWarningBannerVisible = false
        }
    }

    // Window insets controller for focus mode
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }

        LaunchedEffect(insetsController, isFocusMode) {
            if (isFocusMode) {
                insetsController?.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController?.show(WindowInsetsCompat.Type.navigationBars())
            }
        }

        // 9. DisposableEffect
        DisposableEffect(insetsController) {
            onDispose {
                insetsController?.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            imageDetailViewModel.clearImageContext()
        }
    }

    // 10. BackHandler
    BackHandler(enabled = isDeleteMode) {
        isDeleteMode = false
    }

    Scaffold(
        containerColor = if (isFocusMode) Color.Black else MaterialTheme.colorScheme.surface,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
        topBar = {
            if (!isFocusMode) {
                BackTopBar(
                    title = stringResource(R.string.app_name),
                    onBackClick = onNavigateBack,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            // Image Pager as the background layer, always filling the screen
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed,
            ) { page ->
                val photo = photos.getOrNull(page)
                ZoomableImage(
                    model = photo?.contentUri,
                    contentDescription = stringResource(R.string.cd_detail_image),
                    modifier = Modifier.fillMaxSize(),
                    onScaleChanged = { zoomed ->
                        isZoomed = zoomed
                    },
                    onSingleTap = {
                        isFocusMode = !isFocusMode
                    },
                )
            }

            // UI elements as the foreground layer, conditionally visible
            if (!isFocusMode) {
                Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    // Date and Address
                    if (dateTime != null) {
                        val datePart = dateTime!!.split(" ")[0]
                        val formattedDate = datePart.replace(":", ".")
                        Text(
                            text = formattedDate,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = Dimen.ItemSpacingSmall,
                                        bottom = Dimen.SpacingXXSmall,
                                        start = Dimen.ItemSpacingMedium,
                                        end = Dimen.ItemSpacingMedium,
                                    ),
                            textAlign = TextAlign.Left,
                        )
                    }

                    val address = photoAddress
                    if (!address.isNullOrBlank()) {
                        Text(
                            text = address,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.headlineLarge,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = Dimen.SearchSideEmptyPadding,
                                        bottom = Dimen.ItemSpacingSmall,
                                        start = Dimen.ItemSpacingMedium,
                                        end = Dimen.ItemSpacingMedium,
                                    ),
                            textAlign = TextAlign.Left,
                        )
                    }

                    // Spacer to push tags to the bottom
                    Spacer(modifier = Modifier.weight(1f))

                    // Tags section at the bottom
                    if (isError) {
                        Spacer(modifier = Modifier.fillMaxWidth().height(Dimen.SearchBarMinHeight))
                    } else {
                        TagsSection(
                            modifier =
                                Modifier.padding(
                                    start = Dimen.ItemSpacingMedium,
                                    end = Dimen.ItemSpacingMedium,
                                    top = Dimen.ItemSpacingSmall,
                                    bottom = Dimen.ItemSpacingMedium,
                                ),
                            existingTags = existingTags,
                            recommendedTags = recommendedTags,
                            isExistingTagsLoading = isExistingLoading,
                            isRecommendedTagsLoading = isRecommendedLoading,
                            isDeleteMode = isDeleteMode,
                            onEnterDeleteMode = { isDeleteMode = true },
                            onExitDeleteMode = { isDeleteMode = false },
                            onDeleteClick = { tagId ->
                                val currentPhotoId = currentPhoto?.photoId?.takeIf { it.isNotEmpty() } ?: imageId
                                if (currentPhotoId.isNotEmpty()) {
                                    imageDetailViewModel.deleteTagFromPhoto(currentPhotoId, tagId)
                                } else {
                                    warningBannerMessage = context.getString(R.string.image_detail_no_photo_delete)
                                    isWarningBannerVisible = true
                                }
                            },
                            onAddTag = { tagName ->
                                val currentPhotoId = currentPhoto?.photoId?.takeIf { it.isNotEmpty() } ?: imageId
                                if (currentPhotoId.isNotEmpty()) {
                                    imageDetailViewModel.addTagToPhoto(currentPhotoId, tagName)
                                } else {
                                    warningBannerMessage = context.getString(R.string.image_detail_no_photo_add)
                                    isWarningBannerVisible = true
                                }
                            },
                        )
                    }
                }
            }

            // WarningBanner always at the bottom of the main content Box, on top of everything
            if (isWarningBannerVisible && !isFocusMode) {
                WarningBanner(
                    title = stringResource(R.string.error_title),
                    message = warningBannerMessage,
                    onActionClick = { isWarningBannerVisible = false },
                    showActionButton = false,
                    onDismiss = { isWarningBannerVisible = false },
                    showDismissButton = true,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(Dimen.ComponentPadding),
                )
            }
        }
    }
}

// UI 디자인의 색상들을 순서대로 할당
private val tagColors =
    listOf(
        Color(0xFF93C5FD), // Blue
        Color(0xFFFCA5A5), // Red
        Color(0xFF86EFAC), // Green
        Color(0xFFFDE047), // Yellow
        Color(0xFFFDA4AF), // Pink
        Color(0xFFA78BFA), // Purple
        Color(0xFF67E8F9), // Cyan
        Color(0xFFFBBF24), // Amber
        Color(0xFFE879F9), // Magenta
        Color(0xFF34D399), // Emerald
        Color(0xFFF97316), // Orange
        Color(0xFF94A3B8), // Slate
        Color(0xFFE7A396), // Dusty Rose
        Color(0xFFEACE84), // Soft Gold
        Color(0xFF9AB9E1), // Periwinkle
        Color(0xFFD9A1C0), // Mauve
        Color(0xFFF7A97B), // Peach
        Color(0xFFF0ACB7), // Blush Pink
        Color(0xFFEBCF92), // Cream
        Color(0xFFDDE49E), // Pale Lime
        Color(0xFF80E3CD), // Mint Green
        Color(0xFFCCC0F2), // Lavender
        Color(0xFFCAD892), // Sage Green
        Color(0xFF969A60), // Olive
        Color(0xFF758D46), // Moss Green
        Color(0xFF98D0F5), // Baby Blue
        Color(0xFF5E9D8E), // Dusty Teal
        Color(0xFF3C8782), // Deep Teal
        Color(0xFFEB5A6D), // Coral Red
        Color(0xFFF3C9E4), // Light Orchid
        Color(0xFFEEADA7), // Salmon Pink
        Color(0xFFBD8DBD), // Soft Purple
        Color(0xFFFAF5AF), // Pale Yellow
        Color(0xFFAD9281), // Warm Gray
        Color(0xFFF2C6C7), // Rose Beige
        Color(0xFFE87757), // Terracotta
        Color(0xFFED6C84), // Watermelon
        Color(0xFFB9A061), // Khaki
        Color(0xFFA0BA46), // Lime Green
    )

private fun getTagColor(tagId: String): Color = tagColors[abs(tagId.hashCode()) % tagColors.size]

private fun lightenColor(
    color: Color,
    factor: Float = 0.5f,
): Color {
    val red = (color.red + (1 - color.red) * factor)
    val green = (color.green + (1 - color.green) * factor)
    val blue = (color.blue + (1 - color.blue) * factor)
    return Color(red, green, blue, color.alpha)
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
    onAddTag: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 개별 태그의 삭제 모드 상태 관리
    var deleteModeTagId by remember { mutableStateOf<String?>(null) }

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // --- 1. 기존 태그 로딩 처리 ---
        if (isExistingTagsLoading) {
            CircularProgressIndicator(modifier = Modifier.size(Dimen.IconButtonSizeSmall))
        } else {
            // Display existing tags - 개별 롱프레스로 해당 태그만 삭제 모드
            existingTags.forEach { tagItem ->
                val isThisTagInDeleteMode = deleteModeTagId == tagItem.tagId

                Box(
                    modifier =
                        Modifier.combinedClickable(
                            onLongClick = {
                                // 롱프레스 시 이 태그만 삭제 모드로 전환 (토글)
                                deleteModeTagId = if (isThisTagInDeleteMode) null else tagItem.tagId
                            },
                            onClick = {
                                // 클릭 시 해당 태그의 삭제 모드 해제
                                if (isThisTagInDeleteMode) {
                                    deleteModeTagId = null
                                }
                            },
                        ),
                ) {
                    tagXMode(
                        text = tagItem.tagName,
                        isDeleteMode = isThisTagInDeleteMode,
                        onDismiss = {
                            onDeleteClick(tagItem.tagId)
                            deleteModeTagId = null
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // --- 2. 추천 태그 로딩 처리 ---
        if (isRecommendedTagsLoading) {
            CircularProgressIndicator(modifier = Modifier.size(Dimen.IconButtonSizeSmall))
        } else {
            // Display recommended tags (통일된 색상 사용)
            recommendedTags.forEach { tagName ->
                ConfirmableRecommendedTag(
                    tagName = tagName,
                    onConfirm = { onAddTag(it) },
                    color = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }

        // Add Tag Chip
        CustomTagChip(
            onTagAdded = onAddTag,
            onExpanded = {
                scope.launch {
                    // Increase delay to ensure the UI has fully recomposed and measured
                    // the expanded chip before attempting to scroll to the end.
                    delay(400)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            },
        )
    }
}
