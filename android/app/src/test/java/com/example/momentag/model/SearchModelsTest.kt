package com.example.momentag.model

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchModelsTest {
    private fun createSamplePhoto(
        id: String,
        uriString: String,
    ): Photo {
        val mockUri = mockk<Uri>()
        every { mockUri.toString() } returns uriString
        return Photo(
            photoId = id,
            contentUri = mockUri,
        )
    }

    // ========== SearchResultItem Tests ==========

    @Test
    fun `SearchResultItem 생성 테스트`() {
        // Given
        val query = "여행"
        val photo = createSamplePhoto("1", "content://media/external/images/1")

        // When
        val searchResultItem =
            SearchResultItem(
                query = query,
                photo = photo,
            )

        // Then
        assertEquals("여행", searchResultItem.query)
        assertEquals("1", searchResultItem.photo.photoId)
        assertEquals(photo, searchResultItem.photo)
    }

    @Test
    fun `SearchResultItem 빈 쿼리 테스트`() {
        // Given
        val photo = createSamplePhoto("1", "content://media/external/images/1")

        // When
        val searchResultItem =
            SearchResultItem(
                query = "",
                photo = photo,
            )

        // Then
        assertTrue(searchResultItem.query.isEmpty())
        assertEquals(photo, searchResultItem.photo)
    }

    @Test
    fun `SearchResultItem copy 테스트`() {
        // Given
        val original =
            SearchResultItem(
                query = "원래쿼리",
                photo = createSamplePhoto("1", "content://media/external/images/1"),
            )

        // When
        val copied = original.copy(query = "새쿼리")

        // Then
        assertEquals("새쿼리", copied.query)
        assertEquals(original.photo, copied.photo)
    }

    @Test
    fun `SearchResultItem equals 테스트`() {
        // Given
        val photo = createSamplePhoto("1", "content://media/external/images/1")
        val item1 = SearchResultItem("여행", photo)
        val item2 = SearchResultItem("여행", photo)
        val item3 = SearchResultItem("가족", photo)

        // Then
        assertEquals(item1, item2)
        assertNotEquals(item1, item3)
    }

    @Test
    fun `SearchResultItem hashCode 테스트`() {
        // Given
        val photo = createSamplePhoto("1", "content://media/external/images/1")
        val item1 = SearchResultItem("여행", photo)
        val item2 = SearchResultItem("여행", photo)

        // Then
        assertEquals(item1.hashCode(), item2.hashCode())
    }

    @Test
    fun `SearchResultItem 특수문자 쿼리 테스트`() {
        // Given
        val specialQuery = "!@#$%^&*()_+{}[]|\\:\";<>?,./~`"
        val photo = createSamplePhoto("1", "content://media/external/images/1")

        // When
        val item = SearchResultItem(specialQuery, photo)

        // Then
        assertEquals(specialQuery, item.query)
    }

    // ========== SearchUiState Tests ==========

    @Test
    fun `SearchUiState Idle 상태 테스트`() {
        // When
        val state: SearchUiState = SearchUiState.Idle

        // Then
        assertTrue(state is SearchUiState.Idle)
        assertFalse(state is SearchUiState.Loading)
        assertFalse(state is SearchUiState.Success)
    }

    @Test
    fun `SearchUiState Loading 상태 테스트`() {
        // When
        val state: SearchUiState = SearchUiState.Loading

        // Then
        assertTrue(state is SearchUiState.Loading)
        assertFalse(state is SearchUiState.Idle)
    }

    @Test
    fun `SearchUiState Success 상태 테스트`() {
        // Given
        val photo = createSamplePhoto("1", "content://media/external/images/1")
        val results =
            listOf(
                SearchResultItem("여행", photo),
                SearchResultItem("여행", createSamplePhoto("2", "content://media/external/images/2")),
            )

        // When
        val state: SearchUiState = SearchUiState.Success(results, "여행")

        // Then
        assertTrue(state is SearchUiState.Success)
        val successState = state as SearchUiState.Success
        assertEquals(2, successState.results.size)
        assertEquals("여행", successState.query)
    }

    @Test
    fun `SearchUiState Success 빈 결과 리스트 테스트`() {
        // When
        val state: SearchUiState = SearchUiState.Success(emptyList(), "검색어")

        // Then
        assertTrue(state is SearchUiState.Success)
        val successState = state as SearchUiState.Success
        assertTrue(successState.results.isEmpty())
        assertEquals("검색어", successState.query)
    }

    @Test
    fun `SearchUiState Empty 상태 테스트`() {
        // When
        val state: SearchUiState = SearchUiState.Empty("결과없음")

        // Then
        assertTrue(state is SearchUiState.Empty)
        assertEquals("결과없음", (state as SearchUiState.Empty).query)
    }

    @Test
    fun `SearchUiState Error 상태 테스트`() {
        // Given
        val errorMessage = "검색 중 오류가 발생했습니다"

        // When
        val state: SearchUiState = SearchUiState.Error(errorMessage)

        // Then
        assertTrue(state is SearchUiState.Error)
        assertEquals(errorMessage, (state as SearchUiState.Error).message)
    }

    @Test
    fun `SearchUiState 상태 전환 시나리오 테스트`() {
        // Idle -> Loading
        var state: SearchUiState = SearchUiState.Idle
        assertTrue(state is SearchUiState.Idle)

        // Loading
        state = SearchUiState.Loading
        assertTrue(state is SearchUiState.Loading)

        // Loading -> Success
        val results = listOf(SearchResultItem("여행", createSamplePhoto("1", "content://media/external/images/1")))
        state = SearchUiState.Success(results, "여행")
        assertTrue(state is SearchUiState.Success)
    }

    @Test
    fun `SearchUiState Empty와 Success 구분 테스트`() {
        // Given
        val emptyState: SearchUiState = SearchUiState.Empty("검색어")
        val successState: SearchUiState = SearchUiState.Success(emptyList(), "검색어")

        // Then
        assertTrue(emptyState is SearchUiState.Empty)
        assertTrue(successState is SearchUiState.Success)
        assertNotEquals(emptyState::class, successState::class)
    }

    @Test
    fun `SearchUiState Success copy 테스트`() {
        // Given
        val results = listOf(SearchResultItem("여행", createSamplePhoto("1", "content://media/external/images/1")))
        val original = SearchUiState.Success(results, "원래검색")

        // When
        val copied = original.copy(query = "새검색")

        // Then
        assertEquals("새검색", copied.query)
        assertEquals(original.results, copied.results)
    }

    @Test
    fun `SearchUiState when 표현식 테스트`() {
        // Given
        val states =
            listOf<SearchUiState>(
                SearchUiState.Idle,
                SearchUiState.Loading,
                SearchUiState.Success(emptyList(), "query"),
                SearchUiState.Empty("query"),
                SearchUiState.Error("error"),
            )

        // When & Then
        states.forEach { state ->
            val result =
                when (state) {
                    is SearchUiState.Idle -> "idle"
                    is SearchUiState.Loading -> "loading"
                    is SearchUiState.Success -> "success"
                    is SearchUiState.Empty -> "empty"
                    is SearchUiState.Error -> "error"
                }
            assertNotNull(result)
        }
    }

    // ========== SemanticSearchState Tests ==========

    @Test
    fun `SemanticSearchState Idle 상태 테스트`() {
        // When
        val state: SemanticSearchState = SemanticSearchState.Idle

        // Then
        assertTrue(state is SemanticSearchState.Idle)
        assertFalse(state is SemanticSearchState.Loading)
    }

    @Test
    fun `SemanticSearchState Loading 상태 테스트`() {
        // When
        val state: SemanticSearchState = SemanticSearchState.Loading

        // Then
        assertTrue(state is SemanticSearchState.Loading)
        assertFalse(state is SemanticSearchState.Idle)
    }

    @Test
    fun `SemanticSearchState Success 상태 테스트`() {
        // Given
        val photos =
            listOf(
                createSamplePhoto("1", "content://media/external/images/1"),
                createSamplePhoto("2", "content://media/external/images/2"),
            )

        // When
        val state: SemanticSearchState = SemanticSearchState.Success(photos, "해변 풍경")

        // Then
        assertTrue(state is SemanticSearchState.Success)
        val successState = state as SemanticSearchState.Success
        assertEquals(2, successState.photos.size)
        assertEquals("해변 풍경", successState.query)
    }

    @Test
    fun `SemanticSearchState Success 빈 사진 리스트 테스트`() {
        // When
        val state: SemanticSearchState = SemanticSearchState.Success(emptyList(), "검색어")

        // Then
        assertTrue(state is SemanticSearchState.Success)
        assertTrue((state as SemanticSearchState.Success).photos.isEmpty())
    }

    @Test
    fun `SemanticSearchState Empty 상태 테스트`() {
        // When
        val state: SemanticSearchState = SemanticSearchState.Empty("결과없는검색")

        // Then
        assertTrue(state is SemanticSearchState.Empty)
        assertEquals("결과없는검색", (state as SemanticSearchState.Empty).query)
    }

    @Test
    fun `SemanticSearchState NetworkError 상태 테스트`() {
        // Given
        val errorMessage = "네트워크 연결을 확인해주세요"

        // When
        val state: SemanticSearchState = SemanticSearchState.NetworkError(errorMessage)

        // Then
        assertTrue(state is SemanticSearchState.NetworkError)
        assertEquals(errorMessage, (state as SemanticSearchState.NetworkError).message)
    }

    @Test
    fun `SemanticSearchState Error 상태 테스트`() {
        // Given
        val errorMessage = "시맨틱 검색 중 오류가 발생했습니다"

        // When
        val state: SemanticSearchState = SemanticSearchState.Error(errorMessage)

        // Then
        assertTrue(state is SemanticSearchState.Error)
        assertEquals(errorMessage, (state as SemanticSearchState.Error).message)
    }

    @Test
    fun `SemanticSearchState NetworkError와 Error 구분 테스트`() {
        // Given
        val networkError: SemanticSearchState = SemanticSearchState.NetworkError("네트워크 에러")
        val generalError: SemanticSearchState = SemanticSearchState.Error("일반 에러")

        // Then
        assertTrue(networkError is SemanticSearchState.NetworkError)
        assertTrue(generalError is SemanticSearchState.Error)
        assertNotEquals(networkError::class, generalError::class)
    }

    @Test
    fun `SemanticSearchState 상태 전환 시나리오 테스트`() {
        // Idle -> Loading
        var state: SemanticSearchState = SemanticSearchState.Idle
        assertTrue(state is SemanticSearchState.Idle)

        // Loading
        state = SemanticSearchState.Loading
        assertTrue(state is SemanticSearchState.Loading)

        // Loading -> Success
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        state = SemanticSearchState.Success(photos, "검색어")
        assertTrue(state is SemanticSearchState.Success)
        assertEquals(1, (state as SemanticSearchState.Success).photos.size)
    }

    @Test
    fun `SemanticSearchState 에러 처리 시나리오 테스트`() {
        // Loading -> NetworkError
        var state: SemanticSearchState = SemanticSearchState.Loading
        state = SemanticSearchState.NetworkError("네트워크 에러")
        assertTrue(state is SemanticSearchState.NetworkError)

        // 재시도 -> Error
        state = SemanticSearchState.Error("일반 에러")
        assertTrue(state is SemanticSearchState.Error)
    }

    @Test
    fun `SemanticSearchState Success copy 테스트`() {
        // Given
        val photos = listOf(createSamplePhoto("1", "content://media/external/images/1"))
        val original = SemanticSearchState.Success(photos, "원래검색")

        // When
        val copied = original.copy(query = "새검색")

        // Then
        assertEquals("새검색", copied.query)
        assertEquals(original.photos, copied.photos)
    }

    @Test
    fun `SemanticSearchState Empty copy 테스트`() {
        // Given
        val original = SemanticSearchState.Empty("원래검색")

        // When
        val copied = original.copy(query = "새검색")

        // Then
        assertEquals("새검색", copied.query)
    }

    @Test
    fun `SemanticSearchState when 표현식 테스트`() {
        // Given
        val states =
            listOf<SemanticSearchState>(
                SemanticSearchState.Idle,
                SemanticSearchState.Loading,
                SemanticSearchState.Success(emptyList(), "query"),
                SemanticSearchState.Empty("query"),
                SemanticSearchState.NetworkError("network error"),
                SemanticSearchState.Error("error"),
            )

        // When & Then
        states.forEach { state ->
            val result =
                when (state) {
                    is SemanticSearchState.Idle -> "idle"
                    is SemanticSearchState.Loading -> "loading"
                    is SemanticSearchState.Success -> "success"
                    is SemanticSearchState.Empty -> "empty"
                    is SemanticSearchState.NetworkError -> "network_error"
                    is SemanticSearchState.Error -> "error"
                }
            assertNotNull(result)
        }
    }

    @Test
    fun `SemanticSearchState 대량 사진 데이터 테스트`() {
        // Given
        val manyPhotos =
            (1..1000).map {
                createSamplePhoto(it.toString(), "content://media/external/images/$it")
            }

        // When
        val state = SemanticSearchState.Success(manyPhotos, "대량검색")

        // Then
        assertTrue(state is SemanticSearchState.Success)
        assertEquals(1000, state.photos.size)
        assertEquals("500", state.photos[499].photoId)
    }

    @Test
    fun `SearchUiState와 SemanticSearchState 독립성 테스트`() {
        // Given
        val searchState: SearchUiState = SearchUiState.Loading
        val semanticState: SemanticSearchState = SemanticSearchState.Loading

        // Then
        assertTrue(searchState is SearchUiState.Loading)
        assertTrue(semanticState is SemanticSearchState.Loading)
        assertNotEquals(searchState::class, semanticState::class)
    }

    @Test
    fun `SearchResultItem 긴 쿼리 테스트`() {
        // Given
        val longQuery = "검색어".repeat(1000)
        val photo = createSamplePhoto("1", "content://media/external/images/1")

        // When
        val item = SearchResultItem(longQuery, photo)

        // Then
        assertEquals(3000, item.query.length) // "검색어" = 3자 * 1000
    }

    @Test
    fun `SearchUiState Success 대량 결과 테스트`() {
        // Given
        val manyResults =
            (1..1000).map {
                SearchResultItem(
                    "검색$it",
                    createSamplePhoto(it.toString(), "content://media/external/images/$it"),
                )
            }

        // When
        val state = SearchUiState.Success(manyResults, "대량검색")

        // Then
        assertEquals(1000, state.results.size)
        assertEquals("검색500", state.results[499].query)
    }

    @Test
    fun `SemanticSearchState 빈 에러 메시지 테스트`() {
        // When
        val networkError = SemanticSearchState.NetworkError("")
        val generalError = SemanticSearchState.Error("")

        // Then
        assertTrue(networkError.message.isEmpty())
        assertTrue(generalError.message.isEmpty())
    }

    @Test
    fun `SearchUiState Error와 SemanticSearchState Error 비교 테스트`() {
        // Given
        val searchError: SearchUiState = SearchUiState.Error("검색 에러")
        val semanticError: SemanticSearchState = SemanticSearchState.Error("시맨틱 에러")

        // Then
        assertTrue(searchError is SearchUiState.Error)
        assertTrue(semanticError is SemanticSearchState.Error)
        assertNotEquals(searchError::class, semanticError::class)
    }

    @Test
    fun `SearchUiState 모든 상태 타입 검증 테스트`() {
        // Given
        val allStates =
            listOf<SearchUiState>(
                SearchUiState.Idle,
                SearchUiState.Loading,
                SearchUiState.Success(emptyList(), "query"),
                SearchUiState.Empty("query"),
                SearchUiState.Error("error"),
            )

        // Then
        assertEquals(5, allStates.size)
        assertTrue(allStates.any { it is SearchUiState.Idle })
        assertTrue(allStates.any { it is SearchUiState.Loading })
        assertTrue(allStates.any { it is SearchUiState.Success })
        assertTrue(allStates.any { it is SearchUiState.Empty })
        assertTrue(allStates.any { it is SearchUiState.Error })
    }

    @Test
    fun `SemanticSearchState 모든 상태 타입 검증 테스트`() {
        // Given
        val allStates =
            listOf<SemanticSearchState>(
                SemanticSearchState.Idle,
                SemanticSearchState.Loading,
                SemanticSearchState.Success(emptyList(), "query"),
                SemanticSearchState.Empty("query"),
                SemanticSearchState.NetworkError("network error"),
                SemanticSearchState.Error("error"),
            )

        // Then
        assertEquals(6, allStates.size)
        assertTrue(allStates.any { it is SemanticSearchState.Idle })
        assertTrue(allStates.any { it is SemanticSearchState.Loading })
        assertTrue(allStates.any { it is SemanticSearchState.Success })
        assertTrue(allStates.any { it is SemanticSearchState.Empty })
        assertTrue(allStates.any { it is SemanticSearchState.NetworkError })
        assertTrue(allStates.any { it is SemanticSearchState.Error })
    }
}
