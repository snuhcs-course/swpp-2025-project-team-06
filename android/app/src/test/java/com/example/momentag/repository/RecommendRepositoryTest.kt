package com.example.momentag.repository

import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.StoryResponse
import com.example.momentag.model.StoryStateResponse
import com.example.momentag.model.Tag
import com.example.momentag.network.ApiService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RecommendRepositoryTest {
    private lateinit var repository: RecommendRepository
    private lateinit var apiService: ApiService

    @Before
    fun setUp() {
        apiService = mockk()
        repository = RecommendRepository(apiService)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Helper Functions ==========

    private fun createTag(
        id: String = "tag1",
        name: String = "TestTag",
        isPreset: Boolean = false,
    ) = Tag(
        tagName = name,
        tagId = id,
        isPreset = isPreset,
    )

    private fun createPhotoResponse(
        id: String = "photo1",
        pathId: Long = 100L,
        createdAt: String = "2024-01-01T00:00:00Z",
    ) = PhotoResponse(
        photoId = id,
        photoPathId = pathId,
        createdAt = createdAt,
    )

    private fun createStoryResponse(
        photoId: String = "photo1",
        photoPathId: Long = 100L,
        tags: List<String> = listOf("tag1", "tag2"),
    ) = StoryResponse(
        photoId = photoId,
        photoPathId = photoPathId,
        tags = tags,
    )

    private fun createStoryStateResponse(
        status: String = "ready",
        stories: List<StoryResponse> = emptyList(),
    ) = StoryStateResponse(
        status = status,
        stories = stories,
    )

    // ========== recommendTagFromPhoto Tests ==========

    @Test
    fun `recommendTagFromPhoto returns Success with tags`() =
        runTest {
            // Given
            val photoId = "photo1"
            val tags = listOf(createTag("tag1"), createTag("tag2"))
            coEvery { apiService.recommendTagFromPhoto(photoId) } returns Response.success(tags)

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertEquals(tags, (result as RecommendRepository.RecommendResult.Success).data)
            coVerify { apiService.recommendTagFromPhoto(photoId) }
        }

    @Test
    fun `recommendTagFromPhoto returns empty list when no tags recommended`() =
        runTest {
            // Given
            val photoId = "photo1"
            coEvery { apiService.recommendTagFromPhoto(photoId) } returns Response.success(emptyList())

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertTrue((result as RecommendRepository.RecommendResult.Success).data.isEmpty())
        }

    @Test
    fun `recommendTagFromPhoto returns Unauthorized on 401`() =
        runTest {
            // Given
            val photoId = "photo1"
            coEvery { apiService.recommendTagFromPhoto(photoId) } returns
                Response.error(401, "".toResponseBody())

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Unauthorized)
            assertEquals("Authentication failed", (result as RecommendRepository.RecommendResult.Unauthorized).message)
        }

    @Test
    fun `recommendTagFromPhoto returns BadRequest on 400`() =
        runTest {
            // Given
            val photoId = "photo1"
            coEvery { apiService.recommendTagFromPhoto(photoId) } returns
                Response.error(400, "".toResponseBody())

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.BadRequest)
            assertEquals("Bad request", (result as RecommendRepository.RecommendResult.BadRequest).message)
        }

    @Test
    fun `recommendTagFromPhoto returns Error on 404`() =
        runTest {
            // Given
            val photoId = "photo1"
            coEvery { apiService.recommendTagFromPhoto(photoId) } returns
                Response.error(404, "".toResponseBody())

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unknown error"))
        }

    @Test
    fun `recommendTagFromPhoto returns Error on 500`() =
        runTest {
            // Given
            val photoId = "photo1"
            coEvery { apiService.recommendTagFromPhoto(photoId) } returns
                Response.error(500, "".toResponseBody())

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unknown error"))
        }

    @Test
    fun `recommendTagFromPhoto returns NetworkError on IOException`() =
        runTest {
            // Given
            val photoId = "photo1"
            coEvery { apiService.recommendTagFromPhoto(photoId) } throws IOException("Network error")

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.NetworkError)
            assertTrue((result as RecommendRepository.RecommendResult.NetworkError).message.contains("Network error"))
        }

    @Test
    fun `recommendTagFromPhoto returns Error on generic Exception`() =
        runTest {
            // Given
            val photoId = "photo1"
            coEvery { apiService.recommendTagFromPhoto(photoId) } throws RuntimeException("Unexpected error")

            // When
            val result = repository.recommendTagFromPhoto(photoId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unexpected error"))
        }

    // ========== recommendPhotosFromTag Tests ==========

    @Test
    fun `recommendPhotosFromTag returns Success with photos`() =
        runTest {
            // Given
            val tagId = "tag1"
            val photos = listOf(createPhotoResponse("photo1"), createPhotoResponse("photo2"))
            coEvery { apiService.recommendPhotosFromTag(tagId) } returns Response.success(photos)

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertEquals(photos, (result as RecommendRepository.RecommendResult.Success).data)
            coVerify { apiService.recommendPhotosFromTag(tagId) }
        }

    @Test
    fun `recommendPhotosFromTag returns empty list when no photos recommended`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { apiService.recommendPhotosFromTag(tagId) } returns Response.success(emptyList())

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertTrue((result as RecommendRepository.RecommendResult.Success).data.isEmpty())
        }

    @Test
    fun `recommendPhotosFromTag returns Unauthorized on 401`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { apiService.recommendPhotosFromTag(tagId) } returns
                Response.error(401, "".toResponseBody())

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Unauthorized)
            assertEquals("Authentication failed", (result as RecommendRepository.RecommendResult.Unauthorized).message)
        }

    @Test
    fun `recommendPhotosFromTag returns BadRequest on 400`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { apiService.recommendPhotosFromTag(tagId) } returns
                Response.error(400, "".toResponseBody())

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.BadRequest)
            assertEquals("Bad request", (result as RecommendRepository.RecommendResult.BadRequest).message)
        }

    @Test
    fun `recommendPhotosFromTag returns Error on 404`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { apiService.recommendPhotosFromTag(tagId) } returns
                Response.error(404, "".toResponseBody())

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unknown error"))
        }

    @Test
    fun `recommendPhotosFromTag returns NetworkError on IOException`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { apiService.recommendPhotosFromTag(tagId) } throws IOException("Network error")

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.NetworkError)
            assertTrue((result as RecommendRepository.RecommendResult.NetworkError).message.contains("Network error"))
        }

    @Test
    fun `recommendPhotosFromTag returns Error on generic Exception`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { apiService.recommendPhotosFromTag(tagId) } throws RuntimeException("Unexpected error")

            // When
            val result = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unexpected error"))
        }

    // ========== recommendPhotosFromPhotos Tests ==========

    @Test
    fun `recommendPhotosFromPhotos returns Success with photos`() =
        runTest {
            // Given
            val photoIds = listOf("photo1", "photo2")
            val recommendedPhotos = listOf(createPhotoResponse("photo3"), createPhotoResponse("photo4"))
            coEvery { apiService.recommendPhotosFromPhotos(any()) } returns Response.success(recommendedPhotos)

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertEquals(recommendedPhotos, (result as RecommendRepository.RecommendResult.Success).data)
            coVerify {
                apiService.recommendPhotosFromPhotos(
                    match { it.photos == photoIds },
                )
            }
        }

    @Test
    fun `recommendPhotosFromPhotos returns empty list when no photos recommended`() =
        runTest {
            // Given
            val photoIds = listOf("photo1")
            coEvery { apiService.recommendPhotosFromPhotos(any()) } returns Response.success(emptyList())

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            assertTrue((result as RecommendRepository.RecommendResult.Success).data.isEmpty())
        }

    @Test
    fun `recommendPhotosFromPhotos handles empty input list`() =
        runTest {
            // Given
            val photoIds = emptyList<String>()
            coEvery { apiService.recommendPhotosFromPhotos(any()) } returns Response.success(emptyList())

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Success)
            coVerify {
                apiService.recommendPhotosFromPhotos(
                    match { it.photos.isEmpty() },
                )
            }
        }

    @Test
    fun `recommendPhotosFromPhotos returns Unauthorized on 401`() =
        runTest {
            // Given
            val photoIds = listOf("photo1")
            coEvery { apiService.recommendPhotosFromPhotos(any()) } returns
                Response.error(401, "".toResponseBody())

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Unauthorized)
            assertEquals("Authentication failed", (result as RecommendRepository.RecommendResult.Unauthorized).message)
        }

    @Test
    fun `recommendPhotosFromPhotos returns BadRequest on 400`() =
        runTest {
            // Given
            val photoIds = listOf("photo1")
            coEvery { apiService.recommendPhotosFromPhotos(any()) } returns
                Response.error(400, "".toResponseBody())

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.BadRequest)
            assertEquals("Bad request", (result as RecommendRepository.RecommendResult.BadRequest).message)
        }

    @Test
    fun `recommendPhotosFromPhotos returns Error on 404`() =
        runTest {
            // Given
            val photoIds = listOf("photo1")
            coEvery { apiService.recommendPhotosFromPhotos(any()) } returns
                Response.error(404, "".toResponseBody())

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unknown error"))
        }

    @Test
    fun `recommendPhotosFromPhotos returns NetworkError on IOException`() =
        runTest {
            // Given
            val photoIds = listOf("photo1")
            coEvery { apiService.recommendPhotosFromPhotos(any()) } throws IOException("Network error")

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.NetworkError)
            assertTrue((result as RecommendRepository.RecommendResult.NetworkError).message.contains("Network error"))
        }

    @Test
    fun `recommendPhotosFromPhotos returns Error on generic Exception`() =
        runTest {
            // Given
            val photoIds = listOf("photo1")
            coEvery { apiService.recommendPhotosFromPhotos(any()) } throws RuntimeException("Unexpected error")

            // When
            val result = repository.recommendPhotosFromPhotos(photoIds)

            // Then
            assertTrue(result is RecommendRepository.RecommendResult.Error)
            assertTrue((result as RecommendRepository.RecommendResult.Error).message.contains("unexpected error"))
        }

    // ========== getStories Tests ==========

    @Test
    fun `getStories returns Success with stories when status is ready`() =
        runTest {
            // Given
            val stories = listOf(createStoryResponse(), createStoryResponse("photo2", 200L))
            val storyStateResponse = createStoryStateResponse(status = "ready", stories = stories)
            coEvery { apiService.getStories(null) } returns Response.success(storyStateResponse)

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.Success)
            val successResult = result as RecommendRepository.StoryResult.Success
            assertEquals("ready", successResult.data.status)
            assertEquals(2, successResult.data.stories.size)
            coVerify { apiService.getStories(null) }
        }

    @Test
    fun `getStories returns Success with custom size parameter`() =
        runTest {
            // Given
            val size = 5
            val stories = listOf(createStoryResponse())
            val storyStateResponse = createStoryStateResponse(status = "ready", stories = stories)
            coEvery { apiService.getStories(size) } returns Response.success(storyStateResponse)

            // When
            val result = repository.getStories(size)

            // Then
            assertTrue(result is RecommendRepository.StoryResult.Success)
            coVerify { apiService.getStories(size) }
        }

    @Test
    fun `getStories returns Success with empty stories list`() =
        runTest {
            // Given
            val storyStateResponse = createStoryStateResponse(status = "ready", stories = emptyList())
            coEvery { apiService.getStories(null) } returns Response.success(storyStateResponse)

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.Success)
            val successResult = result as RecommendRepository.StoryResult.Success
            assertTrue(successResult.data.stories.isEmpty())
        }

    @Test
    fun `getStories returns Success when status is processing`() =
        runTest {
            // Given
            val storyStateResponse = createStoryStateResponse(status = "processing", stories = emptyList())
            coEvery { apiService.getStories(null) } returns Response.success(storyStateResponse)

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.Success)
            assertEquals("processing", (result as RecommendRepository.StoryResult.Success).data.status)
        }

    @Test
    fun `getStories returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.getStories(null) } returns
                Response.error(401, "".toResponseBody())

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.Unauthorized)
            assertEquals("Authentication failed", (result as RecommendRepository.StoryResult.Unauthorized).message)
        }

    @Test
    fun `getStories returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.getStories(null) } returns
                Response.error(400, "".toResponseBody())

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.BadRequest)
            assertEquals("Bad request", (result as RecommendRepository.StoryResult.BadRequest).message)
        }

    @Test
    fun `getStories returns Error on 404`() =
        runTest {
            // Given
            coEvery { apiService.getStories(null) } returns
                Response.error(404, "".toResponseBody())

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.Error)
            assertTrue((result as RecommendRepository.StoryResult.Error).message.contains("unknown error"))
        }

    @Test
    fun `getStories returns Error on 500`() =
        runTest {
            // Given
            coEvery { apiService.getStories(null) } returns
                Response.error(500, "".toResponseBody())

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.Error)
            assertTrue((result as RecommendRepository.StoryResult.Error).message.contains("unknown error"))
        }

    @Test
    fun `getStories returns NetworkError on IOException`() =
        runTest {
            // Given
            coEvery { apiService.getStories(null) } throws IOException("Network error")

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.NetworkError)
            assertTrue((result as RecommendRepository.StoryResult.NetworkError).message.contains("Network error"))
        }

    @Test
    fun `getStories returns Error on generic Exception`() =
        runTest {
            // Given
            coEvery { apiService.getStories(null) } throws RuntimeException("Unexpected error")

            // When
            val result = repository.getStories()

            // Then
            assertTrue(result is RecommendRepository.StoryResult.Error)
            assertTrue((result as RecommendRepository.StoryResult.Error).message.contains("unexpected error"))
        }

    // ========== Integration Tests ==========

    @Test
    fun `workflow - recommend tags then photos from selected tag`() =
        runTest {
            // Given - recommend tags for a photo
            val photoId = "photo1"
            val tags = listOf(createTag("tag1", "Sunset"), createTag("tag2", "Beach"))
            coEvery { apiService.recommendTagFromPhoto(photoId) } returns Response.success(tags)

            // When - get tag recommendations
            val tagResult = repository.recommendTagFromPhoto(photoId)
            assertTrue(tagResult is RecommendRepository.RecommendResult.Success)
            val tagId = (tagResult as RecommendRepository.RecommendResult.Success).data[0].tagId

            // Given - recommend photos from the selected tag
            val photos = listOf(createPhotoResponse("photo2"), createPhotoResponse("photo3"))
            coEvery { apiService.recommendPhotosFromTag(tagId) } returns Response.success(photos)

            // When - get photo recommendations from tag
            val photoResult = repository.recommendPhotosFromTag(tagId)

            // Then
            assertTrue(photoResult is RecommendRepository.RecommendResult.Success)
            assertEquals(2, (photoResult as RecommendRepository.RecommendResult.Success).data.size)
        }

    @Test
    fun `workflow - recommend photos from multiple photos then get stories`() =
        runTest {
            // Given - recommend photos from selected photos
            val selectedPhotoIds = listOf("photo1", "photo2")
            val recommendedPhotos = listOf(createPhotoResponse("photo3"), createPhotoResponse("photo4"))
            coEvery { apiService.recommendPhotosFromPhotos(any()) } returns Response.success(recommendedPhotos)

            // When - get photo recommendations
            val photoResult = repository.recommendPhotosFromPhotos(selectedPhotoIds)
            assertTrue(photoResult is RecommendRepository.RecommendResult.Success)

            // Given - get stories
            val storyStateResponse = createStoryStateResponse(status = "ready", stories = listOf(createStoryResponse()))
            coEvery { apiService.getStories(null) } returns Response.success(storyStateResponse)

            // When - get stories
            val storyResult = repository.getStories()

            // Then
            assertTrue(storyResult is RecommendRepository.StoryResult.Success)
            assertEquals("ready", (storyResult as RecommendRepository.StoryResult.Success).data.status)
        }

    @Test
    fun `all methods handle different error types correctly`() =
        runTest {
            // Setup - different error scenarios
            coEvery { apiService.recommendTagFromPhoto("photo1") } returns Response.error(401, "".toResponseBody())
            coEvery { apiService.recommendPhotosFromTag("tag1") } returns Response.error(400, "".toResponseBody())
            coEvery { apiService.recommendPhotosFromPhotos(any()) } throws IOException("Network error")
            coEvery { apiService.getStories(null) } throws RuntimeException("Unexpected error")

            // When
            val tagResult = repository.recommendTagFromPhoto("photo1")
            val photoFromTagResult = repository.recommendPhotosFromTag("tag1")
            val photoFromPhotosResult = repository.recommendPhotosFromPhotos(listOf("photo1"))
            val storyResult = repository.getStories()

            // Then
            assertTrue(tagResult is RecommendRepository.RecommendResult.Unauthorized)
            assertTrue(photoFromTagResult is RecommendRepository.RecommendResult.BadRequest)
            assertTrue(photoFromPhotosResult is RecommendRepository.RecommendResult.NetworkError)
            assertTrue(storyResult is RecommendRepository.StoryResult.Error)
        }

    @Test
    fun `all success scenarios return correct data`() =
        runTest {
            // Given
            val tags = listOf(createTag("tag1"))
            val photos = listOf(createPhotoResponse("photo1"))
            val storyStateResponse = createStoryStateResponse(status = "ready", stories = listOf(createStoryResponse()))

            coEvery { apiService.recommendTagFromPhoto(any()) } returns Response.success(tags)
            coEvery { apiService.recommendPhotosFromTag(any()) } returns Response.success(photos)
            coEvery { apiService.recommendPhotosFromPhotos(any()) } returns Response.success(photos)
            coEvery { apiService.getStories(any()) } returns Response.success(storyStateResponse)

            // When
            val tagResult = repository.recommendTagFromPhoto("photo1")
            val photoFromTagResult = repository.recommendPhotosFromTag("tag1")
            val photoFromPhotosResult = repository.recommendPhotosFromPhotos(listOf("photo1"))
            val storyResult = repository.getStories(5)

            // Then
            assertTrue(tagResult is RecommendRepository.RecommendResult.Success)
            assertEquals(tags, (tagResult as RecommendRepository.RecommendResult.Success).data)

            assertTrue(photoFromTagResult is RecommendRepository.RecommendResult.Success)
            assertEquals(photos, (photoFromTagResult as RecommendRepository.RecommendResult.Success).data)

            assertTrue(photoFromPhotosResult is RecommendRepository.RecommendResult.Success)
            assertEquals(photos, (photoFromPhotosResult as RecommendRepository.RecommendResult.Success).data)

            assertTrue(storyResult is RecommendRepository.StoryResult.Success)
            assertEquals("ready", (storyResult as RecommendRepository.StoryResult.Success).data.status)
        }
}
