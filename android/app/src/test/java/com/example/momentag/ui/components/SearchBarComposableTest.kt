package com.example.momentag.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SearchBar 및 SearchBarControlledCustom Composable 함수에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchBarComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region SearchBar (Internal State) Tests
    @Test
    fun searchBar_displaysDefaultPlaceholder() {
        // Given
        composeTestRule.setContent {
            SearchBar(onSearch = {})
        }

        // Then
        composeTestRule.onNodeWithText("Search Anything...").assertIsDisplayed()
    }

    @Test
    fun searchBar_displaysCustomPlaceholder() {
        // Given
        val customPlaceholder = "Search for tags..."
        composeTestRule.setContent {
            SearchBar(
                onSearch = {},
                placeholder = customPlaceholder,
            )
        }

        // Then
        composeTestRule.onNodeWithText(customPlaceholder).assertIsDisplayed()
    }

    @Test
    fun searchBar_displaysSearchIcon() {
        // Given
        composeTestRule.setContent {
            SearchBar(onSearch = {})
        }

        // Then
        composeTestRule.onNodeWithContentDescription("검색 실행").assertIsDisplayed()
    }

    @Test
    fun searchBar_canInputText() {
        // Given
        composeTestRule.setContent {
            SearchBar(onSearch = {})
        }

        // When
        composeTestRule.onNodeWithText("Search Anything...").performTextInput("test query")

        // Then
        composeTestRule.onNodeWithText("test query").assertIsDisplayed()
    }

    @Test
    fun searchBar_searchIcon_triggersCallback() {
        // Given
        var searchQuery = ""
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQuery = it })
        }

        // When - 텍스트 입력 후 검색 아이콘 클릭
        composeTestRule.onNodeWithText("Search Anything...").performTextInput("my search")
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertEquals("my search", searchQuery)
    }

    @Test
    fun searchBar_imeAction_triggersCallback() {
        // Given
        var searchQuery = ""
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQuery = it })
        }

        // When - 텍스트 입력 후 IME 액션 (키보드 검색 버튼)
        composeTestRule.onNodeWithText("Search Anything...").performTextInput("keyboard search")
        composeTestRule.onNodeWithText("keyboard search").performImeAction()

        // Then
        assertEquals("keyboard search", searchQuery)
    }

    @Test
    fun searchBar_emptySearch_sendsEmptyString() {
        // Given
        var searchQuery = "not empty"
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQuery = it })
        }

        // When - 아무것도 입력하지 않고 검색
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertEquals("", searchQuery)
    }

    @Test
    fun searchBar_multipleSearches_updatesQuery() {
        // Given
        val searchQueries = mutableListOf<String>()
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQueries.add(it) })
        }

        // When - 여러 번 검색
        composeTestRule.onNodeWithText("Search Anything...").performTextInput("first")
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        composeTestRule.onNodeWithText("first").performTextInput(" second")
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertEquals(2, searchQueries.size)
        assertEquals("first", searchQueries[0])
        assertEquals("first second", searchQueries[1])
    }

    @Test
    fun searchBar_longText_canBeEntered() {
        // Given
        val longText = "This is a very long search query with many words"
        var searchQuery = ""
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQuery = it })
        }

        // When
        composeTestRule.onNodeWithText("Search Anything...").performTextInput(longText)
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertEquals(longText, searchQuery)
    }

    @Test
    fun searchBar_specialCharacters_canBeEntered() {
        // Given
        val specialText = "test@#$%^&*()"
        var searchQuery = ""
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQuery = it })
        }

        // When
        composeTestRule.onNodeWithText("Search Anything...").performTextInput(specialText)
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertEquals(specialText, searchQuery)
    }

    @Test
    fun searchBar_koreanText_canBeEntered() {
        // Given
        val koreanText = "한글 검색"
        var searchQuery = ""
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQuery = it })
        }

        // When
        composeTestRule.onNodeWithText("Search Anything...").performTextInput(koreanText)
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertEquals(koreanText, searchQuery)
    }
    // endregion

    // region SearchBarControlledCustom (External State) Tests
    @Test
    fun searchBarControlled_displaysDefaultPlaceholder() {
        // Given
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "",
                onValueChange = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Search Anything...").assertIsDisplayed()
    }

    @Test
    fun searchBarControlled_displaysCustomPlaceholder() {
        // Given
        val customPlaceholder = "Type to search..."
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "",
                onValueChange = {},
                onSearch = {},
                placeholder = customPlaceholder,
            )
        }

        // Then
        composeTestRule.onNodeWithText(customPlaceholder).assertIsDisplayed()
    }

    @Test
    fun searchBarControlled_displaysSearchIcon() {
        // Given
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "",
                onValueChange = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("검색 실행").assertIsDisplayed()
    }

    @Test
    fun searchBarControlled_displaysProvidedValue() {
        // Given
        val initialValue = "existing text"
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = initialValue,
                onValueChange = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(initialValue).assertIsDisplayed()
    }

    @Test
    fun searchBarControlled_textChange_triggersCallback() {
        // Given
        var currentValue = ""
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = currentValue,
                onValueChange = { currentValue = it },
                onSearch = {},
            )
        }

        // When
        composeTestRule.onNodeWithText("Search Anything...").performTextInput("new text")

        // Then
        assertEquals("new text", currentValue)
    }

    @Test
    fun searchBarControlled_searchIcon_triggersCallback() {
        // Given
        var searchCalled = false
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "test query",
                onValueChange = {},
                onSearch = { searchCalled = true },
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertTrue(searchCalled)
    }

    @Test
    fun searchBarControlled_imeAction_triggersCallback() {
        // Given
        var searchCalled = false
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "test",
                onValueChange = {},
                onSearch = { searchCalled = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("test").performImeAction()

        // Then
        assertTrue(searchCalled)
    }

    @Test
    fun searchBarControlled_emptyValue_displaysPlaceholder() {
        // Given
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "",
                onValueChange = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("Search Anything...").assertIsDisplayed()
    }

    @Test
    fun searchBarControlled_multipleSearches_callsOnSearch() {
        // Given
        var searchCount = 0
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "query",
                onValueChange = {},
                onSearch = { searchCount++ },
            )
        }

        // When - 여러 번 검색
        repeat(3) {
            composeTestRule.onNodeWithContentDescription("검색 실행").performClick()
        }

        // Then
        assertEquals(3, searchCount)
    }

    @Test
    fun searchBarControlled_onValueChange_callsCallback() {
        // Given
        var callbackCalled = false
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "",
                onValueChange = { callbackCalled = true },
                onSearch = {},
            )
        }

        // When - 텍스트 입력
        composeTestRule.onNodeWithText("Search Anything...").performTextInput("test")

        // Then - onValueChange 콜백이 호출됨
        assertTrue(callbackCalled)
    }

    @Test
    fun searchBarControlled_longText_canBeDisplayed() {
        // Given
        val longText = "This is a very long text value that should be displayed"
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = longText,
                onValueChange = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(longText).assertIsDisplayed()
    }
    // endregion

    // region Comparison Tests
    @Test
    fun bothSearchBars_displaySearchIcon() {
        // SearchBar
        composeTestRule.setContent {
            SearchBar(onSearch = {})
        }
        composeTestRule.onNodeWithContentDescription("검색 실행").assertIsDisplayed()
    }

    @Test
    fun bothSearchBars_controlled_displaySearchIcon() {
        // SearchBarControlledCustom
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = "",
                onValueChange = {},
                onSearch = {},
            )
        }
        composeTestRule.onNodeWithContentDescription("검색 실행").assertIsDisplayed()
    }
    // endregion

    // region Edge Cases
    @Test
    fun searchBar_whitespacesOnly_canBeSearched() {
        // Given
        var searchQuery = ""
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQuery = it })
        }

        // When
        composeTestRule.onNodeWithText("Search Anything...").performTextInput("   ")
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertEquals("   ", searchQuery)
    }

    @Test
    fun searchBarControlled_whitespacesOnly_displayedCorrectly() {
        // Given
        val whitespaceValue = "   "
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = whitespaceValue,
                onValueChange = {},
                onSearch = {},
            )
        }

        // Then - 공백도 올바르게 표시됨
        composeTestRule.onNodeWithText(whitespaceValue).assertIsDisplayed()
    }

    @Test
    fun searchBar_numbersAndSymbols_canBeEntered() {
        // Given
        val mixedText = "123!@# ABC xyz"
        var searchQuery = ""
        composeTestRule.setContent {
            SearchBar(onSearch = { searchQuery = it })
        }

        // When
        composeTestRule.onNodeWithText("Search Anything...").performTextInput(mixedText)
        composeTestRule.onNodeWithContentDescription("검색 실행").performClick()

        // Then
        assertEquals(mixedText, searchQuery)
    }

    @Test
    fun searchBarControlled_numbersAndSymbols_displayed() {
        // Given
        val mixedText = "456$%^ DEF"
        composeTestRule.setContent {
            SearchBarControlledCustom(
                value = mixedText,
                onValueChange = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(mixedText).assertIsDisplayed()
    }
    // endregion
}
