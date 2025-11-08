
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.momentag.ui.storytag

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.StoryTagChip
import com.example.momentag.model.StoryModel
import com.example.momentag.model.StoryState
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.viewmodel.StoryViewModel
import com.example.momentag.viewmodel.SubmissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =============== Screen ===============

@Composable
fun StoryTagSelectionScreen(
    viewModel: StoryViewModel,
    onBack: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val storyState by viewModel.storyState.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val submissionStates by viewModel.submissionStates.collectAsState()
    val viewedStories by viewModel.viewedStories.collectAsState()
    val editModeStory by viewModel.editModeStory.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf(BottomTab.StoryScreen) }

    // Load initial stories
    LaunchedEffect(Unit) {
        if (storyState is StoryState.Idle) {
            viewModel.loadStories(10)
        }
    }

    // Handle different states
    when (val state = storyState) {
        is StoryState.Idle -> {
            // Show nothing while idle
        }
        is StoryState.Loading -> {
            // Show loading screen
            Box(
                modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("추억을 불러오는 중...", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        is StoryState.Success -> {
            val stories = state.stories
            val pagerState = rememberPagerState(pageCount = { stories.size })

            // Track previous page to detect scroll direction
            var previousPage by remember { mutableStateOf(0) }

            // Lazy-load tags for current page
            val currentPage = pagerState.currentPage
            LaunchedEffect(currentPage) {
                if (currentPage < stories.size) {
                    val currentStory = stories[currentPage]
                    viewModel.loadTagsForStory(currentStory.id, currentStory.photoId)
                }
            }

            // Handle scroll: mark as viewed when scrolling down, exit edit mode when scrolling
            LaunchedEffect(currentPage) {
                if (currentPage != previousPage) {
                    // Exit edit mode for any story being edited
                    editModeStory?.let { editingStoryId ->
                        viewModel.exitEditMode(editingStoryId)
                    }

                    // Mark story as viewed when scrolling down (to next page)
                    if (currentPage > previousPage && previousPage < stories.size) {
                        val previousStory = stories[previousPage]
                        viewModel.markStoryAsViewed(previousStory.id)
                    }
                }
                previousPage = currentPage
            }

            // Detect when to load more stories (when approaching the last page)
            LaunchedEffect(currentPage) {
                if (currentPage >= stories.size - 2 && state.hasMore) {
                    viewModel.loadMoreStories(10)
                }
            }

            Column(
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
            ) {
                // 상단 앱바
                BackTopBar(
                    title = "Moment",
                    onBackClick = {
                        viewModel.resetState()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // 스토리 페이저 (페이지 기반 스크롤)
                val flingBehavior =
                    PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapPositionalThreshold = 0.35f, // 35% 스크롤하면 다음 페이지로 넘어감 (기본값 50%)
                    )

                VerticalPager(
                    state = pagerState,
                    flingBehavior = flingBehavior,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) { page ->
                    val story = stories[page]
                    val isFirstStory = page == 0
                    val selectedForThisStory = selectedTags[story.id] ?: emptySet()
                    val submissionState = submissionStates[story.id] ?: SubmissionState.Idle
                    val isViewed = viewedStories.contains(story.id)
                    val isEditMode = editModeStory == story.id

                    // 각 스토리 페이지
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // 스토리 컨텐츠 영역
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            ) {
                                StoryPageFullBlock(
                                    story = story,
                                    showScrollHint = isFirstStory,
                                )
                            }

                            // 태그 선택 카드
                            TagSelectionCard(
                                tags = story.suggestedTags,
                                selectedTags = selectedForThisStory,
                                submissionState = submissionState,
                                isViewed = isViewed,
                                isEditMode = isEditMode,
                                onTagToggle = { tag ->
                                    viewModel.toggleTag(story.id, tag)
                                },
                                onDone = {
                                    viewModel.submitTagsForStory(story.id)
                                },
                                onRetry = {
                                    viewModel.submitTagsForStory(story.id)
                                },
                                onEdit = {
                                    viewModel.enterEditMode(story.id, story.photoId)
                                },
                                onSuccess = {
                                    // Auto-advance to next story after success animation
                                    coroutineScope.launch {
                                        // Brief delay to show checkmark
                                        delay(500)

                                        // Clear edit mode and mark as viewed after showing checkmark
                                        viewModel.clearEditMode()
                                        viewModel.markStoryAsViewed(story.id)

                                        if (page < stories.lastIndex) {
                                            pagerState.animateScrollToPage(page + 1)
                                            viewModel.setCurrentIndex(page + 1)
                                            viewModel.resetSubmissionState(story.id)
                                        } else if (!state.hasMore) {
                                            viewModel.resetState()
                                            onBack()
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 12.dp),
                            )
                        }
                    }
                }

                // 하단 네비게이션 바
                BottomNavBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(),
                            ),
                    currentTab = currentTab,
                    onTabSelected = { tab ->
                        currentTab = tab
                        when (tab) {
                            BottomTab.HomeScreen -> {
                                navController.navigate(Screen.Home.route)
                            }
                            BottomTab.SearchResultScreen -> {
                                navController.navigate(Screen.SearchResult.initialRoute())
                            }
                            BottomTab.AddTagScreen -> {
                                navController.navigate(Screen.AddTag.route)
                            }
                            BottomTab.StoryScreen -> {
                                // 이미 Story 화면
                            }
                        }
                    },
                )
            }
        }
        is StoryState.Error -> {
            // Show error screen
            Box(
                modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(onClick = { viewModel.loadStories(10) }) {
                        Text("Retry")
                    }
                }
            }
        }
        is StoryState.NetworkError -> {
            // Show network error screen
            Box(
                modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Network Error: ${state.message}", color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(onClick = { viewModel.loadStories(10) }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

// =============== 이게 핵심 블럭 ===============
// StoryPageFullBlock = 날짜/위치 + 이미지 + (공백) + TagSelectionCard
@Composable
private fun StoryPageFullBlock(
    story: StoryModel,
    showScrollHint: Boolean,
) {
    var isScrollHintVisible by remember { mutableStateOf(showScrollHint) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        isScrollHintVisible = false
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .matchParentSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 상단 정보 + 즐겨찾기
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = story.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = story.location,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                var isFavorite by remember { mutableStateOf(false) }
                IconButton(
                    onClick = { isFavorite = !isFavorite },
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 이미지 영역 - 고정 높이로 일관성 유지
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(480.dp) // 고정된 높이로 모든 스토리에서 일관된 크기 유지
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = story.images.firstOrNull(),
                    contentDescription = "story image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isScrollHintVisible) {
            ScrollHintOverlay(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .offset(y = 120.dp),
            )
        }
    }
}

// =============== Scroll Hint ===============

@Composable
internal fun ScrollHintOverlay(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "scrollbounce")
    val offsetY by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "scrollbounce",
    )

    // Capture color scheme values in composable scope
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer

    Box(
        modifier =
            modifier
                .offset(y = offsetY.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                ).background(
                    color = surfaceContainerColor.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                ).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = onSurfaceColor.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "Scroll for more moments",
                color = onSurfaceColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// =============== TagSelectionCard ===============

@Composable
internal fun TagSelectionCard(
    tags: List<String>,
    selectedTags: Set<String>,
    submissionState: SubmissionState,
    isViewed: Boolean,
    isEditMode: Boolean,
    onTagToggle: (String) -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Determine if in read-only mode (viewed but not editing)
    val isReadOnly = isViewed && !isEditMode

    // Trigger auto-advance when submission succeeds (both initial submission and edits)
    LaunchedEffect(submissionState) {
        if (submissionState is SubmissionState.Success) {
            onSuccess()
        }
    }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isReadOnly) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // Show different text based on mode
            Text(
                text =
                    if (isReadOnly) {
                        "이 추억에 붙인 태그"
                    } else if (isEditMode) {
                        "태그 수정하기"
                    } else {
                        "이 추억을 어떻게 기억하고 싶나요?"
                    },
                color =
                    if (isReadOnly) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = 12.dp),
            )

            FlowRow(
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                tags.forEach { tagText ->
                    val isAddChip =
                        (tagText == "+" || tagText == "＋" || tagText == "add")

                    if (isAddChip && !isReadOnly) {
                        AddTagChip(
                            onClick = {
                                // TODO: 사용자 새 태그 추가
                            },
                        )
                    } else if (!isAddChip) {
                        val isSelected = selectedTags.contains(tagText)

                        StoryTagChip(
                            text = tagText,
                            isSelected = isSelected,
                            onClick = {
                                if (!isReadOnly) {
                                    onTagToggle(tagText)
                                }
                            },
                            enabled = !isReadOnly,
                        )
                    }
                }
            }

            val hasSelection = selectedTags.isNotEmpty()
            // In edit mode, allow submitting even with no tags (to remove all tags)
            // In initial submission, require at least one tag
            val canSubmit = if (isEditMode) true else hasSelection

            // Show error message if submission failed
            if (submissionState is SubmissionState.Error) {
                Text(
                    text = submissionState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Show Edit button in read-only mode, Done button otherwise
            if (isReadOnly) {
                GradientPillButton(
                    text = "Edit",
                    enabled = true,
                    submissionState = SubmissionState.Idle,
                    onClick = onEdit,
                )
            } else {
                GradientPillButton(
                    text = if (isEditMode) "Done" else "Done",
                    enabled = canSubmit,
                    submissionState = submissionState,
                    onClick = {
                        when (submissionState) {
                            is SubmissionState.Error -> onRetry()
                            else -> onDone()
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun AddTagChip(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(50),
                ).clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
internal fun GradientPillButton(
    text: String,
    enabled: Boolean,
    submissionState: SubmissionState,
    onClick: () -> Unit,
) {
    val isLoading = submissionState is SubmissionState.Loading
    val isSuccess = submissionState is SubmissionState.Success
    val isError = submissionState is SubmissionState.Error

    val bgModifier =
        when {
            isSuccess ->
                Modifier.background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(24.dp),
                )
            isError ->
                Modifier.background(
                    MaterialTheme.colorScheme.errorContainer,
                    RoundedCornerShape(24.dp),
                )
            enabled || isLoading ->
                Modifier.background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(24.dp),
                )
            else ->
                Modifier.background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(24.dp),
                )
        }

    val isClickable = enabled && !isLoading && !isSuccess

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .then(bgModifier)
                .clickable(enabled = isClickable) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
            isSuccess -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            isError -> {
                Text(
                    text = "Retry",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            else -> {
                Text(
                    text = text,
                    color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
internal fun FlowRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->

        val placeables =
            measurables.map { measurable ->
                measurable.measure(constraints)
            }

        val rowSpacingPx = 8.dp.roundToPx()
        val colSpacingPx = 8.dp.roundToPx()

        val rows = mutableListOf<List<Placeable>>()
        val currentRow = mutableListOf<Placeable>()
        var currentRowWidth = 0
        var currentRowHeight = 0

        var totalHeight = 0
        val maxWidth = constraints.maxWidth

        placeables.forEach { p ->
            val w = p.width
            val h = p.height

            if (currentRowWidth > 0 && currentRowWidth + colSpacingPx + w > maxWidth) {
                rows.add(currentRow.toList())
                totalHeight += currentRowHeight + rowSpacingPx
                currentRow.clear()
                currentRowWidth = 0
                currentRowHeight = 0
            }

            currentRow.add(p)
            currentRowWidth =
                if (currentRowWidth == 0) w else currentRowWidth + colSpacingPx + w
            currentRowHeight = maxOf(currentRowHeight, h)
        }

        if (currentRow.isNotEmpty()) {
            rows.add(currentRow.toList())
            totalHeight += currentRowHeight
        }

        val layoutHeight =
            totalHeight
                .coerceAtLeast(constraints.minHeight)
                .coerceAtMost(constraints.maxHeight)

        layout(
            width = constraints.maxWidth,
            height = layoutHeight,
        ) {
            var yPos = 0
            rows.forEach { row ->
                var xPos = 0
                var rowHeightPx = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x = xPos, y = yPos)
                    xPos += placeable.width + colSpacingPx
                    rowHeightPx = maxOf(rowHeightPx, placeable.height)
                }
                yPos += rowHeightPx + rowSpacingPx
            }
        }
    }
}

// =============== Model & Preview ===============

// Preview disabled - requires ViewModel instantiation
// @Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
// @Composable
// private fun Preview_StoryTagSelectionScreen() {
//     // Preview would require ViewModel setup
// }

@Composable
private fun StoryPageFullBlockPreviewContent(
    @androidx.annotation.DrawableRes drawableResId: Int,
    date: String,
    location: String,
    suggestedTags: List<String>,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .matchParentSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            Text(
                text = date,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = location,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // 여기서는 Coil 말고 painterResource 써!
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
//                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                androidx.compose.foundation.Image(
                    painter =
                        androidx.compose.ui.res
                            .painterResource(id = drawableResId),
                    contentDescription = "preview image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TagSelectionCard(
                tags = suggestedTags,
                selectedTags = setOf("#카페", "#디저트"),
                submissionState = SubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    name = "Story Page Preview",
)
@Composable
private fun StoryPageFullBlockPreview() {
    MaterialTheme {
        StoryPageFullBlockPreviewContent(
            drawableResId = R.drawable.img1,
            date = "2024년 8월 1일",
            location = "서울 특별시",
            suggestedTags = listOf("#카페", "#친구와", "#디저트", "+"),
        )
    }
}
