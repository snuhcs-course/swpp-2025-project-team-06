package com.example.momentag

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.TagCntData
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.viewmodel.MyTagsViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyTagsScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MyTagsViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
    val uiState by viewModel.uiState.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var tagToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    var showEditDialog by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editedTagName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CommonTopBar(
                title = "#Tag",
                showBackButton = true,
                onBackClick = { navController.popBackStack() },
                actions = {
                    if (uiState is MyTagsUiState.Success && uiState.tags.isNotEmpty()) {
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
                // Create New Tag 버튼
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

                // Bottom Navigation Bar
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
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Error: ${state.message}",
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.refreshTags() }) {
                                Text("Retry")
                            }
                        }
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

    // 삭제 확인 다이얼로그
    if (showDeleteDialog && tagToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "태그 삭제",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = "'${tagToDelete?.second}' 태그를 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tagToDelete?.first?.let { viewModel.deleteTag(it) }
                        Toast.makeText(context, "삭제되었습니다", Toast.LENGTH_SHORT).show()
                        showDeleteDialog = false
                        tagToDelete = null
                    },
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        tagToDelete = null
                    },
                ) {
                    Text("취소")
                }
            },
        )
    }

    // 태그 수정 다이얼로그
    if (showEditDialog && tagToEdit != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = "태그 이름 수정",
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Column {
                    Text(
                        text = "새로운 태그 이름을 입력해주세요",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = editedTagName,
                        onValueChange = { editedTagName = it },
                        singleLine = true,
                        placeholder = { Text("태그 이름") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editedTagName.isNotBlank()) {
                            tagToEdit?.first?.let { tagId ->
                                viewModel.renameTag(tagId, editedTagName.trim())
                            }
                        }
                        showEditDialog = false
                        tagToEdit = null
                        editedTagName = ""
                    },
                    enabled = editedTagName.isNotBlank(),
                ) {
                    Text("수정")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEditDialog = false
                        tagToEdit = null
                        editedTagName = ""
                    },
                ) {
                    Text("취소")
                }
            },
        )
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
            // 태그가 있을 때 - 스크롤 가능한 컨텐츠
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

                // My Tags 헤더
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
                    tags.forEachIndexed { index, tagData ->
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

                Spacer(modifier = Modifier.height(80.dp)) // 하단 버튼 공간 확보
            }
        } else {
            // 태그가 없을 때 - Empty State (Pull-to-Refresh 지원을 위해 스크롤 가능한 컨텐츠로 변경)
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()), // 스크롤 추가로 Pull-to-Refresh 활성화
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
                    text = "태그를 만들어보세요",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "키워드로 추억을\n모아보세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
