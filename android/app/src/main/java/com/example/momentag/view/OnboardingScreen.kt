package com.example.momentag.view

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.momentag.R
import com.example.momentag.Screen
import com.example.momentag.ui.components.BottomNavBar
import com.example.momentag.ui.components.BottomTab
import com.example.momentag.ui.components.CommonTopBar
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
import com.example.momentag.ui.theme.rememberAppBackgroundBrush
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavHostController,
    onComplete: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 7 })
    val backgroundBrush = rememberAppBackgroundBrush()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundBrush),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> OnboardingPage0Upload()
                1 -> OnboardingPage1TagAlbums()
                2 -> OnboardingPage2SearchWithTags()
                3 -> OnboardingPage3SearchResults()
                4 -> OnboardingPage4SearchResultsSelection()
                5 -> OnboardingPage5Moment()
                6 -> OnboardingPage6MomentScroll()
            }
        }

        // Page indicators (above bottom bar + system navigation bar)
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp + navigationBarPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(7) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                },
                            ),
                )
            }
        }


        // Start button (below indicators)
        if (pagerState.currentPage == 6) {
            Button(
                onClick = {
                    onComplete()
                    navController.navigate(Screen.Login.createRoute(false)) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = Dimen.ScreenHorizontalPadding)
                        .padding(bottom = 20.dp + navigationBarPadding)
                        .height(56.dp),
                shape = RoundedCornerShape(Dimen.ButtonCornerRadius),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_start),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ============================================
// Page 0: HomeScreen - Upload Photos
// ============================================
@Composable
private fun OnboardingPage0Upload() {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            Box {
                // Faded topbar with title
                Box(modifier = Modifier.alpha(0.3f)) {
                    CommonTopBar(
                        title = stringResource(R.string.app_name),
                        showLogout = false,
                        onLogoutClick = {},
                    )
                }
                // Highlighted upload button overlaid at exact actions position
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .padding(end = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    IconButton(onClick = { }) {
                        StandardIcon.Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            sizeRole = IconSizeRole.DefaultAction,
                        )
                    }
                }
            }
        },
        bottomBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                BottomNavBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(),
                            ),
                    currentTab = BottomTab.HomeScreen,
                    onTabSelected = {},
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rememberAppBackgroundBrush())
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimen.ScreenHorizontalPadding),
            ) {
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                // Search Bar (faded)
                Box(modifier = Modifier.alpha(0.3f)) {
                    MockChipSearchBar(
                        listState = listState,
                        placeholder = stringResource(R.string.search_placeholder_with_tag),
                    )
                }

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                // Tag Albums Grid (faded)
                Box(modifier = Modifier.alpha(0.3f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(9) { index ->
                            MockTagAlbumItem(
                                tagName =
                                    listOf(
                                        "Travel",
                                        "Family",
                                        "Friends",
                                        "Food",
                                        "Nature",
                                        "Daily",
                                        "Work",
                                        "Pets",
                                        "Hobby",
                                    )[index],
                            )
                        }
                    }
                }
            }

            OnboardingExplanation(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp),
                title = stringResource(R.string.onboarding_page0_title),
                description = stringResource(R.string.onboarding_page0_description),
            )
        }
    }
}

// ============================================
// Page 1: HomeScreen - Tag Albums
// ============================================
@Composable
private fun OnboardingPage1TagAlbums() {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                CommonTopBar(
                    title = stringResource(R.string.app_name),
                    showLogout = false,
                    onLogoutClick = {},
                    actions = {
                        // Upload button (faded)
                        IconButton(onClick = { }) {
                            StandardIcon.Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                sizeRole = IconSizeRole.DefaultAction,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                BottomNavBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(),
                            ),
                    currentTab = BottomTab.HomeScreen,
                    onTabSelected = {},
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rememberAppBackgroundBrush())
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimen.ScreenHorizontalPadding),
            ) {
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                // Search Bar (faded)
                Box(modifier = Modifier.alpha(0.3f)) {
                    MockChipSearchBar(
                        listState = listState,
                        placeholder = stringResource(R.string.search_placeholder_with_tag),
                    )
                }

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                // Tag Albums Grid (highlighted)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(9) { index ->
                        MockTagAlbumItem(
                            tagName =
                                listOf(
                                    "Travel",
                                    "Family",
                                    "Friends",
                                    "Food",
                                    "Nature",
                                    "Daily",
                                    "Work",
                                    "Pets",
                                    "Hobby",
                                )[index],
                        )
                    }
                }
            }

            // Explanation (bottom fixed position)
            OnboardingExplanation(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp),
                title = stringResource(R.string.onboarding_page1_title),
                description = stringResource(R.string.onboarding_page1_description),
            )
        }
    }
}

// ============================================
// Page 2: HomeScreen - Search with Tags
// ============================================
@Composable
private fun OnboardingPage2SearchWithTags() {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                CommonTopBar(
                    title = stringResource(R.string.app_name),
                    showLogout = false,
                    onLogoutClick = {},
                    actions = {
                        // Upload button (faded)
                        IconButton(onClick = { }) {
                            StandardIcon.Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                sizeRole = IconSizeRole.DefaultAction,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                BottomNavBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(),
                            ),
                    currentTab = BottomTab.HomeScreen,
                    onTabSelected = {},
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rememberAppBackgroundBrush())
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimen.ScreenHorizontalPadding),
            ) {
                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                // Search Bar with Chip (highlighted) - primary color
                MockChipSearchBarWithChip(listState = listState)

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))

                // Tag suggestions (highlighted)
                MockTagSuggestions()

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                // Tag Albums Grid (faded)
                Box(modifier = Modifier.alpha(0.3f).weight(1f)) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    ) {
                        items(9) { index ->
                            MockTagAlbumItem(
                                tagName =
                                    listOf(
                                        "Travel",
                                        "Family",
                                        "Friends",
                                        "Food",
                                        "Nature",
                                        "Daily",
                                        "Work",
                                        "Pets",
                                        "Hobby",
                                    )[index],
                            )
                        }
                    }
                }
            }

            OnboardingExplanation(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp),
                title = stringResource(R.string.onboarding_page2_title),
                description = stringResource(R.string.onboarding_page2_description),
            )
        }
    }
}

// ============================================
// Page 3: SearchResultScreen
// ============================================
@Composable
private fun OnboardingPage3SearchResults() {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                CommonTopBar(
                    title = stringResource(R.string.search_result_title),
                    showBackButton = true,
                    onBackClick = {},
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                BottomNavBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(),
                            ),
                    currentTab = BottomTab.HomeScreen,
                    onTabSelected = {},
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rememberAppBackgroundBrush())
                    .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Search bar (faded)
                Box(modifier = Modifier.alpha(0.3f).padding(horizontal = Dimen.ScreenHorizontalPadding)) {
                    Column {
                        Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                        MockChipSearchBarWithChip(listState = listState)
                        Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                    }
                }

                // Photo Grid (highlighted)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(Dimen.ScreenHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(12) {
                        MockPhotoItem()
                    }
                }
            }

            OnboardingExplanation(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp),
                title = stringResource(R.string.onboarding_page3_title),
                description = stringResource(R.string.onboarding_page3_description),
            )
        }
    }
}

// ============================================
// Page 4: SearchResultScreen - Photo Selection
// ============================================
@Composable
private fun OnboardingPage4SearchResultsSelection() {
    val isSelected = true
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                CommonTopBar(
                    title = stringResource(R.string.search_result_title),
                    showBackButton = true,
                    onBackClick = {},
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                BottomNavBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(),
                            ),
                    currentTab = BottomTab.HomeScreen,
                    onTabSelected = {},
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rememberAppBackgroundBrush())
                    .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Search bar (faded)
                Box(modifier = Modifier.alpha(0.3f).padding(horizontal = Dimen.ScreenHorizontalPadding)) {
                    Column {
                        Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                        MockChipSearchBarWithChip(listState = listState)
                        Spacer(modifier = Modifier.height(Dimen.ItemSpacingSmall))
                    }
                }

                // Photo Grid with selection (highlighted)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(Dimen.ScreenHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(12) { index ->
                        MockPhotoItemWithSelection(isSelected = isSelected && index < 3)
                    }
                }
            }

            // Add Tag Button
            Button(
                onClick = { },
                shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = Dimen.ItemSpacingLarge, end = Dimen.ScreenHorizontalPadding),
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    sizeRole = IconSizeRole.Navigation,
                    intent = IconIntent.Inverse,
                )
                Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))
                Text(
                    text = stringResource(R.string.onboarding_add_tag_button),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            OnboardingExplanation(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp),
                title = stringResource(R.string.onboarding_page4_title),
                description = stringResource(R.string.onboarding_page4_description),
            )
        }
    }
}

// ============================================
// Page 5: Moment - Tag Addition
// ============================================
@Composable
private fun OnboardingPage5Moment() {
    Scaffold(
        topBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                CommonTopBar(
                    title = stringResource(R.string.story_title),
                    showBackButton = true,
                    onBackClick = {},
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                BottomNavBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(),
                            ),
                    currentTab = BottomTab.StoryScreen,
                    onTabSelected = {},
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rememberAppBackgroundBrush())
                    .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Story content area (takes remaining space)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = Dimen.ScreenHorizontalPadding),
                    ) {
                        Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                        // Date (faded)
                        Box(modifier = Modifier.alpha(0.3f)) {
                            Text(
                                text = "2023.11.13",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                        // Image (faded)
                        Box(
                            modifier =
                                Modifier
                                    .alpha(0.3f)
                                    .fillMaxWidth()
                                    .height(Dimen.StoryImageHeight)
                                    .clip(RoundedCornerShape(Dimen.ComponentCornerRadius))
                                    .background(
                                        Brush.linearGradient(
                                            colors =
                                                listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.tertiaryContainer,
                                                ),
                                        ),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp),
                            )
                        }
                    }
                }

                // Tag card (highlighted, fixed at bottom)
                MockStoryTagCard(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Dimen.ScreenHorizontalPadding),
                )

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))
            }

            OnboardingExplanation(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp),
                title = stringResource(R.string.onboarding_page5_title),
                description = stringResource(R.string.onboarding_page5_description),
            )
        }
    }
}

// ============================================
// Page 6: Moment - Vertical Scroll
// ============================================
@Composable
private fun OnboardingPage6MomentScroll() {
    Scaffold(
        topBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                CommonTopBar(
                    title = stringResource(R.string.story_title),
                    showBackButton = true,
                    onBackClick = {},
                )
            }
        },
        bottomBar = {
            Box(modifier = Modifier.alpha(0.3f)) {
                BottomNavBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom)
                                    .asPaddingValues(),
                            ),
                    currentTab = BottomTab.StoryScreen,
                    onTabSelected = {},
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(rememberAppBackgroundBrush())
                    .padding(paddingValues),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Story content area (takes remaining space)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    // One single story (faded)
                    Box(modifier = Modifier.alpha(0.3f)) {
                        Column(
                            modifier = Modifier.padding(horizontal = Dimen.ScreenHorizontalPadding),
                        ) {
                            Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                            Text(
                                text = "2023.11.13",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(Dimen.StoryImageHeight)
                                        .clip(RoundedCornerShape(Dimen.ComponentCornerRadius))
                                        .background(
                                            Brush.linearGradient(
                                                colors =
                                                    listOf(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        MaterialTheme.colorScheme.tertiaryContainer,
                                                    ),
                                            ),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                        }
                    }

                    // Scroll hint (highlighted, center)
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = stringResource(R.string.onboarding_swipe_vertical),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Tag card (faded, fixed at bottom)
                Box(modifier = Modifier.alpha(0.3f)) {
                    MockStoryTagCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Dimen.ScreenHorizontalPadding),
                    )
                }

                Spacer(modifier = Modifier.height(Dimen.ItemSpacingMedium))
            }

            OnboardingExplanation(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 170.dp),
                title = stringResource(R.string.onboarding_page6_title),
                description = stringResource(R.string.onboarding_page6_description),
            )
        }
    }
}

// ============================================
// Mock Components
// ============================================

@Composable
private fun MockAlbumItem() {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.height(Dimen.GridItemSpacing))

        Text(
            text = "Album",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )

        Text(
            text = "12 photos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun MockChipSearchBar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    placeholder: String = "",
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimen.SearchBarMinHeight),
        shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimen.ScreenHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MockChipSearchBarWithChip(listState: androidx.compose.foundation.lazy.LazyListState) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(Dimen.SearchBarMinHeight),
        shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Dimen.ScreenHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))

            // Tag chip (CircleShape like SearchChipView)
            Row(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clip(CircleShape)
                        .padding(
                            horizontal = Dimen.ButtonPaddingVertical,
                            vertical = Dimen.ButtonPaddingSmallVertical,
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
            ) {
                Text(
                    text = "#Travel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Spacer(modifier = Modifier.width(Dimen.SearchSideEmptyPadding))

            Text(
                text = " ocean",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MockTagSuggestions() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimen.ItemSpacingSmall),
    ) {
        listOf("Family", "Friends", "Food").forEach { tagName ->
            Row(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .clip(CircleShape)
                        .padding(
                            horizontal = Dimen.ButtonPaddingVertical,
                            vertical = Dimen.ButtonPaddingSmallVertical,
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
            ) {
                Text(
                    text = "#$tagName",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun MockTagAlbumItem(tagName: String) {
    Box {
        Box(
            modifier =
                Modifier
                    .padding(top = Dimen.ItemSpacingMedium)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Dimen.TagCornerRadius))
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                        ),
                    )
                    .align(Alignment.BottomCenter),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
        }

        Text(
            text = tagName,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = Dimen.ItemSpacingSmall)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(Dimen.ButtonCornerRadius),
                    ).padding(horizontal = Dimen.ItemSpacingSmall, vertical = Dimen.GridItemSpacing),
        )
    }
}

@Composable
private fun MockPhotoItem() {
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                            ),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Photo,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun MockPhotoItemWithSelection(isSelected: Boolean) {
    Box(
        modifier = Modifier.aspectRatio(1f),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp),
            )
        }

        // Dimmed overlay for selected items
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(Dimen.ImageCornerRadius))
                        .background(Color.Black.copy(alpha = 0.3f)),
            )
        }

        // Checkbox
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimen.GridItemSpacing)
                    .size(Dimen.IconButtonSizeSmall)
                    .clip(RoundedCornerShape(Dimen.ComponentCornerRadius))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Check,
                    sizeRole = IconSizeRole.InlineAction,
                    intent = IconIntent.OnPrimaryContainer,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun MockStoryTagCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Dimen.ComponentCornerRadius),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(Dimen.ComponentPadding),
        ) {
            Text(
                text = stringResource(R.string.story_remember_this),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(bottom = Dimen.ItemSpacingMedium),
            )

            Row(
                modifier = Modifier.padding(bottom = Dimen.ItemSpacingLarge),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MockStoryTagChip("Travel")
                MockStoryTagChip("Ocean")

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Done button
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(Dimen.ButtonHeightMedium)
                        .clip(RoundedCornerShape(Dimen.SearchBarCornerRadius))
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(Dimen.SearchBarCornerRadius),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_done),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun MockStoryTagChip(tagName: String) {
    val isSelected = tagName == "Ocean"
    val backgroundColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    val textColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        }

    Box(contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(Dimen.TagCornerRadius),
            color = backgroundColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "#$tagName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = Dimen.TagItemSpacer, y = -Dimen.TagItemSpacer)
                        .size(Dimen.StoryTagChipBadgeSize)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center,
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    sizeRole = IconSizeRole.ChipAction,
                    tintOverride = Color.White,
                )
            }
        }
    }
}

@Composable
private fun OnboardingExplanation(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth(0.9f)
                .shadow(8.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
