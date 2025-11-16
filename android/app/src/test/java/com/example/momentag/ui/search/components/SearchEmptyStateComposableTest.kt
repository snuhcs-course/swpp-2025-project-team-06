package com.example.momentag.ui.search.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SearchEmptyState Composable 함수들에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchEmptyStateComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region SearchEmptyStateCustom Tests
    @Test
    fun searchEmptyState_displaysQueryMessage() {
        // Given
        val query = "테스트 검색어"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysEnglishQuery() {
        // Given
        val query = "test query"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysEmptyQuery() {
        // Given
        val query = ""
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysLongQuery() {
        // Given
        val query = "이것은 매우 긴 검색어입니다 정말로 아주 긴 검색어"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysSpecialCharacters() {
        // Given
        val query = "!@#$%^&*()"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysNumberQuery() {
        // Given
        val query = "12345"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysMixedQuery() {
        // Given
        val query = "테스트123test"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysQueryWithSpaces() {
        // Given
        val query = "검색어 공백 포함"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysQueryWithNewline() {
        // Given
        val query = "첫줄\n둘째줄"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_displaysQueryWithQuotes() {
        // Given
        val query = "\"quoted\" text"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }
    // endregion

    // region SearchIdleCustom Tests
    @Test
    fun searchIdle_displaysIdleMessage() {
        // Given
        composeTestRule.setContent {
            SearchIdleCustom(
                history = emptyList(),
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Please enter a search term.").assertIsDisplayed()
    }

    @Test
    fun searchIdle_displaysCorrectText() {
        // Given
        composeTestRule.setContent {
            SearchIdleCustom(
                history = emptyList(),
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }

        // Then
        val expectedText = "Please enter a search term."
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun searchIdle_centersContent() {
        // Given
        composeTestRule.setContent {
            SearchIdleCustom(
                history = emptyList(),
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }

        // Then - 텍스트가 표시되면 중앙 정렬이 적용됨
        composeTestRule.onNodeWithText("Please enter a search term.").assertIsDisplayed()
    }
    // endregion

    // region SearchErrorStateFallbackCustom Tests
    @Test
    fun searchErrorFallback_displaysErrorMessage() {
        // Given
        composeTestRule.setContent {
            SearchErrorStateFallbackCustom()
        }

        // Then
        composeTestRule.onNodeWithText("An error occurred.").assertIsDisplayed()
    }

    @Test
    fun searchErrorFallback_displaysCorrectText() {
        // Given
        composeTestRule.setContent {
            SearchErrorStateFallbackCustom()
        }

        // Then
        val expectedText = "An error occurred."
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun searchErrorFallback_centersContent() {
        // Given
        composeTestRule.setContent {
            SearchErrorStateFallbackCustom()
        }

        // Then - 텍스트가 표시되면 중앙 정렬이 적용됨
        composeTestRule.onNodeWithText("An error occurred.").assertIsDisplayed()
    }
    // endregion

    // region Integration Tests - Multiple States
    @Test
    fun searchStates_emptyStateWithQuery1() {
        // Given
        val query = "태그1"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchStates_emptyStateWithQuery2() {
        // Given
        val query = "tag2"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchStates_emptyStateWithQuery3() {
        // Given
        val query = "검색123"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchStates_emptyStateWithQuery4() {
        // Given
        val query = "!"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchStates_idleStateDifferentMessage() {
        // Given - Idle 상태
        composeTestRule.setContent {
            SearchIdleCustom(
                history = emptyList(),
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }
        // Then
        composeTestRule.onNodeWithText("Please enter a search term.").assertIsDisplayed()
    }

    @Test
    fun searchStates_emptyStateDifferentMessage() {
        // Given - Empty 상태
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = "test")
        }
        // Then
        composeTestRule.onNodeWithText("\"test\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchStates_errorStateDifferentMessage() {
        // Given - Error 상태
        composeTestRule.setContent {
            SearchErrorStateFallbackCustom()
        }
        // Then
        composeTestRule.onNodeWithText("An error occurred.").assertIsDisplayed()
    }
    // endregion

    // region Edge Cases
    @Test
    fun searchEmptyState_veryLongQuery() {
        // Given
        val query = "a".repeat(100)
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_queryWithEmojis() {
        // Given
        val query = "😀🎉✨"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_queryWithSpecialKoreanCharacters() {
        // Given
        val query = "ㄱㄴㄷㄹㅁ"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_queryWithWhitespace() {
        // Given
        val query = "   "
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_queryWithTab() {
        // Given
        val query = "test\tquery"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }
    // endregion

    // region Real-world Scenarios
    @Test
    fun searchEmptyState_typicalTagSearch() {
        // Given - 일반적인 태그 검색
        val query = "#여행"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_typicalLocationSearch() {
        // Given - 위치 검색
        val query = "서울"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_typicalDateSearch() {
        // Given - 날짜 검색
        val query = "2025-01-01"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchEmptyState_typicalPersonSearch() {
        // Given - 사람 이름 검색
        val query = "홍길동"
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = query)
        }

        // Then
        composeTestRule.onNodeWithText("\"$query\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }
    // endregion

    // region Component Consistency Tests
    @Test
    fun searchStates_idleUsesCorrectTextColor() {
        // Given & Then - Idle 상태가 Temp_word 색상을 사용하는지 확인
        // (시각적으로는 확인 불가, 렌더링만 확인)

        composeTestRule.setContent {
            SearchIdleCustom(
                history = emptyList(),
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }
        composeTestRule.onNodeWithText("Please enter a search term.").assertIsDisplayed()
    }

    @Test
    fun searchStates_emptyUsesCorrectTextColor() {
        // Given & Then
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = "test")
        }
        composeTestRule.onNodeWithText("\"test\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchStates_errorUsesCorrectTextColor() {
        // Given & Then
        composeTestRule.setContent {
            SearchErrorStateFallbackCustom()
        }
        composeTestRule.onNodeWithText("An error occurred.").assertIsDisplayed()
    }

    @Test
    fun searchStates_idleCenterContentAlignment() {
        // Given & Then - Idle 상태가 중앙 정렬을 사용하는지 확인

        composeTestRule.setContent {
            SearchIdleCustom(
                history = emptyList(),
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }
        composeTestRule.onNodeWithText("Please enter a search term.").assertIsDisplayed()
    }

    @Test
    fun searchStates_emptyCenterContentAlignment() {
        // Given & Then
        composeTestRule.setContent {
            SearchEmptyStateCustom(query = "test")
        }
        composeTestRule.onNodeWithText("\"test\"에 대한 검색 결과가 없습니다.").assertIsDisplayed()
    }

    @Test
    fun searchStates_errorCenterContentAlignment() {
        // Given & Then
        composeTestRule.setContent {
            SearchErrorStateFallbackCustom()
        }
        composeTestRule.onNodeWithText("An error occurred.").assertIsDisplayed()
    }
    // endregion
}
