package com.example.momentag.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.Color
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
 * WarningBanner Composable 함수에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WarningBannerComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region Basic Display Tests
    @Test
    fun warningBanner_displaysTitle() {
        // Given
        val title = "Warning Title"
        composeTestRule.setContent {
            WarningBanner(
                title = title,
                message = "Test message",
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun warningBanner_displaysMessage() {
        // Given
        val message = "This is a warning message"
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = message,
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun warningBanner_displaysBothTitleAndMessage() {
        // Given
        val title = "Error Occurred"
        val message = "Please try again"
        composeTestRule.setContent {
            WarningBanner(
                title = title,
                message = message,
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun warningBanner_displaysWarningIcon() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Warning Icon").assertIsDisplayed()
    }
    // endregion

    // region Action Button Tests
    @Test
    fun warningBanner_showsActionButtonByDefault() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()
    }

    @Test
    fun warningBanner_actionButton_triggersCallback() {
        // Given
        var actionClicked = false
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = { actionClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Action").performClick()

        // Then
        assertTrue(actionClicked)
    }

    @Test
    fun warningBanner_actionButton_multipleClicks() {
        // Given
        var clickCount = 0
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = { clickCount++ },
            )
        }

        // When - 3번 클릭
        repeat(3) {
            composeTestRule.onNodeWithContentDescription("Action").performClick()
        }

        // Then
        assertEquals(3, clickCount)
    }

    @Test
    fun warningBanner_showActionButton_false_hidesActionButton() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                showActionButton = false,
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Action").assertDoesNotExist()
    }

    @Test
    fun warningBanner_customActionIcon() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                actionIcon = Icons.Default.Check,
            )
        }

        // Then - 아이콘은 표시되지만 특정 아이콘을 구분하기는 어려움
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()
    }
    // endregion

    // region Dismiss Button Tests
    @Test
    fun warningBanner_dismissButton_notShownByDefault() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun warningBanner_dismissButton_shownWhenEnabled() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                onDismiss = {},
                showDismissButton = true,
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun warningBanner_dismissButton_triggersCallback() {
        // Given
        var dismissClicked = false
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                onDismiss = { dismissClicked = true },
                showDismissButton = true,
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Close").performClick()

        // Then
        assertTrue(dismissClicked)
    }

    @Test
    fun warningBanner_dismissButton_multipleClicks() {
        // Given
        var dismissCount = 0
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                onDismiss = { dismissCount++ },
                showDismissButton = true,
            )
        }

        // When - 2번 클릭
        repeat(2) {
            composeTestRule.onNodeWithContentDescription("Close").performClick()
        }

        // Then
        assertEquals(2, dismissCount)
    }

    @Test
    fun warningBanner_dismissButton_notShownWithoutCallback() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                onDismiss = null,
                showDismissButton = true,
            )
        }

        // Then - onDismiss가 null이면 showDismissButton이 true여도 표시 안됨
        composeTestRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }
    // endregion

    // region Custom Styling Tests
    @Test
    fun warningBanner_customBackgroundColor() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                backgroundColor = Color.Green,
            )
        }

        // Then - 색상은 시각적으로만 확인 가능, 컴포넌트는 정상 렌더링
        composeTestRule.onNodeWithText("Title").assertIsDisplayed()
    }

    @Test
    fun warningBanner_customIcon() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                icon = Icons.Default.Info,
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Warning Icon").assertIsDisplayed()
    }
    // endregion

    // region All Features Together Tests
    @Test
    fun warningBanner_allFeaturesEnabled() {
        // Given
        var actionClicked = false
        var dismissClicked = false

        composeTestRule.setContent {
            WarningBanner(
                title = "Complete Warning",
                message = "All features enabled",
                onActionClick = { actionClicked = true },
                onDismiss = { dismissClicked = true },
                showActionButton = true,
                showDismissButton = true,
            )
        }

        // Then - 모든 요소가 표시됨
        composeTestRule.onNodeWithText("Complete Warning").assertIsDisplayed()
        composeTestRule.onNodeWithText("All features enabled").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Warning Icon").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()

        // When - 액션 버튼 클릭
        composeTestRule.onNodeWithContentDescription("Action").performClick()
        // Then
        assertTrue(actionClicked)

        // When - 닫기 버튼 클릭
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        // Then
        assertTrue(dismissClicked)
    }

    @Test
    fun warningBanner_allFeaturesDisabled() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Minimal Warning",
                message = "No buttons",
                onActionClick = {},
                showActionButton = false,
                showDismissButton = false,
            )
        }

        // Then - 텍스트와 아이콘만 표시
        composeTestRule.onNodeWithText("Minimal Warning").assertIsDisplayed()
        composeTestRule.onNodeWithText("No buttons").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Warning Icon").assertIsDisplayed()

        // 버튼들은 표시 안됨
        composeTestRule.onNodeWithContentDescription("Action").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun warningBanner_allCustomParameters() {
        // Given
        val title = "Custom Title"
        val message = "Custom Message"
        var actionClicked = false

        composeTestRule.setContent {
            WarningBanner(
                title = title,
                message = message,
                onActionClick = { actionClicked = true },
                backgroundColor = Color.Blue,
                icon = Icons.Default.Info,
                actionIcon = Icons.Default.Check,
                showActionButton = true,
                showDismissButton = false,
            )
        }

        // Then
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Warning Icon").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()

        // When
        composeTestRule.onNodeWithContentDescription("Action").performClick()

        // Then
        assertTrue(actionClicked)
    }
    // endregion

    // region Edge Cases
    @Test
    fun warningBanner_emptyTitle() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "",
                message = "Message only",
                onActionClick = {},
            )
        }

        // Then - 빈 타이틀이지만 렌더링됨
        composeTestRule.onNodeWithText("Message only").assertIsDisplayed()
    }

    @Test
    fun warningBanner_emptyMessage() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title only",
                message = "",
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Title only").assertIsDisplayed()
    }

    @Test
    fun warningBanner_longTitle() {
        // Given
        val longTitle = "This is a very long warning title that might wrap to multiple lines"
        composeTestRule.setContent {
            WarningBanner(
                title = longTitle,
                message = "Message",
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(longTitle).assertIsDisplayed()
    }

    @Test
    fun warningBanner_longMessage() {
        // Given
        val longMessage = "This is a very long warning message that provides detailed information about the issue"
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = longMessage,
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(longMessage).assertIsDisplayed()
    }

    @Test
    fun warningBanner_multilineMessage() {
        // Given
        val multilineMessage = "Line 1\nLine 2\nLine 3"
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = multilineMessage,
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(multilineMessage).assertIsDisplayed()
    }

    @Test
    fun warningBanner_specialCharacters() {
        // Given
        val specialTitle = "Error: <Connection> Failed!"
        val specialMessage = "Check & retry \"now\""
        composeTestRule.setContent {
            WarningBanner(
                title = specialTitle,
                message = specialMessage,
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(specialTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(specialMessage).assertIsDisplayed()
    }

    @Test
    fun warningBanner_koreanText() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "경고",
                message = "작업이 지연되고 있습니다.",
                onActionClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("경고").assertIsDisplayed()
        composeTestRule.onNodeWithText("작업이 지연되고 있습니다.").assertIsDisplayed()
    }
    // endregion

    // region Button Combinations Tests
    @Test
    fun warningBanner_onlyActionButton() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                showActionButton = true,
                showDismissButton = false,
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun warningBanner_onlyDismissButton() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                onDismiss = {},
                showActionButton = false,
                showDismissButton = true,
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Action").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun warningBanner_bothButtons() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                onDismiss = {},
                showActionButton = true,
                showDismissButton = true,
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun warningBanner_noButtons() {
        // Given
        composeTestRule.setContent {
            WarningBanner(
                title = "Title",
                message = "Message",
                onActionClick = {},
                showActionButton = false,
                showDismissButton = false,
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Action").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }
    // endregion
}
