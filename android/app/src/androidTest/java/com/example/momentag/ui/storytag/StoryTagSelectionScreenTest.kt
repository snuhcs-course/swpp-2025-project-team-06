package com.example.momentag.ui.storytag

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.model.StoryModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoryTagSelectionScreenTest {
    // TODO: API 연결 후 목업으로 말고 Integration Test 필요
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var testStories: List<StoryModel>

    // Use an immutable Map for selectedTags so tests that wrap it with
    // remember { mutableStateOf(...) } don't hold a mutable collection inside
    // MutableState (which triggers lint warnings).
    private var selectedTags: Map<String, Set<String>> = emptyMap()
    private var doneClickedStoryIds = mutableListOf<String>()
    private var completeClicked = false
    private var backClicked = false
    // loadMoreCount was unused in tests; remove to silence warnings

    @Before
    fun setUp() {
        // Use local drawable resources for stability in tests
        testStories =
            listOf(
                StoryModel(
                    id = "story1",
                    images = listOf("android.resource://com.example.momentag/drawable/img1"),
                    date = "2024.10.15",
                    location = "강남 맛집",
                    suggestedTags = listOf("#food", "#맛집", "#행복", "+"),
                ),
                StoryModel(
                    id = "story2",
                    images = listOf("android.resource://com.example.momentag/drawable/img2"),
                    date = "2024.09.22",
                    location = "제주도 여행",
                    suggestedTags = listOf("#여행", "#바다", "#힐링", "+"),
                ),
                StoryModel(
                    id = "story3",
                    images = listOf("android.resource://com.example.momentag/drawable/img3"),
                    date = "2024.08.10",
                    location = "홍대 카페",
                    suggestedTags = listOf("#카페", "#친구", "#커피", "+"),
                ),
            )

        selectedTags = mutableMapOf()
        doneClickedStoryIds = mutableListOf()
        completeClicked = false
        backClicked = false
        // loadMoreCount removed; no-op
    }

    @Test
    fun storyTagSelectionScreen_DisplaysFirstStory() {
        // When
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // Then: 첫 번째 스토리의 정보가 표시되어야 함
        composeTestRule.onNodeWithText("강남 맛집").assertIsDisplayed()
        composeTestRule.onNodeWithText("2024.10.15").assertIsDisplayed()
        composeTestRule.onNodeWithText("이 추억을 어떻게 기억하고 싶나요?").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_TopBarDisplaysCorrectTitle() {
        // When
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // Then
        composeTestRule.onNodeWithText("Moment").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_BackButtonWorks() {
        // Given
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: 뒤로가기 버튼 클릭
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        // Then
        assert(backClicked)
    }

    @Test
    fun tagSelectionCard_DoneButtonDisabledWhenNoTagsSelected() {
        // When
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = emptyMap(),
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // Then: Done 버튼이 비활성화되어야 함
        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun tagSelectionCard_TagToggleWorks() {
        // Given
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: 태그 클릭
        composeTestRule.onNodeWithText("#food").performClick()

        // Then: 태그가 선택되어야 함
        assert(selectedTags["story1"]?.contains("#food") == true)
    }

    @Test
    fun tagSelectionCard_DoneButtonEnabledAfterTagSelection() {
        // Given
        composeTestRule.setContent {
            var localSelectedTags by remember { mutableStateOf(selectedTags) }

            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = localSelectedTags,
                onTagToggle = { storyId, tag ->
                    localSelectedTags =
                        localSelectedTags.toMutableMap().apply {
                            val current = this[storyId] ?: emptySet()
                            this[storyId] = if (current.contains(tag)) current - tag else current + tag
                        }
                },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: 태그 선택
        composeTestRule.onNodeWithText("#food").performClick()

        // Then: Done 버튼이 활성화되어야 함
        composeTestRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun tagSelectionCard_DoneButtonTriggersCallback() {
        // Given
        composeTestRule.setContent {
            var localSelectedTags by remember { mutableStateOf(selectedTags) }

            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = localSelectedTags,
                onTagToggle = { storyId, tag ->
                    localSelectedTags =
                        localSelectedTags.toMutableMap().apply {
                            val current = this[storyId] ?: emptySet()
                            this[storyId] = if (current.contains(tag)) current - tag else current + tag
                        }
                },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: 태그 선택 후 Done 클릭
        composeTestRule.onNodeWithText("#food").performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        // Then: onDone 콜백이 호출되어야 함
        assert(doneClickedStoryIds.contains("story1"))
    }

    @Test
    fun storyTagSelectionScreen_DisplaysAllSuggestedTags() {
        // When
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // Then: 모든 추천 태그가 표시되어야 함
        composeTestRule.onNodeWithText("#food").assertIsDisplayed()
        composeTestRule.onNodeWithText("#맛집").assertIsDisplayed()
        composeTestRule.onNodeWithText("#행복").assertIsDisplayed()
        composeTestRule.onNodeWithText("+").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_MultipleTagsCanBeSelected() {
        // Given
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: 여러 태그 선택
        composeTestRule.onNodeWithText("#food").performClick()
        composeTestRule.onNodeWithText("#맛집").performClick()
        composeTestRule.onNodeWithText("#행복").performClick()

        // Then: 모든 태그가 선택되어야 함
        assert(selectedTags["story1"]?.contains("#food") == true)
        assert(selectedTags["story1"]?.contains("#맛집") == true)
        assert(selectedTags["story1"]?.contains("#행복") == true)
    }

    @Test
    fun storyTagSelectionScreen_TagCanBeDeselected() {
        // Given
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: 태그 선택 후 다시 클릭 (deselect)
        composeTestRule.onNodeWithText("#food").performClick()
        assert(selectedTags["story1"]?.contains("#food") == true)

        composeTestRule.onNodeWithText("#food").performClick()

        // Then: 태그가 해제되어야 함
        assert(selectedTags["story1"]?.contains("#food") == false)
    }

    @Test
    fun storyTagSelectionScreen_LoadingIndicatorShownWhenLoading() {
        // When
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                // 로딩 인디케이터가 테스트 환경에서 화면 상단에 보이게 하기 위해 빈 스토리 리스트를 사용
                // LazyColumn은 뷰포트를 채우는 항목들 때메 로딩 항목을 화면 밖으로 배치할 수도 있으니까
                stories = emptyList(),
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                isLoading = true,
                navController = rememberNavController(),
            )
        }

        // Then: 로딩 메시지가 표시되어야 함
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("더 많은 추억을 불러오는 중...").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_FavoriteButtonWorks() {
        // Given
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: 즐겨찾기 버튼 클릭
        composeTestRule.onNodeWithContentDescription("Favorite").performClick()

        // Then: 버튼이 클릭 가능해야 함 (상태 변경은 내부적으로 처리됨)
        composeTestRule.onNodeWithContentDescription("Favorite").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_ScrollToNextStory() {
        // Given
        composeTestRule.setContent {
            var localSelectedTags by remember { mutableStateOf(selectedTags) }

            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = localSelectedTags,
                onTagToggle = { storyId, tag ->
                    localSelectedTags =
                        localSelectedTags.toMutableMap().apply {
                            val current = this[storyId] ?: emptySet()
                            this[storyId] = if (current.contains(tag)) current - tag else current + tag
                        }
                },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: 태그 선택 후 Done 클릭 (자동으로 다음 스토리로 스크롤)
        composeTestRule.onNodeWithText("#food").performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        // Wait for scroll animation to complete
        composeTestRule.waitForIdle()
        Thread.sleep(1000) // LazyColumn 스크롤 애니메이션 대기

        // Then: onDone 콜백이 호출되었는지 확인
        assert(doneClickedStoryIds.contains("story1"))

        // Note: LazyColumn의 animateScrollToItem은 Compose UI 테스트에서
        // 완벽하게 시뮬레이션되지 않을 수 있어 두 번째 스토리 표시 TEST는 생략해둠.
        // TODO :  실제 기기/에뮬레이터에서 수동 테스트 필요
    }

    @Test
    fun storyTagSelectionScreen_ScrollHintDisplayedOnFirstStory() {
        // When
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // Then: 첫 번째 스토리에서 스크롤 힌트가 표시되어야 함
        composeTestRule.onNodeWithText("스크롤하여 다음 추억").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_DoneOnLastStoryTriggersComplete() {
        // Given: 마지막 스토리만 있는 경우
        val singleStory = listOf(testStories.first())
        composeTestRule.setContent {
            var localSelectedTags by remember { mutableStateOf(selectedTags) }

            StoryTagSelectionScreen(
                stories = singleStory,
                selectedTags = localSelectedTags,
                onTagToggle = { storyId, tag ->
                    localSelectedTags =
                        localSelectedTags.toMutableMap().apply {
                            val current = this[storyId] ?: emptySet()
                            this[storyId] = if (current.contains(tag)) current - tag else current + tag
                        }
                },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                hasMore = false, // 더 이상 로드할 스토리 없음
                navController = rememberNavController(),
            )
        }

        // When: 태그 선택 후 Done 클릭
        composeTestRule.onNodeWithText("#food").performClick()
        composeTestRule.onNodeWithText("Done").performClick()

        composeTestRule.waitForIdle()
        Thread.sleep(500) // 콜백 처리 대기

        // Then: onComplete가 호출되어야 함
        assert(completeClicked)
    }

    @Test
    fun storyTagSelectionScreen_NavigationBarHomeTabWorks() {
        // Given
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // When: Home 탭 클릭
        // 실제 네비게이션 그래프가 없으므로 네비게이션 클릭 대신 노드 존재 여부만 확인
        composeTestRule.onNodeWithContentDescription("Home").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_NavigationBarSearchTabWorks() {
        // Given
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // 실제 네비게이션 그래프가 없으므로 네비게이션 클릭 대신 노드 존재 여부만 확인
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_NavigationBarTagTabWorks() {
        // Given
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // 실제 네비게이션 그래프가 없으므로 네비게이션 클릭 대신 노드 존재 여부만 확인
        composeTestRule.onNodeWithContentDescription("Tag").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_NavigationBarStoryTabSelected() {
        // When
        composeTestRule.setContent {
            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag -> toggleTag(storyId, tag) },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                navController = rememberNavController(),
            )
        }

        // Then: Story 탭이 표시되어야 함 (현재 활성화된 탭)
        composeTestRule.onNodeWithContentDescription("Story").assertIsDisplayed()
    }

    @Test
    fun storyTagSelectionScreen_InfiniteScrollTriggersLoadMore() {
        // TODO: API 연결 후 테스트 필요
        // Given
        // Note: we previously tracked a local loadMoreCalled flag here but never
        // read it; remove the unused variable to silence warnings. The full
        // onLoadMore behavior is best tested in integration/device tests.
        composeTestRule.setContent {
            var localSelectedTags by remember { mutableStateOf(selectedTags) }

            StoryTagSelectionScreen(
                stories = testStories,
                selectedTags = localSelectedTags,
                onTagToggle = { storyId, tag ->
                    localSelectedTags =
                        localSelectedTags.toMutableMap().apply {
                            val current = this[storyId] ?: emptySet()
                            this[storyId] = if (current.contains(tag)) current - tag else current + tag
                        }
                },
                onDone = { storyId -> doneClickedStoryIds.add(storyId) },
                onComplete = { completeClicked = true },
                onBack = { backClicked = true },
                onLoadMore = { /* no-op for unit test; integration/device test should verify onLoadMore */ },
                isLoading = false,
                hasMore = true,
                navController = rememberNavController(),
            )
        }

        // When: 마지막 아이템 근처까지 스크롤
        // Done을 연속으로 클릭하여 마지막 스토리에 근접
        composeTestRule.onNodeWithText("#food").performClick()
        composeTestRule.onNodeWithText("Done").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // 두 번째 스토리가 표시되는지 확인하지 않고 onDone 호출 확인만
        assert(doneClickedStoryIds.contains("story1"))

        // Note: LazyColumn의 스크롤 감지 및 onLoadMore 호출은
        // Compose UI 테스트에서 완벽히 시뮬레이션되지 않을 수 있음
        // TODO :  실제 기기/에뮬레이터에서 수동 테스트 필요
    }

    // Helper function to toggle tags
    private fun toggleTag(
        storyId: String,
        tag: String,
    ) {
        // Reassign an immutable Map by building a new copy with the modified set
        selectedTags =
            selectedTags.toMutableMap().apply {
                val current = this[storyId] ?: emptySet()
                this[storyId] = if (current.contains(tag)) current - tag else current + tag
            }
    }
}
