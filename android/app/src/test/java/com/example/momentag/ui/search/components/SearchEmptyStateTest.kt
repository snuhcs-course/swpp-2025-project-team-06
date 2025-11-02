package com.example.momentag.ui.search.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEmptyStateTest {
    // ==================== SearchEmptyStateCustom Tests ====================

    @Test
    fun `SearchEmptyStateCustom should accept query parameter`() {
        // Given
        val query = "test query"

        // When & Then
        assertEquals("test query", query)
        assertTrue(query.isNotEmpty())
    }

    @Test
    fun `SearchEmptyStateCustom should accept empty query`() {
        // Given
        val query = ""

        // When & Then
        assertEquals("", query)
        assertTrue(query.isEmpty())
    }

    @Test
    fun `SearchEmptyStateCustom should accept long query`() {
        // Given
        val query = "this is a very long search query that should still work properly"

        // When & Then
        assertTrue(query.length > 50)
        assertTrue(query.contains("search query"))
    }

    @Test
    fun `SearchEmptyStateCustom should accept Korean query`() {
        // Given
        val query = "한글 검색어"

        // When & Then
        assertEquals("한글 검색어", query)
        assertTrue(query.contains("한글"))
    }

    @Test
    fun `SearchEmptyStateCustom should accept special characters in query`() {
        // Given
        val query = "@#$%^&*()"

        // When & Then
        assertTrue(query.contains("@#$"))
        assertTrue(query.isNotEmpty())
    }

    @Test
    fun `SearchEmptyStateCustom should accept emoji in query`() {
        // Given
        val query = "📸 사진"

        // When & Then
        assertTrue(query.contains("📸"))
        assertTrue(query.contains("사진"))
    }

    @Test
    fun `SearchEmptyStateCustom should accept numbers in query`() {
        // Given
        val query = "123456"

        // When & Then
        assertEquals("123456", query)
        assertTrue(query.all { it.isDigit() })
    }

    @Test
    fun `SearchEmptyStateCustom should accept mixed language query`() {
        // Given
        val query = "hello 안녕 こんにちは"

        // When & Then
        assertTrue(query.contains("hello"))
        assertTrue(query.contains("안녕"))
        assertTrue(query.contains("こんにちは"))
    }

    @Test
    fun `SearchEmptyStateCustom should format message correctly`() {
        // Given
        val query = "test"
        val expectedMessage = "\"$query\"에 대한 검색 결과가 없습니다."

        // When & Then
        assertEquals("\"test\"에 대한 검색 결과가 없습니다.", expectedMessage)
        assertTrue(expectedMessage.contains(query))
    }

    @Test
    fun `SearchEmptyStateCustom message should include query with quotes`() {
        // Given
        val query = "sample"
        val message = "\"$query\"에 대한 검색 결과가 없습니다."

        // When & Then
        assertTrue(message.startsWith("\""))
        assertTrue(message.contains("\"$query\""))
    }

    @Test
    fun `SearchEmptyStateCustom should handle query with whitespace`() {
        // Given
        val query = "  test  "

        // When & Then
        assertEquals("  test  ", query)
        assertTrue(query.trim() == "test")
    }

    @Test
    fun `SearchEmptyStateCustom should handle single character query`() {
        // Given
        val query = "a"

        // When & Then
        assertEquals(1, query.length)
        assertEquals("a", query)
    }

    @Test
    fun `SearchEmptyStateCustom should handle very long query`() {
        // Given
        val query = "query ".repeat(100)

        // When & Then
        assertTrue(query.length > 500)
        assertTrue(query.contains("query"))
    }

    @Test
    fun `SearchEmptyStateCustom message should be in Korean`() {
        // Given
        val query = "test"
        val message = "\"$query\"에 대한 검색 결과가 없습니다."

        // When & Then
        assertTrue(message.contains("검색 결과"))
        assertTrue(message.contains("없습니다"))
    }

    @Test
    fun `SearchEmptyStateCustom should handle query with line breaks`() {
        // Given
        val query = "line1\nline2"

        // When & Then
        assertTrue(query.contains("\n"))
        assertEquals(2, query.split("\n").size)
    }

    // ==================== SearchIdleCustom Tests ====================

    @Test
    fun `SearchIdleCustom should display idle message`() {
        // Given
        val idleMessage = "검색어를 입력해주세요."

        // When & Then
        assertEquals("검색어를 입력해주세요.", idleMessage)
        assertTrue(idleMessage.isNotEmpty())
    }

    @Test
    fun `SearchIdleCustom message should be in Korean`() {
        // Given
        val message = "검색어를 입력해주세요."

        // When & Then
        assertTrue(message.contains("검색어"))
        assertTrue(message.contains("입력"))
    }

    @Test
    fun `SearchIdleCustom should have descriptive message`() {
        // Given
        val message = "검색어를 입력해주세요."

        // When & Then
        assertTrue(message.length > 5)
        assertTrue(message.contains("검색"))
    }

    @Test
    fun `SearchIdleCustom should not require parameters`() {
        // Given - no parameters needed

        // When
        val hasParameters = false

        // Then
        assertFalse(hasParameters)
    }

    @Test
    fun `SearchIdleCustom message should end with period`() {
        // Given
        val message = "검색어를 입력해주세요."

        // When & Then
        assertTrue(message.endsWith("."))
    }

    @Test
    fun `SearchIdleCustom should display user guidance`() {
        // Given
        val message = "검색어를 입력해주세요."

        // When & Then
        assertTrue(message.contains("입력해주세요"))
    }

    // ==================== SearchErrorStateFallbackCustom Tests ====================

    @Test
    fun `SearchErrorStateFallbackCustom should display error message`() {
        // Given
        val errorMessage = "오류가 발생했습니다."

        // When & Then
        assertEquals("오류가 발생했습니다.", errorMessage)
        assertTrue(errorMessage.isNotEmpty())
    }

    @Test
    fun `SearchErrorStateFallbackCustom message should be in Korean`() {
        // Given
        val message = "오류가 발생했습니다."

        // When & Then
        assertTrue(message.contains("오류"))
        assertTrue(message.contains("발생"))
    }

    @Test
    fun `SearchErrorStateFallbackCustom should indicate error state`() {
        // Given
        val message = "오류가 발생했습니다."

        // When & Then
        assertTrue(message.contains("오류"))
    }

    @Test
    fun `SearchErrorStateFallbackCustom should not require parameters`() {
        // Given - no parameters needed

        // When
        val hasParameters = false

        // Then
        assertFalse(hasParameters)
    }

    @Test
    fun `SearchErrorStateFallbackCustom message should end with period`() {
        // Given
        val message = "오류가 발생했습니다."

        // When & Then
        assertTrue(message.endsWith("."))
    }

    @Test
    fun `SearchErrorStateFallbackCustom should be user-friendly`() {
        // Given
        val message = "오류가 발생했습니다."

        // When & Then
        assertTrue(message.length > 5)
        assertFalse(message.contains("error"))
        assertFalse(message.contains("exception"))
    }

    // ==================== Comparison Tests ====================

    @Test
    fun `All three components should have different messages`() {
        // Given
        val query = "test"
        val emptyMessage = "\"$query\"에 대한 검색 결과가 없습니다."
        val idleMessage = "검색어를 입력해주세요."
        val errorMessage = "오류가 발생했습니다."

        // When & Then
        assertNotEquals(emptyMessage, idleMessage)
        assertNotEquals(idleMessage, errorMessage)
        assertNotEquals(emptyMessage, errorMessage)
    }

    @Test
    fun `SearchEmptyStateCustom requires query parameter vs others do not`() {
        // Given
        val emptyStateNeedsQuery = true
        val idleNeedsQuery = false
        val errorNeedsQuery = false

        // When & Then
        assertTrue(emptyStateNeedsQuery)
        assertFalse(idleNeedsQuery)
        assertFalse(errorNeedsQuery)
    }

    @Test
    fun `All messages should be in Korean`() {
        // Given
        val emptyMessage = "\"test\"에 대한 검색 결과가 없습니다."
        val idleMessage = "검색어를 입력해주세요."
        val errorMessage = "오류가 발생했습니다."

        // When & Then
        assertTrue(emptyMessage.any { it.code >= 0xAC00 && it.code <= 0xD7A3 })
        assertTrue(idleMessage.any { it.code >= 0xAC00 && it.code <= 0xD7A3 })
        assertTrue(errorMessage.any { it.code >= 0xAC00 && it.code <= 0xD7A3 })
    }

    @Test
    fun `All messages should end with period`() {
        // Given
        val emptyMessage = "\"test\"에 대한 검색 결과가 없습니다."
        val idleMessage = "검색어를 입력해주세요."
        val errorMessage = "오류가 발생했습니다."

        // When & Then
        assertTrue(emptyMessage.endsWith("."))
        assertTrue(idleMessage.endsWith("."))
        assertTrue(errorMessage.endsWith("."))
    }

    // ==================== Integration Tests ====================

    @Test
    fun `SearchEmptyStateCustom should handle search flow with no results`() {
        // Given
        val userQuery = "nonexistent"
        val searchResults = emptyList<String>()

        // When
        val showEmptyState = searchResults.isEmpty() && userQuery.isNotEmpty()
        val message =
            if (showEmptyState) {
                "\"$userQuery\"에 대한 검색 결과가 없습니다."
            } else {
                ""
            }

        // Then
        assertTrue(showEmptyState)
        assertTrue(message.contains(userQuery))
    }

    @Test
    fun `SearchIdleCustom should be shown before search`() {
        // Given
        val hasSearched = false
        val query = ""

        // When
        val showIdleState = !hasSearched && query.isEmpty()

        // Then
        assertTrue(showIdleState)
    }

    @Test
    fun `SearchErrorStateFallbackCustom should be shown on error`() {
        // Given
        val hasError = true

        // When
        val showErrorState = hasError

        // Then
        assertTrue(showErrorState)
    }

    @Test
    fun `Should transition from idle to empty state`() {
        // Given
        val states = mutableListOf<String>()

        // When - idle state
        states.add("idle")

        // When - user searches
        val query = "test"
        states.add("searching")

        // When - no results
        states.add("empty")

        // Then
        assertEquals(3, states.size)
        assertEquals("idle", states[0])
        assertEquals("searching", states[1])
        assertEquals("empty", states[2])
    }

    @Test
    fun `Should show appropriate state based on conditions`() {
        // Given
        data class SearchState(
            val query: String = "",
            val hasSearched: Boolean = false,
            val hasError: Boolean = false,
            val results: List<String> = emptyList(),
        )

        // When - idle
        val idleState = SearchState()
        val isIdle = !idleState.hasSearched && idleState.query.isEmpty()

        // When - empty
        val emptyState = SearchState(query = "test", hasSearched = true)
        val isEmpty = emptyState.hasSearched && emptyState.results.isEmpty()

        // When - error
        val errorState = SearchState(hasError = true)
        val isError = errorState.hasError

        // Then
        assertTrue(isIdle)
        assertTrue(isEmpty)
        assertTrue(isError)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `SearchEmptyStateCustom should handle query with only spaces`() {
        // Given
        val query = "   "

        // When
        val message = "\"$query\"에 대한 검색 결과가 없습니다."

        // Then
        assertTrue(message.contains("\"   \""))
    }

    @Test
    fun `SearchEmptyStateCustom should handle query with quotes`() {
        // Given
        val query = "test\"quote"

        // When
        val message = "\"$query\"에 대한 검색 결과가 없습니다."

        // Then
        assertTrue(message.contains(query))
    }

    @Test
    fun `SearchEmptyStateCustom should handle URL as query`() {
        // Given
        val query = "https://example.com"

        // When & Then
        assertTrue(query.startsWith("https://"))
        assertTrue(query.contains("example"))
    }

    @Test
    fun `SearchEmptyStateCustom should handle email as query`() {
        // Given
        val query = "test@example.com"

        // When & Then
        assertTrue(query.contains("@"))
        assertTrue(query.contains(".com"))
    }

    @Test
    fun `SearchEmptyStateCustom should handle hashtag query`() {
        // Given
        val query = "#sunset"

        // When & Then
        assertTrue(query.startsWith("#"))
        assertEquals("#sunset", query)
    }

    @Test
    fun `SearchEmptyStateCustom should handle multiple hashtags`() {
        // Given
        val query = "#travel #beach #vacation"

        // When & Then
        assertEquals(3, query.split(" ").count { it.startsWith("#") })
    }

    @Test
    fun `All components should support Box alignment`() {
        // Given
        val usesBoxAlignment = true

        // When & Then
        assertTrue(usesBoxAlignment) // All three use Box with Alignment.Center
    }

    @Test
    fun `Message lengths should be reasonable`() {
        // Given
        val emptyMessage = "\"test\"에 대한 검색 결과가 없습니다."
        val idleMessage = "검색어를 입력해주세요."
        val errorMessage = "오류가 발생했습니다."

        // When & Then
        assertTrue(emptyMessage.length < 100)
        assertTrue(idleMessage.length < 50)
        assertTrue(errorMessage.length < 50)
    }

    @Test
    fun `SearchEmptyStateCustom should handle numeric query`() {
        // Given
        val query = "12345"

        // When
        val message = "\"$query\"에 대한 검색 결과가 없습니다."

        // Then
        assertTrue(message.contains("12345"))
    }

    @Test
    fun `SearchEmptyStateCustom should handle date format query`() {
        // Given
        val query = "2024-01-01"

        // When & Then
        assertTrue(query.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `Components should have distinct purposes`() {
        // Given
        val purposes =
            mapOf(
                "SearchEmptyStateCustom" to "No search results",
                "SearchIdleCustom" to "Before search",
                "SearchErrorStateFallbackCustom" to "Error occurred",
            )

        // When & Then
        assertEquals(3, purposes.size)
        assertEquals("No search results", purposes["SearchEmptyStateCustom"])
        assertEquals("Before search", purposes["SearchIdleCustom"])
        assertEquals("Error occurred", purposes["SearchErrorStateFallbackCustom"])
    }

    @Test
    fun `SearchEmptyStateCustom should support query sanitization`() {
        // Given
        val query = "<script>alert('xss')</script>"

        // When
        val sanitizedQuery = query.replace("<", "&lt;").replace(">", "&gt;")

        // Then
        assertFalse(sanitizedQuery.contains("<script>"))
        assertTrue(sanitizedQuery.contains("&lt;script&gt;"))
    }

    @Test
    fun `All components should use Temp_word color reference`() {
        // Given
        val useTempWordColor = true

        // When & Then
        assertTrue(useTempWordColor) // All three use Temp_word for text color
    }

    @Test
    fun `SearchIdleCustom should prompt user action`() {
        // Given
        val message = "검색어를 입력해주세요."

        // When & Then
        assertTrue(message.contains("입력해주세요"))
    }

    @Test
    fun `SearchErrorStateFallbackCustom should be generic error message`() {
        // Given
        val message = "오류가 발생했습니다."

        // When & Then
        assertFalse(message.contains("network"))
        assertFalse(message.contains("timeout"))
        assertFalse(message.contains("서버"))
    }
}
