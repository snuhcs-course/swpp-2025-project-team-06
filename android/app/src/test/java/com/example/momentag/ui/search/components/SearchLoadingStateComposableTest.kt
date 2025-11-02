package com.example.momentag.ui.search.components

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
 * SearchLoadingStateCustom Composable 함수에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchLoadingStateComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region Basic Display Tests
    @Test
    fun searchLoadingState_displaysLoadingText() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // Then
        composeTestRule.onNodeWithText("Loading ...").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_displaysBearEmoji() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // Then
        composeTestRule.onNodeWithText("🐻").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_displaysAllLoadingElements() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // Then - 곰돌이, 로딩 텍스트, 프로그레스 바 표시
        composeTestRule.onNodeWithText("🐻").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading ...").assertIsDisplayed()
    }
    // endregion

    // region Warning Banner Tests - Before Delay
    @Test
    fun searchLoadingState_warningNotShownInitially() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // Then - 초기에는 경고 메시지가 표시되지 않음
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Please refresh the page.").assertDoesNotExist()
    }

    @Test
    fun searchLoadingState_warningNotShownBefore5Seconds() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When - 3초만 대기
        composeTestRule.mainClock.advanceTimeBy(3000)

        // Then - 아직 경고 메시지가 표시되지 않음
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertDoesNotExist()
    }
    // endregion

    // region Warning Banner Tests - After Delay
    @Test
    fun searchLoadingState_warningShownAfter5Seconds() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When - 5초 대기
        composeTestRule.mainClock.advanceTimeBy(5000)

        // Then - 경고 메시지가 표시됨
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please refresh the page.").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_warningShownAfter6Seconds() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When - 6초 대기 (5초보다 더 오래)
        composeTestRule.mainClock.advanceTimeBy(6000)

        // Then
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_warningPersistsAfter10Seconds() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When - 10초 대기
        composeTestRule.mainClock.advanceTimeBy(10000)

        // Then - 경고 메시지가 계속 표시됨
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
    }
    // endregion

    // region Refresh Button Tests
    @Test
    fun searchLoadingState_refreshButtonClickable() {
        // Given
        var refreshClicked = false
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = { refreshClicked = true })
        }

        // When - 5초 대기 후 새로고침 버튼 클릭
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Action").performClick()

        // Then
        assertTrue(refreshClicked)
    }

    @Test
    fun searchLoadingState_refreshButtonMultipleClicks() {
        // Given
        var clickCount = 0
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = { clickCount++ })
        }

        // When - 5초 대기 후 여러 번 클릭
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        repeat(3) {
            composeTestRule.onNodeWithContentDescription("Action").performClick()
        }

        // Then
        assertEquals(3, clickCount)
    }

    @Test
    fun searchLoadingState_refreshCallbackInvoked() {
        // Given
        var refreshCount = 0
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = { refreshCount++ })
        }

        // When
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Action").performClick()

        // Then
        assertEquals(1, refreshCount)
    }
    // endregion

    // region Warning Icon Tests
    @Test
    fun searchLoadingState_warningIconDisplayed() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then
        composeTestRule.onNodeWithContentDescription("Warning Icon").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_refreshIconDisplayed() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()
    }
    // endregion

    // region State Transition Tests
    @Test
    fun searchLoadingState_transitionFromNoWarningToWarning() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // Then - 초기 상태: 경고 없음
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertDoesNotExist()

        // When - 5초 경과
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then - 경고 표시됨
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_loadingElementsRemainAfterWarning() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When - 5초 대기
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then - 로딩 요소들이 여전히 표시됨
        composeTestRule.onNodeWithText("🐻").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading ...").assertIsDisplayed()
        // 경고도 표시됨
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
    }
    // endregion

    // region Timing Edge Cases
    @Test
    fun searchLoadingState_warningShownAt5000ms() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When - 정확히 5000ms
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then - 경고 메시지가 표시됨
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_warningShownAt5001ms() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When - 5001ms
        composeTestRule.mainClock.advanceTimeBy(5001)
        composeTestRule.waitForIdle()

        // Then
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
    }
    // endregion

    // region Multiple Component States
    @Test
    fun searchLoadingState_allElementsBeforeWarning() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // Then - 경고 전에는 로딩 요소만
        composeTestRule.onNodeWithText("🐻").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading ...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Action").assertDoesNotExist()
    }

    @Test
    fun searchLoadingState_allElementsAfterWarning() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then - 경고 후에는 모든 요소 표시
        composeTestRule.onNodeWithText("🐻").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading ...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please refresh the page.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Warning Icon").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Action").assertIsDisplayed()
    }
    // endregion

    // region Callback Tests
    @Test
    fun searchLoadingState_onRefreshNotCalledBeforeClick() {
        // Given
        var refreshCalled = false
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = { refreshCalled = true })
        }

        // When - 5초 대기만 하고 클릭 안함
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then
        assertEquals(false, refreshCalled)
    }

    @Test
    fun searchLoadingState_onRefreshCalledAfterClick() {
        // Given
        var refreshCalled = false
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = { refreshCalled = true })
        }

        // When
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Action").performClick()

        // Then
        assertTrue(refreshCalled)
    }
    // endregion

    // region Warning Banner Content Tests
    @Test
    fun searchLoadingState_warningTitleCorrect() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_warningMessageCorrect() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then
        composeTestRule.onNodeWithText("Please refresh the page.").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_warningBannerHasBothTitleAndMessage() {
        // Given
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then - 제목과 메시지 둘 다 표시
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please refresh the page.").assertIsDisplayed()
    }
    // endregion

    // region Integration Tests
    @Test
    fun searchLoadingState_completeUserFlow() {
        // Given
        var refreshCount = 0
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = { refreshCount++ })
        }

        // Step 1: 초기 로딩 상태 확인
        composeTestRule.onNodeWithText("🐻").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading ...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertDoesNotExist()

        // Step 2: 3초 대기 - 아직 경고 없음
        composeTestRule.mainClock.advanceTimeBy(3000)
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertDoesNotExist()

        // Step 3: 추가로 2초 더 대기 (총 5초) - 경고 표시
        composeTestRule.mainClock.advanceTimeBy(2000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()

        // Step 4: 새로고침 버튼 클릭
        composeTestRule.onNodeWithContentDescription("Action").performClick()
        assertEquals(1, refreshCount)

        // Step 5: 모든 요소가 여전히 표시됨
        composeTestRule.onNodeWithText("🐻").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading ...").assertIsDisplayed()
    }

    @Test
    fun searchLoadingState_longWaitScenario() {
        // Given - 매우 오래 대기하는 시나리오
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = {})
        }

        // When - 30초 대기
        composeTestRule.mainClock.advanceTimeBy(30000)
        composeTestRule.waitForIdle()

        // Then - 모든 요소가 표시됨
        composeTestRule.onNodeWithText("🐻").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading ...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Please refresh the page.").assertIsDisplayed()
    }
    // endregion

    // region LaunchedEffect Tests
    @Test
    fun searchLoadingState_launchedEffectTriggersOnce() {
        // Given
        var refreshCallCount = 0
        composeTestRule.setContent {
            SearchLoadingStateCustom(onRefresh = { refreshCallCount++ })
        }

        // When - 5초 대기하고 경고 표시됨
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()

        // Then - 경고가 한 번만 표시됨 (LaunchedEffect가 한 번만 실행)
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()

        // 추가 대기해도 경고는 계속 표시됨
        composeTestRule.mainClock.advanceTimeBy(5000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Loading is taking longer than usual.").assertIsDisplayed()
    }
    // endregion
}
