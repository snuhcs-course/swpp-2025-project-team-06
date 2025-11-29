package com.example.momentag.repository

import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.PhotoUploadData
import com.example.momentag.model.Tag
import com.example.momentag.model.TagId
import com.example.momentag.model.TagResponse
import com.example.momentag.model.TaskInfo
import com.example.momentag.network.ApiService
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteRepositoryTest {
    private lateinit var repository: RemoteRepository
    private lateinit var apiService: ApiService

    @Before
    fun setUp() {
        apiService = mockk()
        repository = RemoteRepository(apiService)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    // ========== Helper Functions ==========

    private fun createTag(
        id: String = "tag1",
        name: String = "TestTag",
    ) = Tag(
        tagId = id,
        tagName = name,
    )

    private fun createTagResponse(
        id: String = "tag1",
        name: String = "TestTag",
        thumbnailPhotoPathId: Long? = null,
        createdAt: String? = "2024-01-01T00:00:00Z",
        updatedAt: String? = "2024-01-01T00:00:00Z",
        photoCount: Int = 0,
    ) = TagResponse(
        tagId = id,
        tagName = name,
        thumbnailPhotoPathId = thumbnailPhotoPathId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        photoCount = photoCount,
    )

    private fun createPhotoResponse(id: String = "photo1") =
        PhotoResponse(
            photoId = id,
            photoPathId = 1L,
            createdAt = "2024-01-01T00:00:00Z",
        )

    private fun createPhotoDetailResponse() =
        PhotoDetailResponse(
            photoPathId = 1L,
            address = null,
            tags = listOf(createTag()),
        )

    private fun createTaskInfo(
        taskId: String = "task1",
        photoPathIds: List<Long> = listOf(1L, 2L),
    ) = TaskInfo(
        taskId = taskId,
        photoPathIds = photoPathIds,
    )

    // ========== getAllTags Tests ==========

    @Test
    fun `getAllTags returns Success with tags`() =
        runTest {
            // Given
            val tags = listOf(createTagResponse("tag1"), createTagResponse("tag2"))
            coEvery { apiService.getAllTags() } returns Response.success(tags)

            // When
            val result = repository.getAllTags()

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(tags, (result as RemoteRepository.Result.Success).data)
            coVerify { apiService.getAllTags() }
        }

    @Test
    fun `getAllTags returns Error when body is null`() =
        runTest {
            // Given
            coEvery { apiService.getAllTags() } returns Response.success(null)

            // When
            val result = repository.getAllTags()

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals("Response body is null", (result as RemoteRepository.Result.Error).message)
        }

    @Test
    fun `getAllTags returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.getAllTags() } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.getAllTags()

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
            assertEquals("Authentication failed", (result as RemoteRepository.Result.Unauthorized).message)
        }

    @Test
    fun `getAllTags returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.getAllTags() } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.getAllTags()

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
            assertEquals("Bad request", (result as RemoteRepository.Result.BadRequest).message)
        }

    @Test
    fun `getAllTags returns Error on other error codes`() =
        runTest {
            // Given
            coEvery { apiService.getAllTags() } returns Response.error(500, "".toResponseBody())

            // When
            val result = repository.getAllTags()

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals(500, (result as RemoteRepository.Result.Error).code)
        }

    @Test
    fun `getAllTags returns Exception on IOException`() =
        runTest {
            // Given
            coEvery { apiService.getAllTags() } throws IOException("Network error")

            // When
            val result = repository.getAllTags()

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
            assertTrue((result as RemoteRepository.Result.Exception).e is IOException)
        }

    @Test
    fun `getAllTags returns Exception on generic Exception`() =
        runTest {
            // Given
            coEvery { apiService.getAllTags() } throws Exception("Unknown error")

            // When
            val result = repository.getAllTags()

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== getAllPhotos Tests ==========

    @Test
    fun `getAllPhotos returns Success with photos`() =
        runTest {
            // Given
            val photos = listOf(createPhotoResponse("photo1"), createPhotoResponse("photo2"))
            coEvery { apiService.getAllPhotos(null, null) } returns Response.success(photos)

            // When
            val result = repository.getAllPhotos()

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(photos, (result as RemoteRepository.Result.Success).data)
        }

    @Test
    fun `getAllPhotos with pagination returns Success`() =
        runTest {
            // Given
            val photos = listOf(createPhotoResponse("photo1"))
            val limit = 10
            val offset = 5
            coEvery { apiService.getAllPhotos(limit, offset) } returns Response.success(photos)

            // When
            val result = repository.getAllPhotos(limit, offset)

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(photos, (result as RemoteRepository.Result.Success).data)
            coVerify { apiService.getAllPhotos(limit, offset) }
        }

    @Test
    fun `getAllPhotos returns Error when body is null`() =
        runTest {
            // Given
            coEvery { apiService.getAllPhotos(null, null) } returns Response.success(null)

            // When
            val result = repository.getAllPhotos()

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals("Response body is null", (result as RemoteRepository.Result.Error).message)
        }

    @Test
    fun `getAllPhotos returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.getAllPhotos(null, null) } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.getAllPhotos()

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `getAllPhotos returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.getAllPhotos(null, null) } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.getAllPhotos()

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
        }

    @Test
    fun `getAllPhotos returns Exception on IOException`() =
        runTest {
            // Given
            coEvery { apiService.getAllPhotos(null, null) } throws IOException("Network error")

            // When
            val result = repository.getAllPhotos()

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== getPhotoDetail Tests ==========

    @Test
    fun `getPhotoDetail returns Success with detail`() =
        runTest {
            // Given
            val photoDetail = createPhotoDetailResponse()
            coEvery { apiService.getPhotoDetail("photo1") } returns Response.success(photoDetail)

            // When
            val result = repository.getPhotoDetail("photo1")

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(photoDetail, (result as RemoteRepository.Result.Success).data)
        }

    @Test
    fun `getPhotoDetail returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.getPhotoDetail("photo1") } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.getPhotoDetail("photo1")

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `getPhotoDetail returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.getPhotoDetail("photo1") } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.getPhotoDetail("photo1")

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
        }

    @Test
    fun `getPhotoDetail returns NetworkError on IOException`() =
        runTest {
            // Given
            coEvery { apiService.getPhotoDetail("photo1") } throws IOException("Network error")

            // When
            val result = repository.getPhotoDetail("photo1")

            // Then
            assertTrue(result is RemoteRepository.Result.NetworkError)
            assertTrue((result as RemoteRepository.Result.NetworkError).message.contains("Network error"))
        }

    @Test
    fun `getPhotoDetail returns Exception on generic Exception`() =
        runTest {
            // Given
            coEvery { apiService.getPhotoDetail("photo1") } throws Exception("Unknown error")

            // When
            val result = repository.getPhotoDetail("photo1")

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== getPhotosByTag Tests ==========

    @Test
    fun `getPhotosByTag returns Success with photos`() =
        runTest {
            // Given
            val photos = listOf(createPhotoResponse("photo1"))
            coEvery { apiService.getPhotosByTag("tag1") } returns Response.success(photos)

            // When
            val result = repository.getPhotosByTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(photos, (result as RemoteRepository.Result.Success).data)
        }

    @Test
    fun `getPhotosByTag returns Error when body is null`() =
        runTest {
            // Given
            coEvery { apiService.getPhotosByTag("tag1") } returns Response.success(null)

            // When
            val result = repository.getPhotosByTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
        }

    @Test
    fun `getPhotosByTag returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.getPhotosByTag("tag1") } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.getPhotosByTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `getPhotosByTag returns Exception on IOException`() =
        runTest {
            // Given
            coEvery { apiService.getPhotosByTag("tag1") } throws IOException("Network error")

            // When
            val result = repository.getPhotosByTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== postTags Tests ==========

    @Test
    fun `postTags returns Success with tag response`() =
        runTest {
            // Given
            val tagResponse = TagId(id = "tag1")
            coEvery { apiService.postTags(any()) } returns Response.success(tagResponse)

            // When
            val result = repository.postTags("NewTag")

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(tagResponse, (result as RemoteRepository.Result.Success).data)
            coVerify { apiService.postTags(match { it.name == "NewTag" }) }
        }

    @Test
    fun `postTags returns Error when body is null`() =
        runTest {
            // Given
            coEvery { apiService.postTags(any()) } returns Response.success(null)

            // When
            val result = repository.postTags("NewTag")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
        }

    @Test
    fun `postTags returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.postTags(any()) } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.postTags("NewTag")

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `postTags returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.postTags(any()) } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.postTags("NewTag")

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
        }

    @Test
    fun `postTags returns Exception on IOException`() =
        runTest {
            // Given
            coEvery { apiService.postTags(any()) } throws IOException("Network error")

            // When
            val result = repository.postTags("NewTag")

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== uploadPhotos Tests ==========

    @Test
    fun `uploadPhotos returns Success with task info`() =
        runTest {
            // Given
            val photoUploadData =
                PhotoUploadData(
                    photo = listOf(mockk<MultipartBody.Part>()),
                    metadata = mockk<RequestBody>(),
                )
            val taskInfos = listOf(createTaskInfo("task1"), createTaskInfo("task2"))
            coEvery {
                apiService.uploadPhotos(any(), any())
            } returns Response.success(taskInfos)

            // When
            val result = repository.uploadPhotos(photoUploadData)

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(taskInfos, (result as RemoteRepository.Result.Success).data)
        }

    @Test
    fun `uploadPhotos returns Error when body is null`() =
        runTest {
            // Given
            val photoUploadData =
                PhotoUploadData(
                    photo = listOf(mockk<MultipartBody.Part>()),
                    metadata = mockk<RequestBody>(),
                )
            coEvery {
                apiService.uploadPhotos(any(), any())
            } returns Response.success(null)

            // When
            val result = repository.uploadPhotos(photoUploadData)

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals("Response body is null", (result as RemoteRepository.Result.Error).message)
        }

    @Test
    fun `uploadPhotos returns Unauthorized on 401`() =
        runTest {
            // Given
            val photoUploadData =
                PhotoUploadData(
                    photo = listOf(mockk<MultipartBody.Part>()),
                    metadata = mockk<RequestBody>(),
                )
            coEvery {
                apiService.uploadPhotos(any(), any())
            } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.uploadPhotos(photoUploadData)

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `uploadPhotos returns BadRequest on 400`() =
        runTest {
            // Given
            val photoUploadData =
                PhotoUploadData(
                    photo = listOf(mockk<MultipartBody.Part>()),
                    metadata = mockk<RequestBody>(),
                )
            coEvery {
                apiService.uploadPhotos(any(), any())
            } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.uploadPhotos(photoUploadData)

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
        }

    @Test
    fun `uploadPhotos returns Exception on IOException`() =
        runTest {
            // Given
            val photoUploadData =
                PhotoUploadData(
                    photo = listOf(mockk<MultipartBody.Part>()),
                    metadata = mockk<RequestBody>(),
                )
            coEvery {
                apiService.uploadPhotos(any(), any())
            } throws IOException("Network error")

            // When
            val result = repository.uploadPhotos(photoUploadData)

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
            assertTrue((result as RemoteRepository.Result.Exception).e is IOException)
        }

    @Test
    fun `uploadPhotos returns Exception on generic Exception`() =
        runTest {
            // Given
            val photoUploadData =
                PhotoUploadData(
                    photo = listOf(mockk<MultipartBody.Part>()),
                    metadata = mockk<RequestBody>(),
                )
            coEvery {
                apiService.uploadPhotos(any(), any())
            } throws Exception("Unknown error")

            // When
            val result = repository.uploadPhotos(photoUploadData)

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== removeTagFromPhoto Tests ==========

    @Test
    fun `removeTagFromPhoto returns Success on successful removal`() =
        runTest {
            // Given
            coEvery { apiService.removeTagFromPhoto("photo1", "tag1") } returns Response.success(Unit)

            // When
            val result = repository.removeTagFromPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(Unit, (result as RemoteRepository.Result.Success).data)
        }

    @Test
    fun `removeTagFromPhoto returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.removeTagFromPhoto("photo1", "tag1") } returns
                Response.error(401, "".toResponseBody())

            // When
            val result = repository.removeTagFromPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `removeTagFromPhoto returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.removeTagFromPhoto("photo1", "tag1") } returns
                Response.error(400, "".toResponseBody())

            // When
            val result = repository.removeTagFromPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
        }

    @Test
    fun `removeTagFromPhoto returns Error on 404`() =
        runTest {
            // Given
            coEvery { apiService.removeTagFromPhoto("photo1", "tag1") } returns
                Response.error(404, "".toResponseBody())

            // When
            val result = repository.removeTagFromPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals(404, (result as RemoteRepository.Result.Error).code)
            assertTrue(result.message.contains("not found"))
        }

    @Test
    fun `removeTagFromPhoto returns Exception on IOException`() =
        runTest {
            // Given
            coEvery { apiService.removeTagFromPhoto("photo1", "tag1") } throws IOException("Network error")

            // When
            val result = repository.removeTagFromPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== postTagsToPhoto Tests ==========

    @Test
    fun `postTagsToPhoto returns Success on successful post`() =
        runTest {
            // Given
            coEvery { apiService.postTagsToPhoto("photo1", any()) } returns Response.success(Unit)

            // When
            val result = repository.postTagsToPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            coVerify {
                apiService.postTagsToPhoto(
                    "photo1",
                    match {
                        it.size == 1 && it[0].id == "tag1"
                    },
                )
            }
        }

    @Test
    fun `postTagsToPhoto returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.postTagsToPhoto("photo1", any()) } returns
                Response.error(401, "".toResponseBody())

            // When
            val result = repository.postTagsToPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `postTagsToPhoto returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.postTagsToPhoto("photo1", any()) } returns
                Response.error(400, "".toResponseBody())

            // When
            val result = repository.postTagsToPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
        }

    @Test
    fun `postTagsToPhoto returns Error on 404`() =
        runTest {
            // Given
            coEvery { apiService.postTagsToPhoto("photo1", any()) } returns
                Response.error(404, "".toResponseBody())

            // When
            val result = repository.postTagsToPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals(404, (result as RemoteRepository.Result.Error).code)
        }

    @Test
    fun `postTagsToPhoto returns Exception on IOException`() =
        runTest {
            // Given
            coEvery { apiService.postTagsToPhoto("photo1", any()) } throws IOException("Network error")

            // When
            val result = repository.postTagsToPhoto("photo1", "tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== removeTag Tests ==========

    @Test
    fun `removeTag returns Success on successful removal`() =
        runTest {
            // Given
            coEvery { apiService.removeTag("tag1") } returns Response.success(Unit)

            // When
            val result = repository.removeTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(Unit, (result as RemoteRepository.Result.Success).data)
        }

    @Test
    fun `removeTag returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.removeTag("tag1") } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.removeTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `removeTag returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.removeTag("tag1") } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.removeTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
        }

    @Test
    fun `removeTag returns Error on 404`() =
        runTest {
            // Given
            coEvery { apiService.removeTag("tag1") } returns Response.error(404, "".toResponseBody())

            // When
            val result = repository.removeTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals(404, (result as RemoteRepository.Result.Error).code)
        }

    @Test
    fun `removeTag returns Exception on IOException`() =
        runTest {
            // Given
            coEvery { apiService.removeTag("tag1") } throws IOException("Network error")

            // When
            val result = repository.removeTag("tag1")

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== renameTag Tests ==========

    @Test
    fun `renameTag returns Success on successful rename`() =
        runTest {
            // Given
            val newTagId = TagId(id = "tag1")
            coEvery { apiService.renameTag("tag1", any()) } returns Response.success(newTagId)

            // When
            val result = repository.renameTag("tag1", "NewTagName")

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            assertEquals(newTagId, (result as RemoteRepository.Result.Success).data)
            coVerify { apiService.renameTag("tag1", match { it.name == "NewTagName" }) }
        }

    @Test
    fun `renameTag returns Error when body is null`() =
        runTest {
            // Given
            coEvery { apiService.renameTag("tag1", any()) } returns Response.success(null)

            // When
            val result = repository.renameTag("tag1", "NewName")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals("Response body is null", (result as RemoteRepository.Result.Error).message)
        }

    @Test
    fun `renameTag returns Unauthorized on 401`() =
        runTest {
            // Given
            coEvery { apiService.renameTag("tag1", any()) } returns Response.error(401, "".toResponseBody())

            // When
            val result = repository.renameTag("tag1", "NewName")

            // Then
            assertTrue(result is RemoteRepository.Result.Unauthorized)
        }

    @Test
    fun `renameTag returns BadRequest on 400`() =
        runTest {
            // Given
            coEvery { apiService.renameTag("tag1", any()) } returns Response.error(400, "".toResponseBody())

            // When
            val result = repository.renameTag("tag1", "NewName")

            // Then
            assertTrue(result is RemoteRepository.Result.BadRequest)
        }

    @Test
    fun `renameTag returns Error on 403 (forbidden)`() =
        runTest {
            // Given
            coEvery { apiService.renameTag("tag1", any()) } returns Response.error(403, "".toResponseBody())

            // When
            val result = repository.renameTag("tag1", "NewName")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals(403, (result as RemoteRepository.Result.Error).code)
            assertTrue(result.message.contains("forbidden"))
        }

    @Test
    fun `renameTag returns Error on 404 (not found)`() =
        runTest {
            // Given
            coEvery { apiService.renameTag("tag1", any()) } returns Response.error(404, "".toResponseBody())

            // When
            val result = repository.renameTag("tag1", "NewName")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals(404, (result as RemoteRepository.Result.Error).code)
            assertTrue(result.message.contains("not found"))
        }

    @Test
    fun `renameTag returns Error on 500`() =
        runTest {
            // Given
            coEvery { apiService.renameTag("tag1", any()) } returns Response.error(500, "".toResponseBody())

            // When
            val result = repository.renameTag("tag1", "NewName")

            // Then
            assertTrue(result is RemoteRepository.Result.Error)
            assertEquals(500, (result as RemoteRepository.Result.Error).code)
        }

    @Test
    fun `renameTag returns Exception on IOException`() =
        runTest {
            // Given
            coEvery { apiService.renameTag("tag1", any()) } throws IOException("Network error")

            // When
            val result = repository.renameTag("tag1", "NewName")

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    @Test
    fun `renameTag returns Exception on generic Exception`() =
        runTest {
            // Given
            coEvery { apiService.renameTag("tag1", any()) } throws Exception("Unknown error")

            // When
            val result = repository.renameTag("tag1", "NewName")

            // Then
            assertTrue(result is RemoteRepository.Result.Exception)
        }

    // ========== Integration Tests ==========

    @Test
    fun `workflow - create tag then add photos`() =
        runTest {
            // Given - create tag
            val tagResponse = TagId(id = "newTag")
            coEvery { apiService.postTags(any()) } returns Response.success(tagResponse)

            // When - create tag
            val createResult = repository.postTags("MyTag")
            assertTrue(createResult is RemoteRepository.Result.Success)
            val tagId = (createResult as RemoteRepository.Result.Success).data.id

            // Given - add tag to photo
            coEvery { apiService.postTagsToPhoto("photo1", any()) } returns Response.success(Unit)

            // When - add tag to photo
            val addResult = repository.postTagsToPhoto("photo1", tagId)

            // Then
            assertTrue(addResult is RemoteRepository.Result.Success)
        }

    @Test
    fun `workflow - get photos then get detail`() =
        runTest {
            // Given - get all photos
            val photos = listOf(createPhotoResponse("photo1"))
            coEvery { apiService.getAllPhotos(null, null) } returns Response.success(photos)

            // When - get all photos
            val photosResult = repository.getAllPhotos()
            assertTrue(photosResult is RemoteRepository.Result.Success)
            val photoId = (photosResult as RemoteRepository.Result.Success).data[0].photoId

            // Given - get photo detail
            val photoDetail = createPhotoDetailResponse()
            coEvery { apiService.getPhotoDetail(photoId) } returns Response.success(photoDetail)

            // When - get photo detail
            val detailResult = repository.getPhotoDetail(photoId)

            // Then
            assertTrue(detailResult is RemoteRepository.Result.Success)
        }

    @Test
    fun `multiple operations handle different error types`() =
        runTest {
            // Setup - success, error, exception
            coEvery { apiService.getAllTags() } returns Response.success(listOf(createTagResponse()))
            coEvery { apiService.getAllPhotos(null, null) } returns Response.error(401, "".toResponseBody())
            coEvery { apiService.getPhotoDetail(any()) } throws IOException("Network error")

            // When
            val tagsResult = repository.getAllTags()
            val photosResult = repository.getAllPhotos()
            val detailResult = repository.getPhotoDetail("photo1")

            // Then
            assertTrue(tagsResult is RemoteRepository.Result.Success)
            assertTrue(photosResult is RemoteRepository.Result.Unauthorized)
            assertTrue(detailResult is RemoteRepository.Result.NetworkError)
        }

    @Test
    fun `workflow - upload photos and get task info`() =
        runTest {
            // Given
            val photoUploadData =
                PhotoUploadData(
                    photo = listOf(mockk<MultipartBody.Part>()),
                    metadata = mockk<RequestBody>(),
                )
            val taskInfos = listOf(createTaskInfo("task1", listOf(1L, 2L)))
            coEvery { apiService.uploadPhotos(any(), any()) } returns Response.success(taskInfos)

            // When
            val result = repository.uploadPhotos(photoUploadData)

            // Then
            assertTrue(result is RemoteRepository.Result.Success)
            val uploadResult = result as RemoteRepository.Result.Success
            assertEquals(1, uploadResult.data.size)
            assertEquals("task1", uploadResult.data[0].taskId)
            assertEquals(listOf(1L, 2L), uploadResult.data[0].photoPathIds)
        }

    @Test
    fun `workflow - rename tag handling all error scenarios`() =
        runTest {
            // Success case
            val newTagId = TagId(id = "renamedTag")
            coEvery { apiService.renameTag("tag1", any()) } returns Response.success(newTagId)
            val successResult = repository.renameTag("tag1", "NewName")
            assertTrue(successResult is RemoteRepository.Result.Success)

            // Forbidden case (preset tag)
            coEvery { apiService.renameTag("presetTag", any()) } returns Response.error(403, "".toResponseBody())
            val forbiddenResult = repository.renameTag("presetTag", "NewName")
            assertTrue(forbiddenResult is RemoteRepository.Result.Error)
            assertEquals(403, (forbiddenResult as RemoteRepository.Result.Error).code)

            // Not found case
            coEvery { apiService.renameTag("nonExistent", any()) } returns Response.error(404, "".toResponseBody())
            val notFoundResult = repository.renameTag("nonExistent", "NewName")
            assertTrue(notFoundResult is RemoteRepository.Result.Error)
            assertEquals(404, (notFoundResult as RemoteRepository.Result.Error).code)
        }
}
