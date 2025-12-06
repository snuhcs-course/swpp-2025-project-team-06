package com.example.momentag.ui.storytag

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.model.StoryModel
import com.example.momentag.view.StoryTagSelectionScreen
import com.example.momentag.viewmodel.StoryViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hilt 환경에서 동작
 * - Hilt가 ViewModel을 생성하게 둠 (hiltRule.inject())
 * - 생성된 ViewModel 인스턴스를 가져와 reflection으로 내부 MutableStateFlow 값을 설정
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class StoryScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var viewModel: StoryViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        viewModel = ViewModelProvider(composeRule.activity)[StoryViewModel::class.java]
    }

    /**
     * reflection으로 ViewModel 내부 private MutableStateFlow 필드 값을 바꿔서
     * UI에 데이터/상태를 주입
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(
        name: String,
        value: T,
    ) {
        try {
            val field = StoryViewModel::class.java.getDeclaredField(name)
            field.isAccessible = true
            val flow = field.get(viewModel) as MutableStateFlow<T>
            flow.value = value
        } catch (e: NoSuchFieldException) {
            // Test can continue if the field is not found
        }
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
        setFlow(
            "_storyState",
            StoryViewModel.StoryState.Success(
                stories = stories,
                currentIndex = 0,
                hasMore = false,
            ),
        )

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
    fun storyScreen_initialState_showsFirstStoryAndHintAndDoneDisabled() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 문자열 리소스 가져오기
        val scrollHint = composeRule.activity.getString(R.string.story_scroll_for_next)
        val done = composeRule.activity.getString(R.string.action_done)

        // 첫 번째 스토리 날짜
        composeRule.onNodeWithText("2025.01.01").assertIsDisplayed()
        // 첫 번째 태그
        composeRule.onNodeWithText("#Tag1A").assertIsDisplayed()
        // 스크롤 힌트
        composeRule.onNodeWithText(scrollHint).assertIsDisplayed()
        // Done 버튼 비활성화
        composeRule.onNodeWithText(done).assertIsNotEnabled()
    }

    // ---------- 2. 태그 선택 시 Done 활성화 ----------

    @Test
    fun storyScreen_tagClick_enablesDoneAndUpdatesSelectedTags() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 문자열 리소스 가져오기
        val done = composeRule.activity.getString(R.string.action_done)

        // 초기에는 Done 비활성화
        composeRule.onNodeWithText(done).assertIsNotEnabled()

        // 태그 선택
        composeRule.onNodeWithText("#Tag1A").performClick()

        // Done 버튼 활성화
        composeRule.onNodeWithText(done).assertIsEnabled()

        // ViewModel 의 선택 상태도 확인
        val selected = viewModel.getSelectedTags("story1")
        assertTrue(selected.contains("#Tag1A"))
    }

    @Test
    fun storyScreen_tagToggleTwice_disablesDoneAndClearsSelection() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 문자열 리소스 가져오기
        val done = composeRule.activity.getString(R.string.action_done)

        // 태그 선택
        composeRule.onNodeWithText("#Tag1A").performClick()
        composeRule.onNodeWithText(done).assertIsEnabled()
        assertTrue(viewModel.getSelectedTags("story1").contains("#Tag1A"))

        // 다시 클릭해서 해제
        composeRule.onNodeWithText("#Tag1A").performClick()

        // Done 이 다시 비활성화
        composeRule.onNodeWithText(done).assertIsNotEnabled()

        // ViewModel 선택 상태도 비어야 함
        assertTrue(viewModel.getSelectedTags("story1").isEmpty())
    }

    // ---------- 3. 스와이프 시 다음 스토리 + 힌트 숨김 + viewed 처리 ----------

    @Test
    fun storyScreen_swipeUp_showsNextStoryMarksPreviousViewedAndHidesHint() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 문자열 리소스 가져오기
        val scrollHint = composeRule.activity.getString(R.string.story_scroll_for_next)

        // 첫 페이지 확인
        composeRule.onNodeWithText("2025.01.01").assertIsDisplayed()
        // 처음에는 힌트가 존재
        composeRule.onNodeWithText(scrollHint).assertIsDisplayed()

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
            .onAllNodesWithText(scrollHint)
            .assertCountEquals(0)
    }

    // ---------- 4. 이미 본 스토리(read-only) 에서는 Edit 버튼만 보임 ----------

    @Test
    fun storyScreen_readOnlyMode_showsEditButtonAndHidesDone() {
        val stories = createSampleStories()

        // 문자열 리소스 가져오기
        val edit = composeRule.activity.getString(R.string.action_edit)
        val done = composeRule.activity.getString(R.string.action_done)

        // 먼저 viewedStories 에 story1 을 넣어 둔다
        viewModel.markStoryAsViewed("story1")

        setContentWithStories(stories)

        // 태그는 그대로 보인다
        composeRule.onNodeWithText("#Tag1A").assertIsDisplayed()

        // 읽기 전용이므로 하단 버튼 텍스트는 Edit 이어야 함
        composeRule.onNodeWithText(edit).assertIsDisplayed()

        // Done 이라는 텍스트는 더 이상 없어야 함
        composeRule
            .onAllNodesWithText(done)
            .assertCountEquals(0)

        // ViewModel 의 viewedStories 에도 story1 이 포함되어 있어야 함
        assertTrue(viewModel.viewedStories.value.contains("story1"))
        assertFalse(viewModel.viewedStories.value.contains("story2"))
    }

    // ---------- 5. 스토리 전환 후 태그 선택 상태 유지 ----------

    @Test
    fun storyScreen_switchStory_maintainsTagSelectionOnReturn() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 첫 번째 스토리에서 태그 선택
        composeRule.onNodeWithText("#Tag1A").performClick()
        assertTrue(viewModel.getSelectedTags("story1").contains("#Tag1A"))

        // Pager 노드 찾기 (스크롤 가능 노드)
        val pagerNode = composeRule.onNode(hasScrollAction())

        // 위로 스와이프해서 두 번째 스토리로 이동
        pagerNode.performTouchInput {
            swipeUp()
        }

        // 두 번째 스토리 날짜가 보여야 한다
        composeRule.onNodeWithText("2025.01.02").assertIsDisplayed()

        // 아래로 스와이프해서 첫 번째 스토리로 다시 돌아가기
        pagerNode.performTouchInput {
            swipeDown()
        }

        // 첫 번째 스토리 날짜가 다시 보여야 한다
        composeRule.onNodeWithText("2025.01.01").assertIsDisplayed()

        // ViewModel 에 저장된 선택 상태가 유지되어야 함
        assertTrue(viewModel.getSelectedTags("story1").contains("#Tag1A"))
    }

    // ---------- 6. Edit 버튼 클릭으로 편집 모드 전환 ----------

    @Test
    fun storyScreen_editMode_showsEditTagsAndDoneButton() {
        val stories = createSampleStories()

        // 문자열 리소스 가져오기
        val edit = composeRule.activity.getString(R.string.action_edit)
        val done = composeRule.activity.getString(R.string.action_done)
        val editTags = composeRule.activity.getString(R.string.story_edit_tags)

        // 먼저 viewedStories 에 story1 을 넣어 읽기 전용 모드로 만듦
        viewModel.markStoryAsViewed("story1")

        setContentWithStories(stories)

        // 초기에는 Edit 버튼이 보임
        composeRule.onNodeWithText(edit).assertIsDisplayed()

        // reflection 으로 편집 모드 상태를 직접 설정
        // (실제 API 호출 없이 편집 모드 진입 시뮬레이션)
        setFlow("_editModeStory", "story1")
        setFlow("_selectedTags", mapOf("story1" to emptySet<String>()))

        // 편집 모드로 전환되면 "Edit Tags" 텍스트가 나타남
        composeRule.onNodeWithText(editTags).assertIsDisplayed()

        // Done 버튼이 나타남
        composeRule.onNodeWithText(done).assertIsDisplayed()

        // Edit 버튼은 사라짐
        composeRule
            .onAllNodesWithText(edit)
            .assertCountEquals(0)

        // ViewModel 의 editModeStory 가 story1 이어야 함
        assertTrue(viewModel.editModeStory.value == "story1")
    }

    // ---------- 7. Done 버튼 클릭 시 제출 상태 변경 ----------

    @Test
    fun storyScreen_doneButtonClick_triggersSubmission() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 문자열 리소스 가져오기
        val done = composeRule.activity.getString(R.string.action_done)

        // 태그 선택
        composeRule.onNodeWithText("#Tag1A").performClick()

        // Done 버튼 클릭
        composeRule.onNodeWithText(done).performClick()

        // 제출 상태가 Loading 또는 Success/Error 로 변경되었는지 확인
        // (실제 API 호출은 모킹되지 않았으므로 상태만 확인)
        val submissionState = viewModel.storyTagSubmissionStates.value["story1"]
        assertTrue(
            submissionState is StoryViewModel.StoryTagSubmissionState.Loading ||
                submissionState is StoryViewModel.StoryTagSubmissionState.Success ||
                submissionState is StoryViewModel.StoryTagSubmissionState.Error,
        )
    }

    // ---------- 8. 커스텀 태그 추가 기능 ----------

    @Test
    fun storyScreen_customTagAdded_appearsInSelectedTags() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // "+" 버튼이 표시되는지 확인
        composeRule.onNodeWithText("+").assertIsDisplayed()

        // 커스텀 태그를 직접 ViewModel 을 통해 추가
        // (UI의 TextField 조작은 testTag가 없어서 어려우므로 ViewModel API 직접 호출)
        val customTag = "#CustomTag"
        viewModel.addCustomTagToStory("story1", customTag)

        // ViewModel 의 선택된 태그에 커스텀 태그가 추가되었는지 확인
        assertTrue(viewModel.getSelectedTags("story1").contains(customTag))

        // UI 재구성 대기
        composeRule.waitForIdle()

        // 추가된 커스텀 태그가 화면에 표시되는지 확인
        composeRule.onNodeWithText(customTag).assertIsDisplayed()
    }

    @Test
    fun storyScreen_multipleTagsSelected_allStoredInViewModel() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 문자열 리소스 가져오기
        val done = composeRule.activity.getString(R.string.action_done)

        // 초기에는 Done 비활성화
        composeRule.onNodeWithText(done).assertIsNotEnabled()

        // 첫 번째 태그 선택
        composeRule.onNodeWithText("#Tag1A").performClick()

        // 두 번째 태그 선택
        composeRule.onNodeWithText("#Tag1B").performClick()

        // Done 버튼 활성화
        composeRule.onNodeWithText(done).assertIsEnabled()

        // ViewModel 에 두 개의 태그가 모두 저장되었는지 확인
        val selectedTags = viewModel.getSelectedTags("story1")
        assertTrue(selectedTags.contains("#Tag1A"))
        assertTrue(selectedTags.contains("#Tag1B"))
        assertTrue(selectedTags.size == 2)
    }

    @Test
    fun storyScreen_customTagWithSuggestedTags_allSelected() {
        val stories = createSampleStories()
        setContentWithStories(stories)

        // 제안된 태그 선택
        composeRule.onNodeWithText("#Tag1A").performClick()

        // 커스텀 태그 추가
        val customTag = "#MyTrip"
        viewModel.addCustomTagToStory("story1", customTag)

        // UI 재구성 대기
        composeRule.waitForIdle()

        // 두 태그 모두 선택되었는지 확인
        val selectedTags = viewModel.getSelectedTags("story1")
        assertTrue(selectedTags.contains("#Tag1A"))
        assertTrue(selectedTags.contains(customTag))
        assertTrue(selectedTags.size == 2)

        // 두 태그 모두 화면에 표시되는지 확인
        composeRule.onNodeWithText("#Tag1A").assertIsDisplayed()
        composeRule.onNodeWithText(customTag).assertIsDisplayed()
    }
}
