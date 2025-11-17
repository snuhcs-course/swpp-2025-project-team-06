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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.TagCntData
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.RenameTagDialog
import com.example.momentag.ui.components.TagChipWithCount
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.components.confirmDialog
import com.example.momentag.viewmodel.MyTagsViewModel
import com.example.momentag.viewmodel.TagActionState
import com.example.momentag.viewmodel.TagSortOrder
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MyTagsScreen(
    navController: NavController,
    myTagsViewModel: MyTagsViewModel =
        viewModel(
            factory = ViewModelFactory.getInstance(LocalContext.current),
        ),
) {
    val context = LocalContext.current
    val uiState by myTagsViewModel.uiState.collectAsState()
    val isEditMode by myTagsViewModel.isEditMode.collectAsState()
    val selectedTagsForBulkEdit by myTagsViewModel.selectedTagsForBulkEdit.collectAsState()
    val sortOrder by myTagsViewModel.sortOrder.collectAsState()
    val saveState by myTagsViewModel.saveState.collectAsState()
    val actionState by myTagsViewModel.tagActionState.collectAsState()

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showSortSheet by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    var showEditDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editedTagName by remember { mutableStateOf("") }

    var showAddTagConfirmDialog by remember { mutableStateOf(false) }
    var tagToAddPhotosTo by remember { mutableStateOf<TagCntData?>(null) }

    var showErrorBanner by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
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
            is TagActionState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                showErrorBanner = false
                myTagsViewModel.clearActionState()
            }
            is TagActionState.Error -> {
                errorMessage = state.message
                showErrorBanner = true
                myTagsViewModel.clearActionState()
            }
            is TagActionState.Idle -> {
            }
            is TagActionState.Loading -> {
                // TODO: 필요시 로딩 인디케이터 표시 (현재는 Dialog가 닫히므로 생략)
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
                showErrorBanner = true
                delay(2000) // 2초 후 자동 사라짐
                showErrorBanner = false
            }
            else -> { }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                val currentState = uiState
                CommonTopBar(
                    title = "#Tag",
                    showBackButton = true,
                    onBackClick = {
                        navController.previousBackStackEntry?.savedStateHandle?.set("shouldRefresh", true)
                        navController.popBackStack()
                    },
                    actions = {
                        if (myTagsViewModel.isSelectedPhotosEmpty() &&
                            currentState is MyTagsUiState.Success &&
                            currentState.tags.isNotEmpty()
                        ) {
                            // Sort Button (always visible)
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            // Delete Button (always visible, enters edit mode on click)
                            val hasSelectedTags = selectedTagsForBulkEdit.isNotEmpty()
                            IconButton(
                                onClick = {
                                    if (isEditMode && hasSelectedTags) {
                                        showBulkDeleteConfirm = true
                                    } else if (!isEditMode) {
                                        myTagsViewModel.toggleEditMode()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint =
                                        if (isEditMode && hasSelectedTags) {
                                            MaterialTheme.colorScheme.error
                                        } else if (isEditMode) {
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
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
                    if (showErrorBanner && saveState is MyTagsViewModel.SaveState.Error) {
                        WarningBanner(
                            title = "Save failed",
                            message = (saveState as MyTagsViewModel.SaveState.Error).message,
                            onActionClick = { },
                            onDismiss = { showErrorBanner = false },
                            showActionButton = false,
                            showDismissButton = true,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    AnimatedVisibility(visible = isSelectingForPhotos) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                    ).padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Select a tag or create a new one",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // Create New Tag 버튼
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
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 8.dp)
                                .height(52.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Text(
                            text = "+ Create New Tag",
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }

                    AnimatedVisibility(visible = showErrorBanner && errorMessage != null) {
                        WarningBanner(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 8.dp),
                            title = "Action Failed",
                            message = errorMessage ?: "Unknown error",
                            onActionClick = { showErrorBanner = false },
                            showActionButton = false,
                            showDismissButton = true,
                            onDismiss = { showErrorBanner = false },
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
                        .background(
                            brush =
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0.5f to MaterialTheme.colorScheme.surface,
                                            1.0f to
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                    alpha = 0.7f,
                                                ),
                                        ),
                                ),
                        ).padding(paddingValues),
            ) {
                when (val state = uiState) {
                    is MyTagsUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    is MyTagsUiState.Error -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            WarningBanner(
                                title = "Failed to Load Tags",
                                message = state.message,
                                onActionClick = { myTagsViewModel.refreshTags() },
                                showActionButton = true,
                                showDismissButton = false,
                            )
                        }
                    }

                    is MyTagsUiState.Success -> {
                        MyTagsContent(
                            tags = state.tags,
                            navController = navController,
                            isEditMode = isEditMode,
                            selectedTagsForBulkEdit = selectedTagsForBulkEdit,
                            onToggleTagSelection = { tagId ->
                                myTagsViewModel.toggleTagSelection(tagId)
                            },
                            showBulkDeleteConfirm = showBulkDeleteConfirm,
                            onShowBulkDeleteConfirmChange = { showBulkDeleteConfirm = it },
                            onEditTag = { tagId, tagName ->
                                tagToEdit = Pair(tagId, tagName)
                                editedTagName = tagName
                                showEditDialog = true
                            },
                            onDeleteTag = { tagId, tagName ->
                                tagToDelete = Pair(tagId, tagName)
                                showDeleteDialog = true
                            },
                            onConfirmAddTag = { tagData ->
                                tagToAddPhotosTo = tagData
                                showAddTagConfirmDialog = true
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
        if (saveState == MyTagsViewModel.SaveState.Loading) {
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
                            .size(56.dp)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                clip = false,
                            ),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 5.dp,
                )
            }
        }
    }

    if (showDeleteDialog && tagToDelete != null) {
        confirmDialog(
            title = "Delete Tag",
            message = "Are you sure you want to delete '${tagToDelete?.second}' tag?",
            confirmButtonText = "Delete",
            onConfirm = {
                tagToDelete?.first?.let { myTagsViewModel.deleteTag(it) }
                showDeleteDialog = false
                tagToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                tagToDelete = null
            },
            dismissible = true,
        )
    }
    if (showEditDialog && tagToEdit != null) {
        RenameTagDialog(
            title = "Edit Tag Name",
            message = "Enter the new tag name",
            initialValue = editedTagName,
            onConfirm = { newName ->
                if (newName.isNotBlank()) {
                    tagToEdit?.first?.let { tagId ->
                        myTagsViewModel.renameTag(tagId, newName)
                    }
                }
                showEditDialog = false
                tagToEdit = null
                editedTagName = ""
            },
            onDismiss = {
                showEditDialog = false
                tagToEdit = null
                editedTagName = ""
            },
            dismissible = true,
        )
    }

    if (showAddTagConfirmDialog && tagToAddPhotosTo != null) {
        confirmDialog(
            title = "Add to Tag",
            message = "Are you sure you want to add the selected photos to '${tagToAddPhotosTo?.tagName}'?",
            confirmButtonText = "Add",
            onConfirm = {
                tagToAddPhotosTo?.tagId?.let {
                    myTagsViewModel.savePhotosToExistingTag(it)
                }
                showAddTagConfirmDialog = false
                tagToAddPhotosTo = null
            },
            onDismiss = {
                showAddTagConfirmDialog = false
                tagToAddPhotosTo = null
            },
            dismissible = true,
        )
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = sheetState,
        ) {
            SortOptionsSheet(
                currentOrder = sortOrder,
                onOrderChange = { newOrder ->
                    myTagsViewModel.setSortOrder(newOrder)
                    scope.launch {
                        sheetState.hide()
                        showSortSheet = false
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
    showBulkDeleteConfirm: Boolean,
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
        // 글로벌 선택 모드 진입 시에도 개별 편집 모드 해제
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
                            // 빈 공간 클릭 시 개별 편집 모드도 해제
                            individualEditTagId = null
                        }.padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isSelectingTagForPhotos) "Choose Tag to add photos" else "My Tags",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (tags.size >= 2) "${tags.size} tags" else "${tags.size} tag",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tagData ->
                        val tagModifier =
                            if (isSelectingTagForPhotos) {
                                Modifier.shadow(elevation = 4.dp, shape = RoundedCornerShape(16.dp))
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

                Spacer(modifier = Modifier.height(80.dp))
            }

            // Bulk delete confirmation dialog
            if (showBulkDeleteConfirm && selectedTagsForBulkEdit.isNotEmpty()) {
                confirmDialog(
                    title = "Delete Tags",
                    message = "Are you sure you want to delete ${selectedTagsForBulkEdit.size} tag(s)?",
                    confirmButtonText = "Delete",
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
                    contentDescription = "Empty Tag",
                    modifier =
                        Modifier
                            .size(200.dp)
                            .rotate(45f),
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Create memories",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Organize your memories\nby keyword",
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
        Color(0xFF5A8DE1), // 미디엄 블루
        Color(0xFFC0405A), // 딥 로즈
        Color(0xFF759D5A), // 모스 그린
        Color(0xFF865FAD), // 미디엄 퍼플
        Color(0xFFD1A82A), // 머스타드/골드
        Color(0xFF5F9191), // 미디엄 틸
        Color(0xFFD9661E), // 번트 오렌지
    )

private fun getTagColor(tagId: String): Color = tagColors[abs(tagId.hashCode()) % tagColors.size]

@Composable
private fun SortOptionsSheet(
    currentOrder: TagSortOrder,
    onOrderChange: (TagSortOrder) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            "Sort by",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SortOptionItem(
            text = "Name (A-Z)",
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.NAME_ASC,
            onClick = { onOrderChange(TagSortOrder.NAME_ASC) },
        )
        SortOptionItem(
            text = "Name (Z-A)",
            icon = Icons.Default.ArrowDownward,
            isSelected = currentOrder == TagSortOrder.NAME_DESC,
            onClick = { onOrderChange(TagSortOrder.NAME_DESC) },
        )
        SortOptionItem(
            text = "Recently Added",
            icon = Icons.Default.FiberNew,
            isSelected = currentOrder == TagSortOrder.CREATED_DESC,
            onClick = { onOrderChange(TagSortOrder.CREATED_DESC) },
        )
        SortOptionItem(
            text = "Count (Descending)",
            icon = Icons.Default.ArrowUpward,
            isSelected = currentOrder == TagSortOrder.COUNT_DESC,
            onClick = { onOrderChange(TagSortOrder.COUNT_DESC) },
        )
        SortOptionItem(
            text = "Count (Ascending)",
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
