package com.example.momentag

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tag.kt의 Composable 함수들에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TagComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region tagChip Tests - Plain Variant
    @Test
    fun tagChip_plain_displaysText() {
        // Given
        composeTestRule.setContent {
            tagChip(text = "#카페", variant = TagVariant.Plain)
        }

        // Then
        composeTestRule.onNodeWithText("#카페").assertIsDisplayed()
    }

    @Test
    fun tagChip_plain_noCloseButton() {
        // Given
        composeTestRule.setContent {
            tagChip(text = "#카페", variant = TagVariant.Plain)
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertDoesNotExist()
    }

    @Test
    fun tagChip_plain_englishText() {
        // Given
        composeTestRule.setContent {
            tagChip(text = "#coffee", variant = TagVariant.Plain)
        }

        // Then
        composeTestRule.onNodeWithText("#coffee").assertIsDisplayed()
    }

    @Test
    fun tagChip_plain_longText() {
        // Given
        val longText = "#이것은매우긴태그텍스트입니다"
        composeTestRule.setContent {
            tagChip(text = longText, variant = TagVariant.Plain)
        }

        // Then
        composeTestRule.onNodeWithText(longText).assertIsDisplayed()
    }

    @Test
    fun tagChip_plain_specialCharacters() {
        // Given
        composeTestRule.setContent {
            tagChip(text = "#@카페!", variant = TagVariant.Plain)
        }

        // Then
        composeTestRule.onNodeWithText("#@카페!").assertIsDisplayed()
    }
    // endregion

    // region tagChip Tests - CloseAlways Variant
    @Test
    fun tagChip_closeAlways_displaysText() {
        // Given
        composeTestRule.setContent {
            tagChip(
                text = "#카페",
                variant = TagVariant.CloseAlways(onDismiss = {}),
            )
        }

        // Then
        composeTestRule.onNodeWithText("#카페").assertIsDisplayed()
    }

    @Test
    fun tagChip_closeAlways_displaysCloseButton() {
        // Given
        composeTestRule.setContent {
            tagChip(
                text = "#카페",
                variant = TagVariant.CloseAlways(onDismiss = {}),
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertIsDisplayed()
    }

    @Test
    fun tagChip_closeAlways_clickCloseButton() {
        // Given
        var dismissCalled = false
        composeTestRule.setContent {
            tagChip(
                text = "#카페",
                variant = TagVariant.CloseAlways(onDismiss = { dismissCalled = true }),
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").performClick()

        // Then
        assertTrue(dismissCalled)
    }

    @Test
    fun tagChip_closeAlways_multipleClicks() {
        // Given
        var dismissCount = 0
        composeTestRule.setContent {
            tagChip(
                text = "#카페",
                variant = TagVariant.CloseAlways(onDismiss = { dismissCount++ }),
            )
        }

        // When
        repeat(3) {
            composeTestRule.onNodeWithContentDescription("Dismiss Tag").performClick()
        }

        // Then
        assertEquals(3, dismissCount)
    }
    // endregion

    // region tagChip Tests - CloseWhen Variant
    @Test
    fun tagChip_closeWhen_deleteModeTrue_displaysCloseButton() {
        // Given
        composeTestRule.setContent {
            tagChip(
                text = "#카페",
                variant = TagVariant.CloseWhen(isDeleteMode = true, onDismiss = {}),
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertIsDisplayed()
    }

    @Test
    fun tagChip_closeWhen_deleteModeFalse_noCloseButton() {
        // Given
        composeTestRule.setContent {
            tagChip(
                text = "#카페",
                variant = TagVariant.CloseWhen(isDeleteMode = false, onDismiss = {}),
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertDoesNotExist()
    }

    @Test
    fun tagChip_closeWhen_deleteModeTrue_clickCloseButton() {
        // Given
        var dismissCalled = false
        composeTestRule.setContent {
            tagChip(
                text = "#카페",
                variant =
                    TagVariant.CloseWhen(
                        isDeleteMode = true,
                        onDismiss = { dismissCalled = true },
                    ),
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").performClick()

        // Then
        assertTrue(dismissCalled)
    }

    @Test
    fun tagChip_closeWhen_multipleClicks() {
        // Given
        var dismissCount = 0
        composeTestRule.setContent {
            tagChip(
                text = "#카페",
                variant =
                    TagVariant.CloseWhen(
                        isDeleteMode = true,
                        onDismiss = { dismissCount++ },
                    ),
            )
        }

        // When
        repeat(2) {
            composeTestRule.onNodeWithContentDescription("Dismiss Tag").performClick()
        }

        // Then
        assertEquals(2, dismissCount)
    }
    // endregion

    // region tagChip Tests - Recommended Variant
    @Test
    fun tagChip_recommended_displaysText() {
        // Given
        composeTestRule.setContent {
            tagChip(text = "#추천태그", variant = TagVariant.Recommended)
        }

        // Then
        composeTestRule.onNodeWithText("#추천태그").assertIsDisplayed()
    }

    @Test
    fun tagChip_recommended_noCloseButton() {
        // Given
        composeTestRule.setContent {
            tagChip(text = "#추천태그", variant = TagVariant.Recommended)
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertDoesNotExist()
    }

    @Test
    fun tagChip_recommended_hasAlpha() {
        // Given - Recommended는 0.5f alpha 적용
        composeTestRule.setContent {
            tagChip(text = "#추천태그", variant = TagVariant.Recommended)
        }

        // Then - 텍스트는 표시되지만 alpha가 적용됨 (시각적으로는 확인 불가)
        composeTestRule.onNodeWithText("#추천태그").assertIsDisplayed()
    }
    // endregion

    // region tag() Wrapper Function Tests
    @Test
    fun tag_displaysText() {
        // Given
        composeTestRule.setContent {
            tag(text = "#기본태그")
        }

        // Then
        composeTestRule.onNodeWithText("#기본태그").assertIsDisplayed()
    }

    @Test
    fun tag_noCloseButton() {
        // Given
        composeTestRule.setContent {
            tag(text = "#기본태그")
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertDoesNotExist()
    }

    @Test
    fun tag_koreanText() {
        // Given
        composeTestRule.setContent {
            tag(text = "#한글태그")
        }

        // Then
        composeTestRule.onNodeWithText("#한글태그").assertIsDisplayed()
    }
    // endregion

    // region tagX() Wrapper Function Tests
    @Test
    fun tagX_displaysText() {
        // Given
        composeTestRule.setContent {
            tagX(text = "#삭제가능", onDismiss = {})
        }

        // Then
        composeTestRule.onNodeWithText("#삭제가능").assertIsDisplayed()
    }

    @Test
    fun tagX_displaysCloseButton() {
        // Given
        composeTestRule.setContent {
            tagX(text = "#삭제가능", onDismiss = {})
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertIsDisplayed()
    }

    @Test
    fun tagX_clickCloseButton() {
        // Given
        var dismissCalled = false
        composeTestRule.setContent {
            tagX(text = "#삭제가능", onDismiss = { dismissCalled = true })
        }

        // When
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").performClick()

        // Then
        assertTrue(dismissCalled)
    }
    // endregion

    // region tagXMode() Wrapper Function Tests
    @Test
    fun tagXMode_deleteModeTrue_displaysCloseButton() {
        // Given
        composeTestRule.setContent {
            tagXMode(text = "#조건부삭제", isDeleteMode = true, onDismiss = {})
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertIsDisplayed()
    }

    @Test
    fun tagXMode_deleteModeFalse_noCloseButton() {
        // Given
        composeTestRule.setContent {
            tagXMode(text = "#조건부삭제", isDeleteMode = false, onDismiss = {})
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertDoesNotExist()
    }

    @Test
    fun tagXMode_clickCloseButton() {
        // Given
        var dismissCalled = false
        composeTestRule.setContent {
            tagXMode(
                text = "#조건부삭제",
                isDeleteMode = true,
                onDismiss = { dismissCalled = true },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").performClick()

        // Then
        assertTrue(dismissCalled)
    }
    // endregion

    // region tagRecommended() Wrapper Function Tests
    @Test
    fun tagRecommended_displaysText() {
        // Given
        composeTestRule.setContent {
            tagRecommended(text = "#추천")
        }

        // Then
        composeTestRule.onNodeWithText("#추천").assertIsDisplayed()
    }

    @Test
    fun tagRecommended_noCloseButton() {
        // Given
        composeTestRule.setContent {
            tagRecommended(text = "#추천")
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Dismiss Tag").assertDoesNotExist()
    }
    // endregion

    // region StoryTagChip Tests
    @Test
    fun storyTagChip_notSelected_displaysText() {
        // Given
        composeTestRule.setContent {
            StoryTagChip(text = "#스토리", isSelected = false, onClick = {})
        }

        // Then
        composeTestRule.onNodeWithText("#스토리").assertIsDisplayed()
    }

    @Test
    fun storyTagChip_notSelected_noCheckIcon() {
        // Given
        composeTestRule.setContent {
            StoryTagChip(text = "#스토리", isSelected = false, onClick = {})
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Selected").assertDoesNotExist()
    }

    @Test
    fun storyTagChip_selected_displaysText() {
        // Given
        composeTestRule.setContent {
            StoryTagChip(text = "#스토리", isSelected = true, onClick = {})
        }

        // Then
        composeTestRule.onNodeWithText("#스토리").assertIsDisplayed()
    }

    @Test
    fun storyTagChip_selected_displaysCheckIcon() {
        // Given
        composeTestRule.setContent {
            StoryTagChip(text = "#스토리", isSelected = true, onClick = {})
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Selected").assertIsDisplayed()
    }

    @Test
    fun storyTagChip_clickTriggersCallback() {
        // Given
        var clickCalled = false
        composeTestRule.setContent {
            StoryTagChip(text = "#스토리", isSelected = false, onClick = { clickCalled = true })
        }

        // When
        composeTestRule.onNodeWithText("#스토리").performClick()

        // Then
        assertTrue(clickCalled)
    }

    @Test
    fun storyTagChip_multipleClicks() {
        // Given
        var clickCount = 0
        composeTestRule.setContent {
            StoryTagChip(text = "#스토리", isSelected = false, onClick = { clickCount++ })
        }

        // When
        repeat(3) {
            composeTestRule.onNodeWithText("#스토리").performClick()
        }

        // Then
        assertEquals(3, clickCount)
    }

    @Test
    fun storyTagChip_selectedState_clickable() {
        // Given
        var clickCalled = false
        composeTestRule.setContent {
            StoryTagChip(text = "#스토리", isSelected = true, onClick = { clickCalled = true })
        }

        // When
        composeTestRule.onNodeWithText("#스토리").performClick()

        // Then
        assertTrue(clickCalled)
    }
    // endregion

    // region Edge Cases
    @Test
    fun tagChip_emojiText() {
        // Given
        composeTestRule.setContent {
            tagChip(text = "☕️✨", variant = TagVariant.Plain)
        }

        // Then
        composeTestRule.onNodeWithText("☕️✨").assertIsDisplayed()
    }

    @Test
    fun storyTagChip_longText() {
        // Given
        val longText = "#이것은매우긴스토리태그텍스트입니다"
        composeTestRule.setContent {
            StoryTagChip(text = longText, isSelected = false, onClick = {})
        }

        // Then
        composeTestRule.onNodeWithText(longText).assertIsDisplayed()
    }
    // endregion

    // region Multiple Tags Scenario
    @Test
    fun multipleTags_differentVariants() {
        // Given
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                tag(text = "#기본")
                tagX(text = "#삭제", onDismiss = {})
                tagRecommended(text = "#추천")
            }
        }

        // Then
        composeTestRule.onNodeWithText("#기본").assertIsDisplayed()
        composeTestRule.onNodeWithText("#삭제").assertIsDisplayed()
        composeTestRule.onNodeWithText("#추천").assertIsDisplayed()
    }

    @Test
    fun multipleStoryTags_mixedSelection() {
        // Given
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                StoryTagChip(text = "#선택됨", isSelected = true, onClick = {})
                StoryTagChip(text = "#선택안됨", isSelected = false, onClick = {})
            }
        }

        // Then
        composeTestRule.onNodeWithText("#선택됨").assertIsDisplayed()
        composeTestRule.onNodeWithText("#선택안됨").assertIsDisplayed()
    }
    // endregion

    // region Real World Scenarios
    @Test
    fun scenario_cafeTagsWithCloseButtons() {
        // Given
        var tag1Dismissed = false
        var tag2Dismissed = false

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Row {
                tagX(text = "#카페", onDismiss = { tag1Dismissed = true })
                tagX(text = "#디저트", onDismiss = { tag2Dismissed = true })
            }
        }

        // Then
        composeTestRule.onNodeWithText("#카페").assertIsDisplayed()
        composeTestRule.onNodeWithText("#디저트").assertIsDisplayed()
    }

    @Test
    fun scenario_storyTagSelection() {
        // Given
        var selectedCount = 0
        val tags = listOf("#여행", "#가족", "#바다")

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                tags.forEach { tagText ->
                    StoryTagChip(
                        text = tagText,
                        isSelected = false,
                        onClick = { selectedCount++ },
                    )
                }
            }
        }

        // When - 모든 태그 클릭
        tags.forEach { tagText ->
            composeTestRule.onNodeWithText(tagText).performClick()
        }

        // Then
        assertEquals(3, selectedCount)
    }

    @Test
    fun scenario_mixedTagVariants() {
        // Given
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                tag(text = "#기본태그")
                tagX(text = "#삭제가능", onDismiss = {})
                tagXMode(text = "#조건부", isDeleteMode = false, onDismiss = {})
                tagRecommended(text = "#추천태그")
                StoryTagChip(text = "#스토리태그", isSelected = true, onClick = {})
            }
        }

        // Then - 모든 태그가 표시됨
        composeTestRule.onNodeWithText("#기본태그").assertIsDisplayed()
        composeTestRule.onNodeWithText("#삭제가능").assertIsDisplayed()
        composeTestRule.onNodeWithText("#조건부").assertIsDisplayed()
        composeTestRule.onNodeWithText("#추천태그").assertIsDisplayed()
        composeTestRule.onNodeWithText("#스토리태그").assertIsDisplayed()
    }
    // endregion

    // region Korean Text Coverage
    @Test
    fun koreanTags_variousTexts() {
        // Given
        val koreanTags = listOf("#한글", "#태그", "#테스트", "#커버리지")

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                koreanTags.forEach { tagText ->
                    tag(text = tagText)
                }
            }
        }

        // Then
        koreanTags.forEach { tagText ->
            composeTestRule.onNodeWithText(tagText).assertIsDisplayed()
        }
    }

    @Test
    fun storyTagChip_koreanWithSelection() {
        // Given
        composeTestRule.setContent {
            StoryTagChip(text = "#한국어태그", isSelected = true, onClick = {})
        }

        // Then
        composeTestRule.onNodeWithText("#한국어태그").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Selected").assertIsDisplayed()
    }
    // endregion
}
