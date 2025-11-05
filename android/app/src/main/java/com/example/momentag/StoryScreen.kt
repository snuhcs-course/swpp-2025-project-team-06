
@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.example.momentag.ui.storytag

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.momentag.ui.theme.Background
import com.example.momentag.ui.theme.Button
import com.example.momentag.ui.theme.Pink80
import com.example.momentag.ui.theme.Temp_word
import com.example.momentag.ui.theme.Word
import com.example.momentag.viewmodel.StoryViewModel
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
                modifier = modifier.fillMaxSize().background(Background),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("추억을 불러오는 중...", color = Word)
                }
            }
        }
        is StoryState.Success -> {
            val stories = state.stories
            val pagerState = rememberPagerState(pageCount = { stories.size })

            // Lazy-load tags for current page
            val currentPage = pagerState.currentPage
            LaunchedEffect(currentPage) {
                if (currentPage < stories.size) {
                    val currentStory = stories[currentPage]
                    viewModel.loadTagsForStory(currentStory.id, currentStory.photoId)
                }
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
                        .background(Background),
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
                VerticalPager(
                    state = pagerState,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) { page ->
                    val story = stories[page]
                    val isFirstStory = page == 0
                    val selectedForThisStory = selectedTags[story.id] ?: emptySet()

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
                                onTagToggle = { tag ->
                                    viewModel.toggleTag(story.id, tag)
                                },
                                onDone = {
                                    viewModel.submitTagsForStory(story.id)

                                    // Done 누르면 다음 스토리로 자동 스크롤
                                    coroutineScope.launch {
                                        if (page < stories.lastIndex) {
                                            pagerState.animateScrollToPage(page + 1)
                                            viewModel.setCurrentIndex(page + 1)
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
                modifier = modifier.fillMaxSize().background(Background),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error: ${state.message}", color = Word)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadStories(10) }) {
                        Text("Retry")
                    }
                }
            }
        }
        is StoryState.NetworkError -> {
            // Show network error screen
            Box(
                modifier = modifier.fillMaxSize().background(Background),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Network Error: ${state.message}", color = Word)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadStories(10) }) {
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
    Box(
        modifier = Modifier.fillMaxSize(),
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
                        fontSize = 12.sp,
                        color = Word,
                    )
                    Text(
                        text = story.location,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Word,
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
                            .background(Color.White.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Button else Color.Gray,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 이미지 영역 최대한 크게
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f) // 남는 공간을 모두 차지
                        .clip(RoundedCornerShape(8.dp))
                        .background(Temp_word),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = story.images.firstOrNull(),
                    contentDescription = "story image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit, // 비율 유지하며 프레임 최대한 채우기
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (showScrollHint) {
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

    Column(
        modifier = modifier.offset(y = offsetY.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.4f),
            modifier =
                Modifier
                    .size(24.dp)
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.5f), // 빛 색상
                            radius = this.size.maxDimension / 1.8f, // 번짐 크기
                            center = this.center,
                        )
                    },
        )
        Text(
            text = "스크롤하여 다음 추억",
            fontSize = 12.sp,
            color = Color.Black,
            style =
                TextStyle(
                    shadow =
                        androidx.compose.ui.graphics.Shadow(
                            color = Color.White, // 그림자(빛) 색상
                            offset = Offset(0f, 0f), // 중심에서 얼마나 이동시킬지
                            blurRadius = 12f, // 얼마나 번지게 할지
                        ),
                ),
        )
    }
}

// =============== TagSelectionCard ===============

@Composable
internal fun TagSelectionCard(
    tags: List<String>,
    selectedTags: Set<String>,
    onTagToggle: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = Pink80

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(20.dp),
                    clip = false,
                ),
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = Color.White,
            ),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            // TODO : 이 추억을 어떻게 기억하고 싶나요? 이거 남겨두는게 나을지,, 아니면 첫번재만 보여줄지...난 남겨두는 것도 괜찮다고 생각됨.
            Text(
                text = "이 추억을 어떻게 기억하고 싶나요?",
                fontSize = 14.sp,
                color = Color(0xFF444444),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            FlowRow(
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                tags.forEach { tagText ->
                    val isAddChip =
                        (tagText == "+" || tagText == "＋" || tagText == "add")

                    if (isAddChip) {
                        AddTagChip(
                            onClick = {
                                // TODO: 사용자 새 태그 추가
                            },
                        )
                    } else {
                        val isSelected = selectedTags.contains(tagText)

                        StoryTagChip(
                            text = tagText,
                            isSelected = isSelected,
                            onClick = { onTagToggle(tagText) },
                        )
                    }
                }
            }

            val hasSelection = selectedTags.isNotEmpty()

            GradientPillButton(
                text = "Done",
                enabled = hasSelection,
                onClick = onDone,
            )
        }
    }
}

@Composable
internal fun AddTagChip(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFF5F5F5))
                .border(
                    width = 1.dp,
                    color = Color(0xFFCCCCCC),
                    shape = RoundedCornerShape(50),
                ).clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+",
            fontSize = 14.sp,
            color = Color(0xFF555555),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun GradientPillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val gradientBrush =
        Brush.horizontalGradient(
            listOf(
                Color(0xFFFF8A80),
                Color(0xFFFF6F61),
            ),
        )

    val bgModifier =
        if (enabled) {
            Modifier.background(gradientBrush, RoundedCornerShape(24.dp))
        } else {
            Modifier.background(
                Color(0xFFFFCFCB),
                RoundedCornerShape(24.dp),
            )
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .then(bgModifier)
                .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
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
                .background(Color.White),
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
                fontSize = 12.sp,
                color = Color(0xFF8A8A8A),
            )
            Text(
                text = location,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF000000),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // 여기서는 Coil 말고 painterResource 써!
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
//                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEDEDED)),
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
                onTagToggle = {},
                onDone = {},
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
