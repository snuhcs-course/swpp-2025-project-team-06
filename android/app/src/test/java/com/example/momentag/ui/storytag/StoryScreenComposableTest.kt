package com.example.momentag.ui.storytag

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.momentag.model.StoryTagSubmissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * StoryScreen Composable 함수들에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 *
 * 주의: StoryTagSelectionScreen은 ViewModel에 의존하므로 독립적인 Composable 함수들만 테스트
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StoryScreenComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region ScrollHintOverlay Tests
    @Test
    fun scrollHintOverlay_displaysScrollIcon() {
        // Given
        composeTestRule.setContent {
            ScrollHintOverlay()
        }

        // Then - 아이콘은 contentDescription이 null이므로 텍스트로 확인
        composeTestRule.onNodeWithText("스크롤하여 다음 추억").assertIsDisplayed()
    }

    @Test
    fun scrollHintOverlay_displaysHintText() {
        // Given
        composeTestRule.setContent {
            ScrollHintOverlay()
        }

        // Then
        composeTestRule.onNodeWithText("스크롤하여 다음 추억").assertIsDisplayed()
    }

    @Test
    fun scrollHintOverlay_textIsKorean() {
        // Given
        composeTestRule.setContent {
            ScrollHintOverlay()
        }

        // Then
        val expectedText = "스크롤하여 다음 추억"
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }
    // endregion

    // region TagSelectionCard Tests - Basic Display
    @Test
    fun tagSelectionCard_displaysTitle() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페", "#친구"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("이 추억을 어떻게 기억하고 싶나요?").assertIsDisplayed()
    }

    @Test
    fun tagSelectionCard_displaysSingleTag() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("#카페").assertIsDisplayed()
    }

    @Test
    fun tagSelectionCard_displaysMultipleTags() {
        // Given
        val tags = listOf("#카페", "#친구", "#디저트")
        composeTestRule.setContent {
            TagSelectionCard(
                tags = tags,
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        tags.forEach { tag ->
            composeTestRule.onNodeWithText(tag).assertIsDisplayed()
        }
    }

    @Test
    fun tagSelectionCard_displaysDoneButton() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }
    // endregion

    // region TagSelectionCard Tests - Tag Selection
    @Test
    fun tagSelectionCard_tagClickTriggersToggle() {
        // Given
        var toggledTag = ""
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = { tag -> toggledTag = tag },
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // When
        composeTestRule.onNodeWithText("#카페").performClick()

        // Then
        assertEquals("#카페", toggledTag)
    }

    @Test
    fun tagSelectionCard_multipleTagClicks() {
        // Given
        val clickedTags = mutableListOf<String>()
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페", "#친구"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = { tag -> clickedTags.add(tag) },
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // When
        composeTestRule.onNodeWithText("#카페").performClick()
        composeTestRule.onNodeWithText("#친구").performClick()

        // Then
        assertEquals(2, clickedTags.size)
        assertTrue(clickedTags.contains("#카페"))
        assertTrue(clickedTags.contains("#친구"))
    }

    @Test
    fun tagSelectionCard_selectedTagsDisplayed() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페", "#친구"),
                selectedTags = setOf("#카페"),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then - 선택된 태그도 표시됨
        composeTestRule.onNodeWithText("#카페").assertIsDisplayed()
    }
    // endregion

    // region TagSelectionCard Tests - Done Button
    @Test
    fun tagSelectionCard_doneButtonDisabledWhenNoSelection() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun tagSelectionCard_doneButtonEnabledWhenHasSelection() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = setOf("#카페"),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun tagSelectionCard_doneButtonClickTriggersCallback() {
        // Given
        var doneClicked = false
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = setOf("#카페"),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = { doneClicked = true },
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // When
        composeTestRule.onNodeWithText("Done").performClick()

        // Then
        assertTrue(doneClicked)
    }

    @Test
    fun tagSelectionCard_doneButtonNotClickableWhenDisabled() {
        // Given
        var doneClicked = false
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = { doneClicked = true },
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // When - 비활성화 상태에서 클릭 시도
        composeTestRule.onNodeWithText("Done").performClick()

        // Then - 콜백이 호출되지 않음
        assertEquals(false, doneClicked)
    }
    // endregion

    // region TagSelectionCard Tests - Add Tag Chip
    @Test
    fun tagSelectionCard_displaysAddTagChip() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페", "+"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("+").assertIsDisplayed()
    }

    @Test
    fun tagSelectionCard_addTagChipWithPlusSign() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("＋"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("+").assertIsDisplayed()
    }

    @Test
    fun tagSelectionCard_addTagChipWithAddText() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("add"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("+").assertIsDisplayed()
    }
    // endregion

    // region GradientPillButton Tests - Enabled State
    @Test
    fun gradientPillButton_displaysText() {
        // Given
        composeTestRule.setContent {
            GradientPillButton(
                text = "Done",
                enabled = true,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun gradientPillButton_customText() {
        // Given
        val buttonText = "완료"
        composeTestRule.setContent {
            GradientPillButton(
                text = buttonText,
                enabled = true,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(buttonText).assertIsDisplayed()
    }

    @Test
    fun gradientPillButton_enabledClickTriggersCallback() {
        // Given
        var clicked = false
        composeTestRule.setContent {
            GradientPillButton(
                text = "Done",
                enabled = true,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = { clicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("Done").performClick()

        // Then
        assertTrue(clicked)
    }

    @Test
    fun gradientPillButton_enabledMultipleClicks() {
        // Given
        var clickCount = 0
        composeTestRule.setContent {
            GradientPillButton(
                text = "Done",
                enabled = true,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = { clickCount++ },
            )
        }

        // When
        repeat(3) {
            composeTestRule.onNodeWithText("Done").performClick()
        }

        // Then
        assertEquals(3, clickCount)
    }
    // endregion

    // region GradientPillButton Tests - Disabled State
    @Test
    fun gradientPillButton_disabledDisplaysText() {
        // Given
        composeTestRule.setContent {
            GradientPillButton(
                text = "Done",
                enabled = false,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun gradientPillButton_disabledDoesNotTriggerCallback() {
        // Given
        var clicked = false
        composeTestRule.setContent {
            GradientPillButton(
                text = "Done",
                enabled = false,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = { clicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("Done").performClick()

        // Then
        assertEquals(false, clicked)
    }

    @Test
    fun gradientPillButton_disabledIgnoresMultipleClicks() {
        // Given
        var clickCount = 0
        composeTestRule.setContent {
            GradientPillButton(
                text = "Done",
                enabled = false,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = { clickCount++ },
            )
        }

        // When
        repeat(3) {
            composeTestRule.onNodeWithText("Done").performClick()
        }

        // Then
        assertEquals(0, clickCount)
    }
    // endregion

    // region GradientPillButton Tests - State Transitions
    @Test
    fun gradientPillButton_disabledStateDoesNotTrigger() {
        // Given - 비활성화 상태
        var clicked = false
        composeTestRule.setContent {
            GradientPillButton(
                text = "Done",
                enabled = false,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = { clicked = true },
            )
        }

        // When - 클릭 시도
        composeTestRule.onNodeWithText("Done").performClick()

        // Then
        assertEquals(false, clicked)
    }

    @Test
    fun gradientPillButton_enabledStateTriggers() {
        // Given - 활성화 상태
        var clicked = false
        composeTestRule.setContent {
            GradientPillButton(
                text = "Done",
                enabled = true,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = { clicked = true },
            )
        }

        // When - 클릭
        composeTestRule.onNodeWithText("Done").performClick()

        // Then
        assertTrue(clicked)
    }
    // endregion

    // region FlowRow Tests
    @Test
    fun flowRow_displaysSingleChild() {
        // Given
        composeTestRule.setContent {
            FlowRow {
                androidx.compose.material3.Text("Item 1")
            }
        }

        // Then
        composeTestRule.onNodeWithText("Item 1").assertIsDisplayed()
    }

    @Test
    fun flowRow_displaysMultipleChildren() {
        // Given
        composeTestRule.setContent {
            FlowRow {
                androidx.compose.material3.Text("Item 1")
                androidx.compose.material3.Text("Item 2")
                androidx.compose.material3.Text("Item 3")
            }
        }

        // Then
        composeTestRule.onNodeWithText("Item 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Item 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Item 3").assertIsDisplayed()
    }

    @Test
    fun flowRow_noChildren() {
        // Given
        composeTestRule.setContent {
            FlowRow {
                // No children
            }
        }

        // Then - 에러 없이 렌더링됨
        // (시각적 확인은 불가)
    }
    // endregion

    // region Integration Tests - TagSelectionCard
    @Test
    fun tagSelectionCard_completeUserFlowWithoutSelection() {
        // Given
        var selectedTag = ""
        var doneClicked = false

        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페", "#친구"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = { tag -> selectedTag = tag },
                onAddCustomTag = {},
                onDone = { doneClicked = true },
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Step 1: 제목 확인
        composeTestRule.onNodeWithText("이 추억을 어떻게 기억하고 싶나요?").assertIsDisplayed()

        // Step 2: Done 버튼 비활성화 확인
        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()

        // Step 3: 태그 클릭
        composeTestRule.onNodeWithText("#카페").performClick()
        assertEquals("#카페", selectedTag)
    }

    @Test
    fun tagSelectionCard_completeUserFlowWithSelection() {
        // Given
        var doneClicked = false

        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페", "#친구"),
                selectedTags = setOf("#카페"),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = { doneClicked = true },
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Step 1: Done 버튼 활성화 확인
        composeTestRule.onNodeWithText("Done").assertIsEnabled()

        // Step 2: Done 버튼 클릭
        composeTestRule.onNodeWithText("Done").performClick()
        assertTrue(doneClicked)
    }

    @Test
    fun tagSelectionCard_withAddTagChipFlow() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페", "#친구", "+"),
                selectedTags = setOf("#카페"),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then - 모든 요소가 표시됨
        composeTestRule.onNodeWithText("이 추억을 어떻게 기억하고 싶나요?").assertIsDisplayed()
        composeTestRule.onNodeWithText("#카페").assertIsDisplayed()
        composeTestRule.onNodeWithText("#친구").assertIsDisplayed()
        composeTestRule.onNodeWithText("+").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }
    // endregion

    // region Edge Cases
    @Test
    fun tagSelectionCard_emptyTags() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = emptyList(),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then - 제목과 Done 버튼만 표시
        composeTestRule.onNodeWithText("이 추억을 어떻게 기억하고 싶나요?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun tagSelectionCard_manyTags() {
        // Given
        val manyTags = (1..20).map { "#태그$it" }
        composeTestRule.setContent {
            TagSelectionCard(
                tags = manyTags,
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then - 일부 태그가 표시됨 (FlowRow가 자동으로 줄바꿈)
        composeTestRule.onNodeWithText("#태그1").assertIsDisplayed()
        composeTestRule.onNodeWithText("#태그5").assertIsDisplayed()
    }

    @Test
    fun tagSelectionCard_longTagText() {
        // Given
        val longTag = "#이것은매우긴태그텍스트입니다정말로아주길어요"
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf(longTag),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(longTag).assertIsDisplayed()
    }

    @Test
    fun tagSelectionCard_specialCharacterTags() {
        // Given
        val tags = listOf("#@카페!", "#친구&", "#100%")
        composeTestRule.setContent {
            TagSelectionCard(
                tags = tags,
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        tags.forEach { tag ->
            composeTestRule.onNodeWithText(tag).assertIsDisplayed()
        }
    }

    @Test
    fun tagSelectionCard_allTagsSelected() {
        // Given
        val tags = listOf("#카페", "#친구", "#디저트")
        composeTestRule.setContent {
            TagSelectionCard(
                tags = tags,
                selectedTags = tags.toSet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then - Done 버튼 활성화
        composeTestRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun gradientPillButton_emptyText() {
        // Given
        composeTestRule.setContent {
            GradientPillButton(
                text = "",
                enabled = true,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = {},
            )
        }

        // Then - 빈 텍스트도 렌더링됨 (버튼은 표시)
        // (시각적으로만 확인 가능)
    }

    @Test
    fun gradientPillButton_veryLongText() {
        // Given
        val longText = "이것은 매우 긴 버튼 텍스트입니다 정말로 아주 길어요"
        composeTestRule.setContent {
            GradientPillButton(
                text = longText,
                enabled = true,
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(longText).assertIsDisplayed()
    }
    // endregion

    // region Korean Text Tests
    @Test
    fun tagSelectionCard_koreanTitle() {
        // Given
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        val koreanTitle = "이 추억을 어떻게 기억하고 싶나요?"
        composeTestRule.onNodeWithText(koreanTitle).assertIsDisplayed()
    }

    @Test
    fun tagSelectionCard_koreanTags() {
        // Given
        val koreanTags = listOf("#한국어", "#테스트", "#태그")
        composeTestRule.setContent {
            TagSelectionCard(
                tags = koreanTags,
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // Then
        koreanTags.forEach { tag ->
            composeTestRule.onNodeWithText(tag).assertIsDisplayed()
        }
    }

    @Test
    fun scrollHintOverlay_koreanText() {
        // Given
        composeTestRule.setContent {
            ScrollHintOverlay()
        }

        // Then
        composeTestRule.onNodeWithText("스크롤하여 다음 추억").assertIsDisplayed()
    }
    // endregion

    // region Callback Verification Tests
    @Test
    fun tagSelectionCard_onToggleCalledWithCorrectTag() {
        // Given
        val receivedTags = mutableListOf<String>()
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페", "#친구", "#디저트"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = { tag -> receivedTags.add(tag) },
                onAddCustomTag = {},
                onDone = {},
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // When
        composeTestRule.onNodeWithText("#카페").performClick()
        composeTestRule.onNodeWithText("#디저트").performClick()

        // Then
        assertEquals(2, receivedTags.size)
        assertEquals("#카페", receivedTags[0])
        assertEquals("#디저트", receivedTags[1])
    }

    @Test
    fun tagSelectionCard_onDoneCalledOnlyWhenEnabled() {
        // Given
        var doneCount = 0
        composeTestRule.setContent {
            TagSelectionCard(
                tags = listOf("#카페"),
                selectedTags = emptySet(),
                storyTagSubmissionState = StoryTagSubmissionState.Idle,
                isViewed = false,
                isEditMode = false,
                onTagToggle = {},
                onAddCustomTag = {},
                onDone = { doneCount++ },
                onRetry = {},
                onEdit = {},
                onSuccess = {},
            )
        }

        // When - 비활성화 상태에서 클릭
        composeTestRule.onNodeWithText("Done").performClick()

        // Then
        assertEquals(0, doneCount)
    }
    // endregion
}
