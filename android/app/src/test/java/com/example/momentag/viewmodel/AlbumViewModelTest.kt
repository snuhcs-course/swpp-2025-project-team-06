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
                mainCoroutineRule.testDispatcher,
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
            assertEquals(errorMessage, (state as AlbumViewModel.AlbumLoadingState.Error).message)
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
            val errorMessage = "Network error"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Error(errorMessage)

            // When
            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            // Then
            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(errorMessage, (state as AlbumViewModel.RecommendLoadingState.Error).message)
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
            assertEquals(errorMessage, (state as AlbumViewModel.TagDeleteState.Error).message)
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
}
