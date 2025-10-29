package com.example.momentag.repository

import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.PhotoToPhotoRequest
import com.example.momentag.model.Tag
import com.example.momentag.network.ApiService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Response
import java.io.IOException

@ExperimentalCoroutinesApi
class RecommendRepositoryTest {
    private lateinit var apiService: ApiService
    private lateinit var repository: RecommendRepository

    @Before
    fun setUp() {
        apiService = mock()
        repository = RecommendRepository(apiService)
    }

    // ========== recommendPhotosFromTag Tests ==========

    @Test
    fun `recommendPhotosFromTag returns Success when API call succeeds`() =
        runTest {
            // Given
            val tagId = "tag123"
            val expectedPhotos =
                listOf(
                    PhotoResponse(photoId = "photo1", photoPathId = 1L),
                    PhotoResponse(photoId = "photo2", photoPathId = 2L),
                )
            val response = Response.success(expectedPhotos)
            whenever(apiService.recommendPhotosFromTag(tagId)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertEquals(expectedPhotos, (result as RecommendRepository.RecommendResult.Success).data)
            verify(apiService).recommendPhotosFromTag(tagId)
        }

    @Test
    fun `recommendPhotosFromTag returns BadRequest when API returns 400`() =
        runTest {
            // Given
            val tagId = "tag123"
            val response = Response.error<List<PhotoResponse>>(400, "".toResponseBody())
            whenever(apiService.recommendPhotosFromTag(tagId)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.BadRequest)
            assertEquals("Bad request", (result as RecommendRepository.RecommendResult.BadRequest).message)
            verify(apiService).recommendPhotosFromTag(tagId)
        }

    @Test
    fun `recommendPhotosFromTag returns Unauthorized when API returns 401`() =
        runTest {
            // Given
            val tagId = "tag123"
            val response = Response.error<List<PhotoResponse>>(401, "".toResponseBody())
            whenever(apiService.recommendPhotosFromTag(tagId)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Unauthorized)
            assertEquals(
                "Authentication failed",
                (result as RecommendRepository.RecommendResult.Unauthorized).message,
            )
            verify(apiService).recommendPhotosFromTag(tagId)
        }

    @Test
    fun `recommendPhotosFromTag returns Error when API returns 500`() =
        runTest {
            // Given
            val tagId = "tag123"
            val response = Response.error<List<PhotoResponse>>(500, "".toResponseBody())
            whenever(apiService.recommendPhotosFromTag(tagId)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unknown error"))
            verify(apiService).recommendPhotosFromTag(tagId)
        }

    @Test
    fun `recommendPhotosFromTag returns NetworkError when IOException occurs`() =
        runTest {
            // Given
            val tagId = "tag123"
            val exception = IOException("Network connection failed")
            doAnswer { throw exception }.whenever(apiService).recommendPhotosFromTag(tagId)

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.NetworkError)
            assertEquals(
                "Network error: Network connection failed",
                (result as RecommendRepository.RecommendResult.NetworkError).message,
            )
            verify(apiService).recommendPhotosFromTag(tagId)
        }

    @Test
    fun `recommendPhotosFromTag returns Error when unexpected exception occurs`() =
        runTest {
            // Given
            val tagId = "tag123"
            val exception = RuntimeException("Unexpected error")
            whenever(apiService.recommendPhotosFromTag(tagId)).thenThrow(exception)

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertEquals(
                "An unexpected error occurred: Unexpected error",
                (result as RecommendRepository.RecommendResult.Error).message,
            )
            verify(apiService).recommendPhotosFromTag(tagId)
        }

    // ========== recommendTagFromPhoto Tests ==========

    @Test
    fun `recommendTagFromPhoto returns Success when API call succeeds`() =
        runTest {
            // Given
            val photoId = "photo123"
            val expectedTag = Tag(tagName = "Nature", tagId = "tag1")
            val response = Response.success(expectedTag)
            whenever(apiService.recommendTagFromPhoto(photoId)).thenReturn(response)

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertEquals(expectedTag, (result as RecommendRepository.RecommendResult.Success).data)
            verify(apiService).recommendTagFromPhoto(photoId)
        }

    @Test
    fun `recommendTagFromPhoto returns BadRequest when API returns 400`() =
        runTest {
            // Given
            val photoId = "photo123"
            val response = Response.error<Tag>(400, "".toResponseBody())
            whenever(apiService.recommendTagFromPhoto(photoId)).thenReturn(response)

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.BadRequest)
            assertEquals("Bad request", (result as RecommendRepository.RecommendResult.BadRequest).message)
            verify(apiService).recommendTagFromPhoto(photoId)
        }

    @Test
    fun `recommendTagFromPhoto returns Unauthorized when API returns 401`() =
        runTest {
            // Given
            val photoId = "photo123"
            val response = Response.error<Tag>(401, "".toResponseBody())
            whenever(apiService.recommendTagFromPhoto(photoId)).thenReturn(response)

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Unauthorized)
            assertEquals(
                "Authentication failed",
                (result as RecommendRepository.RecommendResult.Unauthorized).message,
            )
            verify(apiService).recommendTagFromPhoto(photoId)
        }

    @Test
    fun `recommendTagFromPhoto returns Error when API returns 404`() =
        runTest {
            // Given
            val photoId = "photo123"
            val response = Response.error<Tag>(404, "".toResponseBody())
            whenever(apiService.recommendTagFromPhoto(photoId)).thenReturn(response)

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unknown error"))
            verify(apiService).recommendTagFromPhoto(photoId)
        }

    @Test
    fun `recommendTagFromPhoto returns NetworkError when IOException occurs`() =
        runTest {
            // Given
            val photoId = "photo123"
            val exception = IOException("Connection timeout")
            doAnswer { throw exception }.whenever(apiService).recommendTagFromPhoto(photoId)

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.NetworkError)
            assertEquals(
                "Network error: Connection timeout",
                (result as RecommendRepository.RecommendResult.NetworkError).message,
            )
            verify(apiService).recommendTagFromPhoto(photoId)
        }

    @Test
    fun `recommendTagFromPhoto returns Error when unexpected exception occurs`() =
        runTest {
            // Given
            val photoId = "photo123"
            val exception = NullPointerException("Null value encountered")
            whenever(apiService.recommendTagFromPhoto(photoId)).thenThrow(exception)

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertEquals(
                "An unexpected error occurred: Null value encountered",
                (result as RecommendRepository.RecommendResult.Error).message,
            )
            verify(apiService).recommendTagFromPhoto(photoId)
        }

    // ========== recommendPhotosFromPhotos Tests ==========

    @Test
    fun `recommendPhotosFromPhotos returns Success when API call succeeds`() =
        runTest {
            // Given
            val photoIds = listOf("photo1", "photo2", "photo3")
            val expectedPhotos =
                listOf(
                    PhotoResponse(photoId = "photo4", photoPathId = 4L),
                    PhotoResponse(photoId = "photo5", photoPathId = 5L),
                )
            val request = PhotoToPhotoRequest(photoIds)
            val response = Response.success(expectedPhotos)
            whenever(apiService.recommendPhotosFromPhotos(request)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertEquals(expectedPhotos, (result as RecommendRepository.RecommendResult.Success).data)
            verify(apiService).recommendPhotosFromPhotos(any())
        }

    @Test
    fun `recommendPhotosFromPhotos returns Success with empty list`() =
        runTest {
            // Given
            val photoIds = listOf("photo1")
            val expectedPhotos = emptyList<PhotoResponse>()
            val request = PhotoToPhotoRequest(photoIds)
            val response = Response.success(expectedPhotos)
            whenever(apiService.recommendPhotosFromPhotos(request)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertEquals(expectedPhotos, (result as RecommendRepository.RecommendResult.Success).data)
            verify(apiService).recommendPhotosFromPhotos(any())
        }

    @Test
    fun `recommendPhotosFromPhotos returns BadRequest when API returns 400`() =
        runTest {
            // Given
            val photoIds = listOf("photo1", "photo2")
            val request = PhotoToPhotoRequest(photoIds)
            val response = Response.error<List<PhotoResponse>>(400, "".toResponseBody())
            whenever(apiService.recommendPhotosFromPhotos(request)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.BadRequest)
            assertEquals("Bad request", (result as RecommendRepository.RecommendResult.BadRequest).message)
            verify(apiService).recommendPhotosFromPhotos(any())
        }

    @Test
    fun `recommendPhotosFromPhotos returns Unauthorized when API returns 401`() =
        runTest {
            // Given
            val photoIds = listOf("photo1", "photo2")
            val request = PhotoToPhotoRequest(photoIds)
            val response = Response.error<List<PhotoResponse>>(401, "".toResponseBody())
            whenever(apiService.recommendPhotosFromPhotos(request)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Unauthorized)
            assertEquals(
                "Authentication failed",
                (result as RecommendRepository.RecommendResult.Unauthorized).message,
            )
            verify(apiService).recommendPhotosFromPhotos(any())
        }

    @Test
    fun `recommendPhotosFromPhotos returns Error when API returns 503`() =
        runTest {
            // Given
            val photoIds = listOf("photo1")
            val request = PhotoToPhotoRequest(photoIds)
            val response = Response.error<List<PhotoResponse>>(503, "".toResponseBody())
            whenever(apiService.recommendPhotosFromPhotos(request)).thenReturn(response)

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unknown error"))
            verify(apiService).recommendPhotosFromPhotos(any())
        }

    @Test
    fun `recommendPhotosFromPhotos returns NetworkError when IOException occurs`() =
        runTest {
            // Given
            val photoIds = listOf("photo1", "photo2")
            val exception = IOException("No network available")
            doAnswer { throw exception }.whenever(apiService).recommendPhotosFromPhotos(any())

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.NetworkError)
            assertEquals(
                "Network error: No network available",
                (result as RecommendRepository.RecommendResult.NetworkError).message,
            )
            verify(apiService).recommendPhotosFromPhotos(any())
        }

    @Test
    fun `recommendPhotosFromPhotos returns Error when unexpected exception occurs`() =
        runTest {
            // Given
            val photoIds = listOf("photo1", "photo2")
            val exception = IllegalStateException("Invalid state")
            whenever(apiService.recommendPhotosFromPhotos(any())).thenThrow(exception)

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertEquals(
                "An unexpected error occurred: Invalid state",
                (result as RecommendRepository.RecommendResult.Error).message,
            )
            verify(apiService).recommendPhotosFromPhotos(any())
        }

    @Test
    fun `recommendPhotosFromPhotos creates correct request body`() =
        runTest {
            // Given
            val photoIds = listOf("photo1", "photo2", "photo3")
            val expectedPhotos = listOf<PhotoResponse>()
            val response = Response.success(expectedPhotos)
            whenever(apiService.recommendPhotosFromPhotos(any())).thenReturn(response)

            // When
            repository.recommendPhotosFromPhotos(photoIds)

            // Then
            verify(apiService).recommendPhotosFromPhotos(PhotoToPhotoRequest(photoIds))
        }
}
