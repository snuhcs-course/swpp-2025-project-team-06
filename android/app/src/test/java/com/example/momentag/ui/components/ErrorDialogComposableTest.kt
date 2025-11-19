package com.example.momentag.ui.components

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
 * ErrorDialog 및 ErrorOverlay Composable 함수에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ErrorDialogComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region errorDialog Tests
    @Test
    fun errorDialog_displaysDefaultTitle() {
        // Given
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "Test error",
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("ERROR").assertIsDisplayed()
    }

    @Test
    fun errorDialog_displaysCustomTitle() {
        // Given
        val customTitle = "Connection Failed"
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "Test error",
                onRetry = {},
                title = customTitle,
            )
        }

        // Then
        composeTestRule.onNodeWithText(customTitle).assertIsDisplayed()
    }

    @Test
    fun errorDialog_displaysErrorMessage() {
        // Given
        val errorMessage = "Network connection failed"
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = errorMessage,
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    @Test
    fun errorDialog_displaysDefaultRetryButton() {
        // Given
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "Test error",
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("RETRY").assertIsDisplayed()
    }

    @Test
    fun errorDialog_displaysCustomRetryButtonText() {
        // Given
        val customRetryText = "Try Again"
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "Test error",
                onRetry = {},
                retryButtonText = customRetryText,
            )
        }

        // Then
        composeTestRule.onNodeWithText(customRetryText).assertIsDisplayed()
    }

    @Test
    fun errorDialog_retryButton_triggersCallback() {
        // Given
        var retryClicked = false
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "Test error",
                onRetry = { retryClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("RETRY").performClick()

        // Then
        assertTrue(retryClicked)
    }

    @Test
    fun errorDialog_retryButton_multipleClicks() {
        // Given
        var retryCount = 0
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "Test error",
                onRetry = { retryCount++ },
            )
        }

        // When - 3번 클릭
        repeat(3) {
            composeTestRule.onNodeWithText("RETRY").performClick()
        }

        // Then
        assertEquals(3, retryCount)
    }

    @Test
    fun errorDialog_allCustomParameters() {
        // Given
        val customTitle = "Custom Title"
        val customMessage = "Custom error message"
        val customRetryText = "Retry Now"
        var retryClicked = false

        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = customMessage,
                onRetry = { retryClicked = true },
                title = customTitle,
                retryButtonText = customRetryText,
            )
        }

        // Then - 모든 커스텀 텍스트가 표시됨
        composeTestRule.onNodeWithText(customTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(customMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(customRetryText).assertIsDisplayed()

        // When
        composeTestRule.onNodeWithText(customRetryText).performClick()

        // Then
        assertTrue(retryClicked)
    }

    @Test
    fun errorDialog_multilineErrorMessage() {
        // Given
        val multilineMessage = "Line 1\nLine 2\nLine 3"
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = multilineMessage,
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(multilineMessage).assertIsDisplayed()
    }

    @Test
    fun errorDialog_longErrorMessage() {
        // Given
        val longMessage = "This is a very long error message that might need to wrap to multiple lines in the dialog"
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = longMessage,
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(longMessage).assertIsDisplayed()
    }

    @Test
    fun errorDialog_withDismissCallback() {
        // Given
        var dismissCalled = false
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "Test error",
                onRetry = {},
                onDismiss = { dismissCalled = true },
                dismissible = true,
            )
        }

        // Then - onDismiss 콜백이 설정되어 있음 (백프레스나 외부 클릭으로 호출됨)
        // Note: Dialog의 onDismissRequest는 UI 테스트에서 직접 트리거하기 어려움
        composeTestRule.onNodeWithText("ERROR").assertIsDisplayed()
    }
    // endregion

    // region ErrorOverlay Tests
    @Test
    fun errorOverlay_displaysDefaultTitle() {
        // Given
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("ERROR").assertIsDisplayed()
    }

    @Test
    fun errorOverlay_displaysCustomTitle() {
        // Given
        val customTitle = "Server Error"
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = {},
                title = customTitle,
            )
        }

        // Then
        composeTestRule.onNodeWithText(customTitle).assertIsDisplayed()
    }

    @Test
    fun errorOverlay_displaysErrorMessage() {
        // Given
        val errorMessage = "Failed to load data"
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = errorMessage,
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }

    @Test
    fun errorOverlay_displaysDefaultRetryButton() {
        // Given
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("RETRY").assertIsDisplayed()
    }

    @Test
    fun errorOverlay_displaysCustomRetryButtonText() {
        // Given
        val customRetryText = "Reload"
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = {},
                retryButtonText = customRetryText,
            )
        }

        // Then
        composeTestRule.onNodeWithText(customRetryText).assertIsDisplayed()
    }

    @Test
    fun errorOverlay_retryButton_triggersCallback() {
        // Given
        var retryClicked = false
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = { retryClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("RETRY").performClick()

        // Then
        assertTrue(retryClicked)
    }

    @Test
    fun errorOverlay_retryButton_multipleClicks() {
        // Given
        var retryCount = 0
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = { retryCount++ },
            )
        }

        // When - 2번 클릭
        repeat(2) {
            composeTestRule.onNodeWithText("RETRY").performClick()
        }

        // Then
        assertEquals(2, retryCount)
    }

    @Test
    fun errorOverlay_withoutDismissButton_noCloseIcon() {
        // Given
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = {},
                onDismiss = null,
            )
        }

        // Then - Close 아이콘이 표시되지 않음
        composeTestRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun errorOverlay_withDismissButton_showsCloseIcon() {
        // Given
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = {},
                onDismiss = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun errorOverlay_closeButton_triggersCallback() {
        // Given
        var dismissClicked = false
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = {},
                onDismiss = { dismissClicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Close").performClick()

        // Then
        assertTrue(dismissClicked)
    }

    @Test
    fun errorOverlay_closeButton_multipleClicks() {
        // Given
        var dismissCount = 0
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test error",
                onRetry = {},
                onDismiss = { dismissCount++ },
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
    fun errorOverlay_allCustomParameters() {
        // Given
        val customTitle = "Loading Failed"
        val customMessage = "Unable to fetch data from server"
        val customRetryText = "Refresh"
        var retryClicked = false
        var dismissClicked = false

        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = customMessage,
                onRetry = { retryClicked = true },
                onDismiss = { dismissClicked = true },
                title = customTitle,
                retryButtonText = customRetryText,
            )
        }

        // Then - 모든 요소가 표시됨
        composeTestRule.onNodeWithText(customTitle).assertIsDisplayed()
        composeTestRule.onNodeWithText(customMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText(customRetryText).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()

        // When - 재시도 버튼 클릭
        composeTestRule.onNodeWithText(customRetryText).performClick()
        // Then
        assertTrue(retryClicked)

        // When - 닫기 버튼 클릭
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        // Then
        assertTrue(dismissClicked)
    }

    @Test
    fun errorOverlay_multilineErrorMessage() {
        // Given
        val multilineMessage = "Error occurred\nPlease try again\nContact support if problem persists"
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = multilineMessage,
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(multilineMessage).assertIsDisplayed()
    }
    // endregion

    // region Comparison Tests
    @Test
    fun bothComponents_displaySameContent() {
        // errorDialog
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "Test message",
                onRetry = {},
                title = "Test Title",
            )
        }

        // Then
        composeTestRule.onNodeWithText("Test Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test message").assertIsDisplayed()
        composeTestRule.onNodeWithText("RETRY").assertIsDisplayed()
    }

    @Test
    fun errorOverlay_displaysSameContent() {
        // ErrorOverlay
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "Test message",
                onRetry = {},
                title = "Test Title",
            )
        }

        // Then
        composeTestRule.onNodeWithText("Test Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test message").assertIsDisplayed()
        composeTestRule.onNodeWithText("RETRY").assertIsDisplayed()
    }
    // endregion

    // region Edge Cases
    @Test
    fun errorDialog_emptyErrorMessage() {
        // Given
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "",
                onRetry = {},
            )
        }

        // Then - 빈 메시지지만 다이얼로그는 렌더링됨
        composeTestRule.onNodeWithText("ERROR").assertIsDisplayed()
        composeTestRule.onNodeWithText("RETRY").assertIsDisplayed()
    }

    @Test
    fun errorOverlay_emptyErrorMessage() {
        // Given
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "",
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("ERROR").assertIsDisplayed()
        composeTestRule.onNodeWithText("RETRY").assertIsDisplayed()
    }

    @Test
    fun errorDialog_specialCharactersInMessage() {
        // Given
        val specialMessage = "Error: <Network> & \"Connection\" failed!"
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = specialMessage,
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(specialMessage).assertIsDisplayed()
    }

    @Test
    fun errorOverlay_specialCharactersInMessage() {
        // Given
        val specialMessage = "Error: <Server> & \"Response\" timeout!"
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = specialMessage,
                onRetry = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(specialMessage).assertIsDisplayed()
    }

    @Test
    fun errorDialog_koreanText() {
        // Given
        composeTestRule.setContent {
            ErrorDialog(
                errorMessage = "네트워크 연결에 실패했습니다.",
                onRetry = {},
                title = "오류",
                retryButtonText = "재시도",
            )
        }

        // Then
        composeTestRule.onNodeWithText("오류").assertIsDisplayed()
        composeTestRule.onNodeWithText("네트워크 연결에 실패했습니다.").assertIsDisplayed()
        composeTestRule.onNodeWithText("재시도").assertIsDisplayed()
    }

    @Test
    fun errorOverlay_koreanText() {
        // Given
        composeTestRule.setContent {
            ErrorOverlay(
                errorMessage = "데이터 로딩 실패",
                onRetry = {},
                title = "에러",
                retryButtonText = "다시 시도",
            )
        }

        // Then
        composeTestRule.onNodeWithText("에러").assertIsDisplayed()
        composeTestRule.onNodeWithText("데이터 로딩 실패").assertIsDisplayed()
        composeTestRule.onNodeWithText("다시 시도").assertIsDisplayed()
    }
    // endregion
}
