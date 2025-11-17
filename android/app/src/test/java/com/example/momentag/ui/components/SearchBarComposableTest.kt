package com.example.momentag.ui.components

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import com.example.momentag.model.TagItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ChipSearchBar Composable 함수에 대한 UI 테스트
 * Robolectric을 사용하여 Unit Test에서 Composable의 Line Coverage를 포함시킴
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SearchBarComposableTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // region ChipSearchBar Tests
    @Test
    fun chipSearchBar_displaysDefaultPlaceholder() {
        // Given
        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val contentItems =
                listOf(
                    SearchContentElement.Text(id = "text-0", text = "\u200B"),
                )
            val textStates = mapOf("text-0" to TextFieldValue("\u200B"))
            val focusRequesters = mapOf("text-0" to FocusRequester())
            val bringIntoViewRequesters =
                mapOf(
                    "text-0" to
                        androidx.compose.foundation.relocation
                            .BringIntoViewRequester(),
                )

            ChipSearchBar(
                listState = listState,
                isFocused = false,
                hideCursor = false,
                contentItems = contentItems,
                textStates = textStates,
                focusRequesters = focusRequesters,
                bringIntoViewRequesters = bringIntoViewRequesters,
                onContainerClick = {},
                onChipClick = {},
                onTextChange = { _, _ -> },
                onFocus = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("검색 또는 #태그 입력").assertIsDisplayed()
    }

    @Test
    fun chipSearchBar_displaysCustomPlaceholder() {
        // Given
        val customPlaceholder = "Search for tags..."
        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val contentItems =
                listOf(
                    SearchContentElement.Text(id = "text-0", text = "\u200B"),
                )
            val textStates = mapOf("text-0" to TextFieldValue("\u200B"))
            val focusRequesters = mapOf("text-0" to FocusRequester())
            val bringIntoViewRequesters =
                mapOf(
                    "text-0" to
                        androidx.compose.foundation.relocation
                            .BringIntoViewRequester(),
                )

            ChipSearchBar(
                listState = listState,
                isFocused = false,
                hideCursor = false,
                contentItems = contentItems,
                textStates = textStates,
                focusRequesters = focusRequesters,
                bringIntoViewRequesters = bringIntoViewRequesters,
                onContainerClick = {},
                onChipClick = {},
                onTextChange = { _, _ -> },
                onFocus = {},
                onSearch = {},
                placeholder = customPlaceholder,
            )
        }

        // Then
        composeTestRule.onNodeWithText(customPlaceholder).assertIsDisplayed()
    }

    @Test
    fun chipSearchBar_displaysSearchIcon() {
        // Given
        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val contentItems =
                listOf(
                    SearchContentElement.Text(id = "text-0", text = "\u200B"),
                )
            val textStates = mapOf("text-0" to TextFieldValue("\u200B"))
            val focusRequesters = mapOf("text-0" to FocusRequester())
            val bringIntoViewRequesters =
                mapOf(
                    "text-0" to
                        androidx.compose.foundation.relocation
                            .BringIntoViewRequester(),
                )

            ChipSearchBar(
                listState = listState,
                isFocused = false,
                hideCursor = false,
                contentItems = contentItems,
                textStates = textStates,
                focusRequesters = focusRequesters,
                bringIntoViewRequesters = bringIntoViewRequesters,
                onContainerClick = {},
                onChipClick = {},
                onTextChange = { _, _ -> },
                onFocus = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun chipSearchBar_displaysTextContent() {
        // Given
        val testText = "test query"
        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val contentItems =
                listOf(
                    SearchContentElement.Text(id = "text-0", text = testText),
                )
            val textStates = mapOf("text-0" to TextFieldValue(testText))
            val focusRequesters = mapOf("text-0" to FocusRequester())
            val bringIntoViewRequesters =
                mapOf(
                    "text-0" to
                        androidx.compose.foundation.relocation
                            .BringIntoViewRequester(),
                )

            ChipSearchBar(
                listState = listState,
                isFocused = true,
                hideCursor = false,
                contentItems = contentItems,
                textStates = textStates,
                focusRequesters = focusRequesters,
                bringIntoViewRequesters = bringIntoViewRequesters,
                onContainerClick = {},
                onChipClick = {},
                onTextChange = { _, _ -> },
                onFocus = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(testText).assertIsDisplayed()
    }

    @Test
    fun chipSearchBar_displaysChip() {
        // Given
        val tag =
            TagItem(
                tagName = "test",
                coverImageId = null,
                tagId = "1",
                createdAt = null,
                updatedAt = null,
                photoCount = 5,
            )
        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val contentItems =
                listOf(
                    SearchContentElement.Chip(id = "chip-0", tag = tag),
                    SearchContentElement.Text(id = "text-0", text = "\u200B"),
                )
            val textStates = mapOf("text-0" to TextFieldValue("\u200B"))
            val focusRequesters = mapOf("text-0" to FocusRequester())
            val bringIntoViewRequesters =
                mapOf(
                    "text-0" to
                        androidx.compose.foundation.relocation
                            .BringIntoViewRequester(),
                )

            ChipSearchBar(
                listState = listState,
                isFocused = true,
                hideCursor = false,
                contentItems = contentItems,
                textStates = textStates,
                focusRequesters = focusRequesters,
                bringIntoViewRequesters = bringIntoViewRequesters,
                onContainerClick = {},
                onChipClick = {},
                onTextChange = { _, _ -> },
                onFocus = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("#test").assertIsDisplayed()
    }

    @Test
    fun chipSearchBar_displaysMultipleElements() {
        // Given
        val tag1 =
            TagItem(
                tagName = "tag1",
                coverImageId = null,
                tagId = "1",
                createdAt = null,
                updatedAt = null,
                photoCount = 5,
            )
        val tag2 =
            TagItem(
                tagName = "tag2",
                coverImageId = null,
                tagId = "2",
                createdAt = null,
                updatedAt = null,
                photoCount = 3,
            )
        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val contentItems =
                listOf(
                    SearchContentElement.Chip(id = "chip-0", tag = tag1),
                    SearchContentElement.Text(id = "text-0", text = "some text"),
                    SearchContentElement.Chip(id = "chip-1", tag = tag2),
                    SearchContentElement.Text(id = "text-1", text = "\u200B"),
                )
            val textStates =
                mapOf(
                    "text-0" to TextFieldValue("some text"),
                    "text-1" to TextFieldValue("\u200B"),
                )
            val focusRequesters =
                mapOf(
                    "text-0" to FocusRequester(),
                    "text-1" to FocusRequester(),
                )
            val bringIntoViewRequesters =
                mapOf(
                    "text-0" to
                        androidx.compose.foundation.relocation
                            .BringIntoViewRequester(),
                    "text-1" to
                        androidx.compose.foundation.relocation
                            .BringIntoViewRequester(),
                )

            ChipSearchBar(
                listState = listState,
                isFocused = true,
                hideCursor = false,
                contentItems = contentItems,
                textStates = textStates,
                focusRequesters = focusRequesters,
                bringIntoViewRequesters = bringIntoViewRequesters,
                onContainerClick = {},
                onChipClick = {},
                onTextChange = { _, _ -> },
                onFocus = {},
                onSearch = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("#tag1").assertIsDisplayed()
        composeTestRule.onNodeWithText("some text").assertIsDisplayed()
        composeTestRule.onNodeWithText("#tag2").assertIsDisplayed()
    }

    @Test
    fun chipSearchBar_onContainerClick_triggersCallback() {
        // Given
        var containerClicked = false
        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val contentItems =
                listOf(
                    SearchContentElement.Text(id = "text-0", text = "\u200B"),
                )
            val textStates = mapOf("text-0" to TextFieldValue("\u200B"))
            val focusRequesters = mapOf("text-0" to FocusRequester())
            val bringIntoViewRequesters =
                mapOf(
                    "text-0" to
                        androidx.compose.foundation.relocation
                            .BringIntoViewRequester(),
                )

            ChipSearchBar(
                listState = listState,
                isFocused = false,
                hideCursor = false,
                contentItems = contentItems,
                textStates = textStates,
                focusRequesters = focusRequesters,
                bringIntoViewRequesters = bringIntoViewRequesters,
                onContainerClick = { containerClicked = true },
                onChipClick = {},
                onTextChange = { _, _ -> },
                onFocus = {},
                onSearch = {},
            )
        }

        // When
        composeTestRule.onNodeWithContentDescription("Search").performClick()

        // Then
        assertEquals(true, containerClicked)
    }
    // endregion

    // region SearchHistoryItem Tests
    @Test
    fun searchHistoryItem_displaysTextQuery() {
        // Given
        val query = "test search"
        val parser: (String, List<TagItem>) -> List<SearchContentElement> = { q, _ ->
            listOf(SearchContentElement.Text(id = "text-0", text = q))
        }

        composeTestRule.setContent {
            SearchHistoryItem(
                query = query,
                allTags = emptyList(),
                parser = parser,
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText(query).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("History").assertIsDisplayed()
    }

    @Test
    fun searchHistoryItem_displaysChipQuery() {
        // Given
        val tag =
            TagItem(
                tagName = "vacation",
                coverImageId = null,
                tagId = "1",
                createdAt = null,
                updatedAt = null,
                photoCount = 10,
            )
        val query = "#vacation"
        val parser: (String, List<TagItem>) -> List<SearchContentElement> = { _, _ ->
            listOf(SearchContentElement.Chip(id = "chip-0", tag = tag))
        }

        composeTestRule.setContent {
            SearchHistoryItem(
                query = query,
                allTags = listOf(tag),
                parser = parser,
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("#vacation").assertIsDisplayed()
    }

    @Test
    fun searchHistoryItem_displaysDeleteButton() {
        // Given
        val query = "test"
        val parser: (String, List<TagItem>) -> List<SearchContentElement> = { q, _ ->
            listOf(SearchContentElement.Text(id = "text-0", text = q))
        }

        composeTestRule.setContent {
            SearchHistoryItem(
                query = query,
                allTags = emptyList(),
                parser = parser,
                onHistoryClick = {},
                onHistoryDelete = {},
            )
        }

        // Then
        composeTestRule.onNodeWithContentDescription("Delete history item").assertIsDisplayed()
    }
    // endregion

    // region SuggestionChip Tests
    @Test
    fun suggestionChip_displaysTagName() {
        // Given
        val tag =
            TagItem(
                tagName = "summer",
                coverImageId = null,
                tagId = "1",
                createdAt = null,
                updatedAt = null,
                photoCount = 5,
            )
        composeTestRule.setContent {
            SuggestionChip(
                tag = tag,
                onClick = {},
            )
        }

        // Then
        composeTestRule.onNodeWithText("#summer").assertIsDisplayed()
    }

    @Test
    fun suggestionChip_triggersOnClick() {
        // Given
        var clicked = false
        val tag =
            TagItem(
                tagName = "test",
                coverImageId = null,
                tagId = "1",
                createdAt = null,
                updatedAt = null,
                photoCount = 1,
            )
        composeTestRule.setContent {
            SuggestionChip(
                tag = tag,
                onClick = { clicked = true },
            )
        }

        // When
        composeTestRule.onNodeWithText("#test").performClick()

        // Then
        assertEquals(true, clicked)
    }
    // endregion

    // region SearchContentElement Tests
    @Test
    fun searchContentElement_text_hasCorrectProperties() {
        // Given
        val element = SearchContentElement.Text(id = "text-1", text = "sample")

        // Then
        assertEquals("text-1", element.id)
        assertEquals("sample", element.text)
    }

    @Test
    fun searchContentElement_chip_hasCorrectProperties() {
        // Given
        val tag =
            TagItem(
                tagName = "test",
                coverImageId = null,
                tagId = "1",
                createdAt = null,
                updatedAt = null,
                photoCount = 5,
            )
        val element = SearchContentElement.Chip(id = "chip-1", tag = tag)

        // Then
        assertEquals("chip-1", element.id)
        assertEquals(tag, element.tag)
    }
    // endregion
}
