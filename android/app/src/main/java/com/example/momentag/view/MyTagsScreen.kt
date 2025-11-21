package com.example.momentag.view

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.TagCntData
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.ConfirmDialog
import com.example.momentag.ui.components.RenameTagDialog
import com.example.momentag.ui.components.TagChipWithCount
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.viewmodel.MyTagsViewModel
import com.example.momentag.viewmodel.TagSortOrder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MyTagsScreen(navController: NavController) {
    // 1. Context and Platform-related variables
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 2. ViewModel instance
    val myTagsViewModel: MyTagsViewModel = hiltViewModel()

    // 3. States collected from ViewModel (collectAsState)
    val uiState by myTagsViewModel.uiState.collectAsState()
    val isEditMode by myTagsViewModel.isEditMode.collectAsState()
    val selectedTagsForBulkEdit by myTagsViewModel.selectedTagsForBulkEdit.collectAsState()
    val sortOrder by myTagsViewModel.sortOrder.collectAsState()
    val saveState by myTagsViewModel.saveState.collectAsState()
    val actionState by myTagsViewModel.tagActionState.collectAsState()

    // 4. 로컬 상태 변수
    var isSortSheetVisible by remember { mutableStateOf(false) }
    var isDeleteDialogVisible by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isEditDialogVisible by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editedTagName by remember { mutableStateOf("") }
    var isAddTagConfirmDialogVisible by remember { mutableStateOf(false) }
    var tagToAddPhotosTo by remember { mutableStateOf<TagCntData?>(null) }
    var isErrorBannerVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBulkDeleteConfirmVisible by remember { mutableStateOf(false) }

    // 5. rememberCoroutineScope
    val scope = rememberCoroutineScope()

    // 6. Remembered objects
    val sheetState = rememberModalBottomSheetState()
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    myTagsViewModel.refreshTags()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(actionState) {
        when (val state = actionState) {
            is MyTagsViewModel.TagActionState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                isErrorBannerVisible = false
                myTagsViewModel.clearActionState()
            }
            is MyTagsViewModel.TagActionState.Error -> {
                errorMessage = context.getString(state.error.toMessageResId())
                isErrorBannerVisible = true
                myTagsViewModel.clearActionState()
            }
            is MyTagsViewModel.TagActionState.Idle -> {
            }
            is MyTagsViewModel.TagActionState.Loading -> {
                // TODO: Show loading indicator if needed (currently skipped as Dialog closes)
            }
        }
    }

    BackHandler(enabled = true) {
        if (isEditMode) {
            myTagsViewModel.toggleEditMode()
        } else {
            navController.previousBackStackEntry?.savedStateHandle?.set("shouldRefresh", true)
            navController.popBackStack()
        }
    }

    val isSelectingForPhotos by remember(uiState) {
        mutableStateOf(!myTagsViewModel.isSelectedPhotosEmpty())
    }

    LaunchedEffect(saveState) {
        when (saveState) {
            is MyTagsViewModel.SaveState.Success -> {
                navController.previousBackStackEntry?.savedStateHandle?.set("selectionModeComplete", true)
                myTagsViewModel.clearDraft()
                myTagsViewModel.refreshTags()
            }
            is MyTagsViewModel.SaveState.Error -> {
                isErrorBannerVisible = true
                delay(2000) // Auto-dismiss after 2 seconds
                isErrorBannerVisible = false
            }
            else -> { }
        }
    }

    val backgroundBrush = rememberAppBackgroundBrush()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                val currentState = uiState
                CommonTopBar(
                    title = stringResource(R.string.tag_screen_title),
                    showBackButton = true,
                    onBackClick = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("shouldRefresh", true)
                        navController.popBackStack()
                    },
                    actions = {
                        if (myTagsViewModel.isSelectedPhotosEmpty() &&
                            currentState is MyTagsViewModel.MyTagsUiState.Success &&
                            currentState.tags.isNotEmpty()
                        ) {
                            // Sort Button (always visible)
                            IconButton(onClick = { isSortSheetVisible = true }) {
                                StandardIcon.Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    sizeRole = IconSizeRole.DefaultAction,
                                    contentDescription = stringResource(R.string.cd_sort_action),
                                )
                            }
                            // Delete Button (always visible, enters edit mode on click)
                            val hasSelectedTags = selectedTagsForBulkEdit.isNotEmpty()
                            IconButton(
                                onClick = {
                                    if (isEditMode && hasSelectedTags) {
                                        isBulkDeleteConfirmVisible = true
                                    } else if (!isEditMode) {
                                        myTagsViewModel.toggleEditMode()
                                    }
                                },
                            ) {
                                val deleteIntent =
                                    when {
                                        isEditMode && hasSelectedTags -> IconIntent.Error
                                        isEditMode -> IconIntent.Disabled
                                        else -> IconIntent.Neutral
                                    }
                                StandardIcon.Icon(
                                    imageVector = Icons.AutoMirrored.Filled.LabelOff,
                                    contentDescription = stringResource(R.string.cd_delete_action),
                                    intent = deleteIntent,
                                )
                            }
                        }
                    },
                )
            },
            bottomBar = {
                Column(
                    modifier =
                        Modifier
                            .background(Color.Transparent)
                            .fillMaxWidth(),
                ) {
                    // Error Banner
                    AnimatedVisibility(
                        visible = isErrorBannerVisible && saveState is MyTagsViewModel.SaveState.Error,
                        enter = Animation.EnterFromBottom,
                        exit = Animation.ExitToBottom,
                    ) {
                        WarningBanner(
                            title = stringResource(R.string.error_title_save_failed),
                            message = stringResource((saveState as MyTagsViewModel.SaveState.Error).error.toMessageResId()),
                            onActionClick = { },
                            onDismiss = { isErrorBannerVisible = false },
                            showActionButton = false,
                            showDismissButton = true,
                        )
                        Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                    }

                    AnimatedVisibility(
                        visible = isSelectingForPhotos,
                        enter = Animation.EnterFromBottom,
                        exit = Animation.ExitToBottom,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimen.ScreenHorizontalPadding)
                                    .padding(bottom = Dimen.ItemSpacingSmall)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
                                    ).padding(horizontal = Dimen.ButtonPaddingHorizontal, vertical = Dimen.ButtonPaddingVertical),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.empty_state_select_tag),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // Create New Tag button
                    Button(
                        onClick = {
                            navController.previousBackStackEntry?.savedStateHandle?.set(
                                "selectionModeComplete",
                                true,
                            )
                            navController.navigate(Screen.AddTag.route)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimen.ScreenHorizontalPadding)
                                .padding(top = Dimen.ItemSpacingSmall, bottom = Dimen.ItemSpacingSmall)
                                .height(Dimen.ButtonHeightLarge),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
                    ) {
                        Text(
                            text = stringResource(R.string.tag_create_new),
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }

                    AnimatedVisibility(
                        visible = isErrorBannerVisible && errorMessage != null,
                        enter = Animation.EnterFromBottom,
                        exit = Animation.ExitToBottom,
                    ) {
                        WarningBanner(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimen.ScreenHorizontalPadding)
                                    .padding(bottom = Dimen.ItemSpacingSmall),
                            title = stringResource(R.string.error_title_action_failed),
                            message = errorMessage ?: stringResource(R.string.error_message_unknown),
                            onActionClick = { isErrorBannerVisible = false },
                            showActionButton = false,
                            showDismissButton = true,
                            onDismiss = { isErrorBannerVisible = false },
                        )
                    }

                    BottomNavBar(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    WindowInsets.navigationBars
                                        .only(WindowInsetsSides.Bottom)
                                        .asPaddingValues(),
                                ),
                        currentTab = BottomTab.MyTagsScreen,
                        onTabSelected = { tab ->
                            when (tab) {
                                BottomTab.HomeScreen -> {
                                    myTagsViewModel.clearDraft()
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }

                                BottomTab.MyTagsScreen -> {
                                }

                                BottomTab.StoryScreen -> {
                                    myTagsViewModel.clearDraft()
                                    navController.navigate(Screen.Story.route) {
                                        popUpTo(Screen.Home.route)
                                    }
                                }
                            }
                        },
                    )
                }
            },
        ) { paddingValues ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(backgroundBrush)
                        .padding(paddingValues),
            ) {
                when (val state = uiState) {
                    is MyTagsViewModel.MyTagsUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is MyTagsViewModel.MyTagsUiState.Error -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = Dimen.FormScreenHorizontalPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            WarningBanner(
                                title = stringResource(R.string.error_title_load_tags_failed),
                                message = stringResource(state.error.toMessageResId()),
                                onActionClick = { myTagsViewModel.refreshTags() },
                                showActionButton = true,
                                showDismissButton = false,
                            )
                        }
                    }

                    is MyTagsViewModel.MyTagsUiState.Success -> {
                        MyTagsContent(
                            tags = state.tags,
                            navController = navController,
                            isEditMode = isEditMode,
                            selectedTagsForBulkEdit = selectedTagsForBulkEdit,
                            onToggleTagSelection = { tagId ->
                                myTagsViewModel.toggleTagSelection(tagId)
                            },
                            isBulkDeleteConfirmVisible = isBulkDeleteConfirmVisible,
                            onShowBulkDeleteConfirmChange = { isBulkDeleteConfirmVisible = it },
                            onEditTag = { tagId, tagName ->
                                tagToEdit = Pair(tagId, tagName)
                                editedTagName = tagName
                                isEditDialogVisible = true
                            },
                            onDeleteTag = { tagId, tagName ->
                                tagToDelete = Pair(tagId, tagName)
                                isDeleteDialogVisible = true
                            },
                            onConfirmAddTag = { tagData ->
                                tagToAddPhotosTo = tagData
                                isAddTagConfirmDialogVisible = true
                            },
                            onRefresh = { myTagsViewModel.refreshTags() },
                            onEnterEditMode = { myTagsViewModel.toggleEditMode() },
                            onExitEditMode = { if (isEditMode) myTagsViewModel.toggleEditMode() },
                            onDeleteSelectedTags = { selectedTagIds ->
                                // Delete each selected tag
                                selectedTagIds.forEach { tagId ->
                                    state.tags.find { it.tagId == tagId }?.tagName ?: ""
                                    myTagsViewModel.deleteTag(tagId)
                                }
                            },
                            myTagsViewModel = myTagsViewModel,
                        )
                    }
                }
            }
        }

        // Full Screen Loading Overlay
        AnimatedVisibility(
            visible = saveState == MyTagsViewModel.SaveState.Loading,
            enter = Animation.DefaultFadeIn,
            exit = Animation.DefaultFadeOut,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(Dimen.BottomNavBarHeight)
                            .shadow(
                                elevation = Dimen.BottomNavShadowElevation,
                                shape = CircleShape,
                                clip = false,
                            ),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = Dimen.CircularProgressStrokeWidthBig,
                )
            }
        }
    }

    if (isDeleteDialogVisible && tagToDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_tag_title),
            message = stringResource(R.string.dialog_delete_tag_message, tagToDelete?.second ?: ""),
            confirmButtonText = stringResource(R.string.action_delete),
            onConfirm = {
                tagToDelete?.first?.let { myTagsViewModel.deleteTag(it) }
                isDeleteDialogVisible = false
                tagToDelete = null
            },
            onDismiss = {
                isDeleteDialogVisible = false
                tagToDelete = null
            },
            dismissible = true,
        )
    }
    if (isEditDialogVisible && tagToEdit != null) {
        RenameTagDialog(
            title = stringResource(R.string.dialog_rename_tag_title),
            message = stringResource(R.string.dialog_rename_tag_message),
            initialValue = editedTagName,
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    tagToEdit?.first?.let { tagId ->
                        myTagsViewModel.renameTag(tagId, newName)
                    }
                }
                isEditDialogVisible = false
                tagToEdit = null
                editedTagName = ""
            },
            onDismiss = {
                isEditDialogVisible = false
                tagToEdit = null
                editedTagName = ""
            },
            dismissible = true,
        )
    }

    if (isAddTagConfirmDialogVisible && tagToAddPhotosTo != null) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_add_to_tag_title),
            message = stringResource(R.string.dialog_add_to_tag_message, tagToAddPhotosTo?.tagName ?: ""),
            confirmButtonText = stringResource(R.string.dialog_add),
            onConfirm = {
                tagToAddPhotosTo?.tagId?.let {
                    myTagsViewModel.savePhotosToExistingTag(it)
                }
                isAddTagConfirmDialogVisible = false
                tagToAddPhotosTo = null
            },
            onDismiss = {
                isAddTagConfirmDialogVisible = false
                tagToAddPhotosTo = null
            },
            dismissible = true,
        )
    }

    if (isSortSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isSortSheetVisible = false },
            sheetState = sheetState,
        ) {
            SortOptionsSheet(
                currentOrder = sortOrder,
                onOrderChange = { newOrder ->
                    myTagsViewModel.setSortOrder(newOrder)
                    scope.launch {
                        sheetState.hide()
                        isSortSheetVisible = false
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun MyTagsContent(
    tags: List<TagCntData>,
    navController: NavController,
    isEditMode: Boolean = false,
    selectedTagsForBulkEdit: Set<String>,
    onToggleTagSelection: (String) -> Unit,
    isBulkDeleteConfirmVisible: Boolean,
    onShowBulkDeleteConfirmChange: (Boolean) -> Unit,
    onEditTag: (String, String) -> Unit = { _, _ -> },
    onDeleteTag: (String, String) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onEnterEditMode: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
    onConfirmAddTag: (TagCntData) -> Unit = {},
    onDeleteSelectedTags: (Set<String>) -> Unit = {},
    myTagsViewModel: MyTagsViewModel,
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val isSelectingTagForPhotos = !myTagsViewModel.isSelectedPhotosEmpty()

    // Track which tags are in individual edit mode (long-pressed)
    var individualEditTagId by remember { mutableStateOf<String?>(null) }

    // Exit individual edit mode when entering/exiting global edit mode
    LaunchedEffect(isEditMode) {
        // Exit individual edit mode when entering global selection mode
        individualEditTagId = null
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            onRefresh()
            isRefreshing = false
        },
        state = pullToRefreshState,
    ) {
        if (tags.isNotEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .clickable(
                            indication = null,
                            interactionSource =
                                remember {
                                    MutableInteractionSource()
                                },
                        ) {
                            onExitEditMode()
                            // Exit individual edit mode when clicking empty space
                            individualEditTagId = null
                        }.padding(horizontal = Dimen.FormScreenHorizontalPadding),
            ) {
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            if (isSelectingTagForPhotos) {
                                stringResource(R.string.tag_choose_to_add_photos)
                            } else {
                                stringResource(R.string.tag_my_tags_title)
                            },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            if (tags.size >= 2) {
                                stringResource(R.string.tag_count_plural, tags.size)
                            } else {
                                stringResource(R.string.tag_count_singular, tags.size)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingLarge))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                    verticalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
                ) {
                    tags.forEach { tagData ->
                        val tagModifier =
                            if (isSelectingTagForPhotos) {
                                Modifier.shadow(
                                    elevation = Dimen.BottomNavTonalElevation,
                                    shape = RoundedCornerShape(Dimen.TagCornerRadius),
                                )
                            } else {
                                Modifier
                            }

                        val isThisTagInEditMode = individualEditTagId == tagData.tagId

                        TagChipWithCount(
                            modifier = tagModifier,
                            tagName = tagData.tagName,
                            count = tagData.count,
                            color = getTagColor(tagData.tagId),
                            onClick = {
                                if (isSelectingTagForPhotos) {
                                    onConfirmAddTag(tagData)
                                } else if (isEditMode && !isThisTagInEditMode) {
                                    // In global edit mode, toggle checkbox selection
                                    onToggleTagSelection(tagData.tagId)
                                } else if (!isEditMode && !isThisTagInEditMode) {
                                    navController.navigate(
                                        Screen.Album.createRoute(
                                            tagId = tagData.tagId,
                                            tagName = tagData.tagName,
                                        ),
                                    )
                                }
                            },
                            isEditMode = isThisTagInEditMode,
                            onEdit = { onEditTag(tagData.tagId, tagData.tagName) },
                            onDelete = { onDeleteTag(tagData.tagId, tagData.tagName) },
                            onLongClick = {
                                if (isThisTagInEditMode) {
                                    // Exit individual edit mode for this tag
                                    individualEditTagId = null
                                } else {
                                    // Enter individual edit mode for this tag
                                    individualEditTagId = tagData.tagId
                                }
                            },
                            showCheckbox = isEditMode && !isThisTagInEditMode,
                            isChecked = selectedTagsForBulkEdit.contains(tagData.tagId),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Dimen.FloatingButtonAreaPadding))
            }

            // Bulk delete confirmation dialog
            if (isBulkDeleteConfirmVisible && selectedTagsForBulkEdit.isNotEmpty()) {
                ConfirmDialog(
                    title = stringResource(R.string.dialog_delete_tags_title),
                    message = stringResource(R.string.dialog_delete_tags_message, selectedTagsForBulkEdit.size),
                    confirmButtonText = stringResource(R.string.action_delete),
                    onConfirm = {
                        onDeleteSelectedTags(selectedTagsForBulkEdit)
                        onShowBulkDeleteConfirmChange(false)
                        onExitEditMode()
                    },
                    onDismiss = {
                        onShowBulkDeleteConfirmChange(false)
                    },
                    dismissible = true,
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.tag),
                    contentDescription = stringResource(R.string.cd_empty_tag),
                    modifier =
                        Modifier
                            .size(Dimen.FloatingButtonAreaPaddingLarge)
                            .rotate(45f),
                )

                Spacer(modifier = Modifier.height(Dimen.SectionSpacing))

                Text(
                    text = stringResource(R.string.tag_create_memories),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                Text(
                    text = stringResource(R.string.tag_organize_memories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val tagColors =
    listOf(
        Color(0xFF5A8DE1), // Medium blue
        Color(0xFFC0405A), // Deep rose
        Color(0xFF759D5A), // Moss green
        Color(0xFF865FAD), // Medium purple
        Color(0xFFD1A82A), // Mustard/gold
        Color(0xFF5F9191), // Medium teal
        Color(0xFFD9661E), // Burnt orange
    )

private fun getTagColor(tagId: String): Color = tagColors[abs(tagId.hashCode()) % tagColors.size]

@Composable
private fun SortOptionsSheet(
    currentOrder: TagSortOrder,
    onOrderChange: (TagSortOrder) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = Dimen.ItemSpacingLarge)) {
        Text(
            stringResource(R.string.tag_sort_by),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Dimen.ScreenHorizontalPadding, vertical = Dimen.ItemSpacingSmall),
        )

        SortOptionItem(
            text = stringResource(R.string.tag_sort_name_az),
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.NAME_ASC,
            onClick = { onOrderChange(TagSortOrder.NAME_ASC) },
        )
        SortOptionItem(
            text = stringResource(R.string.tag_sort_name_za),
            icon = Icons.Default.ArrowDownward,
            isSelected = currentOrder == TagSortOrder.NAME_DESC,
            onClick = { onOrderChange(TagSortOrder.NAME_DESC) },
        )
        SortOptionItem(
            text = stringResource(R.string.tag_sort_recently_added),
            icon = Icons.Default.FiberNew,
            isSelected = currentOrder == TagSortOrder.CREATED_DESC,
            onClick = { onOrderChange(TagSortOrder.CREATED_DESC) },
        )
        SortOptionItem(
            text = stringResource(R.string.tag_sort_count_desc),
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.COUNT_DESC,
            onClick = { onOrderChange(TagSortOrder.COUNT_DESC) },
        )
        SortOptionItem(
            text = stringResource(R.string.tag_sort_count_asc),
            icon = Icons.Default.ArrowDownward,
            isSelected = currentOrder == TagSortOrder.COUNT_ASC,
            onClick = { onOrderChange(TagSortOrder.COUNT_ASC) },
        )
    }
}

@Composable
private fun SortOptionItem(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Dimen.ScreenHorizontalPadding, vertical = Dimen.ItemSpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val optionIntent = if (isSelected) IconIntent.Primary else IconIntent.Muted
        StandardIcon.Icon(
            imageVector = icon,
            contentDescription = text,
            intent = optionIntent,
        )
        Spacer(modifier = Modifier.width(Dimen.ItemSpacingLarge))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            StandardIcon.Icon(
                imageVector = Icons.Default.Check,
                intent = IconIntent.Primary,
                contentDescription = stringResource(R.string.cd_photo_selected),
            )
        }
    }
}
