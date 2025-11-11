package com.example.momentag

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.TagCntData
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.components.RenameTagDialog
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.components.confirmDialog
import com.example.momentag.viewmodel.MyTagsViewModel
import com.example.momentag.viewmodel.TagSortOrder
import com.example.momentag.viewmodel.ViewModelFactory
import com.example.momentag.viewmodel.TagActionState
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MyTagsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MyTagsViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val uiState by viewModel.uiState.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val actionState by viewModel.tagActionState.collectAsState()

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    var showSortSheet by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    var showEditDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editedTagName by remember { mutableStateOf("") }

    var showErrorBanner by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTags()
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
                viewModel.clearActionState()
            }
            is TagActionState.Error -> {
                errorMessage = state.message
                showErrorBanner = true
                viewModel.clearActionState()
            }
            is TagActionState.Idle -> {
            }
            is TagActionState.Loading -> {
                // TODO: 필요시 로딩 인디케이터 표시 (현재는 Dialog가 닫히므로 생략)
            }
        }
    }

    Scaffold(
        topBar = {
            val currentState = uiState
            CommonTopBar(
                title = "#Tag",
                showBackButton = true,
                onBackClick = { navController.popBackStack() },
                actions = {
                    if (currentState is MyTagsUiState.Success && currentState.tags.isNotEmpty()) {
                        IconButton(onClick = { showSortSheet = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                Button(
                    onClick = {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        title = "Action Failed",
                        message = errorMessage ?: "Unknown error",
                        onActionClick = { showErrorBanner = false },
                        showActionButton = false,
                        showDismissButton = true,
                        onDismiss = { showErrorBanner = false }
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
                            BottomTab.HomeScreen -> navController.navigate(Screen.Home.route)
                            BottomTab.SearchResultScreen -> navController.navigate(Screen.SearchResult.createRoute(""))
                            BottomTab.MyTagsScreen -> { /* 현재 화면이므로 아무것도 안 함 */ }
                            BottomTab.StoryScreen -> navController.navigate(Screen.Story.route)
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
                                        1.0f to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
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
                            onActionClick = { viewModel.refreshTags() },
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
                        onEditTag = { tagId, tagName ->
                            tagToEdit = Pair(tagId, tagName)
                            editedTagName = tagName
                            showEditDialog = true
                        },
                        onDeleteTag = { tagId, tagName ->
                            tagToDelete = Pair(tagId, tagName)
                            showDeleteDialog = true
                        },
                        onRefresh = { viewModel.refreshTags() },
                        onEnterEditMode = { viewModel.toggleEditMode() },
                        onExitEditMode = { if (isEditMode) viewModel.toggleEditMode() },
                    )
                }
            }
        }
    }

    if (showDeleteDialog && tagToDelete != null) {
        confirmDialog(
            title = "Delete Tag",
            message = "Are you sure you want to delete '${tagToDelete?.second}' tag?",
            confirmButtonText = "Delete",
            onConfirm = {
                tagToDelete?.first?.let { viewModel.deleteTag(it) }
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
                        viewModel.renameTag(tagId, newName)
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
            dismissible = true
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
                    viewModel.setSortOrder(newOrder)
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
    onEditTag: (String, String) -> Unit = { _, _ -> },
    onDeleteTag: (String, String) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onEnterEditMode: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

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
                                    androidx.compose.foundation.interaction
                                        .MutableInteractionSource()
                                },
                        ) {
                            onExitEditMode()
                        }.padding(horizontal = 24.dp),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "My Tags",
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
                        TagChipWithCount(
                            tagName = tagData.tagName,
                            count = tagData.count,
                            color = getTagColor(tagData.tagId),
                            onClick = {
                                if (!isEditMode) {
                                    navController.navigate(
                                        Screen.Album.createRoute(
                                            tagId = tagData.tagId,
                                            tagName = tagData.tagName,
                                        ),
                                    )
                                }
                            },
                            isEditMode = isEditMode,
                            onEdit = { onEditTag(tagData.tagId, tagData.tagName) },
                            onDelete = { onDeleteTag(tagData.tagId, tagData.tagName) },
                            onLongClick = { onEnterEditMode() },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

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