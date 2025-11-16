package com.example.momentag.repository

import com.example.momentag.model.PhotoResponse
import com.example.momentag.network.ApiService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchRepositoryTest {
    private lateinit var repository: SearchRepository
    private lateinit var apiService: ApiService

    @Before
    fun setUp() {
        apiService = mockk()
        repository = SearchRepository(apiService)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // Helper function
    private fun createPhotoResponse(id: String = "photo1") =
        PhotoResponse(
            photoId = id,
            photoPathId = 1L,
            createdAt = "2024-01-01T00:00:00Z",
        )

    // ========== Input Validation Tests ==========

    @Test
    fun `semanticSearch returns BadRequest when query is blank`() =
        runTest {
            // When
            val result = repository.semanticSearch("   ", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.BadRequest)
            assertEquals("Query cannot be empty", (result as SearchRepository.SearchResult.BadRequest).message)
            coVerify(exactly = 0) { apiService.semanticSearch(any(), any()) }
        }

    @Test
    fun `semanticSearch returns BadRequest when query is empty`() =
        runTest {
            // When
            val result = repository.semanticSearch("", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.BadRequest)
            assertEquals("Query cannot be empty", (result as SearchRepository.SearchResult.BadRequest).message)
        }

    @Test
    fun `semanticSearch returns BadRequest when offset is negative`() =
        runTest {
            // When
            val result = repository.semanticSearch("test", -1)

            // Then
            assertTrue(result is SearchRepository.SearchResult.BadRequest)
            assertEquals("Offset must be non-negative", (result as SearchRepository.SearchResult.BadRequest).message)
            coVerify(exactly = 0) { apiService.semanticSearch(any(), any()) }
        }

    @Test
    fun `semanticSearch accepts zero offset`() =
        runTest {
            // Given
            val photos = listOf(createPhotoResponse())
            coEvery { apiService.semanticSearch("test", 0) } returns Response.success(photos)

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Success)
            coVerify { apiService.semanticSearch("test", 0) }
        }

    // ========== Success Tests ==========

    @Test
    fun `semanticSearch returns Success with photos`() =
        runTest {
            // Given
            val photos = listOf(createPhotoResponse("photo1"), createPhotoResponse("photo2"))
            coEvery { apiService.semanticSearch("sunset", 0) } returns Response.success(photos)

            // When
            val result = repository.semanticSearch("sunset", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Success)
            assertEquals(photos, (result as SearchRepository.SearchResult.Success).photos)
        }

    @Test
    fun `semanticSearch returns Success with single photo`() =
        runTest {
            // Given
            val photos = listOf(createPhotoResponse("photo1"))
            coEvery { apiService.semanticSearch("test", 0) } returns Response.success(photos)

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Success)
            assertEquals(1, (result as SearchRepository.SearchResult.Success).photos.size)
        }

    @Test
    fun `semanticSearch with offset parameter`() =
        runTest {
            // Given
            val photos = listOf(createPhotoResponse())
            coEvery { apiService.semanticSearch("test", 10) } returns Response.success(photos)

            // When
            val result = repository.semanticSearch("test", 10)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Success)
            coVerify { apiService.semanticSearch("test", 10) }
        }

    // ========== Empty Result Tests ==========

    @Test
    fun `semanticSearch returns Empty when response body is empty list`() =
        runTest {
            // Given
            coEvery { apiService.semanticSearch("test", 0) } returns Response.success(emptyList())

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Empty)
            assertEquals("test", (result as SearchRepository.SearchResult.Empty).query)
        }

    @Test
    fun `semanticSearch returns Empty on 404 response`() =
        runTest {
            // Given
            coEvery { apiService.semanticSearch("test", 0) } returns Response.error(404, mockk(relaxed = true))

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Empty)
            assertEquals("test", (result as SearchRepository.SearchResult.Empty).query)
        }

    // ========== Error Tests ==========

    @Test
    fun `semanticSearch returns BadRequest on 400 response`() =
        runTest {
            // Given
            coEvery { apiService.semanticSearch("test", 0) } returns Response.error(400, mockk(relaxed = true))

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.BadRequest)
            assertTrue((result as SearchRepository.SearchResult.BadRequest).message.contains("Invalid request"))
        }

    @Test
    fun `semanticSearch returns Unauthorized on 401 response`() =
        runTest {
            // Given
            coEvery { apiService.semanticSearch("test", 0) } returns Response.error(401, mockk(relaxed = true))

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Unauthorized)
            assertEquals("Authentication required", (result as SearchRepository.SearchResult.Unauthorized).message)
        }

    @Test
    fun `semanticSearch returns Error on other error codes`() =
        runTest {
            // Given
            coEvery { apiService.semanticSearch("test", 0) } returns Response.error(500, mockk(relaxed = true))

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Error)
            assertTrue((result as SearchRepository.SearchResult.Error).message.contains("Unexpected error"))
            assertTrue(result.message.contains("500"))
        }

    @Test
    fun `semanticSearch returns NetworkError on IOException`() =
        runTest {
            // Given
            coEvery { apiService.semanticSearch("test", 0) } throws IOException("Connection timeout")

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.NetworkError)
            assertTrue((result as SearchRepository.SearchResult.NetworkError).message.contains("Network error"))
            assertTrue(result.message.contains("Connection timeout"))
        }

    @Test
    fun `semanticSearch returns Error on generic Exception`() =
        runTest {
            // Given
            coEvery { apiService.semanticSearch("test", 0) } throws Exception("Unknown error")

            // When
            val result = repository.semanticSearch("test", 0)

            // Then
            assertTrue(result is SearchRepository.SearchResult.Error)
            assertTrue((result as SearchRepository.SearchResult.Error).message.contains("Unknown error"))
        }

    // ========== Integration Tests ==========

    @Test
    fun `multiple searches with different queries`() =
        runTest {
            // Given
            val photos1 = listOf(createPhotoResponse("photo1"))
            val photos2 = listOf(createPhotoResponse("photo2"))
            coEvery { apiService.semanticSearch("sunset", 0) } returns Response.success(photos1)
            coEvery { apiService.semanticSearch("mountain", 0) } returns Response.success(photos2)

            // When
            val result1 = repository.semanticSearch("sunset", 0)
            val result2 = repository.semanticSearch("mountain", 0)

            // Then
            assertTrue(result1 is SearchRepository.SearchResult.Success)
            assertTrue(result2 is SearchRepository.SearchResult.Success)
            assertEquals("photo1", (result1 as SearchRepository.SearchResult.Success).photos[0].photoId)
            assertEquals("photo2", (result2 as SearchRepository.SearchResult.Success).photos[0].photoId)
        }

    @Test
    fun `pagination with different offsets`() =
        runTest {
            // Given
            val photos1 = listOf(createPhotoResponse("photo1"))
            val photos2 = listOf(createPhotoResponse("photo2"))
            coEvery { apiService.semanticSearch("test", 0) } returns Response.success(photos1)
            coEvery { apiService.semanticSearch("test", 10) } returns Response.success(photos2)

            // When
            val result1 = repository.semanticSearch("test", 0)
            val result2 = repository.semanticSearch("test", 10)

            // Then
            assertTrue(result1 is SearchRepository.SearchResult.Success)
            assertTrue(result2 is SearchRepository.SearchResult.Success)
            coVerify { apiService.semanticSearch("test", 0) }
            coVerify { apiService.semanticSearch("test", 10) }
        }

    @Test
    fun `handles different error scenarios in sequence`() =
        runTest {
            // Setup
            coEvery { apiService.semanticSearch("valid", 0) } returns
                Response.success(listOf(createPhotoResponse()))
            coEvery { apiService.semanticSearch("unauthorized", 0) } returns
                Response.error(401, mockk(relaxed = true))
            coEvery { apiService.semanticSearch("network", 0) } throws
                IOException("Network error")

            // When
            val result1 = repository.semanticSearch("valid", 0)
            val result2 = repository.semanticSearch("unauthorized", 0)
            val result3 = repository.semanticSearch("network", 0)

            // Then
            assertTrue(result1 is SearchRepository.SearchResult.Success)
            assertTrue(result2 is SearchRepository.SearchResult.Unauthorized)
            assertTrue(result3 is SearchRepository.SearchResult.NetworkError)
        }

    @Test
    fun `query trimming is handled by validation`() =
        runTest {
            // When - whitespace queries
            val result1 = repository.semanticSearch("  ", 0)
            val result2 = repository.semanticSearch("\t", 0)
            val result3 = repository.semanticSearch("\n", 0)

            // Then - all should fail validation
            assertTrue(result1 is SearchRepository.SearchResult.BadRequest)
            assertTrue(result2 is SearchRepository.SearchResult.BadRequest)
            assertTrue(result3 is SearchRepository.SearchResult.BadRequest)
        }
}
