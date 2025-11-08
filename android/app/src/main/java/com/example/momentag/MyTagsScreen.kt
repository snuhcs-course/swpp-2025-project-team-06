package com.example.momentag

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.TagCntData
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.viewmodel.MyTagsViewModel
import com.example.momentag.viewmodel.ViewModelFactory

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
                    IconButton(onClick = { viewModel.toggleEditMode() }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        bottomBar = {
            BottomNavBar(
                currentTab = BottomTab.AddTagScreen,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomTab.HomeScreen -> navController.navigate(Screen.Home.route)
                        BottomTab.SearchResultScreen -> navController.navigate(Screen.SearchResult.createRoute(""))
                        BottomTab.AddTagScreen -> { /* 현재 화면이므로 아무것도 안 함 */ }
                        BottomTab.StoryScreen -> navController.navigate(Screen.Story.route)
                    }
                },
            )
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
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

        // 태그가 있을 때
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEachIndexed { index, tagData ->
                    TagChipWithCount(
                        tagName = tagData.tagName,
                        count = tagData.count,
                        color = getTagColor(index),
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
                    )
                }
            }
        } else {
                    // 태그가 없을 때 - Empty State
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
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

                Spacer(modifier = Modifier.weight(1f))

                // Create New Tag Button
                Button(
                    onClick = {
                        navController.navigate(Screen.AddTag.route)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Text(
                        text = "+ Create New Tag",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }



// UI 디자인의 색상들을 순서대로 할당
private val tagColors =
    listOf(
        Color(0xFF000000), // Black for #302
        Color(0xFFFF9B9B), // Light Coral for #cozy
        Color(0xFF9BBAFF), // Blue for #game
        Color(0xFF9BFFB0), // Green for #study
        Color(0xFF9BFFE5), // Mint for #cool
        Color(0xFFFFE59B), // Yellow for #hobby
        Color(0xFFFFB0E5), // Pink for #dream
        Color(0xFFFF9B9B), // Coral for #home
    )

private fun getTagColor(index: Int): Color {
    return tagColors.getOrElse(index) { tagColors[index % tagColors.size] }
}
