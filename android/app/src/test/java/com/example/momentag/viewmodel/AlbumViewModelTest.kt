package com.example.momentag.viewmodel

import android.net.Uri
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.TagId
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        localRepository = mockk(relaxed = true)
        remoteRepository = mockk(relaxed = true)
        recommendRepository = mockk(relaxed = true)
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

    private fun createMockPhoto(id: String): Photo =
        Photo(
            photoId = id,
            contentUri = mockk<Uri>(relaxed = true),
            createdAt = "2023-01-01",
        )

    // --- loadAlbum Tests ---

    @Test
    fun `loadAlbum success updates state and notifies repository`() =
        runTest {
            val tagId = "tag1"
            val tagName = "Tag 1"
            val photoResponses = listOf(mockk<PhotoResponse>())
            val photos = listOf(createMockPhoto("p1"))

            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.Success(photoResponses)
            every { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Success)
            assertEquals(photos, (state as AlbumViewModel.AlbumLoadingState.Success).photos)
            verify { imageBrowserRepository.setTagAlbum(photos, tagName) }
        }

    @Test
    fun `loadAlbum unauthorized updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.Unauthorized("401")

            viewModel.loadAlbum(tagId, "Tag 1")
            advanceUntilIdle()

            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.Unauthorized, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    @Test
    fun `loadAlbum network error updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.NetworkError("Error")

            viewModel.loadAlbum(tagId, "Tag 1")
            advanceUntilIdle()

            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.NetworkError, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    @Test
    fun `loadAlbum bad request updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.BadRequest("Bad Request")

            viewModel.loadAlbum(tagId, "Tag 1")
            advanceUntilIdle()

            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    @Test
    fun `loadAlbum exception updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.Exception(Exception("Error"))

            viewModel.loadAlbum(tagId, "Tag 1")
            advanceUntilIdle()

            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    @Test
    fun `loadAlbum unknown error updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.Error(500, "Error")

            viewModel.loadAlbum(tagId, "Tag 1")
            advanceUntilIdle()

            val state = viewModel.albumLoadingState.value
            assertTrue(state is AlbumViewModel.AlbumLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.AlbumLoadingState.Error).error)
        }

    // --- loadRecommendations Tests ---

    @Test
    fun `loadRecommendations success updates state`() =
        runTest {
            val tagId = "tag1"
            val photoResponses = listOf(mockk<PhotoResponse>())
            val photos = listOf(createMockPhoto("p1"))

            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns
                RecommendRepository.RecommendResult.Success(photoResponses)
            every { localRepository.toPhotos(photoResponses) } returns photos

            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Success)
            assertEquals(photos, (state as AlbumViewModel.RecommendLoadingState.Success).photos)
        }

    @Test
    fun `loadRecommendations unauthorized updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns RecommendRepository.RecommendResult.Unauthorized("401")

            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.Unauthorized, (state as AlbumViewModel.RecommendLoadingState.Error).error)
        }

    @Test
    fun `loadRecommendations network error updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns RecommendRepository.RecommendResult.NetworkError("Error")

            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.NetworkError, (state as AlbumViewModel.RecommendLoadingState.Error).error)
        }

    @Test
    fun `loadRecommendations bad request updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns RecommendRepository.RecommendResult.BadRequest("Error")

            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.RecommendLoadingState.Error).error)
        }

    @Test
    fun `loadRecommendations unknown error updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { recommendRepository.recommendPhotosFromTag(tagId) } returns RecommendRepository.RecommendResult.Error("Error")

            viewModel.loadRecommendations(tagId)
            advanceUntilIdle()

            val state = viewModel.recommendLoadingState.value
            assertTrue(state is AlbumViewModel.RecommendLoadingState.Error)
            assertEquals(AlbumViewModel.AlbumError.UnknownError, (state as AlbumViewModel.RecommendLoadingState.Error).error)
        }

    // --- Selection Tests ---

    @Test
    fun `toggleRecommendPhoto adds and removes photo`() {
        val photo = createMockPhoto("p1")

        viewModel.toggleRecommendPhoto(photo)
        assertTrue(viewModel.selectedRecommendPhotos.value.containsKey("p1"))

        viewModel.toggleRecommendPhoto(photo)
        assertFalse(viewModel.selectedRecommendPhotos.value.containsKey("p1"))
    }

    @Test
    fun `resetRecommendSelection clears selection`() {
        val photo = createMockPhoto("p1")
        viewModel.toggleRecommendPhoto(photo)

        viewModel.resetRecommendSelection()

        assertTrue(viewModel.selectedRecommendPhotos.value.isEmpty())
    }

    @Test
    fun `toggleTagAlbumPhoto adds and removes photo`() {
        val photo = createMockPhoto("p1")

        viewModel.toggleTagAlbumPhoto(photo)
        assertTrue(viewModel.selectedTagAlbumPhotos.value.containsKey("p1"))

        viewModel.toggleTagAlbumPhoto(photo)
        assertFalse(viewModel.selectedTagAlbumPhotos.value.containsKey("p1"))
    }

    @Test
    fun `resetTagAlbumPhotoSelection clears selection`() {
        val photo = createMockPhoto("p1")
        viewModel.toggleTagAlbumPhoto(photo)

        viewModel.resetTagAlbumPhotoSelection()

        assertTrue(viewModel.selectedTagAlbumPhotos.value.isEmpty())
    }

    // --- deleteTagFromPhotos Tests ---

    @Test
    fun `deleteTagFromPhotos success updates album state`() =
        runTest {
            val tagId = "tag1"
            val photo1 = createMockPhoto("p1")
            val photo2 = createMockPhoto("p2")
            val photosToDelete = listOf(photo1)

            // Setup initial state
            val initialPhotos = listOf(photo1, photo2)
            val photoResponses = listOf(mockk<PhotoResponse>())
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.Success(photoResponses)
            every { localRepository.toPhotos(photoResponses) } returns initialPhotos
            viewModel.loadAlbum(tagId, "Tag 1")
            advanceUntilIdle()

            coEvery { remoteRepository.removeTagFromPhoto("p1", tagId) } returns RemoteRepository.Result.Success(Unit)

            viewModel.deleteTagFromPhotos(photosToDelete, tagId)
            advanceUntilIdle()

            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Success)
            val albumState = viewModel.albumLoadingState.value as AlbumViewModel.AlbumLoadingState.Success
            assertEquals(listOf(photo2), albumState.photos)
        }

    @Test
    fun `deleteTagFromPhotos skips invalid photos`() =
        runTest {
            val tagId = "tag1"
            val invalidPhoto = createMockPhoto("") // Blank ID
            val validPhoto = createMockPhoto("p1")

            coEvery { remoteRepository.removeTagFromPhoto("p1", tagId) } returns RemoteRepository.Result.Success(Unit)

            viewModel.deleteTagFromPhotos(listOf(invalidPhoto, validPhoto), tagId)
            advanceUntilIdle()

            coVerify(exactly = 0) { remoteRepository.removeTagFromPhoto("", any()) }
            coVerify(exactly = 1) { remoteRepository.removeTagFromPhoto("p1", any()) }
            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Success)
        }

    @Test
    fun `deleteTagFromPhotos unauthorized updates state`() =
        runTest {
            val tagId = "tag1"
            val photo = createMockPhoto("p1")
            coEvery { remoteRepository.removeTagFromPhoto("p1", tagId) } returns RemoteRepository.Result.Unauthorized("401")

            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Error)
            assertEquals(
                AlbumViewModel.AlbumError.Unauthorized,
                (viewModel.tagDeleteState.value as AlbumViewModel.TagDeleteState.Error).error,
            )
        }

    @Test
    fun `deleteTagFromPhotos network error updates state`() =
        runTest {
            val tagId = "tag1"
            val photo = createMockPhoto("p1")
            coEvery { remoteRepository.removeTagFromPhoto("p1", tagId) } returns RemoteRepository.Result.NetworkError("Error")

            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Error)
            assertEquals(
                AlbumViewModel.AlbumError.NetworkError,
                (viewModel.tagDeleteState.value as AlbumViewModel.TagDeleteState.Error).error,
            )
        }

    @Test
    fun `deleteTagFromPhotos bad request updates state`() =
        runTest {
            val tagId = "tag1"
            val photo = createMockPhoto("p1")
            coEvery { remoteRepository.removeTagFromPhoto("p1", tagId) } returns RemoteRepository.Result.BadRequest("Error")

            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Error)
            assertEquals(
                AlbumViewModel.AlbumError.UnknownError,
                (viewModel.tagDeleteState.value as AlbumViewModel.TagDeleteState.Error).error,
            )
        }

    @Test
    fun `deleteTagFromPhotos exception updates state`() =
        runTest {
            val tagId = "tag1"
            val photo = createMockPhoto("p1")
            coEvery { remoteRepository.removeTagFromPhoto("p1", tagId) } returns RemoteRepository.Result.Exception(Exception("Error"))

            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Error)
            assertEquals(
                AlbumViewModel.AlbumError.UnknownError,
                (viewModel.tagDeleteState.value as AlbumViewModel.TagDeleteState.Error).error,
            )
        }

    @Test
    fun `deleteTagFromPhotos unknown error updates state`() =
        runTest {
            val tagId = "tag1"
            val photo = createMockPhoto("p1")
            coEvery { remoteRepository.removeTagFromPhoto("p1", tagId) } returns RemoteRepository.Result.Error(500, "Error")

            viewModel.deleteTagFromPhotos(listOf(photo), tagId)
            advanceUntilIdle()

            assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Error)
        }

    // --- renameTag Tests ---

    @Test
    fun `renameTag success updates state`() =
        runTest {
            val tagId = "tag1"
            val newName = "New Name"
            coEvery { remoteRepository.renameTag(tagId, newName) } returns RemoteRepository.Result.Success(TagId(tagId))

            viewModel.renameTag(tagId, newName)
            advanceUntilIdle()

            assertTrue(viewModel.tagRenameState.value is AlbumViewModel.TagRenameState.Success)
        }

    @Test
    fun `renameTag id mismatch updates state`() =
        runTest {
            val tagId = "tag1"
            val newName = "New Name"
            coEvery { remoteRepository.renameTag(tagId, newName) } returns RemoteRepository.Result.Success(TagId("differentId"))

            viewModel.renameTag(tagId, newName)
            advanceUntilIdle()

            assertTrue(viewModel.tagRenameState.value is AlbumViewModel.TagRenameState.Error)
            assertEquals(
                AlbumViewModel.AlbumError.UnknownError,
                (viewModel.tagRenameState.value as AlbumViewModel.TagRenameState.Error).error,
            )
        }

    @Test
    fun `renameTag unauthorized updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { remoteRepository.renameTag(tagId, "name") } returns RemoteRepository.Result.Unauthorized("401")

            viewModel.renameTag(tagId, "name")
            advanceUntilIdle()

            assertTrue(viewModel.tagRenameState.value is AlbumViewModel.TagRenameState.Error)
            assertEquals(
                AlbumViewModel.AlbumError.Unauthorized,
                (viewModel.tagRenameState.value as AlbumViewModel.TagRenameState.Error).error,
            )
        }

    @Test
    fun `renameTag network error updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { remoteRepository.renameTag(tagId, "name") } returns RemoteRepository.Result.NetworkError("Error")

            viewModel.renameTag(tagId, "name")
            advanceUntilIdle()

            assertTrue(viewModel.tagRenameState.value is AlbumViewModel.TagRenameState.Error)
            assertEquals(
                AlbumViewModel.AlbumError.NetworkError,
                (viewModel.tagRenameState.value as AlbumViewModel.TagRenameState.Error).error,
            )
        }

    @Test
    fun `renameTag unknown error updates state`() =
        runTest {
            val tagId = "tag1"
            coEvery { remoteRepository.renameTag(tagId, "name") } returns RemoteRepository.Result.Error(500, "Error")

            viewModel.renameTag(tagId, "name")
            advanceUntilIdle()

            assertTrue(viewModel.tagRenameState.value is AlbumViewModel.TagRenameState.Error)
        }

    // --- addRecommendedPhotosToTagAlbum Tests ---

    @Test
    fun `addRecommendedPhotosToTagAlbum success updates album state`() =
        runTest {
            val tagId = "tag1"
            val tagName = "Tag 1"
            val photo = createMockPhoto("p1")
            val photosToAdd = listOf(photo)

            // Setup initial state
            val initialPhotos = emptyList<Photo>()
            val photoResponses = listOf(mockk<PhotoResponse>())
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.Success(photoResponses)
            every { localRepository.toPhotos(photoResponses) } returns initialPhotos
            viewModel.loadAlbum(tagId, tagName)
            advanceUntilIdle()

            coEvery { remoteRepository.postTagsToPhoto("p1", tagId) } returns RemoteRepository.Result.Success(Unit)

            viewModel.addRecommendedPhotosToTagAlbum(photosToAdd, tagId, tagName)
            advanceUntilIdle()

            assertTrue(viewModel.tagAddState.value is AlbumViewModel.TagAddState.Success)
            coVerify(exactly = 2) { remoteRepository.getPhotosByTag(tagId) }
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum skips invalid photos`() =
        runTest {
            val tagId = "tag1"
            val invalidPhoto = createMockPhoto("")
            val validPhoto = createMockPhoto("p1")

            coEvery { remoteRepository.postTagsToPhoto("p1", tagId) } returns RemoteRepository.Result.Success(Unit)
            // Mock getPhotosByTag for the reload
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.Success(emptyList())

            viewModel.addRecommendedPhotosToTagAlbum(listOf(invalidPhoto, validPhoto), tagId, "Tag 1")
            advanceUntilIdle()

            coVerify(exactly = 0) { remoteRepository.postTagsToPhoto("", any()) }
            coVerify(exactly = 1) { remoteRepository.postTagsToPhoto("p1", any()) }
        }

    @Test
    fun `addRecommendedPhotosToTagAlbum unauthorized updates state`() =
        runTest {
            val tagId = "tag1"
            val photo = createMockPhoto("p1")
            coEvery { remoteRepository.postTagsToPhoto("p1", tagId) } returns RemoteRepository.Result.Unauthorized("401")
            // Mock getPhotosByTag for the reload (even if partial failure, it reloads)
            coEvery { remoteRepository.getPhotosByTag(tagId) } returns RemoteRepository.Result.Success(emptyList())

            viewModel.addRecommendedPhotosToTagAlbum(listOf(photo), tagId, "Tag 1")
            advanceUntilIdle()

            assertTrue(viewModel.tagAddState.value is AlbumViewModel.TagAddState.Error)
            assertEquals(AlbumViewModel.AlbumError.Unauthorized, (viewModel.tagAddState.value as AlbumViewModel.TagAddState.Error).error)
        }

    // --- Other Tests ---

    @Test
    fun `initializeAddPhotosFlow delegates to repository`() {
        val tagId = "tag1"
        val tagName = "Tag 1"

        viewModel.initializeAddPhotosFlow(tagId, tagName)

        verify { photoSelectionRepository.initialize(tagName, emptyList(), tagId) }
    }

    @Test
    fun `getPhotosToShare returns selected photos`() {
        val photo1 = createMockPhoto("p1")
        val photo2 = createMockPhoto("p2")

        viewModel.toggleTagAlbumPhoto(photo1)
        viewModel.toggleTagAlbumPhoto(photo2)

        val sharedPhotos = viewModel.getPhotosToShare()
        assertEquals(2, sharedPhotos.size)
        assertTrue(sharedPhotos.contains(photo1))
        assertTrue(sharedPhotos.contains(photo2))
    }

    @Test
    fun `scroll position logic works correctly`() {
        // Set
        viewModel.setScrollToIndex(5)
        assertEquals(5, viewModel.scrollToIndex.value)

        // Clear
        viewModel.clearScrollToIndex()
        assertNull(viewModel.scrollToIndex.value)

        // Restore
        every { imageBrowserRepository.getCurrentIndex() } returns 10
        viewModel.restoreScrollPosition()
        assertEquals(10, viewModel.scrollToIndex.value)
    }

    @Test
    fun `reset states works correctly`() {
        // Delete State
        viewModel.resetDeleteState()
        assertTrue(viewModel.tagDeleteState.value is AlbumViewModel.TagDeleteState.Idle)

        // Rename State
        viewModel.resetRenameState()
        assertTrue(viewModel.tagRenameState.value is AlbumViewModel.TagRenameState.Idle)

        // Add State
        viewModel.resetAddState()
        assertTrue(viewModel.tagAddState.value is AlbumViewModel.TagAddState.Idle)
    }
}
