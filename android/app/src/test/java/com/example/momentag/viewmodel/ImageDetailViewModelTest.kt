package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.ImageContext
import com.example.momentag.model.ImageDetailTagState
import com.example.momentag.model.PhotoDetailResponse
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
        val context = ImageContext(images = emptyList(), currentIndex = 0, contextType = ImageContext.ContextType.SEARCH_RESULT)
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
        val context = ImageContext(images = emptyList(), currentIndex = 0, contextType = ImageContext.ContextType.GALLERY)
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
            assertTrue(viewModel.imageDetailTagState.value is ImageDetailTagState.Idle)
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
            assertTrue(state is ImageDetailTagState.Success)
            assertEquals(tags, (state as ImageDetailTagState.Success).existingTags)
            assertEquals("123 Main St", viewModel.photoAddress.value)
        }

    @Test
    fun `loadPhotoTags error updates state with error message`() =
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
            assertTrue(state is ImageDetailTagState.Error)
            assertEquals(errorMessage, (state as ImageDetailTagState.Error).message)
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
    fun `deleteTagFromPhoto error updates state with error message`() =
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
            assertEquals(errorMessage, (state as ImageDetailViewModel.TagDeleteState.Error).message)
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
        val context = ImageContext(images = emptyList(), currentIndex = 0, contextType = ImageContext.ContextType.GALLERY)
        every { imageBrowserRepository.getPhotoContext(photoId) } returns context
        viewModel.loadImageContext(photoId)

        // When
        viewModel.clearImageContext()

        // Then
        assertNull(viewModel.imageContext.value)
        assertTrue(viewModel.imageDetailTagState.value is ImageDetailTagState.Idle)
    }
}
