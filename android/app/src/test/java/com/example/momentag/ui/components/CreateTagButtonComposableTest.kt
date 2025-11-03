package com.example.momentag.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
 * CreateTagButton Composable 함수에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CreateTagButtonComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun createTagButton_displaysDefaultText() {
        // Given
        composeTestRule.setContent {
            CreateTagButton(onClick = {})
        }

        // Then
        composeTestRule.onNodeWithText("Create Tag").assertIsDisplayed()
    }

    @Test
    fun createTagButton_displaysCustomText() {
        // Given
        val customText = "Add New Tag"
        composeTestRule.setContent {
            CreateTagButton(
                text = customText,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(customText).assertIsDisplayed()
    }

    @Test
    fun createTagButton_displaysIcon() {
        // Given
        composeTestRule.setContent {
            CreateTagButton(onClick = {})
        }

        // Then - ContentDescription이 text와 같음
        composeTestRule.onNodeWithContentDescription("Create Tag").assertIsDisplayed()
    }

    @Test
    fun createTagButton_iconWithCustomText_hasMatchingContentDescription() {
        // Given
        val customText = "Custom Button"
        composeTestRule.setContent {
            CreateTagButton(
                text = customText,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription(customText).assertIsDisplayed()
    }

    @Test
    fun createTagButton_isEnabledByDefault() {
        // Given
        composeTestRule.setContent {
            CreateTagButton(onClick = {})
        }

        // Then
        composeTestRule.onNodeWithText("Create Tag").assertIsEnabled()
    }

    @Test
    fun createTagButton_canBeDisabled() {
        // Given
        composeTestRule.setContent {
            CreateTagButton(
                enabled = false,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Create Tag").assertIsNotEnabled()
    }

    @Test
    fun createTagButton_enabled_isClickable() {
        // Given
        composeTestRule.setContent {
            CreateTagButton(
                enabled = true,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Create Tag").assertHasClickAction()
    }

    @Test
    fun createTagButton_disabled_stillHasClickAction() {
        // Given
        composeTestRule.setContent {
            CreateTagButton(
                enabled = false,
                onClick = {},
            )
        }

        // Then - disabled 상태여도 clickAction은 있음 (단지 작동하지 않을 뿐)
        composeTestRule.onNodeWithText("Create Tag").assertHasClickAction()
    }

    @Test
    fun createTagButton_onClick_triggersCallback() {
        // Given
        var clicked = false
        composeTestRule.setContent {
            CreateTagButton(
                onClick = { clicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("Create Tag").performClick()

        // Then
        assertTrue(clicked)
    }

    @Test
    fun createTagButton_multipleClicks_triggersMultipleTimes() {
        // Given
        var clickCount = 0
        composeTestRule.setContent {
            CreateTagButton(
                onClick = { clickCount++ },
            )
        }

        // When - 3번 클릭
        repeat(3) {
            composeTestRule.onNodeWithText("Create Tag").performClick()
        }

        // Then
        assertEquals(3, clickCount)
    }

    @Test
    fun createTagButton_disabled_doesNotTriggerCallback() {
        // Given
        var clicked = false
        composeTestRule.setContent {
            CreateTagButton(
                enabled = false,
                onClick = { clicked = true },
            )
        }

        // When - disabled 버튼 클릭 시도
        composeTestRule.onNodeWithText("Create Tag").performClick()

        // Then - 콜백이 호출되지 않음
        assertTrue(!clicked)
    }

    @Test
    fun createTagButton_withAllParameters() {
        // Given
        var clicked = false
        val customText = "Submit Tag"

        composeTestRule.setContent {
            CreateTagButton(
                text = customText,
                enabled = true,
                onClick = { clicked = true },
            )
        }

        // Then - 모든 요소가 올바르게 표시됨
        composeTestRule.onNodeWithText(customText).assertIsDisplayed()
        composeTestRule.onNodeWithText(customText).assertIsEnabled()
        composeTestRule.onNodeWithContentDescription(customText).assertIsDisplayed()

        // When - 클릭
        composeTestRule.onNodeWithText(customText).performClick()

        // Then
        assertTrue(clicked)
    }

    @Test
    fun createTagButton_emptyText_stillRenders() {
        // Given
        composeTestRule.setContent {
            CreateTagButton(
                text = "",
                onClick = {},
            )
        }

        // Then - 빈 텍스트도 렌더링됨
        composeTestRule.onNodeWithText("").assertExists()
    }

    @Test
    fun createTagButton_longText_displaysCorrectly() {
        // Given
        val longText = "This is a very long button text that might wrap"
        composeTestRule.setContent {
            CreateTagButton(
                text = longText,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(longText).assertIsDisplayed()
    }

    @Test
    fun createTagButton_enabledState_textVisible() {
        // Given - enabled 상태
        composeTestRule.setContent {
            CreateTagButton(
                text = "Enabled Button",
                enabled = true,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Enabled Button").assertIsDisplayed()
    }

    @Test
    fun createTagButton_disabledState_textVisible() {
        // Given - disabled 상태
        composeTestRule.setContent {
            CreateTagButton(
                text = "Disabled Button",
                enabled = false,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Disabled Button").assertIsDisplayed()
    }

    @Test
    fun createTagButton_enabledState_callbackTriggered() {
        // Given
        var clickCount = 0

        composeTestRule.setContent {
            CreateTagButton(
                text = "Enabled Button",
                enabled = true,
                onClick = { clickCount++ },
            )
        }

        // When - enabled 상태에서 클릭
        composeTestRule.onNodeWithText("Enabled Button").performClick()

        // Then
        assertEquals(1, clickCount)
    }

    @Test
    fun createTagButton_disabledState_callbackNotTriggered() {
        // Given
        var clickCount = 0

        composeTestRule.setContent {
            CreateTagButton(
                text = "Disabled Button",
                enabled = false,
                onClick = { clickCount++ },
            )
        }

        // When - disabled 상태에서 클릭 시도
        composeTestRule.onNodeWithText("Disabled Button").performClick()

        // Then - 콜백 호출 안됨
        assertEquals(0, clickCount)
    }

    @Test
    fun createTagButton_iconAndText_bothVisible() {
        // Given
        val text = "Create Tag"
        composeTestRule.setContent {
            CreateTagButton(
                text = text,
                onClick = {},
            )
        }

        // Then - 텍스트와 아이콘 모두 표시됨
        composeTestRule.onNodeWithText(text).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(text).assertIsDisplayed()
    }

    @Test
    fun createTagButton_clickCallback_receivesCorrectContext() {
        // Given
        var callbackExecuted = false
        var buttonText = ""

        composeTestRule.setContent {
            val text = "Test Button"
            CreateTagButton(
                text = text,
                onClick = {
                    callbackExecuted = true
                    buttonText = text
                },
            )
        }

        // When
        composeTestRule.onNodeWithText("Test Button").performClick()

        // Then
        assertTrue(callbackExecuted)
        assertEquals("Test Button", buttonText)
    }
}
