package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: AlbumViewModel
    private lateinit var localRepository: LocalRepository
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var recommendRepository: RecommendRepository
    private lateinit var imageBrowserRepository: ImageBrowserRepository
    private lateinit var photoSelectionRepository: PhotoSelectionRepository

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        localRepository = mockk()
        remoteRepository = mockk()
        recommendRepository = mockk()
        imageBrowserRepository = mockk(relaxed = true)
        photoSelectionRepository = mockk(relaxed = true)

        viewModel =
            AlbumViewModel(
                localRepository,
                remoteRepository,
                recommendRepository,
                imageBrowserRepository,
                photoSelectionRepository,
            )
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

    private fun createPhotoResponse(id: String = "photo1") =
        PhotoResponse(
            photoId = id,
            photoPathId = 1L,
            createdAt = "2025-01-01",
        )

    private fun createPhoto(id: String = "photo1"): Photo {
        val uri = createMockUri("content://media/external/images/media/$id")
        every { Uri.parse("content://media/external/images/media/$id") } returns uri
        return Photo(
            photoId = id,
            contentUri = uri,
            createdAt = "2025-01-01",
        )
    }

    // Load album tests
    @Test
    fun `loadAlbum success loads photos and recommendations`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "Test Tag"
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            val recommendPhotoResponses = listOf(createPhotoResponse("photo2"))
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(recommendPhotoResponses)
            coEvery { localRepository.toPhotos(recommendPhotoResponses) } returns emptyList()

            // When
            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Success)
            assertEquals(photos, (state as AlbumViewModel.AlbumLoadingState.Success).photos)
            verify { imageBrowserRepository.setTagAlbum(photos, tagName) }
        }

    @Test
    fun `loadAlbum error updates state with error message`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "Test Tag"
            val errorMessage = "Network error"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    // Load recommendations tests
    @Test
    fun `loadRecommendations success loads recommended photos`() =
        runTest {
            // Given
            val tagId = "tag1"
            val photoResponses = listOf(createPhotoResponse())
            val photos = listOf(createPhoto())
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos

            // When
            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Success)
            assertEquals(photos, (state as AlbumViewModel.RecommendLoadingState.Success).photos)
        }

    // Photo selection tests
    @Test
    fun `toggleRecommendPhoto toggles photo selection`() {
        // Given
        val photo = createPhoto()

        // When - select photo
        viewModel.toggleRecommendPhoto(photo)

        // Then
        assertTrue(viewModel.selectedRecommendPhotos.value.contains(photo))

        // When - deselect photo
        viewModel.toggleRecommendPhoto(photo)

        // Then
        assertFalse(viewModel.selectedRecommendPhotos.value.contains(photo))
    }

    @Test
    fun `resetRecommendSelection clears selection`() {
        // Given
        viewModel.toggleRecommendPhoto(createPhoto())

        // When
        viewModel.resetRecommendSelection()

        // Then
        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
    }

    @Test
    fun `toggleTagAlbumPhoto toggles photo selection`() {
        // Given
        val photo = createPhoto()

        // When - select photo
        viewModel.toggleTagAlbumPhoto(photo)

        // Then
        assertTrue(viewModel.selectedTagAlbumPhotos.value.contains(photo))

        // When - deselect photo
        viewModel.toggleTagAlbumPhoto(photo)

        // Then
        assertFalse(viewModel.selectedTagAlbumPhotos.value.contains(photo))
    }

    // Delete tag tests
    @Test
    fun `resetDeleteState resets to Idle`() =
        runTest {
            // When
            viewModel.resetDeleteState()

            // Then
            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Idle)
        }

    // Rename tag tests
    @Test
    fun `resetRenameState resets to Idle`() =
        runTest {
            // When
            viewModel.resetRenameState()

            // Then
            assertTrue(viewModel.tagRenameState.value is AlbumViewModel.TagRenameState.Idle)
        }

    // Add state tests
    @Test
    fun `resetAddState resets to Idle`() =
        runTest {
            // When
            viewModel.resetAddState()

            // Then
            assertTrue(viewModel.tagAddState.value is AlbumViewModel.TagAddState.Idle)
        }

    // Initialize add photos flow
    @Test
    fun `initializeAddPhotosFlow initializes photoSelectionRepository`() {
        // Given
        val tagId = "tag1"
        val tagName = "Test Tag"

        // When
        viewModel.initializeAddPhotosFlow(tagId, tagName)

        // Then
        verify {
            photoSelectionRepository.initialize(
                initialTagName = tagName,
                initialPhotos = emptyList(),
                existingTagId = tagId,
            )
        }
    }

    @Test
    fun `loadRecommendations error updates state with error message`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Error("Error")

            // When
            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.RecommendLoadingState.Error).error)
        }

    @Test
    fun `resetTagAlbumPhotoSelection clears selection`() {
        // Given
        viewModel.toggleTagAlbumPhoto(createPhoto())

        // When
        viewModel.resetTagAlbumPhotoSelection()

        // Then
        assertTrue(viewModel.selectedTagAlbumPhotos.value.isEmpty())
    }

    @Test
    fun `deleteTagFromPhotos success updates state to Success`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            coEvery { remoteRepository.removeTagFromPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)

            // When
            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Success)
        }

    @Test
    fun `deleteTagFromPhotos error updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val errorMessage = "Error deleting tag"
            coEvery { remoteRepository.removeTagFromPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is AlbumViewModel.TagDeleteState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `getPhotosToShare returns selected photos`() {
        // Given
        val photo1 = createPhoto("photo1")
        val photo2 = createPhoto("photo2")
        viewModel.toggleTagAlbumPhoto(photo1)
        viewModel.toggleTagAlbumPhoto(photo2)

        // When
        val photosToShare = viewModel.getPhotosToShare()

        // Then
        assertEquals(listOf(photo1, photo2), photosToShare)
    }

    // Additional loadAlbum error case tests
    @Test
    fun `loadAlbum unauthorized updates state with error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "Test Tag"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Unauthorized("Unauthorized")

            // When
            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.Unauthorized, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    @Test
    fun `loadAlbum network error updates state with error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "Test Tag"
            val errorMessage = "Connection failed"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.NetworkError, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    @Test
    fun `loadAlbum bad request updates state with error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "Test Tag"
            val errorMessage = "Invalid tag ID"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    @Test
    fun `loadAlbum exception updates state with error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "Test Tag"
            val exception = Exception("Unexpected error")
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    // Additional loadRecommendations error case tests
    @Test
    fun `loadRecommendations unauthorized updates state with error`() =
        runTest {
            // Given
            val tagId = "tag1"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Unauthorized("Unauthorized")

            // When
            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.Unauthorized, (state as AlbumViewModel.RecommendLoadingState.Error).error)
        }

    @Test
    fun `loadRecommendations network error updates state with error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val errorMessage = "Connection failed"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.NetworkError(errorMessage)

            // When
            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.NetworkError, (state as AlbumViewModel.RecommendLoadingState.Error).error)
        }

    @Test
    fun `loadRecommendations bad request updates state with error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val errorMessage = "Invalid request"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.BadRequest(errorMessage)

            // When
            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.RecommendLoadingState.Error).error)
        }

    // deleteTagFromPhotos with photo path ID conversion tests
    @Test
    fun `deleteTagFromPhotos with numeric photo ID converts to UUID`() =
        runTest {
            // Given
            val photo = createPhoto("123")
            val tagId = "tag1"
            val actualPhotoId = "uuid-123"
            val photoResponse = createPhotoResponse(actualPhotoId).copy(photoPathId = 123L)

            // Setup for delete
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(listOf(photoResponse))
            coEvery { remoteRepository.removeTagFromPhoto(actualPhotoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)

            // When
            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Success)
        }

    @Test
    fun `deleteTagFromPhotos with numeric photo ID not found returns error`() =
        runTest {
            // Given
            val photo = createPhoto("999")
            val tagId = "tag1"

            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(emptyList())

            // When
            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is AlbumViewModel.TagDeleteState.Error)
            assertEquals(AlbumViewModel.AlbumError.NotFound, (state as AlbumViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhotos unauthorized updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val errorMessage = "Unauthorized"
            coEvery { remoteRepository.removeTagFromPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.Unauthorized(errorMessage)

            // When
            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is AlbumViewModel.TagDeleteState.Error)
            assertEquals(AlbumViewModel.AlbumError.Unauthorized, (state as AlbumViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhotos bad request updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val errorMessage = "Bad request"
            coEvery { remoteRepository.removeTagFromPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is AlbumViewModel.TagDeleteState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhotos network error updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val errorMessage = "Network error"
            coEvery { remoteRepository.removeTagFromPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is AlbumViewModel.TagDeleteState.Error)
            assertEquals(AlbumViewModel.AlbumError.NetworkError, (state as AlbumViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhotos exception updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val exception = Exception("Unexpected error")
            coEvery { remoteRepository.removeTagFromPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagDeleteState.value
            assertTrue(state is AlbumViewModel.TagDeleteState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagDeleteState.Error).error)
        }

    @Test
    fun `deleteTagFromPhotos updates album state after successful deletion`() =
        runTest {
            // Given
            val photo1 = createPhoto("photo1")
            val photo2 = createPhoto("photo2")
            val tagId = "tag1"
            val tagName = "Test Tag"

            // First load the album
            val photoResponses = listOf(createPhotoResponse("photo1"), createPhotoResponse("photo2"))
            val photos = listOf(photo1, photo2)
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(photoResponses)
            coEvery { localRepository.toPhotos(photoResponses) } returns photos
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for delete
            coEvery { remoteRepository.removeTagFromPhoto(photo1.photoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)

            // When
            viewModel.deleteTagFromPhotos(listOf(photo1), tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Success)
            val remainingPhotos = (state as AlbumViewModel.AlbumLoadingState.Success).photos
            assertEquals(1, remainingPhotos.size)
            assertEquals(photo2, remainingPhotos[0])
        }

    // renameTag tests
    @Test
    fun `renameTag success updates state to Success`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "New Tag Name"
            val tagIdResponse =
                com.example.momentag.model
                    .TagId(id = tagId)
            coEvery { remoteRepository.renameTag(tagId, tagName) } returns
                RemoteRepository.Result.Success(tagIdResponse)

            // When
            viewModel.renameTag(tagId, tagName)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagRenameState.value is AlbumViewModel.TagRenameState.Success)
        }

    @Test
    fun `renameTag with mismatched ID returns error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "New Tag Name"
            val tagIdResponse =
                com.example.momentag.model
                    .TagId(id = "different-tag-id")
            coEvery { remoteRepository.renameTag(tagId, tagName) } returns
                RemoteRepository.Result.Success(tagIdResponse)

            // When
            viewModel.renameTag(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagRenameState.value
            assertTrue(state is AlbumViewModel.TagRenameState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagRenameState.Error).error)
        }

    @Test
    fun `renameTag error updates state to Error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "New Tag Name"
            val errorMessage = "Server error"
            coEvery { remoteRepository.renameTag(tagId, tagName) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.renameTag(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagRenameState.value
            assertTrue(state is AlbumViewModel.TagRenameState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagRenameState.Error).error)
        }

    @Test
    fun `renameTag unauthorized updates state to Error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "New Tag Name"
            val errorMessage = "Unauthorized"
            coEvery { remoteRepository.renameTag(tagId, tagName) } returns
                RemoteRepository.Result.Unauthorized(errorMessage)

            // When
            viewModel.renameTag(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagRenameState.value
            assertTrue(state is AlbumViewModel.TagRenameState.Error)
            assertEquals(AlbumViewModel.AlbumError.Unauthorized, (state as AlbumViewModel.TagRenameState.Error).error)
        }

    @Test
    fun `renameTag bad request updates state to Error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "New Tag Name"
            val errorMessage = "Bad request"
            coEvery { remoteRepository.renameTag(tagId, tagName) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.renameTag(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagRenameState.value
            assertTrue(state is AlbumViewModel.TagRenameState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagRenameState.Error).error)
        }

    @Test
    fun `renameTag network error updates state to Error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "New Tag Name"
            val errorMessage = "Network error"
            coEvery { remoteRepository.renameTag(tagId, tagName) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.renameTag(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagRenameState.value
            assertTrue(state is AlbumViewModel.TagRenameState.Error)
            assertEquals(AlbumViewModel.AlbumError.NetworkError, (state as AlbumViewModel.TagRenameState.Error).error)
        }

    @Test
    fun `renameTag exception updates state to Error`() =
        runTest {
            // Given
            val tagId = "tag1"
            val tagName = "New Tag Name"
            val exception = Exception("Unexpected error")
            coEvery { remoteRepository.renameTag(tagId, tagName) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.renameTag(tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagRenameState.value
            assertTrue(state is AlbumViewModel.TagRenameState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagRenameState.Error).error)
        }

    // addRecommendedPhotosToTagAlbum tests
    @Test
    fun `addRecommendedPhotosToTagAlbum success adds photos to album`() =
        runTest {
            // Given
            val photo1 = createPhoto("photo1")
            val photo2 = createPhoto("photo2")
            val tagId = "tag1"
            val tagName = "Test Tag"

            // First load the album
            val initialPhotoResponses = listOf(createPhotoResponse("photo1"))
            val initialPhotos = listOf(photo1)
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(initialPhotoResponses)
            coEvery { localRepository.toPhotos(initialPhotoResponses) } returns initialPhotos
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for adding photos
            coEvery { remoteRepository.postTagsToPhoto(photo2.photoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)

            // Reload album after adding - needs to return updated list
            val updatedPhotoResponses = listOf(createPhotoResponse("photo1"), createPhotoResponse("photo2"))
            val updatedPhotos = listOf(photo1, photo2)
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(updatedPhotoResponses)
            coEvery { localRepository.toPhotos(updatedPhotoResponses) } returns updatedPhotos

            // When
            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo2), tagId, tagName)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagAddState.value is AlbumViewModel.TagAddState.Success)
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum with numeric photo ID converts to UUID`() =
        runTest {
            // Given
            val photo = createPhoto("456")
            val tagId = "tag1"
            val tagName = "Test Tag"
            val actualPhotoId = "uuid-456"
            val photoResponse = createPhotoResponse(actualPhotoId).copy(photoPathId = 456L)

            // Setup album
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for adding
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(listOf(photoResponse))
            coEvery { remoteRepository.postTagsToPhoto(actualPhotoId, tagId) } returns
                RemoteRepository.Result.Success(Unit)

            // When
            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo), tagId, tagName)
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.tagAddState.value is AlbumViewModel.TagAddState.Success)
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum with conversion error sets error state`() =
        runTest {
            // Given
            val photo = createPhoto("789")
            val tagId = "tag1"
            val tagName = "Test Tag"

            // Setup album
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for adding - photo not found
            coEvery { remoteRepository.getAllPhotos() } returns
                RemoteRepository.Result.Success(emptyList())

            // When
            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo), tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is AlbumViewModel.TagAddState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum error updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val tagName = "Test Tag"
            val errorMessage = "Failed to add tag"

            // Setup album
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for adding
            coEvery { remoteRepository.postTagsToPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.Error(500, errorMessage)

            // When
            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo), tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is AlbumViewModel.TagAddState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum unauthorized updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val tagName = "Test Tag"
            val errorMessage = "Unauthorized"

            // Setup album
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for adding
            coEvery { remoteRepository.postTagsToPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.Unauthorized(errorMessage)

            // When
            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo), tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is AlbumViewModel.TagAddState.Error)
            assertEquals(AlbumViewModel.AlbumError.Unauthorized, (state as AlbumViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum bad request updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val tagName = "Test Tag"
            val errorMessage = "Bad request"

            // Setup album
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for adding
            coEvery { remoteRepository.postTagsToPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.BadRequest(errorMessage)

            // When
            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo), tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is AlbumViewModel.TagAddState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum network error updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val tagName = "Test Tag"
            val errorMessage = "Network error"

            // Setup album
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for adding
            coEvery { remoteRepository.postTagsToPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.NetworkError(errorMessage)

            // When
            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo), tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is AlbumViewModel.TagAddState.Error)
            assertEquals(AlbumViewModel.AlbumError.NetworkError, (state as AlbumViewModel.TagAddState.Error).error)
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum exception updates state to Error`() =
        runTest {
            // Given
            val photo = createPhoto("photo1")
            val tagId = "tag1"
            val tagName = "Test Tag"
            val exception = Exception("Unexpected error")

            // Setup album
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns
                RemoteRepository.Result.Success(emptyList())
            coEvery { localRepository.toPhotos(emptyList()) } returns emptyList()
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(emptyList())

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            // Setup for adding
            coEvery { remoteRepository.postTagsToPhoto(photo.photoId, tagId) } returns
                RemoteRepository.Result.Exception(exception)

            // When
            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo), tagId, tagName)
            advanceUntilIdle()

            // Then
            val state = viewModel.tagAddState.value
            assertTrue(state is AlbumViewModel.TagAddState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.TagAddState.Error).error)
        }
}
