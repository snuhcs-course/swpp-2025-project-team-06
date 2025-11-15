package com.example.momentag.ui.storytag

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.model.StoryModel
import com.example.momentag.model.StoryState
import com.example.momentag.network.ApiService
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.viewmodel.StoryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito

@RunWith(AndroidJUnit4::class)
class StoryScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var viewModel: StoryViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // ApiService 는 interface 라서 mock 가능 (final class 아님)
        val apiService = Mockito.mock(ApiService::class.java)

        // 실제 레포지토리들 사용 (final class mock 안 함)
        val recommendRepository = RecommendRepository(apiService)
        val remoteRepository = RemoteRepository(apiService)
        val localRepository = LocalRepository(context)
        val imageBrowserRepository = ImageBrowserRepository()

        viewModel =
            StoryViewModel(
                recommendRepository = recommendRepository,
                localRepository = localRepository,
                remoteRepository = remoteRepository,
                imageBrowserRepository = imageBrowserRepository,
            )
    }

    // ---------- Reflection helper (StoryState 주입용) ----------

    private fun setStoryStateSuccess(
        stories: List<StoryModel>,
        currentIndex: Int = 0,
        hasMore: Boolean = false,
    ) {
        val field = StoryViewModel::class.java.getDeclaredField("_storyState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(viewModel) as MutableStateFlow<StoryState>
        flow.value =
            StoryState.Success(
                stories = stories,
                currentIndex = currentIndex,
                hasMore = hasMore,
            )
    }

    private fun createSampleStories(): List<StoryModel> =
        listOf(
            StoryModel(
                id = "story1",
                photoId = "photo1",
                images = listOf(Uri.parse("content://test/1")),
                date = "2025.01.01",
                location = "Seoul",
                suggestedTags = listOf("#Tag1A", "#Tag1B"),
            ),
            StoryModel(
                id = "story2",
                photoId = "photo2",
                images = listOf(Uri.parse("content://test/2")),
                date = "2025.01.02",
                location = "Busan",
                suggestedTags = listOf("#Tag2A"),
            ),
        )

    private fun setContentWithStories(stories: List<StoryModel>) {
        // 처음부터 StoryState 를 Success 로 세팅해서
        // StoryScreen 의 LaunchedEffect 가 loadStories() 를 호출하지 않게 막는다.
        setStoryStateSuccess(stories = stories, hasMore = false)

        composeRule.setContent {
            val navController = rememberNavController()
            StoryTagSelectionScreen(
                viewModel = viewModel,
                onBack = {},
                navController = navController,
            )
        }
    }

    // ---------- 1. 초기 화면 상태 ----------

    @Test
    fun scenario_1_1_initialScreenShowsFirstStoryAndHintAndDoneDisabled() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 첫 번째 스토리 날짜
        composeRule.onNodeWithText("2025.01.01").assertIsDisplayed()
        // 첫 번째 태그
        composeRule.onNodeWithText("#Tag1A").assertIsDisplayed()
        // 스크롤 힌트
        composeRule.onNodeWithText("Scroll for next moments").assertIsDisplayed()
        // Done 버튼 비활성화
        composeRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    // ---------- 2. 태그 선택 시 Done 활성화 ----------

    @Test
    fun scenario_2_1_tagClickEnablesDoneAndUpdatesSelectedTags() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 초기에는 Done 비활성화
        composeRule.onNodeWithText("Done").assertIsNotEnabled()

        // 태그 선택
        composeRule.onNodeWithText("#Tag1A").performClick()

        // Done 버튼 활성화
        composeRule.onNodeWithText("Done").assertIsEnabled()

        // ViewModel 의 선택 상태도 확인
        val selected = viewModel.getSelectedTags("story1")
        assertTrue(selected.contains("#Tag1A"))
    }

    /**
     * 같은 태그를 다시 한 번 눌렀을 때
     * - 선택이 해제되고
     * - Done 버튼이 다시 비활성화되고
     * - ViewModel 의 selectedTags 도 비어 있는지 확인
     */
    @Test
    fun scenario_2_2_tagToggleTwiceDisablesDoneAndClearsSelection() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 태그 선택
        composeRule.onNodeWithText("#Tag1A").performClick()
        composeRule.onNodeWithText("Done").assertIsEnabled()
        assertTrue(viewModel.getSelectedTags("story1").contains("#Tag1A"))

        // 다시 클릭해서 해제
        composeRule.onNodeWithText("#Tag1A").performClick()

        // Done 이 다시 비활성화
        composeRule.onNodeWithText("Done").assertIsNotEnabled()

        // ViewModel 선택 상태도 비어야 함
        assertTrue(viewModel.getSelectedTags("story1").isEmpty())
    }

    // ---------- 3. 스와이프 시 다음 스토리 + 힌트 숨김 + viewed 처리 ----------

    @Test
    fun scenario_3_1_swipeUpShowsNextStoryMarksPreviousViewedAndHidesHint() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 첫 페이지 확인
        composeRule.onNodeWithText("2025.01.01").assertIsDisplayed()
        // 처음에는 힌트가 존재
        composeRule.onNodeWithText("Scroll for next moments").assertIsDisplayed()

        // Pager 노드 찾기 (스크롤 가능 노드)
        val pagerNode = composeRule.onNode(hasScrollAction())

        // 위로 스와이프해서 다음 페이지로 이동
        pagerNode.performTouchInput {
            swipeUp()
        }

        // 두 번째 스토리 날짜가 보여야 한다
        composeRule.onNodeWithText("2025.01.02").assertIsDisplayed()

        // 첫 번째 스토리는 viewed 에 들어간다
        assertTrue(viewModel.viewedStories.value.contains("story1"))

        // 스크롤 이후에는 힌트가 사라져야 한다
        composeRule
            .onAllNodesWithText("Scroll for next moments")
            .assertCountEquals(0)
    }

    // ---------- 4. 이미 본 스토리(read-only) 에서는 Edit 버튼만 보임 ----------

    /**
     * story1 을 이미 본 것으로 표시한 뒤 화면을 띄우면
     * - 태그 칩은 그대로 보이고
     * - 아래 버튼은 "Done" 이 아니라 "Edit" 이어야 한다
     * (TagSelectionCard 의 read-only 모드)
     */
    @Test
    fun scenario_4_1_readOnlyModeShowsEditButtonAndHidesDone() {
        val stories = createSampleStories()

        // 먼저 viewedStories 에 story1 을 넣어 둔다
        viewModel.markStoryAsViewed("story1")

        setContentWithStories(stories)

        // 태그는 그대로 보인다
        composeRule.onNodeWithText("#Tag1A").assertIsDisplayed()

        // 읽기 전용이므로 하단 버튼 텍스트는 Edit 이어야 함
        composeRule.onNodeWithText("Edit").assertIsDisplayed()

        // Done 이라는 텍스트는 더 이상 없어야 함
        composeRule
            .onAllNodesWithText("Done")
            .assertCountEquals(0)

        // ViewModel 의 viewedStories 에도 story1 이 포함되어 있어야 함
        assertTrue(viewModel.viewedStories.value.contains("story1"))
        assertFalse(viewModel.viewedStories.value.contains("story2"))
    }
}
