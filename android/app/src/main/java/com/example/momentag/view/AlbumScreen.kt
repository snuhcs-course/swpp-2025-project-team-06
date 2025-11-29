package com.example.momentag.view

import android.Manifest
import android.R.attr.content
import android.annotation.SuppressLint
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.Photo
import com.example.momentag.ui.components.AddPhotosButton
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.ConfirmDialog
import com.example.momentag.ui.components.VerticalScrollbar
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.Dimen.ItemSpacingXSmall
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import com.example.momentag.util.ShareUtils
import com.example.momentag.viewmodel.AlbumViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    tagId: String,
    tagName: String,
    navController: NavController,
    onNavigateBack: () -> Unit,
) {
    // 1. Context 및 Platform 관련 변수
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // 2. ViewModel 인스턴스
    val albumViewModel: AlbumViewModel = hiltViewModel()

    // 3. ViewModel에서 가져온 상태 (collectAsState)
    val imageLoadState by albumViewModel.albumLoadingState.collectAsState()
    val tagDeleteState by albumViewModel.tagDeleteState.collectAsState()
    val tagRenameState by albumViewModel.tagRenameState.collectAsState()
    val tagAddState by albumViewModel.tagAddState.collectAsState()
    val selectedTagAlbumPhotos: Map<String, Photo> by albumViewModel.selectedTagAlbumPhotos.collectAsState()
    val selectedTagAlbumPhotoIds = selectedTagAlbumPhotos.keys
    var isDragSelecting by remember { mutableStateOf(false) }
    val scrollToIndex by albumViewModel.scrollToIndex.collectAsState()

    // 4. 로컬 상태 변수
    var hasPermission by remember { mutableStateOf(false) }
    var currentTagName by remember(tagName) { mutableStateOf(tagName) }
    var editableTagName by remember(tagName) { mutableStateOf(tagName) }
    var isFocused by remember { mutableStateOf(false) }
    var isTagAlbumPhotoSelectionMode by remember { mutableStateOf(false) }
    var isTagAlbumPhotoSelectionModeDelay by remember { mutableStateOf(false) }
    var isDeleteConfirmationDialogVisible by remember { mutableStateOf(false) }
    var isRecommendationExpanded by remember { mutableStateOf(false) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorBannerTitle by remember { mutableStateOf(context.getString(R.string.error_title)) }
    var errorBannerMessage by remember { mutableStateOf(context.getString(R.string.error_message_generic)) }
    var isSelectPhotosBannerShareVisible by remember { mutableStateOf(false) }
    var isSelectPhotosBannerUntagVisible by remember { mutableStateOf(false) }

    // 5. Derived 상태 및 계산된 값
    val minPanelHeight = Dimen.ExpandedPanelMinHeight
    val maxPanelHeight = (config.screenHeightDp * 0.6f).dp
    var panelHeight by remember(config) { mutableStateOf((config.screenHeightDp / 3).dp) }
    val gridState = rememberLazyGridState()
    val backgroundBrush = rememberAppBackgroundBrush()

    // 6. rememberCoroutineScope & ActivityResultLauncher
    val scope = rememberCoroutineScope()
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                if (isGranted) {
                    hasPermission = true
                }
            },
        )
    val submitAndClearFocus = {
        if (editableTagName.length > 25) {
            // Do nothing, keep focus to let user fix it
        } else {
            if (editableTagName.isNotBlank() && editableTagName != currentTagName) {
                albumViewModel.renameTag(tagId, editableTagName)
            } else if (editableTagName.isBlank()) { // If text is blank, revert to the last good name
                editableTagName = currentTagName
            }

            keyboardController?.hide() // Hide keyboard
            focusManager.clearFocus() // Remove focus (cursor)
        }
    }
    // 7. LaunchedEffect
    LaunchedEffect(isSelectPhotosBannerShareVisible) {
        if (isSelectPhotosBannerShareVisible) {
            delay(2000)
            isSelectPhotosBannerShareVisible = false
        }
    }

    LaunchedEffect(isSelectPhotosBannerUntagVisible) {
        if (isSelectPhotosBannerUntagVisible) {
            delay(2000)
            isSelectPhotosBannerUntagVisible = false
        }
    }

    LaunchedEffect(isTagAlbumPhotoSelectionMode) {
        delay(200L) // 0.2초
        isTagAlbumPhotoSelectionModeDelay = isTagAlbumPhotoSelectionMode
    }

    LaunchedEffect(tagDeleteState) {
        when (val state = tagDeleteState) {
            is AlbumViewModel.TagDeleteState.Success -> {
                Toast.makeText(context, context.getString(R.string.album_photo_removed), Toast.LENGTH_SHORT).show() // Success: Toast
                albumViewModel.resetDeleteState()
                isErrorBannerVisible = false
            }
            is AlbumViewModel.TagDeleteState.Error -> {
                errorBannerTitle = context.getString(R.string.album_failed_remove_photo)
                errorBannerMessage =
                    when (state.error) {
                        AlbumViewModel.AlbumError.NetworkError -> context.getString(R.string.error_message_network)
                        AlbumViewModel.AlbumError.Unauthorized -> context.getString(R.string.error_message_authentication_required)
                        AlbumViewModel.AlbumError.NotFound -> context.getString(R.string.error_message_photo_not_found)
                        AlbumViewModel.AlbumError.UnknownError -> context.getString(R.string.error_message_unknown)
                    }
                isErrorBannerVisible = true // Failure: Banner
                albumViewModel.resetDeleteState()
            }
            else -> Unit // Idle, Loading
        }
    }

    LaunchedEffect(tagRenameState) {
        when (val state = tagRenameState) {
            is AlbumViewModel.TagRenameState.Success -> {
                Toast.makeText(context, context.getString(R.string.album_tag_renamed), Toast.LENGTH_SHORT).show() // Success: Toast
                currentTagName = editableTagName
                albumViewModel.resetRenameState()
                isErrorBannerVisible = false
            }
            is AlbumViewModel.TagRenameState.Error -> {
                errorBannerTitle = context.getString(R.string.album_failed_rename_tag)
                errorBannerMessage =
                    when (state.error) {
                        AlbumViewModel.AlbumError.NetworkError -> context.getString(R.string.error_message_network)
                        AlbumViewModel.AlbumError.Unauthorized -> context.getString(R.string.error_message_authentication_required)
                        AlbumViewModel.AlbumError.NotFound -> context.getString(R.string.error_message_photo_not_found)
                        AlbumViewModel.AlbumError.UnknownError -> context.getString(R.string.error_message_unknown)
                    }
                isErrorBannerVisible = true // Failure: Banner
                editableTagName = currentTagName
                albumViewModel.resetRenameState()
            }
            else -> Unit // Idle, Loading
        }
    }

    LaunchedEffect(tagAddState) {
        when (val state = tagAddState) {
            is AlbumViewModel.TagAddState.Success -> {
                Toast.makeText(context, context.getString(R.string.album_photos_added), Toast.LENGTH_SHORT).show() // Success: Toast
                albumViewModel.resetAddState()
                isErrorBannerVisible = false
            }
            is AlbumViewModel.TagAddState.Error -> {
                errorBannerTitle = context.getString(R.string.album_failed_add_photos)
                errorBannerMessage =
                    when (state.error) {
                        AlbumViewModel.AlbumError.NetworkError -> context.getString(R.string.error_message_network)
                        AlbumViewModel.AlbumError.Unauthorized -> context.getString(R.string.error_message_authentication_required)
                        AlbumViewModel.AlbumError.NotFound -> context.getString(R.string.error_message_photo_not_found)
                        AlbumViewModel.AlbumError.UnknownError -> context.getString(R.string.error_message_unknown)
                    }
                isErrorBannerVisible = true // Failure: Banner
                albumViewModel.resetAddState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(hasPermission, tagId) {
        if (hasPermission && imageLoadState is AlbumViewModel.AlbumLoadingState.Idle) {
            albumViewModel.loadAlbum(tagId, tagName)
        }
    }

    LaunchedEffect(tagName) {
        currentTagName = tagName
        editableTagName = tagName
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

    // Refresh album when returning from SelectImageScreen
    LaunchedEffect(navController) {
        val currentBackStackEntry = navController.currentBackStackEntry
        val savedStateHandle = currentBackStackEntry?.savedStateHandle
        savedStateHandle?.getLiveData<Boolean>("photos_added")?.observeForever { photosAdded ->
            if (photosAdded == true) {
                albumViewModel.loadAlbum(tagId, currentTagName)
                savedStateHandle.remove<Boolean>("photos_added")
            }
        }
    }

    // Scroll restoration: restore scroll position when returning from ImageDetailScreen
    LaunchedEffect(scrollToIndex) {
        scrollToIndex?.let { index ->
            scope.launch {
                gridState.animateScrollToItem(index)
                albumViewModel.clearScrollToIndex()
            }
        }
    }

    // Restore scroll position when screen becomes visible (returning from ImageDetailScreen)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // Fetch last viewed index from ImageBrowserRepository and restore scroll
                    albumViewModel.restoreScrollPosition()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = isRecommendationExpanded || isTagAlbumPhotoSelectionMode) {
        if (isRecommendationExpanded) {
            isRecommendationExpanded = false
        } else if (isTagAlbumPhotoSelectionMode) {
            isTagAlbumPhotoSelectionMode = false
            albumViewModel.resetTagAlbumPhotoSelection()
        }
    }

    if (isDeleteConfirmationDialogVisible) {
        ConfirmDialog(
            title = stringResource(R.string.album_remove_photos_title),
            message = stringResource(R.string.album_remove_photos_message, selectedTagAlbumPhotos.size, currentTagName),
            confirmButtonText = stringResource(R.string.album_remove),
            onConfirm = {
                albumViewModel.deleteTagFromPhotos(
                    photos = selectedTagAlbumPhotos.values.toList(),
                    tagId = tagId,
                )
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.album_photos_removed_count, selectedTagAlbumPhotos.size),
                        Toast.LENGTH_SHORT,
                    ).show()

                isDeleteConfirmationDialogVisible = false
                isTagAlbumPhotoSelectionMode = false
                albumViewModel.resetTagAlbumPhotoSelection()
            },
            onDismiss = {
                isDeleteConfirmationDialogVisible = false
            },
            dismissible = true,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CommonTopBar(
                title = stringResource(R.string.app_name),
                showBackButton = true,
                onBackClick = {
                    if (isTagAlbumPhotoSelectionMode) {
                        isTagAlbumPhotoSelectionMode = false
                        albumViewModel.resetTagAlbumPhotoSelection()
                    } else {
                        onNavigateBack()
                    }
                },
                navigationIcon =
                    if (isTagAlbumPhotoSelectionMode) {
                        {
                            IconButton(
                                onClick = {
                                    isTagAlbumPhotoSelectionMode = false
                                    albumViewModel.resetTagAlbumPhotoSelection()
                                },
                            ) {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Close,
                                    sizeRole = IconSizeRole.Navigation,
                                    contentDescription = stringResource(R.string.cd_deselect_all),
                                )
                            }
                        }
                    } else {
                        null
                    },
                actions = {
                    if (isTagAlbumPhotoSelectionMode) {
                        val isEnabled = selectedTagAlbumPhotos.isNotEmpty()
                        Row {
                            IconButton(
                                onClick = {
                                    val photos = albumViewModel.getPhotosToShare()
                                    ShareUtils.sharePhotos(context, photos)
                                    if (photos.isNotEmpty()) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.share_photos_count, photos.size),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                },
                                enabled = isEnabled,
                                colors =
                                    IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    ),
                                modifier = Modifier.width(Dimen.IconButtonSizeMediumLarge),
                            ) {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.cd_share),
                                    sizeRole = IconSizeRole.DefaultAction,
                                    intent = if (isEnabled) IconIntent.Primary else IconIntent.Disabled,
                                )
                            }
                            IconButton(
                                onClick = { isDeleteConfirmationDialogVisible = true },
                                enabled = isEnabled,
                            ) {
                                val deleteIntent =
                                    when {
                                        isEnabled -> IconIntent.Error
                                        else -> IconIntent.Disabled
                                    }
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.cd_untag),
                                    sizeRole = IconSizeRole.DefaultAction,
                                    intent = deleteIntent,
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { paddingValues ->

        // === 최상단 레이어 컨테이너: Edge-to-edge 오버레이를 Column 밖의 sibling으로 렌더링 ===
        val bodyModifier =
            Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(paddingValues)

        val content: @Composable () -> Unit = {
            // 본문: 가로 16dp 패딩
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimen.ScreenHorizontalPadding)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { submitAndClearFocus() },
            ) {
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                // 제목(태그명) 행
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "#",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(Dimen.GridItemSpacing))
                        BasicTextField(
                            value = editableTagName,
                            onValueChange = { editableTagName = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .onFocusChanged { isFocused = it.isFocused },
                            textStyle =
                                MaterialTheme.typography.headlineMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitAndClearFocus() }),
                            singleLine = true,
                        )
                    }

                    if (editableTagName.isNotEmpty() && isFocused) {
                        IconButton(
                            onClick = { editableTagName = "" },
                            modifier = Modifier.size(Dimen.IconButtonSizeSmall),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(20.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = CircleShape,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                StandardIcon.Icon(
                                    imageVector = Icons.Default.Close,
                                    sizeRole = IconSizeRole.InlineAction,
                                    tintOverride = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    contentDescription = stringResource(R.string.cd_clear_text),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = Dimen.ItemSpacingSmall, bottom = ItemSpacingXSmall),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )

                if (editableTagName.length > 25) {
                    Text(
                        text = stringResource(R.string.error_message_tag_name_too_long),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = Dimen.ItemSpacingSmall),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.help_tag_album_edit),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = Dimen.ItemSpacingSmall),
                    )
                }

                AnimatedVisibility(
                    visible = isSelectPhotosBannerShareVisible,
                    enter = Animation.EnterFromBottom,
                    exit = Animation.ExitToBottom,
                ) {
                    WarningBanner(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimen.ItemSpacingSmall),
                        title = stringResource(R.string.album_no_photos_selected_title),
                        message = stringResource(R.string.album_select_photos_to_share),
                        onActionClick = { isSelectPhotosBannerShareVisible = false },
                        showActionButton = false,
                        showDismissButton = true,
                        onDismiss = { isSelectPhotosBannerShareVisible = false },
                    )
                }

                AnimatedVisibility(
                    visible = isSelectPhotosBannerUntagVisible,
                    enter = Animation.EnterFromBottom,
                    exit = Animation.ExitToBottom,
                ) {
                    WarningBanner(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimen.ItemSpacingSmall),
                        title = stringResource(R.string.album_no_photos_selected_title),
                        message = stringResource(R.string.album_select_photos_to_untag),
                        onActionClick = { isSelectPhotosBannerUntagVisible = false },
                        showActionButton = false,
                        showDismissButton = true,
                        onDismiss = { isSelectPhotosBannerUntagVisible = false },
                    )
                }

                AnimatedVisibility(
                    visible = isErrorBannerVisible,
                    enter = Animation.EnterFromBottom,
                    exit = Animation.ExitToBottom,
                ) {
                    WarningBanner(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimen.ItemSpacingSmall),
                        title = errorBannerTitle,
                        message = errorBannerMessage,
                        onActionClick = { isErrorBannerVisible = false },
                        showActionButton = false,
                        showDismissButton = true,
                        onDismiss = { isErrorBannerVisible = false },
                    )
                }

                if (!hasPermission) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.album_permission_required))
                    }
                } else {
                    AlbumGridArea(
                        albumLoadState = imageLoadState,
                        recommendLoadState = albumViewModel.recommendLoadingState.collectAsState().value,
                        selectedTagAlbumPhotos = selectedTagAlbumPhotos,
                        navController = navController,
                        isTagAlbumPhotoSelectionMode = isTagAlbumPhotoSelectionMode,
                        onSetTagAlbumPhotoSelectionMode = { isTagAlbumPhotoSelectionMode = it },
                        onToggleTagAlbumPhoto = { photo -> albumViewModel.toggleTagAlbumPhoto(photo) },
                        // 펼쳐짐 여부와 패널 높이에 따라 그리드 bottom padding 조절
                        isRecommendationExpanded = isRecommendationExpanded,
                        panelHeight = panelHeight,
                        // Chip 클릭 시 오버레이 열기
                        onExpandRecommend = { isRecommendationExpanded = true },
                        // Add photos button handler
                        albumViewModel = albumViewModel,
                        tagId = tagId,
                        tagName = currentTagName,
                        gridState = gridState,
                        onDragSelectionStart = { isDragSelecting = true },
                        onDragSelectionEnd = { isDragSelecting = false },
                    )
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(paddingValues),
        ) {
            // Keep the same gesture scope alive while toggling selection mode by always wrapping content
            PullToRefreshBox(
                isRefreshing = imageLoadState is AlbumViewModel.AlbumLoadingState.Loading,
                onRefresh = {
                    if (isTagAlbumPhotoSelectionMode || isDragSelecting) return@PullToRefreshBox

                    scope.launch {
                        if (hasPermission) albumViewModel.loadAlbum(tagId, tagName)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                content()
            }

            // Scrollbar positioned outside Column to span padding boundary
            if (hasPermission && imageLoadState is AlbumViewModel.AlbumLoadingState.Success) {
                VerticalScrollbar(
                    state = gridState,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(
                                top = Dimen.ItemSpacingLarge + Dimen.TopBarHeight + Dimen.SectionSpacing, // Spacer + Title row + Divider
                                end = Dimen.ScreenHorizontalPadding / 2,
                            ),
                )
            }

            // === Edge-to-edge 오버레이 (Column 바깥, 동일 Box의 sibling) ===
            AnimatedVisibility(
                visible = isRecommendationExpanded,
                enter = Animation.EnterFromBottom,
                exit = Animation.ExitToBottom,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                RecommendExpandedPanel(
                    recommendLoadState = albumViewModel.recommendLoadingState.collectAsState().value,
                    selectedRecommendPhotos = albumViewModel.selectedRecommendPhotos.collectAsState().value,
                    navController = navController,
                    onToggleRecommendPhoto = { photo -> albumViewModel.toggleRecommendPhoto(photo) },
                    onResetRecommendSelection = { albumViewModel.resetRecommendSelection() },
                    onAddPhotosToAlbum = { photos ->
                        albumViewModel.addRecommendedPhotosToTagAlbum(photos, tagId, tagName)
                    },
                    panelHeight = panelHeight,
                    onHeightChange = { delta ->
                        panelHeight = (panelHeight - delta).coerceIn(minPanelHeight, maxPanelHeight)
                    },
                    onCollapse = { isRecommendationExpanded = false },
                )
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun AlbumGridArea(
    albumLoadState: AlbumViewModel.AlbumLoadingState,
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    selectedTagAlbumPhotos: Map<String, Photo>,
    navController: NavController,
    isTagAlbumPhotoSelectionMode: Boolean,
    onSetTagAlbumPhotoSelectionMode: (Boolean) -> Unit,
    onToggleTagAlbumPhoto: (Photo) -> Unit,
    isRecommendationExpanded: Boolean,
    panelHeight: Dp,
    onExpandRecommend: () -> Unit,
    albumViewModel: AlbumViewModel,
    tagId: String,
    tagName: String,
    gridState: LazyGridState,
    onDragSelectionStart: () -> Unit,
    onDragSelectionEnd: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (albumLoadState) {
            is AlbumViewModel.AlbumLoadingState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AlbumViewModel.AlbumLoadingState.Success -> {
                val photos = albumLoadState.photos

                val selectedTagAlbumPhotoIds = selectedTagAlbumPhotos.keys
                val updatedSelectedPhotos = rememberUpdatedState(selectedTagAlbumPhotos)
                val updatedIsSelectionMode = rememberUpdatedState(isTagAlbumPhotoSelectionMode)
                val allPhotosState = rememberUpdatedState(photos)

                val gridGestureModifier =
                    Modifier.pointerInput(Unit) {
                        coroutineScope {
                            val pointerScope = this
                            val autoScrollViewport = 80.dp.toPx()
                            var autoScrollJob: Job? = null
                            var dragAnchorIndex: Int? = null
                            var gestureSelectionIds: MutableSet<String> = mutableSetOf()
                            val lastRangePhotoIds = mutableSetOf<String>()

                            fun findNearestItemByRow(position: Offset): Int? {
                                var best: Pair<Int, Float>? = null // index to horizontal distance
                                gridState.layoutInfo.visibleItemsInfo.forEach { itemInfo ->
                                    val key = itemInfo.key as? String ?: return@forEach
                                    val photoIndex = allPhotosState.value.indexOfFirst { it.photoId == key }
                                    if (photoIndex >= 0) {
                                        val top = itemInfo.offset.y.toFloat()
                                        val bottom = (itemInfo.offset.y + itemInfo.size.height).toFloat()
                                        if (position.y in top..bottom) {
                                            val left = itemInfo.offset.x.toFloat()
                                            val right = (itemInfo.offset.x + itemInfo.size.width).toFloat()
                                            val horizontalDistance =
                                                when {
                                                    position.x < left -> left - position.x
                                                    position.x > right -> position.x - right
                                                    else -> 0f
                                                }
                                            if (best == null || horizontalDistance < best!!.second) {
                                                best = photoIndex to horizontalDistance
                                            }
                                        }
                                    }
                                }
                                return best?.first
                            }

                            fun applyRangeSelection(newRangePhotoIds: Set<String>) {
                                val toSelect = newRangePhotoIds - gestureSelectionIds
                                val toDeselect = (lastRangePhotoIds - newRangePhotoIds).intersect(gestureSelectionIds)

                                toSelect.forEach { id ->
                                    allPhotosState.value.find { it.photoId == id }?.let { photo ->
                                        onToggleTagAlbumPhoto(photo)
                                        gestureSelectionIds.add(id)
                                    }
                                }
                                toDeselect.forEach { id ->
                                    allPhotosState.value.find { it.photoId == id }?.let { photo ->
                                        onToggleTagAlbumPhoto(photo)
                                        gestureSelectionIds.remove(id)
                                    }
                                }

                                lastRangePhotoIds.clear()
                                lastRangePhotoIds.addAll(newRangePhotoIds)
                            }

                            detectDragAfterLongPressIgnoreConsumed(
                                onDragStart = { offset ->
                                    autoScrollJob?.cancel()
                                    gestureSelectionIds = updatedSelectedPhotos.value.keys.toMutableSet()
                                    lastRangePhotoIds.clear()
                                    if (!updatedIsSelectionMode.value) {
                                        onSetTagAlbumPhotoSelectionMode(true)
                                    }
                                    onDragSelectionStart()
                                    gridState.findPhotoItemAtPosition(offset, allPhotosState.value)?.let { (photoId, photo) ->
                                        dragAnchorIndex = allPhotosState.value.indexOfFirst { it.photoId == photoId }.takeIf { it >= 0 }
                                        if (gestureSelectionIds.add(photoId) ||
                                            !updatedSelectedPhotos.value.containsKey(photoId)
                                        ) {
                                            onToggleTagAlbumPhoto(photo)
                                        }
                                        lastRangePhotoIds.add(photoId)
                                    }
                                },
                                onDragEnd = {
                                    dragAnchorIndex = null
                                    lastRangePhotoIds.clear()
                                    gestureSelectionIds.clear()
                                    autoScrollJob?.cancel()
                                    onDragSelectionEnd()
                                },
                                onDragCancel = {
                                    dragAnchorIndex = null
                                    lastRangePhotoIds.clear()
                                    gestureSelectionIds.clear()
                                    autoScrollJob?.cancel()
                                    onDragSelectionEnd()
                                },
                                onDrag = { change ->
                                    change.consume()
                                    val currentItem = gridState.findPhotoItemAtPosition(change.position, allPhotosState.value)
                                    val currentIndex =
                                        currentItem
                                            ?.first
                                            ?.let { id ->
                                                allPhotosState.value.indexOfFirst { it.photoId == id }
                                            }?.takeIf { it >= 0 }
                                            ?: findNearestItemByRow(change.position)

                                    if (currentIndex != null) {
                                        if (dragAnchorIndex == null) dragAnchorIndex = currentIndex
                                        val startIndex = dragAnchorIndex ?: currentIndex
                                        val range =
                                            if (currentIndex >= startIndex) {
                                                startIndex..currentIndex
                                            } else {
                                                currentIndex..startIndex
                                            }

                                        val newRangePhotoIds =
                                            range
                                                .mapNotNull { idx ->
                                                    allPhotosState.value.getOrNull(idx)?.photoId
                                                }.toSet()
                                        if (newRangePhotoIds.isNotEmpty()) {
                                            applyRangeSelection(newRangePhotoIds)
                                        }
                                    }

                                    val viewportHeight =
                                        gridState.layoutInfo.viewportSize.height
                                            .toFloat()
                                    val pointerY = change.position.y

                                    val scrollAmount =
                                        when {
                                            pointerY < autoScrollViewport -> -50f
                                            pointerY > viewportHeight - autoScrollViewport -> 50f
                                            else -> 0f
                                        }

                                    if (scrollAmount != 0f) {
                                        if (autoScrollJob?.isActive != true) {
                                            autoScrollJob =
                                                pointerScope.launch {
                                                    while (true) {
                                                        gridState.scrollBy(scrollAmount)
                                                        delay(50)
                                                    }
                                                }
                                        }
                                    } else {
                                        autoScrollJob?.cancel()
                                    }
                                },
                            )
                        }
                    }

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        contentPadding =
                            PaddingValues(
                                bottom = if (isRecommendationExpanded) panelHeight else Dimen.FloatingButtonAreaPadding,
                            ),
                        modifier = Modifier.fillMaxSize().then(gridGestureModifier),
                    ) {
                        item(
                            key = "add_photos_button",
                        ) {
                            AddPhotosButton(
                                onClick = {
                                    albumViewModel.initializeAddPhotosFlow(tagId, tagName)
                                    navController.navigate(Screen.SelectImage.route)
                                },
                                modifier = Modifier.aspectRatio(1f),
                            )
                        }

                        items(
                            count = photos.size,
                            key = { index -> photos[index].photoId },
                        ) { index ->
                            val photo = photos[index]
                            ImageGridUriItem(
                                photo = photo,
                                navController = navController,
                                isSelectionMode = isTagAlbumPhotoSelectionMode,
                                isSelected = selectedTagAlbumPhotoIds.contains(photo.photoId),
                                onToggleSelection = { onToggleTagAlbumPhoto(photo) },
                                // 롱프레스는 부모(pointerInput)에서 처리 (onLongPress는 사용하지 않음)
                                onLongPress = {},
                            )
                        }
                    }
                }
            }
            is AlbumViewModel.AlbumLoadingState.Error -> {
                val errorMessage =
                    when (albumLoadState.error) {
                        AlbumViewModel.AlbumError.NetworkError -> stringResource(R.string.error_message_network)
                        AlbumViewModel.AlbumError.Unauthorized -> stringResource(R.string.error_message_authentication_required)
                        AlbumViewModel.AlbumError.NotFound -> stringResource(R.string.error_message_photo_not_found)
                        AlbumViewModel.AlbumError.UnknownError -> stringResource(R.string.error_message_unknown)
                    }
                WarningBanner(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(R.string.album_failed_to_load),
                    message = errorMessage,
                    onActionClick = { /* PullToRefresh handles this */ },
                    showActionButton = false,
                    showDismissButton = false,
                )
            }
            is AlbumViewModel.AlbumLoadingState.Idle -> {}
        }

        // 축소 상태의 Chip (그리드 위에)
        AnimatedVisibility(
            visible = !isRecommendationExpanded,
            enter = Animation.DefaultFadeIn + expandVertically(animationSpec = Animation.mediumTween()),
            exit = Animation.DefaultFadeOut + shrinkVertically(animationSpec = Animation.mediumTween()),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimen.ItemSpacingLarge),
        ) {
            RecommendChip(
                recommendLoadState = recommendLoadState,
                onExpand = onExpandRecommend,
            )
        }
    }
}

@Composable
private fun RecommendChip(
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    onExpand: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .shadow(elevation = Dimen.BottomNavTonalElevation, shape = RoundedCornerShape(Dimen.Radius20))
                .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(Dimen.Radius20))
                .clip(RoundedCornerShape(Dimen.Radius20))
                .clickable(onClick = onExpand)
                .padding(horizontal = Dimen.ButtonPaddingHorizontal, vertical = Dimen.ButtonPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when (recommendLoadState) {
            is AlbumViewModel.RecommendLoadingState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimen.CircularProgressSizeXSmall),
                    strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.album_ai_recommending),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is AlbumViewModel.RecommendLoadingState.Success -> {
                StandardIcon.Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.cd_ai),
                    sizeRole = IconSizeRole.StatusIndicator,
                    intent = IconIntent.Primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.album_ai_recommend),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is AlbumViewModel.RecommendLoadingState.Error -> {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.error_title),
                    sizeRole = IconSizeRole.StatusIndicator,
                    intent = IconIntent.Error,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.album_recommendation_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            is AlbumViewModel.RecommendLoadingState.Idle -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimen.CircularProgressSizeXSmall),
                    strokeWidth = Dimen.CircularProgressStrokeWidthSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.album_preparing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimen.GridItemSpacing))
        StandardIcon.Icon(
            imageVector = Icons.Default.ExpandLess,
            contentDescription = stringResource(R.string.cd_expand),
            sizeRole = IconSizeRole.StatusIndicator,
        )
    }
}

@Composable
private fun RecommendExpandedPanel(
    recommendLoadState: AlbumViewModel.RecommendLoadingState,
    selectedRecommendPhotos: Map<String, Photo>,
    navController: NavController,
    onToggleRecommendPhoto: (Photo) -> Unit,
    onResetRecommendSelection: () -> Unit,
    onAddPhotosToAlbum: (List<Photo>) -> Unit,
    panelHeight: Dp,
    onHeightChange: (Dp) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val selectedRecommendPhotoIds = selectedRecommendPhotos.keys
    // 터치이벤트 뒤로 전달 되지 않도록
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(panelHeight)
                .shadow(
                    elevation = Dimen.BottomNavShadowElevation,
                    shape = RoundedCornerShape(topStart = Dimen.SearchBarCornerRadius, topEnd = Dimen.SearchBarCornerRadius),
                ).background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shape = RoundedCornerShape(topStart = Dimen.SearchBarCornerRadius, topEnd = Dimen.SearchBarCornerRadius),
                ).clip(RoundedCornerShape(topStart = Dimen.SearchBarCornerRadius, topEnd = Dimen.SearchBarCornerRadius)),
    ) {
        Column {
            // Drag handle + Header 영역 (드래그 가능)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                with(density) {
                                    onHeightChange(dragAmount.toDp())
                                }
                            }
                        }.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { /* 클릭 효과 없음 */ },
                        ),
            ) {
                // Drag handle
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimen.IconButtonSizeLarge),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(Dimen.IconButtonSizeLarge)
                                .height(Dimen.GridItemSpacing)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(Dimen.Radius2),
                                ),
                    )
                }

                // Header
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimen.ScreenHorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (recommendLoadState is AlbumViewModel.RecommendLoadingState.Success &&
                        recommendLoadState.photos.isNotEmpty() &&
                        selectedRecommendPhotos.isNotEmpty()
                    ) {
                        // 사진 선택 시: Add와 Cancel 버튼
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onResetRecommendSelection) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(
                                onClick = {
                                    onAddPhotosToAlbum(selectedRecommendPhotos.values.toList())
                                    onResetRecommendSelection()
                                    onCollapse()
                                },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                    ),
                                shape = RoundedCornerShape(Dimen.Radius20),
                                contentPadding =
                                    PaddingValues(
                                        horizontal = Dimen.ButtonPaddingLargeHorizontal,
                                        vertical = Dimen.ButtonPaddingVertical,
                                    ),
                            ) {
                                Text(
                                    text =
                                        stringResource(
                                            R.string.album_add_photos_count,
                                            selectedRecommendPhotos.size,
                                            if (selectedRecommendPhotos.size > 1) "s" else "",
                                        ),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StandardIcon.Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                sizeRole = IconSizeRole.Navigation,
                                intent = IconIntent.Primary,
                                contentDescription = stringResource(R.string.cd_ai),
                            )
                            Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                            Text(
                                text = stringResource(R.string.album_ai_recommend),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    IconButton(onClick = onCollapse) {
                        StandardIcon.Icon(
                            imageVector = Icons.Default.ExpandMore,
                            sizeRole = IconSizeRole.DefaultAction,
                            contentDescription = stringResource(R.string.cd_collapse),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = Dimen.ScreenHorizontalPadding)) {
                // Grid / states
                when (recommendLoadState) {
                    is AlbumViewModel.RecommendLoadingState.Loading,
                    is AlbumViewModel.RecommendLoadingState.Idle,
                    -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Dimen.CircularProgressSizeBig),
                                strokeWidth = Dimen.CircularProgressStrokeWidth,
                            )
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Success -> {
                        val recommendPhotos = recommendLoadState.photos
                        if (recommendPhotos.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.album_no_recommendations),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                                verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                                modifier = Modifier.weight(1f),
                                userScrollEnabled = true,
                            ) {
                                items(
                                    count = recommendPhotos.size,
                                    key = { idx -> recommendPhotos[idx].photoId },
                                ) { idx ->
                                    ImageGridUriItem(
                                        photo = recommendPhotos[idx],
                                        navController = navController,
                                        isSelectionMode = true,
                                        isSelected = selectedRecommendPhotoIds.contains(recommendPhotos[idx].photoId),
                                        onToggleSelection = { onToggleRecommendPhoto(recommendPhotos[idx]) },
                                        onLongPress = {},
                                    )
                                }
                            }
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Error -> {
                        val errorMessage =
                            when (recommendLoadState.error) {
                                AlbumViewModel.AlbumError.NetworkError -> stringResource(R.string.error_message_network)
                                AlbumViewModel.AlbumError.Unauthorized -> stringResource(R.string.error_message_authentication_required)
                                AlbumViewModel.AlbumError.NotFound -> stringResource(R.string.error_message_photo_not_found)
                                AlbumViewModel.AlbumError.UnknownError -> stringResource(R.string.error_message_unknown)
                            }
                        Box(modifier = Modifier.fillMaxWidth().height(Dimen.ExpandedPanelHeight), contentAlignment = Alignment.Center) {
                            WarningBanner(
                                modifier = Modifier.fillMaxWidth(),
                                title = stringResource(R.string.album_recommendation_failed),
                                message = errorMessage,
                                onActionClick = { /* TODO: Retry logic needed */ },
                                showActionButton = false,
                                showDismissButton = false,
                            )
                        }
                    }
                    is AlbumViewModel.RecommendLoadingState.Idle -> {
                        Box(modifier = Modifier.fillMaxWidth().height(Dimen.ExpandedPanelHeight), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

private suspend fun PointerInputScope.detectDragAfterLongPressIgnoreConsumed(
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress != null) {
            onDragStart(longPress.position)

            // 롱프레스 이벤트 자체를 첫 드래그로 처리 (HomeScreen과 동일)
            onDrag(longPress)

            // 이어서 드래그 계속 추적
            drag(longPress.id) { change ->
                onDrag(change)
            }
            onDragEnd()
        } else {
            onDragCancel()
        }
    }
}

private fun LazyGridState.findPhotoItemAtPosition(
    position: Offset,
    allPhotos: List<Photo>,
): Pair<String, Photo>? {
    for (itemInfo in layoutInfo.visibleItemsInfo) {
        val key = itemInfo.key
        if (key is String) {
            val itemBounds =
                Rect(
                    itemInfo.offset.x.toFloat(),
                    itemInfo.offset.y.toFloat(),
                    (itemInfo.offset.x + itemInfo.size.width).toFloat(),
                    (itemInfo.offset.y + itemInfo.size.height).toFloat(),
                )
            if (itemBounds.contains(position)) {
                val photo = allPhotos.find { it.photoId == key } ?: continue
                return key to photo
            }
        }
    }
    return null
}
