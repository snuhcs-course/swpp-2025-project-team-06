
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.momentag.ui.storytag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.model.StoryModel
import com.example.momentag.ui.components.BackTopBar
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CustomTagChip
import com.example.momentag.ui.components.ErrorOverlay
import com.example.momentag.ui.components.StoryTagChip
import com.example.momentag.ui.components.WarningBanner
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.viewmodel.StoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StoryTagSelectionScreen(
    viewModel: StoryViewModel,
    onBack: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    // 1. ViewModel에서 가져온 상태 (collectAsState)
    val storyState by viewModel.storyState.collectAsState()
    val selectedTags by viewModel.selectedTags.collectAsState()
    val submissionStates by viewModel.storyTagSubmissionStates.collectAsState()
    val viewedStories by viewModel.viewedStories.collectAsState()
    val editModeStory by viewModel.editModeStory.collectAsState()

    // 2. 로컬 상태 변수
    var currentTab by remember { mutableStateOf(BottomTab.StoryScreen) }

    // 3. rememberCoroutineScope
    val coroutineScope = rememberCoroutineScope()

    // 4. LaunchedEffect
    LaunchedEffect(Unit) {
        if (storyState is StoryViewModel.StoryState.Idle) {
            viewModel.loadStories(10)
        }
    }

    // 5. DisposableEffect
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPolling()
        }
    }

    // Use Scaffold for consistent top/bottom bar structure
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            BackTopBar(
                title = stringResource(R.string.story_title),
                onBackClick = {
                    viewModel.resetState()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        bottomBar = {
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
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                        BottomTab.MyTagsScreen -> {
                            navController.navigate(Screen.MyTags.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                        BottomTab.StoryScreen -> {
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        // Handle different states
        when (val state = storyState) {
            is StoryViewModel.StoryState.Idle -> {
                // Show nothing while idle
            }
            is StoryViewModel.StoryState.Loading -> {
                // Show loading screen
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.story_loading_memories), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            is StoryViewModel.StoryState.Success -> {
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
                            .fillMaxSize()
                            .padding(paddingValues),
                ) { page ->
                    val story = stories[page]
                    val isFirstStory = page == 0
                    val selectedForThisStory = selectedTags[story.id] ?: emptySet()
                    val storyTagSubmissionState = submissionStates[story.id] ?: StoryViewModel.StoryTagSubmissionState.Idle
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
                                    onImageClick = {
                                        // Create Photo object from story data
                                        val photo =
                                            com.example.momentag.model.Photo(
                                                photoId = story.photoId,
                                                contentUri = story.images.firstOrNull() ?: android.net.Uri.EMPTY,
                                                createdAt = "",
                                            )
                                        // Set browsing session
                                        viewModel.setStoryBrowsingSession(photo)
                                        // Navigate to image detail
                                        navController.navigate(
                                            Screen.Image.createRoute(
                                                uri = photo.contentUri,
                                                imageId = photo.photoId,
                                            ),
                                        )
                                    },
                                )
                            }

                            // 태그 선택 카드
                            TagSelectionCard(
                                tags = story.suggestedTags,
                                selectedTags = selectedForThisStory,
                                storyTagSubmissionState = storyTagSubmissionState,
                                isViewed = isViewed,
                                isEditMode = isEditMode,
                                onTagToggle = { tag ->
                                    viewModel.toggleTag(story.id, tag)
                                },
                                onAddCustomTag = { customTag ->
                                    viewModel.addCustomTagToStory(story.id, customTag)
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
            }
            is StoryViewModel.StoryState.Error -> {
                ErrorOverlay(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    title = stringResource(R.string.story_error_title),
                    errorMessage = state.message,
                    onRetry = { viewModel.loadStories(10) },
                    onDismiss = {
                        viewModel.resetState()
                        onBack()
                    },
                )
            }
            is StoryViewModel.StoryState.NetworkError -> {
                ErrorOverlay(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    title = stringResource(R.string.story_network_error_title),
                    errorMessage = state.message,
                    onRetry = { viewModel.loadStories(10) },
                    onDismiss = {
                        viewModel.resetState()
                        onBack()
                    },
                )
            }
        }
    }
}

@Composable
private fun StoryPageFullBlock(
    story: StoryModel,
    showScrollHint: Boolean,
    onImageClick: () -> Unit = {},
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
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = story.date,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(480.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onImageClick() },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = story.images.firstOrNull(),
                    contentDescription = stringResource(R.string.cd_story_image),
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
            StandardIcon.Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                sizeRole = IconSizeRole.DefaultAction,
                tintOverride = onSurfaceColor.copy(alpha = 0.7f),
                contentDescription = stringResource(R.string.cd_scroll_up),
            )
            Text(
                text = stringResource(R.string.story_scroll_for_next),
                color = onSurfaceColor.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
internal fun TagSelectionCard(
    tags: List<String>,
    selectedTags: Set<String>,
    storyTagSubmissionState: StoryViewModel.StoryTagSubmissionState,
    isViewed: Boolean,
    isEditMode: Boolean,
    onTagToggle: (String) -> Unit,
    onAddCustomTag: (String) -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Determine if in read-only mode (viewed but not editing)
    val isReadOnly = isViewed && !isEditMode

    // Trigger auto-advance when submission succeeds (both initial submission and edits)
    LaunchedEffect(storyTagSubmissionState) {
        if (storyTagSubmissionState is StoryViewModel.StoryTagSubmissionState.Success) {
            onSuccess()
        }
    }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        shape = RoundedCornerShape(12.dp),
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
                        stringResource(R.string.story_tags_for_memory)
                    } else if (isEditMode) {
                        stringResource(R.string.story_edit_tags)
                    } else {
                        stringResource(R.string.story_remember_this)
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

                // Always show StoryAddTagChip at the end when not in read-only mode
                if (!isReadOnly) {
                    CustomTagChip(
                        onTagAdded = { customTag ->
                            onAddCustomTag(customTag)
                        },
                    )
                }
            }

            val hasSelection = selectedTags.isNotEmpty()
            // In edit mode, allow submitting even with no tags (to remove all tags)
            // In initial submission, require at least one tag
            val canSubmit = if (isEditMode) true else hasSelection

            // Show error message if submission failed
            AnimatedVisibility(visible = storyTagSubmissionState is StoryViewModel.StoryTagSubmissionState.Error) {
                WarningBanner(
                    title = stringResource(R.string.story_failed_save_tag),
                    message =
                        (storyTagSubmissionState as? StoryViewModel.StoryTagSubmissionState.Error)?.message
                            ?: stringResource(R.string.error_message_unknown),
                    onActionClick = onRetry, // Retry button (GradientPillButton changes to Retry)
                    showActionButton = false, // Button is handled by GradientPillButton
                    showDismissButton = false,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Show Edit button in read-only mode, Done button otherwise
            if (isReadOnly) {
                GradientPillButton(
                    text = stringResource(R.string.action_edit),
                    enabled = true,
                    storyTagSubmissionState = StoryViewModel.StoryTagSubmissionState.Idle,
                    onClick = onEdit,
                )
            } else {
                GradientPillButton(
                    text = stringResource(R.string.action_done),
                    enabled = canSubmit,
                    storyTagSubmissionState = storyTagSubmissionState,
                    onClick = {
                        when (storyTagSubmissionState) {
                            is StoryViewModel.StoryTagSubmissionState.Error -> onRetry()
                            else -> onDone()
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun GradientPillButton(
    text: String,
    enabled: Boolean,
    storyTagSubmissionState: StoryViewModel.StoryTagSubmissionState,
    onClick: () -> Unit,
) {
    val isLoading = storyTagSubmissionState is StoryViewModel.StoryTagSubmissionState.Loading
    val isSuccess = storyTagSubmissionState is StoryViewModel.StoryTagSubmissionState.Success
    val isError = storyTagSubmissionState is StoryViewModel.StoryTagSubmissionState.Error

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
                StandardIcon.Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.cd_success),
                    sizeRole = IconSizeRole.DefaultAction,
                    intent = IconIntent.Inverse,
                )
            }
            isError -> {
                Text(
                    text = stringResource(R.string.story_retry),
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
                    contentDescription = stringResource(R.string.cd_preview_image),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TagSelectionCard(
                tags = suggestedTags,
                selectedTags = setOf("#카페", "#디저트"),
                storyTagSubmissionState = StoryViewModel.StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
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
            suggestedTags = listOf("#카페", "#친구와", "#디저트"),
        )
    }
}
