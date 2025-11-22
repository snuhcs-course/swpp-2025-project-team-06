package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.ImageContext
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.Tag
import com.example.momentag.model.TagId
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageDetailViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: ImageDetailViewModel
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var recommendRepository: RecommendRepository

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        imageBrowserRepository = mockk()
        remoteRepository = mockk()
        recommendRepository = mockk()

        viewModel = ImageDetailViewModel(imageBrowserRepository, remoteRepository, recommendRepository)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Uri::class)
    }

    private fun createMockUri(path: String): Uri {
        val uri = mockk<Uri>(relaxed = true)
        every { uri.toString() } returns path
        every { uri.lastPathSegment } returns path.substringAfterLast("/")
        return uri
    }

    private fun createTag(
        id: String = "tag1",
        name: String = "Test Tag",
    ) = Tag(
        tagId = id,
        tagName = name,
    )

    // Load image context tests
    @Test
    fun `loadImageContext loads context from imageBrowserRepository`() {
        // Given
        val photoId = "photo1"
        val context = ImageContext(images = emptyList(), currentIndex = 0, contextType = ImageContext.ContextType.SearchResult("query"))
        every { imageBrowserRepository.getPhotoContext(photoId) } returns context

        // When
        viewModel.loadImageContext(photoId)

        // Then
        assertEquals(context, viewModel.imageContext.value)
    }

    @Test
    fun `loadImageContextByUri loads context by URI`() {
        // Given
        val uri = createMockUri("content://media/external/images/media/1")
        every { Uri.parse("content://media/external/images/media/1") } returns uri
        val context = ImageContext(images = emptyList(), currentIndex = 0, contextType = ImageContext.ContextType.Gallery)
        every { imageBrowserRepository.getPhotoContextByUri(uri) } returns context

        // When
        viewModel.loadImageContextByUri(uri)

        // Then
        assertEquals(context, viewModel.imageContext.value)
    }

    // Load photo tags tests
    @Test
    fun `loadPhotoTags with empty photoId updates state to Idle`() =
        runTest {
            // When
            viewModel.loadPhotoTags("")
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.imageDetailTagState.value is ImageDetailViewModel.ImageDetailTagState.Idle)
            assertNull(viewModel.photoAddress.value)
        }

    @Test
    fun `loadPhotoTags success loads existing tags`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tags = listOf(createTag("tag1", "Tag 1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = tags, address = "123 Main St")
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(photoId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Success)
            assertEquals(tags, (state as ImageDetailViewModel.ImageDetailTagState.Success).existingTags)
            assertEquals("123 Main St", viewModel.photoAddress.value)
        }

    @Test
    fun `loadPhotoTags error updates state with error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val errorMessage = "Network error"
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.ImageDetailTagState.Error).error)
        }

    // Delete tag tests
    @Test
    fun `deleteTagFromPhoto success updates state to Success`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagId = "tag1"
            coEvery { remoteRepository.removeTagFromPhoto(photoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)

            // When
            viewModel.deleteTagFromPhoto(photoId, tagId)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagDeleteState.value is ImageDetailViewModel.TagDeleteState.Success)
        }

    @Test
    fun `deleteTagFromPhoto error updates state with error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagId = "tag1"
            val errorMessage = "Failed to delete"
            coEvery { remoteRepository.removeTagFromPhoto(photoId, tagId) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.deleteTagFromPhoto(photoId, tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is ImageDetailViewModel.TagDeleteState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `resetDeleteState resets to Idle`() {
        // When
        viewModel.resetDeleteState()

        // Then
        assertTrue(viewModel.tagDeleteState.value is ImageDetailViewModel.TagDeleteState.Idle)
    }

    // Add tag tests
    @Test
    fun `addTagToPhoto success creates tag and associates it`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val tagId = "new-tag-id"
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = emptyList(), address = null)
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(photoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(photoId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagAddState.value is ImageDetailViewModel.TagAddState.Success)
        }

    @Test
    fun `addTagToPhoto with tag creation failure updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val errorMessage = "Failed to create tag"
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `resetAddState resets to Idle`() {
        // When
        viewModel.resetAddState()

        // Then
        assertTrue(viewModel.tagAddState.value is ImageDetailViewModel.TagAddState.Idle)
    }

    // Clear context test
    @Test
    fun `clearImageContext clears context and resets tag state`() {
        // Given
        val photoId = "photo1"
        val context = ImageContext(images = emptyList(), currentIndex = 0, contextType = ImageContext.ContextType.Gallery)
        every { imageBrowserRepository.getPhotoContext(photoId) } returns context
        viewModel.loadImageContext(photoId)

        // When
        viewModel.clearImageContext()

        // Then
        assertNull(viewModel.imageContext.value)
        assertTrue(viewModel.imageDetailTagState.value is ImageDetailViewModel.ImageDetailTagState.Idle)
    }

    // Additional loadPhotoTags error case tests
    @Test
    fun `loadPhotoTags unauthorized updates state with error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val errorMessage = "Unauthorized"
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Unauthorized(errorMessage)

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.Unauthorized, (state as ImageDetailViewModel.ImageDetailTagState.Error).error)
        }

    @Test
    fun `loadPhotoTags bad request updates state with error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val errorMessage = "Bad request"
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.ImageDetailTagState.Error).error)
        }

    @Test
    fun `loadPhotoTags network error updates state with error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val errorMessage = "Network error"
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.NetworkError, (state as ImageDetailViewModel.ImageDetailTagState.Error).error)
        }

    @Test
    fun `loadPhotoTags exception updates state with error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val exception = Exception("Unexpected error")
            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.ImageDetailTagState.Error).error)
        }

    @Test
    fun `loadPhotoTags with numeric photoId converts to UUID`() =
        runTest {
            // Given
            val photoPathId = "123"
            val actualPhotoId = "uuid-123"
            val photoResponse = PhotoResponse(photoId = actualPhotoId, photoPathId = 123L, createdAt = "2025-01-01")
            val photoDetail = PhotoDetailResponse(photoPathId = 123L, tags = emptyList(), address = null)

            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(listOf(photoResponse))
            coEvery { remoteRepository.getPhotoDetail(actualPhotoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(actualPhotoId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            // When
            viewModel.loadPhotoTags(photoPathId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Success)
        }

    @Test
    fun `loadPhotoTags with numeric photoId not found returns empty tags`() =
        runTest {
            // Given
            val photoPathId = "999"

            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(emptyList())

            // When
            viewModel.loadPhotoTags(photoPathId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Success)
            assertEquals(emptyList<Tag>(), (state as ImageDetailViewModel.ImageDetailTagState.Success).existingTags)
            assertEquals(emptyList<String>(), state.recommendedTags)
            assertNull(viewModel.photoAddress.value)
        }

    @Test
    fun `loadPhotoTags loads recommended tags and filters existing ones`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val existingTags = listOf(createTag("tag1", "Existing Tag"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = existingTags, address = null)
            val recommendedTags = listOf(createTag("tag1", "Existing Tag"), createTag("tag2", "New Tag"), createTag("tag3", "Another Tag"))

            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(photoId) } returns
                RecommendRepository.RecommendResult.Success(recommendedTags)

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Success)
            assertEquals(existingTags, (state as ImageDetailViewModel.ImageDetailTagState.Success).existingTags)
            // Should only show first recommended tag that's not in existing tags
            assertEquals(listOf("New Tag"), state.recommendedTags)
            assertFalse(state.isRecommendedLoading)
        }

    @Test
    fun `loadPhotoTags with recommendation error still shows existing tags`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val existingTags = listOf(createTag("tag1", "Tag 1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = existingTags, address = "123 Main St")

            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(photoId) } returns
                RecommendRepository.RecommendResult.Error("Recommendation failed")

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Success)
            assertEquals(existingTags, (state as ImageDetailViewModel.ImageDetailTagState.Success).existingTags)
            assertEquals(emptyList<String>(), state.recommendedTags)
            assertFalse(state.isRecommendedLoading)
        }

    @Test
    fun `loadPhotoTags with recommendation unauthorized still shows existing tags`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val existingTags = listOf(createTag("tag1", "Tag 1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = existingTags, address = null)

            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(photoId) } returns
                RecommendRepository.RecommendResult.Unauthorized("Unauthorized")

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Success)
            assertEquals(existingTags, (state as ImageDetailViewModel.ImageDetailTagState.Success).existingTags)
            assertFalse(state.isRecommendedLoading)
        }

    @Test
    fun `loadPhotoTags with recommendation bad request still shows existing tags`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val existingTags = listOf(createTag("tag1", "Tag 1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = existingTags, address = null)

            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(photoId) } returns
                RecommendRepository.RecommendResult.BadRequest("Bad request")

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Success)
            assertEquals(existingTags, (state as ImageDetailViewModel.ImageDetailTagState.Success).existingTags)
            assertFalse(state.isRecommendedLoading)
        }

    @Test
    fun `loadPhotoTags with recommendation network error still shows existing tags`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val existingTags = listOf(createTag("tag1", "Tag 1"))
            val photoDetail = PhotoDetailResponse(photoPathId = 1L, tags = existingTags, address = null)

            coEvery { remoteRepository.getPhotoDetail(photoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(photoId) } returns
                RecommendRepository.RecommendResult.NetworkError("Network error")

            // When
            viewModel.loadPhotoTags(photoId)
            advanceUntilIdle()

            // Then
            val state = viewModel.imageDetailTagState.value
            assertTrue(state is ImageDetailViewModel.ImageDetailTagState.Success)
            assertEquals(existingTags, (state as ImageDetailViewModel.ImageDetailTagState.Success).existingTags)
            assertFalse(state.isRecommendedLoading)
        }

    // Additional deleteTagFromPhoto tests
    @Test
    fun `deleteTagFromPhoto with numeric photoId converts to UUID`() =
        runTest {
            // Given
            val photoPathId = "456"
            val actualPhotoId = "uuid-456"
            val tagId = "tag1"
            val photoResponse = PhotoResponse(photoId = actualPhotoId, photoPathId = 456L, createdAt = "2025-01-01")

            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(listOf(photoResponse))
            coEvery { remoteRepository.removeTagFromPhoto(actualPhotoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)

            // When
            viewModel.deleteTagFromPhoto(photoPathId, tagId)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagDeleteState.value is ImageDetailViewModel.TagDeleteState.Success)
        }

    @Test
    fun `deleteTagFromPhoto with numeric photoId not found returns error`() =
        runTest {
            // Given
            val photoPathId = "999"
            val tagId = "tag1"

            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(emptyList())

            // When
            viewModel.deleteTagFromPhoto(photoPathId, tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is ImageDetailViewModel.TagDeleteState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.NotFound, (state as ImageDetailViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhoto unauthorized updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagId = "tag1"
            val errorMessage = "Unauthorized"
            coEvery { remoteRepository.removeTagFromPhoto(photoId, tagId) } returns
                RemoteRepository.Result.Unauthorized(errorMessage)

            // When
            viewModel.deleteTagFromPhoto(photoId, tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is ImageDetailViewModel.TagDeleteState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.Unauthorized, (state as ImageDetailViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhoto bad request updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagId = "tag1"
            val errorMessage = "Bad request"
            coEvery { remoteRepository.removeTagFromPhoto(photoId, tagId) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.deleteTagFromPhoto(photoId, tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is ImageDetailViewModel.TagDeleteState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhoto network error updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagId = "tag1"
            val errorMessage = "Network error"
            coEvery { remoteRepository.removeTagFromPhoto(photoId, tagId) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.deleteTagFromPhoto(photoId, tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is ImageDetailViewModel.TagDeleteState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.NetworkError, (state as ImageDetailViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhoto exception updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagId = "tag1"
            val exception = Exception("Unexpected error")
            coEvery { remoteRepository.removeTagFromPhoto(photoId, tagId) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.deleteTagFromPhoto(photoId, tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is ImageDetailViewModel.TagDeleteState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagDeleteState.Error).error)
        }

    // Additional addTagToPhoto tests
    @Test
    fun `addTagToPhoto with numeric photoId converts to UUID`() =
        runTest {
            // Given
            val photoPathId = "789"
            val actualPhotoId = "uuid-789"
            val tagName = "New Tag"
            val tagId = "new-tag-id"
            val photoResponse = PhotoResponse(photoId = actualPhotoId, photoPathId = 789L, createdAt = "2025-01-01")
            val photoDetail = PhotoDetailResponse(photoPathId = 789L, tags = emptyList(), address = null)

            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(listOf(photoResponse))
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(actualPhotoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)
            coEvery { remoteRepository.getPhotoDetail(actualPhotoId) } returns
                RemoteRepository.Result.Success(photoDetail)
            coEvery { recommendRepository.recommendTagFromPhoto(actualPhotoId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            // When
            viewModel.addTagToPhoto(photoPathId, tagName)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagAddState.value is ImageDetailViewModel.TagAddState.Success)
        }

    @Test
    fun `addTagToPhoto with numeric photoId not found returns error`() =
        runTest {
            // Given
            val photoPathId = "999"
            val tagName = "New Tag"

            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(emptyList())

            // When
            viewModel.addTagToPhoto(photoPathId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.NotFound, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag association error updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val tagId = "new-tag-id"
            val errorMessage = "Failed to associate tag"
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(photoId, tagId) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag association unauthorized updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val tagId = "new-tag-id"
            val errorMessage = "Unauthorized"
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(photoId, tagId) } returns
                RemoteRepository.Result.Unauthorized(errorMessage)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.Unauthorized, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag association bad request updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val tagId = "new-tag-id"
            val errorMessage = "Bad request"
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(photoId, tagId) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag association network error updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val tagId = "new-tag-id"
            val errorMessage = "Network error"
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(photoId, tagId) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.NetworkError, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag association exception updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val tagId = "new-tag-id"
            val exception = Exception("Unexpected error")
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Success(TagId(tagId))
            coEvery { remoteRepository.postTagsToPhoto(photoId, tagId) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag creation unauthorized updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val errorMessage = "Unauthorized"
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Unauthorized(errorMessage)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.Unauthorized, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag creation bad request updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val errorMessage = "Bad request"
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag creation network error updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val errorMessage = "Network error"
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.NetworkError, (state as ImageDetailViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addTagToPhoto with tag creation exception updates state to Error`() =
        runTest {
            // Given
            val photoId = "photo-uuid"
            val tagName = "New Tag"
            val exception = Exception("Unexpected error")
            coEvery { remoteRepository.postTags(tagName) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.addTagToPhoto(photoId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is ImageDetailViewModel.TagAddState.Error)
            assertEquals(ImageDetailViewModel.ImageDetailError.UnknownError, (state as ImageDetailViewModel.TagAddState.Error).error)
        }
}
